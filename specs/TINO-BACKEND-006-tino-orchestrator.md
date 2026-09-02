# TINO-BACKEND-006 — TINO Orchestrator

**Status:** Implementação MediaPipe, modelo embutido e ASR Android conectados; validação em aparelho pendente
**Pré-requisitos:** B002, B003, B004 e B005
**Objetivo:** interpretar fala/texto comercial e escolher ferramentas seguras,
sem colocar regra de negócio dentro do modelo.

## 1. Arquitetura

```text
Speech-to-text (`GemmaLiveTranscriber` como porta de compatibilidade)
    ↓ transcript committed
GemmaOrchestrator
    ↓ ToolCall estruturado
ToolCallValidator
    ↓
CommerceToolDispatcher
    ↓ preview
confirmação humana
    ↓
Commerce Runtime
    ↓ local write first
resultado e evento
```

Gemma interpreta intenção e argumentos. Ele não grava banco, não calcula saldo
final, não altera estoque e não decide confirmação.

## 2. Entrada de transcrição

### Decisão atual de implementação

O TINO mantém a captura de fala atrás de `GemmaTranscriberRuntime` e
`LiveTranscriberPort`. O MediaPipe `LlmInference` recebe texto, então o app
separa explicitamente ASR da inferência Gemma. O ASR Android conectado prefere
reconhecimento no dispositivo e a UI solicita a permissão de microfone na
primeira utilização; quando o serviço não estiver disponível, a operação
manual continua sendo oferecida.

O `MediaPipeGemmaTextInference` já está registrado no DI e é compartilhado por
`MediaPipeGemmaStructuredExtractor` (voz inline) e
`MediaPipeGemmaOrchestrator` (voz global). O modelo esperado é o arquivo
`.task` em `files/models/gemma-3-1b-it-int4.task` dentro do armazenamento
privado do app.

O `VoiceViewModel` já conecta esse adapter à sessão de voz: partial/revised
aparecem como transcrição provisória, committed passa pelo GemmaOrchestrator,
preview exige confirmação e falhas oferecem retorno para operação manual.

Estados:

```text
PARTIAL
REVISED
COMMITTED
FAILED
```

Somente `COMMITTED` pode ser interpretado. Partial/revised servem para
legenda e UX, nunca para mutação.

### Voz contextual e voz global

Voz é um método de entrada transversal, não uma tela isolada. Cada tarefa
declara seu `VoiceContext` e os campos que pode receber:

```text
ONBOARDING       → store_name, owner_name, phone
PRODUCT_CREATE   → product_name, size, unit, sale_price
STOCK_RECEIPT    → product, boxes, units_per_box, quantity, unit_cost
CUSTOMER_CREATE  → name, phone
SUPPLIER_CREATE  → name, phone
CREDIT_SALE      → customer, products
SALE             → products, payment_method
GLOBAL           → intenção roteada pelo Gemma
```

O contrato Android é:

```kotlin
interface VoiceInputPort {
    suspend fun listen(context: VoiceContext): VoiceInputResult
}
```

`GemmaVoiceInputAdapter` coordena transcrição committed e extração
estruturada. O resultado preenche a UI para revisão; não salva entidade nem
executa operação. A voz global continua usando o roteamento de intenção e
preview/confirm do `GemmaOrchestrator`.

O método contextual já está conectado ao onboarding, ao cadastro de produto e
à entrada de mercadoria; cliente e fornecedor permanecem como próximos
contextos P0.

Na entrada contextual, `PARTIAL` e `REVISED` são enviados por callback para o
estado `Listening` da tela atual. Apenas `COMMITTED` dispara extração,
validação e preenchimento dos campos.

Antes de chegar à UI, toda extração passa por `VoiceExtractionValidator`:

- campos fora da allowlist do `VoiceContext` são ignorados;
- telefone é reduzido a dígitos e aceito somente entre 10 e 13 dígitos;
- preço aceita formato brasileiro (`R$ 1.234,50`) e vira valor decimal com duas
  casas;
- quantidade, caixas e unidades por caixa precisam ser inteiros positivos;
- campos ausentes ou inválidos retornam `NeedsCorrection` com os campos válidos
  preservados para revisão manual;
- nenhuma dessas etapas salva, cria ou altera uma entidade.

Assim, o Gemma propõe dados e o código decide se eles são aceitáveis para o
contexto. A tela nunca recebe um campo desconhecido nem aplica silenciosamente
um valor inválido.

Envelope mínimo:

```json
{
  "transcript_id": "uuidv7",
  "session_id": "uuidv7",
  "store_id": "uuidv7",
  "text": "João levou dois cafés fiado",
  "language": "pt-BR",
  "state": "COMMITTED",
  "occurred_at": "..."
}
```

## 3. Saída estruturada

O modelo só pode retornar uma chamada da allowlist:

```text
SEARCH_PRODUCT
SEARCH_CUSTOMER
REGISTER_SALE
REGISTER_CREDIT_SALE
REGISTER_STOCK_RECEIPT
CHECK_STOCK
GET_CUSTOMER_BALANCE
REGISTER_CREDIT_PAYMENT
GET_TODAY_SALES
PREPARE_PURCHASE
FIND_SUPPLIER
```

Formato:

```json
{
  "tool": "REGISTER_CREDIT_SALE",
  "arguments": {
    "customer": "João",
    "product": "café",
    "quantity": 2
  },
  "confidence": 0.91,
  "needs_clarification": false
}
```

Regras:

- JSON inválido vira erro recuperável;
- ferramenta fora da allowlist é rejeitada;
- argumento ausente exige esclarecimento;
- confiança não substitui confirmação;
- o modelo não pode enviar SQL, URL, código ou nome de classe como ferramenta.

## 4. Resolução de entidades

O modelo pode sugerir nome. A resolução final usa o catálogo local/cloud
projetado:

```text
nome falado
  ↓
busca exata
  ↓
alias aprovado
  ↓
ambiguidade
  ↓
pergunta de esclarecimento
```

Se houver mais de um produto ou cliente possível, não escolher pelo primeiro
resultado. A UI deve apresentar as opções ou pedir uma nova fala.

## 5. Consulta versus mutação

Consultas podem responder após validação local:

```text
SEARCH_PRODUCT
SEARCH_CUSTOMER
CHECK_STOCK
GET_CUSTOMER_BALANCE
GET_TODAY_SALES
FIND_SUPPLIER
```

Mutações sempre passam por preview e confirmação:

```text
REGISTER_SALE
REGISTER_CREDIT_SALE
REGISTER_STOCK_RECEIPT
REGISTER_CREDIT_PAYMENT
PREPARE_PURCHASE
```

A confirmação deve mostrar entidade, quantidade, valor e efeito esperado.

## 6. Tool execution

O dispatcher é a fronteira entre interpretação e domínio.

Antes de executar:

- validar argumentos e tipos;
- resolver IDs contra o catálogo;
- conferir estoque, saldo e cliente no runtime;
- calcular valores no domínio;
- verificar confirmação humana;
- executar transação local;
- produzir evento da Outbox.

O modelo nunca pode fornecer o total final como autoridade. Valores são
calculados pelo `CommerceRepository`/runtime.

## 7. Erros e esclarecimentos

Tipos mínimos:

```text
TRANSCRIPT_NOT_COMMITTED
MODEL_UNAVAILABLE
INVALID_TOOL_CALL
MISSING_ARGUMENT
AMBIGUOUS_PRODUCT
AMBIGUOUS_CUSTOMER
PRODUCT_NOT_FOUND
CUSTOMER_NOT_FOUND
INSUFFICIENT_STOCK
INVALID_AMOUNT
CONFIRMATION_REQUIRED
DOMAIN_REJECTED
```

Cada erro deve conservar a intenção original e oferecer um próximo passo.
Nenhum erro executa uma ferramenta alternativa silenciosamente.

## 8. Segurança e limites

- timeout de interpretação;
- limite de tamanho do transcript;
- limite de quantidade e valor antes do runtime;
- ferramentas e argumentos auditáveis;
- redaction de dados sensíveis;
- nenhum prompt externo pode alterar a allowlist;
- confirmação humana não pode ser inferida de uma fala anterior diferente.

## 9. Observabilidade

Registrar de forma redigida:

```text
transcript_id
tool_call_id
tool
validation_result
confirmation_result
execution_result
latency
error_code
```

Não registrar token, prompt secreto ou conteúdo sensível desnecessário.

## 10. Gate de aceite do B006

- partial/revised nunca mutam domínio;
- saída do modelo é validada por schema e allowlist;
- ambiguidade não seleciona primeiro resultado;
- consulta e mutação têm políticas distintas;
- toda mutação mostra preview e exige confirmação;
- total, estoque e saldo vêm do runtime;
- indisponibilidade do modelo permite operação manual quando aplicável;
- erros preservam intenção e oferecem recuperação;
- tool calls são auditáveis;
- testes cobrem prompt inválido, ferramenta proibida e confirmação ausente.
