# TINO — Evidências G4.1: Voice Reliability & Crash Recovery

**Data:** 21/08/2026  
**Estado:** `PASS_AUTOMATED_PENDING_DEVICE`

**Política de device:** não existe device oficial. Qualquer um dos três devices
autorizados e disponíveis pode ser usado para fechar o gate. Cada sessão deve
registrar modelo, Android e API do aparelho utilizado. As execuções no Xiaomi e
no Samsung abaixo são evidências complementares de sessões específicas.

## Automatizado

Comando executado:

```text
gradle :app:testDebugUnitTest --no-daemon
```

Resultado: `BUILD SUCCESSFUL`.

- 329 testes unitários do app passaram;
- `TranscriptLifecycleTest` cobre bloqueio de parcial/revisada, commit único e
  edição antes do processamento;
- o fluxo de voz global compila com `TranscriptReview`, editor e continuação;
- `lintDebug` passou;
- `assembleDebug` passou;
- APK instalado incrementalmente no Samsung SM-A042M/API 34;
- smoke de instalação, abertura e processo ativo passou no Samsung;
- Gemma ficou indisponível no Samsung e o fallback após queda controlada do
  processo isolado passou;
- `tools/g3-2-smoke.sh` passou com Activity aberta e processo ativo.

## Defeito manual reproduzido — inventário global

**Input:** “Quais produtos eu tenho cadastrado no meu estoque?”

**Esperado:** `LIST_PRODUCTS`, consulta global de coleção e lista A2UI dos
produtos cadastrados, sem resolução de `Product`.

**Actual:** `ProductPicker` perguntou “Qual produto?”; após selecionar “Café
Maratá”, a resposta foi “AINDA NÃO CONSIGO RESPONDER” / “Não consegui preparar
essa operação global.”

**Evidência:** as duas telas da sessão manual mostram a cadeia
`intenção global → picker indevido → seleção → fallback de incapacidade`.

**Correção aplicada:** o `FastIntentRouter` agora cobre listagens com “estoque”
e a retomada de escolha global atualiza os argumentos do `ToolCall` e chama
novamente o boundary global. A correção está registrada e aguarda reteste no
device escolhido; não promove nem reprova o gate sem validação manual válida.

## Segundo defeito manual reproduzido — “clientes” sem fast path

**Device da reprodução:** device autorizado da sessão; o transcript apareceu na
tela e permaneceu em “Consultando seus dados...” por mais de dois minutos.

**Esperado:** `LIST_CUSTOMERS` local, com resposta em poucos segundos.

**Causa:** a palavra exata “clientes” não estava no `FastIntentRouter` e caía
no interpretador agentic sem limite de duração.

**Correção aplicada:** fast path para “clientes”/“cliente”, timeout de 3 s para
rotas determinísticas, timeout de 8 s para consultas agentic e recuperação para
estado de erro acionável. O audit redigido agora registra as etapas
`ROUTING_STARTED`, `ROUTING_COMPLETED`, `CAPABILITY_STARTED`,
`CAPABILITY_COMPLETED`, `A2UI_READY`, `QUERY_STARTED`,
`QUERY_COMPLETED`/`QUERY_TIMEOUT`, com `duration_ms`.

O tracing foi ampliado para distinguir também `VOICE_COMMITTED`,
`AGENT_STARTED/COMPLETED`, `ROOM_QUERY_STARTED/COMPLETED` e `RENDERED`. O
fast path local tem orçamento de 3 s; o caminho agentic tem orçamento de 5 s.

Regressões adicionadas:

```text
"clientes" -> LIST_CUSTOMERS / "Consultando clientes…"
"estoque"  -> LIST_PRODUCTS
"produtos" -> LIST_PRODUCTS
```

O APK recompilado foi instalado e aberto no Xiaomi 2410FPCC5G/API 36 em
23/08/2026. Isso comprova build/instalação, mas não substitui a reprodução
manual com microfone; o estado permanece pendente.

## Bugs adicionais encontrados na mesma sessão

### BUG A — “Vender” não retoma após ProductPicker

O picker de produto abriu corretamente, mas a continuação falhou com operação
global indisponível. A correção padroniza quantidade ausente como `1` na prévia
e na execução de `REGISTER_SALE`, mantendo a confirmação humana.

### BUG B — “Abrir clientes” travava

O transcript era recebido, mas não havia destino determinístico. A correção
adiciona navegação direta para Clientes, Estoque, Fiado, Venda e Entrada, sem
passar pelo agente. Testes cobrem `Abrir clientes` e `Vender`; a comprovação
manual no microfone continua pendente.

Regressões adicionadas:

```text
"Quais produtos eu tenho?"                         -> LIST_PRODUCTS
"Me mostra meu estoque"                            -> LIST_PRODUCTS
"O que tenho cadastrado?"                          -> LIST_PRODUCTS
"Lista meus produtos"                              -> LIST_PRODUCTS
"Quanto tenho de Café Maratá?"                     -> GET_PRODUCT_STOCK
retomada de escolha Product em ToolCall global      -> chamada retomada
```

## Instrumentação dos cinco fluxos de microfone

Foi adicionada instrumentação ao `AgenticVoiceViewModel` para registrar e
expor:

```text
partialCount
revisedCount
committedCount
agentExecutionsBeforeSend
agentExecutionsAfterSend
correctionEventCreated
```

Os eventos de auditoria correspondentes são redigidos e não incluem o texto
da fala. A implementação garante por código que `Partial`/`Revised` apenas
atualizam `Listening`, enquanto somente `Enviar` incrementa
`agentExecutionsAfterSend` e chama o Agent Runtime.

Os valores físicos abaixo ainda não foram preenchidos porque exigem fala
humana no microfone:

```text
partialCount = PENDING_MICROPHONE
revisedCount = PENDING_MICROPHONE
agentExecutionsBeforeSend = PENDING_MICROPHONE
agentExecutionsAfterSend = PENDING_MICROPHONE
correctionEvent = PENDING_MICROPHONE
```

## Protocolo manual de fechamento em qualquer device autorizado

Este é o único protocolo autorizado para promover G4.1 a `PASS_FULL`. A
execução pode ser feita em qualquer device autorizado escolhido para a sessão,
mas precisa usar fala humana real no microfone; ADB, texto digitado ou
transcript simulado não substituem esta evidência.

Registro obrigatório:

```text
device = PREENCHER COM O APARELHO USADO NA SESSÃO
partialCount = PENDING_MICROPHONE
revisedCount = PENDING_MICROPHONE
committedCount = PENDING_MICROPHONE
agentExecutionsBeforeSend = PENDING_MICROPHONE
agentExecutionsAfterSend = PENDING_MICROPHONE
originalTranscriptHash = PENDING_MICROPHONE
correctedTranscriptHash = PENDING_MICROPHONE
correctionEvent = PENDING_MICROPHONE
```

Casos obrigatórios:

1. Fala longa: dizer “Quanto eu recebi hoje no Pix e no dinheiro e quanto
   ainda tenho para receber?”. Confirmar `partialCount > 0`, transcript
   completo em `TranscriptReview`, zero execuções antes de `Enviar` e uma após.
2. Review/Edit: produzir uma transcrição incorreta, editar, enviar e confirmar
   que o Agent Runtime recebeu o texto corrigido.
3. Continuar falando: dizer “Bota dois cafés Maratá”, tocar `Continuar`, dizer
   “pra Maria” e confirmar o transcript concatenado completo.
4. Isolamento: durante `Partial`/`Revised`, confirmar que não houve execução
   agentic; somente `Review → Enviar` pode executar uma vez.
5. Learning: corrigir “café Maracá” para “café Maratá”, confirmar
   `CorrectionEvent`/LearningPolicy elegível e provar que nenhum produto,
   cliente ou fornecedor foi criado/alterado automaticamente.

Cada caso deve incluir evidência visual/manual e os contadores reais. Se um
caso falhar, G4.1 permanece `VALIDATING`/`FAIL` e G6 continua bloqueada.

## Chamada Gemma isolada no device

### Evidência histórica no Xiaomi

Comando executado após a instalação do APK debug:

```text
bash tools/g4-1-gemma-smoke.sh /tmp/tino-g4-1-gemma-final-Thcd2X
```

Resultado físico:

- `G4.1 Gemma smoke PASS`;
- `TinoGemmaSmoke: GENERATED OK`;
- `com.tino.app:gemma` permaneceu ativo após a inferência;
- não houve `FATAL EXCEPTION`, `SIGSEGV`, `ANR`, OOM ou `SQLiteException` novos
  no logcat da execução;
- o harness usa prompt fixo e não acessa Agent Runtime, Room, tools ou
  mutation, portanto prova a contenção da inferência e não substitui os fluxos
  de voz end-to-end.

Também foi executada a falha controlada do processo isolado:

```text
bash tools/g4-1-gemma-smoke.sh /tmp/tino-g4-1-fallback-final-RKFToT kill-gemma
```

Resultado:

- `G4.1 fallback smoke PASS`;
- o serviço retornou `UNAVAILABLE — Gemma isolado foi encerrado; usando
  fallback`;
- o PID de `com.tino.app` permaneceu vivo;
- uma nova chamada normal em seguida retornou `GENERATED OK`, comprovando
  recuperação/rebind do serviço isolado.

## Diagnóstico físico histórico

Tentativa de verificação nesta sessão:

```text
adb devices -l
List of devices attached
```

Uma primeira tentativa ocorreu sem device, mas o Xiaomi reapareceu autorizado e
permitiu a captura final. Esse diagnóstico permanece histórico:

- o logcat desta execução não registrou crash novo;
- o `exit-info` histórico registrou dois `APP CRASH(NATIVE)`, status 11;
- o `data_app_native_crash` do Android identificou `signal 11 (SIGSEGV)` em
  `libllm_inference_engine_jni.so`,
  `Java_com_google_mediapipe_tasks_genai_llminference_LlmTaskRunner_nativePredictSync`;
- os tombstones apontam para `LlmInferenceSession.generateResponse`,
  `LlmInference.generateResponse` e `MediaPipeGemmaTextInference.generate`;
- o diagnóstico agora é comprovadamente nativo/MediaPipe Gemma, não Room,
  Compose ou uma exceção Kotlin;
- a chamada Gemma física após a migração para `:gemma` passou; permanecem os
  fluxos de fala longa, revisão/edição, continuação e fallback end-to-end.

## Harness de diagnóstico histórico

Quando for necessário reproduzir o diagnóstico histórico no Xiaomi:

```bash
tools/g4-1-crash-capture.sh /tmp/tino-g4-1-capture
```

Depois de reproduzir a falha, revisar:

- `logcat.txt`;
- `signatures.txt`;
- `exit-info.txt`;
- `pid.txt`.

## Correção de boundary aplicada

`MediaPipeGemmaTextInference` virou cliente Binder/Messenger. A chamada nativa
fica em `GemmaInferenceService` no processo separado `com.tino.app:gemma`.
Queda do serviço vira `Unavailable`/fallback no processo principal; `try/catch`
continua sendo usado para falhas Java, mas não é tratado como proteção contra
SIGSEGV.

## Estado

`G4.1 = PASS_AUTOMATED_PENDING_DEVICE`.

O próximo passo permitido é retestar, no device autorizado escolhido, o fluxo
de inventário global e os golden flows de fala longa, edição, continuação e
fallback integrado à voz. A contenção/fallback isolado já passou em aparelhos
específicos; G4.1 continua `PASS_AUTOMATED_PENDING_DEVICE` até
esses fluxos serem comprovados com microfone real e sem regressão.

## Evidência complementar — Samsung SM-A042M / API 34

Execuções em 22/08/2026:

```text
bash tools/g3-2-smoke.sh app/build/outputs/apk/debug/app-debug.apk
bash tools/g4-1-gemma-smoke.sh /tmp/tino-g4-1-gemma-samsung-4J8Jah
bash tools/g4-1-gemma-smoke.sh /tmp/tino-g4-1-fallback-samsung-3DVNau kill-gemma
```

Resultados:

- instalação, abertura e processo principal: `PASS`;
- Gemma normal: `UNAVAILABLE` por modelo indisponível no aparelho;
- fallback após queda controlada: `PASS`;
- MainActivity aberta com processo principal ativo;
- os fluxos manuais de microfone continuam pendentes.
