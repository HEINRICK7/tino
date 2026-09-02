# TINO — Status real do projeto

**Data do levantamento:** 21/08/2026  
**Escopo:** Android, domínio comercial, voz/agente, A2UI, sincronização, fiscal, UX/UI e integrações.  
**Base do levantamento:** código existente, testes, builds, documentos em `specs/` e checkpoints na raiz.

**Atualização do checkpoint — 28/08/2026: Intelligence System core** — o contexto do painel `···` passou a ser construído pelo `IntelligenceFactsPort` com histórico temporal de estoque, recebimentos semanais, comportamento de pagamento, recorrência de clientes, compras de fornecedores e memória governada. `TinoEvidenceEngine` agora materializa evidências antes do ranking e liga insights às evidências; cada evidência preserva também os valores observados que sustentaram a conclusão e sua origem (`ROOM`, `DERIVED` ou `BUSINESS_MEMORY`), incluindo regra e features quando o insight vem de uma recomendação. O ranking considera e registra relevância contextual, impacto no negócio, horizonte temporal e momento de geração. Previsões/anomalias seguem rotuladas como estimativas. O Attention Engine vertical possui estados persistidos, deduplicação, dismiss/snooze, digest, métricas e reconciliação de sinais desaparecidos. A fatia de notificações locais persistentes agora agenda digest periódico via WorkManager, cria canal Android e respeita `POST_NOTIFICATIONS`; canal, publicação estável e cancelamento possuem testes Robolectric. A análise de vendas ganhou detecção estatística explicável com amostra mínima, baseline auditável, sazonalidade repetida por dia da semana, previsão estatística de demanda e regressão linear local com backtesting temporal, gate de erro/cobertura e fallback seguro. Pedidos de compra agora possuem data prevista, recebimento real e sinais de atraso/pontualidade no contexto de inteligência. O runtime também responde um corpus local aprovado de ajuda/glossário com provenance versionada, catálogo validado com ativação/rollback e metadados de versão, modo e latência, mantendo desconhecidos indisponíveis. Ações disparadas por pensamentos preservam contexto de produto/fornecedor até o `AgentIntent`; reposição, estoque, lista de produto e fornecedor específico não caem mais no catálogo global. `REGISTER_STOCK_ENTRY` agora atravessa o contrato agentic com preview e gate de confirmação. Suíte atual: 553 testes do app + 32 do fiscal, lint e assemble passam nos gates registrados. No Samsung SM-A042M/API 34 autorizado, G3.2 passou, o painel contextual, o caminho real de consulta do Intelligence Runtime e a consulta de fornecedores abriram sem crash, e o fallback e a inferência real do Gemma passaram com CPU/XNNPACK. O sistema completo ainda não é declarado fechado: atenção comercial real sem dados fabricados, mutações físicas controladas, avaliação do modelo em dados reais, RAG produtivo e cobertura física exaustiva continuam pendentes.

**Atualização do checkpoint — 28/08/2026: fechamento dos gates locais do device** — o Gemma real agora passa no Samsung SM-A042M usando backend CPU/XNNPACK, com carregamento em 28,7 s e resposta `GENERATED OK` em aproximadamente 29 s dentro de um orçamento finito de 90 s; o fallback isolado continua protegido. O publisher de atenção foi validado no aparelho com canal `tino-attention`, permissão concedida e notificação visível via smoke G4.2; o toque da notificação agora leva diretamente a “Avisos do TINO”. A atenção agora é recalculada imediatamente no startup e após mudanças observadas nos fatos, sem depender apenas do digest de seis horas. Nenhum dado comercial foi fabricado para obter essa evidência, então atenção comercial real e mutação física controlada permanecem pendentes.

**Atualização do checkpoint — 28/08/2026: G4.3 Intelligence Runtime no device** — o smoke somente-leitura atravessou o `IntelligenceRuntimePort` real no Samsung SM-A042M e retornou `ANSWERED` com fato `products`, analytics `lowest_stock` e resposta do Room, sem mutação. O planner determinístico passou a ser fast path para perguntas de negócio conhecidas, evitando o cold start do Gemma na consulta normal; o modelo permanece como proposta opcional para perguntas não mapeadas, com fallback seguro. O timeout global e o orçamento do prompt foram ajustados para o cold start finito observado. A avaliação de demanda agora acompanha a evidência com janelas, MAE, MAPE, cobertura e decisão do gate. Evidência: [TINO-EVIDENCE-G4.3-2026-08-28.md](TINO-EVIDENCE-G4.3-2026-08-28.md).

**Atualização do checkpoint — 28/08/2026: G4.4 boundary físico de mutação** — o Samsung SM-A042M executou com sucesso o teste instrumentado que atravessa `CommerceToolDispatcher`, Room, `MutationSafetyCoordinator` e `MutationSafeToolExecutor`: preview sem alteração, confirmação com token, persistência e bloqueio de execução direta/replay. O cenário usa banco Room em memória e não toca o banco piloto comercial. Evidência: [TINO-EVIDENCE-G4.4-MUTATION-2026-08-28.md](TINO-EVIDENCE-G4.4-MUTATION-2026-08-28.md).

**Atualização do checkpoint — 28/08/2026: G4.5 readiness de dados** — a leitura do snapshot real passou sem mutação, mas após a execução do teste instrumentado/reinstalação do APK o banco atualmente instalado do Samsung está vazio (`products=0`, `customers=0`, `stock_movements=0`). Não existe backup local do pacote comercial identificado; a avaliação de previsão em dados reais foi interrompida até restauração/importação legítima. Evidência: [TINO-EVIDENCE-G4.5-DATASET-2026-08-28.md](TINO-EVIDENCE-G4.5-DATASET-2026-08-28.md).

**Atualização do checkpoint — 28/08/2026: catálogo aprovado persistente** — o conhecimento local aprovado agora é restaurado do Room pela migration 25→26; ativação, validação e rollback sobrevivem a nova instância do adapter e são transacionais. A suíte atual tem 556 testes do app + 32 do fiscal, lint e assemble verdes. RAG externo autenticado ainda não foi declarado implementado.

**Atualização do checkpoint — 26/08/2026:** G6.1 — Predictive Replenishment
Baseline — foi implementada com heurística local explicável, persistência Room,
decisão na Home, outcomes, qualidade das features e expiração. Testes, lint,
APK e smoke no Xiaomi/API 36 passaram. A evidência está em
[TINO-EVIDENCE-G6.1-2026-08-26.md](TINO-EVIDENCE-G6.1-2026-08-26.md). G6.1 ainda
aguarda validação física específica da interação; portanto G6 completa não é
considerada `PASS_FULL`.

Este documento separa o que já possui implementação executável do que ainda é uma fundação incompleta ou apenas uma especificação. Uma funcionalidade não é considerada pronta apenas porque existe um modelo, uma tela, uma interface ou um documento descrevendo-a.

O inventário executável das lacunas, estados e critérios de validação está em
[TINO-INCOMPLETE-VALIDATION-BACKLOG.md](TINO-INCOMPLETE-VALIDATION-BACKLOG.md).
O resumo detalhado desta rodada está em
[TINO-STATUS-2026-08-20.md](TINO-STATUS-2026-08-20.md).

## Legenda

| Classificação | Significado |
|---|---|
| **PRONTO** | Existe caminho executável, integração suficiente para o escopo atual e teste/build correspondente. |
| **PARCIAL** | Existe implementação real, mas falta integração, persistência, validação física, promoção para o catálogo oficial ou acabamento. |
| **SÓ NO PAPEL** | Existe principalmente em especificação, contrato, spike ou interface; ainda não existe fluxo integrado confiável no produto. |

## Resumo executivo

O TINO já é um aplicativo Android local-first funcional para operações comerciais básicas. A base local de produtos, estoque, vendas, clientes, fiado, pagamentos, fornecedores, eventos e sincronização está implementada. Também existe uma camada agentic funcional para consultas, preparação de mutações, confirmação humana, resolução de entidades e respostas A2UI.

O que ainda impede chamar o produto de completo:

- a voz real/Gemma ainda depende de disponibilidade e configuração do aparelho;
- a sincronização REST/cloud possui adapter e contratos, mas não backend cloud produtivo conectado por padrão;
- várias capabilities existem no dispatcher legado, mas ainda não foram promovidas ao catálogo agentic canônico;
- a UX/UI ainda precisa de revisão tela a tela para chegar ao nível visual da Home;
- Activity agora possui projeção persistida em Room, migration 7→8 e participação no snapshot/restore;
- a telemetria do Intelligence Runtime possui migrations 8→9, 9→10 e 10→11, persistência Room e consulta dos últimos eventos;
- o Interaction State possui migration 11→12, porta de domínio, adapter Room,
  política de retenção, expiração e limpeza testadas;
- Undo está fechado para pagamento de fiado com compensação idempotente local/remota; outras mutações continuam sem Undo até existir compensação segura;
- WhatsApp e RAG produtivo continuam fora do produto integrado. O Intelligence Runtime agora possui uma fatia local executável com ADK Kotlin oficial conectado somente no planejamento, além do núcleo Evidence→Insight→Attention persistido; Firebase AI/LiteRT-LM continuam fora.

Validação mais recente do código:

- **556 testes unitários do app + 32 testes do módulo fiscal passaram**;
- `testDebugUnitTest` passou;
- `lintDebug` passou;
- `assembleDebug` passou;
- `assembleRelease` passou em configuração release-like, sem minificação;
- APK gerado em `app/build/outputs/apk/debug/app-debug.apk`;
- APK gerado em 20/08/2026; instalação incremental, abertura da MainActivity,
  renderização multi-componente e atualização seletiva foram validadas no
  Xiaomi/API 36.

### Incremento atual — G5 Long-Term Business Memory

- `BusinessMemoryPort`, `MemoryCandidate`, `BusinessMemoryRecord`, provenance,
  confidence e lifecycle foram implementados no domínio;
- policy rejeita fatos comerciais mutáveis: saldo, estoque, preço, Pix,
  pagamento e total continuam sendo consultados no Room comercial;
- evidências promovem candidatos, contradições demovem aprendizados e remoção
  mantém histórico não resolvível;
- Room 13→14 e `RoomBusinessMemoryRepository` persistem a memória fora do
  domínio;
- `CommerceContextMemory` restaura aliases duráveis apenas como contexto para
  interpretação;
- 326 testes, lint e APK passaram;
- Xiaomi/API 36 validou correção, `TRUSTED`, contradição, `DEMOTED`, restart,
  `REMOVED` e recarga persistente;
- G5 está em `PASS_FULL`; G6.1 — Predictive Replenishment — possui baseline
  local implementada e aguarda validação física específica; G6 amplo —
  Predictive Tools / ML — permanece parcial.

### Incremento extraordinário — G4.1 Voice Reliability & Crash Recovery

- transcript parcial/revisado não chega ao agente;
- `Committed` abre uma revisão editável antes do processamento;
- o usuário pode editar, continuar falando, cancelar ou enviar;
- correções grounded podem emitir `CorrectionEvent` para o aprendizado local;
- naquela etapa, 329 testes, lint e APK passaram;
- crash capture com logcat/exit-info foi preparado;
- o crash histórico foi classificado como SIGSEGV no
  `libllm_inference_engine_jni.so`/`nativePredictSync`;
- a inferência foi isolada no processo `:gemma`, com fallback quando o serviço
  fica indisponível;
- em uma sessão complementar no Samsung SM-A042M/API 34, startup físico,
  fallback após queda controlada e estabilidade do processo principal
  passaram; a Gemma ficou indisponível por falta do modelo; qualquer device
  autorizado pode ser usado para a validação final;
- a chamada Gemma isolada `GENERATED OK` no Xiaomi permanece evidência histórica;
- os fluxos de voz end-to-end foram comprovados no device autorizado;
- contadores de Partial/Revised/Committed e submissão ao agente já estão
  instrumentados para a validação física;
- G4.1 está em `PASS_FULL`. A consolidação física de fala longa, Review/Edit,
  Continuar falando, cancelamento, correction learning, roteamento, ProductPicker,
  clientes, estoque, financeiro e fallback está em
  [TINO-EVIDENCE-G4.1-2026-08-23-FINAL-PASS.md](TINO-EVIDENCE-G4.1-2026-08-23-FINAL-PASS.md).
  As falhas originais permanecem preservadas nas evidências históricas. M1–M8
  foram aprovados; Multi-Vertical e G6 continuam bloqueados pela sequência oficial.

**Hardening complementar — 23/08/2026:** aliases semânticos de catálogo,
clientes e reposição foram ampliados; `clientes` passou a navegar diretamente
sem consulta agentic; confirmação, seleção e Undo ganharam deadlines e
cancelamento propagado; o catálogo local de tools agora é incluído no contrato
do classificador Gemma sem expor dados crus do Room; e o estado de processamento
da Home foi reduzido a uma faixa compacta. `testDebugUnitTest`, `assembleDebug`
e `lintDebug` passaram. O APK foi instalado e aberto no device USB
`2410FPCC5G`/serial `69WOBUFENFLFGAJZ`; isso registra build/deploy, mas não fecha
os itens que exigem microfone real.

**A2UI glanceable — 23/08/2026:** o contrato visual de itens de lista passou a
preservar `icon`, `context`, `primary value`, `supporting text`, `status` e
`action` como semântica, com round-trip JSON coberto por teste. O renderer
Compose agora prioriza valor principal, labels curtos, ícone contextual,
hierarquia de cor e touch target mínimo; produtos exibem estoque antes do
preço, e reposição explicita “Estoque zerado” ou “Estoque baixo”. A regra
formal é: **“A2UI deve ser glanceable: entender primeiro, ler depois.”** A
família de primitives permanece pequena e a migração incremental começa pelo
`ReadListCard`; o renderer continua sendo a única camada responsável por
layout e responsividade.

**Catálogo customizado v1 — 23/08/2026:** a fundação do `tino.catalog.v1` foi
registrada no `TinoCustomComponentCatalog`, com contratos para Metric, Product,
Customer, Debt, InventoryAlert, Sale, Summary, QuickQuery, Confirmation,
Status e MiniChart. Props e ações passam pelo registry central; componentes
Basic não foram duplicados. A implementação visual continua incremental,
usando os renderers tipados existentes durante a migração.

**TinoUiPlanner — 23/08/2026:** a composição deixou de ser apenas uma decisão
de surface. `TinoUiPlanner` recebe resultados estruturados e contexto de device,
monta uma `TinoA2UiTree`, achata somente para componentes Basic/custom registrados
e bloqueia qualquer tipo fora do catálogo. Reposição, recebíveis, produtos,
clientes e resumo financeiro possuem composições testadas; `MiniChart` só entra
quando existe série real e espaço suficiente. Padrões não representáveis geram
`CatalogCandidate`, nunca um componente criado em runtime.

### Incremento atual — Unificação Agentic 001

Foi implementada a primeira fatia da unificação:

- `ListProductsUseCase` e `ObserveProductsUseCase`;
- `GetProductStockUseCase`;
- `GetProductPriceUseCase`;
- `ListCustomersUseCase`;
- `ListReceivablesUseCase`;
- `ListOverdueUseCase`;
- `RegisterCreditPaymentUseCase`;
- tipos explícitos para query, mutation, navigation, confirmação e `operationId`;
- handlers canônicos para as capabilities prioritárias;
- associação entre `TinoToolCatalog` e `TinoCapabilityRegistry`;
- Agent, UI de produtos e dispatcher de pagamento convergindo para os mesmos use cases.

Essa fatia não significa que toda a UI foi migrada. Venda, venda fiada,
entrada de estoque, alteração de preço e cadastro de cliente ainda possuem
caminhos diretos no ViewModel e permanecem na seção de trabalho parcial.

### Incremento atual — Intelligence Runtime P0

Foi implementada e testada a primeira vertical slice de inteligência comercial
global, sem limitar a inteligência ao fiado:

- `IntelligenceRuntimePort` tornou-se a porta única para perguntas abertas;
- `GoogleAdkRuntimeAdapter` isola a futura engine ADK e faz fallback seguro para o runtime local;
- `TinoIntelligenceToolRegistry` registra ferramentas de fatos, analytics e conhecimento com schema, versão, autorização e confirmação;
- `RoomCommerceIntelligenceFacts` lê clientes, recebíveis, eventos financeiros, produtos e movimentos de estoque por portas de domínio;
- `DeterministicBusinessAnalytics` calcula variação de períodos, atraso médio de pagamento, velocidade de saída e cobertura de estoque;
- respostas carregam plano, tool calls, fatos usados, analytics usados, confiança, status e limitações;
- `InsightCard` foi adicionado ao contrato/codec/allowlist/renderer A2UI;
- o coordenador agentic usa o runtime como fallback quando a intenção antiga não é suficiente;
- testes cobrem perguntas multi-tool, falta de dados sem percentual inventado, conhecimento indisponível, velocidade de estoque, registry e round-trip A2UI.

Limites honestos desta fatia: o ADK oficial está conectado somente como planner;
o adapter reaproveita Gemma local e cai para o runtime determinístico quando o
modelo não está disponível. Memória é process-local e o adapter RAG/knowledge
ainda está indisponível. Mutações continuam exigindo preview e confirmação pelo
caminho de capabilities existente.

### Incremento anterior — Fundação Multi-Vertical

Foi criado o contrato mínimo para o TINO continuar sendo um único aplicativo:

- `BusinessProfile` exige `CORE` e o módulo do vertical principal;
- `BusinessModule` permite composição de módulos híbridos sem criar outro APK;
- `TinoModuleRegistry` expõe o pack Retail inicial e suas capabilities/vocabulário/analytics;
- o pack usa os mesmos `TinoCapabilityId` e o mesmo Intelligence Runtime global.

Essa base foi expandida nesta execução pela integração composicional abaixo.

### Incremento atual — Contexto composicional persistido

- `OperationalPattern` e `BusinessType` foram adicionados como linguagem de domínio compatível com o legado;
- `BusinessProfile` persiste padrões operacionais e capacidades permanentes;
- migration Room 15→16 adiciona esses campos sem alterar dados comerciais;
- `DefaultBusinessContextResolver` é a fonte única de composição para módulos, capabilities, vocabulário, analytics e A2UI;
- Home, Agentic Shell e navegação passam a consumir o contexto resolvido;
- resumo da Home e Quick Queries ocultam conteúdo de módulos/capabilities inativos;
- ativações `EPHEMERAL` expiram sem modificar o perfil persistido;
- capability inativa oferece recovery `USAR UMA VEZ`, com limpeza após o ciclo;
- ativação permanente explícita fica limitada a queries/navegação; mutações não
  podem ser liberadas por esse caminho;
- o validador rejeita capabilities permanentes desconhecidas ou mutáveis, e o
  audit registra padrões e concessões de recovery;
- regressões cobrem composição por perfil, persistência e expiração de ativação.

Novos vertical packs continuam fora desta rodada.

### Incremento atual — TINO Intelligence Gate 1

O runtime passou a responder golden questions compostas que não dependem de uma
intent específica:

- Pix versus dinheiro usando resumo financeiro e breakdown determinístico;
- recebíveis com pagamento recente usando saldo + histórico de clientes;
- menor estoque usando catálogo real e ordenação por saldo;
- estoque pior/melhor que ontem usando saldo atual + movimentos das últimas 24 horas;
- comparação semanal, ranking de recebíveis e comportamento de pagamento continuam cobertos.

Cada fluxo expõe plano, tool calls, fatos, analytics, confiança e limitações no
`InsightCard`. Isso é o primeiro gate de raciocínio multi-tool local. Ainda não
é um loop ADK autônomo: o planejamento é determinístico e o engine ADK oficial
continua ausente.

### Incremento atual — TINO Intelligence Gate 2

O runtime foi decomposto antes de receber novas heurísticas:

- `DeterministicIntelligenceQueryPlanner` transforma a pergunta em um `IntelligencePlan`;
- o executor determinístico executa o plano contra facts/analytics/knowledge;
- `DeterministicClarificationPolicy` centraliza pedidos de referência ausente;
- `DeterministicGroundingComposer` garante que a resposta exponha o plano de tools executado;
- o runtime principal ficou responsável por timeout, memória e composição das portas.

Os golden scenarios agora cobrem composição de estoque baixo + queda rápida,
Pix + tendência do total recebido, recebíveis + histórico recente e cliente
com maior dívida + velocidade de pagamento. A classificação atual é
**deterministic multi-tool reasoning system**; a seleção ainda é determinística,
não um loop genérico de planejamento de ADK.

### Incremento atual — TINO Intelligence Gate 2.5

O slice de inteligência recebeu uma separação estrutural preparatória para o
ADK, preservando o comportamento determinístico e as APIs de compatibilidade:

- `planning/QueryPlanner.kt` concentra objetivos, planos e planner determinístico;
- `execution/PlanExecutor.kt` concentra a execução contra Room, analytics e knowledge;
- `execution/PlanStepHandler.kt` define o contrato/contexto e o `IntelligenceHandlerRegistry`;
- `execution/handlers/` separa os fluxos financeiro, cliente, estoque e conhecimento;
- `grounding/GroundingComposer.kt` concentra evidências e plano exposto na resposta;
- `clarification/ClarificationPolicy.kt` concentra pedidos de desambiguação;
- `DeterministicIntelligencePlanValidator` valida tools registradas, limite de etapas e bloqueio de mutação antes do executor;
- a antiga `IntelligencePlanning.kt` ficou apenas como fachada de aliases, evitando quebra dos consumidores existentes.

O contrato de plano agora possui `requiresClarification` e `confidence`, deixando
o caminho pronto para o `AdkQueryPlanner` produzir o mesmo plano validável. O
ADK continua opcional em runtime: se o modelo estiver indisponível, o planner
determinístico segue mantendo o TINO operacional.

O executor também foi modularizado: `PlanExecutor` agora apenas coordena o
contexto, o `HandlerRegistry` e o grounding. Os handlers financeiro, cliente,
estoque e conhecimento carregam os fluxos específicos por objetivo. Isso
reduz o acoplamento para a entrada do ADK e mantém o mesmo corpus de golden
flows executando pelo caminho determinístico.

### Incremento atual — TINO Intelligence Gate 3.1: ADK Planning Loop

A composição do runtime agora recebe por DI:

- `PlannerPort`;
- `IntelligencePlanValidator`;
- `IntelligencePlanExecutor`.

O `DeterministicIntelligenceQueryPlanner` e o `AdkQueryPlanner` implementam o
mesmo contrato. O composition root usa o segundo, com fallback determinístico.
O adapter oficial `GoogleAdkGemmaPlanProposal` usa `LlmAgent` e
`InMemoryRunner` do `google-adk-kotlin-core:0.6.0`, reaproveitando a porta local
de Gemma como backend de texto. O ADK recebe pergunta, contexto e catálogo
descritivo e devolve apenas JSON de proposta; ele não conhece Room, DAO,
repository, handlers ou mutações.

Cada resposta agora expõe `plannerUsed` (`adk` ou `deterministic-fallback`) e o
planner registra a rota pela `PlannerObservationPort`. Os testes cobrem plano
ADK válido, fallback, observabilidade, parsing de uma pergunta composta e
bloqueio de goal desconhecido. O validator permanece obrigatório antes do
executor; os handlers, Room, analytics e A2UI não foram alterados para receber
o ADK.

Limite atual: o modelo local Gemma ainda depende de arquivo disponível no device.
Sem modelo, o ADK falha de forma segura e o TINO segue pelo planner determinístico.
O Gate 3.2 agora registra taxa de fallback/latência e avalia o mesmo corpus
contra os dois planners; ainda falta observar essa comparação em uso real no
device e em um painel operacional.

### Incremento atual — TINO Intelligence Gate 3.2: Evaluation & Observability

O próximo slice foi implementado sem ampliar o poder de execução do ADK:

- `GoogleAdkGemmaPlanProposal` foi dividido em `AdkPromptBuilder`, `AdkModelAdapter`, `AdkPlanParser` e `AdkPlanProposal`;
- `IntelligenceTelemetryPort` registra `requestId`, planner, plano, validação, fallback, execução, latência e estágio do erro;
- `RoomIntelligenceTelemetryRepository` persiste esses eventos na tabela `intelligence_telemetry` pelas migrations `8→9`, `9→10` e `10→11`;
- cada evento distingue `sessionId`, `plannerSelected`, `plannerUsed`, motivo de fallback, grounding completeness e categorias de rejeição;
- `PlannerAbEvaluator` compara planner determinístico e ADK com o mesmo corpus versionado de perguntas compostas;
- o Gate 3.2 mede correção do plano, seleção/ordenação de tools, fallback, rejeição do validator, prontidão para grounding, erros e latência;
- telemetria não pode interromper uma operação comercial nem substituir fatos do Room.

O A/B eval atual é um avaliador de planejamento: ele não executa ferramentas e,
portanto, `groundingReady` significa plano correto e validado, não uma prova de
grounding factual em produção.

### Incremento concluído — TINO Intelligence Gate 3.3: Agent Loop

O runtime recebeu um loop agentic controlado, mantendo o ADK restrito ao
planejamento:

- `AgentRuntimePort` expõe a execução do loop sem acoplar consumidores ao ADK;
- `AgentInteraction` carrega request, limite rígido de turns e timeout;
- o ciclo formal é `PLAN → EXECUTE_READ → OBSERVE → REPLAN/CLARIFY/FINAL`;
- todo plano passa pelo `PlanValidator` antes de chegar ao único
  `PlanExecutor`;
- falha observada de ferramenta pode replanejar, mas limite esgotado retorna
  erro seguro sem mutação parcial;
- `loopId`, índice do turno, estado e decisão entram na telemetria Room pela
  migration `10→11`;
- DI registra `AgentRuntimePort`; ADK continua sem acesso a Room, DAO,
  repository, handlers ou mutações.

A G3.3 passou com 6 testes focados, 285 testes Android, 32 testes do fiscal
core, lint e APK. A G3.4, G3.5, G3.6 e G3.7 também passaram. A validação
física da G3.2 está `PASS_FULL` no Xiaomi/API 36.

### Incremento concluído — TINO Intelligence Gate 4: ADK Autonomous Loop

O runtime agora coordena um ciclo autônomo controlado, sem transferir a
segurança para o modelo:

- `AdkAgentRuntime` é a fronteira de composição do ciclo;
- `PlannerPort` pode ser ADK ou determinístico, com fallback existente;
- cada turno segue `PLAN → VALIDATE → EXECUTE → OBSERVE`;
- observações podem causar `REPLAN`, mas respeitam limites de tools, replans,
  duplicidade e timeout;
- estados terminais distinguem resposta, clarificação, confirmação,
  insuficiência, falha, unsupported e timeout;
- `PlanValidator`, `PlanExecutor`, Room, memória, A2UI e Mutation Safety não
  foram bypassados;
- harness físico no Xiaomi validou multi-tool + replan, clarificação e
  proteção de loop;
- 321 testes app, lint, assemble e instalação incremental passaram.

O Gate 4 não torna voz, RAG, sync cloud, UX/UI ou capabilities comerciais
incompletas automaticamente concluídas. Essas lacunas permanecem no backlog.

### Incremento atual — TINO Intelligence Gate 3.4: Interaction State

O contexto agentic agora atravessa uma fronteira persistível própria:

- `InteractionState` pertence ao domínio e não conhece Room nem JSON;
- `InteractionStateStore` define a porta para carregar, salvar, limpar e expirar;
- `TinoAgentSession` mantém a tela atual, até oito superfícies recentes e o
  draft da operação pendente;
- `UNTIL_RESOLVED` limita a retenção de uma operação a preview/confirmação ou
  ao TTL; `SESSION` mantém somente o contexto da sessão;
- `RoomInteractionStateStore` grava o draft serializado na tabela
  `interaction_states`, sem transformar contexto em fato comercial;
- cancelamento e expiração removem a operação pendente, mas preservam a âncora
  da tela;
- G3.4 passou com 4 testes focados, 259 testes Android, lint e APK.

### Incremento atual — TINO Intelligence Gate 3.5: Correction Learning

O aprendizado de correções foi separado da resolução de entidades e passou a
ter uma política explícita:

- `CorrectionEvent` registra provenance, escopo, sessão, origem e timestamp;
- `CorrectionLearningEngine` implementa `CANDIDATE`, `LEARNED`, `TRUSTED`,
  `DEMOTED` e `REMOVED`;
- correções consistentes promovem uma associação; confirmação reforça a
  evidência; contradição demove o alias anterior;
- remoção impede resolução sem apagar o histórico de auditoria;
- `CommerceContextMemory` usa escopo `SESSION`, impedindo vazamento para outra
  sessão ou alteração automática do catálogo global;
- `LearnedAliasMemory` permanece como facade compatível com o código existente;
- G3.5 passou com 4 testes focados, 285 testes Android, lint e APK.

### Incremento atual — TINO Intelligence Gate 3.6: Adaptive Lexicon

- `AdaptiveLexiconPort` separa o contrato adaptativo do algoritmo e do
  composition root;
- scoring lexical, fonético e contextual combina catálogo, aliases aprendidos,
  uso recente, frequência e tela atual;
- thresholds e margem de ambiguidade evitam guessing em nomes próximos;
- `EntityResolutionService` consulta o léxico adaptativo depois do caminho
  legado e sempre retorna a entidade real do catálogo/Room;
- `Maracá → Café Maratá` passa sem alterar preço, estoque ou identidade;
- G3.6 passou com 4 testes focados do léxico, integração do resolver, 285
  testes Android, lint e APK; G3.7 está liberada.

### Incremento atual — TINO Intelligence Gate 3.7: UiPlanner

- `GroundedResult` e `UiContext` mantêm o contrato de apresentação no domínio;
- `UiPlannerPort` produz decisões semânticas tipadas, sem conhecer Compose,
  renderer, Room, tools ou JSON A2UI;
- texto simples vira `TEXT`; comparação/lista/ranking vira `CREATE_SURFACE`;
- surface semântica ativa pode virar `UPDATE_SURFACE`;
- ambiguidade, input ausente, confirmação e erro têm decisões próprias;
- dados insuficientes não viram insight inventado;
- G3.7 passou com 9 testes focados, 292 testes Android, lint e APK; G3.8 e G3.9
  estão concluídas em `PASS_FULL` e G3.10 está liberada.

### Incremento atual — TINO Intelligence Gate 3.9: Component Catalog

- `TinoComponentCatalog` define seis grupos semânticos: layout, display,
  business, intelligence, interaction e operations;
- descriptors versionados declaram props, tipos e bindings permitidos;
- `TinoEffectiveComponentCatalog` combina o Core Catalog com contribuidores
  verticais sem acoplar o registry ao ADK, Room ou Compose;
- componentes desconhecidos têm fallback seguro e props não declaradas são
  rejeitadas antes do renderer;
- o SurfaceHost renderiza múltiplos componentes conhecidos e preserva os
  componentes não alterados em updates incrementais;
- G3.9 passou com 7 testes focados, 292 testes Android, lint, APK e smoke físico
  no Xiaomi/API 36: três componentes foram exibidos e somente `received` mudou
  de R$ 215,00 para R$ 300,00 na mesma `surfaceId`.

### Incremento atual — TINO Intelligence Gate 3.10: A2UI Actions → Agent Loop

- `A2uiActionEvent` representa a entrada declarativa com sessão, surface,
  componente, ação e payload;
- `A2uiActionValidator` valida existência, allowlist, schema e sessão antes do
  runtime;
- `A2uiActionRouter` diferencia ações locais de ações agentic;
- `A2uiActionRuntimeBridge` converte o evento em `IntelligenceRequest` sem
  importar Room, DAO, repository, handler ou Compose para o domínio;
- o renderer só emite o evento; nenhuma regra comercial fica no componente;
- ações A2UI conhecidas entram pelo caminho determinístico do `AgentRuntimePort`
  e não acionam Gemma local sem necessidade;
- G3.10 passou com 6 testes focados, 299 testes Android, lint, APK e smoke físico:
  `Todos` virou `Só os atrasados` na mesma `surfaceId`, sem mutação e sem crash
  no processo final.

### Incremento concluído — TINO Intelligence Gate 3.11: Mutation Safety & Confirmation

- `ProposedOperation` declara capability, risco, argumentos, fingerprint,
  expiração e idempotency key;
- `MutationSafetyCoordinator` exige token vinculado à prévia, rejeita stale state,
  replay, confirmação expirada e argumentos alterados;
- `MutationSafeToolExecutor` impede que `execute(call, true)` atravesse o gate;
- operações `PENDING`, `EXECUTING` e `COMMITTED` são persistidas no Room pela
  migration 12→13;
- `confirm_operation` A2UI atravessa `MutationConfirmationPort` sem acesso do
  renderer ao Room;
- voz e texto encaminham a confirmação emitida pela prévia até o executor único;
- 314 testes do app, lint, APK e startup físico no Xiaomi/API 36 passaram;
- o harness físico comprovou confirmação, cancelamento, replay, token cruzado,
  stale fingerprint e restauração após restart;
- G3.11 está em `PASS_FULL`; G3.12 está em `PASS_FULL`. Consulte
  [TINO-TASK-G3.12.md](TINO-TASK-G3.12.md) para a evidência física.

### Incremento atual — Contexto, Multiturno e Voz 001

Foi implementada a lapidação do contexto operacional já existente, sem criar
capabilities novas:

- memória contextual injetada como instância compartilhada, sem cópia local no coordenador;
- prioridade de referência explícita sobre tela, conversa e entidade recente;
- origem da resolução registrada (`EXPLICIT`, `SCREEN`, `CONVERSATION`, `PENDING_ACTION`);
- continuação de produto (`dele`, `e o preço`) e de cliente (`e o Chico`, timeline mensal);
- correção de quantidade, entidade e forma de pagamento antes da execução;
- confirmação/cancelamento determinísticos e confirmação sem pending action segura;
- draft multiturno com itens adicionais acumulados, preview atualizado e confirmação única;
- expiração do contexto conversacional sem apagar a âncora da tela;
- partial/revised continuam apenas na superfície de escuta; somente committed entra no pipeline;
- sessão agentic e ViewModel de voz compartilham transições de listening, understanding, success e cancel;
- ledger process-local de interações com classificação, origem de contexto e nomes de métricas operacionais.

Golden scenarios cobertos incluem adicionar itens em vários turnos, perguntar o
total do draft, corrigir quantidade/cliente/pagamento, confirmar uma vez,
cancelar sem mutação, resolver pronome com tela e recusar pronome sem
antecedente.

### Incremento atual — A2UI Refinement 001

Foi iniciada e validada a lapidação visual do A2UI sem ampliar o domínio:

- previews de pagamento, fiado, entrada e alteração de preço agora carregam dados estruturados;
- mapper não depende mais de texto livre para montar a hierarquia visual normal;
- valor principal, entidade, linhas de detalhe, ícone e ação usam uma composição consistente;
- CTA padronizado em `Cancelar` à esquerda e `Confirmar` à direita;
- Undo usa sempre `Desfazer`;
- queries de lista ricas foram promovidas a `BOTTOM_SHEET`, enquanto respostas pequenas continuam compactas;
- saldo de cliente, listas, vazio e fallback receberam hierarquia e linguagem mais curtas;
- ícones Material do Design System substituíram símbolos/emoji nos componentes refinados;
- valores monetários são renderizados como uma unidade visual e botões possuem touch target mínimo/semântica acessível;
- codec JSON preserva os campos estruturados e a versão do envelope;
- foram adicionados previews de pagamento, fiado com fonte ampliada e valores grandes;
- métricas process-local de fallback e componente desconhecido foram adicionadas.

Ainda falta a validação física dos bottom sheets, font scale e renderização no aparelho, pois o ADB está sem device detectado.

### Incremento atual — Undo Activity 001

Foi fechada a primeira vertical slice confiável de Activity, Undo e correção:

- `AgentActivityEntity`/`AgentActivityDao` e `RoomAgentActivityRepository` persistem operação, resumo estruturado, origem, estado de Undo, status e relação de compensação;
- migration explícita `MIGRATION_7_8`, índices por timestamp, operação e estado, sem destructive migration;
- Activity sobrevive à recriação do ledger e do banco e entra no `CommerceSnapshotRepository`;
- `operationId` é idempotente no ledger e a transição expirada vira `EXPIRED` sem apagar histórico;
- Undo repetido não cria segunda compensação; pagamento de fiado gera lançamento positivo e evento `credit.payment.reversed`;
- `RemoteEventApplier` aplica a reversão de pagamento de forma idempotente;
- o Undo cria uma Activity própria relacionada à Activity original;
- correção pós-execução de pagamento gera preview e executa `reversal + novo payment`, preservando o lançamento original;
- testes cobrem persistência/restart, expiração, reversão idempotente, correção e aplicação remota duplicada.

Limites honestos desta vertical slice: não há Activity dashboard novo, e preço, entrada de estoque, venda e venda fiada ainda não exibem Undo porque suas compensações completas não foram implementadas com segurança.

### Incremento atual — UX P0 001

Foi aplicada a primeira fatia executável da lapidação visual orientada pela Home:

- `TinoSectionLabel` adiciona uma hierarquia leve e compartilhada para seções internas;
- a tela `Mais` foi agrupada em `Operação`, `Meu comércio` e `TINO`, sem alterar destinos;
- a tela `Estoque` explicita a seção `Produtos` depois da busca e dos filtros;
- o detalhe do cliente mostra o saldo com destaque e mantém a abertura da caderneta junto ao resumo principal, antes dos dados cadastrais;
- o microfone continua sendo um único `TinoVoiceFab` global, circular, com parallax, pulso e estados de escuta/processamento;
- testes, lint, build e instalação física foram validados após a alteração.

Ainda falta concluir a revisão visual tela a tela das rotas secundárias, validar font scale e executar uma inspeção visual interativa no aparelho quando a camada de tela estiver disponível para automação. Nenhum fluxo comercial foi removido nesta fatia.

### Incremento atual — Offline Recovery 001

Foi implementada uma fatia de recuperação e sincronização previsível, sem adicionar backend ou capability de negócio:

- eventos deixados em `SYNCING` por morte do processo voltam para `PENDING` antes da próxima tentativa;
- o contador de pendências inclui eventos em voo, evitando uma fila invisível para a UI;
- timeout após aceitação remota pode ser repetido sem duplicar a mutation, preservando `eventId`;
- erros de autenticação, indisponibilidade temporária e rejeição permanente deixam de compartilhar o mesmo caminho de retry;
- a fila de WorkManager é best-effort: falha ao agendar sync não desfaz nem transforma em erro uma operação local já confirmada;
- tentativas, sucesso, retry, falha e eventos duplicados ignorados passam pelo `AuditLogger` sem tokens ou payloads sensíveis;
- restore valida versão, identidade dos eventos, JSON e IDs antes de limpar o estado atual;
- `RestorePolicy.REQUIRE_EMPTY` permite recusar restauração sobre aparelho preenchido de forma explícita;
- testes cobrem restart durante sync, timeout com retry idempotente, snapshot corrompido e política de restauração.

Limites honestos: o gateway cloud produtivo ainda não está configurado por padrão, não existe tela de conflito e o gate físico de modo avião depende de o aparelho expor a camada de tela/automação. O comportamento local e a fila persistida continuam testáveis no Room.

### Incremento atual — Fiscal UX 001

Foi implementada uma fatia executável da experiência de leitura de notas, mantendo o fluxo local e sem alterar estoque automaticamente:

- a câmera agora apresenta orientação e estados em linguagem humana: `Posicione a nota inteira`, `Enquadre a nota inteira`, `Afaste um pouco`, `Melhore a iluminação`, `Mantenha firme` e `Pronto`;
- a captura manual e a captura automática só são liberadas quando os gates de qualidade estão prontos, estáveis e com quadrilátero detectado;
- falhas de captura deixam de ser silenciosas e mostram recuperação explícita;
- o processamento usa `Lendo a nota…` e informa que nada foi alterado no estoque;
- a entrada da funcionalidade foi simplificada para `Ler nota`, `Escanear nota` e `Escolher uma foto`, sem expor DANFE/OCR como linguagem principal;
- a tela de resultado usa `Produtos encontrados`, pede conferência de produto, quantidade e custo, e sinaliza itens com quantidade/unidade ou confiança insuficiente;
- `FiscalFoundScreen`, `FiscalReviewScreen` e `DocumentUploadScreen` receberam cópia consistente e orientada à decisão do comerciante;
- testes unitários adicionais cobrem prioridade dos gates, condição de pronto e captura fora do enquadramento.

Limites honestos: a câmera e o XML ainda não convergem para um único review canônico na `MainActivity`; o `FiscalImportCommitService` existente continua separado do review visual, portanto esta fatia não registra entrada no estoque nem simula confirmação. A próxima fatia deve ligar documento interpretado → matching → preview → confirmação → commit idempotente, além de validar notas reais no device.

### Incremento atual — Hardening Técnico 001

Foi aplicada a primeira fatia de hardening sem mudança de comportamento comercial:

- coletores de estado da `TinoApp` passaram a ser lifecycle-aware com `collectAsStateWithLifecycle`, reduzindo trabalho ativo enquanto a Activity está em background;
- a câmera continua limitada ao ciclo de vida do Compose e o runtime de voz mantém cancelamento/teardown no ViewModel;
- foram adicionados fitness tests executáveis para impedir acesso de `domain/agent` a Room/DAO, impedir acesso do renderer A2UI ao repositório comercial e bloquear a introdução de `GlobalScope`;
- o manifesto explicita `usesCleartextTraffic=false`, mantendo a regra de HTTPS do gateway cloud;
- o README agora documenta teste, lint, build release-like e o motivo de R8 permanecer desativado até existir uma matriz de keep rules validada;
- a compilação limpa foi repetida com heap explícito de 4 GiB após o empacotamento debug exceder o heap padrão do Gradle;
- a direção de migração Room continua explícita, sem destructive migration, e a proteção de segredos permanece no Android Keystore.

Limites honestos: a composição global está em `TinoApp.kt` e as telas
secundárias ainda estão agrupadas no módulo de apresentação; a extração por
feature deve ser feita em fatias menores para não introduzir regressão. Ainda
falta uma matriz física de dois devices/font scales, screenshot regression
automatizada e validação release com R8 habilitado.

### Incremento atual — M01 Composition Root 001

Foi executada a primeira fatia do módulo de composição, com mudança de baixo
risco e comportamento preservado:

- `MainActivity.kt` foi reduzida a um host de startup: splash, orientação,
  barras do sistema, tema e instalação da composição Compose;
- `TinoApp.kt` recebeu a composição, navegação, handlers e telas que antes
  estavam no host;
- `TinoNavigation.kt` agora concentra o shell de navegação, roteamento de abas
  e seleção da tela atual;
- `TinoHome.kt` agora concentra a Home e a superfície agentic principal, sem
  mudar callbacks, estado ou regras comerciais;
- `TinoApp` ficou explícita como composição interna do módulo, sem introduzir
  acesso de Activity a DAO, repository ou regra comercial;
- um fitness test garante que `MainActivity` permaneça fina e não volte a
  concentrar mutações ou acesso direto à infraestrutura.

Validação histórica desta fatia: compilação Kotlin, 223 testes unitários do
app, lint, build debug/release e smoke de instalação no device. Esse registro
não substitui a validação física atual do APK, que está bloqueada porque o ADB
não detecta device. Limite honesto: o M01
ainda não está encerrado estruturalmente; o shell de navegação e a Home foram
separados, mas as telas secundárias continuam agrupadas no módulo de
apresentação. A próxima fatia deve separar os primeiros features restantes em
arquivos próprios, mantendo os mesmos gates.

### Incremento atual — Pilot Readiness 001

Foi criado o kit executável para iniciar uma validação controlada no balcão:

- build identificado como `0.1.0-pilot.1`, `versionCode=2`, permitindo upgrade sobre o APK anterior sem limpar o Room;
- `APP_START` registra somente build, canal, API e modelo do device, com redaction por allowlist;
- `AuditEventType` agora possui categorias mínimas para voz, intenção, mutação, sync e fiscal, prontas para instrumentação sem registrar conteúdo sensível;
- `tools/pilot-smoke.sh` instala com `adb install -r`, abre a Activity, verifica processo vivo, versão e crashes recentes;
- `pilot/TINO-PILOT-RUNBOOK.md` define escopo congelado, ordem da sessão, comandos, gates P0/P1, critérios de saída e limitações honestas;
- templates de sessão, problema e baseline do device foram adicionados em `pilot/`;
- baseline físico registrado para Xiaomi `2410FPCC5G`, Android 16/API 36, 720×1640, 320 dpi e aproximadamente 7,5 GiB de RAM.

Validação histórica executada: 223 testes do app, 32 testes fiscais, lint,
`assembleDebug`, `assembleRelease`, instalação incremental e abertura no
device. O piloto com comerciante real, utterances naturais, documentos fiscais
autorizados, conexão ruim real e upgrade entre sessões ainda precisa ser
executado; esse registro histórico não substitui o smoke físico atual.

## 1. O que está pronto

### 1.1 Aplicativo Android e arquitetura base — PRONTO

- Aplicativo Android em Kotlin com Jetpack Compose.
- Separação prática entre domínio, infraestrutura, interface adapters e UI.
- Hilt para injeção de dependências.
- Room/SQLite como armazenamento local.
- Orientação portrait-first.
- Tema visual TINO com tokens de cor, tipografia, espaçamento, tamanhos, formas e componentes compartilhados.
- APK debug compilável pelo Gradle.
- Testes unitários e testes com Robolectric/Room em partes relevantes do domínio.

Principais pontos de entrada:

- `app/src/main/java/com/tino/app/MainActivity.kt`
- `app/src/main/java/com/tino/app/core/di/AppModule.kt`
- `app/src/main/java/com/tino/app/core/database/TinoDatabase.kt`
- `app/src/main/java/com/tino/app/ui/theme/`
- `app/src/main/java/com/tino/app/ui/components/`

### 1.2 Comércio local — PRONTO

O domínio local possui operações reais, com persistência no Room:

- produtos;
- preço e unidade;
- estoque calculado por movimentos;
- venda direta;
- venda fiada por produto;
- fiado por valor;
- clientes;
- saldo e timeline de clientes;
- recebimento de pagamento do fiado;
- formas de pagamento: dinheiro, PIX, cartão e crédito;
- fornecedores;
- entrada de mercadoria;
- compras e itens de compra;
- eventos de domínio e outbox.

O `CommerceRepository` é a fonte efetiva das mutações locais. As regras financeiras ficam no domínio, e a UI não deve inventar saldo, preço ou estoque.

Arquivos principais:

- `app/src/main/java/com/tino/app/domain/commerce/CommerceRepository.kt`
- `app/src/main/java/com/tino/app/domain/commerce/CommerceRules.kt`
- `app/src/main/java/com/tino/app/domain/commerce/TemporalCredit.kt`
- `app/src/main/java/com/tino/app/core/database/Entities.kt`
- `app/src/main/java/com/tino/app/core/database/Daos.kt`

### 1.3 Fluxos comerciais de UI — PRONTO no escopo atual

Existem telas e navegação para:

- Home;
- produtos;
- detalhe e edição de produto;
- ajuste de estoque;
- novo produto;
- venda rápida;
- recebimento de venda;
- lista de clientes;
- detalhe do cliente;
- conta/caderneta do cliente;
- lista de fiado;
- recebimento de pagamento;
- seleção de cliente;
- confirmação de fiado;
- fornecedores;
- entrada de mercadoria;
- pedidos, separação e entrega;
- resumo, insights e notificações;
- configurações;
- modo offline;
- restauração do comércio;
- onboarding/primeiro acesso.

Os fluxos locais têm validações de quantidade, preço, estoque, cliente selecionado e estados vazios reais. O primeiro acesso grava o perfil do comércio no Room e direciona perfis existentes para a Home.

### 1.4 Voz contextual e comandos de comércio — PRONTO/PARCIAL conforme o runtime

O pipeline de linguagem já existe:

```text
texto ou transcrição
    ↓
Fast Intent Router / Gemma adapter
    ↓
intent estruturada
    ↓
resolução de entidade
    ↓
query ou preview
    ↓
confirmação humana, quando necessário
    ↓
domínio local
    ↓
A2UI
```

Comportamentos implementados:

- roteamento global a partir da Home e de telas internas;
- consultas de vendas/resumo financeiro;
- consulta de saldo de cliente;
- consulta da timeline/caderneta;
- leitura de produtos, preço e estoque por caminhos existentes;
- preparação de venda fiada;
- preparação de pagamento de fiado;
- alteração de preço com preview e confirmação;
- entrada de mercadoria com preview e confirmação;
- venda e venda fiada por voz/texto nos caminhos suportados;
- seleção de cliente por voz;
- confirmação e cancelamento por voz;
- correção de campos e continuidade de contexto;
- resolução de cliente, produto e fornecedor com estados `Resolved`, `NotFound` e `Ambiguous`;
- rejeição de referências ambíguas sem escolher silenciosamente o primeiro resultado;
- fallback determinístico quando o Gemma não está disponível;
- proteção contra mutação antes da confirmação.

Arquivos principais:

- `app/src/main/java/com/tino/app/domain/language/`
- `app/src/main/java/com/tino/app/domain/agent/AgentQuery.kt`
- `app/src/main/java/com/tino/app/domain/agent/AgenticTextQueryCoordinator.kt`
- `app/src/main/java/com/tino/app/domain/voice/ToolCalling.kt`
- `app/src/main/java/com/tino/app/domain/voice/GlobalCommandRouter.kt`
- `app/src/main/java/com/tino/app/feature/voice/AgenticVoiceViewModel.kt`

### 1.5 Agentic Shell — PRONTO como primeira versão

A fundação do shell global já está implementada:

- sessão agentic global;
- registro do contexto da tela atual;
- entidade primária ativa, como cliente ou produto;
- capabilities disponíveis por contexto;
- estado de voz e ação pendente;
- slots coletados e slots ausentes;
- memória de contexto para perguntas de continuação;
- resolução de pronomes como “dele”, “dela”, “ele” e “ela” usando o contexto atual;
- FAB/superfície global acessível a partir das telas internas;
- Home com superfície agentic própria;
- confirmação humana antes de mutações.

Arquivos principais:

- `app/src/main/java/com/tino/app/domain/agent/TinoAgentSession.kt`
- `app/src/main/java/com/tino/app/domain/agent/ScreenContextRegistry.kt`
- `app/src/main/java/com/tino/app/domain/agent/TinoCapabilityRegistry.kt`
- `app/src/main/java/com/tino/app/feature/voice/TinoAgentSessionViewModel.kt`

### 1.6 A2UI — protocolo, renderer e fallback — PRONTO como fundação

Já existe:

- envelope `tino.a2ui` versionado;
- codec JSON próprio;
- allowlist de componentes;
- fallback seguro para componente desconhecido ou versão inválida;
- componentes financeiros, escolha de entidade, confirmação, saldo, timeline e listas;
- renderer Compose fechado para tipos conhecidos;
- mappers de domínio para A2UI;
- componentes semânticos registrados para resumo financeiro, preview de pagamento, preview de crédito, entrada de estoque, alteração de preço, estoque, fornecedor, seletor de esclarecimento, sucesso e recuperação de erro;
- política que associa leitura a overlay e mutação a bottom sheet;
- ação de sucesso com referência da operação e da atividade para Undo.
- contrato semântico explícito para resultados: listas em `ReadListCard`,
  escolhas em `EntityChoice`, mutações em `ActionConfirmation` e erros
  recuperáveis/timeout em `ErrorStatusCard` com retry; a Home não desenha mais
  a resposta primária de erro por `TinoCard` ad hoc.

Arquivos principais:

- `app/src/main/java/com/tino/app/interfaceadapter/a2ui/A2uiProtocol.kt`
- `app/src/main/java/com/tino/app/interfaceadapter/a2ui/A2uiJsonCodec.kt`
- `app/src/main/java/com/tino/app/interfaceadapter/a2ui/A2uiSemanticRegistry.kt`
- `app/src/main/java/com/tino/app/interfaceadapter/a2ui/A2uiPresentationPolicy.kt`
- `app/src/main/java/com/tino/app/ui/a2ui/TinoA2UiRenderer.kt`

### 1.7 Atividade operacional e Undo — PRONTO/PARCIAL; cobertura limitada por capability

O projeto já possui:

- ledger separado do histórico de conversa;
- registro de capability, resumo, origem, operação e estado de Undo;
- estados `AVAILABLE`, `REQUESTED`, `COMPLETED`, `EXPIRED` e `FAILED`;
- política explícita de compensação;
- planner que não apaga o lançamento original;
- botão “DESFAZER” para operações elegíveis na superfície A2UI;
- compensação real de pagamento de fiado por novo lançamento positivo;
- evento de domínio `credit.payment.reversed`.

Implementação adicional validada:

- projeção local Room versionada e restaurável;
- resumo estruturado para pagamentos, estoque, preço e fallback genérico;
- idempotência por `operationId` e relação `compensatesActivityId`;
- correção de pagamento pós-execução sem edição destrutiva;
- aplicação remota idempotente do evento reverso.

Arquivos principais:

- `app/src/main/java/com/tino/app/domain/agent/AgentActivity.kt`
- `app/src/main/java/com/tino/app/domain/agent/AgentUndoService.kt`
- `app/src/main/java/com/tino/app/domain/commerce/CommerceRepository.kt`

### 1.8 Fiscal local e intake de documentos — PRONTO/PARCIAL

O núcleo fiscal possui implementação executável para:

- parser XML de NF-e;
- modelo canônico fiscal;
- matching de produtos;
- empacotamento do intake;
- plano de commit;
- separação entre nota encontrada, documento revisado e mercadoria confirmada;
- scanner de documento no Android;
- detecção/retificação de perspectiva;
- OCR de DANFE via ML Kit;
- revisão antes do commit de estoque;
- testes do parser, matching, packaging, importação e commit.

Módulos:

- `tino-fiscal-core/`
- `app/src/main/java/com/tino/app/feature/fiscal/`
- `app/src/main/java/com/tino/app/domain/fiscal/`

### 1.9 Auditoria, segurança e sync local — PRONTO/PARCIAL

Implementado no Android:

- UUIDv7;
- identidade local;
- eventos de domínio;
- outbox;
- cursor de pull;
- idempotência por evento;
- aplicador remoto de eventos conhecidos;
- snapshot e restauração local;
- retry e backoff;
- timeout;
- limite de resposta;
- request-id;
- circuit breaker process-local;
- auditoria com redaction;
- armazenamento de tokens usando Android Keystore;
- gateway em memória para testes.

Arquivos principais:

- `app/src/main/java/com/tino/app/core/sync/`
- `app/src/main/java/com/tino/app/core/observability/AuditLogging.kt`
- `app/src/main/java/com/tino/app/core/security/SecureTokenStore.kt`

## 2. O que está pela metade

### 2.1 Voz real no aparelho — PARCIAL

O código possui portas e adapters para transcrição ao vivo, Gemma e reconhecimento Android. Porém o runtime real ainda depende do aparelho, permissões, modelo e configuração correta.

Quando o runtime não está disponível, o app mostra indisponibilidade honesta ou usa fallback determinístico. Isso significa:

- o pipeline de linguagem está testado;
- o caminho de microfone existe;
- a sequência completa falando no aparelho ainda precisa ser validada como gate físico;
- não se deve declarar que a experiência de voz real está concluída apenas porque os testes unitários passam.

Arquivos relacionados:

- `app/src/main/java/com/tino/app/core/speech/AndroidSpeechRecognizerRuntime.kt`
- `app/src/main/java/com/tino/app/core/speech/GemmaTranscriber.kt`
- `app/src/main/java/com/tino/app/core/speech/MediaPipeGemmaTextInference.kt`
- `app/src/main/java/com/tino/app/core/speech/GemmaModelStore.kt`

### 2.2 Catálogo agentic canônico — PARCIAL

O `TinoToolCatalog` já define ferramentas para resumo, produtos, estoque, preço, clientes, recebíveis, atrasados, saldo, timeline, crédito e pagamento.

Contudo, a matriz oficial ainda registra uma diferença entre:

- capabilities canônicas publicadas pelo Agent Boundary;
- capabilities presentes no `AgentCapability`;
- ferramentas existentes no `CommerceToolDispatcher` legado;
- telas que consultam diretamente repositories.

Os gaps principais são:

- promover todas as leituras de produtos, estoque, preço, clientes, recebíveis, atrasados e fornecedores para o mesmo caminho canônico;
- promover pagamento de fiado para o catálogo agentic completo;
- eliminar novas regras no dispatcher legado;
- garantir para cada capability o contrato de fonte da verdade, A2UI, offline, ambiguidade e empty state.

### 2.3 A2UI e superfície de atenção — PARCIAL

O protocolo e o renderer existem, mas nem todo tipo semântico registrado possui um componente visual dedicado. Alguns tipos ainda reutilizam cards genéricos ou apenas estão na allowlist.

Também faltam:

- componente visual completo para cada preview semântico;
- histórico operacional persistido;
- timeline de atividade acessível ao usuário;
- Undo para alteração de preço, estoque, venda e demais mutações;
- aplicação remota de todos os eventos de compensação, incluindo `credit.payment.reversed`;
- política de expiração configurada por operação, em vez de somente contrato base;
- cobertura visual automatizada dos estados A2UI.

### 2.4 Undo — PARCIAL

O fluxo de pagamento de fiado já possui compensação local. Ainda não é correto afirmar que existe Undo universal do TINO.

Faltam políticas e compensadores para:

- alteração de preço;
- entrada de estoque;
- venda direta;
- venda fiada;
- criação de cliente;
- outras mutações comerciais.

O princípio correto continua sendo compensar com novo evento, nunca apagar silenciosamente o evento original.

### 2.5 Sincronização cloud — PARCIAL

O Android possui `RestSyncGateway`, contratos, retry e gateway de teste. Porém o gateway padrão permanece indisponível quando a URL de sync não está configurada, e não existe backend cloud produtivo conectado neste workspace.

Portanto, está pronto:

- o contrato;
- o adapter Android;
- a resiliência local;
- o teste em memória.

Ainda não está pronto:

- servidor cloud de produção;
- autenticação real end-to-end;
- armazenamento cloud;
- observabilidade cloud;
- reconciliação de conflitos em produção;
- operação de restore cloud validada.

### 2.6 UX/UI — PARCIAL

A direção visual da Home e o design system estão aplicados. A navegação, os componentes e os fluxos principais já têm consistência suficiente para teste.

Ainda falta uma rodada P0 completa nas telas secundárias:

- venda rápida;
- recebimento e fiado;
- produtos;
- fornecedores;
- pedidos e entrega;
- configurações;
- offline, erro, sucesso e confirmação.

Débitos visuais conhecidos:

- `TinoApp.kt` ainda concentra muitos fluxos e telas;
- algumas telas ainda usam layouts genéricos;
- não há baseline de screenshot automatizado;
- estados de erro/offline nem sempre têm o mesmo acabamento da Home;
- ainda existem avisos de API Android depreciada para barras do sistema;
- a validação visual continua majoritariamente manual.

### 2.7 Serviço fiscal externo — PARCIAL

`tino-fiscal-service/` possui servidor TypeScript, contratos e testes, mas o adapter real de NFeWizard/certificado fica desligado por padrão.

Está pronto para desenvolvimento controlado:

- health check;
- contrato HTTP;
- handoff de XML;
- retry e validações;
- modo `NOT_CONFIGURED` seguro.

Ainda falta:

- adapter externo aprovado;
- certificado de homologação real fora do repositório;
- execução controlada em homologação;
- integração completa com o Android.

## 3. O que está só no papel ou em spike

### 3.1 Backend cloud completo — SÓ NO PAPEL/CONTRATO

Os documentos B002 a B008 descrevem contratos de sync, projeções, intake, WhatsApp, orquestrador, inteligência e hardening. Isso não equivale a um backend cloud produtivo executando esses contratos.

Os documentos principais são:

- `specs/TINO-BACKEND-002-sync-contracts.md`
- `specs/TINO-BACKEND-003-commerce-projections.md`
- `specs/TINO-BACKEND-004-fiscal-intake.md`
- `specs/TINO-BACKEND-005-whatsapp-orders.md`
- `specs/TINO-BACKEND-006-tino-orchestrator.md`
- `specs/TINO-BACKEND-007-intelligence-data.md`
- `specs/TINO-BACKEND-008-production-hardening.md`

### 3.2 WhatsApp de ponta a ponta — SÓ NO PAPEL, com fundação parcial

Existem modelos de pedido, parser de mensagens e `OrderDraftService` local. Porém não existe gateway WhatsApp conectado, webhook produtivo, identidade externa persistida nem lifecycle completo de pedido operando com provedor real.

Ainda não está entregue:

- webhook autenticado;
- idempotência de mensagens no provedor;
- conversa persistida;
- confirmação real do cliente;
- integração de retirada/entrega com canal;
- atualização de status enviada ao cliente;
- pagamento e fechamento operacional pelo canal.

### 3.3 Intelligence Data e Attention Engine — PARCIAL / local + vertical slice A3

Existe um motor local de evidências (`TinoEvidenceEngine`) integrado ao `IntelligenceRuntime`, com fatos Room, analytics determinísticos, memória governada, histórico de clientes/fornecedores e Attention Engine persistente. Isso é uma base executável com cobertura comercial ampliada, não o sistema completo de inteligência operacional descrito nos documentos.

Ainda não existe como produto integrado:

- ML de negócio aprovado além das análises estatísticas locais já integradas;
- cobertura física exaustiva de todas as ações e estados do painel;
- concessão e validação física da permissão/canal de notificações no APK final;
- camada de dados cloud para inteligência;
- aprendizagem de novos padrões a partir de eventos reais além da memória governada e das regras auditáveis.

O vertical slice atual já possui contexto temporal do `IntelligenceFactsPort`,
ranking de insights, memória governada alimentando o contexto, sinais de
clientes/fornecedores, histórico de entrega prevista/real com fluxo local de
pedido e recebimento, Attention Engine local com persistência, digest,
métricas, deduplicação, reconciliação, dismiss/snooze e publicação periódica
de notificações locais, além de uma detecção estatística explicável de picos
diários de vendas em
[TINO-EVIDENCE-INTELLIGENCE-CORE-2026-08-28.md](TINO-EVIDENCE-INTELLIGENCE-CORE-2026-08-28.md).

### 3.4 Agentic UX A3 — Attention Engine — PARCIAL / vertical slice local

O VS-A3 possui um vertical slice integrado no painel global `···`, com sinais determinísticos, ranking, persistência, dismiss/snooze, digest, métricas, reconciliação e notificação operacional local periódica. O canal, a permissão e a publicação Android passaram no smoke físico G4.2 com atenção sintética isolada; ainda falta cobertura física exaustiva e atenção comercial real sem dados fabricados.

Não considerar pronto:

- central de pendências dedicada por entidade;
- validação física da notificação com atenção comercial real;
- cobertura física exaustiva de todas as ações e estados;
- integração de sinais com sync cloud produtivo.

### 3.5 ADK, Koog e RAG — ADK PARCIAL / Gate 3.2

O projeto possui `koog-spike/` e documentos avaliando orquestração. Isso não significa que ADK, Koog ou RAG sejam o runtime oficial do TINO.

No estado atual:

- o app usa adapters locais e roteamento determinístico como fallback;
- Gemma fica atrás de portas;
- `google-adk-kotlin-core:0.6.0` está no APK e `GoogleAdkGemmaPlanProposal` usa `LlmAgent` + `InMemoryRunner` somente para propor `ExecutionPlan`;
- o ADK não recebe tools executáveis, Room, DAO, repository ou handlers;
- o modelo local pode ficar indisponível; nesse caso `deterministic-fallback` mantém o TINO operacional;
- não existe RAG operacional para fatos comerciais;
- RAG não deve substituir consultas ao Room/projeções.

### 3.6 Produção completa — SÓ NO PAPEL

Continuam fora de um release produtivo completo:

- backend cloud provisionado;
- autenticação e gestão de sessão end-to-end;
- observabilidade centralizada;
- crash reporting/monitoramento operacional conectado;
- restore cloud real;
- pipeline de release;
- testes em múltiplos devices e densidades;
- baseline de regressão visual;
- política operacional de migração e suporte.

## 4. Matriz consolidada por área

| Área | Estado | O que existe | Principal pendência |
|---|---|---|---|
| Android/Compose | **PRONTO** | App compilável, navegação, tema e componentes | Refatoração por feature e acabamento global |
| Room/SQLite | **PRONTO** | Dados comerciais e migrações locais | Mais cobertura de migração/restore em cenários reais |
| Comércio local | **PRONTO** | Produtos, estoque, vendas, clientes, fiado, pagamentos e fornecedores | Promover todas as operações ao catálogo agentic |
| Voz contextual | **PARCIAL** | Parser, routers, adapters, confirmação e contexto | Gate físico de microfone/Gemma |
| Agentic Shell | **PRONTO** | Sessão global, contexto, slots e FAB | Persistência e atenção global |
| A2UI | **PRONTO/PARCIAL** | Envelope, codec, allowlist, renderer e mappers | Componentes semânticos dedicados e cobertura completa |
| Activity/Undo | **PRONTO/PARCIAL** | Activity persistida, restart/restore, Undo e correção idempotentes para pagamento de fiado | Activity UX dedicada e compensadores seguros para as demais mutações |
| Sync Android | **PRONTO/PARCIAL** | Outbox, eventos, REST adapter, retry e circuit breaker | Backend cloud e operação end-to-end |
| Fiscal local | **PRONTO** | XML, matching, scanner, OCR, revisão e commit | Provedor externo de homologação/produção |
| Fiscal externo | **PARCIAL** | Serviço TypeScript e contrato seguro | Adapter real, certificado e integração |
| UX/UI | **PARCIAL** | Home e design system aplicados | Revisão P0 de todas as telas e screenshots |
| WhatsApp | **SÓ NO PAPEL/PARCIAL** | Parser e draft local | Gateway, webhook, confirmação e lifecycle real |
| Intelligence | **PARCIAL / G3.2–G5 PASS_FULL / G4.1 PASS_FULL / M1–M8 PASS** | Runtime local, fatos Room, planner ADK, Agent Loop, memória working/session/business, A2UI incremental, mutation safety, Gemma isolado/fallback, commit gate de voz, Shared Agent State, Agent Progress Runtime, Agentic Streaming, HITL, Interrupt/Correction, Tino Presence, Full Runtime Integration, núcleo Evidence→Insight→Attention, estatística local explicável, previsão estatística e por regressão local, conhecimento local aprovado com catálogo validado/versionado e rollback, entrega de fornecedores e notificações locais periódicas | multi-store, retomada complexa, validação do modelo de negócio em dados reais, RAG externo produtivo e Multi-Vertical Runtime |
| Attention Engine A3 | **PARCIAL / vertical slice local** | Sinais, ranking, persistência Room, digest, métricas, dismiss/snooze, reconciliação e notificações locais integrados ao painel e às telas de avisos; canal/permissão/publicação passaram no smoke físico G4.2 | Central dedicada por entidade, atenção comercial real no device e integração cloud |
| ADK/Koog/RAG | **PARCIAL / SPIKE** | Adapter/port ADK isolado, Koog spike e boundary de knowledge, catálogo local aprovado validado/versionado com rollback | Engine ADK oficial, ingestão RAG externa persistente/autenticada e integração produtiva |

## 5. Próxima ordem recomendada

Para reduzir risco e aumentar o que é demonstrável:

1. Fechar G4.1 no device: fala longa, review/edição, continuar falando,
   fallback e captura de crash; depois consolidar os fluxos multiturno sobre G3.12 + G5, mantendo
   o device/API 36 como baseline físico.
2. Seguir a ordem e os DoD do
   [backlog de incompletos](TINO-INCOMPLETE-VALIDATION-BACKLOG.md).
3. Exercitar o fluxo completo de voz real no aparelho: ouvir, interpretar, preview, confirmar, mutar e cancelar.
4. Tornar o escopo `default-store` da Business Memory configurável por loja/tenant.
5. Criar Activity UX simples a partir da projeção persistida.
6. Promover as capabilities legadas prioritárias para o catálogo canônico:
   - `LIST_PRODUCTS`;
   - `GET_PRODUCT_STOCK`;
   - `GET_PRODUCT_PRICE`;
   - `LIST_RECEIVABLES`;
   - `LIST_OVERDUE`;
   - `REGISTER_CREDIT_PAYMENT`.
7. Fechar a rodada UX/UI P0 tela por tela.
8. Acompanhar os resultados do eval A/B no device e promover somente ganhos comprovados do ADK.
9. Evoluir o Attention Engine A3 local para notificações operacionais e cobertura multi-vertical somente após os gates do vertical slice.
10. Tratar WhatsApp, backend cloud, memória persistente e RAG como milestones separados, não como funcionalidades já entregues.

## 6. Gates antes de chamar o TINO de produto completo

### Gate técnico

- `testDebugUnitTest` verde;
- `lintDebug` verde;
- `assembleDebug` verde;
- smoke test em device real;
- nenhum crash fatal nos fluxos principais.

### Gate de produto

- nenhuma resposta factual sem fonte real;
- nenhuma mutação sem confirmação;
- entidade ambígua exige esclarecimento;
- estados vazio, offline, erro e sucesso tratados;
- Undo não apaga histórico;
- dados permanecem locais mesmo sem cloud.

### Gate de integração

- sync cloud real testado;
- restore testado em segundo device;
- eventos novos aplicados remotamente;
- fiscal externo em homologação;
- voz real exercitada no aparelho;
- WhatsApp, se incluído no release, testado com idempotência.

### Gate de UX/UI

- Home, Produtos, Clientes, Caderneta e Mais com a mesma linguagem;
- microfone global consistente onde previsto;
- navegação de volta e cancelamento previsível;
- nenhuma tela secundária com aparência de placeholder;
- screenshot/regressão visual em pelo menos dois tamanhos de aparelho.

## Conclusão

O TINO não é apenas uma ideia: já existe um produto Android local-first funcional, com domínio comercial, UI, voz estruturada, A2UI, sync resiliente e núcleo fiscal executáveis.

Também não é correto dizer que o sistema está completo. A parte mais madura hoje é o comércio local Android e a fundação do Agentic Shell. As maiores lacunas são runtime de voz real, cloud, persistência/sync da atividade, cobertura universal de Undo, acabamento de UX/UI, WhatsApp, inteligência estatística/ML, notificações operacionais e cobertura multi-vertical.

O critério de verdade deste documento é o mesmo do projeto: código integrado e testado vale como implementação; contrato, interface, spike ou especificação isolada não vale como funcionalidade pronta.
