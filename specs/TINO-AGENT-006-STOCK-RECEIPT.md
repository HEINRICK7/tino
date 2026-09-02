# TINO — Agentic Golden Path 006: Entrada de mercadoria

**Status:** implementação concluída; gates em execução
**Tipo:** checkpoint de execução contínua
**Pré-requisito:** `specs/TINO-AGENT-005-CREDIT-COMMAND.md`
**Próximo documento:** `specs/TINO-AGENT-007-AMBIGUITY.md`

## Objetivo

Suportar:

```text
"Chegou uma caixa de Café Maratá com 24 unidades."
```

O TINO deve preparar a entrada, mostrar o estoque antes/depois, permitir
conferência e só então registrar o movimento local.

## Implementação

- `REGISTER_STOCK_RECEIPT` permanece uma mutação com confirmação.
- O preview valida quantidade positiva e custo não negativo.
- O preview mostra produto, quantidade, custo, estoque atual, estoque depois e fornecedor quando houver.
- A execução continua usando `CommerceRepository.registerStockReceipt()`.
- A transação cria compra, item de compra, movimento de estoque e evento local.

## Gates

| Gate | Evidência | Status |
|---|---|---|
| `INTENT` | Tool `REGISTER_STOCK_RECEIPT` na allowlist | PASS |
| `PRODUCT_RESOLUTION` | Produto existente resolvido no catálogo | PASS para nome único |
| `QUANTITY` | Quantidade positiva validada | PASS |
| `PREVIEW` | Estoque antes/depois e custo visíveis | PASS |
| `CONFIRMATION` | Sem mutação antes de confirmar | PASS |
| `EVENTS` | Compra, movimento e evento local | PASS |
| `OFFLINE` | Operação local-first | PASS |
| `BUILD` | Suite, assemble e lint | PASS |
| `REAL_DEVICE` | Fala em português ainda requer validação manual | IN_PROGRESS |

## Próximo passo automático

Executar os gates e criar a surface de esclarecimento para produto/cliente não
resolvido, sem escolher silenciosamente o primeiro resultado.
