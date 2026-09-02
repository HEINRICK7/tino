# TASK G3.5 — Correction Learning Engine

Status: PASS  
Next task allowed: YES

## Objective

Transformar correções do comerciante em evidência local, escopada e auditável,
sem permitir que uma correção isolada altere o catálogo global ou fatos do
comércio.

## Implementado

- `CorrectionEvent` com provenance, escopo, origem e timestamp;
- estados `CANDIDATE`, `LEARNED`, `TRUSTED`, `DEMOTED` e `REMOVED`;
- `CorrectionLearningPort` e `CorrectionLearningEngine` no domínio;
- promoção após evidência consistente e reforço por confirmação;
- contradição demove o alias anterior e impede resolução imediata;
- demotion e remoção preservam histórico, mas deixam de resolver o alias;
- escopo `SESSION` evita vazamento entre sessões; nenhum aprendizado global
  automático;
- `CommerceContextMemory` integrado ao engine mantendo compatibilidade com o
  interpretador existente;
- `LearnedAliasMemory` mantido como facade de compatibilidade.

## Acceptance criteria

- [x] “café maracá” cria `CANDIDATE`;
- [x] correções consistentes promovem a `LEARNED` e depois `TRUSTED`;
- [x] confirmação reforça suporte e registra provenance;
- [x] contradição reduz/despromove a associação anterior;
- [x] remoção funciona e impede resolução futura;
- [x] estado e resolução são isolados por escopo/sessão;
- [x] correção isolada nunca cria substituição global;
- [x] domínio não depende de Room ou framework;
- [x] testes focados, suíte regressiva, lint e build verdes.

## Regression guards

- aliases aprendidos não substituem nomes reais do catálogo;
- correction learning nunca cria ou altera entidades comerciais;
- alias `CANDIDATE` não é usado para resolver uma entidade;
- `DEMOTED` e `REMOVED` não são resolvíveis;
- G3.6 não começa antes de G3.5 receber PASS.

## Evidências de execução

- `CorrectionLearningEngineTest`: 4 testes focados PASS;
- cenário legado de promoção de alias: PASS;
- suíte Android: 263 testes, 0 falhas, 0 erros;
- `:app:lintDebug`: PASS;
- `:app:assembleDebug`: PASS; APK de aproximadamente 558 MB;
- device continua acumulado como `PENDING_DEVICE_VALIDATION` da G3.2;
- G3.6 está liberada.
