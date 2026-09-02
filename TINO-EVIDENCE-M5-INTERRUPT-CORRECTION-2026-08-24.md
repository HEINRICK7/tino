# TINO — Evidência M5: Interrupt & Correction

**Data:** 2026-08-24  
**Estado:** `M5 = PASS`  
**Próximo gate:** `M6 = BLOCKED_BY_M5`  
**Escopo:** correção estruturada de interação; nenhum trabalho de M6–M8 foi iniciado nesta execução.

## Resultado

M5 foi fechado com a cadeia:

```text
InteractionPatch
  → validação do patch
  → normalização de slots
  → invalidação de dependências
  → atualização condicional do SharedAgentState
  → regeneração do preview pelo coordinator
```

O patch não reinicia cegamente o run e não altera Room/Core. O SharedAgentState
continua sendo a verdade da interação; dados operacionais permanecem nas
capabilities e repositórios comerciais.

## Proteções comprovadas

- patches de quantidade, cliente, produto, período, valor e método são aceitos
  somente em campos suportados;
- campos independentes permanecem no rascunho;
- dependências derivadas são removidas quando o campo de origem muda;
- `expectedStateVersion` rejeita correção que chegou atrasada;
- ação em `EXECUTING` rejeita correção e preserva o snapshot integral;
- aliases `payment_method`/`paymentMethod` e `amount_cents`/`amount` são
  tratados de forma equivalente para cálculo de slots obrigatórios;
- cancelamento limpa a ação pendente sem executar mutação;
- learning permanece fora do patch e só pode ser materializado pelo fluxo
  de correção já aprovado após execução bem-sucedida;
- a auditoria registra aplicação ou rejeição sem expor valores comerciais.

## Testes executados

```text
gradle :app:testDebugUnitTest \
  --tests com.tino.app.domain.agent.AgentRuntimeModulesTest \
  --tests com.tino.app.domain.agent.AgenticQueryTest \
  --no-daemon
→ BUILD SUCCESSFUL

gradle :app:testDebugUnitTest --no-daemon
→ BUILD SUCCESSFUL
```

As regressões específicas cobrem preservação de slots independentes,
invalidação derivada, operação ativa, estado obsoleto, aliases de slots e
continuação segura do preview após correção.

## Decisão de gate

`M5 = PASS` com implementação, integração no coordinator, regressões e suíte
unitária completa verdes. `M6` permanece bloqueado e não foi iniciado.
