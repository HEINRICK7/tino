# TINO — G4.1: fala longa física — aprovado

**Data:** 23/08/2026  
**Estado do cenário:** `PASS`  
**Estado do gate:** `IN_EXECUTION / REMAINING_PHYSICAL_REQUIRED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Build:** `0.1.0-pilot.1` / versionCode 2  
**PID:** `22688`

## Evidência física

Frase executada:

```text
Quanto eu recebi hoje no Pix e no dinheiro e quanto ainda tenho para receber?
```

O log confirmou:

```text
SPEECH_PROVIDER_SELECTED provider=ON_DEVICE locale=pt-BR
VOICE_TRANSCRIPT_PARTIAL partial_count=1..27
VOICE_TRANSCRIPT_COMMITTED committed_count=1 transcript_state=REVIEW
VOICE_AGENT_SUBMITTED agent_executions_before_send=0
ROUTING_COMPLETED duration_ms=117 route=fast fast_path=true
CAPABILITY_COMPLETED duration_ms=48
A2UI_READY duration_ms=0
QUERY_COMPLETED duration_ms=174 fast_path=true
RENDERED duration_ms=177
```

Não houve `ProductPicker`, timeout, erro de tool, crash ou ANR do TINO. A
execução agentic permaneceu em zero antes de `Enviar` e foi executada somente
após o envio.

Houve `VOICE_CORRECTION_QUEUED` durante a revisão, mas a alteração não
produziu uma correção grounded de produto/cliente; portanto não era elegível
para `CorrectionEvent`/learning neste cenário financeiro.

## Decisão

O cenário de fala longa está aprovado. G4.1 continua em `IN_EXECUTION` até a
conclusão dos demais cenários físicos obrigatórios.
