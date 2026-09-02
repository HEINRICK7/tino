# TINO — G4.1: clientes em aberto — aprovado

**Data:** 23/08/2026  
**Estado do cenário:** `PASS_WITH_UI_EVIDENCE`  
**Estado do gate:** `IN_EXECUTION / REMAINING_PHYSICAL_REQUIRED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Build:** `0.1.0-pilot.1` / versionCode 2

## Evidência física

Entrada falada:

```text
Quem está me devendo?
```

A UI apresentou o resumo correto de fiado:

```text
Chico Filó — R$ 9,35 — Em aberto
Maria Lina — R$ 29,85 — Em aberto
```

Não houve erro de inteligência local, ProductPicker ou loading prolongado.

## Decisão

`PASS_WITH_UI_EVIDENCE` para consulta de clientes com saldo em aberto.
