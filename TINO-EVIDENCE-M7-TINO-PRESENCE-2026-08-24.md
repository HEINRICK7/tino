# TINO — Evidência M7: Tino Presence

**Data:** 2026-08-24  
**Estado:** `M7 = PASS`  
**Próximo gate:** `M8 = BLOCKED_BY_M7`  
**Escopo:** projeção de presença a partir dos sinais reais do runtime; M8 não foi iniciado nesta execução.

## Resultado

M7 mantém a cadeia:

```text
SharedAgentState + AgentProgress + Human Gate
  → TinoPresenceResolver
  → TinoPresenceState
  → TinoAgentSessionViewModel
  → MainShell / FAB
```

O resolver não acessa Room, Gemma ou timers para inventar atividade. A presença
é uma projeção visual dos sinais existentes.

## Estados comprovados

- `LISTENING`: voz atual vence qualquer terminal antigo do progresso;
- `THINKING`: progresso ativo é visível mesmo antes de o estado de voz alcançar
  `UNDERSTANDING`;
- `RESOLVING`: resolução do agente permanece em processamento no FAB;
- `WAITING_FOR_USER`: Human Gate, confirmação compartilhada e clarificação
  entram como espera explícita;
- `COMPLETED`: sucesso do runtime projeta sucesso transitório no FAB;
- `ERROR`: falha do agente/progresso projeta recuperação visual;
- `IDLE`: cancelamento e ausência de execução não mantêm presença artificial.

## Proteção principal

Um `RunCompleted` antigo não pode mascarar um novo `LISTENING`. O resolver
prioriza estados atuais de voz e confirmação antes de consultar terminais
anteriores do progresso. Isso evita FAB preso em sucesso, erro ou espera de uma
execução anterior.

## Testes executados

```text
gradle :app:testDebugUnitTest \
  --tests com.tino.app.domain.agent.AgentRuntimeModulesTest \
  --tests com.tino.app.domain.agent.AgenticShellTest \
  --no-daemon
→ BUILD SUCCESSFUL
```

As regressões cobrem espera, listening, progresso ativo, terminal antigo,
confirmação pendente e integração com o runtime.

## Decisão de gate

`M7 = PASS` com resolver, ViewModel, projeção do shell, regressões e validação
automatizada verdes. `M8` permanece bloqueado e não foi iniciado.
