# TINO — Evidência M3 Agentic Streaming

**Data:** 2026-08-24  
**Estado:** `M3 = PASS`  
**Dependências:** `M1 = PASS`, `M2 = PASS`  
**Próximo gate:** `M4 = BLOCKED_BY_M3` até autorização e validação própria

## Escopo

Envelope ordenado e versionado para o fluxo speech → transcript → agent →
tool → A2UI → terminal, com recuperação para hosts Compose/A2UI e descarte
seguro após encerramento.

## Entrega

- `AgentStreamEvent` carrega `eventId`, `runId`, sequência monotônica, timestamp,
  `payloadVersion`, tipo e payload limitado/validado.
- `AgentStreamingRuntime` mantém `StateFlow<AgentStreamSnapshot>` para recuperar
  a última posição sem depender de polling.
- O fluxo possui replay limitado e backpressure suspensivo para consumidores
  ativos.
- Apenas um stream pode estar ativo por vez; streams concorrentes são rejeitados.
- `COMPLETED`, `FAILED` e `CANCELLED` fecham o stream; eventos posteriores são
  rejeitados.
- A voz emite `SPEECH`, parciais, revisão, fim da fala e committed; o coordinator
  reutiliza o mesmo `runId` até `A2UI_UPDATED` e o evento terminal.
- Consultas de texto/quick query continuam criando um stream próprio quando não
  existe uma sessão de voz ativa.
- Cancelamento propaga `CancellationException` corretamente e fecha o stream
  sem deixar lifecycle pendente.

## Testes executados

```text
gradle :app:testDebugUnitTest \
  --tests com.tino.app.domain.agent.AgentRuntimeModulesTest \
  --tests com.tino.app.domain.agent.AgenticQueryTest \
  --tests com.tino.app.domain.agent.AgenticGemmaA2uiTest \
  --no-daemon
→ BUILD SUCCESSFUL

gradle :app:testDebugUnitTest \
  --tests com.tino.app.domain.agent.AgentRuntimeModulesTest \
  --no-daemon
→ BUILD SUCCESSFUL após o ajuste final de cancelamento
```

As regressões cobrem sequência, payload versionado, replay, snapshot terminal,
concorrência, descarte após terminalidade, timeout/cancelamento e integração
do coordinator.

## Build e device

```text
gradle :app:assembleDebug --no-daemon
→ BUILD SUCCESSFUL

adb -s 69WOBUFENFLFGAJZ install -r app/build/outputs/apk/debug/app-debug.apk
→ Success
```

- Device: Xiaomi `2410FPCC5G`, serial `69WOBUFENFLFGAJZ`, API 36.
- Pacote: `com.tino.app`.
- Cold start executado após a instalação.
- Nenhum `FATAL EXCEPTION` do processo `com.tino.app` apareceu no recorte de
  logcat da inicialização.

## Decisão

M3 está fechado com contrato, implementação, integração speech-to-A2UI,
recovery, backpressure, terminalidade, regressões automatizadas e build/device
smoke. M4 não foi iniciado.
