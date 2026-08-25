import { createHash } from "node:crypto";
import { FiscalServiceError } from "./types.ts";

export interface ValidatedNfeXml {
  xml: string;
  sha256: string;
  accessKey: string;
}

const ACCESS_KEY_LENGTH = 44;

export function validateNfeXml(xmlBytes: Buffer, expectedAccessKey?: string): ValidatedNfeXml {
  if (xmlBytes.length === 0 || xmlBytes.length > 10 * 1024 * 1024) {
    throw new FiscalServiceError("XML_INVALID", "Fiscal XML size is invalid");
  }

  const xml = xmlBytes.toString("utf8").replace(/^\uFEFF/, "");
  if (xml.includes("<!DOCTYPE") || xml.includes("<!ENTITY") || /<\s*!doctype/i.test(xml)) {
    throw new FiscalServiceError("XML_INVALID", "Fiscal XML cannot contain external entity declarations");
  }

  const hasNfeRoot = /<\s*(?:nfeProc|NFe)\b/i.test(xml);
  const infNfe = xml.match(/<\s*infNFe\b[^>]*\bId\s*=\s*["']NFe(\d{44})["']/i);
  if (!hasNfeRoot || !infNfe) {
    throw new FiscalServiceError("XML_INVALID", "Fiscal XML is not a recognizable NF-e document");
  }

  const accessKey = infNfe[1];
  if (accessKey.length !== ACCESS_KEY_LENGTH) {
    throw new FiscalServiceError("XML_INVALID", "NF-e access key has invalid length");
  }
  if (expectedAccessKey && expectedAccessKey !== accessKey) {
    throw new FiscalServiceError("ACCESS_KEY_MISMATCH", "NF-e access key does not match the requested document");
  }

  return {
    xml,
    sha256: createHash("sha256").update(xmlBytes).digest("hex"),
    accessKey,
  };
}
