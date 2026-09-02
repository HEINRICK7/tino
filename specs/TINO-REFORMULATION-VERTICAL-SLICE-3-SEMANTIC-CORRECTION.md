# TINO — Vertical Slice 3: Correção Semântica

Status: aprovado e implementado

## Decisão

`payment_method` permanece opcional no evento `credit.payment.received` e o
`schemaVersion` continua em `1`.

Quando o campo estiver ausente, o applier interpreta o pagamento como
`unknown`. Ausência de informação nunca é convertida em `cash`.

## Banco local

A migration `4 → 5` adiciona `credit_entries.paymentMethod` com default
`unknown`. Registros antigos do tipo `SALE` são marcados como `credit`; os
registros antigos do tipo `PAYMENT` permanecem `unknown`.

Pagamentos novos precisam informar `cash`, `pix` ou `card`. `unknown` e
`credit` são inválidos para uma nova operação de pagamento de dívida.

## Projeções

Os pagamentos de dívida são projetados diretamente de `CreditEntryEntity`:

- total recebido: soma de `-amountCents` para `PAYMENT`;
- recebido por método: mesma soma filtrada por `paymentMethod`;
- `unknown` não entra em `cash`.

Nenhum `DirectReceipt` é criado para o pagamento de dívida.

## Compatibilidade

Eventos antigos sem `payment_method` continuam sendo aceitos e classificados
como `unknown`. Retry, replay remoto e snapshots antigos permanecem
idempotentes e restauráveis.
