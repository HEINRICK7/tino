# TINO Fiscal Service — Slice 006

Serviço Node/TypeScript isolado para descobrir documentos fiscais recebidos e
entregar XML validado ao pipeline fiscal local do TINO.

## Limites deste slice

- O certificado A1 é carregado somente neste serviço.
- O Android não recebe certificado, senha, acesso à SEFAZ ou dependência de
  NFeWizard.
- O serviço expõe um `FiscalPort` pequeno: descobrir referências e buscar XML.
- O XML devolvido é um envelope de handoff; a canonicalização de domínio
  continua sendo responsabilidade do `tino-fiscal-core` no Android.
- Produção fica bloqueada por padrão. O primeiro ambiente autorizado deve ser
  homologação, com certificado de teste.
- Não há emissão de NFC-e, cancelamento ou alteração de documentos.

## Execução local

```bash
npm test
npm run check
```

O servidor usa um cliente NFeWizard injetado. Sem um adapter configurado, ele
responde `NOT_CONFIGURED`; isso é intencional e evita que um ambiente local
faça uma chamada fiscal acidental.

## Contrato HTTP mínimo

`GET /health`

`POST /v1/fiscal/incoming/discover`

```json
{
  "storeId": "store-001",
  "cnpj": "12345678000199",
  "uf": "PI",
  "environment": "HOMOLOGATION"
}
```

`POST /v1/fiscal/incoming/document`

```json
{
  "storeId": "store-001",
  "cnpj": "12345678000199",
  "uf": "PI",
  "environment": "HOMOLOGATION",
  "reference": {
    "accessKey": "35260112345678000199550010000000011000000010",
    "externalId": "dfe-001"
  }
}
```

O adapter real de NFeWizard deve ser conectado por injeção no processo do
serviço. Nenhuma credencial deve entrar no JSON, no Android, no log ou no
repositório.

## Homologação controlada

O adapter real fica desligado por padrão. Para preparar uma execução de
homologação, o processo precisa receber:

```text
TINO_FISCAL_ENVIRONMENT=HOMOLOGATION
TINO_FISCAL_REAL_ADAPTER=true
TINO_FISCAL_A1_PATH=/caminho/fora-do-repositorio/certificado-teste.pfx
TINO_FISCAL_A1_PASSWORD_ENV=TINO_FISCAL_A1_PASSWORD
TINO_FISCAL_CNPJ=00000000000000
TINO_FISCAL_UF=PI
TINO_FISCAL_A1_PASSWORD=...   # secret manager, nunca commitado
```

Antes de iniciar:

```bash
npm run preflight
```

O pacote `nfewizard-io` não é instalado automaticamente. Ele deve passar por
revisão de licença antes de uma instalação deliberada. Sem ele, o serviço
continua iniciando, mas responde `NOT_CONFIGURED` para chamadas fiscais.
