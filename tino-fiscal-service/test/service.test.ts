import assert from "node:assert/strict";
import { test } from "node:test";
import { createHash } from "node:crypto";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { FiscalServiceError, type CertificateProvider, type LoadedA1Certificate, type NfeWizardClient } from "../src/types.ts";
import { PfxCertificateProvider } from "../src/certificate.ts";
import { NfeWizardAdapter } from "../src/nfewizard.ts";
import { FiscalService, RedactedAuditLogger } from "../src/service.ts";
import { validateNfeXml } from "../src/xml.ts";

const context = {
  storeId: "store-001",
  cnpj: "12345678000199",
  uf: "PI",
  environment: "HOMOLOGATION" as const,
};
const accessKey = "35260112345678000199550010000000011000000010";
const xml = `<nfeProc><NFe><infNFe Id="NFe${accessKey}"><ide><nNF>1</nNF></ide></infNFe></NFe></nfeProc>`;
const reference = { accessKey, externalId: "dfe-001" };
const certificate: LoadedA1Certificate = {
  pfx: Buffer.from("fake-pfx"),
  password: "test-only",
  fingerprint: createHash("sha256").update("fake-pfx").digest("hex"),
};

class FakeCertificateProvider implements CertificateProvider {
  calls = 0;
  async load() {
    this.calls += 1;
    return certificate;
  }
}

class FakeNfeWizardClient implements NfeWizardClient {
  discoveryCalls = 0;
  fetchCalls = 0;
  failFetchAttempts = 0;
  delayFetchMs = 0;
  private readonly fetchedCertificates: LoadedA1Certificate[] = [];

  async discoverIncomingDocuments() {
    this.discoveryCalls += 1;
    return { documents: [reference], nextCursor: "next-1" };
  }

  async fetchXml(_context: typeof context, _reference: typeof reference, loaded: LoadedA1Certificate) {
    this.fetchCalls += 1;
    this.fetchedCertificates.push(loaded);
    if (this.delayFetchMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, this.delayFetchMs));
    }
    if (this.fetchCalls <= this.failFetchAttempts) {
      throw new FiscalServiceError("SEFAZ_UNAVAILABLE", "temporary homologation outage", { retryable: true });
    }
    return Buffer.from(xml);
  }
}

function createService(client: FakeNfeWizardClient, certificateProvider = new FakeCertificateProvider()) {
  const audit = new RedactedAuditLogger();
  const service = new FiscalService(
    new NfeWizardAdapter(client),
    certificateProvider,
    {
      certificate: { pfxPath: "/not-used-in-test.pfx", passwordEnvVar: "TEST_PASSWORD" },
      allowProduction: false,
      retry: { maxAttempts: 3, timeoutMs: 100, backoffMs: 0 },
    },
    audit,
  );
  return { service, audit, certificateProvider };
}

test("NFeWizard adapter maps discovery to FiscalPort contract", async () => {
  const client = new FakeNfeWizardClient();
  const { service } = createService(client);
  const result = await service.discoverIncomingDocuments(context);
  assert.deepEqual(result.references, [reference]);
  assert.equal(result.nextCursor, "next-1");
  assert.equal(client.discoveryCalls, 1);
});

test("golden path fetches, hashes and creates canonical handoff", async () => {
  const client = new FakeNfeWizardClient();
  const { service, audit } = createService(client);
  const result = await service.fetchFiscalDocument(context, reference);
  assert.equal(result.xml, xml);
  assert.equal(result.validation.accessKey, accessKey);
  assert.equal(result.handoff.target, "tino-fiscal-core");
  assert.equal(result.sha256, createHash("sha256").update(Buffer.from(xml)).digest("hex"));
  assert.equal(client.fetchCalls, 1);
  assert.equal(audit.events.at(-1)?.name, "fiscal.xml.handoff.ready");
  assert.equal("xml" in audit.events.at(-1)!, false);
});

test("retry handles temporary SEFAZ outage and succeeds", async () => {
  const client = new FakeNfeWizardClient();
  client.failFetchAttempts = 2;
  const { service } = createService(client);
  const result = await service.fetchFiscalDocument(context, reference);
  assert.equal(result.validation.valid, true);
  assert.equal(client.fetchCalls, 3);
});

test("non-retryable gateway errors are not retried", async () => {
  const client = new FakeNfeWizardClient();
  client.fetchXml = async () => {
    throw new FiscalServiceError("SEFAZ_REJECTED", "rejected by homologation", { retryable: false });
  };
  const { service } = createService(client);
  await assert.rejects(() => service.fetchFiscalDocument(context, reference), { code: "SEFAZ_REJECTED" });
});

test("timeout is observable and retries only within the configured budget", async () => {
  const client = new FakeNfeWizardClient();
  client.delayFetchMs = 150;
  const { service } = createService(client);
  await assert.rejects(() => service.fetchFiscalDocument(context, reference), { code: "TIMEOUT" });
  assert.equal(client.fetchCalls, 3);
});

test("production is blocked until explicitly enabled", async () => {
  const client = new FakeNfeWizardClient();
  const { service } = createService(client);
  await assert.rejects(
    () => service.fetchFiscalDocument({ ...context, environment: "PRODUCTION" }, reference),
    { code: "PRODUCTION_DISABLED" },
  );
  assert.equal(client.fetchCalls, 0);
});

test("access key mismatch fails before canonical handoff", async () => {
  const client = new FakeNfeWizardClient();
  client.fetchXml = async () => Buffer.from(xml.replace(accessKey, accessKey.replace(/^3/, "4")));
  const { service } = createService(client);
  await assert.rejects(() => service.fetchFiscalDocument(context, reference), { code: "ACCESS_KEY_MISMATCH" });
});

test("structural XML validator rejects external entities and malformed NF-e", () => {
  assert.throws(() => validateNfeXml(Buffer.from("<!DOCTYPE foo><NFe/>")), { code: "XML_INVALID" });
  assert.throws(() => validateNfeXml(Buffer.from("<NFe><infNFe Id=\"NFe123\"/></NFe>")), { code: "XML_INVALID" });
});

test("certificate material is passed only from service to gateway", async () => {
  const client = new FakeNfeWizardClient();
  const { service, certificateProvider } = createService(client);
  await service.fetchFiscalDocument(context, reference);
  assert.equal(certificateProvider.calls, 1);
  assert.equal(client.fetchCalls, 1);
  assert.equal(client.fetchedCertificates[0]?.fingerprint, certificate.fingerprint);
});

test("A1 provider loads a non-empty PFX without exposing it in configuration", async () => {
  const directory = await mkdtemp(join(tmpdir(), "tino-fiscal-"));
  const file = join(directory, "homologation.pfx");
  const previousPassword = process.env.TEST_A1_PASSWORD;
  process.env.TEST_A1_PASSWORD = "test-only-password";
  try {
    await writeFile(file, Buffer.from("homologation-pfx-bytes"));
    const loaded = await new PfxCertificateProvider().load({
      pfxPath: file,
      passwordEnvVar: "TEST_A1_PASSWORD",
    });
    assert.equal(loaded.pfx.toString(), "homologation-pfx-bytes");
    assert.equal(loaded.password, "test-only-password");
    assert.equal(loaded.fingerprint.length, 64);
  } finally {
    if (previousPassword === undefined) delete process.env.TEST_A1_PASSWORD;
    else process.env.TEST_A1_PASSWORD = previousPassword;
    await rm(directory, { recursive: true, force: true });
  }
});
