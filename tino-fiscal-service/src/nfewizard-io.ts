import { mkdir, mkdtemp, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, join } from "node:path";
import {
  FiscalServiceError,
  type DiscoverIncomingDocumentsRequest,
  type FiscalDocumentReference,
  type LoadedA1Certificate,
  type NfeWizardClient,
  type FiscalStoreContext,
} from "./types.ts";

type NfeWizardInstance = {
  NFE_LoadEnvironment(input: { config: Record<string, any> }): Promise<void>;
  NFE_DistribuicaoDFePorUltNSU(input: Record<string, any>): Promise<any>;
  NFE_DistribuicaoDFePorChave(input: Record<string, any>): Promise<any>;
};

export interface NfeWizardIoClientOptions {
  packageName?: string;
  workspaceRoot?: string;
  connectionTimeoutMs?: number;
}

const UF_CODES: Record<string, number> = {
  AC: 12, AL: 27, AP: 16, AM: 13, BA: 29, CE: 23, DF: 53, ES: 32,
  GO: 52, MA: 21, MT: 51, MS: 50, MG: 31, PA: 15, PB: 25, PR: 41,
  PE: 26, PI: 22, RJ: 33, RN: 24, RS: 43, RO: 11, RR: 14, SC: 42,
  SP: 35, SE: 28, TO: 17,
};

function ensureNotAborted(signal: AbortSignal): void {
  if (signal.aborted) {
    throw new FiscalServiceError("TIMEOUT", "Fiscal gateway operation was cancelled", { retryable: true });
  }
}

function extractAccessKey(xml: string): string | undefined {
  return xml.match(/<\s*infNFe\b[^>]*\bId\s*=\s*["']NFe(\d{44})["']/i)?.[1]
    ?? xml.match(/\bchNFe\b[^>]*>(\d{44})</i)?.[1];
}

function extractNsu(fileName: string): string | undefined {
  return fileName.match(/-(?:res|proc|event)(?:-[^-]+)?-(\d{15})\.xml$/i)?.[1];
}

/**
 * Real NFeWizard integration kept in the outer adapter layer.
 * The dependency is optional so the service remains bootable in a clean
 * environment and cannot accidentally call SEFAZ before the license and
 * homologation gates are accepted.
 */
export class NfeWizardIoClient implements NfeWizardClient {
  private readonly packageName: string;
  private readonly workspaceRoot: string;
  private readonly connectionTimeoutMs: number;

  constructor(options: NfeWizardIoClientOptions = {}) {
    this.packageName = options.packageName ?? "nfewizard-io";
    this.workspaceRoot = options.workspaceRoot ?? tmpdir();
    this.connectionTimeoutMs = options.connectionTimeoutMs ?? 30_000;
  }

  async discoverIncomingDocuments(
    request: DiscoverIncomingDocumentsRequest,
    certificate: LoadedA1Certificate,
    signal: AbortSignal,
  ): Promise<{ documents: FiscalDocumentReference[]; nextCursor?: string }> {
    ensureNotAborted(signal);
    const workspace = await this.createWorkspace(certificate);
    try {
      const wizard = await this.createWizard(request, certificate, workspace);
      const response = await wizard.NFE_DistribuicaoDFePorUltNSU({
        cUFAutor: this.ufCode(request.uf),
        CNPJ: request.cnpj,
        ultNSU: request.cursor ?? "000000000000000",
      });
      ensureNotAborted(signal);
      const documents = await this.referencesFromWorkspace(workspace.xmlDirectory);
      return {
        documents,
        nextCursor: this.extractMaxNsu(response, documents) ?? request.cursor,
      };
    } catch (error) {
      throw this.mapGatewayError(error);
    } finally {
      await rm(workspace.root, { recursive: true, force: true });
    }
  }

  async fetchXml(
    context: FiscalStoreContext,
    reference: FiscalDocumentReference,
    certificate: LoadedA1Certificate,
    signal: AbortSignal,
  ): Promise<Buffer> {
    ensureNotAborted(signal);
    const workspace = await this.createWorkspace(certificate);
    try {
      const wizard = await this.createWizard(context, certificate, workspace);
      const response = await wizard.NFE_DistribuicaoDFePorChave({
        cUFAutor: this.ufCode(context.uf),
        CNPJ: context.cnpj,
        consChNFe: { chNFe: reference.accessKey },
      });
      ensureNotAborted(signal);
      const files = await readdir(workspace.xmlDirectory);
      for (const file of files) {
        if (!file.toLowerCase().endsWith(".xml")) continue;
        const bytes = await readFile(join(workspace.xmlDirectory, file));
        if (extractAccessKey(bytes.toString("utf8")) === reference.accessKey) return bytes;
      }

      const embedded = this.extractEmbeddedXml(response, reference.accessKey);
      if (embedded) return Buffer.from(embedded, "utf8");
      throw new FiscalServiceError("SEFAZ_REJECTED", "NFeWizard returned no XML for the requested NF-e");
    } catch (error) {
      throw this.mapGatewayError(error);
    } finally {
      await rm(workspace.root, { recursive: true, force: true });
    }
  }

  private async createWizard(
    context: FiscalStoreContext,
    certificate: LoadedA1Certificate,
    workspace: { xmlDirectory: string; logDirectory: string; certificatePath: string },
  ): Promise<NfeWizardInstance> {
    let imported: any;
    try {
      imported = await import(this.packageName);
    } catch {
      throw new FiscalServiceError("NOT_CONFIGURED", `${this.packageName} is not installed`, { retryable: false });
    }
    const Wizard = imported.default ?? imported.NFeWizard;
    if (!Wizard) throw new FiscalServiceError("NOT_CONFIGURED", "NFeWizard constructor is unavailable");
    const wizard: NfeWizardInstance = new Wizard();
    await wizard.NFE_LoadEnvironment({
      config: {
        dfe: {
          baixarXMLDistribuicao: true,
          pathXMLDistribuicao: workspace.xmlDirectory,
          armazenarXMLRetorno: true,
          pathXMLRetorno: workspace.logDirectory,
          armazenarXMLConsulta: true,
          pathXMLConsulta: workspace.logDirectory,
          armazenarXMLConsultaComTagSoap: false,
          armazenarRetornoEmJSON: false,
          pathRetornoEmJSON: workspace.logDirectory,
          pathCertificado: workspace.certificatePath,
          senhaCertificado: certificate.password,
          UF: context.uf,
          CPFCNPJ: context.cnpj,
        },
        nfe: {
          ambiente: context.environment === "HOMOLOGATION" ? 2 : 1,
          versaoDF: "4.00",
        },
        lib: {
          connection: { timeout: this.connectionTimeoutMs },
          log: {
            exibirLogNoConsole: false,
            armazenarLogs: true,
            pathLogs: workspace.logDirectory,
          },
          useOpenSSL: false,
          useForSchemaValidation: "validateSchemaJsBased",
        },
      },
    });
    return wizard;
  }

  private async createWorkspace(certificate: LoadedA1Certificate) {
    const root = await mkdtemp(join(this.workspaceRoot, "tino-nfewizard-"));
    const xmlDirectory = join(root, "xml");
    const logDirectory = join(root, "logs");
    const certificatePath = certificate.sourcePath ?? join(root, "certificate.pfx");
    await mkdir(xmlDirectory, { recursive: true, mode: 0o700 });
    await mkdir(logDirectory, { recursive: true, mode: 0o700 });
    await writeFile(join(root, ".keep"), "");
    if (!certificate.sourcePath) {
      await writeFile(certificatePath, certificate.pfx, { mode: 0o600 });
    }
    return { root, xmlDirectory, logDirectory, certificatePath };
  }

  private async referencesFromWorkspace(directory: string): Promise<FiscalDocumentReference[]> {
    const files = await readdir(directory);
    const references: FiscalDocumentReference[] = [];
    for (const file of files) {
      if (!file.toLowerCase().endsWith(".xml")) continue;
      const xml = (await readFile(join(directory, file))).toString("utf8");
      const accessKey = extractAccessKey(xml);
      if (accessKey) references.push({
        accessKey,
        externalId: extractNsu(file) ?? basename(file, ".xml"),
      });
    }
    return references;
  }

  private extractMaxNsu(response: any, documents: FiscalDocumentReference[]): string | undefined {
    const candidates = JSON.stringify(response ?? {}).match(/\b(?:NSU|ultNSU|maxNSU)\b[^\d]{0,8}(\d{15})/gi)
      ?.map((value) => value.match(/(\d{15})/)?.[1]).filter(Boolean) as string[] | undefined;
    return candidates?.sort().at(-1) ?? documents.map((document) => document.externalId).sort().at(-1);
  }

  private extractEmbeddedXml(response: any, accessKey: string): string | undefined {
    const values: string[] = [];
    const visit = (value: any): void => {
      if (typeof value === "string") {
        if (value.includes(accessKey) && value.includes("<")) values.push(value);
        return;
      }
      if (Array.isArray(value)) value.forEach(visit);
      else if (value && typeof value === "object") Object.values(value).forEach(visit);
    };
    visit(response);
    return values.find((value) => extractAccessKey(value) === accessKey);
  }

  private ufCode(uf: string): number {
    const code = UF_CODES[uf];
    if (!code) throw new FiscalServiceError("INVALID_REQUEST", `Unsupported UF: ${uf}`);
    return code;
  }

  private mapGatewayError(error: unknown): FiscalServiceError {
    if (error instanceof FiscalServiceError) return error;
    const message = error instanceof Error ? error.message : "NFeWizard request failed";
    return new FiscalServiceError("SEFAZ_UNAVAILABLE", message, { retryable: true });
  }
}
