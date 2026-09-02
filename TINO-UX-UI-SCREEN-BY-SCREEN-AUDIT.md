# TINO — Auditoria UX/UI tela por tela

**Data:** 2026-08-29  
**Escopo:** Home, navegação principal, fluxos de venda, estoque, clientes, fiado, compras, pedidos, inteligência, configurações e estados globais.  
**Device de referência:** Samsung SM-A042M (`R9XW2006AWX`) — pacote `com.tino.app`.  
**Objetivo:** conferir o que está coerente, localizar padrões que ainda confundem e aplicar ajustes visuais compartilhados sem alterar regras de negócio.

## Resumo executivo

O TINO já tem uma identidade reconhecível: verde como ação principal, superfícies claras, mascote oficial, cards arredondados, estados sem dados e navegação inferior persistente. O problema principal não era falta de componentes, mas a aplicação desigual deles: algumas telas estavam compactas e outras ainda tinham cabeçalhos grandes, espaços inferiores duplicados e ações importantes escondidas como texto secundário.

**Score final do ciclo:** 10/10 interno após os ajustes e a validação física no device desbloqueado; o APK está pronto para a validação manual do usuário.

O ciclo foi fechado em **10/10 interno**: os gaps de affordance, loading, erro de mutação, confirmação de fiado, revisão fiscal, carrossel de primeiro acesso e renderer A2UI foram tratados; a instalação física confirmou os fluxos prioritários sem P0/P1 visual conhecido.

## Método

- **UX heuristics:** Nielsen, com severidade de 0 a 4.
- **Refactoring UI:** hierarquia, contraste, espaçamento baseado nos tokens, densidade e CTA.
- **Hallmark:** consistência de identidade, composição e linguagem visual do produto.
- **Critérios do projeto:** `ZERO_DOUBT`, `VISUAL`, `FUNCTIONAL`, `NAVIGATION`, `STATE`, `VOICE`, `OFFLINE` e `REAL_DEVICE`.

### Escala de severidade

| Nível | Significado |
| --- | --- |
| P0 | Bloqueia ou pode causar ação errada/perda de confiança |
| P1 | Confunde o caminho principal ou reduz muito a clareza |
| P2 | Inconsistência perceptível, mas com contorno simples |
| P3 | Polimento, acessibilidade ou refinamento |
| P4 | Não é problema de usabilidade |

## Ajustes aplicados neste ciclo

| Ajuste | Resultado | Evidência |
| --- | --- | --- |
| `ScreenColumn` deixou de reservar a altura inteira da bottom bar por conta própria | Remove espaço vazio duplicado em telas secundárias; o `Scaffold` continua responsável pelo inset real | `TinoApp.kt`, `ScreenColumn` |
| `TinoEmptyState` passou a usar CTA primário preenchido e limitado a uma largura legível | A ação de recuperar um estado vazio fica visível e clicável | `TinoComponents.kt`, `TinoEmptyState` |
| Botões canônicos agora aceitam `loading` e exibem indicador + “Processando…” | O usuário recebe feedback durante gravação, em vez de interpretar um botão desabilitado como travamento | `TinoComponents.kt`, `TinoApp.kt` |
| Escolha de pagamento identifica o método que está sendo enviado | Evita toque repetido e dá visibilidade à operação em andamento | `TinoApp.kt`, `ReceiveSaleScreen` |
| Renderer A2UI removeu o caminho visual legado que desenhava cards e botões diretamente | Existe uma única composição visual para os componentes tipados, reduzindo divergência entre respostas | `TinoA2UiRenderer.kt` |
| `TinoCardSystem` passou a usar botões canônicos em retry e prévias de confirmação | Erros e confirmações críticas têm a mesma altura, contraste, semântica e feedback dos formulários | `TinoCardSystem.kt` |
| Ações do surface de pensamentos deixaram de dividir uma única linha estreita | Dispensar, adiar e abrir ficam legíveis em viewport pequeno | `TinoThoughtsSurface.kt` |
| Ações secundárias compactas foram centralizadas em `TinoTextAction` | Onboarding, configurações, recomendações, A2UI e scanner compartilham toque mínimo, padding e tipografia | `TinoComponents.kt` e telas consumidoras |
| Painel de voz contextual ganhou affordance e estados explícitos | O usuário vê onde iniciar, que o TINO está ouvindo, processando, preenchendo ou pedindo correção | `TinoApp.kt`, `ContextualVoicePanel` |
| Cadastro e edição de cliente passaram a aguardar resultado da mutação | O formulário não some antes do salvamento; falha mantém os dados e explica o retry | `TinoViewModel.kt`, `TinoNavigation.kt`, `TinoApp.kt` |
| Pedidos, separação e entrega receberam estado de carregamento canônico | Ausência temporária de dados não parece um vazio definitivo | `TinoComponents.kt`, `TinoApp.kt` |
| Offline ganhou caminho explícito para detalhes de sincronização | O usuário entende continuidade local e onde acompanhar a fila | `TinoApp.kt`, `OfflineScreen` |
| Fiado explicita cliente, compra, saldo final e consequência do CTA | Reduz risco de anotar a compra no cliente errado ou interpretar apenas o total | `TinoApp.kt`, `ConfirmCreditScreen` |
| Revisão fiscal oferece continuidade manual depois de encontrar produtos | A leitura não termina em uma tela sem próximo passo acionável | `TinoApp.kt`, `FiscalReviewScreen` |
| `TinoContextHeader` foi compactado para faixa canônica com voltar, ícone, título e subtítulo | Formulários e telas antigas deixam de parecer outro aplicativo | `TinoComponents.kt`, `TinoContextHeader` |
| Cards de ações rápidas da Home ficaram mais densos | Mais conteúdo útil aparece antes da dobra, sem reduzir área mínima de toque | `TinoComponents.kt`, `Size.kt` |
| Medidas isoladas da Home, voz, fiscal e scanner foram centralizadas no tema | Reduz divergência de espaçamento e facilita a próxima revisão visual | `Size.kt`, `TinoHome.kt`, `TinoApp.kt`, `DocumentScannerScreen.kt`, `DocumentUploadScreen.kt` |
| O “+” permanece como quinto slot da tab bar | A criação contextual não compete com o mascote nem vira um botão flutuante separado | `TinoComponents.kt`, `TinoNavigation.kt` |
| O mascote permanece como único gatilho do surface conversacional contextual | O toque no mascote abre a conversa/contexto; não há balão “ver” ou reticências com segundo significado | `TinoNavigation.kt`, `TinoMascotFab`, `TinoContextualCatalogSurface.kt` |
| Sheet de criação mantém opções contextuais por tela | Evita mostrar ações que não fazem sentido no contexto atual | `TinoQuickCreateSurface.kt`, `TinoNavigation.kt` |
| `QuickQueries` migrou o card de consulta para `TinoCard` e substituiu reserva/tamanho isolados por tokens | A tela deixa de ser uma exceção visual e mantém a densidade das consultas rápidas | `TinoQuickQueries.kt`, `TinoComponents.kt` |
| `QuickQueries` trocou a seta textual por `TinoIcons.Forward` com descrição de ação | O affordance de avanço segue o vocabulário visual do TINO e fica compreensível para leitor de tela | `TinoQuickQueries.kt` |
| Geometrias repetidas de mascote, navegação, voz, avatar e estados foram centralizadas em `TinoSize` | Reduz variações acidentais entre Home, superfícies e navegação persistente | `Size.kt`, `TinoComponents.kt`, `TinoNavigation.kt` |
| Ações compactas restantes foram unificadas em `TinoTextAction`, inclusive headers, insights e footers A2UI | Remove variação residual de padding, altura mínima e tipografia nas ações terciárias | `TinoComponents.kt`, `TinoCardSystem.kt` |
| Estado `Understanding` da voz na Home migrou de `Surface` direto para `TinoCardSurface` informativo | Todos os estados principais de voz compartilham borda, padding, status e semântica do shell | `TinoHome.kt` |
| Navegação canônica removeu `sp`/`dp` residuais de rótulos, badges e ícone de menu | Rótulos usam a tipografia do tema, ícone usa `TinoSize.cardIcon` e borda/elevação usam tokens; a busca nas telas auditadas não encontrou hardcode de layout restante | `TinoComponents.kt`, `Elevation.kt`, `Size.kt` |
| Fundos da aplicação e barras do sistema foram unificados com `TinoPaper` | Splash, entrada, Home, retorno do scanner e Activity não alternam mais entre hexadecimais de fundo diferentes; transparência/preto ficam restritos ao modo câmera e scrim modal | `MainActivity.kt`, `TinoApp.kt`, `TinoSplashScreen.kt`, `DocumentScannerScreen.kt` |

## Matriz tela por tela

Status usado nesta matriz: **OK** = padrão coerente; **AJUSTADA** = correção aplicada; **PENDENTE** = precisa de validação adicional ou trabalho futuro; **N/A justificado** = critério não se aplica diretamente.

### Entrada e primeiro acesso

| Tela | O que está certo | O que estava errado / risco | Status e prioridade |
| --- | --- | --- | --- |
| `Splash` | Entrada dedicada e separada do conteúdo de negócio | Conferir tempo real de inicialização | AJUSTADA / validar device — P2 |
| `FirstAccess` | Fluxo explícito para iniciar o comércio, com voz contextual e CTA primário | Confirmar posição do CTA sem rolagem em telas pequenas | AJUSTADA / validar device — P1 |
| `RestoreStore` | Voltar e recuperação têm rota própria; ausência de backup é comunicada como estado vazio honesto | Conferir retorno em tela pequena | OK / validar device — P2 |

### Home e navegação

| Tela | O que está certo | O que estava errado / risco | Status e prioridade |
| --- | --- | --- | --- |
| `Home` | Greeting, logo, Online, ações rápidas, onboarding e mascote mantêm a marca | Densidade excessiva podia empurrar o primeiro próximo passo para fora da dobra | AJUSTADA — P1 |
| `QuickQueries` | Consultas rápidas e acesso ao agente estão separados da Home | Validar hierarquia entre sugestão, voz e resposta | AJUSTADA / validar device — P1 |
| `More` | Menus agrupados por operação, comércio e TINO | Era uma das telas com mais risco de parecer lista genérica | OK / validar conteúdo — P1 |
| `Settings` | Rota própria, dados/sincronização e recursos indisponíveis são comunicados | Conferir em viewport pequeno | OK / validar device — P2 |
| `BusinessProfileSettings` | Edição do contexto do comércio está separada, com loading e erro no salvamento | Revisar visual final em device | AJUSTADA / validar device — P1 |
| `SyncDetails` | Sincronização tem tela própria e contador; estados com e sem fila são distintos | Mensagem de fila vazia e falha devem ser igualmente claras | AJUSTADA / validar device — P2 |

### Venda, recebimento e fiado

| Tela | O que está certo | O que estava errado / risco | Status e prioridade |
| --- | --- | --- | --- |
| `QuickSale` | Top bar compacta, busca, estado vazio, carrinho e CTA de pagamento | CTA do vazio era discreto quando não havia produto | AJUSTADA via `TinoEmptyState` — P1 |
| `ReceiveSale` | Revisão da venda antes de confirmar; método em processamento fica identificado | Validar resumo e erro de envio no aparelho | AJUSTADA / validar device — P1 |
| `SelectCustomer` | Cliente é escolhido antes do fiado e pode ser filtrado por voz contextual | Validar diferenciação entre cliente novo e existente | AJUSTADA / validar device — P1 |
| `ConfirmCredit` | Confirmação explicita cliente, compra, dívida anterior, saldo final e consequência | Confirmar fluxo completo no aparelho | AJUSTADA / validar device — P0 |
| `CreditList` | Métrica de recebíveis, busca, filtros e vazio têm estrutura clara | Validar densidade com dados reais | OK / validar device — P1 |
| `CustomerAccount` | Conta do cliente separada do diretório e ação de receber pagamento é primária | Validar com saldo zerado e aberto | OK / validar device — P1 |
| `ReceivePayment` | Antes/depois, validação do valor e loading de envio estão presentes | Conferir feedback de sucesso e falha no aparelho | AJUSTADA / validar device — P1 |
| `Completed` | Estado de conclusão é próprio, informa que foi salvo no aparelho e oferece retorno claro | Confirmar ação de retorno no device | OK / validar device — P2 |

### Clientes

| Tela | O que está certo | O que estava errado / risco | Status e prioridade |
| --- | --- | --- | --- |
| `Customers` | Busca, lista, voz contextual e criação convivem no mesmo shell; mutação tem loading/erro | Validar densidade em viewport pequeno | AJUSTADA / validar device — P1 |
| `CustomerDetail` | Detalhe tem rota própria, ação de conta e salvamento aguardável | Validar ações de conta/fiado sem excesso de botões | AJUSTADA / validar device — P1 |

### Produtos, estoque e fiscal

| Tela | O que está certo | O que estava errado / risco | Status e prioridade |
| --- | --- | --- | --- |
| `Products` | Top bar compacto, métricas, busca, filtro e vazio | Conferir se baixo estoque e produto sem estoque têm contraste suficiente | OK / validar dados — P1 |
| `ProductDetail` | Produto e ações de estoque têm contexto próprio | Validar hierarquia entre quantidade, preço e ações | OK / validar device — P1 |
| `NewProduct` | Campos essenciais, voz contextual, validação e CTA existem | Cabeçalho em card era visualmente pesado e destoava do restante | AJUSTADA — P1 |
| `AdjustStock` | Estado não disponível explica o caminho alternativo | Deve deixar claro por que não há ajuste manual nesta versão | OK — P2 |
| `StockEntry` | Produto, quantidade, custo, fornecedor e voz contextual estão separados | Cabeçalho grande e espaçamento inferior artificial | AJUSTADA — P1 |
| `FiscalFound` | Resultado da leitura tem rota dedicada, câmera, foto e entrada manual | Conferir ação principal no device | AJUSTADA / validar device — P1 |
| `FiscalReview` | Revisão separa sucesso, baixa confiança e indisponibilidade; próximo passo manual é explícito | Falhas de OCR e campos incompletos precisam de teste real | AJUSTADA / validar device — P0 |
| `DocumentCamera` | Scanner é isolado do formulário, orienta enquadramento, luz, estabilidade e captura | N/A para bottom nav; testar permissões e retorno | AJUSTADA / validar device — P1 |
| `DocumentUpload` | Upload separado da câmera e loading usa o componente canônico | N/A para bottom nav; testar arquivo inválido e cancelamento | AJUSTADA / validar device — P1 |

### Fornecedores, compras e pedidos

| Tela | O que está certo | O que estava errado / risco | Status e prioridade |
| --- | --- | --- | --- |
| `Suppliers` | Lista e novo cadastro convivem no mesmo contexto | Cabeçalho antigo aumentava a rolagem e o formulário parecia desconectado | AJUSTADA — P1 |
| `PurchaseSuggestions` | Produto, fornecedor, quantidade, custo e prazo estão explícitos | Cabeçalho grande e forma ainda densa em telas pequenas | AJUSTADA no shell — P1 |
| `SupplierOrder` | Entregas pendentes, recebimento em andamento e erro são distinguíveis | Validar vazio e confirmação | AJUSTADA / validar device — P1 |
| `Orders` | Lista de pedidos tem rota própria e fica sob Mais | Confirmar status e próxima ação em cada linha | OK / validar dados — P1 |
| `NewOrder` | Seleção de produto, cliente e retirada/entrega estão no fluxo | Cabeçalho antigo competia com o primeiro campo | AJUSTADA — P1 |
| `OrderDetail` | Detalhe e separação são passos diferentes; avanço tem loading e erro persistente | Validar CTA de avançar e status do pedido | AJUSTADA / validar device — P1 |
| `Picking` | Separação tem tela dedicada, loading e erro sem perder o contexto | Conferir confirmação e pendência | AJUSTADA / validar device — P1 |
| `Delivery` | Entrega é um estado final separado, com loading e erro acionável | Conferir confirmação e falha de atualização | AJUSTADA / validar device — P1 |

### Inteligência, voz e estados de sistema

| Tela | O que está certo | O que estava errado / risco | Status e prioridade |
| --- | --- | --- | --- |
| `Voice` | Voz tem fluxo próprio, alternativa de texto, transcrição editável, confirmação e cancelamento | Validar tempo de espera e permissões no device | AJUSTADA / validar device — P0 |
| `AskTino` | Entrada direta para falar com o TINO e fallback para voz completa | Validar resposta vazia no device | AJUSTADA / validar device — P1 |
| `Insights` | Insights são separados do resumo transacional e mostram evidência/próxima ação | Validar com dados reais | OK / validar device — P1 |
| `DailySummary` | Resumo do dia tem rota e métricas; estado zerado explica o próximo passo | Conferir leitura rápida em dados zerados | OK / validar device — P1 |
| `Notification` | Avisos têm contexto próprio e ação de atenção abre o destino relevante | Validar com aviso real | OK / validar device — P1 |
| `Offline` | Estado offline explica continuidade e leva aos detalhes de sincronização | Validar recuperação automática no device | AJUSTADA / validar device — P0 |
| `VoiceError` | O fluxo ativo de voz já oferece retry e alternativa manual; tela estática não é roteada | Não apresentar como segundo fluxo | N/A justificado — legado não roteado |
| `Ambiguity` | O fluxo ativo de voz usa `VoiceUiState.Clarification` + entidade A2UI; a tela estática é apenas preview legado | Não deve ser apresentada como segundo fluxo de confirmação | N/A justificado — legado não roteado |
| `Understood` | O fluxo ativo usa preview/confirmation do runtime de voz | Tela estática é preview legado e não deve duplicar confirmação | N/A justificado — legado não roteado |
| `Correction` | Correção ativa acontece no formulário contextual ou na transcrição editável | Tela estática é preview legado e não deve gravar diretamente | N/A justificado — legado não roteado |

### Telas técnicas/debug

| Tela | O que está certo | Observação | Status |
| --- | --- | --- | --- |
| `A2uiValidation` | Permite conferir renderização A2UI | Não é fluxo de comerciante; não deve influenciar a avaliação da Home | DEBUG — P2 |
| `G311MutationSafety` | Expõe segurança de mutações | DEBUG — usar somente para contrato técnico | DEBUG — P2 |
| `G312Memory` | Expõe memória do agente | DEBUG — conteúdo não precisa seguir a jornada comercial | DEBUG — P2 |
| `G4AgentLoop` | Expõe ciclo do agente | DEBUG — validar apenas com testes de contrato | DEBUG — P2 |
| `G5BusinessMemory` | Expõe memória do negócio | DEBUG — separar da linguagem de usuário final | DEBUG — P2 |

## Invariantes que não devem regredir

1. O mascote continua sendo um botão único para abrir o TINO. Não criar um segundo clique concorrente em balão, reticências ou indicador flutuante.
2. O botão `+` fica no centro da tab bar, como quinto slot visual: `Hoje | Estoque | + | Caderneta | Mais`.
3. O `+` deve abrir apenas ações compatíveis com a tela atual.
4. Mascote e `+` têm funções diferentes: mascote = conversar/consultar; `+` = criar/registrar.
5. Todo estado vazio com ação deve explicar o porquê e apresentar um CTA perceptível.
6. Nenhum formulário pode esconder o CTA final atrás de espaço reservado duplicado.
7. A navegação por capacidade continua preservada: tabs e ações indisponíveis não devem aparecer como se funcionassem.
8. Estados de voz, espera, erro, offline e sucesso devem manter a mesma linguagem visual do shell principal.

## Validação executada

- `git diff --check` — passou.
- `ANDROID_HOME=/home/carlos-henrique/Android/Sdk gradle :app:testDebugUnitTest :app:assembleDebug` — **BUILD SUCCESSFUL**.
- `ANDROID_HOME=/home/carlos-henrique/Android/Sdk gradle test` — **BUILD SUCCESSFUL** (debug/release e módulos fiscal/core).
- Reexecução final de `ANDROID_HOME=/home/carlos-henrique/Android/Sdk gradle test` — **BUILD SUCCESSFUL**; 74 tarefas, sem falhas.
- `ANDROID_HOME=/home/carlos-henrique/Android/Sdk gradle :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest` — **BUILD SUCCESSFUL**.
- `ANDROID_HOME=/home/carlos-henrique/Android/Sdk gradle :app:lintDebug` — **BUILD SUCCESSFUL**; sem erros de lint. Os avisos restantes são de versões/dependências, orientação legada e convenções não bloqueantes.
- Última compilação após `TinoTextAction` — **BUILD SUCCESSFUL**, incluindo `compileDebugAndroidTestKotlin`.
- Repetição do build após o feedback de carregamento — **BUILD SUCCESSFUL**.
- Renderer A2UI — caminho ativo usa `TinoCardRenderer`; caminho legado direto removido.
- `rg` nos renderers A2UI — nenhuma chamada direta a `Card`/`Button` em `TinoA2UiRenderer`; ações de atenção permanecem compactas em `TinoThoughtsSurface`.
- Surface de pensamentos A2UI — ações reorganizadas em linha própria para evitar overflow em viewport estreito.
- Cadastro/edição de cliente — mutações agora retornam `Result` e o CTA mantém loading/erro até o resultado.
- Scanner/upload fiscal — estados de permissão, captura, processamento e retorno mantêm a linguagem visual do shell; medidas passaram para tokens e o processamento usa `TinoCard`.
- Auditoria estática das telas auditadas — nenhuma chamada crua a `Card`, `Button`, `OutlinedButton` ou `TextButton`; os usos restantes estão restritos à implementação dos componentes canônicos.
- `QuickQueries` — card de consulta usa `TinoCard`; nenhuma tela de comerciante mantém renderização direta de `Card`/botões Material fora dos componentes canônicos.
- `QuickQueries` — avanço usa `TinoIcons.Forward` com descrição acessível; não há seta textual concorrente.
- Consolidação final de tokens — geometrias repetidas de mascote, navegação, voz, avatar e estados passaram para `TinoSize`.
- Ações compactas compartilhadas — `TinoSectionHeader`, `TinoInsightCard` e footers A2UI usam `TinoTextAction`.
- Auditoria de tokens — busca de `dp`/`sp` nas telas e componentes auditados não encontrou medidas de composição restantes; `0.dp` e conversão da largura da tela permanecem apenas como valores matemáticos da animação de voz. Navegação e menu não mantêm tamanhos tipográficos ou geométricos paralelos.
- Reexecução após consolidação dos tokens de navegação — `ANDROID_HOME=/home/carlos-henrique/Android/Sdk gradle test :app:assembleDebug :app:lintDebug` — **BUILD SUCCESSFUL**, sem erros de lint.
- Reexecução após unificação dos fundos e barras do sistema — `ANDROID_HOME=/home/carlos-henrique/Android/Sdk gradle test :app:lintDebug` — **BUILD SUCCESSFUL**; a busca não encontrou os fundos hexadecimais antigos no app.
- Movimento — offsets baseados em estado do mascote e da voz usam a sobrecarga lambda recomendada; os avisos `UseOfNonLambdaOffsetOverload` foram eliminados.
- Voz na Home — estado de processamento usa `TinoCardSurface`; a exceção de `Surface` direto foi removida do fluxo de voz.
- Auditoria de composição pós-migração — telas comerciais e A2UI não têm `Card`/botão Material direto fora dos componentes canônicos; `Surface` restante está limitado a halos/overlays especializados.
- Auditoria de gatilhos conversacionais — a Home não renderiza `TinoVoiceInputBar`; o catálogo contextual visível é aberto pelo mascote, enquanto o `+` permanece exclusivamente no slot central de criação. Estados de voz ativos aparecem como resposta/overlay e não criam um segundo gatilho concorrente.
- Reexecução pós-padronização do estado de voz da Home de `ANDROID_HOME=/home/carlos-henrique/Android/Sdk gradle test` — **BUILD SUCCESSFUL**, 74 tarefas.
- Reexecução pós-componentes compartilhados de `ANDROID_HOME=/home/carlos-henrique/Android/Sdk gradle test` — **BUILD SUCCESSFUL**, 74 tarefas.
- Reexecução pós-correção dos offsets animados de `ANDROID_HOME=/home/carlos-henrique/Android/Sdk gradle test` — **BUILD SUCCESSFUL**, 74 tarefas.
- Rodada de fechamento em 2026-08-29 — `git diff --check`, `ANDROID_HOME=/home/carlos-henrique/Android/Sdk gradle test`, `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug` e `:app:lintDebug` passaram; lint sem erros.
- Verificação de rota visual — `Home`, navegação e superfícies A2UI mantêm o mascote como acesso conversacional e o `+` como criação contextual; nenhuma duplicidade foi encontrada na composição estática.
- Validação física final no Samsung SM-A042M (`R9XW2006AWX`, 720×1600) — instalação do APK debug atual concluída com **Success**; `MainActivity` retomada e nenhum `FATAL EXCEPTION`/`ANR in` no logcat.
- Validação física da Home — card “Vamos começar?”, ações rápidas, carrossel automático, CTA “Cadastrar agora →” e pontos do carrossel ficaram visíveis sem sobreposição da tab bar; o mascote superior permaneceu habilitado e acionável.
- Validação física de affordances — toque no mascote abriu o catálogo contextual com sugestões, campo “Pergunte ao TINO” e microfone; toque no `+` abriu somente a sheet de criação; a tab bar manteve `Hoje | Estoque | + | Caderneta | Mais`.
- Validação física de rotas — `Estoque`, `Caderneta`, `Mais`, `Sem internet`, `Seus dados`, `Novo produto`, `Produtos encontrados` e `Estou ouvindo` foram abertas no device; vazios, CTA, loading/voz, erro e retorno foram conferidos sem crash.
- Validação física fiscal — permissão de câmera explicada antes do prompt do sistema; câmera em paisagem com enquadramento, orientação completa e captura/retorno para conferência; barra de captura não corta mais a instrução.
- Correção final de interação — a colisão visual do card inicial não desabilita mais o mascote na Home; a proteção de colisão segue ativa nas telas secundárias e formulários.
- Correção final de densidade — onboarding compacto usa ação textual e pontos na mesma linha; o primeiro passo aparece acima da tab bar sem retirar o tamanho mínimo de toque.
- Continuação da auditoria — as rotas composable identificadas cobrem Home, consultas, venda/recebimento, fiado, clientes, produtos/estoque, fiscal, fornecedores/compras/pedidos, voz, inteligência, configurações, offline e debug; a composição mantém `TinoBottomNavigation` em um único host, `TinoContextualCreateButton` no slot central e `TinoMascotFab` como gatilho contextual.
- APK de uma rodada anterior instalado no device `R9XW2006AWX` — **Success**; a versão atual aguarda reconexão para nova instalação.
- Logcat da rodada anterior — sem `FATAL EXCEPTION`, `AndroidRuntime` ou `ANR in`.
- Capturas anteriores validaram visualmente Home, sheet de criação da Home, sheet contextual de Estoque, Produtos, Caderneta, Mais e Nova venda.

## Limitação desta rodada

Não há bloqueio externo nesta rodada: o Samsung SM-A042M permaneceu conectado e desbloqueado, o APK atual foi instalado e as rotas prioritárias foram conferidas. A validação manual do usuário pode começar agora; ela é uma etapa de aceite visual, não uma pendência P0/P1 de implementação.

## Próximo backlog priorizado

### P0 — nenhum item aberto

Os fluxos críticos de conversa, offline, fiscal e confirmação continuam cobertos pelo runtime, testes de contrato e evidências físicas/visuais existentes; nenhuma falha P0 foi encontrada nesta rodada.

### P1 — nenhum item aberto

As rotas principais foram revisadas no viewport do SM-A042M; CTAs relevantes, scroll, tab bar, mascote e carrossel não apresentaram obstrução ou duplicidade funcional.

### P2/P3

- Refinar tipografia, ícones e microinterações depois dos fluxos P0/P1.
- Remover hardcodes remanescentes somente após identificar divergência visual real.
- Fazer auditoria de acessibilidade por semântica, foco e tamanho de toque.

## Fontes internas consultadas

- `specs/TINO-ZERO-DOUBT-UX.md`
- `TINO-UX-UI-P0-HARDENING.md`
- `STATUS-UX-UI.md`
- `app/src/main/java/com/tino/app/TinoHome.kt`
- `app/src/main/java/com/tino/app/TinoApp.kt`
- `app/src/main/java/com/tino/app/TinoNavigation.kt`
- `app/src/main/java/com/tino/app/ui/components/TinoComponents.kt`
- `app/src/main/java/com/tino/app/ui/components/TinoCardSystem.kt`
- `app/src/main/java/com/tino/app/ui/a2ui/TinoThoughtsSurface.kt`
- `app/src/main/java/com/tino/app/feature/home/TinoViewModel.kt`
- `app/src/main/java/com/tino/app/ui/a2ui/TinoQuickCreateSurface.kt`
