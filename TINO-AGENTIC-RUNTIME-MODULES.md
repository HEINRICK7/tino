# TINO — Agentic Runtime Modules

**Status:** M8_PASS / MULTI_VERTICAL_FOUNDATION_IN_PROGRESS  
**Regra:** M1–M8 estão PASS. A fundação Multi-Vertical foi liberada; novos packs e G6 continuam bloqueados até a fundação composicional cumprir seus critérios.  
**Data:** 2026-08-24

Este documento congela a ordem oficial de execução do próximo ciclo do TINO
Agentic Runtime.

## Fronteira de verdade

SharedAgentState é a verdade da interação e da orquestração:

- intenção em andamento;
- entidades resolvidas;
- slots e patches;
- capability atual;
- progresso;
- confirmação;
- contexto de tela;
- estado de cancelamento;
- referências de memória de sessão.

Room/Core continua sendo a verdade operacional do negócio:

- clientes;
- produtos;
- estoque;
- vendas;
- recebíveis;
- pagamentos;
- histórico financeiro;
- fiscal;
- sincronização.

SharedAgentState nunca substitui Room e não pode possuir uma cópia concorrente
da verdade financeira ou cadastral.

## Gate de execução

G4.1
→ PASS
→ M1
→ PASS
→ M2
→ PASS
→ M3
→ PASS
→ M4
→ PASS
→ M5
→ PASS
→ M6
→ PASS
→ M7
→ PASS
→ M8

Não iniciar M(n+1) enquanto M(n) não tiver PASS.

## Definition of Done comum

Um módulo só pode receber PASS quando possuir:

- contrato versionado;
- implementação completa;
- integração real com os consumidores;
- testes unitários e de integração;
- observabilidade;
- comportamento de timeout, erro e cancelamento;
- critérios funcionais comprovados;
- ausência de estado duplicado ou fallback implícito fora do contrato.

## M1 — Shared Agent State

Criar uma única fonte do estado de interação entre Agent Runtime, Compose e
A2UI, preservando Room como verdade operacional.

Inclui SharedAgentState, InteractionState, entidades resolvidas, PendingAction,
CurrentCapability, ConfirmationState, ScreenContext, SessionMemoryRef,
StateFlow, snapshots, versionamento, restauração após recreation, cancelamento
e testes de concorrência.

PASS exige que UI e A2UI observem o mesmo estado sem polling, que o adapter do
agent publique alterações, que o estado sobreviva a recreation e que não exista
estado duplicado na ViewModel.

### Estado da implementação M1 — 2026-08-23 — PASS

Implementado no núcleo:

- `TinoAgentSession` como implementação de `SharedAgentState`, com um único
  `StateFlow<TinoAgentSessionSnapshot>` observável;
- revisão monotônica `stateVersion` em cada mutação;
- projeção da revisão para `InteractionState` e persistência JSON/Room, com
  compatibilidade para registros antigos sem revisão;
- publicação correta da transição `markPreviewReady` pelo mesmo fluxo compartilhado;
- serialização das mutações públicas para evitar perda de estado concorrente;
- proteção dos adapters de persistência contra sobrescrita por revisão antiga;
- adapter somente leitura disponível para ViewModel/Compose;
- regressões de concorrência, publicação de preview, restauração e round-trip Room.
- evento redigido `AGENT_STATE_CHANGED`, com `stateVersion` e metadados de estado
  permitidos, sem transcript, resultado ou dados operacionais sensíveis;
- `TinoApp`/`TinoNavigation` consumindo o snapshot compartilhado para a casca
  Compose, presença e ações de cancelamento, sem uma segunda fonte de estado;
- teste de regressão confirmando transições versionadas e observáveis para UI e
  runtime.

Critérios comprovados para o gate M1:

- `StateFlow` único, snapshots versionados e mutações serializadas;
- persistência/restauração via `InteractionStateStore`/Room e proteção contra
  revisão antiga;
- integração do snapshot na casca Compose e no runtime de presença/A2UI;
- observabilidade redigida de transições, sem duplicar a verdade operacional do
  Room;
- testes focados verdes, build debug verde, instalação no device e cold start
  sem crash.

M1 permanece congelado como contrato/base. M2 foi iniciado somente após sua
aprovação e não altera a fonte de estado compartilhado.

## Estado de implementação M2–M8 — 2026-08-24

Os contratos abaixo foram implementados como runtime puro e possuem regressões
unitárias. M2 recebeu `PASS` após integração produtiva, testes, build e
instalação; os módulos posteriores continuam sem `PASS`.

- **M2 — PASS:** `AgentProgressRuntime` publica
  eventos tipados, ordenados e terminais, com snapshot recuperável, auditoria
  redigida, proteção contra execução concorrente e lifecycle produtivo comum
  para consulta, quick query, seleção de entidade e confirmação. Testes focados
  e build/install do APK estão verdes. A evidência está em
  `TINO-EVIDENCE-M2-AGENT-PROGRESS-2026-08-24.md`.
- **M3 — PASS:** `AgentStreamingRuntime` fornece
  envelope versionado com `eventId`, `runId`, sequência, timestamp e descarte
  após encerramento; o coordinator e o fluxo de voz compartilham o mesmo
  `runId`, com snapshot/replay para recreation, backpressure e terminalidade.
  Evidência em `TINO-EVIDENCE-M3-AGENTIC-STREAMING-2026-08-24.md`.
- **M4 — PASS:** `HumanGatePolicy` é compartilhada pelo
  `HumanGateRuntime` e pelo `MutationSafetyCoordinator`; o fluxo real mantém
  token persistido, expiração exata, cancelamento seguro, idempotência,
  auditoria redigida e commit como fonte única de autorização. Evidência em
  `TINO-EVIDENCE-M4-HITL-2026-08-24.md`.
- **M5 — PASS:** `InterruptCorrectionRuntime` aplica patches estruturados de
  quantidade, cliente, produto, período, valor e método sem reiniciar a
  interação. A versão esperada do estado impede sobrescrita tardia, operações
  em `EXECUTING` rejeitam correção, aliases de slots são normalizados,
  dependências derivadas são invalidadas e o coordinator só regenera o preview
  quando o patch foi aplicado. Regressões cobrem preservação de slots
  independentes, estado obsoleto, operação ativa, cancelamento e rejeição de
  campos não suportados. Evidência em
  `TINO-EVIDENCE-M5-INTERRUPT-CORRECTION-2026-08-24.md`.
- **M6 — PASS:** o protocolo incremental A2UI possui envelope versionado,
  sequência explícita por surface, compatibilidade controlada para mensagens
  legadas sem sequência, patches parciais de componentes/modelo, evento final
  explícito e rejeição após terminalidade. O host mantém a última surface válida
  diante de patch rejeitado, o renderer permanece determinístico e o catálogo
  continua fechado. Evidência em
  `TINO-EVIDENCE-M6-INCREMENTAL-A2UI-2026-08-24.md`.
- **M7 — PASS:** `TinoPresenceResolver` deriva presença de
  `SharedAgentState`, progresso e Human Gate, sem conhecimento de Room/Gemma.
  Sinais atuais de voz vencem terminais antigos do progresso; execução ativa
  projeta `THINKING`, confirmação pendente projeta `WAITING`, e sucesso, erro e
  cancelamento têm estados terminais seguros. O `TinoAgentSessionViewModel` e
  o `MainShell` projetam a presença no FAB. Evidência em
  `TINO-EVIDENCE-M7-TINO-PRESENCE-2026-08-24.md`.
- **M8 — PASS:** `FullAgentRuntimeIntegration` conecta estado, progresso,
  streaming, capability, Human Gate, timeout, falha, cancelamento e recovery
  A2UI. Execuções são serializadas, deadlines são validados, tools encerram
  com resultado explícito antes do terminal e o fluxo de espera pode retomar
  o mesmo run. Os cinco cenários obrigatórios — leitura, mutação com HITL,
  correção/recompute, interrupt e falha/timeout — possuem regressões e
  observabilidade. Evidência em
  `TINO-EVIDENCE-M8-FULL-RUNTIME-INTEGRATION-2026-08-24.md`.

## M2 — Agent Progress Runtime

Separar progresso operacional de logging diagnóstico.

O módulo deve publicar eventos tipados com runId e executionId:

RunStarted, CapabilityStarted, ToolStarted, ToolProgress, ToolCompleted,
WaitingForUser, RunCompleted, RunFailed e RunCancelled.

PASS exige eventos balanceados, timeout e cancellation explícitos, realtime para
a UI, nenhuma execução infinita e testes de exception, timeout e cancellation.

## M3 — Agentic Streaming

Criar um envelope ordenado para o fluxo completo:

Speech
→ TranscriptPartial
→ TranscriptCommitted
→ AgentStarted
→ StateChanged
→ ToolStarted
→ ToolProgress
→ ToolCompleted
→ A2UIUpdated
→ Completed.

Cada evento deve ter eventId, runId, sequence, timestamp e payload versionado.

PASS exige ordenação, cancellation, backpressure, timeout, descarte de eventos
após encerramento e atualização incremental sem polling.

## M4 — HITL Runtime

Centralizar a decisão de risco:

Capability
→ RiskPolicy
→ ALLOW, CONFIRM ou DENY.

Toda capability deve ser classificada. Mutations financeiras, destrutivas e
fiscais nunca executam sem HumanGate válido.

PASS exige confirmação e cancelamento idempotentes, expiração segura, proteção
contra duplicidade, audit trail, Undo quando elegível e testes por nível de risco.

## M5 — Interrupt & Correction

Implementar correções como patches estruturados, não como reinício cego do run.

Fluxo:

InteractionPatch
→ AffectedFieldsResolver
→ DependencyInvalidation
→ Recompute
→ SharedAgentState
→ A2UI update.

Começar com patches de quantidade, cliente, produto e período.

PASS exige preservação de dados independentes, invalidação das dependências
corretas, preview regenerado, segurança durante tool ativa e testes de correção.

## M6 — Incremental A2UI

Separar estado A2UI do resultado final.

A2UI deve suportar estados parciais permitidos, por exemplo cliente resolvido,
produto em resolução, preço calculando e preview completo.

PASS exige registry fechado, schema versionado, patches incrementais, renderer
Compose determinístico, fallback seguro, proteção contra payload inválido e
evento final explícito.

## M7 — Tino Presence

Implementar a presença visual somente sobre sinais reais dos módulos anteriores.

SharedAgentState + AgentProgress + VoiceState + HumanGate
→ TinoPresenceState
→ TinoPresence Compose.

O mascote não conhece ADK, Room, Gemma, repositories ou capabilities internas.

PASS exige animação baseada em estado real, reação ao áudio, progresso controlando
atividade, waiting-for-user representado corretamente, completion com settle,
cancelamento retornando a idle e lifecycle Compose seguro. Timers artificiais
não são evidência de funcionamento.

## M8 — Full Runtime Integration

M8 não é uma feature adicional. É a prova E2E do runtime completo.

Cenários obrigatórios:

1. Consulta read-only: stream → progress → Room → A2UI → completion.
2. Mutação: resolução → preview → HITL → confirmação → Room mutation →
   completion.
3. Correção: quantidade 2 → patch para 3 → recompute → preview atualizado.
4. Interrupt: cancelamento → tools canceladas quando possível → pending action
   invalidada → estado consistente → Presence idle.
5. Falha: deadline → timeout → progress failed → estado limpo → recovery.

PASS exige todos os cenários com evidência, testes, observabilidade e sem
loading indefinido.

## Restrições

- G4.1 permanece o gate anterior.
- Não implementar Multi-Vertical Runtime Integration durante este bloqueio.
- Não criar novos vertical packs.
- Não introduzir AG-UI, CopilotKit ou outra mudança arquitetural paralela.
- Não iniciar um módulo seguinte por conveniência ou para “adiantar” trabalho.
