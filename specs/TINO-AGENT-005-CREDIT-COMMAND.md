# TINO — Agentic Golden Path 005: Comando de fiado

**Status:** implementado; validação de fala real contínua
**Tipo:** checkpoint de execução
**Pré-requisito:** `specs/TINO-AGENT-004-PRICE-COMMAND.md`
**Objetivo:** preparar e confirmar uma venda fiada sem mutação silenciosa

## Escopo

Suportar frases naturais como:

```text
"João levou dois cafés fiado."
"Anota dois cafés para o João."
```

Fluxo obrigatório:

```text
voz
  ↓
resolve customer + product
  ↓
consult current balance and stock
  ↓
preview consequence
  ↓
human confirmation
  ↓
registerCreditSale()
  ↓
credit.sale.created + sale.created
  ↓
success local
```

## Regras

- Cliente e produto precisam ser resolvidos no catálogo local.
- Produto ambíguo ou cliente ambíguo não pode ser escolhido pelo primeiro resultado.
- Quantidade deve ser positiva e respeitar estoque disponível.
- O preview deve mostrar cliente, produto, quantidade, valor, saldo anterior
  e consequência do novo saldo quando esses dados estiverem disponíveis.
- Nenhuma dívida pode ser criada somente porque o modelo produziu JSON.
- O registro deve usar o caso de uso/repositório existente, não gravar Room no
  orquestrador.
- Se a frase disser apenas “anota R$30 para João” sem produto, não inventar
  mercadoria: retornar esclarecimento ou fallback manual até existir uma regra
  explícita para lançamento avulso.

## Gates

| Gate | Evidência esperada | Status |
|---|---|---|
| `INTENT` | `REGISTER_CREDIT_SALE` selecionada por variações naturais | PASS |
| `CUSTOMER_RESOLUTION` | Cliente existente resolvido pelo catálogo local | PASS para nome único |
| `PRODUCT_RESOLUTION` | Produto existente resolvido pelo catálogo local | PASS para nome único |
| `STOCK_VALIDATION` | `CommerceRules.saleTotal()` bloqueia estoque insuficiente | PASS |
| `PREVIEW` | Cliente, valor, saldo atual, saldo depois e estoque depois | PASS |
| `CONFIRMATION` | Nenhuma mutação antes de confirmar | PASS |
| `DOMAIN_USECASE` | `registerCreditSale()` com transação local | PASS |
| `EVENTS` | Venda e crédito geram eventos | PASS |
| `OFFLINE` | Operação local-first | PASS |
| `REAL_DEVICE` | Fala real em português | IN_PROGRESS |

## Próximo passo

Próximo: tratar resolução parcial/ambiguidade com uma surface de esclarecimento
e seguir para `TINO-AGENT-006-STOCK-RECEIPT.md`.
