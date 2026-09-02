# TINO Intelligence Core — evidência de fechamento da fatia

**Data:** 28/08/2026  
**Escopo:** contexto Evidence → Insight → Attention do painel `···`.

## Entregue

- `TinoEvidenceSnapshotBuilder` coleta fatos pelo `IntelligenceFactsPort` e monta histórico de estoque, comparação de janelas, sazonalidade por dia da semana, recebimentos semanais, comportamento de pagamento e memória governada.
- O mesmo contexto agrega recorrência de compras de clientes e histórico de compras por fornecedor; pedidos de compra persistem data prevista e recebimento real, sem inferir entrega quando esses campos estão ausentes.
- `TinoEvidenceEngine.analyze` preserva evidências candidatas antes do ranking e liga cada insight às evidências correspondentes.
- O ranking combina relevância, urgência, novidade e confiança e limita a superfície visível a três itens.
- A análise estatística de vendas diárias exige ao menos sete dias de base, compara média/desvio do histórico, rotula o resultado como `INFERENCE`/`SUSPECT` e explica que não conhece a causa.
- A sazonalidade por dia da semana exige histórico mínimo e pelo menos duas ocorrências do dia comparado; o contexto expõe média, comparação global, uplift e confiança antes de promover a previsão.
- A previsão de demanda de sete dias exige histórico mínimo e expõe estimativa, faixa inferior/superior, dispersão, horizonte e confiança; ela não preenche dias ausentes com zero inventado.
- O snapshot agora materializa a avaliação temporal do modelo de demanda por produto — janelas de validação, MAE, MAPE, cobertura do intervalo e `passesGate` — e a grava na evidência da previsão, sem promover regressão que não passe o gate.
- Previsão, anomalia, padrão e suspeita permanecem rotulados; Pix ambíguo não gera baixa automática.
- Apenas memórias `LEARNED` e `TRUSTED` entram no contexto; fatos transacionais continuam no Room.
- O `TinoViewModel` publica o contexto reativo e o `MainShell` o consome para a superfície de pensamentos.
- A ação disparada por um pensamento preserva seu contexto até o `AgentIntent`: consultas de reposição/estoque e listas de produto podem consultar o fato do produto, “Ver fornecedor” resolve o fornecedor específico e atalhos globais permanecem sem referência.
- `TinoAttentionEngine` reconcilia o conjunto candidato, resolve sinais que desapareceram após mutação/sync, deduplica por insight e preserva dismiss/snooze.
- O painel também oferece “Amanhã” para adiar uma atenção sem tocar nos dados comerciais.
- Room `attention_items`, `intelligence_evidence` e `attention_outcomes` persistem título, explicação, evidências, prioridade, estado e métricas de resultado; a migration 24→25 adiciona datas previstas/reais aos pedidos de compra.
- `TinoAttentionNotificationWorker` agenda digest local periódico com WorkManager e também uma avaliação one-shot imediata no startup e após mudanças observadas nos fatos; o publisher cria canal Android, respeita `POST_NOTIFICATIONS`, usa IDs estáveis, cancela notificações que saíram do digest e abre diretamente os “Avisos do TINO” ao tocar na notificação.
- O catálogo de conhecimento aprovado deixou de ser apenas process-local: `RoomApprovedKnowledgeCatalog` persiste a versão ativa/anterior na migration 25→26, restaura após nova instância e faz ativação/rollback dentro de transação Room.
- Evidência detalhada da persistência: [TINO-EVIDENCE-KNOWLEDGE-PERSISTENCE-2026-08-28.md](TINO-EVIDENCE-KNOWLEDGE-PERSISTENCE-2026-08-28.md).

## Validação

- `gradle :app:testDebugUnitTest --tests com.tino.app.domain.agent.AgenticQueryTest --no-daemon`: **PASS** — teste de regressão confirma a preservação de `productRef` na ação semântica, incluindo o caminho específico de `REPLENISHMENT_QUERY`.
- `gradle :app:testDebugUnitTest :tino-fiscal-core:test :app:lintDebug :app:assembleDebug --no-daemon`: **PASS** — 551 testes do app e 32 do fiscal, 0 falhas; inclui os testes do núcleo temporal, fornecedores, entrega, estatística, regressão local com backtesting, previsão de demanda, provenance dos valores observados e das recomendações, ranking contextual, catálogo de conhecimento aprovado com validação/rollback e metadados de latência, Attention Engine, publisher de notificações, contexto de entidade nas ações semânticas e preview canônico de entrada de estoque.
- Após o fast path do planner: `gradle :app:testDebugUnitTest --no-daemon`: **PASS** — 553 testes do app, 0 falhas; o módulo fiscal permanece coberto pelo gate verde anterior.
- `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain`: **PASS** — 556 testes do app, 0 falhas; inclui persistência Room, restauração, ativação e rollback do catálogo aprovado.
- `gradle :app:lintDebug :app:assembleDebug --no-daemon`: **PASS** — execução final verde após corrigir a anotação Hilt do worker e integrar a estatística de sazonalidade.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Verificação final após a promoção de `REGISTER_STOCK_ENTRY`, backend CPU do Gemma,
  deep link de notificações, refresh one-shot, timeout finito de 90 s e smoke
  G4.2: APK de 587.118.622 bytes, SHA-256
  `64db442eabae84b8b06f2ea12fa57979e9183a090bfca60f544f80cdc74b30ae`;
  `git diff --check`: **PASS**.
- Build posterior à persistência do catálogo e à correção de provenance: APK de
  587.477.741 bytes, SHA-256
  `f355225c1ee8eb8a9dcf3284788357caf6999da32aa14e60d83989aa87a516c1`;
  `git diff --check`: **PASS**.
- Validação histórica no dispositivo `69WOBUFENFLFGAJZ`: o APK anterior foi instalado incrementalmente sem apagar dados; smoke básico, UIAutomator, painel de atenções, menu “Mais”, tela “Pedido ao fornecedor”, permissão `POST_NOTIFICATIONS` e processo ativo passaram. Naquele momento não foi criado pedido artificial; notificação fora do painel e mutações físicas controladas ficaram pendentes.
- Reexecução física do APK atual: `bash tools/g3-2-smoke.sh ...` e
  `bash tools/g4-1-gemma-smoke.sh /tmp/tino-gemma-current none` retornaram
  `PENDING_DEVICE_VALIDATION` (exit `2`), pois o único serial conectado,
  `R9XW2006AWX`, permanece `unauthorized`.

### Validação física atual — Samsung SM-A042M / Android 14 (API 34)

- O serial `R9XW2006AWX` foi autorizado e o APK atual foi instalado sem apagar os dados. `G3.2` passou: instalação, abertura da `MainActivity` e processo ativo.
- A permissão `POST_NOTIFICATIONS` foi concedida. O painel contextual abriu na tela de entrada, exibiu ações rápidas/consultas e a consulta real de fornecedores retornou os 3 registros locais sem crash.
- O dataset existente possui quatro produtos sem estoque baixo. O painel não exibiu pensamento de atenção e nenhuma entrada artificial foi criada; portanto a ausência de atenção comercial continua sendo silêncio esperado.
- `G4.1` com Gemma real passou após o backend GPU/PowerVR ser substituído pelo caminho CPU/XNNPACK: `GENERATED OK`, carregamento do modelo em 28,7 s e resposta em aproximadamente 29 s, dentro do orçamento finito de 90 s. O cenário de fallback com o processo Gemma encerrado passou também no APK final; o processo principal permaneceu vivo.
- `G4.2` passou no Samsung com o publisher debug-only: canal `tino-attention`, título “TINO percebeu algo”, texto da atenção, permissão Android e preservação dos dados comerciais foram confirmados. O `PendingIntent` de abertura dos “Avisos do TINO” possui cobertura Robolectric; a atenção comercial real continua sem ser fabricada no device.
- Após abrir a `MainActivity` no APK final, o `dumpsys jobscheduler` mostrou o job one-shot do WorkManager do TINO iniciando e finalizando (`SystemJobService` jobs 40/41/42); como o dataset comercial não possui atenção ativa, o worker corretamente não deixou notificação persistente.
- `G4.3` passou no APK final instalado no Samsung com o `IntelligenceRuntimePort` real: uma consulta somente-leitura retornou `ANSWERED`, planner determinístico, fato `products`, analytics `lowest_stock` e resposta baseada no Room, sem mutação. Perguntas já conhecidas não acionam o cold start do Gemma; o modelo permanece reservado para classificação de perguntas não mapeadas. Evidência: `/tmp/tino-g4-3-runtime-final-evidence`.
- `G4.4` passou no Samsung com teste instrumentado de banco Room em memória: prévia não mutou, confirmação com token persistiu a entrada, execução direta foi bloqueada e replay foi rejeitado. Evidência: [TINO-EVIDENCE-G4.4-MUTATION-2026-08-28.md](TINO-EVIDENCE-G4.4-MUTATION-2026-08-28.md).
- `G4.5` confirmou que o snapshot pode ser lido no Samsung sem mutação, mas o banco atualmente instalado está vazio (`products=0`, `customers=0`, `stock_movements=0`); portanto a avaliação de previsão em dados reais permanece bloqueada por restauração do dataset. Evidência: [TINO-EVIDENCE-G4.5-DATASET-2026-08-28.md](TINO-EVIDENCE-G4.5-DATASET-2026-08-28.md).
- Evidências brutas: `/tmp/tino-gemma-current`, `/tmp/tino-gemma-fallback`, `/tmp/tino-gemma-fallback-final`, `/tmp/tino-gemma-fallback-stock-entry`, `/tmp/tino-gemma-cpu-CU0vnV`, `/tmp/tino-gemma-final-90s-BjBz6O`, `/tmp/tino-gemma-fallback-final-IOqSbu`, `/tmp/tino-gemma-latest-dfc85X` e `/tmp/tino-g4-3-runtime-fast-path`.

## Limites assumidos

Esta evidência fecha o núcleo executável desta fatia e a prova física do caminho de consulta, não o Intelligence System completo. Permanecem explícitos: atenção comercial real sem dados fabricados, mutação no banco piloto real mediante autorização, validação/aprovação do modelo de negócio em dados reais, RAG externo produtivo, cobertura física universal e inteligência de objetivos.
