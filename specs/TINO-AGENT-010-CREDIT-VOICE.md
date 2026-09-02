# TINO — Agentic Golden Path 010: Venda fiada por voz

**Status:** IMPLEMENTADO / validação falada real pendente
**Tipo:** checkpoint de execução
**Pré-requisito:** `specs/TINO-AGENT-009-SALE-VOICE.md`
**Objetivo:** reconhecer o cliente na etapa de fiado e preservar a revisão de saldo, estoque e confirmação.

## Fluxo entregue

- A tela `Fiado` permite dizer o nome do cliente inline.
- O TINO preenche a busca e mostra a pessoa correspondente.
- A etapa seguinte continua exibindo o saldo atual e o saldo depois da compra.
- A confirmação `ANOTAR` continua sendo a única ação que grava o fiado.
- O comando global “João levou dois cafés fiado” continua protegido por
  preview, resolução de entidades e confirmação.
- O adaptador global normaliza variações de caixa do tool e quantidades faladas
  como “dois” antes de chegar ao dispatcher.

## Gates

| Gate | Evidência esperada | Status |
|---|---|---|
| `INLINE_CUSTOMER` | Cliente reconhecido na tela atual | PASS |
| `BALANCE_REVIEW` | Saldo atual/depois continuam visíveis | PASS |
| `STOCK_REVIEW` | Estoque depois continua no preview global | PASS |
| `NO_AUTO_SAVE` | Captura não grava fiado | PASS |
| `CONFIRMATION` | Mutação exige confirmação | PASS |
| `BUILD` | `testDebugUnitTest`, `assembleDebug` e `lintDebug` | PASS |
| `REAL_SPEECH` | Fluxo falado completo no aparelho | IN_PROGRESS |

## Próxima ação automática

Validar no aparelho uma frase completa de fiado, incluindo cliente, produto e
quantidade, e depois tratar correções naturais dentro do mesmo fluxo sem exigir
repetição da intenção. A última instalação/smoke launch iniciou com `pid=29375`,
sem fatal exception e com o modelo Gemma dentro do APK.
