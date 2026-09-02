# TINO-BACKEND-002 — Sync Contracts

**Status:** Draft implementável; adapter Android alinhado
**Pré-requisito:** `TINO-BACKEND-001 — Backend Foundation.md`
**Relacionados:** `specs/ADR-001.md`, `specs/SYNC-API.md`
**Escopo:** contrato de eventos, push, pull, idempotência, ordenação e
reprocessamento

Este documento define o que sincroniza. Ele não define ainda a implementação
dos projectors de comércio, que pertence ao `TINO-BACKEND-003`.

## 1. Princípios

- o Android grava a operação localmente antes de tentar sincronizar;
- cada mudança relevante gera um evento imutável na Outbox;
- `event_id` é gerado no dispositivo e é a chave de idempotência global;
- a cloud aceita reenvio seguro do mesmo evento;
- o histórico de eventos é a entrada da projeção, não um snapshot de estoque
  enviado pelo Android;
- eventos desconhecidos são isolados e não interrompem os demais eventos do
  lote;
- nenhum evento aplicado pode ser apagado por uma resposta de retry.

## 2. Envelope canônico

Todos os eventos usam o mesmo envelope:

```json
{
  "event_id": "0190f2d4-7c32-7b8a-9f5b-2a8d4e6c1020",
  "event_type": "stock.received",
  "schema_version": 1,
  "store_id": "0190f2d4-7b31-7c10-8a20-1d4f7e9b0021",
  "device_id": "0190f2d4-7c31-7c11-8a20-1d4f7e9b0022",
  "aggregate_id": "0190f2d4-7c30-7c12-8a20-1d4f7e9b0023",
  "occurred_at": "2026-08-16T18:00:00-03:00",
  "sequence": 42,
  "payload": {}
}
```

Regras:

- todos os IDs são UUIDv7;
- `event_id` nunca muda durante retry;
- `aggregate_id` identifica o agregado afetado;
- `occurred_at` é o instante local da operação, preservado na sincronização;
- `sequence` é monotônica por dispositivo e store quando disponível; não é
  autoridade global de ordenação;
- `payload` precisa validar contra o schema da combinação `event_type` e
  `schema_version`.

## 3. Catálogo v1 de eventos

### Catálogo de entidades

```text
product.created
product.updated
product.price.changed

customer.created
customer.updated

supplier.created
supplier.updated
```

### Catálogo operacional

```text
sale.created
sale.cancelled

stock.received
stock.sold
stock.adjusted

credit.sale.created
credit.payment.received

purchase.created
purchase.updated
```

`stock.sold` pode ser produzido diretamente por uma venda local ou derivado
de `sale.created` pelo projector. O backend deve definir uma única regra de
projeção no milestone 003 para não aplicar a baixa duas vezes.

## 4. Schemas v1

Os payloads abaixo são mínimos e extensíveis apenas por campos opcionais.

### Produto

```json
{
  "product_id": "uuid",
  "name": "Café Maratá 250g",
  "unit": "un",
  "price_cents": 850
}
```

Usado por `product.created`, `product.updated` e
`product.price.changed`. `product.price.changed` deve incluir também
`previous_price_cents` e `new_price_cents`.

### Cliente

```json
{
  "customer_id": "uuid",
  "name": "João Ferreira",
  "phone": null
}
```

### Fornecedor

```json
{
  "supplier_id": "uuid",
  "name": "Distribuidora São Paulo",
  "phone": null
}
```

### Venda

```json
{
  "sale_id": "uuid",
  "items": [
    {
      "product_id": "uuid",
      "quantity": 2,
      "unit_price_cents": 850
    }
  ],
  "total_cents": 1700,
  "payment_method": "cash",
  "customer_id": null
}
```

`payment_method` v1 aceita `cash`, `pix` e `credit`. Uma venda `credit`
precisa conter `customer_id`.

### Estoque

```json
{
  "product_id": "uuid",
  "quantity": 24,
  "source": "manual",
  "reference_id": null
}
```

`stock.received` usa quantidade positiva. `stock.sold` usa quantidade
positiva no payload e o projector aplica a operação como baixa.
`stock.adjusted` exige também `previous_quantity`, `new_quantity` e `reason`.

### Fiado

```json
{
  "customer_id": "uuid",
  "sale_id": "uuid",
  "amount_cents": 1700
}
```

`credit.payment.received` usa `customer_id`, `amount_cents` e um
`payment_id` próprio. Valores monetários nunca são enviados como ponto
flutuante.

### Compra

```json
{
  "purchase_id": "uuid",
  "supplier_id": "uuid",
  "items": [
    {
      "product_id": "uuid",
      "quantity": 24,
      "unit_cost_cents": 620
    }
  ],
  "total_cost_cents": 14880,
  "status": "draft"
}
```

## 5. Push

Endpoint:

```text
POST /v1/sync/events
```

Request:

```json
{
  "events": [
    {
      "event_id": "uuidv7",
      "event_type": "stock.received",
      "schema_version": 1,
      "store_id": "uuidv7",
      "device_id": "uuidv7",
      "aggregate_id": "uuidv7",
      "occurred_at": "2026-08-16T18:00:00-03:00",
      "sequence": 42,
      "payload": {}
    }
  ]
}
```

Limites iniciais:

- lote com no máximo 100 eventos;
- evento individual com no máximo 256 KB;
- o cliente pode reenviar o lote inteiro;
- a cloud deve persistir o evento antes de responder acknowledgement.

Resposta:

```json
{
  "acknowledged_event_ids": ["uuidv7"],
  "already_processed_event_ids": ["uuidv7"],
  "rejected": [
    {
      "event_id": "uuidv7",
      "code": "SCHEMA_INVALID",
      "retryable": false,
      "message": "payload inválido para stock.received v1"
    }
  ]
}
```

Um evento rejeitado não deve ser marcado como sincronizado no Android.
Rejeições não retryable ficam em estado de erro para inspeção e
reprocessamento explícito.

## 6. Idempotência

O armazenamento cloud deve ter uma restrição única em `event_id`.

Comportamento obrigatório:

1. primeiro recebimento: persiste e retorna `acknowledged_event_ids`;
2. reenvio do mesmo evento com envelope idêntico: retorna
   `already_processed_event_ids`;
3. mesmo `event_id` com conteúdo diferente: rejeita com
   `IDEMPOTENCY_KEY_REUSE`;
4. projector nunca aplica duas vezes o mesmo `event_id`;
5. retry usa backoff exponencial com jitter no cliente e no worker cloud.

## 7. Ordenação e dependências

- a ordem global é definida pela cloud apenas depois do recebimento;
- a ordem local entre eventos do mesmo agregado deve respeitar `sequence` e
  `occurred_at`;
- eventos independentes podem ser aplicados em paralelo;
- se um evento depender de outro ainda não recebido, fica `BLOCKED` e não é
  descartado;
- o projector pode reprocessar o agregado quando o evento predecessor chegar;
- timestamp do aparelho não é usado sozinho para resolver conflito.

## 8. Pull e cursor

Endpoint:

```text
GET /v1/sync/changes?cursor=<opaque-cursor>&limit=100
```

Resposta:

```json
{
  "changes": [],
  "next_cursor": "opaque-cursor",
  "has_more": false
}
```

Regras:

- o cursor é opaco para o Android;
- o Android aplica todos os eventos retornados antes de persistir o próximo
  cursor;
- falha no meio do lote mantém o cursor anterior;
- repetir o pull é seguro;
- o servidor não reutiliza uma posição de cursor já emitida para conteúdo
  diferente.

## 9. Conflitos e multi-device

### Mesmo agregado, campos diferentes

Atualizações de campos independentes podem ser mescladas quando o schema
permitir. O resultado precisa gerar um evento de auditoria com os IDs
envolvidos.

### Mesmo campo alterado em dois dispositivos

Não usar last-write-wins silencioso para preço, estoque ou saldo de fiado.
O backend deve:

- registrar ambos os eventos;
- marcar o agregado como `CONFLICT`;
- manter a projeção determinística até a resolução;
- expor os eventos e a diferença para uma rotina de resolução.

### Operações financeiras e estoque

Vendas, pagamentos e movimentos de estoque são fatos imutáveis. Correções
devem gerar eventos compensatórios, nunca editar o evento original.

## 10. Eventos desconhecidos e versões

- `schema_version` é obrigatório;
- versões suportadas são registradas por `event_type`;
- evento desconhecido é persistido em quarentena com `UNSUPPORTED_EVENT`;
- o lote continua para os eventos conhecidos;
- adicionar campos opcionais não exige nova versão;
- remover ou mudar semântica exige nova versão;
- o projector precisa ser capaz de reprocessar eventos históricos suportados.

## 11. Retry, falha e reprocessamento

Estados mínimos da Outbox:

```text
PENDING → UPLOADING → ACKNOWLEDGED
                    ↘ RETRYABLE_FAILURE → PENDING
                    ↘ REJECTED
                    ↘ CONFLICT
```

Retryable:

- timeout;
- ausência de rede;
- HTTP 408, 425, 429 e 5xx;
- indisponibilidade temporária do serviço.

Não retryable:

- schema inválido;
- credencial revogada;
- store ou device não autorizado;
- reutilização de `event_id` com conteúdo diferente.

Todo erro deve preservar o evento original, o código, a mensagem redigida,
quantidade de tentativas e o instante da última tentativa.

## 12. Projeções derivadas

O `TINO-BACKEND-003` consumirá os eventos para gerar, no mínimo:

```text
products
customers
suppliers
sales
stock_projection
credit_projection
purchase_projection
```

Nenhuma dessas tabelas deve ser tratada como autoridade superior ao histórico
de eventos. Uma reconstrução deve conseguir apagar e recriar as projeções a
partir dos eventos aceitos.

## 13. Gate de aceite do B002

O documento só pode sair de `Draft implementável` quando existirem:

- schemas JSON versionados e testáveis;
- endpoint de push aceitando lote e idempotência;
- endpoint de pull com cursor transacional;
- teste de retry sem duplicação;
- teste de evento desconhecido sem abortar o lote;
- teste de conflito entre dois dispositivos;
- teste de reprocessamento de projeção;
- observabilidade para eventos `REJECTED`, `BLOCKED` e `CONFLICT`.

Até esses gates passarem, B003 não deve assumir que o contrato está estável.

## 14. Checkpoint de implementação

O adapter Android já está alinhado a este contrato:

- envia `event_type` no envelope;
- envia `limit=100` no pull;
- trata ACK e `already_processed_event_ids` como sincronizados;
- classifica rejeições retryable e não retryable;
- preserva eventos rejeitados para inspeção;
- coloca eventos remotos desconhecidos em `BLOCKED` sem abortar o lote.

Os testes de unidade cobrem rejeição não retryable, retry de indisponibilidade,
idempotência e quarentena de evento desconhecido. A implementação cloud e seus
testes de contrato ainda pertencem ao próximo ambiente backend.
