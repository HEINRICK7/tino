# TINO — Evidência M8: Full Runtime Integration

**Data:** 2026-08-24  
**Estado:** `M8 = PASS`  
**Próxima frente:** `MULTI_VERTICAL = BLOCKED_BY_M8`  
**Escopo:** integração end-to-end dos módulos M1–M7; Multi-Vertical, novos packs e G6 não foram iniciados.

## Resultado

O runtime integrado mantém a cadeia:

```text
SharedAgentState
  → AgentProgressRuntime
  → AgentStreamingRuntime
  → Human Gate / Capability
  → Tool lifecycle
  → A2UI recovery/update
  → terminal state
```

`FullAgentRuntimeIntegration` serializa execuções, valida deadlines positivos,
preserva o mesmo run durante espera HITL, cancela a operação quando possível e
limpa estado/progresso/streaming em falha, timeout e cancelamento.

## Cenários obrigatórios

### 1. Consulta read-only

`AGENT_STARTED → STATE_CHANGED → TOOL_STARTED → TOOL_COMPLETED → A2UI_UPDATED → COMPLETED`

O resultado é entregue como `FullRuntimeResult.Completed` e o estado compartilhado
termina em `SUCCESS`.

### 2. Mutação com HITL

O primeiro ciclo produz `WaitingForUser`, não executa a operação e mantém o
stream ativo. Após `HumanGateResult.Allowed`, o mesmo run conclui com tool,
A2UI e terminal `COMPLETED`.

### 3. Correção e recompute

Quantidade `2 → 3` passa por `InteractionPatch`, invalidação e atualização do
`SharedAgentState`; o segundo ciclo executa o preview recomposto com a quantidade
corrigida.

### 4. Interrupt

Cancelamento propagado interrompe a operação suspensa, publica `ToolCompleted`
com falha, `A2UI_UPDATED` em `IDLE`, `RunCancelled` e deixa o SharedAgentState
sem ação pendente.

### 5. Falha e timeout

Deadline ou exceção produzem `ToolCompleted(false)`, `A2UI_UPDATED` de recovery,
`RunFailed`, estado compartilhado `FAILED` e ausência de loading indefinido.

## Testes e validação

```text
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
→ BUILD SUCCESSFUL

adb -s 69WOBUFENFLFGAJZ install -r app/build/outputs/apk/debug/app-debug.apk
→ Success
```

Device físico:

```text
Xiaomi 2410FPCC5G / API 36
cold start: processo com.tino.app ativo
FATAL EXCEPTION / AndroidRuntime: nenhum registro
```

## Decisão de gate

`M8 = PASS`. A sequência M1–M8 está fechada. Multi-Vertical, novos packs e G6
continuam bloqueados e aguardam decisão/autorização separada.
