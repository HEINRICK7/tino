# TINO — G4.1: timeout e recuperação física — aprovado

**Data:** 23/08/2026  
**Estado do cenário:** `PASS`  
**Estado do gate:** `IN_EXECUTION / REMAINING_PHYSICAL_REQUIRED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Build:** `0.1.0-pilot.1` / versionCode 2  
**PID:** `5174`

## Evidência física

Frase usada para exercitar o caminho agentic:

```text
cadastrado
```

O log confirmou estado terminal dentro do orçamento:

```text
VOICE_AGENT_SUBMITTED agent_execution_count=1 agent_executions_before_send=0
QUERY_STARTED timeout_ms=5000
AGENT_STARTED
AGENT_COMPLETED status=success duration_ms=4521
ROUTING_COMPLETED duration_ms=4635 route=interpreter fast_path=false
QUERY_COMPLETED duration_ms=4877 fast_path=false
RENDERED duration_ms=4881
```

A UI não permaneceu no spinner. Ela apresentou um erro recuperável:

```text
A inteligência local demorou mais que o esperado. Tente novamente.
[TENTAR DE NOVO]
```

Não houve mutação operacional, crash ou estado de carregamento infinito.

## Decisão

`PASS` para timeout e recuperação acionável.
