# TINO — Incomplete & Unvalidated Backlog

**Data da auditoria:** 21/08/2026  
**Regra:** cada task avança quando todos os critérios executáveis no ambiente
atual estão em `PASS`. Validações físicas indisponíveis ficam acumuladas como
`PENDING_DEVICE_VALIDATION` e não bloqueiam o desenvolvimento automatizado.

**Atualização do checkpoint — 2026-08-26:** G6.1 foi implementada com baseline
heurístico local, persistência, decisões, expiração e outcomes. Ela está em
`IMPLEMENTED_AUTOMATED_PENDING_DEVICE`; ver
[TINO-EVIDENCE-G6.1-2026-08-26.md](TINO-EVIDENCE-G6.1-2026-08-26.md). G6 completa
continua parcial até a validação física específica da interação e dos outcomes.

`CAP-001 / GET_CUSTOMER_CONTACT` também foi integrada em
`IMPLEMENTED_AUTOMATED_PENDING_QUERY_DEVICE`; ver
[TINO-EVIDENCE-CAP-001-CUSTOMER-CONTACT-2026-08-26.md](TINO-EVIDENCE-CAP-001-CUSTOMER-CONTACT-2026-08-26.md).

`CAP-001 / CREATE_CUSTOMER` também foi integrada em
`IMPLEMENTED_AUTOMATED_PENDING_QUERY_DEVICE`; ver
[TINO-EVIDENCE-CAP-001-CREATE-CUSTOMER-2026-08-26.md](TINO-EVIDENCE-CAP-001-CREATE-CUSTOMER-2026-08-26.md).

`CAP-001 / UPDATE_PRODUCT_PRICE` também foi integrada em
`IMPLEMENTED_AUTOMATED_PENDING_QUERY_DEVICE`; ver
[TINO-EVIDENCE-CAP-001-UPDATE-PRICE-2026-08-26.md](TINO-EVIDENCE-CAP-001-UPDATE-PRICE-2026-08-26.md).

Este documento lista trabalho confirmado por código, testes e documentação.
Não trata `return null` de parsers como dívida automaticamente; só registra
lacunas que representam requisito, integração ou validação ausente.

**Atualização do checkpoint — 28/08/2026:** o encadeamento de ações do painel
`···` agora preserva o contexto de produto/fornecedor até a capability.
`REPLENISHMENT_QUERY`, estoque e lista de produto consultam o produto específico
quando originados por um thought de produto; “Ver fornecedor” resolve o
fornecedor específico. A regressão está coberta na suíte do agente. No Samsung
SM-A042M autorizado, G3.2, painel contextual, fallback físico e inferência real
do Gemma passaram com backend CPU/XNNPACK; o dataset atual não produz atenção
comercial visível sem mutar dados artificialmente.

**Atualização do checkpoint — 28/08/2026:** o Gemma real passou no Samsung
SM-A042M com backend CPU/XNNPACK (`GENERATED OK`); o fallback isolado continua
passando. O smoke G4.2 confirmou canal, permissão e publicação Android de uma
atenção sintética sem alterar Room/comércio, e o `PendingIntent` passou a abrir
diretamente “Avisos do TINO”. Atenção comercial real e mutação física controlada
continuam pendentes porque não foram fabricadas no aparelho. A avaliação
one-shot imediata no startup e após fatos locais também foi integrada; o
`dumpsys jobscheduler` confirmou a execução do job no APK final.

**Atualização do checkpoint — 28/08/2026:** G4.3 passou no Samsung SM-A042M
com consulta somente-leitura pelo `IntelligenceRuntimePort` real. A resposta
retornou `ANSWERED`, fato `products` e analytics `lowest_stock`, sem mutação;
perguntas conhecidas agora usam o fast path determinístico e o modelo local fica
reservado para perguntas não mapeadas. Ver
[TINO-EVIDENCE-G4.3-2026-08-28.md](TINO-EVIDENCE-G4.3-2026-08-28.md).

**Atualização do checkpoint — 28/08/2026:** G4.4 passou no Samsung SM-A042M
com teste instrumentado do caminho `preview → confirmação → persistência` e
bloqueio de execução direta/replay. O cenário usou Room em memória e não tocou
o banco piloto; a evidência está em
[TINO-EVIDENCE-G4.4-MUTATION-2026-08-28.md](TINO-EVIDENCE-G4.4-MUTATION-2026-08-28.md).

**Atualização do checkpoint — 28/08/2026:** G4.5 confirmou a leitura do
snapshot no Samsung, mas o banco atualmente instalado está vazio e não há
backup local do pacote comercial. A avaliação do modelo em dados reais aguarda
restauração/importação legítima; ver
[TINO-EVIDENCE-G4.5-DATASET-2026-08-28.md](TINO-EVIDENCE-G4.5-DATASET-2026-08-28.md).

## Estado atual do gate

| Task | Estado | Evidência | Bloqueio |
|---|---|---|---|
| G3.2 — Evaluation & Persistent Observability | **PASS_FULL** | 299 testes app, 32 fiscal, lint, APK e smoke físico PASS | nenhum bloqueio |
| G3.3 — AgentRuntimePort + Agent Loop | **PASS** | loop, testes focados, regressão, lint e APK passam | nenhum critério automatizável pendente |
| G3.4 — Interaction State | **PASS** | contrato, Room, migration, 4 testes focados, 299 testes app, lint e APK passam | nenhum critério automatizável pendente |
| G3.5 — Correction Learning Engine | **PASS** | estados, provenance, escopo, demotion, remoção, 4 testes focados, 299 testes app, lint e APK passam | nenhum critério automatizável pendente |
| G3.6 — Adaptive Lexicon | **PASS** | contrato, scoring lexical/fonético/contextual, aliases, integração e 4 testes focados; 299 testes app, lint e APK passam | nenhum critério automatizável pendente |
| G3.7 — UiPlanner | **PASS** | GroundedResult, UiPlannerPort, decisões tipadas, fallback e 9 testes focados; 299 testes app, lint e APK passam | nenhum critério automatizável pendente |
| G3.8 — A2UI Surface Protocol | **PASS_FULL** | 8 testes focados, 299 testes app, lint, APK e render/update físico no Xiaomi/API 36 | nenhum bloqueio |
| G3.9 — TINO Component Catalog | **PASS_FULL** | 7 testes focados, 299 testes app, lint, APK e surface multi-componente física no Xiaomi/API 36 | nenhum bloqueio |
| G3.10 — A2UI Actions → Agent Loop | **PASS_FULL** | 6 testes focados, 299 testes app, lint, APK e Choice/update físico no Xiaomi/API 36 | nenhum bloqueio |
| G3.11 — Mutation Safety & Confirmation | **PASS_FULL** | 10 testes de safety, 3 Room, 8 A2UI, 314 testes app, 32 fiscal, lint, APK e smoke físico completo no Xiaomi | nenhum bloqueio |
| G3.12 — Working & Session Memory | **PASS_FULL** | 318 testes app, Room/TTL/context tests, lint, APK e smoke físico de seed, restart e limpeza seletiva no Xiaomi | nenhum bloqueio |
| G4 — ADK Autonomous Loop | **PASS_FULL** | 321 testes app, limites de loop/replan/timeout, lint, APK e harness físico de multi-tool, clarificação e proteção no Xiaomi/API 36 | nenhum bloqueio |
| G5 — Long-Term Business Memory | **PASS_FULL** | 326 testes app, policy/lifecycle/provenance, Room 13→14, lint, APK e smoke físico de promoção, contradição, restart e remoção no Xiaomi/API 36 | nenhum bloqueio |
| G4.1 — Voice Reliability & Crash Recovery | **PASS_FULL** | Consolidação física de fala, revisão/edição, continuação, cancelamento, learning, roteamento, ProductPicker, clientes, estoque, financeiro, fallback e Gemma real em `TINO-EVIDENCE-G4.1-2026-08-23-FINAL-PASS.md`; Samsung/API 34 confirmou CPU/XNNPACK dentro de orçamento finito | atenção comercial real e mutação física controlada |
| G4.2 — Attention notification boundary | **PASS** | Smoke físico no Samsung confirmou canal `tino-attention`, permissão, título/texto e preservação do comércio; PendingIntent para “Avisos do TINO” coberto por Robolectric | atenção comercial real sem dados fabricados |
| G4.3 — Intelligence Runtime query path | **PASS_FULL** | 553 testes app, lint/assemble e smoke físico somente-leitura com `ANSWERED`, fatos Room e analytics no Samsung | nenhuma lacuna automatizável; mutações continuam em gate separado |
| G4.4 — Mutation safety physical boundary | **PASS_SAFE_PATH** | 1 teste instrumentado no Samsung, Room em memória, preview/confirm/persistência/replay bloqueado | mutação comercial real no banco piloto exige autorização e dados reais |

### Epic planejada — Multi-Vertical Runtime Integration

**Estado:** `SPEC_BASELINED / BLOCKED_BY_G4.1`  
**Documento:** [TINO-EPIC-MULTI-VERTICAL-RUNTIME-INTEGRATION.md](TINO-EPIC-MULTI-VERTICAL-RUNTIME-INTEGRATION.md)

A epic transforma `BusinessProfile` persistido em fonte de verdade para
módulos, capabilities, Home, navegação e Agentic Shell. Ela não está iniciada;
seu gate de entrada é `G4.1 = PASS_FULL`.

G3.2 passou em modo `PASS_FULL` após o device Xiaomi 2410FPCC5G/API 36 abrir o
APK incrementalmente, reabrir o Room legado e permanecer ativo sem crash. G3.3
até G3.10 também passaram; G3.11 passou em `PASS_FULL` após o smoke físico de
mutation; G3.12 passou em `PASS_FULL` após o smoke físico de memória; G4 e G5
passaram em `PASS_FULL` com harnesses físicos no mesmo Xiaomi/API 36. G6.1 foi
iniciada e possui baseline automatizada; G6 amplo continua parcial.

## P0 — fechar fundação e prova física

### VAL-001 — Smoke test físico e validação de migração

**Estado:** `PASS`  
**Evidência:** APK gerado em `app/build/outputs/apk/debug/app-debug.apk` e
`tools/g3-2-smoke.sh` executado com o Xiaomi 2410FPCC5G/API 36; o script
instalou incrementalmente sem apagar dados, abriu a MainActivity, observou o
processo por 5 segundos e não encontrou crash. A migration 9→10 que falhava
por coluna duplicada foi corrigida alinhando `MIGRATION_8_9` ao schema 9.

**Validado no device:** instalação, abertura, reabertura do Room legado,
ausência de crash e processo estável. Consultas determinísticas, ADK e fallback
continuam validados pela suíte automatizada do Gate 3.2.

**Definition of Done:** device autorizado; `bash tools/g3-2-smoke.sh` executado;
logs sem crash fatal; G3.2 promovida para `PASS_FULL`.

### G3.3 — AgentRuntimePort + Agent Loop

**Estado:** `PASS`  
**Evidência:** `AgentRuntimePort`, `AgentInteraction`, `AgentTurnResult`,
estados formais `PLAN → EXECUTE_READ → OBSERVE → REPLAN → FINAL`, limite rígido,
timeout, clarificação, validação antes do executor, telemetria por turno e
replanejamento após falha de ferramenta implementados em
`app/src/main/java/com/tino/app/domain/intelligence/agent/AgentRuntime.kt`.

**Validação:** 6 testes focados do loop, 255 testes Android, 32 fiscal core,
lint e APK passam; fiscal service e Koog spike também passam. G3.4 está
liberada. Não há validação física adicional própria desta task; o backlog
físico da G3.2 continua acumulado.

### CAP-001 — Promoção do catálogo agentic canônico

**Estado:** `IN_PROGRESS`  
**Evidência:** `TINO-CAPABILITY-MATRIX.md` marca leituras como UI-only/legacy e
pagamento de fiado como GAP canônico.

`LIST_SUPPLIERS` foi promovida nesta rodada; ver
[TINO-EVIDENCE-CAP-001-LIST-SUPPLIERS-2026-08-26.md](TINO-EVIDENCE-CAP-001-LIST-SUPPLIERS-2026-08-26.md).

`GET_CUSTOMER_CONTACT` também foi promovida nesta rodada; ver
[TINO-EVIDENCE-CAP-001-CUSTOMER-CONTACT-2026-08-26.md](TINO-EVIDENCE-CAP-001-CUSTOMER-CONTACT-2026-08-26.md).

`REGISTER_CREDIT_PAYMENT` também foi promovida nesta rodada; ver
[TINO-EVIDENCE-CAP-001-CREDIT-PAYMENT-2026-08-26.md](TINO-EVIDENCE-CAP-001-CREDIT-PAYMENT-2026-08-26.md).

`REGISTER_STOCK_ENTRY` foi promovida nesta rodada: o contrato agentic valida
produto, quantidade, custo unitário e fornecedor opcional, gera preview pelo
dispatcher canônico e mantém a mutação atrás do gate de confirmação; a
regressão cobre parser Gemma, parser determinístico e boundary.

**Implementar:** `LIST_PRODUCTS`, `GET_PRODUCT_STOCK`, `GET_PRODUCT_PRICE`,
`LIST_CUSTOMERS`, `LIST_RECEIVABLES`, `LIST_OVERDUE`, `LIST_SUPPLIERS`,
`GET_CUSTOMER_CONTACT`, `REGISTER_CREDIT_PAYMENT`, `UPDATE_PRODUCT_PRICE` e
`REGISTER_STOCK_ENTRY` com use case, fonte, A2UI,
offline, empty state e testes.

**DoD:** cada capability atravessa o mesmo Boundary → Use Case → Room → A2UI;
dispatcher legado apenas adapter; nenhum fato inventado; read-only e
preview/confirm cobertos.

### VOICE-001 — Gate de voz real

**Estado:** `BLOCKED`  
**Evidência:** portas e adapters existem e testes unitários passam; o fluxo
físico ainda não foi executado.

**DoD:** microfone, permissão, transcrição committed, Gemma disponível ou
fallback honesto, preview, confirmação, cancelamento e erro recuperável no
device; partial transcript nunca gera mutação.

## P1 — completar runtime agentic e superfícies

### G3.4 — Interaction State

**Estado:** `PASS`  
**Evidência:** `InteractionState` e `InteractionStateStore` foram definidos no
domínio; `TinoAgentSession` mantém superfícies ativas; `RoomInteractionStateStore`
persiste drafts, política e expiração na tabela `interaction_states` pela
migration `11→12`.

**Validação:** o golden flow preserva cliente, produto e quantidade até
preview/confirmação; expiração remove o pending sem apagar a tela; limpeza após
cancelamento e round-trip Room passam. Há 4 testes focados, 259 testes Android,
lint e APK PASS. G3.5 está liberada.

### G3.5 — Correction Learning Engine

**Estado:** `PASS`  
**Evidência:** `CorrectionEvent`, `CorrectionLearningPort` e
`CorrectionLearningEngine` implementam estados `CANDIDATE`, `LEARNED`,
`TRUSTED`, `DEMOTED` e `REMOVED`, com provenance e escopo `SESSION`/`STORE`.
`CommerceContextMemory` usa o engine; a facade `LearnedAliasMemory` preserva a
API anterior.

**Validação:** 4 testes focados provam promoção, confirmação, contradição,
remoção e isolamento entre sessões; o cenário legado de “maraca” continua
passando. A suíte Android tem 285 testes, lint e APK PASS. G3.6 está liberada.

### G3.6 — Adaptive Lexicon

**Estado:** `PASS`  
**Evidência:** `AdaptiveLexiconPort`, catálogo tipado, aliases explícitos e
aprendidos, score lexical/fonético/contextual, thresholds, margem de
ambiguidade e fallback seguro implementados em
`app/src/main/java/com/tino/app/domain/language/AdaptiveLexicon.kt`. O
`EntityResolutionService` usa o adaptive path apenas depois do resolver
legado e continua retornando entidades reais do catálogo/Room.

**Validação:** `Maracá → Café Maratá` passa; alias aprendido normalizado passa;
Maria Lina/Maria Luiza permanece ambíguo; referência inexistente não fabrica
resultado. Há 4 testes focados do léxico, integração no resolver, 268 testes
Android, lint e APK PASS. G3.7 está liberada.

### G3.7 — UiPlanner

**Estado:** `PASS`  
**Evidência:** `GroundedResult`, `UiContext`, `UiPlannerPort`, decisões
tipadas e `DeterministicUiPlanner` implementados em
`app/src/main/java/com/tino/app/domain/intelligence/presentation/UiPlanner.kt`.
O planner não conhece Compose, renderer, A2UI JSON, Room ou tools.

**Validação:** texto, surface de comparação, update de surface, clarification,
confirmação, input, erro por dados insuficientes, `NO_UI` e fallback passam em
9 testes focados. A suíte Android tem 292 testes, lint e APK PASS. G3.8 está
concluída em `PASS_FULL`.

### G3.8 — A2UI Surface Protocol

**Estado:** `PASS_FULL`  
**Evidência:** ver [TINO-TASK-G3.8.md](TINO-TASK-G3.8.md). O protocolo possui
`CreateSurface`, `UpdateComponents`, `UpdateDataModel`, `DeleteSurface`,
bindings, codec/versionamento e host incremental. O Xiaomi 2410FPCC5G/API 36
renderizou a surface e atualizou o mesmo modelo sem crash.

**Validação:** 8 testes focados, 292 testes Android, lint, APK, instalação
incremental e smoke visual físico PASS.

### G3.9 — TINO Component Catalog

**Estado:** `PASS_FULL`  
**Evidência:** ver [TINO-TASK-G3.9.md](TINO-TASK-G3.9.md). O catálogo possui
seis grupos semânticos, descriptors versionados, schemas de props, allowlist,
fallback seguro e contribuidores verticais sem acoplamento ao ADK/Room.

**Validação:** 7 testes focados, 292 testes Android, lint, APK, instalação
incremental e surface física com três componentes no Xiaomi/API 36. O update
alterou somente o binding `received`, preservando os demais componentes e a
mesma `surfaceId`, sem crash.

### G3.10 — A2UI Actions → Agent Loop

**Estado:** `PASS_FULL`  
**Evidência:** ver [TINO-TASK-G3.10.md](TINO-TASK-G3.10.md). O renderer emite
`A2uiActionEvent`; o validator bloqueia surface/componente/ação/payload
inválidos; ações de agente entram pelo `AgentRuntimePort`; ações locais não
chamam o runtime.

**Validação:** 6 testes focados, 299 testes Android, lint, APK, instalação
incremental e smoke físico no Xiaomi/API 36. `apply_filter` atualizou a mesma
surface de `Todos` para `Só os atrasados` sem mutação direta ou crash.

**Próximo DoD:** seleção de produto ambígua dispara Action tipada, atualiza
InteractionState, retoma o plano original e atualiza a surface sem reiniciar
o comando.

### G3.11 — Mutation Safety & Confirmation

**Estado:** `PASS_FULL`  
**Evidência:** [TINO-TASK-G3.11.md](TINO-TASK-G3.11.md) e
[TINO-EVIDENCE-G3.11-2026-08-20.md](TINO-EVIDENCE-G3.11-2026-08-20.md). Policy
formal, reserva atômica, token, TTL, stale-state, idempotência persistente,
ponte A2UI e smoke físico completo foram comprovados.

**Resultado:** `PENDING → EXECUTING → COMMITTED`, cancelamento sem efeito,
replay terminal após restart, token cruzado e fingerprint stale rejeitados.

### G3.12 — Working & Session Memory

**Estado:** `PASS_FULL`  
**Evidência:** [TINO-TASK-G3.12.md](TINO-TASK-G3.12.md) e
[TINO-EVIDENCE-G3.12-2026-08-20.md](TINO-EVIDENCE-G3.12-2026-08-20.md).
Working Memory e Session Memory estão separados, persistidos no Room, protegidos
por TTL e validados após restart no Xiaomi. A memória guarda contexto, nunca
fatos comerciais atuais.

### G4 — ADK Autonomous Loop

**Estado:** `PASS_FULL`  
**Evidência:** [TINO-TASK-G4.md](TINO-TASK-G4.md) e
[TINO-EVIDENCE-G4-2026-08-20.md](TINO-EVIDENCE-G4-2026-08-20.md).

O ciclo `observe → plan → execute → observe → replan` foi validado com
`AdkAgentRuntime`, limites de tools/replans/timeout e proteção de chamada
duplicada. O Xiaomi comprovou resposta multi-tool após replan, encerramento em
clarificação e interrupção segura de loop. O ADK continua sem acesso a Room,
DAO, handlers ou mutation executor.

**Próximo passo:** nenhum gate posterior foi iniciado nesta rodada. Voz real,
RAG/Knowledge produtivo, sync cloud, promoção de capabilities, UX/UI e Undo
universal continuam no backlog independente.

### G5 — Long-Term Business Memory

**Estado:** `PASS_FULL`  
**Evidência:** [TINO-TASK-G5.md](TINO-TASK-G5.md) e
[TINO-EVIDENCE-G5-2026-08-20.md](TINO-EVIDENCE-G5-2026-08-20.md).

**Implementado:** contratos `BusinessMemoryPort`, policy anti-fato,
provenance, confidence, lifecycle `CANDIDATE/LEARNED/TRUSTED/DEMOTED/REMOVED`,
store Room, repository adapter e integração com `CommerceContextMemory`.

**Validação:** a correção de alias foi promovida, uma contradição demoveu o
registro anterior, o restart restaurou os registros, e a remoção seguida de
recarga manteve ambos como `REMOVED`. A suíte tem 326 testes, lint e APK
passaram e não houve crash, ANR ou erro SQLite no smoke.

**Regra de grounding:** Business Memory guarda preferências/correções estáveis;
saldo, estoque, preço, Pix, pagamentos e totais continuam exclusivamente no
Room comercial. O escopo físico desta fatia é `default-store`; multi-store
configurável permanece pendente.

**Estado atual:** G6.1 — Predictive Replenishment está implementada em modo
`IMPLEMENTED_AUTOMATED_PENDING_DEVICE`, com baseline heurística, persistência,
decisão, expiração e outcomes. A estatística explicável de vendas diárias,
sazonalidade com amostra mínima e regressão linear local com intervalo residual
já estão integradas ao contexto Evidence; o modelo recua para a estatística
quando a amostra é insuficiente e não altera o estoque.
G6 amplo — Predictive Tools / ML — permanece `PARTIAL`: faltam validação física,
avaliação do modelo com dados reais e aprovação de um modelo de negócio para
produção; o fallback estatístico não deve ser chamado de ML.

## P1 — produto e segurança ainda incompletos

### UX-002 — Estados e regressão visual das telas

**Estado:** `NOT_STARTED`  
**Evidência:** `TINO-UX-UI-P0-HARDENING.md` marca várias telas como state
completeness parcial; não há screenshot baseline automatizado.

**DoD:** Home, Estoque, Clientes, Caderneta, Mais, Fornecedores, Pedidos,
Compra, Entrada e Configurações cobrem loading, empty, error, offline,
success, confirmação, voltar/cancelar e font scale em pelo menos dois devices.

### UNDO-001 — Cobertura universal de Undo

**Estado:** `PARTIAL`  
**Falta:** compensadores seguros para preço, entrada de estoque, venda, venda
fiada, criação de cliente e demais mutações; Activity UX/timeline também não
está entregue.

**DoD:** cada operação declara policy, compensating capability, deadline,
idempotência e teste local/remoto; histórico nunca é apagado.

### SYNC-001 — Cloud sync end-to-end

**Estado:** `NOT_STARTED`  
**Evidência:** Android possui adapter e resiliência local, mas o gateway padrão
é `UnavailableSyncGateway` sem backend cloud neste workspace.

**DoD:** backend, autenticação, armazenamento, conflitos, restore, observability
centralizada e segundo-device testados com eventos duplicados/desconhecidos.

### FISCAL-EXT-001 — Fiscal externo homologado

**Estado:** `BLOCKED`  
**Evidência:** serviço responde `NOT_CONFIGURED`; certificado, pacote real,
endpoint e homologação não estão disponíveis.

**DoD:** licença aprovada, credenciais/certificado fora do repositório, adapter
real, SEFAZ/NFeWizard homologado, XML fetch/discovery e Android end-to-end.

### WHATSAPP-001 — WhatsApp end-to-end

**Estado:** `NOT_STARTED`  
**Evidência:** parser e draft local existem, mas não há webhook autenticado,
identidade externa, confirmação e lifecycle com provedor.

**DoD:** idempotência de mensagem, conversa persistida, confirmação, retirada/
entrega, pagamento e atualização de status testados em sandbox aprovado.

### FISCAL-INTAKE-001 — CameraX e extração real de DANFE

**Estado:** `PARTIAL`  
**Evidência:** `tino-fiscal-core` passa testes de contrato, qualidade,
matching e commit; as especificações de Document Intake ainda marcam
CameraX/HD capture, perspectiva, localização de tabela, runtimes de visão e
extração real como pendentes. Há documentação histórica afirmando smoke físico
passado, mas o estado atual precisa ser revalidado no device disponível.

**DoD:** CameraX preview/análise/captura, amostra DANFE real, modelo ou adapter
de visão aprovado, métricas de recall/quantidade/unidade/NCM/coluna/latência/RAM,
NeedsReview para baixa confiança e nenhuma mutação antes da confirmação.

### FISCAL-SERVICE-001 — Validar módulo TypeScript no pipeline

**Estado:** `PARTIAL`  
**Evidência:** `tino-fiscal-service` tem 10 testes Node passando e `npm run
check` passa; `npm run preflight` fica `BLOCKED` sem
`TINO_FISCAL_A1_PATH`, `TINO_FISCAL_A1_PASSWORD_ENV`, `TINO_FISCAL_CNPJ` e
`TINO_FISCAL_UF`. O serviço não está incluído no Gradle root nem conectado a
um provedor fiscal real; o runtime padrão continua `NOT_CONFIGURED`.

**DoD:** `npm test`, `npm run check` e `preflight` registrados no gate do
serviço; adapter, certificado, homologação e integração Android validados em
ambiente externo aprovado.

### KOOG-001 — Encerrar ou promover o spike Koog

**Estado:** `PARTIAL`  
**Evidência:** `koog-spike` passa sua suíte isolada, mas não é dependência do
app nem runtime oficial. Deve permanecer explicitamente como spike até decisão
de descarte ou promoção com novo gate.

**DoD:** decisão registrada: arquivar/remover do roadmap produtivo ou criar
contrato de integração, critérios de segurança e eval comparável sem duplicar
o caminho ADK.

## P2 — inteligência e operação de produção

### A3-001 — Attention Engine

**Estado:** `PARTIAL`  
**Evidência:** [TINO-EVIDENCE-INTELLIGENCE-CORE-2026-08-28.md](TINO-EVIDENCE-INTELLIGENCE-CORE-2026-08-28.md). O núcleo possui reconciliação, deduplicação, estados `ACTIVE`/`DISMISSED`/`SNOOZED`/`RESOLVED`, persistência Room 21→22→23→24, digest, métricas e dismiss/snooze pela superfície `···`.  
**DoD:** sinais determinísticos, ranking, dismiss/snooze, recorrência,
deduplicação, digest, reconciliação após sync/mutação e métricas de aceitação.

### OPS-001 — Release/readiness operacional

**Estado:** `NOT_STARTED`  
**Falta:** observabilidade centralizada, crash reporting, restore cloud,
pipeline de release, matriz de devices/densidades, migração suportada e
rollback.

### INTEL-001 — Knowledge/RAG separado de fatos

**Estado:** `PARTIAL`  
**Entregue:** `KnowledgeQueryPort` possui corpus local aprovado para ajuda e
glossário fiscal, provenance versionada (`builtin:<collection>:v1:<id>`),
política offline e fallback explícito para termos desconhecidos. O catálogo
local rejeita entradas vazias/duplicadas antes da ativação, mantém versão ativa
e anterior com rollback, e a resposta expõe versão, modo de recuperação e
latência da consulta. Testes do runtime provam que a resposta de conhecimento
não usa fatos transacionais. Ações semânticas do painel preservam o sujeito do
thought até a consulta específica, com regressão automatizada.
**Falta:** RAG produtivo com corpus externo aprovado, ingestão persistente e
autenticada, métricas agregadas de latência e operação de atualização/rollback
fora da memória do processo. A persistência local aprovada foi fechada em Room
com migration 25→26, restauração após restart e ativação/rollback transacionais;
ver [TINO-EVIDENCE-KNOWLEDGE-PERSISTENCE-2026-08-28.md](TINO-EVIDENCE-KNOWLEDGE-PERSISTENCE-2026-08-28.md). Isso não substitui a ingestão externa autenticada.

## Ordem de execução permitida

```text
G3.2 PASS_FULL
  ↓
G3.3 PASS
  ↓
G3.4 PASS
  ↓
G3.5 PASS
  ↓
G3.6 → G3.7 → G3.8 → G3.9 → G3.10 → G3.11 → G3.12
  │
  └── smoke físico Xiaomi/API 36 → PASS_FULL
  ↓
CAP-001 / VOICE-001 / UX-002 / UNDO-001, conforme gates individuais
  ↓
SYNC-001 / FISCAL-EXT-001 / WHATSAPP-001 / A3-001 / OPS-001 / INTEL-001
```

## Evidência da auditoria

- `TINO-PROJECT-STATUS.md` — seções de parcial/só no papel e matriz por área;
- `TINO-CAPABILITY-MATRIX.md` — gaps de capabilities UI-only/legacy;
- `TINO-UX-UI-P0-HARDENING.md` — state completeness parcial;
- `specs/TINO-DOCUMENT-INTAKE-001.md` e `002.md` — gates de scanner pendentes;
- `specs/TINO-FISCAL-SERVICE-006.md` e `TINO-FISCAL-SLICE-007.md` — gates
  externos pendentes;
- `app/src/main/java/com/tino/app/domain/intelligence/` — ausência dos
  contratos formais G3.6–G3.12; G3.5 agora está implementada em
  `domain/language/CorrectionLearning.kt`;
- `adb devices -l` — validação histórica sem device; o checkpoint atual também está registrado na evidência de 28/08/2026.
