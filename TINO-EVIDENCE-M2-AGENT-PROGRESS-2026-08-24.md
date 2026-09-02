# TINO — Evidência M2 Agent Progress Runtime

**Data:** 2026-08-24  
**Estado:** `M2 = PASS`  
**Dependência:** `M1 = PASS`  
**Próximo gate:** `M3 = BLOCKED_BY_M2` até autorização e validação própria

## Escopo

Lifecycle de progresso operacional do Agent Runtime, separado de logging
diagnóstico e compartilhado por consultas, quick queries, continuação de
seleção e confirmação HITL.

## Entrega

- `AgentProgressRuntime` publica eventos tipados com `runId`, `executionId`,
  sequência monotônica e timestamp.
- O `StateFlow<AgentProgressSnapshot>` permite recuperação do estado atual pela
  UI sem polling histórico.
- O runtime rejeita uma segunda execução enquanto outra está `ACTIVE`.
- Estados terminais são explícitos: `COMPLETED`, `FAILED`, `CANCELLED` e
  `WAITING_FOR_USER`.
- Timeout publica `ToolCompleted(succeeded = false)` e `RunFailed`; cancelamento
  publica `RunCancelled` e limpa o estado compartilhado.
- Auditoria `AGENT_PROGRESS` registra apenas metadados allowlisted/redigidos.
- `TinoAgentSessionViewModel` combina o snapshot do M1 com o progresso para a
  presença Compose.
- `AgenticTextQueryCoordinator` usa o mesmo lifecycle para `ask`, `askCapability`,
  `selectEntityChoice` e `confirm`, sem execução invisível fora do M2.
- Confirmação multiturno interna usa o lifecycle já aberto pelo `ask`, evitando
  execução aninhada falsa.

## Testes executados

```text
gradle :app:testDebugUnitTest \
  --tests com.tino.app.domain.agent.AgentRuntimeModulesTest \
  --tests com.tino.app.domain.agent.AgenticQueryTest \
  --tests com.tino.app.domain.agent.AgenticGemmaA2uiTest \
  --no-daemon
→ BUILD SUCCESSFUL
```

Os testes cobrem ordenação/terminalidade, concorrência, timeout, cancelamento,
HITL, continuidade multiturno e integração do coordinator.

## Build e device

```text
gradle :app:assembleDebug --no-daemon
→ BUILD SUCCESSFUL

adb -s 69WOBUFENFLFGAJZ install -r app/build/outputs/apk/debug/app-debug.apk
→ Success
```

- Device: Xiaomi `2410FPCC5G`, serial `69WOBUFENFLFGAJZ`, API 36.
- Pacote: `com.tino.app`.
- Build: `versionName=0.1.0-pilot.1`, `versionCode=2`.
- Cold start executado após a instalação.
- Nenhum `FATAL EXCEPTION` do processo `com.tino.app` apareceu no recorte de
  logcat da inicialização.

## Decisão

M2 está fechado com contrato, implementação, integração produtiva,
observabilidade e regressões automatizadas. M3 não foi iniciado.
