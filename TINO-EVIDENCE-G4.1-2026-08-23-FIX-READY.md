# TINO — G4.1: correção pronta para reteste físico

**Data:** 23/08/2026  
**Estado:** `FIX_READY_FOR_DEVICE_RETEST`  
**Falha de origem:** [TINO-EVIDENCE-G4.1-2026-08-23.md](TINO-EVIDENCE-G4.1-2026-08-23.md)

## Correção limitada aplicada

O `FastIntentRouter` passou a reconhecer a intenção financeira pela composição
semântica dos sinais da pergunta:

- recebimentos com “quanto”, inclusive “recebi hoje”;
- valor ainda a receber com modificadores como “quanto ainda tenho para
  receber”;
- resumo financeiro por “financeiro”, “movimento” e “vendas” em perguntas de
  consulta;
- múltiplas formas de pagamento sem reduzir Pix + dinheiro a apenas uma forma.

Com isso, a capability financeira é resolvida antes do fallback global que
poderia iniciar resolução de `Product`. Não foi criado tratamento especial
para a frase física e não houve alteração em Multi-Vertical, M1–M8,
AG-UI/CopilotKit, novos packs ou refactor oportunista.

## Regressões adicionadas

`FastIntentRouterTest` cobre a frase física e variações naturais:

```text
Quanto eu recebi hoje no Pix e no dinheiro e quanto ainda tenho para receber?
recebi hoje no Pix e no dinheiro e quanto ainda tenho para receber
Quanto recebi hoje?
Como estão minhas vendas hoje?
Me mostra o financeiro de hoje
Qual foi meu movimento hoje?
```

`AgenticQueryTest` comprova que a frase física chega a
`READ_FINANCIAL_SUMMARY` pelo fast path e não pode alcançar `ProductPicker`.
As regressões existentes continuam cobrindo listagem global, estoque e
consulta de produto específico.

## Validação automatizada

```text
gradle :app:testDebugUnitTest \
  --tests com.tino.app.domain.agent.FastIntentRouterTest \
  --tests com.tino.app.domain.agent.AgenticQueryTest \
  --no-daemon

BUILD SUCCESSFUL
```

## Próximo passo

A correção está pronta para o mesmo reteste físico no device Xiaomi
2410FPCC5G/API 36. Esta evidência não promove G4.1 a `PASS_FULL`; nenhum
cenário adicional deve ser executado antes desse reteste.
