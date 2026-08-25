import {
  FiscalServiceError,
  type A1CertificateConfig,
  type AuditLogger,
  type CertificateProvider,
  type DiscoverIncomingDocumentsRequest,
  type FiscalDocumentReference,
  type FiscalGateway,
  type FiscalPort,
  type FiscalStoreContext,
  type CanonicalFiscalHandoff,
} from "./types.ts";
import { withRetry, type RetryPolicy } from "./retry.ts";
import { validateNfeXml } from "./xml.ts";

export interface FiscalServiceConfig {
  certificate: A1CertificateConfig;
  allowProduction: boolean;
  retry: RetryPolicy;
}

export class RedactedAuditLogger implements AuditLogger {
  readonly events: Array<Record<string, unknown>> = [];

  record(event: Record<string, unknown>): void {
    this.events.push({ ...event });
  }
}

export class FiscalService implements FiscalPort {
  private readonly gateway: FiscalGateway;
  private readonly certificateProvider: CertificateProvider;
  private readonly config: FiscalServiceConfig;
  private readonly audit: AuditLogger;

  constructor(
    gateway: FiscalGateway,
    certificateProvider: CertificateProvider,
    config: FiscalServiceConfig,
    audit: AuditLogger,
  ) {
    this.gateway = gateway;
    this.certificateProvider = certificateProvider;
    this.config = config;
    this.audit = audit;
  }

  async discoverIncomingDocuments(
    request: DiscoverIncomingDocumentsRequest,
  ) {
    this.assertContext(request);
    const certificate = await this.loadCertificate(request);
    return withRetry(
      (signal, attempt) =>
        this.gateway.discoverIncomingDocuments(request, certificate, signal),
      this.config.retry,
      (attempt) => this.audit.record({
        name: "fiscal.dfe.discovery.attempt",
        at: new Date().toISOString(),
        storeId: request.storeId,
        environment: request.environment,
        attempt,
      }),
    );
  }

  async fetchFiscalDocument(
    context: FiscalStoreContext,
    reference: FiscalDocumentReference,
  ): Promise<CanonicalFiscalHandoff> {
    this.assertContext(context);
    if (!/^\d{44}$/.test(reference.accessKey)) {
      throw new FiscalServiceError("INVALID_REQUEST", "NF-e access key must contain 44 digits");
    }
    const certificate = await this.loadCertificate(context);
    const xmlBytes = await withRetry(
      (signal, attempt) => this.gateway.fetchXml(context, reference, certificate, signal),
      this.config.retry,
      (attempt) => this.audit.record({
        name: "fiscal.xml.fetch.attempt",
        at: new Date().toISOString(),
        storeId: context.storeId,
        environment: context.environment,
        accessKey: reference.accessKey,
        externalId: reference.externalId,
        attempt,
      }),
    );

    const validated = validateNfeXml(xmlBytes, reference.accessKey);
    const result: CanonicalFiscalHandoff = {
      ...context,
      reference,
      xml: validated.xml,
      sha256: validated.sha256,
      contentType: "application/xml",
      validation: {
        validator: "TINO_NFE_STRUCTURAL_V1",
        valid: true,
        accessKey: validated.accessKey,
      },
      handoff: {
        target: "tino-fiscal-core",
        parserVersion: "TINO_FISCAL_CORE_V1",
      },
      fetchedAt: new Date().toISOString(),
    };
    this.audit.record({
      name: "fiscal.xml.handoff.ready",
      at: new Date().toISOString(),
      storeId: context.storeId,
      environment: context.environment,
      accessKey: reference.accessKey,
      externalId: reference.externalId,
      sha256: result.sha256,
    });
    return result;
  }

  private async loadCertificate(context: FiscalStoreContext) {
    if (context.environment === "PRODUCTION" && !this.config.allowProduction) {
      throw new FiscalServiceError(
        "PRODUCTION_DISABLED",
        "Production fiscal connectivity is disabled in this service",
      );
    }
    return this.certificateProvider.load(this.config.certificate);
  }

  private assertContext(context: FiscalStoreContext): void {
    if (!context.storeId || !/^\d{14}$/.test(context.cnpj) || !/^[A-Z]{2}$/.test(context.uf)) {
      throw new FiscalServiceError("INVALID_REQUEST", "Fiscal store context is invalid");
    }
  }
}
