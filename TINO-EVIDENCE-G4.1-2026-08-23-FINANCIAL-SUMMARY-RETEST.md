# TINO — G4.1: resumo financeiro composto — reteste aprovado

**Data:** 23/08/2026  
**Estado do cenário:** `PASS`  
**Estado do gate:** `IN_EXECUTION / REMAINING_PHYSICAL_REQUIRED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Build:** `0.1.0-pilot.1` / versionCode 2  
**PID:** `13335`

## Evidência física

Entrada falada:

```text
Quanto eu recebi hoje no Pix e no dinheiro e quanto ainda tenho para receber?
```

O runtime concluiu o fast path e renderizou a composição financeira:

```text
ROUTING_COMPLETED duration_ms=134 route=fast fast_path=true
CAPABILITY_COMPLETED duration_ms=53
A2UI_READY
QUERY_COMPLETED duration_ms=198 fast_path=true
RENDERED duration_ms=201
```

A UI apresentou corretamente:

```text
Resumo financeiro de hoje
Recebido hoje — R$ 28,20
Dinheiro — R$ 0,00
PIX — R$ 28,20
A receber — R$ 39,20
```

## Decisão

`PASS` para o resumo financeiro composto. A inconsistência anterior entre o
título “Entrou hoje” e o valor “A receber” foi corrigida.
