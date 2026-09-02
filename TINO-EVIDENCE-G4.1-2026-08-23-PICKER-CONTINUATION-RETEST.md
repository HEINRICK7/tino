# TINO — G4.1: retomada após ProductPicker — reteste aprovado

**Data:** 23/08/2026  
**Estado do cenário:** `PASS`  
**Estado do gate:** `IN_EXECUTION / REMAINING_PHYSICAL_REQUIRED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Build:** `0.1.0-pilot.1` / versionCode 2  
**PID:** `9193`

## Evidência física

Foi repetida a consulta de estoque com seleção de produto no device. Após a
seleção de `Leite em pó LeiteBom`, o log mostrou a retomada completa:

```text
CAPABILITY_STARTED
ROOM_QUERY_STARTED route=PRODUCT_FACT
ENTITY_RESOLUTION_EXACT entity_type=product candidate_count=1 match_strategy=exact
ROOM_QUERY_COMPLETED route=PRODUCT_FACT duration_ms=86
CAPABILITY_COMPLETED duration_ms=87
A2UI_READY
```

A UI saiu do estado `Consultando seus dados…` e apresentou o card correto do
produto selecionado. Não houve fallback, erro de operação global ou loading
infinito.

## Decisão

`PASS` para a retomada após `ProductPicker`.

A falha original permanece preservada em
[TINO-EVIDENCE-G4.1-2026-08-23-PICKER-CONTINUATION-FAIL.md](TINO-EVIDENCE-G4.1-2026-08-23-PICKER-CONTINUATION-FAIL.md).
O gate G4.1 ainda não é `PASS_FULL`; os cenários físicos restantes continuam
obrigatórios.
