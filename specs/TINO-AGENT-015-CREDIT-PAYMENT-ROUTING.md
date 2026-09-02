# TINO Agent 015 — Roteamento seguro de baixa do fiado

**Status:** IMPLEMENTADO / validado
**Escopo:** roteamento determinístico de comandos de recebimento de fiado

## Entrega

O `CommandIntentRouter` reconhece frases como:

```text
Maria Lina pagou 10 reais no PIX
```

e produz apenas a intenção estruturada:

```text
REGISTER_CREDIT_PAYMENT
customer_ref = Maria Lina
amount_cents = 1000
payment_method = PIX
```

O roteador não resolve IDs, não consulta o banco e não executa a baixa. A
operação continua passando pelo preview, resolução de entidades e domínio.

## Proteções

- `recebi 10 reais no PIX` não inventa um cliente e retorna `NoMatch`;
- formas de pagamento no final da frase não são confundidas com o nome do
  cliente;
- `READ_FINANCIAL_SUMMARY` rejeita `customer_ref`, `product_ref`, `quantity` e
  `amount_cents` como campos cruzados;
- frases de fiado por item continuam produzindo `ADD_CREDIT_ITEM` sem preço,
  saldo ou estoque inventados.

## Validação

- testes unitários do roteador: PASS;
- suíte `:app:testDebugUnitTest`: PASS (155 testes);
- `:app:lintDebug`: PASS;
- `:app:assembleDebug`: PASS;
- APK instalado no dispositivo Android conectado e `MainActivity` iniciada sem
  `FATAL EXCEPTION` ou ANR.
