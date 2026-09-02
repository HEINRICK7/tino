# TINO Fiscal Service — Slice 006

**Status:** ISOLATED CONTRACT + HOMOLOGATION HARNESS COMPLETE  
**Data:** 2026-08-18  
**Escopo:** serviço Node/TypeScript separado do app Android

## Decisão

O Android não conversa diretamente com SEFAZ, não carrega certificado A1 e não
conhece NFeWizard. O serviço fiscal é a borda externa:

```text
Android FiscalPort
        ↓ HTTP contract
TINO Fiscal Service
        ↓ FiscalGateway
NfeWizardAdapter
        ↓
SEFAZ / homologação
```

O slice implementado para em `XML + hash + handoff`. A canonicalização de
domínio continua no `tino-fiscal-core`, evitando duas fontes de verdade para o
modelo fiscal.

## Contratos criados

Em `tino-fiscal-service/src/types.ts`:

- `FiscalPort.discoverIncomingDocuments(request)`;
- `FiscalPort.fetchFiscalDocument(context, reference)`;
- `FiscalDocumentReference` com chave de acesso e identificador externo;
- `CanonicalFiscalHandoff` com XML, SHA-256, validação estrutural e destino
  `tino-fiscal-core`;
- `CertificateProvider`, `FiscalGateway` e `NfeWizardClient` como portas;
- erros tipados e auditáveis, sem credenciais no contrato Android.

## Implementação

- `NfeWizardAdapter`: boundary substituível; mapeia cliente NFeWizard para
  `FiscalGateway`.
- `PfxCertificateProvider`: lê PFX e senha somente no processo Node; calcula
  fingerprint para auditoria sem registrar senha ou bytes.
- `validateNfeXml`: valida tamanho, rejeita DTD/ENTITY, exige estrutura NF-e e
  confirma a chave de acesso solicitada.
- `FiscalService`: aplica contexto fiscal, bloqueio de produção, retry,
  timeout, hash, validação e handoff.
- `server.ts`: `GET /health`, descoberta e busca de documento via JSON.
- `UnavailableNfeWizardClient`: default seguro; o serviço não faz chamada
  fiscal acidental sem adapter configurado.

O adapter concreto `NfeWizardIoClient` foi adicionado para a homologação
controlada, com carregamento dinâmico do pacote, workspace temporário para XML
e logs, distribuição por último NSU/chave e limpeza dos artefatos temporários.
Ele só é ativado quando `TINO_FISCAL_REAL_ADAPTER=true` e o ambiente é
`HOMOLOGATION`.

## Golden path comprovado localmente

```text
homologation context
    ↓
fake A1 provider
    ↓
fake NFeWizard client
    ↓
document reference
    ↓
XML NF-e
    ↓
structural validation + access key check
    ↓
SHA-256
    ↓
CanonicalFiscalHandoff → tino-fiscal-core
```

O teste não usa certificado real nem produção SEFAZ. Isso é intencional: o
primeiro contato externo deve ocorrer somente após configurar homologação e
registrar a licença/versão do adapter NFeWizard autorizado.

## Gates

| Gate | Estado | Evidência |
|---|---|---|
| `FISCAL_SERVICE_BOOT` | PASS | `/health` respondeu `200` localmente |
| `NODE_TS_ISOLATION` | PASS | pacote independente `tino-fiscal-service` |
| `NFEWIZARD_ADAPTER` | PASS (boundary) / PENDING (real) | adapter + fake contract tests; pacote não instalado |
| `CERTIFICATE_A1_LOAD` | PASS (local load) | provider + bytes fake; PKCS#12 acceptance remains NFeWizard responsibility |
| `NO_CERT_ON_ANDROID` | PASS | nenhuma alteração em `app` para este slice |
| `SEFAZ_CONNECTIVITY` | PENDING | requer endpoint homologação autorizado |
| `DFE_DISCOVERY` | PASS (fake) / PENDING (SEFAZ) | fake comprovado; externo pendente |
| `XML_FETCH` | PASS (fake) / PENDING (SEFAZ) | fake comprovado; externo pendente |
| `XML_VALIDATION` | PASS estrutural | 10 testes, DTD/ENTITY e chave cobertos |
| `CANONICAL_HANDOFF` | PASS | envelope aponta para `tino-fiscal-core` |
| `TIMEOUT_HANDLING` | PASS | teste de 3 tentativas com timeout |
| `RETRY_POLICY` | PASS | falha transitória recuperada na terceira tentativa |
| `AUDIT` | PASS | eventos redigidos; XML não é logado |
| `LICENSE_GATE_RECORDED` | PENDING | `nfewizard-io` GPL-3.0 requer revisão antes da ativação |
| `ANDROID_DOMAIN_UNCHANGED` | PASS | nenhum arquivo Android alterado |

## Testes e execução

```text
npm test
10 passed, 0 failed

npm run check
passed

node --experimental-strip-types src/server.ts
GET /health → 200
```

## Próximo passo autorizado

Ativar o `NfeWizardIoClient` em ambiente de homologação, após revisão de
licença, instalação da dependência e fornecimento de certificado A1 de teste e
endpoint SEFAZ aprovado. O procedimento está em
[`TINO-FISCAL-SLICE-007.md`](TINO-FISCAL-SLICE-007.md). Depois repetir o mesmo
contrato com um documento real de teste:

```text
DF-e discovery → XML fetch → hash → CanonicalFiscalHandoff
```

Somente após esse teste passar deve-se conectar a tela “Buscar NF-e” do Android.
Não implementar emissão NFC-e, cancelamento, contas a pagar ou novas regras de
domínio neste slice.
