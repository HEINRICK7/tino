# TINO-BACKEND-004 — Fiscal Intake

**Status:** Draft implementável
**Pré-requisito:** `TINO-BACKEND-003-commerce-projections.md`
**Objetivo:** receber NF-e, preservar o XML original, relacionar fornecedor e
produtos e somente alterar estoque após confirmação física.

## 1. Regra principal

Uma nota encontrada não é uma mercadoria recebida.

```text
NF-e recebida
    ↓
Fiscal Inbox
    ↓
XML original preservado
    ↓
parser seguro
    ↓
matching de fornecedor/produto
    ↓
conferência humana
    ↓
mercadoria confirmada
    ↓
purchase.received + stock.received
```

O parse ou a descoberta da nota nunca atualiza estoque automaticamente.

## 2. Entrada e armazenamento

Entradas aceitas:

```text
XML recebido por arquivo
XML recebido por integração fiscal
chave de acesso para consulta autorizada
```

O sistema deve preservar:

```text
fiscal_document_id
access_key
store_id
source
raw_xml
received_at
parser_version
document_status
```

`raw_xml` é imutável e deve ser armazenado com controle de acesso. Logs não
podem imprimir o XML completo ou documentos pessoais.

## 3. Dados extraídos

### Cabeçalho

```text
access_key
issuer_document
issuer_name
recipient_document
issued_at
total_cents
```

### Item

```text
line_number
product_code
barcode / GTIN
description
ncm
unit
quantity
unit_cost_cents
tax metadata quando necessária
```

Valores monetários são inteiros em centavos. Quantidades podem ser decimais
quando a unidade fiscal exigir.

O `NfeXmlParser` Android já preserva XML, fornecedor, chave, total, GTIN,
NCM, unidade, quantidade e custo unitário, além de bloquear DOCTYPE e entidades
externas.

## 4. Estados do documento

```text
RECEIVED
PARSED
MATCHING
READY_FOR_REVIEW
REVIEW_REQUIRED
CONFIRMED_RECEIVED
REJECTED
CANCELLED
FAILED
```

Transições:

```text
RECEIVED → PARSED → MATCHING → READY_FOR_REVIEW
                                  ↓
                           REVIEW_REQUIRED
                                  ↓
                       CONFIRMED_RECEIVED
```

`CONFIRMED_RECEIVED` é o único estado que pode gerar movimento de estoque.

## 5. Duplicidade

`access_key` é única por store quando presente.

Ao receber a mesma chave:

- XML idêntico: retornar o documento existente sem duplicar;
- XML diferente: marcar `FAILED`/`CONFLICT` e exigir inspeção;
- documento cancelado: não permitir novo recebimento automático;
- sem chave: usar hash do XML e manter o documento como potencial duplicata.

O hash é auxiliar. A chave de acesso é a identidade fiscal principal.

## 6. Matching de fornecedor

Ordem de matching:

1. documento fiscal do emitente;
2. relacionamento conhecido store/fornecedor;
3. nome normalizado com aprovação humana;
4. fornecedor novo sugerido, nunca criado silenciosamente.

O resultado deve registrar método, confiança e usuário/dispositivo que aprovou.

## 7. Matching de produto

Ordem de matching:

1. GTIN/barcode;
2. código do produto ligado ao fornecedor;
3. alias aprovado;
4. descrição normalizada com revisão humana;
5. produto desconhecido.

Produto desconhecido não entra no estoque. A revisão deve permitir:

- escolher produto existente;
- criar produto;
- ignorar item;
- corrigir unidade, quantidade ou custo;
- preservar a descrição fiscal original.

## 8. Confirmação de recebimento

A tela de revisão deve mostrar por item:

```text
descrição fiscal
produto TINO relacionado
quantidade
unidade
custo unitário
fornecedor
```

A confirmação gera, na mesma operação lógica:

```text
purchase.created ou purchase.updated(status=RECEIVED)
stock.received por item
fiscal.receipt.confirmed
```

O projector B003 deve aplicar estoque apenas uma vez. Se `stock.received` já
existir para o documento/item, a repetição é idempotente.

## 9. Cancelamento e correção

Nota cancelada não apaga o XML. O sistema registra:

```text
fiscal.document.cancelled
```

Se a mercadoria já tiver sido confirmada, o cancelamento não remove estoque
silenciosamente. Exige operação compensatória de estoque, com motivo e
confirmação.

Correções de matching criam eventos de relacionamento; não alteram o XML
original.

## 10. Offline

O Android pode:

- importar e preservar XML localmente;
- executar parse local;
- revisar matching já conhecido;
- registrar confirmação local e Outbox.

Consulta externa de chave, manifestação fiscal e validação que dependam de
serviço ficam pendentes para retry. A indisponibilidade externa não pode
apagar o XML nem impedir a consulta local do que já foi recebido.

## 11. Segurança

- parser bloqueia XXE/DOCTYPE;
- XML é tratado como dado não confiável;
- limites de tamanho e quantidade de itens são obrigatórios;
- documentos pessoais são redigidos em logs;
- acesso é limitado ao `store_id` autenticado;
- upload externo exige HTTPS e credencial aprovada.

## 12. Gate de aceite do B004

- XML original é preservado e recuperável;
- chave de acesso é idempotente;
- parser rejeita XXE, XML vazio e estrutura inválida;
- matching desconhecido exige revisão;
- nota encontrada não altera estoque;
- confirmação física gera eventos de compra e estoque;
- duplicidade não cria segunda entrada;
- cancelamento usa compensação, nunca exclusão silenciosa;
- offline preserva XML e intenção;
- auditoria identifica quem confirmou a entrada.

Até esses gates passarem, a UI não deve prometer integração fiscal automática.

