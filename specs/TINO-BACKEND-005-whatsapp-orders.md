# TINO-BACKEND-005 — WhatsApp Orders

**Status:** Draft implementável
**Pré-requisito:** `TINO-BACKEND-003-commerce-projections.md`
**Dependências:** B002 sync, B003 projeções, porta de transcrição e gateway
WhatsApp aprovado

## 1. Fluxo

```text
WhatsApp webhook
    ↓
message_id idempotente
    ↓
conversa e identidade do cliente
    ↓
texto ou áudio
    ↓
transcrição, se áudio
    ↓
parser/intérprete
    ↓
CustomerOrder draft
    ↓
catálogo e disponibilidade
    ↓
confirmação do cliente
    ↓
pedido confirmado no comércio
    ↓
separação → pronto → retirada/entrega → concluído
```

Nenhuma mensagem recebida cria venda ou baixa estoque sem confirmação do
cliente e regra explícita de fechamento.

## 2. Identidade do cliente

Chave primária externa:

```text
channel = WHATSAPP
channel_customer_id
```

O sistema relaciona essa identidade a um `customer_id` do store somente após
matching seguro. Número de telefone não deve ser usado para alterar cliente
sem store e conversa autenticados.

Dados de conversa:

```text
conversation_id
store_id
channel_customer_id
last_message_id
state
updated_at
```

Estados de conversa:

```text
OPEN
WAITING_FOR_CLARIFICATION
WAITING_FOR_CONFIRMATION
ORDER_CONFIRMED
CLOSED
```

## 3. Idempotência de webhook

Cada webhook precisa de `message_id` único por canal.

Regras:

- webhook repetido retorna o resultado anterior;
- mesmo `message_id` com conteúdo diferente gera `CONFLICT`;
- mensagem processada não cria segundo draft nem segundo pedido;
- atualização de status do provedor é idempotente por `provider_event_id`;
- processamento ocorre depois de validar assinatura do webhook.

O gateway deve persistir o envelope recebido antes de responder sucesso ao
provedor, quando o contrato do provedor exigir confirmação rápida.

## 4. Catálogo e disponibilidade

O intérprete nunca inventa preço ou disponibilidade. Ele consulta a projeção
de produtos do B003:

```text
product_id
name
aliases
price_cents
current_quantity
sellable
```

Resultados:

```text
RESOLVED
AMBIGUOUS
NOT_FOUND
OUT_OF_STOCK
QUANTITY_UNAVAILABLE
```

Produto ambíguo exige pergunta de esclarecimento. Produto indisponível deve
ser comunicado com alternativa, sem substituir silenciosamente o item.

## 5. Pedido

Entidade mínima:

```text
order_id
store_id
customer_id
conversation_id
channel
status
fulfillment_type
address_reference
total_cents
created_at
confirmed_at
```

Itens:

```text
order_id
line_number
product_id
product_name_snapshot
quantity
unit_price_cents
```

O pedido preserva o nome e preço apresentados na confirmação. Mudança de
preço posterior não altera um pedido confirmado.

## 6. Confirmação

Antes da confirmação, o cliente recebe:

```text
itens
quantidades
preços
total
retirada ou entrega
endereço, quando aplicável
```

Aceitar apenas confirmações claras segundo a política do canal. Mensagens
ambíguas retornam ao estado `WAITING_FOR_CONFIRMATION`.

Após confirmar:

- gerar `order.created` ou evento equivalente;
- reservar estoque somente se essa política estiver aprovada;
- não registrar venda final antes da retirada/entrega ou regra de pagamento;
- permitir cancelamento com motivo e auditoria.

## 7. Retirada, entrega e pagamento

`fulfillment_type`:

```text
PICKUP
DELIVERY
```

Lifecycle mínimo:

```text
CONFIRMED
  ↓
PREPARING
  ↓
READY
  ├─ PICKUP → COLLECTED → COMPLETED
  └─ DELIVERY → OUT_FOR_DELIVERY → DELIVERED → COMPLETED
```

Falhas permitidas:

```text
CANCELLED
UNAVAILABLE
PAYMENT_REVIEW
DELIVERY_FAILED
```

Endereço, referência e localização são dados do pedido e não devem ser
inferidos ou expostos além da necessidade operacional.

## 8. Áudio e transcrição

- áudio é armazenado com `audio_message_id` e política de retenção;
- transcrição parcial nunca cria pedido;
- somente transcript committed entra na interpretação;
- falha de transcrição permite texto alternativo;
- o texto interpretado e a confirmação devem ser auditáveis sem guardar
  segredo ou conteúdo além da política de privacidade.

## 9. Mensagens de recuperação

Cada erro deve oferecer próximo passo:

```text
produto não encontrado → pedir nome ou foto/descrição alternativa
produto ambíguo       → mostrar opções
sem estoque           → sugerir outro item ou remover
endereço ausente      → pedir endereço de entrega
pedido duplicado      → mostrar pedido já existente
gateway indisponível  → preservar conversa e tentar depois
```

## 10. Eventos e integração com comércio

Eventos mínimos:

```text
order.created
order.updated
order.confirmed
order.cancelled
order.preparing
order.ready
order.collected
order.out_for_delivery
order.delivered
order.completed
```

Vendas e movimentos de estoque devem seguir as regras B003. Um pedido
confirmado não deve gerar baixa duplicada quando virar venda.

## 11. Gate de aceite do B005

- webhook assinado e idempotente;
- mensagem duplicada não duplica pedido;
- cliente e conversa são relacionados com segurança;
- catálogo não inventa preço ou estoque;
- ambiguidade e indisponibilidade têm recuperação;
- confirmação humana precede pedido confirmado;
- retirada e entrega têm estados distintos;
- cancelamento e falha preservam auditoria;
- áudio parcial não gera mutação;
- gateway indisponível preserva a conversa;
- pedido confirmado não duplica venda ou baixa de estoque.

