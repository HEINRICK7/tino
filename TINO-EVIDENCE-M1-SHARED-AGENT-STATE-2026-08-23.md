# TINO — Evidência M1 Shared Agent State

**Data:** 2026-08-23  
**Estado:** `M1 = PASS`  
**Próximo gate:** `M2 = BLOCKED_BY_M1` até autorização explícita

## Escopo

Implementação e validação do estado compartilhado de interação entre Agent
Runtime, Compose e A2UI, mantendo Room/Core como fonte de verdade operacional.

## Entrega comprovada

- `TinoAgentSession` mantém um único `StateFlow<TinoAgentSessionSnapshot>`.
- Cada mutação publica `stateVersion` monotônica.
- As mutações públicas são serializadas para evitar perda de estado concorrente.
- O estado é projetado e persistido por `InteractionStateStore`, com restauração
  e proteção contra sobrescrita por revisão antiga.
- `TinoAgentSessionViewModel`, `TinoApp` e `TinoNavigation` consomem o snapshot
  compartilhado para a casca Compose, presença e ações do runtime.
- Transições emitem `AGENT_STATE_CHANGED` com metadados allowlisted e redigidos;
  transcript, resultado e dados operacionais não entram no evento.
- A camada compartilhada não substitui nem replica a verdade operacional do
  Room.

## Verificações executadas

```text
gradle :app:testDebugUnitTest \
  --tests com.tino.app.domain.agent.AgenticShellTest \
  --tests com.tino.app.domain.agent.InteractionStateTest \
  --tests com.tino.app.core.database.RoomInteractionStateStoreTest \
  --no-daemon
→ BUILD SUCCESSFUL

gradle :app:assembleDebug --no-daemon
→ BUILD SUCCESSFUL

adb -s 69WOBUFENFLFGAJZ install -r app/build/outputs/apk/debug/app-debug.apk
→ Success
```

## Validação no device

- Device: Xiaomi `2410FPCC5G`, serial `69WOBUFENFLFGAJZ`, API 36.
- Pacote: `com.tino.app`.
- Build: `versionName=0.1.0-pilot.1`, `versionCode=2`.
- Cold start executado após a instalação.
- Nenhuma exceção fatal do processo `com.tino.app` observada no recorte de
  logcat da inicialização.

## Decisão

M1 está fechado com implementação, integração, observabilidade, testes e
verificação de instalação/cold start. M2 não foi iniciado automaticamente.
