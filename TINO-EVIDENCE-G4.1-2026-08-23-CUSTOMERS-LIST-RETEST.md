# TINO — G4.1: lista completa de clientes — reteste aprovado

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
Me mostra todos os meus clientes.
```

O fluxo foi determinístico e local:

```text
ROUTING_COMPLETED duration_ms=118 route=fast fast_path=true
ROOM_QUERY_STARTED route=LIST_CUSTOMERS
ROOM_QUERY_COMPLETED route=LIST_CUSTOMERS duration_ms=13
CAPABILITY_COMPLETED duration_ms=15
A2UI_READY
QUERY_COMPLETED duration_ms=143 fast_path=true
RENDERED duration_ms=145
```

A UI exibiu `Chico Filó` e `Maria Lina` em “Clientes cadastrados”. Não houve
fallback para inteligência local nem loading prolongado.

## Decisão

`PASS` para listagem global de clientes.

A causa original — frase não reconhecida pelo Fast Router — foi corrigida e
confirmada fisicamente no device.
