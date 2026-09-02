# TINO — Agentic Golden Path 004: Alteração de preço

**Status:** implementação concluída; gates em execução
**Tipo:** checkpoint de execução contínua
**Pré-requisito:** `specs/TINO-AGENT-003-STOCK-QUERY.md`
**Próximo documento:** `specs/TINO-AGENT-005-CREDIT-COMMAND.md`

## Objetivo

Suportar com segurança:

```text
"Muda o Café Maratá para oito e setenta e cinco."
```

O TINO deve consultar o preço real, mostrar a consequência, aguardar
confirmação explícita e só então alterar o produto localmente.

## Fluxo

```text
CHANGE_PRODUCT_PRICE
  ↓
resolve product
  ↓
load current price
  ↓
normalize new_price_cents
  ↓
preview current → new
  ↓
human confirmation
  ↓
CommerceRepository.changeProductPrice()
  ↓
product.price.changed
  ↓
outbox/sync pending
```

## Implementação

- Nova tool específica `CHANGE_PRODUCT_PRICE`.
- Preço novo chega ao domínio em centavos inteiros.
- Preview exibe preço atual real e preço novo.
- Confirmação continua obrigatória porque a operação altera estado comercial.
- Mutação atualiza produto e cria `product.price.changed` na mesma transação.
- O evento preserva `previous_price_cents` e `new_price_cents`.
- O Gemma não acessa banco, não calcula preço final e não executa a alteração.

## Gates

| Gate | Evidência | Status |
|---|---|---|
| `INTENT` | Tool específica na allowlist | PASS |
| `ACTUAL_PRICE_CHECK` | Preview busca produto e preço real | PASS |
| `NO_SILENT_MUTATION` | Coordinator retorna `PreviewReady` | PASS |
| `CONFIRMATION` | `confirm()` é necessário para executar | PASS |
| `DOMAIN_USECASE` | Repository valida preço e produto | PASS |
| `EVENT` | `product.price.changed` preserva antes/depois | PASS |
| `OUTBOX` | Evento é gravado na transação local e sync é agendado | PASS |
| `BUILD` | Suite, assemble e lint após implementação | PASS |
| `REAL_DEVICE` | Fala real ainda requer validação manual | IN_PROGRESS |

## Débitos

- Resolver múltiplos produtos com o mesmo nome antes de permitir confirmação.
- Criar surface dedicada de alteração de preço em vez de preview textual genérico.
- Adicionar observabilidade de `command.previewed`, `command.confirmed` e `command.cancelled`.

## Próximo passo automático

Executar os gates e seguir para o comando de fiado, mantendo confirmação antes
de qualquer lançamento de crédito.
