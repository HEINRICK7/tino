# TASK G3.11 — Mutation Safety & Confirmation

**Status:** `PASS_FULL`  
**Próxima task:** G3.12 está liberada, mas ainda não iniciada.

## Objetivo

Garantir que o agente possa propor operações, mas nunca fazer commit sem uma
confirmação vinculada exatamente à prévia apresentada ao comerciante.

## Fluxo implementado

```text
Agent Runtime / texto / voz
          ↓
    ToolPreview
          ↓
  ProposedOperation
          ↓
 MutationSafetyCoordinator
          ↓
  confirmationToken + TTL
          ↓
  A2UI ActionValidator
          ↓
 MutationConfirmationPort
          ↓
 MutationSafeToolExecutor
          ↓
  Room / domínio
```

O caminho antigo `execute(call, confirmed = true)` continua disponível apenas
para leituras. Para mutations, ele falha deliberadamente. O commit exige
`confirm(call, MutationConfirmation)`.

## Entregue

- `ProposedOperation` tipada com capability, argumentos, risco, operação,
  idempotency key, fingerprint da prévia e expiração;
- política de risco para leitura, mutation de baixo risco e mutation financeira;
- token de confirmação separado da operação;
- detecção de argumentos alterados e de prévia stale;
- expiração de prévia em cinco minutos;
- replay/double-confirm bloqueado por status, reserva atômica e idempotência;
- transição concorrente `PENDING → EXECUTING → COMMITTED`, com retorno a
  `PENDING` em falha controlada;
- cancelamento remove a operação pendente sem tocar no domínio;
- estado `PENDING`/`EXECUTING`/`COMMITTED` persistido no Room;
- migration `12 → 13` validada no teste e no startup físico;
- decorator único `MutationSafeToolExecutor` entre coordenadores e dispatcher;
- `confirm_operation` A2UI encaminhado pelo `MutationConfirmationPort`, sem
  acesso do renderer ao Room;
- harness DEBUG `debug-mutation-001` para smoke físico sem alterar dados
  comerciais reais;
- coordenadores de voz e texto encaminhando o token real da prévia;
- testes de token inválido, stale state, expiração, cancelamento, replay,
  bypass booleano e persistência Room.

## Validação automatizada

- `MutationSafetyTest`: 10 testes focados PASS;
- `RoomMutationOperationStoreTest`: 3 testes focados PASS;
- `A2uiActionProtocolTest`: 8 testes PASS, incluindo confirmação com token e
  rejeição sem token;
- suíte Android: **314 testes**, 0 falhas, 0 erros;
- módulo fiscal: **32 testes**, sem alteração de comportamento;
- lint PASS;
- APK debug gerado PASS.

## Validação física

PASS_FULL no Xiaomi/API 36 em 20/08/2026. A instalação foi incremental, sem
apagar dados, a `MainActivity` abriu com Room v13 e o harness físico exibiu:

- `debug-mutation-001`: `PENDING → COMMITTED`, `Commit count: 1`, via
  `CONFIRMAR VIA A2UI`;
- replay/double-confirm rejeitado com `Operação repetida bloqueada por
  idempotência`, mantendo `Commit count: 1`;
- kill/restart do app restaurou `State: COMMITTED`, `Commit count: 1` e a
  mensagem `Estado restaurado do Room após restart`;
- cancelamento exibiu `Cancelado sem mutação; a operação deixou de ser
  executável`, com `Commit count: 0`;
- token de outra operação rejeitado com `Token de confirmação inválido`;
- fingerprint alterado entre preview e confirm rejeitado com `Os dados mudaram
  desde a prévia. Gere uma nova confirmação.`;
- logs finais sem `FATAL EXCEPTION`, `Fatal signal`, `ANR in` ou
  `SQLiteException`.

As capturas e os comandos executados estão em
[TINO-EVIDENCE-G3.11-2026-08-20.md](TINO-EVIDENCE-G3.11-2026-08-20.md).

G3.12 está liberada para a próxima rodada; não foi iniciada nesta task.
