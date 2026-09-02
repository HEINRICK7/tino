# TINO — Agentic Golden Path 009: Venda rápida por voz

**Status:** IMPLEMENTADO / validação falada real pendente
**Tipo:** checkpoint de execução
**Pré-requisito:** `specs/TINO-AGENT-008-FOLLOW-UP-CONTEXT.md`
**Objetivo:** transformar uma fala curta em itens do carrinho sem salvar a venda automaticamente.

## Fluxo entregue

```text
PESSOA: Quero dois cafés Maratá.
  ↓
TINO: reconhece produto e quantidade
  ↓
carrinho recebe Café Maratá × 2
  ↓
pessoa revisa o carrinho
  ↓
IR PARA PAGAMENTO
```

O painel é inline na tela `Nova venda`. O produto só entra automaticamente
quando existe uma correspondência única; em caso de dúvida, a busca é
preenchida para revisão manual. A venda não é persistida durante a fala.

## Gates

| Gate | Evidência esperada | Status |
|---|---|---|
| `INLINE` | Painel de voz permanece na venda atual | PASS |
| `PRODUCT_MATCH` | Produto único é reconhecido sem escolher silenciosamente | PASS |
| `QUANTITY` | Quantidade falada é aplicada ao carrinho | PASS |
| `REVIEW` | Carrinho continua visível antes do pagamento | PASS |
| `NO_AUTO_SAVE` | Nenhuma venda é gravada pela captura | PASS |
| `BUILD` | `testDebugUnitTest`, `assembleDebug` e `lintDebug` | PASS |
| `REAL_SPEECH` | Fala real no aparelho | IN_PROGRESS |

## Próximo passo

Levar o mesmo padrão para venda fiada: reconhecer cliente, produto e
quantidade, resolver ambiguidades e mostrar saldo/estoque antes da confirmação.
