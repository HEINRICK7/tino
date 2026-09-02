# TASK G3.10 — A2UI Actions → Agent Loop

**Data:** 20/08/2026  
**Status:** `PASS_FULL`  
**Próxima task liberada:** G3.11 — Mutation Safety & Confirmation

## Objetivo

Fechar o circuito de entrada da A2UI sem permitir que renderer ou componente
execute Room, DAO, repository, handler ou mutação diretamente.

```text
A2UI component
      ↓
A2uiActionEvent
      ↓
ActionValidator
      ↓
AgentRuntimePort
      ↓
InteractionState / IntelligenceRequest
      ↓
Planner + executor
      ↓
mesma surfaceId
```

## Implementado

- `A2uiActionEvent` tipado com `surfaceId`, `componentId`, `actionName`,
  `payload` e `sessionId`;
- `TinoActionDescriptor` separa ações `UI_LOCAL` de ações `AGENT`;
- catálogo declara payload permitido e obrigatório por ação;
- `A2uiActionValidator` bloqueia surface, componente, sessão, ação e payload
  inválidos;
- `A2uiActionRouter` envia somente ações validadas ao adapter;
- `A2uiActionRuntimeBridge` converte o evento em `IntelligenceRequest` e chama
  exclusivamente `AgentRuntimePort`;
- `TinoA2UiSurfaceHost` apenas emite eventos declarativos; não conhece regra
  comercial nem executa domínio;
- ações e payloads são preservados no codec A2UI;
- ações A2UI conhecidas usam o planner determinístico dentro do mesmo
  `AgentRuntimePort`, evitando inferência local desnecessária; ADK continua
  disponível para perguntas não conhecidas;
- `confirm_operation` apenas retorna intenção ao runtime nesta task; commit e
  policy universal permanecem na G3.11.

## Critérios de aceite

- [x] evento tipado;
- [x] schema de payload;
- [x] ação desconhecida bloqueada;
- [x] `componentId` inválido bloqueado;
- [x] `surfaceId` inválida bloqueada;
- [x] `sessionId` inválida bloqueada;
- [x] evento validado retorna ao `AgentRuntimePort`;
- [x] contexto da sessão preservado;
- [x] mesma surface pode ser atualizada;
- [x] renderer sem acesso direto a Room/DAO/handler/mutação;
- [x] testes automatizados;
- [x] lint e build;
- [x] instalação incremental;
- [x] Choice físico;
- [x] atualização física da mesma surface;
- [x] cancelamento não efetiva mutação;
- [x] ausência de crash no smoke final.

## Evidências

- `A2uiActionProtocolTest`: 6 testes focados PASS;
- `PlannerPortTest`: caminho A2UI conhecido não chama proposta Gemma;
- suíte Android: 299 testes, 0 falhas, 0 erros;
- `:app:lintDebug`: PASS;
- `:app:assembleDebug`: PASS;
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`: `Success`;
- device físico Xiaomi API 36, serial `69WOBUFENFLFGAJZ`;
- tela `A2UI Actions` exibiu o Choice `Só os atrasados`;
- toque gerou o evento e mostrou `Evento validado e devolvido ao Agent Runtime`;
- a mesma surface exibiu `Todos` → `Só os atrasados`;
- processo final `com.tino.app` permaneceu ativo;
- não houve `Fatal signal`, `FATAL EXCEPTION`, `SQLiteException` ou
  `AndroidRuntime` associado ao processo final.

## Limites preservados

- a tela física é uma validação de fluxo, não uma capability comercial nova;
- a atualização demonstrada é declarativa e não executa mutação;
- confirmação, idempotência, policy e commit universal ficam para G3.11;
- RAG, memória de negócio e dashboards continuam fora desta task.
