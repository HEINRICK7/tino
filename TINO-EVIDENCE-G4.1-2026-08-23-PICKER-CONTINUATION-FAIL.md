# TINO — G4.1: retomada após ProductPicker — falha reproduzida

**Data:** 23/08/2026  
**Estado do cenário:** `FAIL_MANUAL_REPRODUCED`  
**Estado do gate:** `FAIL_MANUAL_REPRODUCED / BLOCKED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Build:** `0.1.0-pilot.1` / versionCode 2  
**PID:** `6566`

## Evidência física

Frase executada:

```text
Quanto tenho desse produto?
```

O primeiro roteamento abriu corretamente a seleção de produto:

```text
ROUTING_COMPLETED duration_ms=115 route=global fast_path=false
ENTITY_RESOLUTION_AMBIGUOUS entity_type=product candidate_count=3
QUERY_COMPLETED duration_ms=223 fast_path=false
RENDERED duration_ms=224
```

Após selecionar `Leite em pó LeiteBom`, a retomada registrou resolução exata
do produto e completou a etapa de capability:

```text
CAPABILITY_STARTED
ENTITY_RESOLUTION_EXACT entity_type=product candidate_count=1 match_strategy=exact
CAPABILITY_COMPLETED duration_ms=78
```

Depois disso não houve `QUERY_COMPLETED`, `A2UI_READY`, `RENDERED` ou estado
terminal de erro. A UI permaneceu indefinidamente em `Consultando seus dados…`.

## Diagnóstico

O ProductPicker funciona e a seleção é resolvida, mas o continuation handler
não encerra a operação após a capability retomada. O cenário não chega a uma
resposta de estoque nem a um fallback recuperável.

## Decisão

`FAIL_MANUAL_REPRODUCED / BLOCKED`.

Corrigir exclusivamente a continuação após `ProductSelected`; não iniciar
outro cenário físico nem M1 até o reteste passar.
