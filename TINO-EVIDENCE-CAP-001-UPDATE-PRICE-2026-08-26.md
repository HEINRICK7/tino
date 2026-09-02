# TINO — Evidência CAP-001: UPDATE_PRODUCT_PRICE

**Data:** 2026-08-26  
**Estado:** `IMPLEMENTED_AUTOMATED_PENDING_QUERY_DEVICE`  
**Tipo:** mutação local, offline, com preview e confirmação obrigatória.

## Caminho integrado

```text
Gemma / AgentIntent.UPDATE_PRODUCT_PRICE
  → TinoAgentBoundary
  → MutationSafeToolExecutor
  → CommerceToolDispatcher.CHANGE_PRODUCT_PRICE
  → UpdateProductPriceUseCase
  → CommerceRepository / ProductRepository / Room
  → product.price.changed + sync local
```

- O intent aceita a referência textual do produto e `new_price_cents`; o
  preço é mantido como inteiro em centavos.
- O dispatcher resolve o produto real no Room e a prévia mostra preço atual e
  novo preço antes de qualquer alteração.
- A gravação exige o token exato da prévia; chamadas booleanas diretas não
  atravessam o `MutationSafeToolExecutor`.
- O use case reutiliza a regra de domínio existente, incluindo rejeição de
  preço inválido ou igual ao atual, evento de preço alterado e sincronização.
- O vocabulário canônico `CHANGE_PRODUCT_PRICE` é aceito como alias do
  intent `UPDATE_PRODUCT_PRICE`.

## Regressões

- parser/prompt Gemma cobrem `product_ref` e `new_price_cents`;
- perfil varejo publica `CHANGE_PRODUCT_PRICE`;
- contrato canônico aponta para `PRODUCT_PRICE_UPDATE`;
- boundary prepara a prévia sem alterar produto ou eventos;
- execução confirmada passa pelo `UpdateProductPriceUseCase`;
- A2UI reutiliza `price_change_preview` com valores atual/novo.

## Validação

```text
gradle :app:testDebugUnitTest --tests com.tino.app.core.speech.MediaPipeGemmaAgentIntentAdapterTest --tests com.tino.app.domain.agent.CanonicalCapabilityContractTest --tests com.tino.app.domain.profile.BusinessProfileTest --tests com.tino.app.domain.commerce.EntityResolutionServiceTest --tests com.tino.app.domain.commerce.CommerceRepositoryTest --no-daemon --max-workers=2
→ BUILD SUCCESSFUL

gradle :app:testDebugUnitTest --no-daemon --max-workers=2
→ 458 testes concluídos; BUILD SUCCESSFUL

gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --max-workers=2
→ BUILD SUCCESSFUL

bash tools/g3-2-smoke.sh
→ G3.2 PASS_FULL — Xiaomi 2410FPCC5G / Android 16 / API 36
```

O fluxo de voz de alteração de preço ainda não foi confirmado manualmente no
aparelho; a validação física específica permanece pendente.
