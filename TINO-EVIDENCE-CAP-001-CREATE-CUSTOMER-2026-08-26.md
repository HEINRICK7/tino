# TINO — Evidência CAP-001: CREATE_CUSTOMER

**Data:** 2026-08-26  
**Estado:** `IMPLEMENTED_AUTOMATED_PENDING_QUERY_DEVICE`  
**Tipo:** mutação local, offline, com preview e confirmação obrigatória.

## Caminho integrado

```text
Gemma / AgentIntent.CREATE_CUSTOMER
  → TinoAgentBoundary
  → MutationSafeToolExecutor
  → CommerceToolDispatcher
  → CreateCustomerUseCase
  → CommerceRepository / CustomerRepository / Room
  → customer.created + sync local
```

- O classificador fornece apenas `customer_name` e o telefone opcional; não
  inventa identificadores nem grava diretamente.
- O boundary transforma o intent em `customer.create` e prepara a prévia.
- A prévia mostra nome e telefone sem alterar clientes ou eventos.
- A gravação só ocorre após autorização pelo token exato da prévia; o
  `MutationConfirmationService` consegue reidratar a operação persistida.
- O use case retorna o cliente realmente criado e o repositório mantém o
  evento de domínio e o agendamento de sincronização já existentes.

## Regressões

- prompt e parser Gemma cobrem `customer_name` e `phone`;
- contrato canônico aponta `CREATE_CUSTOMER` para `CUSTOMER_CREATE`;
- perfil de clientes e varejo publica a capability;
- boundary consulta a ferramenta local e não muta antes da confirmação;
- dispatcher cria o cliente somente na execução confirmada;
- mutation safety mapeia a operação para `CREATE_CUSTOMER` e bloqueia bypass.

## Validação

```text
gradle :app:testDebugUnitTest --tests com.tino.app.core.speech.MediaPipeGemmaAgentIntentAdapterTest --tests com.tino.app.domain.agent.CanonicalCapabilityContractTest --tests com.tino.app.domain.profile.BusinessProfileTest --tests com.tino.app.interfaceadapter.a2ui.A2uiIntentDrivenTest --tests com.tino.app.domain.commerce.EntityResolutionServiceTest --tests com.tino.app.domain.commerce.CommerceRepositoryTest --tests com.tino.app.domain.voice.MutationSafetyTest --no-daemon --max-workers=2
→ BUILD SUCCESSFUL

gradle :app:testDebugUnitTest --no-daemon --max-workers=2
→ 458 testes concluídos; BUILD SUCCESSFUL

gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --max-workers=2
→ BUILD SUCCESSFUL

bash tools/g3-2-smoke.sh
→ G3.2 PASS_FULL — Xiaomi 2410FPCC5G / Android 16 / API 36
```

O fluxo de voz de cadastro ainda não foi confirmado manualmente no aparelho;
a validação física específica permanece pendente.
