# TASK G3.7 — UiPlanner

Status: PASS  
Next task allowed: YES — G3.8

## Objective

Separar a decisão semântica de apresentação do renderer e do protocolo A2UI.
O planner recebe somente contexto controlado e resultado grounded; ele não
conhece Compose, Activity, NavController, Room, tools ou JSON de superfície.

## Implementado

- `GroundedResult` e `GroundedEvidence` como entrada de domínio;
- `UiContext` sem objetos Android ou componentes de apresentação;
- `UiPlannerPort` como boundary substituível;
- decisões tipadas `TEXT`, `CREATE_SURFACE`, `UPDATE_SURFACE`, `REQUEST_INPUT`,
  `REQUEST_CLARIFICATION`, `REQUEST_CONFIRMATION`, `SHOW_RESULT`,
  `SHOW_ERROR` e `NO_UI`;
- `DeterministicUiPlanner` com fallback seguro e decisão baseada em status,
  evidência, hint semântico, inputs ausentes e confirmação;
- `FallbackUiPlanner` para manter uma política determinística quando um planner
  opcional falhar;
- composição de `IntelligenceResponse` para `GroundedResult` sem gerar JSON
  A2UI;
- binding no composition root via `UiPlannerPort`;
- nenhuma dependência do planner para renderer, Compose, A2UI ou infraestrutura.

## Acceptance criteria

- [x] resposta simples grounded vira `TEXT`;
- [x] comparação/lista/ranking grounded vira `CREATE_SURFACE`;
- [x] surface semântica existente vira `UPDATE_SURFACE`;
- [x] entidade ambígua vira `REQUEST_CLARIFICATION`;
- [x] mutação preparada vira `REQUEST_CONFIRMATION`;
- [x] slot ausente vira `REQUEST_INPUT`;
- [x] dados insuficientes viram `SHOW_ERROR`, sem insight inventado;
- [x] resultado vazio vira `NO_UI`;
- [x] planner opcional com falha usa fallback determinístico;
- [x] domínio não consulta Room, não executa tools/mutation e não conhece
  Compose, renderer ou JSON A2UI;
- [x] testes focados, suíte regressiva, lint e build verdes.

## Evidências de execução

- `UiPlannerTest`: 9 testes PASS;
- suíte Android: 285 testes, 0 falhas, 0 erros;
- `:app:lintDebug`: PASS;
- `:app:assembleDebug`: PASS; APK de aproximadamente 558 MB;
- G3.2 foi promovida a `PASS_FULL` no Xiaomi 2410FPCC5G/API 36 após smoke
  incremental, reabertura do Room e observação sem crash;
- G3.8 está liberada e foi concluída em `PASS_FULL`.

## Regression guards

- UiPlanner não produz `A2uiMessage`, JSON, Compose ou componentes Android;
- somente resultado grounded pode gerar surface semântica;
- `INSUFFICIENT_DATA`, `ERROR` e `UNSUPPORTED` nunca viram insight;
- confirmação continua sendo decisão, não execução;
- G3.8 só começa depois deste documento estar em `PASS`.
