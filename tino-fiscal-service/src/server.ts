import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { PfxCertificateProvider } from "./certificate.ts";
import { NfeWizardAdapter, UnavailableNfeWizardClient } from "./nfewizard.ts";
import { NfeWizardIoClient } from "./nfewizard-io.ts";
import { FiscalService, RedactedAuditLogger } from "./service.ts";
import { FiscalServiceError, type FiscalPort } from "./types.ts";

const MAX_BODY_BYTES = 128 * 1024;

async function readJson(request: IncomingMessage): Promise<Record<string, any>> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    const bytes = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    size += bytes.length;
    if (size > MAX_BODY_BYTES) {
      throw new FiscalServiceError("INVALID_REQUEST", "Request body is too large");
    }
    chunks.push(bytes);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new FiscalServiceError("INVALID_REQUEST", "Request body must be valid JSON");
  }
}

function writeJson(response: ServerResponse, status: number, payload: unknown): void {
  response.writeHead(status, { "content-type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(payload));
}

function createPort(): FiscalPort {
  const audit = new RedactedAuditLogger();
  const useRealAdapter = process.env.TINO_FISCAL_REAL_ADAPTER === "true"
    && process.env.TINO_FISCAL_ENVIRONMENT === "HOMOLOGATION";
  const service = new FiscalService(
    new NfeWizardAdapter(useRealAdapter ? new NfeWizardIoClient() : new UnavailableNfeWizardClient()),
    new PfxCertificateProvider(),
    {
      certificate: {
        pfxPath: process.env.TINO_FISCAL_A1_PATH ?? "",
        passwordEnvVar: process.env.TINO_FISCAL_A1_PASSWORD_ENV ?? "TINO_FISCAL_A1_PASSWORD",
      },
      allowProduction: process.env.TINO_FISCAL_ALLOW_PRODUCTION === "true",
      retry: { maxAttempts: 3, timeoutMs: 10_000, backoffMs: 250 },
    },
    audit,
  );
  return service;
}

export function createFiscalHttpServer(port: FiscalPort = createPort()) {
  return createServer(async (request, response) => {
    try {
      if (request.method === "GET" && request.url === "/health") {
        writeJson(response, 200, { status: "ok", service: "tino-fiscal-service" });
        return;
      }
      if (request.method !== "POST") {
        writeJson(response, 405, { error: "METHOD_NOT_ALLOWED" });
        return;
      }

      const body = await readJson(request);
      if (request.url === "/v1/fiscal/incoming/discover") {
        const result = await port.discoverIncomingDocuments(body as any);
        writeJson(response, 200, result);
        return;
      }
      if (request.url === "/v1/fiscal/incoming/document") {
        const { reference, ...context } = body as any;
        const result = await port.fetchFiscalDocument(context, reference);
        writeJson(response, 200, result);
        return;
      }
      writeJson(response, 404, { error: "NOT_FOUND" });
    } catch (error) {
      const fiscalError = error instanceof FiscalServiceError
        ? error
        : new FiscalServiceError("RETRY_EXHAUSTED", "Fiscal service request failed");
      const status = fiscalError.code === "INVALID_REQUEST" ? 400
        : fiscalError.code === "NOT_CONFIGURED" ? 503
        : fiscalError.code === "PRODUCTION_DISABLED" ? 403
        : 502;
      writeJson(response, status, { error: fiscalError.code, message: fiscalError.message });
    }
  });
}

if (process.argv[1]?.endsWith("server.ts")) {
  const port = Number(process.env.PORT ?? 8787);
  createFiscalHttpServer().listen(port, "127.0.0.1", () => {
    console.log(`TINO fiscal service listening on 127.0.0.1:${port}`);
  });
}
