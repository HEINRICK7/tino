# TINO — Task G5: Long-Term Business Memory

**Data:** 20/08/2026  
**Estado:** `PASS_FULL`  
**Gate seguinte:** G6 não iniciada

## Objetivo

Adicionar memória persistente do negócio para preferências e correções estáveis
sem transformar memória em fonte de verdade comercial. Fatos atuais continuam
vindo do Room comercial; Business Memory só auxilia a interpretação.

## Contrato implementado

- `BusinessMemoryPort` e `BusinessMemoryStorePort` no domínio;
- `MemoryCandidate`, `BusinessMemoryRecord`, `MemoryProvenance`,
  `MemoryConfidence` e `MemoryLifecycle` tipados;
- ciclos `CANDIDATE → LEARNED → TRUSTED`;
- contradição produz `DEMOTED` e uma nova evidência começa outro candidato;
- remoção produz `REMOVED`, preservando histórico e impedindo resolução;
- provenance por correção, confirmação, contradição, demotion e remoção;
- confidence, contagem de evidências e eventos de origem persistidos;
- policy rejeita fatos transacionais como saldo, estoque, preço, Pix,
  pagamento e total;
- store escopado e sem dependência de ADK, Compose, DAO ou renderer no domínio.

## Persistência e integração

- `BusinessMemoryEntity` e `BusinessMemoryDao` adicionados ao Room;
- versão do banco atualizada de 13 para 14;
- `MIGRATION_13_14` cria a tabela, índice de escopo e unicidade por
  `scopeKey + memoryKey + value`;
- `RoomBusinessMemoryRepository` mantém o adapter fora do domínio;
- `CommerceContextMemory` registra correções duráveis e restaura aliases
  `LEARNED`/`TRUSTED` antes da interpretação;
- Working Memory, Session Memory e Business Memory continuam separados;
- a resolução durável usa o escopo `default-store` nesta fatia. A seleção de
  loja/tenant configurável continua como pendência de multi-store.

## Golden flows comprovados

1. Correção `Maracá → Café Maratá`:
   `CANDIDATE`, depois `TRUSTED` após evidências repetidas.
2. Contradição:
   aprendizado anterior em `DEMOTED`; alternativa vira novo `CANDIDATE`.
3. Reinício:
   registros são restaurados do Room pelo app reiniciado.
4. Remoção:
   registros passam a `REMOVED` e permanecem não resolvíveis após recarga.
5. Fato comercial:
   valores como saldo, estoque, preço, Pix, pagamento e total são rejeitados
   pela policy e não entram na Business Memory.

## Definition of Done

- [x] contratos de domínio e lifecycle explícitos;
- [x] policy/provenance/confidence implementados;
- [x] persistência Room e migration 13→14;
- [x] integração com `CommerceContextMemory`;
- [x] testes unitários e persistência Robolectric/Room;
- [x] suíte completa, lint e assembleDebug verdes;
- [x] APK instalado incrementalmente no Xiaomi/API 36;
- [x] promoção, contradição, restart e remoção comprovados fisicamente;
- [x] sem crash fatal, ANR ou erro SQLite no smoke;
- [x] evidências registradas em
  [TINO-EVIDENCE-G5-2026-08-20.md](TINO-EVIDENCE-G5-2026-08-20.md).

## Fora da G5

- memória factual de saldo, estoque, preço, recebíveis ou pagamentos;
- memória semântica sem policy/provenance;
- RAG/Knowledge;
- predição, ML e proactive insights;
- G6 e gates posteriores.
