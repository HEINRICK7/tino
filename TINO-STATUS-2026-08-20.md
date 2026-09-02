# TINO — Status Consolidado de Desenvolvimento

**Data:** 21/08/2026  
**Checkpoint:** Gate 4.1 — Voice Reliability & Crash Recovery
**Status:** `PASS_AUTOMATED_PENDING_DEVICE`  
**G3.2:** `PASS_FULL`  
**G3.3:** `PASS`  
**G3.4:** `PASS`  
**G3.6:** `PASS`  
**G3.7:** `PASS`  
**G3.8:** `PASS_FULL`  
**G3.9:** `PASS_FULL`  
**G3.10:** `PASS_FULL`  
**G3.11:** `PASS_FULL`
**G3.12:** `PASS_FULL`
**G4:** `PASS_FULL`
**G5:** `PASS_FULL`
**G4.1:** `PASS_AUTOMATED_PENDING_DEVICE`

## Resumo executivo

Hoje o TINO avançou de um adapter ADK funcional, porém pouco observável, para
uma fundação de planejamento mensurável e persistente. O ADK continua propondo;
o TINO valida e executa; Room continua sendo a fonte de verdade.

O código, os testes automatizados, o lint e o APK estão verdes para G4.1.
Nesta rodada a suíte do app chegou a 329 testes; a proteção de transcript e a
revisão editável passaram. A validação física e o diagnóstico do crash
continuam pendentes porque não há device autorizado no ADB desta sessão.
O device Xiaomi 2410FPCC5G/API 36 abriu o APK incrementalmente, reabriu o Room,
renderizou uma surface com múltiplos componentes semânticos, recebeu um evento
de Choice, devolveu-o ao Agent Runtime e atualizou somente o binding do filtro
na mesma surface sem crash no processo final.

## Implementado hoje

### Adapter ADK

`GoogleAdkGemmaPlanProposal` foi separado em:

- `AdkPromptBuilder` — instrução e contexto enviados ao ADK;
- `AdkModelAdapter` — ponte entre o contrato oficial `Model` e Gemma local;
- `AdkPlanParser` — JSON para `IntelligencePlan`;
- `AdkPlanProposal` — `LlmAgent`/`InMemoryRunner` e proposta de plano.

O ADK não acessa Room, DAO, repository, handler ou mutação.

### Gate 3.9 — TINO Component Catalog

Foi implementado o catálogo semântico que limita o que uma surface agentic
pode declarar sem permitir que o agente invente layout ou código de UI:

- seis grupos versionados: Layout, Display, Business, Intelligence,
  Interaction e Operations;
- descriptors com schema de props, tipos e bindings permitidos;
- allowlist central com fallback seguro para componente desconhecido;
- `TinoEffectiveComponentCatalog` aceita contribuidores verticais sem acoplar
  Core ao ADK, Room ou Compose;
- `SurfaceHost` renderiza múltiplos componentes conhecidos e preserva os
  componentes não alterados durante `UpdateDataModel`.

A surface física de validação exibiu `Money`, `Comparison` e `Evidence`; o
update mudou apenas `received` de R$ 215,00 para R$ 300,00 na mesma `surfaceId`.

### Gate 3.10 — A2UI Actions → Agent Loop

O circuito de entrada declarativa foi conectado sem permitir execução direta no
renderer:

- `A2uiActionEvent` carrega surface, componente, ação, payload e sessão;
- `A2uiActionValidator` verifica existência, allowlist, schema e sessão;
- `A2uiActionRouter` separa ações locais de ações que retornam ao agente;
- `A2uiActionRuntimeBridge` transforma a ação em `IntelligenceRequest` e chama
  `AgentRuntimePort`;
- ações A2UI conhecidas percorrem o planner determinístico dentro do runtime,
  sem iniciar Gemma local desnecessariamente;
- o device validou `Só os atrasados`: o evento foi aceito e a mesma surface
  mudou de `Todos` para `Só os atrasados`.

O primeiro smoke revelou uma falha nativa do caminho Gemma quando uma ação UI
conhecida era enviada ao modelo. A seleção determinística foi adicionada, o
teste de regressão passou e o smoke final permaneceu estável.

### Telemetria persistente

Foi criada a `IntelligenceTelemetryPort` com implementação Room. Cada request
pode registrar:

- `requestId` e `sessionId`;
- planner selecionado e planner usado;
- motivo de fallback;
- plano, quantidade e ordem de steps;
- resultado da validação;
- rejeição por tool desconhecida, argumento, policy ou limite;
- resultado da execução;
- grounding completeness;
- latência total e de planejamento;
- estágio do erro e timestamp.

A telemetria não impede a operação principal e não é fonte de fatos comerciais.

### Persistência e avaliação

- tabela `intelligence_telemetry` adicionada ao Room;
- migrations `8→9`, `9→10`, `10→11` e `11→12` criadas;
- versão atual do banco: `14`;
- `PlannerAbEvaluator` criado com corpus de quatro perguntas compostas;
- comparação de objetivo, seleção/ordem de tools, fallback, validação,
  grounding readiness, erros e latência;
- testes de persistência, runtime, rejeição antes do executor e fallback;
- backlog de incompletos criado em
  [TINO-INCOMPLETE-VALIDATION-BACKLOG.md](TINO-INCOMPLETE-VALIDATION-BACKLOG.md).

### G3.3 — AgentRuntimePort + Agent Loop

Foi implementado o primeiro loop agentic controlado do TINO em
`domain/intelligence/agent/AgentRuntime.kt`:

- `AgentRuntimePort`, `AgentInteraction`, `AgentTurnResult` e decisões formais;
- estados `PLAN`, `EXECUTE_READ`, `OBSERVE`, `REPLAN`, `CLARIFY`,
  `REQUEST_CONFIRMATION` e `FINAL`;
- cada turno planeja, valida, executa somente por `PlanExecutor`, observa e
  decide entre finalizar, esclarecer ou replanejar;
- limite rígido de turns e timeout cancelável;
- plano inválido é rejeitado antes do executor;
- falha de ferramenta gera nova tentativa controlada; esgotamento retorna erro
  explícito sem mutação parcial;
- telemetria persistente registra `loopId`, turno, estado e decisão;
- DI expõe `AgentRuntimePort` sem conceder ao ADK acesso a Room, DAO ou handler;
- migration `10→11` persiste os novos campos do loop; `11→12` persiste o
  Interaction State.

### Gate 4 — ADK Autonomous Loop

O loop agentic foi fortalecido e ganhou uma composição de produção explícita:

- `AdkAgentRuntime` coordena o ciclo sem expor ADK ao domínio comercial;
- `AgentLoopLimits` limita tools, replans, chamadas duplicadas e loops;
- `AgentTerminalState` distingue resposta, clarificação, confirmação,
  insuficiência, falha, unsupported e timeout;
- replanejamento usa a observação do executor e retorna ao mesmo `PlannerPort`;
- o validator continua obrigatório e o executor continua sendo o único caminho
  de execução;
- o device validou multi-tool + replan, clarificação e proteção de loop com
  dados fake read-only;
- evidências detalhadas em
  [TINO-TASK-G4.md](TINO-TASK-G4.md) e
  [TINO-EVIDENCE-G4-2026-08-20.md](TINO-EVIDENCE-G4-2026-08-20.md).

### Gate 5 — Long-Term Business Memory

A memória persistente de negócio foi implementada como uma extensão governada,
sem substituir o Room comercial como fonte de verdade:

- `BusinessMemoryPort`, candidatos, records, provenance, confidence e
  lifecycle tipados no domínio;
- policy bloqueia fatos transacionais como saldo, estoque, preço, Pix,
  pagamento e total;
- evidências promovem `CANDIDATE → LEARNED → TRUSTED`;
- contradição produz `DEMOTED` e remoção produz `REMOVED`, preservando o
  histórico e impedindo resolução;
- Room 13→14 persiste os registros e o adapter permanece fora do domínio;
- `CommerceContextMemory` restaura aliases duráveis sem misturar memória com
  dados comerciais atuais;
- device comprovou correção, promoção, contradição, restart, remoção e
  recarga persistente;
- evidências em [TINO-TASK-G5.md](TINO-TASK-G5.md) e
  [TINO-EVIDENCE-G5-2026-08-20.md](TINO-EVIDENCE-G5-2026-08-20.md).

### Gate 4.1 — Voice Reliability & Crash Recovery

O roadmap foi pausado para fechar a confiabilidade do caminho principal de voz:

- `TranscriptCommitGate` impede que `Partial`/`Revised` cheguem ao Agent Runtime;
- `Committed` entra em `TranscriptReview` antes de qualquer consulta;
- a UI oferece editar, continuar falando, cancelar e enviar;
- edição corrigida é usada imediatamente e pode gerar `CorrectionEvent` quando
  a referência de produto/cliente fica grounded sem ambiguidade;
- `tools/g4-1-crash-capture.sh` captura logcat, exit-info, PID e assinaturas de
  crash para não atribuir a causa ao Gemma sem evidência;
- o crash histórico foi classificado como SIGSEGV em
  `libllm_inference_engine_jni.so`/`nativePredictSync`; a inferência foi isolada
  no processo `:gemma` com fallback quando o serviço cai;
- 329 testes, lint e APK passaram;
- startup físico, chamada Gemma isolada, falha controlada com fallback e
  recuperação do serviço passaram no Xiaomi, sem crash novo; ainda faltam os
  golden flows de microfone; a instrumentação de contadores já está no
  `AgenticVoiceViewModel`.
- G4.1 permanece `PASS_AUTOMATED_PENDING_DEVICE`.

Evidências e critérios em [TINO-TASK-G4.1.md](TINO-TASK-G4.1.md) e
[TINO-EVIDENCE-G4.1-2026-08-21.md](TINO-EVIDENCE-G4.1-2026-08-21.md).

### G3.4 — Interaction State

O estado da interação agora é uma unidade de domínio persistível, sem Room ou
JSON dentro do núcleo:

- `InteractionState` inclui `sessionId`, tela atual, superfícies ativas,
  operação pendente, slots, confirmação, estado de voz e expiração;
- `InteractionStateStore` define a porta; políticas `SESSION` e
  `UNTIL_RESOLVED` tornam a retenção explícita;
- `TinoAgentSession` sincroniza o estado e mantém as últimas superfícies,
  preservando a âncora da tela durante cancelamento/expiração;
- `RoomInteractionStateStore` serializa somente o draft de interação;
- tabela `interaction_states` e migration `11→12` foram conectadas ao Room e
  ao composition root;
- operação expirada ou resolvida não é restaurada como ação executável.

### G3.5 — Correction Learning Engine

O aprendizado de correções deixou de ser um contador de aliases e passou a ser
um contrato de domínio escopado:

- `CorrectionEvent` registra provenance, sessão/escopo, origem e timestamp;
- o engine transita entre `CANDIDATE`, `LEARNED`, `TRUSTED`, `DEMOTED` e
  `REMOVED`;
- duas evidências consistentes tornam a associação aprendida; uma confirmação
  adicional pode torná-la confiável;
- uma contradição demove a associação anterior e não resolve imediatamente a
  alternativa;
- remoção preserva histórico, mas impede resolução;
- `CommerceContextMemory` usa o engine com escopo de sessão; não existe
  promoção global automática;
- `LearnedAliasMemory` continua como facade de compatibilidade.

### G3.6 — Adaptive Lexicon

O vocabulário adaptativo agora é uma porta de domínio substituível, com
catálogo canônico, aliases aprendidos e resolução segura:

- `AdaptiveLexiconPort` separa o contrato do algoritmo e do composition root;
- score lexical por superfície/tokens, score fonético e contexto de uso/tela;
- thresholds e margem de ambiguidade evitam que aproximações fracas sejam
  aceitas automaticamente;
- aliases aprendidos normalizados têm precedência sem alterar o nome canônico;
- `EntityResolutionService` consulta o léxico adaptativo somente após o
  caminho exact/alias/fuzzy existente;
- `Maracá` resolve o produto real `Café Maratá`, enquanto nomes próximos de
  clientes permanecem ambíguos;
- nenhuma entidade, ID, preço, saldo ou estoque é criado pelo léxico.

### G3.7 — UiPlanner

Foi criada a fronteira semântica entre resultado grounded e apresentação:

- `GroundedResult` concentra somente resposta, evidência, status e hint de
  apresentação;
- `UiPlannerPort` recebe `UiContext` e decide uma `UiDecision` tipada;
- decisões cobrem texto, criação/atualização de surface, input, clarification,
  confirmação, resultado, erro e ausência de UI;
- dados insuficientes nunca viram insight;
- planner não importa Compose, Activity, NavController, Room, tools, renderer
  ou JSON A2UI;
- `FallbackUiPlanner` mantém uma política determinística quando um planner
  opcional falha;
- a conversão para A2UI permanece reservada ao próximo gate de surfaces.

### Smoke físico

Foi criado [tools/g3-2-smoke.sh](tools/g3-2-smoke.sh), que instala o APK sem
apagar dados, abre a Activity, verifica processo ativo, versão/API e crash
fatal.

## Pronto e validado

| Área | Estado | Evidência |
|---|---|---|
| ADK como planner protegido | PRONTO no código | adapter, fallback e fronteiras testadas |
| Telemetria de planejamento | PRONTO no código | contrato, runtime, Room, migrations e testes |
| A/B eval | PRONTO como evaluator | corpus reproduzível, sem executar tools |
| PlanValidator antes do executor | PRONTO | teste negativo de tool desconhecida |
| Room como source of truth | PRONTO nesta fatia | ADK sem acesso a fatos transacionais |
| App | PASS | 326 testes, 0 falhas, 0 erros |
| Fiscal core | PASS | 32 testes, 0 falhas, 0 erros |
| Fiscal service | PASS local | 10 testes Node e `npm run check` |
| Koog spike | PASS isolado | suíte passa, mas não é runtime produtivo |
| Lint | PASS | `:app:lintDebug` |
| APK | PASS | `:app:assembleDebug`, aproximadamente 558 MB |
| Documentação | PRONTO | task, backlog, status e matriz atualizados |

## Implementado, mas ainda não concluído

### G3.2 — ADK Evaluation & Observability

**Estado: `PASS_FULL`.** O código e toda a validação automatizada passaram. O
smoke físico também passou no Xiaomi 2410FPCC5G/API 36, sem apagar dados, com
abertura da MainActivity, reabertura do Room legado e processo estável por 5
segundos. A migration foi corrigida após o device revelar a coluna duplicada
na transição 9→10.

```bash
bash tools/g3-2-smoke.sh
```

### Voz real

Portas, adapters e fallback existem; Gemma isolado, fallback após queda e
recovery já foram validados no aparelho. Ainda falta validar no aparelho:
microfone, permissão, transcrição committed, preview, confirmação,
cancelamento, edição, continuação e partial transcript sem mutação.

### Fiscal local e câmera

O core fiscal, scanner overlay e OCR local têm fatias implementadas. Extração
real de DANFE, métricas de precisão, runtimes de visão e review → confirmação →
commit completo continuam pendentes.

### Catálogo agentic

O catálogo semântico G3.9 está implementado e validado: allowlist versionada,
schemas de props, fallback seguro, composição por contribuidores verticais e
renderer capaz de exibir múltiplos componentes. Produtos, estoque, preço,
clientes, recebíveis, atrasados, fornecedores e mutações ainda precisam
convergir para o mesmo caminho validado.

### A2UI, UX/UI e Undo

O contrato tipado, allowlist, catálogo semântico, renderer e surfaces incrementais
agora existem. Ainda faltam ações retomando o loop, estados completos em
todas as telas, screenshots/font scale e Undo/compensadores para preço, estoque,
venda, venda fiada e cliente.

## Pendente no roadmap

G3.10, G3.11, G3.12, G4 e G5 estão em `PASS_FULL`. G4.1 está em
`PASS_AUTOMATED_PENDING_DEVICE`; G6 — Predictive Tools / ML — não deve ser
iniciada antes de G4.1 `PASS_FULL`.

## Fora do produto integrado

- backend cloud, autenticação end-to-end e restore;
- observabilidade centralizada e crash reporting;
- WhatsApp com webhook, confirmação e lifecycle real;
- fiscal externo homologado, certificado e provedor real;
- RAG/Knowledge operacional;
- memória de negócio multi-store configurável, retomada de operações complexas
  fora do working context e revisão operacional avançada;
- Attention Engine A3, previsões/ML e Proactive Agent;
- release pipeline, rollback e matriz de devices;
- Koog como runtime produtivo — permanece spike isolado.

## Evolução da avaliação

| Medição | Antes de hoje | Estado atual |
|---|---:|---:|
| Intelligence Runtime estrutural | 8,8/10 | **9,6/10 automatizado / G4.1 pendente** |
| Gate 3.2 | Em implementação | **PASS_FULL** |
| Gate 3.3 | Planejado | **PASS** |
| Gate 3.4 | Não iniciado | **PASS** |
| Gate 3.5 | Não iniciado | **PASS** |
| Gate 3.6 | Não iniciado | **PASS** |
| Gate 3.7 | Não iniciado | **PASS** |
| Gate 3.8 | Não iniciado | **PASS_FULL** |
| Gate 3.9 | Não iniciado | **PASS_FULL** |
| Gate 3.10 | Não iniciado | **PASS_FULL** |
| Gate 3.11 | Planejado | **PASS_FULL** |
| Gate 3.12 | Não iniciado | **PASS_FULL** |
| Gate 4 | Não iniciado | **PASS_FULL** |
| Gate 5 | Planejado | **PASS_FULL** |
| Prontidão física | Não concluída | **PASS no device Xiaomi/API 36 para smoke, Room e mutation safety** |
| G4.1 — Voice Reliability & Crash Recovery | Extraordinário | **PASS_AUTOMATED_PENDING_DEVICE** |
| G6 — Predictive Tools / ML | Não iniciado | **BLOQUEADO até G4.1 PASS_FULL** |
| Produto completo | Não concluído | **Ainda não concluído** |

O salto de 8,8 para 9,6 vem da separação do adapter ADK, telemetria persistente,
migrations, evaluator A/B, classificação de rejeições, Agent Loop controlado,
Interaction State persistível, Correction Learning escopado, Adaptive Lexicon
seguro, UiPlanner sem dependências de UI e documentação operacional. Essa nota
mede maturidade estrutural da inteligência, não produto completo.

Para aproximar 10/10 ainda faltam cobertura/extensibilidade completa do catálogo A2UI, policy universal de mutação,
memória com fronteiras working/session/business, fitness/evals integrados ao CI
e validação real em devices, migrations, sync e produção.

## Decisão

O TINO está automatizado e testável em ambiente local e físico. G3.2 até G5
estão em `PASS_FULL` conforme os critérios registrados. G6 não foi iniciada.
