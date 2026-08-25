import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import {
  FiscalServiceError,
  type A1CertificateConfig,
  type CertificateProvider,
  type LoadedA1Certificate,
} from "./types.ts";

export class PfxCertificateProvider implements CertificateProvider {
  async load(config: A1CertificateConfig): Promise<LoadedA1Certificate> {
    if (!config.pfxPath || !config.passwordEnvVar) {
      throw new FiscalServiceError(
        "CERTIFICATE_NOT_CONFIGURED",
        "A1 certificate configuration is incomplete",
      );
    }

    const password = process.env[config.passwordEnvVar];
    if (!password) {
      throw new FiscalServiceError(
        "CERTIFICATE_NOT_CONFIGURED",
        "A1 certificate password is not configured",
      );
    }

    let pfx: Buffer;
    try {
      pfx = await readFile(config.pfxPath);
    } catch {
      throw new FiscalServiceError("CERTIFICATE_NOT_CONFIGURED", "A1 certificate file is unavailable");
    }
    if (pfx.length === 0) {
      throw new FiscalServiceError("CERTIFICATE_INVALID", "A1 certificate file is empty");
    }

    return {
      pfx,
      password,
      fingerprint: createHash("sha256").update(pfx).digest("hex"),
      sourcePath: config.pfxPath,
    };
  }
}
