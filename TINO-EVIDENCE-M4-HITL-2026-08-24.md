# TINO — Evidência M4 HITL Runtime

**Data:** 2026-08-24  
**Estado:** `M4 = PASS`  
**Dependências:** `M1 = PASS`, `M2 = PASS`, `M3 = PASS`  
**Próximo gate:** `M5 = BLOCKED_BY_M4` até autorização e validação própria

## Escopo

Gate humano para capabilities com mutação, preservando Room como autoridade da
operação e impedindo execução sem confirmação válida.

## Entrega

- `HumanGatePolicy` classifica queries, navegação e mutações como `ALLOW`,
  `CONFIRM` ou `DENY`.
- `MutationSafetyCoordinator` cria prévia com token, TTL, fingerprint,
  capability, risco e chave de idempotência.
- O token bruto não é persistido; Room guarda somente o hash.
- A autorização valida token, expiração, fingerprint, capability e argumentos
  antes de reservar a operação.
- `PENDING → EXECUTING → COMMITTED` é protegido pelo store persistente e por
  reserva atômica, bloqueando replay e confirmações concorrentes.
- Cancelamento remove somente operações `PENDING`; uma operação já reservada não
  pode ser apagada durante a execução.
- Expiração no instante exato do deadline é negada.
- Auditoria `CONFIRMATION` registra PREPARED, AUTHORIZED, DENIED, COMMITTED,
  RELEASED e CANCELLED usando apenas metadados allowlisted/redigidos.
- O `MutationSafeToolExecutor` não aceita o booleano `confirmed` como bypass:
  mutation só atravessa `confirm(call, token)`.

## Testes executados

```text
gradle :app:testDebugUnitTest \
  --tests com.tino.app.domain.voice.MutationSafetyTest \
  --tests com.tino.app.core.database.RoomMutationOperationStoreTest \
  --tests com.tino.app.domain.agent.AgentRuntimeModulesTest \
  --no-daemon
→ BUILD SUCCESSFUL
```

As regressões cobrem confirmação exata, token inválido, prévia obsoleta,
expiração, cancelamento, cancelamento concorrente com execução, idempotência,
confirmações concorrentes, recreation via Room e trilha de auditoria.

## Build e device

```text
gradle :app:assembleDebug --no-daemon
→ BUILD SUCCESSFUL

adb -s 69WOBUFENFLFGAJZ install -r app/build/outputs/apk/debug/app-debug.apk
→ Success
```

- Device: Xiaomi `2410FPCC5G`, serial `69WOBUFENFLFGAJZ`, API 36.
- Pacote: `com.tino.app`, `versionCode=2`, `versionName=0.1.0-pilot.1`.
- Cold start executado após a instalação.
- Nenhum `FATAL EXCEPTION` do processo `com.tino.app` apareceu no recorte de
  logcat da inicialização.

## Decisão

M4 está fechado com política de risco, confirmação persistente, expiração,
cancelamento seguro, idempotência, audit trail, testes, build e device smoke.
M5 não foi iniciado.
