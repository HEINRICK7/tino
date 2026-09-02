# TINO — G4.1: reteste físico do learning — segunda execução

**Data:** 23/08/2026  
**Estado do cenário:** `FAIL`  
**Estado do gate:** `FAIL_MANUAL_RETEST / BLOCKED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Build:** `0.1.0-pilot.1` / versionCode 2  
**PID:** `16106`

## Fluxo executado

Foi repetido o cenário físico solicitado:

```text
Quanto de Café Maracá tenho?
→ edição para: Quanto de Café Maratá tenho?
→ Enviar
```

## Evidência observada

O log confirmou que a correção foi enfileirada e usada na consulta correta:

```text
VOICE_CORRECTION_QUEUED transcript_state=REVIEW_EDITED
VOICE_AGENT_SUBMITTED agent_executions_before_send=0
ROUTING_COMPLETED duration_ms=69 route=fast fast_path=true
ENTITY_RESOLUTION_EXACT entity_type=product candidate_count=1
ROOM_QUERY_STARTED route=PRODUCT_FACT
ROOM_QUERY_COMPLETED route=PRODUCT_FACT duration_ms=58
CAPABILITY_COMPLETED duration_ms=59
A2UI_READY duration_ms=0
QUERY_COMPLETED duration_ms=134 fast_path=true
RENDERED duration_ms=136
```

O produto resolvido foi o correto e a consulta operacional terminou com
sucesso. Não houve evidência de mutação indevida em produto, estoque, cliente
ou fornecedor.

## Falha do critério de learning

Não foi registrado:

```text
VOICE_CORRECTION_EVENT
```

Também não houve evidência no log de learning persistido/aceito após a
execução bem-sucedida. Portanto, a sequência exigida continua incompleta:

```text
correção detectada ✓
correção usada ✓
execução bem-sucedida ✓
CorrectionEvent ✗
learning persistido ✗
```

## Decisão

`G4.1 = FAIL_MANUAL_RETEST / BLOCKED`.

O cenário de learning falhou novamente. Esta execução não altera o código,
não inicia M1–M8 e não autoriza qualquer outro gate. A próxima ação deve ser
uma correção limitada na materialização de `CorrectionEvent`/learning,
seguida de regressão automatizada e novo reteste físico do mesmo fluxo.
