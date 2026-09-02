# TASK G3.3 — AgentRuntimePort + Agent Loop

Status: PASS  
Next task allowed: YES

## Objective

Criar um runtime agentic capaz de observar o resultado de uma execução, decidir
entre finalizar, esclarecer ou replanejar, sem liberar execução direta ao ADK.

## Scope

- `domain/intelligence/agent`: contratos, estados, decisões e loop;
- integração com `PlannerPort`, `IntelligencePlanValidator` e
  `IntelligencePlanExecutor`;
- telemetria de cada turn do loop;
- DI, testes e documentação da G3.3.

## Out of scope

- InteractionState completo da G3.4;
- Correction Learning, Adaptive Lexicon, UiPlanner e A2UI Surface;
- mutações novas ou execução direta pelo ADK;
- memória de negócio e RAG.

## Acceptance criteria

- [x] `AgentRuntimePort`, `AgentInteraction`, `AgentTurnResult` e `AgentDecision`;
- [x] estados `PLAN`, `EXECUTE_READ`, `OBSERVE`, `REPLAN`, `CLARIFY`,
  `REQUEST_CONFIRMATION` e `FINAL`;
- [x] limite rígido de turns/steps testado;
- [x] timeout e cancelamento preservados;
- [x] cada plano passa pelo `PlanValidator` antes do executor;
- [x] cenário observe → replan → final executável;
- [x] fallback determinístico continua operacional;
- [x] erro não cria mutação parcial;
- [x] telemetria registra todos os turns e decisões;
- [x] ADK continua sem acesso a Room/DAO/repository/handler;
- [x] testes focados, suíte regressiva, lint e build verdes.

## Regression guards

- Room permanece source of truth;
- PlanExecutor continua sendo o único executor;
- PlanValidator não pode ser bypassado;
- cancelamento não vira resposta parcial persistida;
- G3.4 não começa antes de G3.3 receber PASS.

## Evidências de execução

- `AgentRuntimeTest`: 6 testes focados, todos PASS;
- suíte Android: 255 testes, 0 falhas, 0 erros;
- fiscal core: 32 testes, 0 falhas, 0 erros;
- fiscal service: 10 testes Node, `npm run check` PASS;
- Koog spike: `gradle test` PASS;
- `:app:lintDebug`: PASS;
- `:app:assembleDebug`: PASS;
- Room: migration `10→11` adiciona os campos de telemetria do loop;
- validação física permanece acumulada como `PENDING_DEVICE_VALIDATION` da G3.2;
- G3.4 está liberada.
