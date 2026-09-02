# TASK G3.6 — Adaptive Lexicon

Status: PASS  
Next task allowed: YES — G3.7

## Objective

Unificar vocabulário canônico, aliases aprendidos e resolução aproximada em
uma porta de domínio determinística, melhorando referências faladas sem
transformar uma aproximação em fato comercial.

## Implementado

- `AdaptiveLexiconPort` como contrato substituível do domínio;
- `AdaptiveLexiconCandidate`, score e resultado tipados;
- catálogo canônico com aliases explícitos e aliases aprendidos;
- scoring lexical por similaridade de superfície e tokens;
- scoring fonético tolerante a variações do português;
- scoring contextual por uso recente, frequência e tela atual;
- threshold de auto-resolução, threshold de fallback e margem de ambiguidade;
- `NeedsClarification` para aproximações fracas, em vez de guessing;
- integração no `EntityResolutionService` somente depois do resolver legado;
- entidade retornada sempre vem do catálogo/Room; o léxico nunca cria, edita ou
  fornece preço, saldo, estoque ou ID;
- binding no composition root via `AdaptiveLexiconPort`;
- regressão comprovando `Maracá → Café Maratá` e preservando nomes ambíguos.

## Acceptance criteria

- [x] typo fonético `Maracá` encontra o produto persistido `Café Maratá`;
- [x] alias aprendido normalizado resolve o canonical correto;
- [x] nomes próximos de clientes continuam ambíguos;
- [x] referência sem relação retorna fallback sem fabricar entidade;
- [x] resolução antiga exact/alias/fuzzy continua prioritária;
- [x] léxico não altera catálogo, Room ou fatos comerciais;
- [x] Agent/ADK continuam recebendo referência candidata, nunca dados de
  comércio vindos do score;
- [x] testes focados, suíte regressiva, lint e build verdes.

## Evidências de execução

- `AdaptiveLexiconTest`: 4 testes PASS;
- `EntityResolutionServiceTest`: 12 testes PASS, incluindo integração
  fonética com produto persistido;
- suíte Android: 268 testes, 0 falhas, 0 erros;
- `:app:lintDebug`: PASS;
- `:app:assembleDebug`: PASS; APK de aproximadamente 558 MB;
- device continua acumulado como `PENDING_DEVICE_VALIDATION` da G3.2;
- G3.7 está liberada.

## Regression guards

- score fonético não ignora ambiguidade;
- alias `CANDIDATE`, `DEMOTED` ou `REMOVED` não é usado como alias resolvível;
- o léxico não bypassa confirmação nem executor;
- falha do adaptive path mantém o resolver legado e o fallback seguro;
- G3.7 só começa depois deste documento estar em `PASS`.
