# TINO — G4.1: correção pronta para reteste do learning

**Data:** 23/08/2026  
**Estado:** `FIX_READY_FOR_DEVICE_RETEST`  
**Falha física preservada:** [TINO-EVIDENCE-G4.1-2026-08-23-RETEST.md](TINO-EVIDENCE-G4.1-2026-08-23-RETEST.md)  
**Device da falha original:** Xiaomi 2410FPCC5G / Android API 36  
**Build da falha original:** `0.1.0-pilot.1` / versionCode 2

## Causa reproduzida

O fluxo físico corrigiu “Maracá” para “Maratá” e consultou corretamente
`Café Maratá: 0 unidades`, mas registrou somente:

```text
VOICE_CORRECTION_QUEUED
VOICE_AGENT_SUBMITTED (agent_executions_before_send=0)
QUERY_COMPLETED
```

Não houve `VOICE_CORRECTION_EVENT`. A causa foi que a frase no formato
“Quanto de Café Maratá tenho” podia seguir o `GlobalCommandRouter` sem uma
interpretação determinística com referência de produto. O learning dependia
indevidamente dessa interpretação e era descartado silenciosamente.

## Correção limitada

- o interpretador determinístico reconhece a forma semântica
  `quanto de <produto> tenho` como `READ_STOCK`, sem alterar a rota global nem
  a consulta operacional;
- a correção de voz agora é preparada somente depois do grounding da entidade;
- `QUEUED`/`PREPARED` não é evidência de learning;
- o `CorrectionEvent` e a entrada de `BusinessMemory` só são materializados
  depois que a execução retorna um resultado bem-sucedido;
- cancelamento, timeout, erro, seleção ainda não concluída e execução sem
  resultado descartam a correção pendente;
- nenhuma alteração foi feita no roteamento financeiro, Room operacional,
  estoque, Multi-Vertical, M1–M8, AG-UI/CopilotKit ou novos packs.

## Regressões adicionadas

`VoiceCorrectionLearningTest` cobre:

1. “Quanto de Café Maracá tenho” → “Quanto de Café Maratá tenho”: a
   interpretação é `READ_STOCK`, o produto é `cafe marata`, não há evento antes
   do sucesso e, após o commit, surge `CorrectionEvent` com status
   `CANDIDATE` e memória de negócio `CANDIDATE`;
2. edição sem mudança semântica não cria learning;
3. cancelamento descarta a correção;
4. execução sem commit de sucesso não persiste evento nem memória.

## Validação automatizada

```text
gradle :app:testDebugUnitTest \
  --tests com.tino.app.domain.language.VoiceCorrectionLearningTest \
  --tests com.tino.app.domain.language.LanguageFoundationTest \
  --tests com.tino.app.domain.language.AgenticContextContractTest \
  --no-daemon

BUILD SUCCESSFUL
```

## Decisão

O learning físico ainda não foi retestado. G4.1 permanece bloqueado e esta
correção está pronta exclusivamente para o mesmo cenário físico
`Maracá → Maratá`. Não iniciar M1–M8 nem qualquer outro gate antes do reteste.
