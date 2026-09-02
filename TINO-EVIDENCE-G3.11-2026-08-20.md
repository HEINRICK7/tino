# Evidências G3.11 — 20/08/2026

Device físico: Xiaomi 2410FPCC5G, API 36, serial `69WOBUFENFLFGAJZ`.
Pacote: `com.tino.app`, `versionCode=2`, `versionName=0.1.0-pilot.1`.

## Validação automatizada

- `gradle :app:testDebugUnitTest --no-daemon` — PASS, **314 testes**, 0 falhas;
- `gradle :app:lintDebug :app:assembleDebug --no-daemon` — PASS;
- `MutationSafetyTest` — 10 testes focados;
- `RoomMutationOperationStoreTest` — 3 testes focados;
- `A2uiActionProtocolTest` — 8 testes PASS;
- migration Room `12 → 13` — PASS no teste e no startup físico.

## Smoke físico

Instalação e inicialização:

```text
adb get-state                         → device
adb install -r app-debug.apk         → Success
adb shell am start -n com.tino.app/.MainActivity
pidof com.tino.app                   → processo ativo
```

O harness DEBUG acessível em `Mais → G3.11 Mutation Safety` usa somente a
operação persistida de teste `debug-mutation-001`; não altera venda, estoque,
cliente, fiado ou preço comercial.

### 1. Confirmar uma vez

```text
State: PENDING
Commit count: 0
CONFIRMAR VIA A2UI
State: COMMITTED
Commit count: 1
```

O evento percorreu `A2uiActionValidator → A2uiActionRuntimeBridge →
MutationConfirmationPort → MutationSafeToolExecutor`.

### 2. Replay / double-confirm

```text
CONFIRMAR NOVAMENTE / REPLAY
Operação repetida bloqueada por idempotência.
State: COMMITTED
Commit count: 1
```

O teste automatizado adicional executou duas confirmações concorrentes e apenas
uma reservou `PENDING → EXECUTING`.

### 3. Restart

Após `adb shell am force-stop com.tino.app` e nova abertura:

```text
State: COMMITTED
Commit count: 1
Estado restaurado do Room após restart.
```

`COMMITTED` permaneceu terminal após a recriação do processo.

### 4. Cancelamento

```text
State: PENDING
CANCELAR SEM MUTAR
Cancelado sem mutação; a operação deixou de ser executável.
Commit count: 0
```

### 5. Token cruzado e stale state

```text
TESTAR TOKEN DE OUTRA OPERAÇÃO
Token de confirmação inválido.

TESTAR STALE FINGERPRINT
State: INVALIDADO
Os dados mudaram desde a prévia. Gere uma nova confirmação.
Commit count: 0
```

## Capturas locais

As capturas da sessão foram registradas durante o smoke em:

- `/tmp/tino-g311/06-pending.png`
- `/tmp/tino-g311/07-committed.png`
- `/tmp/tino-g311/08-replay.png`
- `/tmp/tino-g311/10-restored-harness.png`
- `/tmp/tino-g311/12-cancelled.png`
- `/tmp/tino-g311/14-wrong-token.png`
- `/tmp/tino-g311/15-stale.png`

## Resultado

`G3.11 = PASS_FULL`. `G3.12 = LIBERADA / NOT_STARTED`.
