# TINO — Task G4.1: Voice Reliability & Crash Recovery

**Data:** 21/08/2026  
**Estado atual:** `PASS_FULL`
**Device de validação:** qualquer device autorizado e disponível; o aparelho
usado deve ser registrado em cada evidência  
**Gate:** extraordinário, obrigatório antes de iniciar qualquer novo gate

**Falha física:** [TINO-EVIDENCE-G4.1-2026-08-23.md](TINO-EVIDENCE-G4.1-2026-08-23.md)  
**Correção automatizada:** [TINO-EVIDENCE-G4.1-2026-08-23-FIX-READY.md](TINO-EVIDENCE-G4.1-2026-08-23-FIX-READY.md)

**Correção do learning:** [TINO-EVIDENCE-G4.1-2026-08-23-LEARNING-FIX-READY.md](TINO-EVIDENCE-G4.1-2026-08-23-LEARNING-FIX-READY.md)

**Reteste do learning:** [TINO-EVIDENCE-G4.1-2026-08-23-LEARNING-RETEST-2.md](TINO-EVIDENCE-G4.1-2026-08-23-LEARNING-RETEST-2.md)

**Reteste aprovado do learning:** [TINO-EVIDENCE-G4.1-2026-08-23-LEARNING-RETEST-3.md](TINO-EVIDENCE-G4.1-2026-08-23-LEARNING-RETEST-3.md)

**Fala longa aprovada:** [TINO-EVIDENCE-G4.1-2026-08-23-LONG-VOICE.md](TINO-EVIDENCE-G4.1-2026-08-23-LONG-VOICE.md)

**Revisão/edição:** [TINO-EVIDENCE-G4.1-2026-08-23-REVIEW-EDIT.md](TINO-EVIDENCE-G4.1-2026-08-23-REVIEW-EDIT.md)

**Continuar falando:** [TINO-EVIDENCE-G4.1-2026-08-23-CONTINUE-SPEAKING.md](TINO-EVIDENCE-G4.1-2026-08-23-CONTINUE-SPEAKING.md)

**Inventário global:** [TINO-EVIDENCE-G4.1-2026-08-23-GLOBAL-INVENTORY.md](TINO-EVIDENCE-G4.1-2026-08-23-GLOBAL-INVENTORY.md)

**Continuação de produto:** [TINO-EVIDENCE-G4.1-2026-08-23-PRODUCT-CONTINUATION.md](TINO-EVIDENCE-G4.1-2026-08-23-PRODUCT-CONTINUATION.md)

**Abrir clientes:** [TINO-EVIDENCE-G4.1-2026-08-23-OPEN-CUSTOMERS.md](TINO-EVIDENCE-G4.1-2026-08-23-OPEN-CUSTOMERS.md)

**Clientes curto:** [TINO-EVIDENCE-G4.1-2026-08-23-CLIENTES-SHORT.md](TINO-EVIDENCE-G4.1-2026-08-23-CLIENTES-SHORT.md)

**Timeout/recovery:** [TINO-EVIDENCE-G4.1-2026-08-23-TIMEOUT-RECOVERY.md](TINO-EVIDENCE-G4.1-2026-08-23-TIMEOUT-RECOVERY.md)

**Fallback integrado à voz:** [TINO-EVIDENCE-G4.1-2026-08-23-FALLBACK-VOICE.md](TINO-EVIDENCE-G4.1-2026-08-23-FALLBACK-VOICE.md)

**Falha ProductPicker/continuação:** [TINO-EVIDENCE-G4.1-2026-08-23-PICKER-CONTINUATION-FAIL.md](TINO-EVIDENCE-G4.1-2026-08-23-PICKER-CONTINUATION-FAIL.md)

**Reteste ProductPicker aprovado:** [TINO-EVIDENCE-G4.1-2026-08-23-PICKER-CONTINUATION-RETEST.md](TINO-EVIDENCE-G4.1-2026-08-23-PICKER-CONTINUATION-RETEST.md)

**Reposição aprovada na UI:** [TINO-EVIDENCE-G4.1-2026-08-23-REPLENISHMENT.md](TINO-EVIDENCE-G4.1-2026-08-23-REPLENISHMENT.md)

**Clientes em aberto aprovados:** [TINO-EVIDENCE-G4.1-2026-08-23-CREDIT-LIST.md](TINO-EVIDENCE-G4.1-2026-08-23-CREDIT-LIST.md)

**Lista completa de clientes retestada:** [TINO-EVIDENCE-G4.1-2026-08-23-CUSTOMERS-LIST-RETEST.md](TINO-EVIDENCE-G4.1-2026-08-23-CUSTOMERS-LIST-RETEST.md)

**Estoque de produto específico aprovado:** [TINO-EVIDENCE-G4.1-2026-08-23-PRODUCT-FACT-RETEST.md](TINO-EVIDENCE-G4.1-2026-08-23-PRODUCT-FACT-RETEST.md)

**Resumo financeiro composto aprovado:** [TINO-EVIDENCE-G4.1-2026-08-23-FINANCIAL-SUMMARY-RETEST.md](TINO-EVIDENCE-G4.1-2026-08-23-FINANCIAL-SUMMARY-RETEST.md)

**Cancelamento antes do envio aprovado:** [TINO-EVIDENCE-G4.1-2026-08-23-CANCEL-VOICE.md](TINO-EVIDENCE-G4.1-2026-08-23-CANCEL-VOICE.md)

**Consolidação final:** [TINO-EVIDENCE-G4.1-2026-08-23-FINAL-PASS.md](TINO-EVIDENCE-G4.1-2026-08-23-FINAL-PASS.md)

**Reteste físico:** [TINO-EVIDENCE-G4.1-2026-08-23-RETEST.md](TINO-EVIDENCE-G4.1-2026-08-23-RETEST.md)

Na validação física de 23/08/2026, uma consulta financeira foi encaminhada
para `ProductPicker`; essa causa foi corrigida no roteamento semântico e passou
no reteste físico da mesma entrada. O mesmo protocolo encontrou depois uma
falha entre correção de voz e learning; a correção também passou no reteste
físico do cenário `Maracá → Maratá`. A fala longa foi aprovada no device, mas o
gate ainda não é `PASS_FULL` porque permanecem cenários físicos obrigatórios.

## Motivo

O roadmap foi pausado para corrigir dois riscos P0: transcript sendo processado
antes da confirmação final e crash recorrente ainda sem causa comprovada.

## Defeito encontrado na validação manual

**Input:** “Quais produtos eu tenho cadastrado no meu estoque?”

**Esperado:** `LIST_PRODUCTS`/consulta global do estoque, retornando a lista
de produtos cadastrados sem solicitar uma entidade `Product`.

**Observado:** a UI abriu `ProductPicker` (“Qual produto?”); após selecionar
“Café Maratá”, a retomada terminou em “Não consegui preparar essa operação
global.”

**Causa:** a rota determinística rejeitava listagens que continham “estoque”,
permitindo que a frase caísse no caminho global/Gemma. Além disso, a retomada
de uma escolha global atualizava o `AgentIntent`, mas não atualizava nem
repassava o `globalToolCall` original ao boundary.

**Correção aplicada:** listagens globais agora são classificadas como
`LIST_PRODUCTS`; a retomada injeta a entidade escolhida nos argumentos do
`ToolCall` e volta pelo `askGlobal` original. O reteste físico ainda é
obrigatório antes de promover o gate.

## Segundo defeito encontrado — consulta simples de clientes presa

**Input:** “clientes”

**Esperado:** rota determinística `LIST_CUSTOMERS`, consulta local e card de
clientes em poucos segundos.

**Observado:** o transcript foi exibido, mas a UI permaneceu em “Consultando
seus dados...” por mais de dois minutos.

**Causa:** a entrada curta não era reconhecida pelo `FastIntentRouter` e caía
no interpretador agentic. O processamento não tinha orçamento de tempo após o
commit, permitindo `Understanding` sem saída.

**Correção aplicada:** “clientes” (além de “cliente”, “estoque” e “produtos”)
agora usa fast path local; consultas rápidas têm timeout de 3 s e consultas
agentic de 5 s, com estado `Error` acionável em vez de spinner infinito.
`VOICE_STAGE` registra `ROUTING_*`, `CAPABILITY_*`, `A2UI_READY` e `QUERY_*`
com duração redigida. O reteste físico ainda é obrigatório.

## Terceiro conjunto de defeitos — navegação e continuidade de venda

### BUG A — Product continuation broken

**Input:** “Vender” → seleção de produto.

**Observado:** o `ProductPicker` abriu, mas a retomada terminou em “AINDA NÃO
CONSIGO RESPONDER / operação global”.

**Causa:** a chamada global podia retornar com o produto selecionado, mas sem
quantidade; `REGISTER_SALE` tratava a ausência como erro genérico.

**Correção aplicada:** a capability de venda assume uma unidade quando o
picker forneceu somente o produto. A prévia e a execução usam a mesma regra,
preservando a confirmação antes da mutação.

## Quarto defeito encontrado — consulta de reposição confundida com catálogo

**Input:** “Quais produtos tenho que comprar?”

**Esperado:** `REPLENISHMENT_QUERY`, retornando somente produtos que precisam
de reposição. Com a evidência atual, Café Maratá aparece com `0 unidades` e
deve ser destacado para compra; os demais produtos não devem ser listados como
necessidade de compra sem uma política de estoque que justifique isso.

**Observado:** a resposta exibiu a lista completa de produtos cadastrados,
como se a pergunta fosse `LIST_PRODUCTS`.

**Causa:** o fast router avaliava o marcador “quais produtos” antes de
identificar o verbo de compra/reposição.

**Correção aplicada:** foi criada a capability determinística
`REPLENISHMENT_QUERY`, avaliada antes de `LIST_PRODUCTS`, com primitive A2UI
`product_replenishment` em destaque de atenção. A política de domínio
`InventoryPolicy` já suporta `minimumStock` e `reorderPoint`; até esses valores
serem persistidos por produto, o default é conservador e sinaliza apenas
estoque zerado.

**Evidência de latência:** o teste mostrou `Fast Router HIT 0ms`, `tool 10ms` e
`A2UI 1ms`; portanto este defeito é semântico/routing, não de performance.

### BUG B — Deterministic navigation hangs

**Input:** “Abrir clientes”.

**Observado:** transcript recebido e carregamento indefinido.

**Correção aplicada:** comandos explícitos de navegação agora têm fast path
para `Customers`, `Products`, `CreditList`, `QuickSale` e `StockEntry`, com
transição direta de tela e sem consulta agentic/LLM. O reteste físico ainda é
obrigatório.

## Implementado

### G4.1-A — Transcript Lifecycle / Commit Gate

- `TranscriptLifecycle` explicita `LISTENING`, `PARTIAL`, `REVISING`,
  `FINALIZING`, `REVIEW`, `EDITING`, `COMMITTED`, `PROCESSING` e `ERROR`;
- `TranscriptCommitGate` é uma regra pura de domínio;
- `Partial` e `Revised` atualizam a tela, mas `canSubmit == false`;
- `Committed` entra em `TranscriptReview` e não chama o Agent Runtime;
- só `Enviar` chama `processFinalTranscript`;
- continuação concatena o novo trecho ao draft anterior antes da revisão.

### G4.1-B — Transcript Editor

Na superfície global do TINO foram adicionados:

- `Editar`;
- campo editável de transcrição;
- `Cancelar` edição;
- `Continuar` falando;
- `Enviar` somente após revisão;
- `Cancelar fala` sem chamar o agente.

### G4.1-C — Correction Capture

- edição diferente da transcrição original é enfileirada na sessão;
- após a interpretação grounded, uma alteração conservadora de um token é
  preparada com produto/cliente resolvido;
- a correção é usada imediatamente no texto enviado;
- somente uma execução bem-sucedida materializa o `CorrectionEvent` e passa o
  aprendizado durável pelo `CommerceContextMemory`, `CorrectionLearningEngine`
  e `BusinessMemoryPort` já existentes;
- edições ambíguas ou complexas são capturadas na UI, mas não são aprendidas
  automaticamente.

### G4.1-F — Microphone Validation Instrumentation

- `transcriptValidation` expõe contadores de `Partial`, `Revised`, `Committed`
  e execuções agentic antes/depois do envio;
- `TINO_AUDIT` registra somente metadados redigidos, sem transcript em texto;
- a fronteira da LLM local tem deadline interno de 4,5 s, propaga
  cancelamento corretamente e nunca deixa a UI depender da inferência para
  encerrar o estado de processamento;
- a Gemma local permanece classificador de intenção: recebe o contrato de
  capabilities e devolve apenas `AgentIntent` JSON validado; ela não recebe
  dados crus do Room nem responde fatos comerciais diretamente;
- `AgentQueryBoundary` executa a capability determinística depois da intenção,
  consulta Room e só então o mapper A2UI compõe a resposta grounded;
- `GemmaCircuitBreaker` diferencia `AVAILABLE`, `INITIALIZING`, `DEGRADED`,
  `UNAVAILABLE` e `RECOVERING`; após timeout, crash ou saída malformada, novas
  frases usam fallback imediato até uma probe de recuperação;
- o pipeline registra `VOICE_COMMITTED`, `ROUTING_STARTED/COMPLETED`,
  `AGENT_STARTED/COMPLETED`, `CAPABILITY_STARTED/COMPLETED`,
  `ROOM_QUERY_STARTED/COMPLETED`, `A2UI_READY`, `RENDERED` e
  `QUERY_TIMEOUT`, todos com duração quando aplicável;
- o orçamento é de 3 s para fast path local e 5 s para agente/LLM, sempre com
  estado terminal recuperável;
- uma correção editada registra `originalTranscript`/`correctedTranscript`
  somente no estado local de validação e marca `CorrectionEvent` quando o
  grounding permitir a criação segura;
- o contador é resetado por sessão de voz e não pode tratar `Partial` ou
  `Revised` como execução do agente.

### G4.1-G — Contrato semântico A2UI

- resultados de lista usam `ReadListCard`, escolhas usam `EntityChoice`,
  resumos usam cards semânticos, mutações usam `ActionConfirmation` e erros
  recuperáveis/timeout usam `ErrorStatusCard` com retry;
- `A2uiSemanticMapper` e `A2uiSemanticRegistry` impedem que o fallback de erro
  seja codificado como `Unsupported` ou desenhado como UI ad hoc;
- testes cobrem a primitive de erro e o round-trip JSON, além da exaustividade
  do renderer e do adaptador de superfícies.

### G4.1-H — Hardening adicional do runtime agentic

- `clientes`/`cliente` agora entram no mesmo fast path de navegação de
  `Abrir clientes`; listagens explícitas como `lista meus clientes` continuam
  sendo consultas determinísticas ao Room com `ReadListCard`.
- O Fast Router usa aliases semânticos reutilizáveis para catálogo, clientes e
  reposição, incluindo `lista meu estoque`, `quero ver meus clientes` e
  `o que está zerado`, sem depender de uma igualdade literal única.
- Confirmação, seleção de entidade e desfazer possuem deadline finito; timeout
  registra telemetry e termina em estado recuperável. Cancelamentos não são
  convertidos em resposta de erro por `catch Throwable`.
- A Home mostra o processamento em uma faixa compacta e não desloca a grade
  operacional para fora da viewport.
- O prompt do classificador Gemma referencia o catálogo real de tools,
  capabilities, argumentos, fonte de verdade e primitive A2UI. O modelo segue
  sem acesso a dados crus: a capability executa a consulta grounded no Room.
- `customers.list` passou a declarar o vínculo canônico com
  `TinoCapabilityId.LIST_CUSTOMERS`.

### G4.1-I — CorrectionEvent após execução bem-sucedida

- a forma semântica `quanto de <produto> tenho` produz grounding de estoque
  mesmo quando a execução operacional segue o `GlobalCommandRouter`;
- `VOICE_CORRECTION_QUEUED` não cria nem prova learning;
- o evento preparado só é materializado depois de um resultado operacional
  bem-sucedido;
- cancelamento, timeout, erro, seleção incompleta e ausência de mudança
  semântica não criam `CorrectionEvent` nem memória de negócio;
- as regressões estão em `VoiceCorrectionLearningTest` e passaram junto das
  suítes afetadas.

**Reteste automatizado e instalação — 23/08/2026**

- `gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` passou;
  build `0.1.0-pilot.1`, `versionCode=2`.
- APK instalado com sucesso no device USB conectado `2410FPCC5G`, serial
  `69WOBUFENFLFGAJZ`; o processo `com.tino.app` abriu com PID `24771` e não
  houve `FATAL EXCEPTION`/`AndroidRuntime` no recorte de log após a abertura.
- Esta evidência substitui a suposição de device oficial: o device conectado
  nesta sessão é o usado para o próximo reteste manual. Ela não fecha os itens
  que exigem microfone real.

### G4.1-D/E — Crash Investigation / Boundary

- criado `tools/g4-1-crash-capture.sh` para limpar logcat, abrir a Activity,
  aguardar reprodução, capturar logcat completo, `exit-info`, PID e assinaturas
  de `FATAL EXCEPTION`, `SIGSEGV`, `ANR`, `SQLiteException`, OOM e tombstone;
- o adapter Gemma já retorna fallback determinístico quando a inferência
  retorna indisponível/falha;
- o diagnóstico físico encontrou `signal 11 (SIGSEGV)` em
  `libllm_inference_engine_jni.so`, no símbolo
  `LlmTaskRunner.nativePredictSync`, chamado por
  `MediaPipeGemmaTextInference.generate`;
- a inferência foi movida para `GemmaInferenceService` no processo `:gemma`;
  o processo principal recebe `Unavailable` quando o serviço cai e mantém o
  fallback determinístico;
- `tools/g4-1-gemma-smoke.sh` exercita a chamada real sem tocar Room, tools ou
  mutations; a execução histórica no Xiaomi retornou `GENERATED OK` com o
  processo `:gemma` ativo e sem crash novo.
- No Samsung SM-A042M/API 34, o APK instalou e abriu com o processo principal
  estável; Gemma ficou `UNAVAILABLE` por indisponibilidade do modelo e o
  fallback após queda controlada do processo `:gemma` passou.
- o mesmo harness simula a queda controlada de `:gemma`, confirma
  `Unavailable`/fallback no processo principal, mantém o PID principal vivo e
  permite uma nova chamada após o rebind.

## Definition of Done

- [x] commit gate e lifecycle testáveis;
- [x] parcial/revisado não chegam ao Agent Runtime;
- [x] review editável, continuar, cancelar e enviar implementados;
- [x] correção usada imediatamente e capturada para aprendizado quando
  grounded de forma não ambígua;
- [x] testes automatizados passam;
- [x] APK instalado e aberto em device autorizado (evidência desta sessão:
  `2410FPCC5G`, serial `69WOBUFENFLFGAJZ`; Samsung SM-A042M permanece evidência
  complementar histórica);
- [x] golden flow de fala longa comprovado no device escolhido para a sessão;
- [x] correção manual comprovada no device escolhido para a sessão;
- [x] continuar falando comprovado no device escolhido para a sessão;
- [x] fallback isolado após indisponibilidade/queda da Gemma comprovado no Samsung;
- [x] crash classificado com logcat/exit-info/tombstone histórico;
- [x] boundary de processo separado e fallback implementados;
- [x] contenção de crash nativo comprovada com chamada Gemma no device;
- [x] fallback integrado à voz e ausência de mutação comprovados no device escolhido.
- [x] fala longa, review/edit, continuar falando e correction learning
  comprovados com microfone real no device escolhido;
- [x] consulta global de inventário retestada no device com zero `ProductPicker`;
- [x] retomada de seleção de produto retestada sem fallback de operação global;
- [x] entrada “clientes” retestada com resposta local rápida e sem spinner
  infinito;
- [x] timeout e recuperação acionável comprovados em device;
- [x] “Abrir clientes” navega diretamente para Clientes;
- [x] “Vender” e seleção de produto retomam a prévia com quantidade padrão 1;

## Regra de avanço

G4.1 só vira `PASS_FULL` após todos os itens físicos e o diagnóstico do crash.
G5 continua implementada como `PASS_FULL`, mas nenhum novo gate posterior deve
ser iniciado enquanto G4.1 estiver pendente.

O protocolo físico detalhado e o formato de registro estão em
[TINO-EVIDENCE-G4.1-2026-08-21.md](TINO-EVIDENCE-G4.1-2026-08-21.md). Não são
aceitos transcripts simulados ou valores estimados para fechar este gate.
