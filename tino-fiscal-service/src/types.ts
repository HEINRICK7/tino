import type { IncomingMessage, ServerResponse } from "node:http";

export type FiscalEnvironment = "HOMOLOGATION" | "PRODUCTION";

export type FiscalErrorCode =
  | "INVALID_REQUEST"
  | "CERTIFICATE_NOT_CONFIGURED"
  | "CERTIFICATE_INVALID"
  | "PRODUCTION_DISABLED"
  | "NOT_CONFIGURED"
  | "SEFAZ_UNAVAILABLE"
  | "SEFAZ_REJECTED"
  | "XML_INVALID"
  | "ACCESS_KEY_MISMATCH"
  | "TIMEOUT"
  | "RETRY_EXHAUSTED";

export class FiscalServiceError extends Error {
  readonly code: FiscalErrorCode;
  readonly retryable: boolean;
  readonly details?: Record<string, string>;

  constructor(
    code: FiscalErrorCode,
    message: string,
    options: { retryable?: boolean; details?: Record<string, string> } = {},
  ) {
    super(message);
    this.name = "FiscalServiceError";
    this.code = code;
    this.retryable = options.retryable ?? false;
    this.details = options.details;
  }
}

export interface FiscalStoreContext {
  storeId: string;
  cnpj: string;
  uf: string;
  environment: FiscalEnvironment;
}

export interface FiscalDocumentReference {
  accessKey: string;
  externalId: string;
  issuedAt?: string;
  supplierTaxId?: string;
}

export interface DiscoverIncomingDocumentsRequest extends FiscalStoreContext {
  cursor?: string;
  limit?: number;
}

export interface DiscoverIncomingDocumentsResult {
  references: FiscalDocumentReference[];
  nextCursor?: string;
  observedAt: string;
}

export interface CanonicalFiscalHandoff extends FiscalStoreContext {
  reference: FiscalDocumentReference;
  xml: string;
  sha256: string;
  contentType: "application/xml";
  validation: {
    validator: "TINO_NFE_STRUCTURAL_V1";
    valid: true;
    accessKey: string;
  };
  handoff: {
    target: "tino-fiscal-core";
    parserVersion: "TINO_FISCAL_CORE_V1";
  };
  fetchedAt: string;
}

export interface A1CertificateConfig {
  pfxPath: string;
  passwordEnvVar: string;
}

export interface LoadedA1Certificate {
  readonly pfx: Buffer;
  readonly password: string;
  readonly fingerprint: string;
  readonly sourcePath?: string;
}

export interface CertificateProvider {
  load(config: A1CertificateConfig): Promise<LoadedA1Certificate>;
}

export interface FiscalGateway {
  discoverIncomingDocuments(
    request: DiscoverIncomingDocumentsRequest,
    certificate: LoadedA1Certificate,
    signal: AbortSignal,
  ): Promise<DiscoverIncomingDocumentsResult>;
  fetchXml(
    context: FiscalStoreContext,
    reference: FiscalDocumentReference,
    certificate: LoadedA1Certificate,
    signal: AbortSignal,
  ): Promise<Buffer>;
}

export interface NfeWizardClient {
  discoverIncomingDocuments(
    request: DiscoverIncomingDocumentsRequest,
    certificate: LoadedA1Certificate,
    signal: AbortSignal,
  ): Promise<{ documents: FiscalDocumentReference[]; nextCursor?: string }>;
  fetchXml(
    context: FiscalStoreContext,
    reference: FiscalDocumentReference,
    certificate: LoadedA1Certificate,
    signal: AbortSignal,
  ): Promise<Buffer>;
}

export interface AuditEvent {
  name: string;
  at: string;
  storeId?: string;
  environment?: FiscalEnvironment;
  accessKey?: string;
  externalId?: string;
  sha256?: string;
  attempt?: number;
  errorCode?: FiscalErrorCode;
}

export interface AuditLogger {
  record(event: AuditEvent): void;
}

export interface FiscalPort {
  discoverIncomingDocuments(
    request: DiscoverIncomingDocumentsRequest,
  ): Promise<DiscoverIncomingDocumentsResult>;
  fetchFiscalDocument(
    context: FiscalStoreContext,
    reference: FiscalDocumentReference,
  ): Promise<CanonicalFiscalHandoff>;
}

export interface HttpDependencies {
  port: FiscalPort;
  server: { close(callback?: (error?: Error) => void): void };
}

export type HttpHandler = (
  request: IncomingMessage,
  response: ServerResponse,
) => Promise<void>;
