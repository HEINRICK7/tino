import {
  FiscalServiceError,
  type DiscoverIncomingDocumentsRequest,
  type FiscalDocumentReference,
  type FiscalGateway,
  type FiscalStoreContext,
  type LoadedA1Certificate,
  type NfeWizardClient,
} from "./types.ts";

export class NfeWizardAdapter implements FiscalGateway {
  private readonly client: NfeWizardClient;

  constructor(client: NfeWizardClient) {
    this.client = client;
  }

  async discoverIncomingDocuments(
    request: DiscoverIncomingDocumentsRequest,
    certificate: LoadedA1Certificate,
    signal: AbortSignal,
  ) {
    const result = await this.client.discoverIncomingDocuments(request, certificate, signal);
    return {
      references: result.documents,
      nextCursor: result.nextCursor,
      observedAt: new Date().toISOString(),
    };
  }

  fetchXml(
    context: FiscalStoreContext,
    reference: FiscalDocumentReference,
    certificate: LoadedA1Certificate,
    signal: AbortSignal,
  ) {
    return this.client.fetchXml(context, reference, certificate, signal);
  }
}

export class UnavailableNfeWizardClient implements NfeWizardClient {
  discoverIncomingDocuments(): Promise<{ documents: FiscalDocumentReference[] }> {
    return Promise.reject(
      new FiscalServiceError("NOT_CONFIGURED", "NFeWizard adapter is not configured", { retryable: false }),
    );
  }

  fetchXml(): Promise<Buffer> {
    return Promise.reject(
      new FiscalServiceError("NOT_CONFIGURED", "NFeWizard adapter is not configured", { retryable: false }),
    );
  }
}
