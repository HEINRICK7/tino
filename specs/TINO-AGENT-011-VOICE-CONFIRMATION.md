# TINO — Agentic Golden Path 011: Confirmação por voz

**Status:** IMPLEMENTADO / validação falada real pendente
**Tipo:** checkpoint de execução
**Pré-requisito:** `specs/TINO-AGENT-010-CREDIT-VOICE.md`
**Objetivo:** permitir confirmar ou cancelar uma operação pendente usando voz, sem remover a proteção humana.

## Fluxo entregue

```text
fala inicial
  ↓
preview visível
  ↓
CONFIRMAR FALANDO
  ↓
"sim" / "confirmar"
  ↓
execução da mesma chamada
```

Também são aceitas formas curtas como “pode”, “anota” e “pode fazer”. “Cancela”
limpa o contexto pendente e não executa a mutação. Uma frase que não seja
confirmação nem cancelamento não passa para o dispatcher.

## Gates

| Gate | Evidência esperada | Status |
|---|---|---|
| `PREVIEW_REQUIRED` | Operação só pode ser confirmada após preview | PASS |
| `VOICE_CONFIRM` | “sim” confirma a chamada pendente | PASS |
| `VOICE_CANCEL` | “cancela” não executa a mutação | PASS |
| `SAME_OPERATION` | A confirmação executa a mesma chamada revisada | PASS |
| `NO_UNKNOWN_TEXT_EXECUTION` | Texto fora da allowlist não executa | PASS |
| `RECOVERABLE_UNKNOWN` | Texto ambíguo mantém a operação aguardando confirmação | PASS |
| `BUILD` | `testDebugUnitTest`, `assembleDebug` e `lintDebug` | PASS |
| `REAL_SPEECH` | Confirmação falada no aparelho | IN_PROGRESS |

## Próxima ação automática

Validar no aparelho a sequência completa de preview → “sim” e depois cobrir
respostas de confirmação mais naturais sem ampliar a allowlist de forma
silenciosa. Último smoke launch: `pid=11192`, sem fatal exception e com Gemma
presente no APK.
