# TINO Fiscal — Slice 007: SEFAZ/NFeWizard Homologation

**Status:** READY FOR CONTROLLED HOMOLOGATION / EXTERNAL GATES PENDING  
**Data:** 2026-08-18

## O que foi feito

O serviço ganhou o adapter real `NfeWizardIoClient` sem importar NFeWizard para
o domínio do serviço:

```text
FiscalService
    ↓ FiscalGateway / NfeWizardClient
NfeWizardIoClient
    ↓ dynamic import
nfewizard-io
    ↓
SEFAZ homologação
```

O adapter:

- usa `NFE_LoadEnvironment` somente no serviço;
- mapeia `HOMOLOGATION` para ambiente 2;
- descobre documentos por último NSU;
- busca documento por chave de acesso;
- lê XML do workspace temporário do NFeWizard;
- tenta o retorno embutido quando não há arquivo local;
- devolve somente bytes XML ao `FiscalService`;
- deixa o `FiscalService` validar chave, estrutura e hash;
- remove XML, logs e cópia temporária do certificado ao terminar.

O servidor só ativa esse adapter quando:

```text
TINO_FISCAL_REAL_ADAPTER=true
TINO_FISCAL_ENVIRONMENT=HOMOLOGATION
```

Sem essas condições, o cliente permanece `UnavailableNfeWizardClient`.

## Preflight

O comando é:

```bash
npm run preflight
```

No ambiente atual, ele falha fechado porque ainda não existem:

- caminho do certificado A1 de teste;
- nome da variável de senha;
- CNPJ fiscal do estabelecimento;
- UF de homologação.

Nenhuma chamada SEFAZ foi feita.

## Gates atuais

| Gate | Estado |
|---|---|
| `LICENSE_REVIEW_RECORDED` | PENDING — `nfewizard-io@1.1.2`, GPL-3.0 |
| `HOMOLOGATION_ENV` | PENDING — configuração não fornecida |
| `CERTIFICATE_LOAD` | PASS local / PENDING real |
| `NFEWIZARD_REAL_ADAPTER` | IMPLEMENTED / PENDING runtime |
| `SEFAZ_CONNECTIVITY` | PENDING |
| `DOCUMENT_DISCOVERY` | PENDING |
| `XML_FETCH` | PENDING |
| `XML_SCHEMA/STRUCTURE` | PASS estrutural local |
| `CANONICAL_HANDOFF` | PASS com fake gateway |
| `NO_PRODUCTION_CALL` | PASS — produção bloqueada por padrão |
| `NO_CERT_ON_ANDROID` | PASS |
| `AUDIT_NO_SECRETS` | PASS no serviço; logs do fornecedor permanecem temporários |
| `TIMEOUT_RETRY` | PASS em testes fake |
| `TESTS` | PASS — 10/10 |
| `SERVICE_BOOT` | PASS — `/health` |

## Bloqueio objetivo

Não é seguro marcar conectividade real sem os quatro insumos externos. O
próximo operador autorizado deverá revisar a licença, instalar a dependência
opcional, fornecer certificado A1 de homologação fora do repositório e rodar o
preflight. Só então deve executar um único ciclo de descoberta/busca em
homologação e guardar o hash do XML como evidência.

Não ativar produção, emissão NFC-e, cancelamento, estoque ou qualquer commit
Android neste slice.
