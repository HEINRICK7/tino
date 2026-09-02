# TINO — Agentic Golden Path 001: Consulta de vendas de hoje

**Status:** implementado; validação de fala real continua no aparelho
**Tipo:** checkpoint de execução contínua
**Pré-requisitos:** `TINO-BACKEND-001`, `TINO-BACKEND-002`, `TINO-BACKEND-003`, `TINO-BACKEND-006`
**Próximo documento:** `TINO-AGENT-002-CREDIT-QUERY.md`

## Objetivo

Fechar a primeira fatia agentic vertical para uma consulta local:

```text
"Quanto vendi hoje?"
  ↓
ASR committed
  ↓
GemmaOrchestrator
  ↓
GET_TODAY_SALES
  ↓
CommerceRepository.todayTotalCents()
  ↓
resposta visível na tela de voz
```

Consulta não altera dados e não deve exigir confirmação humana.

## Implementação

- `CommerceToolName.isReadOnly` classifica consultas sem misturá-las às mutações.
- `VoiceCommandCoordinator` executa tools read-only imediatamente, sem criar comando pendente.
- `VoiceCommandState.AnswerReady` separa resposta de consulta de preview de mutação.
- `VoiceUiState.Answer` apresenta `TINO ENCONTROU` com o resultado e a origem local.
- Mutações continuam no fluxo `preview → confirmação → execução`.
- A resposta usa `todayTotalCents()` do Room por meio do `CommerceRepository`.
- Nenhum SQL, DAO ou regra comercial foi colocado no Gemma.

## Gates

| Gate | Evidência | Status |
|---|---|---|
| `INTENT` | Tool allowlist contém `GET_TODAY_SALES`; Gemma retorna nome e argumentos | PASS |
| `QUERY` | Dispatcher consulta `CommerceRepository.todayTotalCents()` | PASS |
| `NO_MUTATION` | Consulta não cria `pending` nem chama mutação | PASS |
| `SURFACE` | `VoiceUiState.Answer` renderiza resultado contextual | PASS |
| `OFFLINE` | Fonte é Room local | PASS |
| `ZERO_DOUBT` | Interface informa que a consulta veio dos dados deste aparelho | PASS |
| `BUILD` | `testDebugUnitTest`, `assembleDebug` e `lintDebug` | PASS |
| `REAL_DEVICE` | APK instalado e abriu sem crash; fala real ainda precisa de validação manual | IN_PROGRESS |

## Testes

- Teste de unidade garante que query read-only retorna `AnswerReady`.
- Teste garante execução imediata da consulta sem confirmação pendente.
- Testes existentes de confirmação de venda fiada continuam cobrindo mutação protegida.

## Débitos conhecidos

- O dispatcher ainda retorna strings internas em vez de um catálogo completo de surfaces tipadas.
- Consultas de fiado mensal, saldo de cliente e estoque ainda precisam de fatias verticais próprias.
- Ambiguidade de produto/cliente precisa de uma surface dedicada antes de executar comandos.
- A fala real em português ainda depende de validação manual no aparelho conectado.

## Próximo passo automático

Implementar `TINO-AGENT-002-CREDIT-QUERY.md`: consulta de saldo/fiado local,
começando por `GET_CUSTOMER_BALANCE`, com resposta no mesmo contexto e sem
confirmação para leitura.
