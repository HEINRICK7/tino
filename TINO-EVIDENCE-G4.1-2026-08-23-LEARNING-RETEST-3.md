# TINO — G4.1: reteste físico do learning — aprovado

**Data:** 23/08/2026  
**Estado do cenário:** `PASS`  
**Estado do gate:** `IN_EXECUTION / REMAINING_PHYSICAL_REQUIRED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Build:** `0.1.0-pilot.1` / versionCode 2  
**PID:** `21818`

## Fluxo executado

```text
Quanto de Café Maracá tenho?
→ edição para: Quanto de Café Maratá tenho?
→ Enviar
```

## Evidência do pipeline

```text
VOICE_CORRECTION_QUEUED transcript_state=REVIEW_EDITED
VOICE_AGENT_SUBMITTED agent_executions_before_send=0
ROUTING_COMPLETED duration_ms=116 route=fast fast_path=true
ENTITY_RESOLUTION_EXACT entity_type=product candidate_count=1
ROOM_QUERY_COMPLETED route=PRODUCT_FACT duration_ms=69
CAPABILITY_COMPLETED duration_ms=70
A2UI_READY duration_ms=0
QUERY_COMPLETED duration_ms=196 fast_path=true
RENDERED duration_ms=199
VOICE_CORRECTION_EVENT correction_status=CREATED
```

O produto resolvido foi Café Maratá e a consulta de estoque foi concluída
sem mutação operacional.

## Verificação da persistência

Foi feita uma leitura read-only do banco privado do app após a execução. O
registro persistido foi:

```text
scopeKey=default-store
memoryKey=entity alias product maraca
value=cafe marata
lifecycle=CANDIDATE
supportCount=1
```

Isso comprova a sequência completa exigida para este cenário:

```text
correção detectada ✓
correção usada ✓
execução bem-sucedida ✓
CorrectionEvent materializado ✓
learning persistido/aceito ✓
```

Não houve crash, ANR, erro de tool ou alteração indevida de produto, estoque,
cliente ou fornecedor.

## Decisão

O cenário `Maracá → Maratá` está aprovado. G4.1 ainda não é `PASS_FULL`: os
demais cenários físicos obrigatórios precisam continuar, sem iniciar M1–M8.
