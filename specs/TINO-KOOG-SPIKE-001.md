# TINO — KOOG TOOL-CALLING SPIKE 001

**Status:** ANDROID FACTS ADAPTER VALIDATED / DEVICE GATES PENDING  
**Data:** 2026-08-18

## Decisão

Koog não entra ainda como dependência do `app`. O TINO atual usa Kotlin 2.0.21;
o Koog 1.0 documenta uma linha Kotlin mais nova. Inserir o framework agora
misturaria a validação do tool-calling com uma migração de build.

O contrato framework-independent do app foi fechado em
`CreditPreparationToolSet`. Quando o runtime for avaliado, ele deverá depender
desse contrato, e não de Room, DAOs ou `CommerceRepository` diretamente.

O módulo `koog-spike` agora valida separadamente Kotlin 2.2.0 + Koog 1.0.0 e
registra tools class-based. Ele usa uma `CreditPreparationFactsPort` deliberada:
isso prova a integração do framework sem fingir que um fake é o banco real do
TINO.

## Golden path

```text
texto / voz final
      ↓
Fast Router MISS
      ↓
Koog + Gemma (futuro adapter)
      ↓
customer_ref + product_ref + quantity
      ↓
CreditPreparationToolSet
      ├── EntityResolutionService
      ├── Product/Customer facts reais
      └── CommerceToolDispatcher.preview
      ↓
CreditSalePreview
      ↓
A2UI
      ↓
confirmação humana
      ↓
handler determinístico existente
      ↓
domínio / Room
```

## Tools allowlisted

| Tool | Modo | Pode mutar? | Fatos vêm de |
|---|---|---:|---|
| `findCustomer` | READ_ONLY | não | `EntityResolutionService` |
| `findProduct` | READ_ONLY | não | `EntityResolutionService` + estoque local |
| `getCustomerBalance` | READ_ONLY | não | `CommerceRepository` / crédito |
| `getProductStock` | READ_ONLY | não | movimentos de estoque |
| `getCurrentPrice` | READ_ONLY | não | produto local |
| `prepareCreditSale` | PREPARE_ONLY | não | dispatcher + regras de domínio |

O sandbox registra somente `findCustomer`, `findProduct`,
`getCustomerBalance` e `prepareCreditSale`. Estoque e preço são retornados como
fatos do port; nenhum ID é exposto e nenhum commit existe.

Não existe `commitCreditSale` no catálogo do agente. O commit permanece fora do
loop agentic e só ocorre após confirmação humana.

## Invariantes

- o modelo fornece somente referências textuais e quantidade solicitada;
- IDs internos nunca entram no contrato externo;
- preço, saldo e estoque são lidos do estado local;
- ambiguidade e ausência de entidade são estados explícitos;
- falha ou rejeição não muta banco;
- todas as tools são offline-first;
- o resultado factual final passa por capability e A2UI.

## Gates do spike de framework

Já comprovados no sandbox:

`KOOG_BUILD_ISOLATED = PASS`, `KOTLIN_2_2_COMPAT = PASS`,
`TOOL_REGISTRY = PASS`, `PREPARE_ONLY = PASS` e `NO_COMMIT_TOOL = PASS`.

Ainda pendentes, para execução com adapter Android + aparelho:

`KOOG_ANDROID_BOOT`, `LOCAL_GEMMA`, `LOCAL_GEMMA_TOOL_CALLING`, `REAL_DB_FACTS`,
`NO_DIRECT_ROOM_ACCESS`, `ENTITY_RESOLVER_REUSED`, `PREPARE_ONLY`,
`NO_AGENT_COMMIT`, `A2UI_PREVIEW`, `OFFLINE`, `LATENCY_MEASURED` e
`20_VARIATIONS`.

O contrato atual já cobre testes locais de resolução, fatos reais, preview e
ausência de mutação. Isso não deve ser reportado como prova de Koog no aparelho.

## Spike 002 — Android facts adapter

Implementado em `AndroidCreditPreparationFactsAdapter`. O mapeamento é:

```text
findCustomer       → EntityResolutionService.resolveCustomer
findProduct        → EntityResolutionService.resolveProduct
                    + CommerceRepository.stockBalance
getCustomerBalance → EntityResolutionService.resolveCustomer
                    + CommerceRepository.customerBalance
prepareCreditSale  → ToolExecutor.preview(ADD_CREDIT_ITEM)
```

O adapter não duplica regra de crédito, não recebe acesso do Koog e não chama
Room diretamente. O preview é o mesmo preview usado pelo boundary existente.

Resultado do Slice 002:

- `ANDROID_FACTS_ADAPTER = PASS`
- `REAL_CUSTOMER_RESOLUTION = PASS`
- `REAL_PRODUCT_RESOLUTION = PASS`
- `REAL_BALANCE = PASS`
- `REAL_PRICE = PASS`
- `REAL_STOCK = PASS`
- `REAL_PREVIEW = PASS`
- `NO_KOOG_ROOM_ACCESS = PASS`
- `NO_DUPLICATED_DOMAIN_LOGIC = PASS`
- `NO_LLM_FACTS = PASS`
- `NO_COMMIT_TOOL = PASS`
- `NO_MUTATION = PASS`
- `OFFLINE = PASS`
- `AMBIGUITY = PASS`
- `NOT_FOUND = PASS`
- `CONTRACT_TEST = PASS`
- `APP_MAIN_KOTLIN_UNCHANGED = PASS`
- `TESTS = PASS`
- `ASSEMBLE = PASS`
- `LINT = PASS`

O aparelho não estava conectado nesta execução; portanto `KOOG_ANDROID_BOOT`,
`LOCAL_GEMMA_TOOL_CALLING`, latência física e `20_VARIATIONS` continuam
pendentes.

## Checkpoint de validação — 2026-08-18

O adapter agora está exposto no grafo Hilt somente pela porta
`CreditPreparationFactsPort`. Koog continua fora do APK principal e não há
acesso de Room, DAO ou repository no sandbox.

O teste Android usa Room em memória e fatos reais do TINO. Ele comprova:

- cliente, produto, preço, estoque e saldo resolvidos localmente;
- preview produzido pelo `CommerceToolDispatcher` existente;
- preço/estoque/saldo reais, sem fatos fornecidos pelo modelo;
- `NotFound` explícito;
- ambiguidade fuzzy explícita para entidades distintas;
- ambiguidade bloqueia o preview e não cria evento nem altera saldo/estoque;
- `prepareCreditSale` permanece somente preparação, sem ferramenta de commit.

Validação executada:

- `:app:testDebugUnitTest` — PASS;
- `AndroidCreditPreparationFactsAdapterTest` — 3/3 PASS;
- `:tino-agent-contracts:build` — PASS;
- `:app:assembleDebug` — PASS;
- `:app:lintDebug` — PASS;
- `koog-spike:test` — PASS, 3/3.

Ainda não comprovado:

- boot/execução do Koog contra o app em aparelho;
- Gemma local escolhendo as tools reais;
- `20_VARIATIONS` com linguagem natural;
- latência física e smoke test de voz/texto.

O APK foi gerado em `app/build/outputs/apk/debug/app-debug.apk`, mas a
instalação física não foi executada porque `adb devices` não encontrou aparelho
ou emulador disponível.

## Spike 003 — tool orchestration harness

Foi adicionado `CreditPreparationAgent` somente ao sandbox Koog. Ele recebe um
plano estruturado — ainda não uma frase natural — e executa o registry nesta
ordem:

```text
findCustomer
      ↓
findProduct
      ↓
getCustomerBalance
      ↓
prepareCreditSale
      ↓
PREVIEW
```

Cada etapa entra em um trace. `NOT_FOUND` ou `AMBIGUOUS` interrompe a sequência
imediatamente. Não existe `commitCreditSale`, e o harness não tem como alterar
o `CreditPreparationFactsPort`.

Novos testes do sandbox:

- cadeia completa produz preview e não commit;
- ambiguidade de produto interrompe antes de saldo e preparação;
- nenhum resultado contém `COMMIT`;
- registry continua limitado às quatro tools allowlisted.

`koog-spike:test` agora passa com 9 testes.

Este slice não declara `LOCAL_GEMMA_TOOL_CALLING = PASS`: o próximo passo é
conectar um executor/model adapter real ao harness, mantendo o mesmo port e sem
expor Room ao Koog.

## Spike 004 — model plan boundary

Foi criado `GemmaCreditPlanAdapter` como fronteira para o futuro runtime local.
Ele aceita somente o contrato estrito:

```json
{
  "schema": "tino.credit-preparation-plan",
  "schema_version": 1,
  "capability": "ADD_CREDIT_ITEM",
  "customer_ref": "Dona Maria Lina",
  "product_ref": "Café Maratá",
  "quantity": 1
}
```

O parser rejeita JSON inválido, capability diferente, quantidade inválida e
qualquer campo desconhecido. Portanto `customer_id`, `product_id`,
`price_cents`, `balance_cents` e `stock` não conseguem atravessar a fronteira.

`GemmaCreditPreparationFlow` conecta esse plano validado ao harness do Spike
003 e chega ao preview usando as quatro tools allowlisted. Os testes também
comprovam que campos factuais extras são rejeitados antes de qualquer tool.

Este é um adapter de contrato com inference fake nos testes; não é ainda uma
execução do Gemma MediaPipe em aparelho e não deve ser reportado como
`LOCAL_GEMMA_TOOL_CALLING`.

## Spike 005 — ponte para o Gemma local do app

O contrato de inferência foi extraído para `tino-agent-contracts`:
`CreditPlanInferencePort` retorna somente `Generated`, `Unavailable` ou
`Failed`.

No app, `AndroidGemmaCreditPlanInferenceAdapter` delega esse contrato ao
`GemmaTextInference` MediaPipe já existente. O provider Hilt expõe a porta,
não a implementação MediaPipe, para consumidores futuros.

```text
MediaPipeGemmaTextInference
          ↓
AndroidGemmaCreditPlanInferenceAdapter
          ↓
CreditPlanInferencePort
          ↓
GemmaCreditPlanAdapter
          ↓
CreditPreparationAgent
          ↓
facts port / preview
```

Validação do slice:

- sandbox Koog — PASS;
- adapter Android Generated/Unavailable/Failed — PASS;
- testes unitários completos do app — PASS;
- `assembleDebug` — PASS;
- `lintDebug` — PASS.

Ainda pendente: executar `MediaPipeGemmaTextInference` com modelo instalado,
medir latência real e provar seleção de tools pelo Gemma em aparelho. Nenhum
commit foi adicionado ao fluxo.

## Smoke test físico — 2026-08-18

Executado no aparelho conectado via ADB:

- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — PASS;
- `MainActivity` iniciada e registrada como `ResumedActivity` — PASS;
- nenhum `FATAL EXCEPTION` ou `AndroidRuntime` do TINO no boot — PASS;
- `assets/models/gemma3-1b-it-int4.task` presente no APK — PASS.

O modelo ainda não foi marcado como carregado: `GemmaModelStore` só copia o
artefato quando `GemmaTextInference.generate()` é chamado, e a
`NotificationShade` do aparelho permaneceu sobre a Activity durante este
smoke test. Portanto continuam pendentes `MODEL_LOAD`,
`LOCAL_GEMMA_INFERENCE`, `LOCAL_GEMMA_TOOL_CALLING` e as medições reais de
latência.
