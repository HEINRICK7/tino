# TINO — G4.1: fallback integrado à voz — aprovado

**Data:** 23/08/2026  
**Estado do cenário:** `PASS`  
**Estado do gate:** `IN_EXECUTION / REMAINING_PHYSICAL_REQUIRED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Build:** `0.1.0-pilot.1` / versionCode 2  
**PID principal:** `6179`

## Preparação

O harness confirmou a indisponibilidade controlada da Gemma isolada:

```text
TinoGemmaSmoke: UNAVAILABLE
Gemma isolado foi encerrado; usando fallback.
```

Durante a sessão de voz, o processo `com.tino.app:gemma` apresentou falha
nativa isolada (`SIGSEGV` no MediaPipe), enquanto o processo principal
`com.tino.app` permaneceu vivo.

## Evidência física

Frase executada:

```text
me ajuda com meu negócio
```

O pipeline de voz terminou em estado recuperável:

```text
VOICE_TRANSCRIPT_COMMITTED committed_count=1 transcript_state=REVIEW
VOICE_AGENT_SUBMITTED agent_execution_count=1 agent_executions_before_send=0
AGENT_STARTED
AGENT_COMPLETED status=success duration_ms=4515
QUERY_COMPLETED duration_ms=4855 fast_path=false
RENDERED duration_ms=4858
```

A UI apresentou `AINDA NÃO CONSIGO RESPONDER`, explicou que a inteligência
local demorou e ofereceu `TENTAR DE NOVO`. Não houve `CAPABILITY_STARTED`,
consulta de Room, alteração de estoque, cliente ou fiado.

## Decisão

`PASS` para fallback integrado à voz e ausência de mutação operacional.

O crash da Gemma permanece classificado como falha do processo isolado e foi
contido pelo processo principal; ele não foi ocultado como se não tivesse
ocorrido.
