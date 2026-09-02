# TINO Gemma Effectiveness Audit

**Data:** 2026-08-30  
**Escopo:** Android, runtime local, voz, agente, A2UI e intelligence  
**Regra:** auditoria somente; nenhum código, modelo ou arquitetura foi alterado nesta rodada.

## Decisão histórica

**PILOT_ONLY na auditoria; supersedida por `REMOVE` no goal de remoção de 2026-08-30.**

Este documento registra a auditoria que antecedeu a decisão final. A evidência
de implementação e validação da remoção está em
`TINO-GEMMA-REMOVAL-EVIDENCE-2026-08-30.md`.

O Gemma tem integração real e cobre interpretação aberta/contextual que não
está toda coberta pelas regras. Porém, não há evidência suficiente de ganho
funcional exclusivo para justificar colocá-lo no produto principal: os
comandos operacionais mais frequentes são atendidos antes dele, e o custo
físico é alto.

Isso significa:

- manter a integração para pilotos controlados e investigação;
- não expor o Gemma como dependência do MVP público;
- não removê-lo nesta rodada, porque existem fluxos contextuais e de
  planejamento aberto que ainda podem depender dele;
- não otimizar nem trocar o modelo antes de obter um corpus real de uso.

## Resposta principal

> Que problema concreto do TINO o Gemma resolve hoje que não é resolvido suficientemente bem sem ele?

Ele resolve a primeira interpretação de linguagem livre/contextual quando a
linguagem determinística e os routers não reconhecem a frase, produzindo um
envelope estruturado para intenção, extração de campos ou proposta de plano.
Isso é útil para frases variadas e perguntas compostas, mas a auditoria não
encontrou prova de que esse ganho seja necessário para o fluxo principal dos
primeiros clientes. O Gemma não é a fonte de verdade, não consulta Room, não
calcula fatos e não executa tools.

## Integração encontrada

| Componente | Situação atual | Classificação da auditoria |
|---|---|---|
| `gemma3-1b-it-int4.task` | asset presente em `assets/models/`; copiado para armazenamento privado no primeiro uso | PILOT_ONLY |
| `GemmaInferenceService` | MediaPipe `LlmInference`, CPU, processo isolado `:gemma`, máximo de 384 tokens | PILOT_ONLY |
| `MediaPipeGemmaTextInference` | porta Binder/Messenger, mutex, circuit breaker, timeout de 90 s, limite de prompt de 900 caracteres | PILOT_ONLY |
| `MediaPipeGemmaAgentIntentAdapter` | `AgentIntentInterpreter` injetado no coordinator | PILOT_ONLY |
| `MediaPipeGemmaOrchestrator` | `GemmaOrchestrator` usado pelo caminho de voz legado, com fallback para `GlobalCommandRouter` | PILOT_ONLY |
| `MediaPipeGemmaStructuredExtractor` | extrai campos de onboarding, produto, entrada, cliente, fornecedor e vendas contextuais | PILOT_ONLY |
| `GoogleAdkGemmaPlanProposal` | proposta de plano apenas quando o planner determinístico não reconhece a pergunta | PILOT_ONLY |
| `AndroidGemmaCreditPlanInferenceAdapter` | ponte para spike/contrato Koog; não há call site de produto identificado | REMOVE_LATER / código de spike |
| `GemmaSmokeActivity` | debug-only; não chega a tool, Room ou mutação | KEEP como ferramenta de diagnóstico |
| `AndroidSpeechRecognizerRuntime` | ASR do Android; independente do modelo | CORE_MVP separado do Gemma |

Dependências identificadas: `com.google.mediapipe:tasks-genai:0.10.35`, modelo
`gemma3-1b-it-int4.task` e ADK Kotlin usado como adaptador de planejamento.

## Caminho real de execução

Há dois caminhos de voz montados na aplicação:

1. `AgenticVoiceViewModel` → `AgenticTextQueryCoordinator` → interpretação de
   contexto → `FastIntentRouter` → `CommandIntentRouter` →
   `GlobalCommandRouter` → Gemma `AgentIntentInterpreter` somente quando não
   houve resolução → runtime de intelligence quando necessário → boundary
   validado → A2UI/domínio.
2. `VoiceViewModel` → `VoiceCommandCoordinator` → `GemmaOrchestrator` direto
   → `ToolExecutor`; leitura executa após grounding e mutação passa por
   preview/confirmação.

O app compõe os dois ViewModels e os dois caminhos de UI. Portanto, “Gemma
integrado” e “Gemma chamado no fluxo” são ambos verdadeiros hoje, embora não
para todas as consultas.

O planner de intelligence também está conectado: o
`AdkQueryPlanner` retorna primeiro o plano determinístico; só chama a proposta
ADK/Gemma quando o objetivo determinístico é `UNSUPPORTED`. A execução das
tools continua local e validada.

## Matriz de cenários

“Evidência” distingue teste determinístico/estrutural de inferência real do
modelo. Não foi fabricada uma resposta do Gemma para preencher lacunas.

| Entrada | Caminho determinístico | Gemma é necessário? | Resultado seguro esperado | Evidência |
|---|---|---:|---|---|
| “Quanto recebi hoje?” | `READ_FINANCIAL_SUMMARY` / `GET_TODAY_SALES` | Não | consultar fatos locais e renderizar resultado | `FastIntentRouterTest` e `GlobalCommandRouterTest` passam |
| “Quais produtos estão acabando?” | `REPLENISHMENT_QUERY` / `PREPARE_PURCHASE` | Não | consultar reposição, sem inventar produtos | `FastIntentRouterTest` passa; planner determinístico também cobre reposição |
| “João ficou de pagar 180 sexta” | não é pagamento recebido; não há rota determinística comprovada para mutação | Não para agir | pedir esclarecimento ou não agir; nenhuma baixa | regra de pagamentos exige verbo de pagamento e cliente; nenhum E2E Gemma real foi provado |
| “João me pagou 50 no Pix” | interpretação de pagamento determinística / comando global | Não | `REGISTER_CREDIT_PAYMENT`, João, `5000` centavos, `pix`, com grounding | `LanguageFoundationTest` e `GlobalCommandRouterTest` passam |
| “Dei entrada em 3 caixas de cerveja” | entrada sem custo explícito não pode confirmar | Não para agir | pedir custo/produto/unidade; não registrar estoque silenciosamente | router global exige custo; coordinator exige `unitCostCents` |
| correção “Maracá → Maratá” | memória contextual, correção e resolução fonética | Não | resolver produto real ou pedir escolha, sem segunda mutação | `AdaptiveLexiconTest`, `LanguageFoundationTest` e `VoiceCorrectionLearningTest` passam |
| pergunta ambígua | resolução de entidade/A2UI | Não | `EntityChoice`/clarificação, sem adivinhar | `EntityResolutionServiceTest` e `UiPlannerTest` passam |
| fora do domínio | `NoMatch`/`UNSUPPORTED` e fallback seguro | Não | não executar capability | testes de parser, router, planner e fallback passam |
| pergunta aberta não coberta por regras | cai no interpreter Gemma; depois pode cair no planner ADK/Gemma | Possivelmente | intenção/plano estruturado ou `UNSUPPORTED`; nunca execução direta | caminho existe no código, mas não há taxa de acerto do modelo real por cenário |

### O que foi efetivamente medido no Gemma real

O smoke físico disponível é deliberadamente limitado e documenta isso no
próprio código: ele não chama tool, Room, mutation ou Agent Runtime.

- modelo: `gemma3-1b-it-int4.task`;
- tamanho do modelo no asset: `554.661.243` bytes / `528,97 MiB`;
- APK debug: `587.330.463` bytes / `560,12 MiB`;
- o modelo representa aproximadamente `94,44%` do APK medido;
- processo: `com.tino.app:gemma`;
- backend: CPU;
- smoke simples `Responda somente ... OK`: `GENERATED OK`;
- emulator API 35: carregamento após asset privado existente em `4.211 ms`;
- processo Gemma após inferência: aproximadamente `907.799 KB PSS` e
  `979.984 KB RSS`, com cerca de `231.912 KB` de heap nativo e o mapeamento
  de aproximadamente `554.732 KB` do asset;
- timeout configurado para uma solicitação: `90.000 ms`;
- geração máxima configurada: `384` tokens;
- cold start observado anteriormente no device Samsung: aproximadamente
  `28,7 s` até o modelo carregar e resposta `GENERATED OK` em aproximadamente
  `29 s`;
- bateria/energia: o emulator reportou drain global zerado e não produziu
  uma medida atribuível confiável ao Gemma; portanto, consumo de bateria não
  foi declarado medido.

O prompt real de intenção do harness debug com as 16 capabilities padrão
produziu `1006` caracteres para “Quanto recebi hoje?” e foi rejeitado pelo
limite de `900` antes da inferência. Isso é uma limitação do harness/contrato
de prompt, não um “acerto” ou “erro semântico” do modelo. O harness de smoke
também iniciou a tela antes de o warm-up assíncrono terminar em uma execução:
respondeu `UNAVAILABLE` em cerca de `3,8 s`, enquanto o modelo terminou de
carregar depois de cerca de `10,4 s`.

Conclusão da medição física: a capacidade de gerar texto existe, mas a
auditoria não possui ainda medição válida de precisão do Gemma em uma tarefa
de negócio end-to-end. O custo físico, entretanto, é inequívoco.

## Qualidade, segurança e fallback

Pontos positivos observados:

- JSON é extraído e validado antes de entrar no domínio;
- IDs, fatos financeiros e saldos não devem vir do modelo;
- ações passam por resolução, preview e confirmação;
- saída malformada, indisponibilidade, timeout e queda do processo têm
  fallback seguro;
- o processo isolado protege o app principal contra falhas nativas do
  MediaPipe;
- A2UI recebe resultados estruturados/grounded, não texto livre do modelo.

Limitações observadas:

- não há benchmark de precisão real comparando o mesmo corpus entre Fast
  Router/regras e Gemma;
- não há taxa real de fallback, ações erradas, latência por intenção ou
  consumo de bateria por cenário;
- o smoke de intenção atual pode rejeitar prompts padrão antes da inferência;
- a primeira chamada pode ocorrer durante warm-up e cair para fallback;
- há duplicação de superfície: voz legada chama Gemma diretamente e a voz
  agentic possui outra cadeia de roteamento;
- `AndroidGemmaCreditPlanInferenceAdapter` está injetado como contrato, mas
  não apareceu como caminho de produto ativo nesta auditoria.

## Sem Gemma, o que quebra?

Não quebra o núcleo operacional:

- consultas financeiras, estoque, reposição, clientes, fornecedores e
  recebíveis com frases conhecidas continuam nos routers/regras;
- fatos continuam vindo de Room/repositórios e analytics determinísticos;
- vendas, pagamentos e entradas continuam sujeitos a preview, confirmação e
  domínio comercial;
- ambiguidade continua podendo virar escolha explícita;
- correção e learning local continuam na camada de linguagem/memória;
- A2UI e backend não dependem de o texto ter sido gerado por um LLM;
- ASR é uma dependência separada do Gemma.

O que é perdido ou degradado:

- frases abertas e variações não cobertas pelos routers;
- extração contextual de vários campos em telas de cadastro/entrada;
- proposta de plano ADK para perguntas que o planner determinístico não
  reconhece;
- o caminho legado de voz precisa usar seu fallback determinístico ou ficar
  limitado às frases reconhecidas.

Em todos esses casos, o comportamento seguro existente é fallback, edição
manual, clarificação ou `UNSUPPORTED`; não há evidência de que a remoção
cause duplicação de estoque ou perda do ledger.

## Classificação operacional

| Área | Classificação | Decisão prática |
|---|---|---|
| Gemma como dependência obrigatória do MVP | `FUTURE` / não expor | não exigir para ativação do cliente |
| Gemma integrado para piloto | `PILOT_ONLY` | manter atrás de canal/allowlist de piloto |
| smoke e testes de parser/fallback | `HIDDEN_READY` | manter para validação técnica |
| routers determinísticos e linguagem local | `CORE_MVP` | preservar e priorizar no fluxo público |
| `AndroidGemmaCreditPlanInferenceAdapter` sem call site de produto | `REMOVE_FROM_EXPERIENCE` agora; `REMOVE_LATER` no código | não expor; remover somente após confirmação de dependências |
| voz legada + voz agentic simultâneas | `REMOVE_FROM_EXPERIENCE` | escolher uma superfície pública antes de ampliar escopo |

## Gaps que impedem `KEEP`

Para promover o Gemma de `PILOT_ONLY` a `KEEP`, ainda seria necessário um
benchmark real, sem alterar o produto durante a coleta, com corpus de uso
representativo e pares Fast Router/Gemma contendo:

- intenção e entidades corretas;
- capability/tool e argumentos;
- resultado grounded correto;
- latência cold/warm;
- PSS/RSS e tempo de warm-up;
- fallback, timeout, saída malformada e ação indevida;
- energia em aparelho real;
- ganho exclusivo do Gemma sobre regras;
- proporção de uso que realmente chega ao Gemma.

O critério deve ser ganho incremental observado, não apenas “o modelo
carregou” ou “a resposta parece natural”.

## Evidências consultadas

- `app/src/main/java/com/tino/app/domain/agent/AgenticTextQueryCoordinator.kt`:
  ordem de linguagem → Fast Router → Command Router → Global Router → Gemma.
- `app/src/main/java/com/tino/app/core/speech/MediaPipeGemmaAgentIntentAdapter.kt`:
  prompt, schema, parser e validação de intenção.
- `app/src/main/java/com/tino/app/core/speech/MediaPipeGemmaTextInference.kt`:
  limite de prompt, timeout, circuit breaker e processo isolado.
- `app/src/main/java/com/tino/app/core/speech/GemmaInferenceService.kt`:
  MediaPipe, CPU, warm-up e `:gemma`.
- `app/src/main/java/com/tino/app/core/intelligence/AdkPlanProposal.kt` e
  `app/src/main/java/com/tino/app/domain/intelligence/planning/AdkQueryPlanner.kt`:
  uso opcional do Gemma no planejamento.
- `TINO-EVIDENCE-G4.3-2026-08-28.md`: intelligence real no device com planner
  determinístico e fallback seguro.
- `TINO-PROJECT-STATUS.md`: inferência Gemma real no Samsung e limites ainda
  pendentes de avaliação do modelo.
- Suítes executadas nesta auditoria: `FastIntentRouterTest`,
  `GlobalCommandRouterTest`, `MediaPipeGemmaOrchestratorTest`,
  `MediaPipeGemmaAgentIntentAdapterTest`, `MediaPipeGemmaStructuredExtractorTest`,
  `GemmaVoiceInputAdapterTest`, `PlannerPortTest`, `AgenticGemmaA2uiTest`,
  `EntityResolutionServiceTest`, `AgenticQueryTest`, `LanguageFoundationTest`,
  `VoiceCorrectionLearningTest`, `UiPlannerTest` e
  `AndroidGemmaCreditPlanInferenceAdapterTest` — **BUILD SUCCESSFUL**.

## Estado da worktree

A worktree já estava amplamente modificada antes desta auditoria. Nenhum
arquivo de código, modelo, build script ou teste foi alterado nesta rodada; o
único artefato produzido foi este relatório.
