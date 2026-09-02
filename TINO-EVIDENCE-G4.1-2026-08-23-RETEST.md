# TINO — G4.1: reteste físico do cenário financeiro

**Data:** 23/08/2026  
**Estado do cenário:** `PASS`  
**Estado do gate:** `FAIL_MANUAL_RETEST / BLOCKED`  
**Device:** Xiaomi 2410FPCC5G / Android API 36  
**Serial:** `69WOBUFENFLFGAJZ`  
**Build:** `0.1.0-pilot.1` / versionCode 2  
**Evidências:** `/tmp/tino-g4-1-20260823-retest/`

## Execução

- APK corrigido instalado com `adb install -r -d`: `Success`;
- logs limpos antes da sessão;
- cold start concluído com `LaunchState: COLD`;
- Activity em primeiro plano: `com.tino.app/.MainActivity`;
- processo permaneceu ativo: PID `20287`;
- execução iniciada em `2026-08-23T17:04:42-03:00`;
- consulta enviada em `2026-08-23 17:06`.

## Cenário repetido

Foi repetida a mesma frase que havia produzido a falha:

```text
Quanto eu recebi hoje no Pix e no dinheiro e quanto ainda tenho para receber?
```

O resultado visual foi um card financeiro “Entrou hoje”, com “A receber” e
valores financeiros. O `ProductPicker` não apareceu.

## Evidência do pipeline

```text
SPEECH_PROVIDER_SELECTED provider=ON_DEVICE locale=pt-BR
VOICE_TRANSCRIPT_PARTIAL partial_count=30
VOICE_TRANSCRIPT_COMMITTED committed_count=1 transcript_state=REVIEW
VOICE_AGENT_SUBMITTED agent_executions_before_send=0
QUERY_STARTED timeout_ms=3000
ROUTING_COMPLETED duration_ms=130 route=fast fast_path=true
CAPABILITY_COMPLETED duration_ms=39
A2UI_READY duration_ms=0
QUERY_COMPLETED duration_ms=175 fast_path=true
RENDERED duration_ms=178
```

Não houve `ENTITY_RESOLUTION` nem `ProductPicker` na sessão. Não houve
`FATAL EXCEPTION`, `ANR in`, `VOICE_FAILURE` ou `TOOL_FAILURE`; o processo
permaneceu ativo após a resposta.

## Comparação com a falha original

| Execução | Rota | Entidade | Resultado |
|---|---|---|---|
| Falha original | `global` | `product`, 3 candidatos ambíguos | `ProductPicker` |
| Reteste corrigido | `fast` | nenhuma resolução de produto | card financeiro |

O cenário físico que falhou foi corrigido e passou no device. A instrumentação
atual registra a rota e as etapas, mas não imprime o nome textual da capability
no evento `CAPABILITY_STARTED`; a capability é confirmada pelo card financeiro
renderizado e pela ausência de resolução de produto.

## Decisão

- cenário 1 — fala longa/consulta financeira: `PASS`;
- G4.1 ainda não é `PASS_FULL`;
- continuar somente com os demais casos físicos obrigatórios do protocolo;
- M1–M8, Multi-Vertical e G6 continuam bloqueados até a conclusão de G4.1.

## Cenário 2 — Review/Edit

O ASR produziu inicialmente uma formulação incorreta, “como recebi hoje”. Na
tela de revisão, o texto foi editado para:

```text
Quanto recebi hoje?
```

O resultado foi um card financeiro “Entrou hoje”, sem `ProductPicker`.

O logcat confirmou:

```text
VOICE_TRANSCRIPT_COMMITTED committed_count=1 transcript_state=REVIEW
VOICE_CORRECTION_QUEUED transcript_state=REVIEW_EDITED
VOICE_AGENT_SUBMITTED agent_executions_before_send=0
ROUTING_COMPLETED duration_ms=108 route=fast fast_path=true
CAPABILITY_COMPLETED duration_ms=52
A2UI_READY duration_ms=0
QUERY_COMPLETED duration_ms=164 fast_path=true
RENDERED duration_ms=166
```

O cenário Review/Edit passou: a correção foi registrada, o agente recebeu o
texto corrigido somente após `Enviar` e a resposta foi financeira.

## Cenário 3 — Continuar falando

As duas falas foram concatenadas na revisão, resultando em:

```text
bota dois café maratá para Maria
```

O log registrou dois commits de transcript (`committed_count=2`) antes do
envio. A resposta apresentada pelo TINO foi uma análise correta do estoque:

```text
Café Maratá tem 0 unidade(s), vendeu 4 nos últimos 30 dias e está com
aproximadamente menos de 1 dia de estoque.
```

Esse dado corresponde ao estoque real observado no app e não é uma falha de
consulta. O runtime concluiu pelo caminho do interpretador agentic, com
`AGENT_COMPLETED status=success`, rota `interpreter`, duração aproximada de
4,9 s, sem timeout, crash ou erro de tool. O cenário de continuação passou:
o transcript foi concatenado e a resposta factual foi apresentada.

## Cenário 4 — Isolamento antes de Enviar

Foi iniciada uma nova fala para produzir a tela de revisão. O último ciclo
registrado começou às `17:26:57` e terminou em revisão às `17:27:02`.

```text
VOICE_START
VOICE_TRANSCRIPT_PARTIAL partial_count=1..6
VOICE_TRANSCRIPT_COMMITTED committed_count=1 transcript_state=REVIEW
```

Depois desse commit não houve `VOICE_AGENT_SUBMITTED`, `ROUTING_STARTED`,
`CAPABILITY_STARTED`, `A2UI_READY` ou `QUERY_COMPLETED`. A execução agentic
permaneceu em zero antes de `Enviar`. O cenário de isolamento passou.

## Cenário 5 — Learning: falha reproduzida

O resultado operacional da correção foi correto: a tela mostrou `Estoque —
Café Maratá: 0 unidades`. Porém, o critério adicional de learning não foi
comprovado.

O log registrou:

```text
VOICE_CORRECTION_QUEUED transcript_state=REVIEW_EDITED
VOICE_AGENT_SUBMITTED agent_executions_before_send=0
ROUTING_COMPLETED route=global fast_path=false
ENTITY_RESOLUTION_FUZZY entity_type=product candidate_count=1
QUERY_COMPLETED duration_ms=133
```

Mas não registrou `VOICE_CORRECTION_EVENT` nem outro evento de learning criado
após a interpretação corrigida. Assim, a consulta de estoque passou, mas não
há evidência suficiente de que “Maracá → Maratá” tenha sido persistido como
correção elegível. Nenhum produto, cliente ou fornecedor foi criado ou
alterado pela operação observada.

Conforme o protocolo, este caso é `FAIL_MANUAL_RETEST` e os testes restantes
foram interrompidos. Não houve alteração de código durante a sessão.
