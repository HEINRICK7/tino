# TINO — Gate 4 — ADK Autonomous Loop

**Data:** 20/08/2026  
**Status:** `PASS_FULL`  
**Escopo:** ciclo autônomo controlado `observe → plan → execute → observe → replan → decide`.

## Resultado

O TINO agora possui uma fronteira de runtime explícita para o loop agentic.
`AdkAgentRuntime` recebe um `PlannerPort` substituível — atualmente o
`AdkQueryPlanner`, com fallback determinístico — e mantém as decisões de
segurança no TINO:

```text
AgentInteraction
      ↓
AdkAgentRuntime / AgentRuntimePort
      ↓
PlannerPort
      ↓
ExecutionPlan
      ↓
PlanValidator
      ↓
PlanExecutor
      ↓
observação grounded
      ↓
replan, clarification, confirmation ou resposta
```

O ADK não acessa Room, DAO, repository, handler ou mutation executor.

## Implementado

- `AgentLoopLimits` com `maxToolCalls`, `maxReplans`, detecção de chamadas
  duplicadas e proteção contra loop;
- `AgentTerminalState` com `ANSWERED`, `REQUEST_CLARIFICATION`,
  `REQUEST_CONFIRMATION`, `INSUFFICIENT_DATA`, `TOOL_FAILURE`, `UNSUPPORTED` e
  `TIMEOUT`;
- timeout global convertido em resultado terminal seguro;
- contagem de tools e replan independente do limite de turns;
- fingerprint de plano/observação para bloquear repetição sem progresso;
- validator obrigatório antes de qualquer execução;
- `AdkAgentRuntime` exposto pelo composition root, sem dependência de SDK ADK
  dentro do domínio;
- harness físico `G4AgentLoopViewModel` e tela de debug no menu Mais, usando
  somente dados fake read-only.

## Critérios executáveis

| Critério | Resultado |
|---|---|
| Planner pode replanejar após observar resultado | PASS |
| Multi-tool executa em turnos controlados | PASS |
| Clarificação encerra o turno sem executar mutação | PASS |
| Limite de tools | PASS |
| Limite de replans | PASS |
| Chamada/plano duplicado bloqueado | PASS |
| Timeout terminal | PASS |
| Plano inválido bloqueado antes do executor | PASS |
| ADK sem acesso a Room/DAO/handlers | PASS |
| Suíte, lint e APK | PASS |
| Smoke físico no Xiaomi/API 36 | PASS |

## Não faz parte deste gate

Memória nova, RAG/Knowledge produtivo, voz real, proactive insights e novas
capabilities comerciais não foram iniciados. O próximo gate só deve ser
definido após uma decisão/documento próprio; este gate não altera a segurança
de mutações da G3.11.

