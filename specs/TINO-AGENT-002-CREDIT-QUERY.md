# TINO — Agentic Golden Path 002: Consulta de fiado

**Status:** implementado; validação de fala real contínua
**Tipo:** checkpoint de execução contínua
**Pré-requisito:** `specs/TINO-AGENT-001-FIRST-QUERY.md`
**Próximo documento:** `specs/TINO-AGENT-003-STOCK-QUERY.md`

## Objetivo

Permitir perguntas de leitura como:

```text
"Quanto João deve?"
"Mostra o saldo do João."
```

O TINO resolve o cliente no catálogo local, consulta o saldo derivado dos
lançamentos de fiado e mostra uma superfície nomeada sem confirmação.

## Implementação aproveitada

- `GET_CUSTOMER_BALANCE` já pertence à allowlist de tools.
- `CommerceRepository.customerBalance(customerId)` é a autoridade operacional.
- `CommerceToolName.isReadOnly` impede a criação de preview pendente.
- `ToolExecutionResult` agora carrega `title` e `message` para a superfície.
- A UI apresenta, por exemplo, `Fiado de João` e o saldo em destaque.
- A confirmação continua reservada para comandos que alteram crédito.

## Gates

| Gate | Evidência | Status |
|---|---|---|
| `INTENT` | Tool `GET_CUSTOMER_BALANCE` na allowlist | PASS |
| `RESOLUTION` | Dispatcher resolve cliente pelo repositório local | PASS |
| `QUERY` | Consulta saldo sem mutação | PASS |
| `SURFACE` | Resultado possui título semântico e valor | PASS |
| `NO_CONFIRMATION` | Query retorna `AnswerReady` imediatamente | PASS |
| `OFFLINE` | Consulta depende de dados locais | PASS |
| `BUILD` | Suite, assemble e lint passam após alteração | PASS |
| `REAL_DEVICE` | Fala em português ainda requer validação manual | IN_PROGRESS |

## Testes

- Teste unitário garante resposta de `GET_CUSTOMER_BALANCE` sem confirmação pendente.
- Teste anterior garante o mesmo comportamento para `GET_TODAY_SALES`.
- O executor continua exigindo confirmação para mutações.

## Próximo passo automático

Executar os gates deste documento e seguir para `TINO-AGENT-003-STOCK-QUERY.md`,
fechando a pergunta “Quantos cafés ainda tem?” com resolução de produto e
superfície de estoque.
