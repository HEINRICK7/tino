# TINO — Task G3.12

## Working & Session Memory

**Data:** 20/08/2026  
**Status:** `PASS_FULL`  
**Device:** Xiaomi 2410FPCC5G — API 36 — ADB `69WOBUFENFLFGAJZ`

## Objetivo

Permitir que o TINO mantenha contexto entre turnos e reinícios sem transformar
memória em fonte de verdade comercial. A memória guarda referências,
rascunhos e contexto de interação; valores atuais continuam vindo do Room.

## Implementado

- `WorkingMemory` separado de `SessionMemory` em `InteractionState`;
- working memory para intent, slots, operação pendente e clarificação;
- session memory para tela, entidades recentes, objetivo, surface ativa,
  resumo não factual e contagem de turnos;
- `sessionId` explícito (`default` no app atual);
- TTL independente: working context de curta duração e session context de 30
  minutos de inatividade;
- limpeza de working memory sem apagar entidade/tela da sessão;
- `PendingClarification` persistível e limpável;
- serialização Room retrocompatível: estados antigos continuam carregáveis;
- restauração Room → snapshot preserva tela, entidades, objetivo e resultado
  de referência;
- texto reutiliza a sessão persistida para referências como “ela” e “esse”;
- ações A2UI registram a `surfaceId` na sessão e preservam o contexto;
- texto e voz usam o mesmo `TinoAgentSession` compartilhado;
- harness debug físico em `Mais → G3.12 Memória`.

## Critérios executáveis

| Critério | Resultado |
|---|---|
| Working/Session separados | PASS |
| Lifecycle e TTL explícitos | PASS |
| Clarificação e rascunho persistíveis | PASS |
| Memória não substitui Room | PASS |
| Memória não executa mutation | PASS |
| Persistência e restauração após restart | PASS |
| A2UI preserva contexto | PASS |
| Limpeza preserva contexto de sessão | PASS |
| Testes multiturno/contextuais | PASS |
| Lint, build e APK | PASS |
| Instalação incremental no device | PASS |
| Smoke físico sem crash/ANR/SQLite error | PASS |

## Golden flow físico

1. Abrir `G3.12 Memória`.
2. Salvar o contexto da Maria: `CUSTOMER_DETAIL`, entidade `Maria`, objetivo
   `READ_CUSTOMER_BALANCE` e surface `g312-memory-surface`.
3. Criar rascunho: `ADD_CREDIT_ITEM`, cliente/produto/quantidade preenchidos e
   `payment_method` pendente.
4. Encerrar e reabrir o app sem apagar dados.
5. Confirmar no device que a mesma entidade, objetivo, surface, rascunho e
   clarificação foram restaurados.
6. Limpar Working Memory.
7. Confirmar que operação, slots e clarificação desapareceram, enquanto Maria,
   objetivo e contagem de sessão permaneceram.

## Evidências

- [TINO-EVIDENCE-G3.12-2026-08-20.md](TINO-EVIDENCE-G3.12-2026-08-20.md)
- 318 testes do app passaram;
- lint e `assembleDebug` passaram;
- APK instalado incrementalmente no Xiaomi;
- `MainActivity` permaneceu viva após instalação e restart;
- logs físicos não apresentaram `FATAL EXCEPTION`, `ANR`, `SQLiteException`,
  `no such table` ou `database is locked`.

## Fora desta task

- Business Memory de longo prazo;
- fatos comerciais armazenados na memória;
- RAG/Knowledge Memory;
- sincronização cloud da memória;
- planejamento de novas operações sem confirmação.

## Decisão do gate

G3.12 está em `PASS_FULL`. O próximo gate do roadmap pode ser avaliado
separadamente; esta task não libera implementação de memória de negócio,
RAG ou Attention Engine.
