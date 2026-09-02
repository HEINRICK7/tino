# TINO — auditoria de mascote, ilustrações e estados informativos

Status: diagnóstico fechado; camada estática criada e estados informativos centrais migrados seletivamente.

## CORREÇÃO DE ESCOPO

`TINO_MASCOT_STATES_ASSETS_MASTER.png` não substitui o mascote funcional existente.

O sistema abaixo fica congelado e fora do escopo desta migração:

- `app/src/main/java/com/tino/app/ui/components/TinoMascot.kt`;
- `TinoMascotFab`, `TinoMascotPresence` e `TinoMascotInlineLabel`;
- `TinoPresenceMode`, `TinoPresenceResolver` e o renderer agêntico atual;
- escuta, voz, olhos, movimentos, launcher de sugestões e comportamento de presença.

Os novos assets são ilustrações estáticas de comunicação. A nova API deve se chamar `TinoIllustration`/`TinoIllustrationState`; ela não pode reutilizar `TinoMascotState`.

## MASCOT_CALL_SITES — PROTEGIDOS

| Local | Tela/componente | Intenção observada | Decisão |
|---|---|---|---|
| `app/src/main/java/com/tino/app/TinoHome.kt:522` | `TinoVoiceInputBar` | Entrada de voz, integrada à presença agêntica. | Não alterar. |
| `app/src/main/java/com/tino/app/ui/components/TinoComponents.kt:174` | `TinoMascotPresence` | Feedback vivo de voz/operação, usado também no resultado NFC-e. | Não alterar; NFC-e permanece fora desta task. |
| `app/src/main/java/com/tino/app/ui/components/TinoComponents.kt:224` | `TinoMascotFab` | Launcher global de voz e sugestões. | Não alterar. |
| `app/src/main/java/com/tino/app/ui/components/TinoComponents.kt:369` | `TinoGettingStartedCarousel` | Presença viva em dica de primeira entrada. | Não substituir aqui; avaliar depois se o conteúdo precisa de `TinoIllustration`, sem remover a presença viva. |
| `app/src/main/java/com/tino/app/ui/components/TinoComponents.kt:482` | `TinoContextualEmptyState` | Presença viva em empty state contextual. | Não alterar automaticamente; a nova ilustração pode ser adicionada em um componente informativo separado quando houver função clara. |
| `app/src/main/java/com/tino/app/TinoNavigation.kt:1041` | `MainShell` → `TinoMascotFab` | Acesso conversacional primário. | Não alterar. |

Não existe migração `OLD_STATE → NEW_STATE` para o mascote funcional. `Idle`, `Observing`, `LookingLeft`, `LookingRight`, `Thinking`, `Attention` e `Guiding` permanecem como parte do sistema vivo até uma task específica autorizar sua evolução.

## NEW_STATIC_ILLUSTRATION_CATALOG

A nova camada terá um enum independente:

`LOADING`, `NOT_FOUND`, `SYNCING`, `OFFLINE`, `SUCCESS`, `WARNING`, `ERROR`, `SEARCHING`, `EXPLAINING`, `LEARNING`, `SLEEPING`.

Esses nomes descrevem ilustrações informativas e não comandam `TinoMascot`. Não há `LISTENING`, `THINKING` ou `DEFAULT` neste catálogo: esses conceitos pertencem ao sistema vivo ou exigiriam uma intenção de conteúdo explícita, nunca uma ponte implícita.

Implementação:

- `app/src/main/java/com/tino/app/ui/illustration/TinoIllustration.kt` concentra o enum, resolver e composable estático;
- `app/src/main/java/com/tino/app/ui/theme/TinoIllustrationTokens.kt` concentra tamanhos de uso;
- `app/src/main/java/com/tino/app/ui/illustration/BusinessActivityIllustration.kt` possui resolver independente para identidade do negócio.

## STATIC_ILLUSTRATION_CALL_SITES_TO_CREATE

O componente estático deverá ser usado apenas em conteúdo que tenha uma função visível e texto associado:

- onboarding: apresentação, explicação dos campos, atividades, modos e resumo;
- empty/search: produto, cliente, histórico ou filtro realmente sem resultado — `NOT_FOUND`/`SEARCHING`;
- operações remotas: carregamento — `LOADING`; sincronização — `SYNCING`; sucesso relevante — `SUCCESS`; erro real — `ERROR`; offline — `OFFLINE`;
- aprendizagem persistida: `LEARNING` somente quando algo foi confirmado e salvo;
- avisos de confirmação/ambiguidade/estoque baixo: `WARNING`.

Não adicionar a ilustração a cada card, cabeçalho, botão, item de navegação ou empty positivo. Máximo de uma ilustração estática principal por viewport e sempre com texto; o mascote vivo pode aparecer em seu próprio contexto sem ser substituído.

## EMPTY_STATES

`TinoEmptyState` é usado em:

- Estoque/produtos: catálogo vazio ou filtro sem resultado — candidato a `NOT_FOUND` com ação contextual;
- Clientes/Caderneta: cliente ausente, lista vazia ou filtro sem resultado — candidato a `NOT_FOUND` com ação contextual;
- seleção inválida, produto/cliente nulo e compra incompleta — manter ícone/texto até confirmar que representam busca/entidade ausente;
- Fiscal, pedidos, fornecedores, histórico e entregas — separar ausência acionável de empty positivo;
- Insights/avisos sem itens e sincronização sem pendências — empty positivo, sem ilustração automática;
- A2UI e diagnósticos — o estado informativo deve ser explícito no contrato, sem inferência visual genérica.

`TinoEmptyState` agora aceita `illustrationState` opcional. Sem esse argumento, mantém o ícone anterior; com ele, renderiza uma única ilustração estática com descrição acessível e texto associado.

Call sites já migrados por intenção:

- produtos e venda: `LEARNING` para catálogo ainda não iniciado, `NOT_FOUND` para filtro sem resultado;
- clientes e Caderneta: `LEARNING` para primeira configuração, `NOT_FOUND` para busca/entidade ausente;
- seleção incompleta e orientação operacional: `WARNING`/`EXPLAINING`;
- importação fiscal: `SEARCHING`, `NOT_FOUND` e `ERROR` conforme o resultado;
- pedidos, fornecedores e entregas: `LEARNING`, `EXPLAINING` ou `SUCCESS` conforme a finalidade do empty;
- sincronização, avisos e resumo diário: `SUCCESS` para estado saudável, `EXPLAINING` quando há orientação necessária.

## LOADING_AND_SYNC_STATES

- Onboarding e bootstrap inicial: `LOADING` estático durante operação remota, mantendo os textos de progresso.
- Estoque/catalog sync: `SYNCING`, depois `SUCCESS` ou `ERROR` conforme o resultado.
- Busca efetiva de produto/cliente/histórico: `SEARCHING`; não usar como loading genérico.
- Voz: não alterar o mascote funcional. A ilustração estática só entra em uma superfície textual separada se houver necessidade de explicação.
- `TinoLoadingState` em bootstrap, pedidos e entrega recebe `LOADING`; upload de nota usa `SEARCHING`. O indicador existente foi preservado.
- NFC-e: excluído desta task.

## ERROR_SUCCESS_OFFLINE_WARNING_LEARNING

- `ERROR`: somente rede, servidor, sync, persistência ou operação não concluída; com retry contextual.
- Validação de formulário: manter erro inline, sem `ERROR` ilustrativo automático.
- `WARNING`: matching ambíguo, confirmação necessária, estoque baixo e dados incompletos.
- `SUCCESS`: cadastro, produto, compra, estoque ou entendimento concluídos; não para clique trivial.
- `OFFLINE`: sem conexão, sem bloquear capacidade local.
- `LEARNING`: somente depois de uma confirmação persistida de aprendizado do usuário.

## BUSINESS_ACTIVITY_SCREENS

Os fluxos existentes ainda não consomem o catálogo de atividades. Os pontos existentes são:

- `FirstAccessScreen` (`TinoApp.kt:914`): um `BusinessVertical` singular, “Personalizar recursos” e dados de UI misturados ao onboarding;
- `BusinessProfileSettingsScreen` (`TinoApp.kt:3422`): repete “Tipo de negócio” singular e “Recursos ativos”;
- `HomeScreen`: não mostra atividade/identidade do estabelecimento;
- Estoque, Clientes, Caderneta e A2UI: não usam Business Activity Assets.

`BusinessActivity` e `BusinessActivityIllustration` já existem em camada própria, mas os fluxos de onboarding e Meu negócio ainda usam o `BusinessVertical` singular. A integração visual nesses fluxos fica como próxima etapa; não deve alterar o contrato atual sem a auditoria de persistência/backend.

## ASSETS_AND_RUNTIME

- Masters encontrados: `assets/branding/masters/TINO_BUSINESS_ACTIVITY_ASSETS_MASTER.png` e `assets/branding/masters/TINO_MASCOT_STATES_ASSETS_MASTER.png`.
- Masters são referência visual e não devem ser carregados pelo runtime.
- Assets individuais estáticos já extraídos para `app/src/main/res/drawable/` não têm relação com o renderer vivo; os de estados são consumidos por `TinoIllustration` e os de negócio por `BusinessActivityIllustration`.
- Os assets usam WebP com transparência; o master de atividades tem 9 cenas visíveis.
- O contrato pede `tino_business_other.webp`, mas não há cena OTHER inequívoca na prancha; o arquivo atual é um placeholder transparente e não deve ser apresentado como ilustração final.

## LEGACY_ENUM_DEPENDENCIES

As referências do enum vivo estão confinadas ao componente atual, aos wrappers de presença e aos testes correspondentes. Elas não devem ser renomeadas nem substituídas como parte do catálogo estático.

Critério executado: `rg` prova que nenhum import ou chamada de `TinoIllustration` alcança `TinoMascotState`, `TinoPresenceMode` ou `TinoMascotFab`. O renderer estático tem resolver próprio, tokens próprios e acessibilidade própria.

## GATE

| Critério | Status | Evidência |
|---|---|---|
| `LIVE_MASCOT_FROZEN` | PASS | `TinoMascot.kt` não recebeu chamadas nem imports da camada estática. |
| `TINO_PRESENCE_UNCHANGED` | PASS | `TinoPresenceMode`/`TinoMascotState` permanecem no sistema vivo. |
| `VOICE_FLOW_UNCHANGED` | PASS | Nenhum fluxo de voz foi redirecionado para ilustrações. |
| `TINO_FAB_UNCHANGED` | PASS | `TinoMascotFab` não foi substituído. |
| `ILLUSTRATION_CATALOG_CREATED` | PASS | Enum fechado sem estados de presença. |
| `ILLUSTRATION_COMPONENT_CREATED` | PASS | `TinoIllustration` com resolver e `contentDescription`. |
| `BUSINESS_ASSET_RESOLVER` | PASS | `BusinessActivityIllustrationResolver` independente. |
| `STATIC_UI_STATES_MIGRATED` | PASS | Empty/loading/offline e call sites informativos centrais usam intenção explícita. |
| `NO_CROSS_DEPENDENCY` | PASS | Grep de isolamento sem referências cruzadas novas. |

## OPEN_RISKS

1. O backend aceita `vertical` singular; atividades múltiplas e modos podem exigir contrato posterior. A primeira implementação pode manter a representação local sem misturar NFC-e.
2. A prancha não apresenta um asset `OTHER` inequívoco; o placeholder deve ser substituído pela fonte oficial antes de expor a opção visualmente.
3. A prancha do mascote contém acessórios e elementos decorativos. A seleção final precisa ser validada pela marca; nenhum crop pode alterar o mascote funcional.
4. Não há medição anterior do APK registrada no workspace; medir antes/depois quando os assets estáticos forem realmente usados.
