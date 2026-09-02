# TINO — G4.1: continuar falando físico — falha corrigida e reteste aprovado

**Data:** 23/08/2026  
**Estado original:** `FAIL_MANUAL_REPRODUCED`  
**Estado atual do cenário:** `PASS_AFTER_FIX`  
**Estado do gate:** `IN_EXECUTION / REMAINING_PHYSICAL_REQUIRED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Build:** `0.1.0-pilot.1` / versionCode 2  
**PID da falha original:** `24583`  
**PID do reteste:** `28701`

## Evidência física

O fluxo recebeu duas falas separadas e produziu dois commits antes do envio:

```text
VOICE_TRANSCRIPT_COMMITTED committed_count=1 transcript_state=REVIEW
VOICE_TRANSCRIPT_COMMITTED committed_count=2 transcript_state=REVIEW
VOICE_AGENT_SUBMITTED agent_execution_count=1 agent_executions_before_send=0
```

Isso comprova que a segunda fala não foi executada antes de `Enviar`. Porém,
após o envio, a execução terminou com:

```text
ROUTING_COMPLETED duration_ms=130 route=language fast_path=false
QUERY_COMPLETED duration_ms=137 fast_path=false
RENDERED duration_ms=139
```

A UI apresentou:

```text
CAPABILITY_DISABLED
capability=ADD_CREDIT_ITEM
```

Não houve execução operacional da operação concatenada. O cenário falhou
porque o runtime classificou a solicitação final como `ADD_CREDIT_ITEM`, mas
não conseguiu encaminhá-la para uma capability ativa; a UI exibiu erro
recuperável em vez de concluir a operação.

## Reteste físico após correção

O build corrigido foi instalado no mesmo device e o fluxo foi repetido:

```text
VOICE_TRANSCRIPT_COMMITTED committed_count=1
VOICE_TRANSCRIPT_COMMITTED committed_count=2
VOICE_AGENT_SUBMITTED agent_execution_count=1 agent_executions_before_send=0
ROUTING_COMPLETED duration_ms=110 route=command fast_path=false
CAPABILITY_STARTED
ENTITY_RESOLUTION_EXACT entity_type=product candidate_count=1 match_strategy=exact
ENTITY_RESOLUTION_FUZZY entity_type=customer candidate_count=1 match_strategy=fuzzy
CAPABILITY_COMPLETED duration_ms=77
QUERY_COMPLETED duration_ms=199 fast_path=false
RENDERED duration_ms=201
```

A UI exibiu `Fiado registrado para Maria Lina`. O `CAPABILITY_DISABLED` não
reapareceu e a execução ocorreu somente após `Enviar`.

## Decisão

Falha original reproduzida e corrigida. Reteste físico: `PASS`.

O gate retorna a `IN_EXECUTION`; permanecem os demais cenários físicos
obrigatórios.
