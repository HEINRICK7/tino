# TINO — G4.1: comando curto “clientes” — aprovado com retry de ASR

**Data:** 23/08/2026  
**Estado do cenário:** `PASS_WITH_TRANSIENT_ASR_RETRY`  
**Estado do gate:** `IN_EXECUTION / REMAINING_PHYSICAL_REQUIRED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Build:** `0.1.0-pilot.1` / versionCode 2  
**PID:** `549`

## Evidência física

Na primeira tentativa, o Android ASR registrou uma falha transitória de
transcrição:

```text
VOICE_FAILURE stage=transcription
```

Sem execução agentic ou spinner prolongado, a frase foi repetida. Na segunda
tentativa, o log confirmou:

```text
VOICE_TRANSCRIPT_PARTIAL partial_count=1..3
VOICE_TRANSCRIPT_COMMITTED committed_count=1 transcript_state=REVIEW
VOICE_AGENT_SUBMITTED agent_execution_count=1 agent_executions_before_send=0
NAVIGATION_COMPLETED route=CUSTOMERS fast_path=true
```

A tela Clientes abriu corretamente e exibiu os clientes cadastrados. A falha
transitória de ASR foi recuperável e não chegou ao roteamento/capability.

## Decisão

`PASS` para o comando curto `clientes`, com recuperação após retry de
transcrição. O gate continua em `IN_EXECUTION` pelos demais cenários.
