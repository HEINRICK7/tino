const required = [
  "TINO_FISCAL_A1_PATH",
  "TINO_FISCAL_A1_PASSWORD_ENV",
  "TINO_FISCAL_CNPJ",
  "TINO_FISCAL_UF",
];

const missing = required.filter((key) => !process.env[key]);
const environment = process.env.TINO_FISCAL_ENVIRONMENT ?? "HOMOLOGATION";

if (environment !== "HOMOLOGATION") {
  console.error("BLOCKED: Slice 007 preflight accepts HOMOLOGATION only.");
  process.exitCode = 2;
} else if (missing.length > 0) {
  console.error(`BLOCKED: missing homologation configuration: ${missing.join(", ")}`);
  process.exitCode = 2;
} else {
  console.log("READY: homologation configuration is present; no SEFAZ call was made.");
}
