# TINO — G4.1: estoque de produto específico — aprovado

**Data:** 23/08/2026  
**Estado do cenário:** `PASS`  
**Estado do gate:** `IN_EXECUTION / REMAINING_PHYSICAL_REQUIRED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Build:** `0.1.0-pilot.1` / versionCode 2  
**PID:** `11373`

## Evidência física

Entrada falada:

```text
Quanto tenho de Café Maratá?
```

O runtime confirmou resolução exata e consulta local:

```text
ROUTING_COMPLETED duration_ms=134 route=fast fast_path=true
ROOM_QUERY_STARTED route=PRODUCT_FACT
ENTITY_RESOLUTION_EXACT entity_type=product candidate_count=1 match_strategy=exact
ROOM_QUERY_COMPLETED route=PRODUCT_FACT duration_ms=112
CAPABILITY_COMPLETED duration_ms=113
A2UI_READY
QUERY_COMPLETED duration_ms=254 fast_path=true
RENDERED duration_ms=256
```

A UI apresentou `Café Maratá — 0 uns — Estoque zerado`, sem ProductPicker,
fallback ou loading prolongado.

## Decisão

`PASS` para consulta de estoque de produto específico.
