# TASK G3.2 — ADK Evaluation & Persistent Observability

Status: PASS_FULL  
Next task allowed: YES

## Objective

Tornar o planejamento ADK mensurável, comparável ao planner determinístico e
diagnosticável no runtime local, sem permitir que telemetria ou eval alterem a
fonte de verdade comercial.

## Scope

- `domain/intelligence`: contrato de telemetria, eventos e avaliador A/B;
- `core/database`: entidade, DAO, repository e migration da telemetria;
- `core/intelligence`: separação do adapter ADK;
- runtime/DI, testes e documentação do Gate 3.2.

## Out of scope

- Agent Loop G3.3;
- memória de negócio, learning, lexicon adaptativo e RAG;
- execução direta de tools pelo ADK;
- alteração das regras transacionais do Room.

## Acceptance criteria

- [x] evento persistente por request com `requestId` e `sessionId`;
- [x] `plannerSelected`, `plannerUsed` e fallback distinguíveis;
- [x] planning latency, validation result, fallback reason e execution result;
- [x] grounding completeness e estágio de erro;
- [x] número/ordem de steps e rejeições classificadas;
- [x] corpus A/B reproduzível contra planner determinístico e ADK;
- [x] telemetria offline e incapaz de impedir a operação principal;
- [x] ADK continua sem acesso a Room/DAO/repository/handler;
- [x] testes focados, regressão, lint, build e evidências atualizadas;
- [x] APK/device validado fisicamente — `PASS_FULL`.

## Regression guards

- `PlanValidator` continua obrigatório;
- Room permanece source of truth;
- fallback determinístico continua operacional;
- mutações continuam fora do caminho de planejamento ADK;
- golden flows de inteligência continuam verdes.

## Validation evidence

- focused tests: PASS;
- app regression at current closure: 277 passed / 0 failed / 0 errors;
- fiscal module: 32 passed / 0 failed / 0 errors;
- architecture/fitness tests: PASS dentro da suíte app;
- agent evals: PASS — corpus Gate 3.2 reproduzível, sem execução de tools;
- lint: PASS (`:app:lintDebug`);
- build: PASS (`:app:assembleDebug`);
- APK: PASS — `app/build/outputs/apk/debug/app-debug.apk` (558 MB);
- device: PASS_FULL — Xiaomi 2410FPCC5G, Android 16/API 36; instalação
  incremental sem apagar dados, abertura da MainActivity, Room existente
  reaberto e processo observado por 5 segundos sem crash.

## Decision

PASS_FULL: implementação, validação automatizada e smoke físico passaram. A
correção necessária foi tornar `MIGRATION_8_9` compatível com o schema 9 real;
o device confirmou a abertura do banco legado e a migração até a versão atual
sem apagar dados. Os caminhos determinístico, ADK e fallback permanecem
protegidos pelos testes automatizados do Gate 3.2.
