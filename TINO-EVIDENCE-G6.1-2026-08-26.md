# TINO — Evidência G6.1: Predictive Replenishment Baseline

**Data:** 2026-08-26  
**Estado:** `G6.1 = IMPLEMENTED_AUTOMATED_PENDING_DEVICE`  
**Escopo:** sinais locais de estoque, recomendações explicáveis, persistência,
decisão, expiração e métricas de outcome.

## Entrega

- `LocalHeuristicRecommendationEngine` gera recomendações determinísticas de
  `STOCKOUT`, `REPLENISHMENT` e `SLOW_MOVING`.
- Cada recomendação carrega evidência, qualidade e versão das features.
- Recomendações pendentes são persistidas em Room, deduplicadas por tipo e
  produto e expiradas após o TTL definido.
- A Home permite aceitar ou rejeitar a recomendação.
- Outcomes `SHOWN`, `ACCEPTED`, `REJECTED`, `EXPIRED` e sinais de resultado
  posterior são observáveis.
- A recomendação nunca altera estoque, preço, venda ou qualquer outra verdade
  operacional.

## Gates automatizados

```text
gradle :app:testDebugUnitTest --no-daemon --max-workers=2
→ BUILD SUCCESSFUL

gradle :app:lintDebug --no-daemon --max-workers=2
→ BUILD SUCCESSFUL (0 errors; warnings existentes)

gradle :app:assembleDebug --no-daemon --max-workers=2
→ BUILD SUCCESSFUL
```

O heap padrão do Gradle foi ajustado para 4 GB porque o D8 excedeu 2 GB ao
mesclar as dependências nativas do APK.

## Smoke físico

- Device: Xiaomi 2410FPCC5G, Android 16/API 36, serial
  `69WOBUFENFLFGAJZ`.
- APK instalado incrementalmente com `adb install -r`.
- MainActivity abriu e o processo `com.tino.app` permaneceu ativo.
- Nenhum `FATAL EXCEPTION` foi encontrado no smoke.

O smoke comprova startup e estabilidade básica do APK. A aceitação/rejeição
de recomendações e a observação dos outcomes ainda precisam de uma sessão
manual específica no aparelho antes de promover G6.1 para `PASS_FULL`.
Na inspeção da Home desta sessão não havia uma recomendação pendente visível;
nenhuma decisão foi simulada nem dado local foi alterado.

## Limites

- Esta evidência não declara G6 completo.
- Não há modelo ML externo conectado; o baseline é local, determinístico e
  explicável.
- Não há mutação automática de estoque ou compra.
