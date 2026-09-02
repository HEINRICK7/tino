# TINO-BACKEND-003 — Commerce Projections

**Status:** Draft implementável; aplicador Android multi-item alinhado
**Pré-requisito:** `TINO-BACKEND-002-sync-contracts.md`
**Objetivo:** transformar o histórico aceito de eventos em estado consultável
para produtos, estoque, vendas, clientes, fiado, fornecedores e compras.

Este documento define projeções derivadas. A autoridade é o histórico de
eventos aceitos; tabelas de projeção podem ser apagadas e reconstruídas.

## 1. Pipeline

```text
sync_events
    ↓
event validator
    ↓
projector checkpoints
    ↓
commerce projectors
    ↓
products
customers
suppliers
sales
stock_projection
credit_projection
purchase_projection
```

O projector precisa ser determinístico: mesmos eventos aceitos, mesma versão
de schema e mesma ordem lógica produzem o mesmo estado.

## 2. Armazenamento cloud

### `sync_events`

Histórico imutável do envelope B002:

```text
event_id PRIMARY KEY
event_type
schema_version
store_id
device_id
aggregate_id
occurred_at
sequence
payload_json
received_at
```

Restrições:

- `event_id` único globalmente;
- `store_id` validado contra a identidade autenticada;
- payload original preservado;
- evento aceito não é editado;
- correção de fato gera evento compensatório.

### `projection_checkpoints`

```text
projection_name
store_id
last_event_position
projector_version
updated_at
```

O checkpoint só avança depois que a transação de projeção for confirmada.

### `projection_failures`

Falhas devem registrar:

```text
event_id
projection_name
error_code
retryable
attempts
last_error
created_at
resolved_at
```

Uma projeção com falha não pode silenciosamente avançar seu checkpoint.

## 3. Produtos

Eventos:

```text
product.created
product.updated
product.price.changed
```

Projeção mínima:

```text
products
  id
  store_id
  name
  unit
  current_price_cents
  created_at
  updated_at
  version
```

Regras:

- `product.created` cria o produto uma vez;
- repetição do mesmo evento não duplica o produto;
- `product.price.changed` preserva preço anterior em histórico de auditoria;
- conflito de alteração do mesmo campo não usa last-write-wins silencioso.

## 4. Estoque

O cloud não recebe `current_stock` como verdade. Ele calcula o saldo:

```text
stock.received  +24
stock.sold       -2
stock.adjusted   -1
                       ↓
current_stock     = 21
```

Projeções:

```text
stock_movements
  movement_id
  store_id
  product_id
  event_id
  direction
  quantity
  reason
  reference_id
  occurred_at

stock_projection
  store_id
  product_id
  current_quantity
  last_event_id
  version
```

Regras:

- cada movimento referencia exatamente um evento aceito;
- aplicação é idempotente por `event_id`;
- `stock.received` soma quantidade positiva;
- `stock.sold` subtrai quantidade positiva;
- `stock.adjusted` aplica `new_quantity - previous_quantity` e exige motivo;
- não aceitar saldo negativo sem uma política explícita do produto;
- divergência de `previous_quantity` gera `CONFLICT`, não sobrescrita.

Uma venda pode produzir baixa de estoque durante a projeção de
`sale.created`, ou por `stock.sold`, mas nunca pelas duas rotas. A escolha
oficial do B003 é: `sale.created` é o fato comercial e a baixa é derivada uma
única vez pelo projector de estoque.

## 5. Vendas

Projeções:

```text
sales
  sale_id
  store_id
  total_cents
  payment_method
  customer_id
  occurred_at
  event_id

sale_items
  sale_id
  line_number
  product_id
  quantity
  unit_price_cents
```

Regras:

- uma venda aceita é imutável;
- cancelamento não apaga a venda: cria `sale.cancelled`;
- cancelamento gera movimentos compensatórios de estoque e fiado quando
  aplicável;
- total projetado deve ser verificável pela soma dos itens;
- venda `credit` exige cliente válido.

## 6. Fiado

O fiado é um ledger, não um campo mutável:

```text
credit.sale.created       +40
credit.payment.received   -20
                              ↓
balance                    = 20
```

Projeções:

```text
credit_entries
  entry_id
  store_id
  customer_id
  type
  amount_cents
  reference_id
  occurred_at
  event_id

credit_projection
  store_id
  customer_id
  balance_cents
  last_event_id
```

Regras:

- venda fiada gera lançamento positivo;
- pagamento gera lançamento negativo;
- pagamento maior que o saldo exige regra explícita; não truncar em silêncio;
- cada lançamento é idempotente por evento/entry id;
- saldo é recalculável a partir de `credit_entries`.

## 7. Clientes e fornecedores

`customer.created`, `customer.updated`, `supplier.created` e
`supplier.updated` alimentam cadastros por `store_id` e ID global.

Conflitos de nome não devem apagar registros. O projector mantém o ID global
e grava a alteração em auditoria. Matching por nome é responsabilidade de uma
camada posterior, não da projeção básica.

## 8. Compras

Projeções:

```text
purchases
purchase_items
purchase_status_history
```

Uma compra em `draft` não altera estoque. O estoque só recebe movimento quando
existir evento de recebimento físico aceito, como `stock.received`.

Fluxo:

```text
purchase.created
  ↓
purchase.updated / ordered
  ↓
mercadoria confirmada
  ↓
stock.received
```

Nota fiscal encontrada não equivale a mercadoria recebida. A projeção fiscal
será detalhada no B004.

## 9. Consultas da aplicação

As APIs de leitura devem consultar projeções por `store_id` autenticado:

```text
GET /v1/stores/{store_id}/products
GET /v1/stores/{store_id}/stock
GET /v1/stores/{store_id}/sales
GET /v1/stores/{store_id}/customers
GET /v1/stores/{store_id}/credit
GET /v1/stores/{store_id}/suppliers
GET /v1/stores/{store_id}/purchases
```

Respostas devem informar `projection_version` ou `as_of_event_position` para
permitir que clientes entendam até qual ponto o estado foi projetado.

## 10. Rebuild

Para reconstruir uma projeção:

1. selecionar `store_id` e `projection_name`;
2. criar versão temporária da projeção;
3. ler eventos aceitos em ordem determinística;
4. aplicar validações e idempotência;
5. comparar contagens, saldos e hashes com a projeção atual;
6. publicar a nova versão atomicamente;
7. atualizar checkpoint;
8. preservar falhas e divergências para auditoria.

O rebuild de estoque e fiado deve produzir um relatório de reconciliação.

## 11. Gate de aceite do B003

- projeções de produto, venda, estoque e fiado implementadas;
- replay do mesmo evento não altera o saldo duas vezes;
- estoque é reconstruído por movimentos;
- fiado é reconstruído por ledger;
- cancelamento usa compensação;
- compra em draft não altera estoque;
- conflito é observável e não sobrescrito silenciosamente;
- checkpoint não avança quando uma transação falha;
- rebuild gera resultado determinístico;
- consultas retornam a posição/versionamento da projeção.

Até esses gates passarem, B004 não deve depender de projeções cloud como se
fossem produção.
