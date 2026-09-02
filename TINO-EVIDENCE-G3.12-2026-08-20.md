# TINO — Evidência G3.12

**Data:** 20/08/2026  
**Device:** Xiaomi 2410FPCC5G / API 36  
**ADB:** `69WOBUFENFLFGAJZ`  
**Pacote:** `com.tino.app`

## Automação

- `gradle :app:testDebugUnitTest`: PASS — 318 casos do app;
- `gradle :app:lintDebug`: PASS;
- `gradle :app:assembleDebug`: PASS;
- APK: `app/build/outputs/apk/debug/app-debug.apk`;
- testes de Interaction State, Room store, A2UI e restauração de snapshot:
  PASS;
- suíte fiscal existente permaneceu verde.

## Smoke físico

| Ação | Evidência |
|---|---|
| `adb devices` | `69WOBUFENFLFGAJZ device` |
| instalação | `adb install -r` retornou `Success` |
| startup | `MainActivity` aberta e processo ativo via `pidof` |
| seed de sessão | `Tela: CUSTOMER_DETAIL`, `Maria`, `READ_CUSTOMER_BALANCE`, `g312-memory-surface` |
| seed de working | `ADD_CREDIT_ITEM`, slots customer/product/quantity e clarificação de pagamento |
| restart sem limpar dados | entidade, objetivo, surface, rascunho e clarificação restaurados |
| limpeza | working voltou a “nenhuma”; Maria e objetivo permaneceram |
| estabilidade | sem FATAL EXCEPTION, ANR ou erro SQLite no logcat |

## Falha encontrada e corrigida durante o gate

O primeiro smoke restaurava o rascunho, mas perdia entidade e objetivo quando a
navegação regravava o snapshot. A causa era a conversão `InteractionState` →
`snapshot` não projetar `SessionMemory` de volta aos campos de contexto legados.

A correção foi aplicada em `InteractionState.toSnapshot()` e coberta por teste
de regressão. O smoke foi repetido com instalação incremental e passou.

## Conclusão

Todos os critérios executáveis e físicos da G3.12 foram comprovados. Status:
`PASS_FULL`.
