# TINO Sync API v1

Contrato de transporte. A definição completa de envelopes, schemas, eventos,
conflitos e reprocessamento está em
[`TINO-BACKEND-002-sync-contracts.md`](TINO-BACKEND-002-sync-contracts.md).

Contrato mínimo consumido pelo Android. O servidor deve exigir TLS, autenticação
do dispositivo e tratar `event_id` como chave idempotente.

## Push

`POST /v1/sync/events`

```json
{
  "events": [
    {
      "event_id": "uuidv7",
      "store_id": "uuidv7",
      "device_id": "uuidv7",
      "aggregate_id": "uuidv7",
      "event_type": "sale.created",
      "schema_version": 1,
      "occurred_at": 1776373200000,
      "sequence": 42,
      "payload": {}
    }
  ]
}
```

Resposta `200`:

```json
{
  "acknowledged_event_ids": ["uuidv7"],
  "already_processed_event_ids": [],
  "rejected": []
}
```

Reenviar o mesmo evento deve devolvê-lo como `already_processed`, sem
duplicar o efeito no servidor. Um mesmo `event_id` com conteúdo diferente
deve ser rejeitado.

O lote aceita no máximo 100 eventos. Eventos rejeitados devem informar código,
mensagem redigida e se o erro é retryable. Uma rejeição não deve interromper o
processamento dos demais eventos do lote.

## Pull

`GET /v1/sync/changes?cursor=<opaque-cursor>&limit=100`

Resposta `200`:

```json
{
  "changes": [],
  "next_cursor": "opaque-cursor",
  "has_more": false
}
```

O cursor só avança depois que o Android aplicar todos os eventos retornados.
Falha no meio do lote mantém o cursor anterior; repetir o pull é seguro.
