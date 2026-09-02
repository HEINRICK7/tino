# TASK G3.4 — Interaction State

Status: PASS  
Next task allowed: YES

## Objective

Transformar o contexto de uma interação em uma unidade de domínio persistível,
com tela atual, superfícies ativas e operação pendente, sem fazer o Agent Loop
ou as regras de comércio dependerem do Room.

## Implementado

- `InteractionState` e `InteractionStateStore` no domínio;
- políticas `SESSION` e `UNTIL_RESOLVED`;
- `TinoAgentSession` mantém superfícies ativas e sincroniza o estado por uma
  porta persistente;
- operação pendente preserva cliente, produto, quantidade, slots, estágio e
  confirmação;
- `RoomInteractionStateStore` implementa o adapter JSON/Room;
- tabela `interaction_states` e migration `11→12`;
- expiração remove a operação pendente sem apagar a âncora da tela;
- cancelamento/limpeza persiste o contexto de tela sem deixar operação
  executável pendurada.

## Acceptance criteria

- [x] contrato formal de `InteractionState` com `sessionId`;
- [x] tela atual e superfícies ativas persistíveis;
- [x] operação pendente persistível como draft, não como mutação executável;
- [x] cliente, produto e quantidade preservados até preview/confirmação;
- [x] política `UNTIL_RESOLVED` com expiração determinística;
- [x] limpeza após cancelamento/resolução;
- [x] domínio não importa Room, DAO ou JSON;
- [x] adapter Room e DI conectados;
- [x] migration `11→12` registrada;
- [x] testes focados, suíte regressiva, lint e build verdes.

## Regression guards

- Room continua sendo detalhe externo e fonte de verdade apenas para dados
  comerciais;
- InteractionState nunca executa capability;
- cancelamento não apaga a tela atual;
- estado expirado não é restaurado;
- G3.5 não começa antes de G3.4 receber PASS.

## Evidências de execução

- `InteractionStateTest`: 2 testes focados PASS;
- `RoomInteractionStateStoreTest`: 2 testes focados PASS;
- suíte Android: 259 testes, 0 falhas, 0 erros;
- `:app:lintDebug`: PASS;
- `:app:assembleDebug`: PASS; APK de aproximadamente 558 MB;
- Room: migration `11→12` cria `interaction_states`;
- device continua acumulado como `PENDING_DEVICE_VALIDATION` da G3.2;
- G3.5 está liberada.
