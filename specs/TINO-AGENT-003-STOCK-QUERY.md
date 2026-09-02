# TINO — Agentic Golden Path 003: Consulta de estoque

**Status:** implementado; validação de fala real contínua
**Tipo:** checkpoint de execução contínua
**Pré-requisito:** `specs/TINO-AGENT-002-CREDIT-QUERY.md`
**Próximo documento:** `specs/TINO-AGENT-004-PRICE-COMMAND.md`

## Objetivo

Responder perguntas locais como:

```text
"Quantos cafés ainda tem?"
"Tem Café Maratá no estoque?"
```

O agente deve resolver o produto pelo nome falado, consultar o saldo de
movimentos localmente e mostrar a quantidade sem confirmação.

## Implementação

- `CHECK_STOCK` é uma tool read-only da allowlist.
- `CommerceToolDispatcher` resolve o produto pelo catálogo local.
- `CommerceRepository.stockBalance(product.id)` é a autoridade operacional.
- A resposta usa a superfície nomeada `Estoque`.
- Nenhum estoque é alterado por uma consulta.

## Gates

| Gate | Evidência | Status |
|---|---|---|
| `INTENT` | Tool `CHECK_STOCK` na allowlist | PASS |
| `RESOLUTION` | Produto resolvido no repositório local | PASS |
| `QUERY` | Saldo lido sem mutação | PASS |
| `SURFACE` | Resultado nomeado `Estoque` | PASS |
| `NO_CONFIRMATION` | Query retorna `AnswerReady` | PASS |
| `OFFLINE` | Consulta usa Room local | PASS |
| `BUILD` | Suite, assemble e lint passam | PASS |
| `REAL_DEVICE` | APK abre no aparelho; fala real ainda requer validação manual | IN_PROGRESS |

## Próximo passo automático

Executar os gates e iniciar `TINO-AGENT-004-PRICE-COMMAND.md`, que será a
primeira mutação de alto risco com preço real, preview, confirmação e evento.
