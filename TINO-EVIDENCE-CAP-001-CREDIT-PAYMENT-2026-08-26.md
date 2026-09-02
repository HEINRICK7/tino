# TINO — Evidência CAP-001: REGISTER_CREDIT_PAYMENT

**Data:** 2026-08-26  
**Estado:** `IMPLEMENTED_AUTOMATED_PENDING_QUERY_DEVICE`  
**Tipo:** mutação local, offline, com preview e confirmação obrigatória.

## Caminho integrado

```text
CommandIntentRouter / Gemma
  → AgentIntent.REGISTER_CREDIT_PAYMENT
  → TinoAgentBoundary
  → MutationSafeToolExecutor
  → CommerceToolDispatcher
  → RegisterCreditPaymentUseCase
  → CommerceRepository / CreditLedger / Room
```

- O classificador fornece somente referência textual, valor e forma de
  pagamento; entidade, saldo e operação são resolvidos localmente.
- O preview consulta o cliente no Room e mostra saldo atual e saldo projetado.
- A mutação não ocorre no `ask`: exige o token do preview através do
  `MutationSafeToolExecutor`.
- O `operationId` é gerado/preservado no domínio e mantém idempotência do
  lançamento e do evento financeiro.
- Os aliases canônicos `RECEIVE_CREDIT_PAYMENT` e
  `READ_CUSTOMER_BALANCE` emitidos pelo prompt TINO são normalizados para os
  nomes aceitos pelo `AgentIntent`.

## Regressões

- preview real contra Room sem alteração de créditos ou eventos;
- confirmação e idempotência continuam cobertas pelos testes de mutation
  safety e `CommerceRepository`;
- contrato canônico aponta para `RECEIVE_CREDIT_PAYMENT` e `CREDIT_PAYMENT`;
- ausência de cliente, método ou valor não permite execução.

## Validação

```text
gradle :app:testDebugUnitTest --tests com.tino.app.core.speech.MediaPipeGemmaAgentIntentAdapterTest --tests com.tino.app.domain.agent.AgenticQueryTest --tests com.tino.app.domain.commerce.EntityResolutionServiceTest --tests com.tino.app.domain.voice.MutationSafetyTest --no-daemon --max-workers=2
→ BUILD SUCCESSFUL

gradle :app:lintDebug :app:assembleDebug --no-daemon --max-workers=2
→ BUILD SUCCESSFUL

bash tools/g3-2-smoke.sh
→ G3.2 PASS_FULL — Xiaomi 2410FPCC5G / API 36
```

O fluxo de voz de pagamento ainda não foi confirmado manualmente no aparelho;
a validação física específica permanece pendente.
