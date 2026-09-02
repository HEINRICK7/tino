# TINO — Arquitetura Real do Sistema

**Data:** 23/08/2026  
**Escopo:** Android local-first, agents, voz, A2UI, comércio, fiscal e sync  
**Regra deste documento:** descreve o código existente. O ADK Kotlin oficial
está conectado somente como planner; ele não recebe tools do TINO nem acessa
Room. O Attention Engine local já está integrado ao runtime e à UX; RAG externo
e cloud produtivo continuam fora do produto integrado.

## 1. Visão geral

O TINO é um aplicativo Android local-first. A interface recebe texto, voz ou
toque; a camada agentic interpreta a intenção; as capabilities consultam ou
preparam operações no domínio; a UI mostra uma resposta ou preview; e o Room
continua sendo a fonte local da verdade.

```mermaid
flowchart LR
    U["Comerciante"] --> INPUT["Toque · texto · voz · câmera"]

    subgraph ANDROID["TINO Android"]
        UI["Compose UI<br/>MainActivity host · TinoApp · telas · FAB de voz"]
        AGENT["Agentic Shell<br/>contexto · multiturno · sessão"]
        INTEL["Intelligence Runtime<br/>facts · analytics · memory · approved knowledge · attention"]
        CAP["Capabilities / Use Cases<br/>consultas · previews · mutações"]
        DOMAIN["Domínio comercial<br/>regras · dinheiro · estoque · fiado"]
        A2UI["A2UI TINO<br/>mensagem tipada · mapper · renderer"]
        ROOM[("Room / SQLite<br/>fonte local da verdade")]
        OUTBOX["Domain Events + Outbox"]
        SYNC["SyncCoordinator<br/>WorkManager · retry · breaker"]
    end

    subgraph ADAPTERS["Adapters locais"]
        SPEECH["Android SpeechRecognizer"]
        GEMMA["Gemma via MediaPipe<br/>quando disponível"]
        MLKIT["ML Kit / CameraX<br/>intake fiscal"]
        ADK["Google ADK Kotlin<br/>planner oficial"]
    end

    subgraph FUTURE["Fora do runtime atual"]
        CLOUD["Cloud / backend produtivo"]
        RAG["RAG externo<br/>planejado / não integrado"]
    end

    INPUT --> UI
    UI --> SPEECH
    SPEECH --> AGENT
    UI --> AGENT
    AGENT --> GEMMA
    GEMMA --> AGENT
    AGENT --> CAP
    AGENT --> INTEL
    INTEL --> CAP
    INTEL --> ROOM
    CAP --> DOMAIN
    DOMAIN --> ROOM
    DOMAIN --> OUTBOX
    CAP --> A2UI
    A2UI --> UI
    OUTBOX --> SYNC
    SYNC -. "se URL/configuração existir" .-> CLOUD
    MLKIT --> CAP
    ADK -->|"propõe plano; não executa"| INTEL
    RAG -. "não conectado" .-> CAP

    classDef current fill:#dff7e6,stroke:#159447,color:#123b22;
    classDef adapter fill:#e8f0ff,stroke:#5c7cba,color:#1d3157;
    classDef future fill:#f2f2f2,stroke:#999,color:#555,stroke-dasharray: 5 5;
    class UI,AGENT,INTEL,CAP,DOMAIN,A2UI,ROOM,OUTBOX,SYNC current;
    class SPEECH,GEMMA,MLKIT,ADK adapter;
    class CLOUD,RAG future;
```

## 2. Fronteiras do projeto

```mermaid
flowchart TB
    PRESENTATION["presentation / feature / Compose"]
    ADAPTER["interface adapters<br/>A2UI · speech · fiscal · sync"]
    APPLICATION["application / agent boundary<br/>coordinators · capability registry"]
    DOMAIN["domain<br/>commerce rules · entities · ports"]
    INFRA["infrastructure<br/>Room · CameraX · MediaPipe · WorkManager"]
    EXTERNAL["external systems<br/>cloud · model runtime · Android APIs"]

    PRESENTATION --> APPLICATION
    PRESENTATION --> ADAPTER
    ADAPTER --> APPLICATION
    APPLICATION --> DOMAIN
    APPLICATION --> ADAPTER
    INFRA --> DOMAIN
    INFRA --> ADAPTER
    EXTERNAL --> INFRA

    RULE1["Regra: UI não acessa DAO"]
    RULE2["Regra: Agent não acessa DAO"]
    RULE3["Regra: A2UI não acessa repository"]
    RULE4["Regra: domínio não depende de Android"]

    PRESENTATION -. "fitness test" .-> RULE1
    APPLICATION -. "fitness test" .-> RULE2
    ADAPTER -. "renderer tipado e allowlist" .-> RULE3
    DOMAIN -. "ports / adapters" .-> RULE4
```

A composição foi extraída do host para `TinoApp.kt`; a navegação foi movida para
`TinoNavigation.kt`; e a Home foi movida para `TinoHome.kt`. Isso preserva a
direção das dependências e permite que `MainActivity` permaneça responsável
apenas pelo startup, splash, orientação, barras do sistema e instalação do
conteúdo Compose. As demais telas ainda estão agrupadas no módulo de
apresentação; a extração por feature continua sendo uma dívida de organização,
não uma justificativa para reescrever o aplicativo.

## 2.1 Perfil de negócio e módulos verticais

O contrato inicial de plataforma já está no domínio em
`domain/profile/BusinessProfile.kt`. Ele modela um único APK com `CORE` sempre
habilitado e módulos ativados por perfil. O `TinoModuleRegistry` possui o pack
Retail inicial e compartilha `TinoCapabilityId` com o Agentic Shell.

```mermaid
flowchart TB
    APK["Um APK TINO"] --> PROFILE["BusinessProfile"]
    PROFILE --> CORE["CORE<br/>clientes · financeiro · estoque · fiscal"]
    PROFILE --> MODULES["TinoModuleRegistry"]
    MODULES --> RETAIL["RETAIL pack<br/>implementado inicialmente"]
    MODULES -. "packs futuros" .-> BAKERY["BAKERY"]
    MODULES -. "packs futuros" .-> RESTAURANT["RESTAURANT"]
    RETAIL --> CAP["TinoCapabilityRegistry"]
    CAP --> INTEL["Intelligence Runtime único"]
    INTEL --> A2UI["A2UI adaptado ao perfil"]
```

Onboarding, persistência do perfil e packs Bakery/Restaurant ainda não estão
integrados às telas. A fundação impede a criação de aplicativos separados e
deixa a expansão como composição de módulos.

### 2.2 Contrato semântico de A2UI

Capabilities e agentes devolvem resultados semânticos; eles não montam
Compose, escolhem telas ou produzem componentes A2UI arbitrários. A fronteira
`interfaceadapter/a2ui` faz o mapeamento explícito para uma primitive
permitida pelo catálogo:

| Resultado semântico | Primitive A2UI |
| --- | --- |
| Lista/coleção de produtos, clientes ou recebíveis | `ReadListCard` |
| Entidade ambígua que precisa de escolha | `EntityChoice` |
| Resumo de cliente ou financeiro | card semântico correspondente |
| Preview, confirmação ou conclusão de mutação | `ActionConfirmation` |
| Erro recuperável ou timeout | `ErrorStatusCard` (`error_recovery`) com retry |

No bounded context de estoque, perguntas de catálogo e de compra também são
distintas: `LIST_PRODUCTS` retorna todos os produtos cadastrados, enquanto
`REPLENISHMENT_QUERY` aplica `InventoryPolicy(minimumStock, reorderPoint)` e
retorna somente candidatos à reposição. Sem política persistida por produto, o
default é deliberadamente conservador e considera apenas estoque zerado.

O renderer Compose aceita somente componentes tipados e allowlisted. O
`A2uiSemanticMapper` centraliza resultados de recuperação, e o codec preserva
essa primitive no round-trip JSON. Um novo resultado agentic só pode entrar na
interface depois de possuir mapper e teste de contrato; respostas de erro não
devem voltar como `Unsupported(type = "error_recovery")` nem ser desenhadas
por um `TinoCard` ad hoc na Home.

#### Princípio visual: A2UI glanceable

**A2UI deve ser glanceable: entender primeiro, ler depois.** O comerciante
precisa reconhecer o resultado em 1–2 segundos, sem decifrar um parágrafo.
Resultados de coleção carregam semântica visual, não apenas texto solto:

`icon · title · context · primary value · supporting text · status · action`

O contrato continua separado em camadas:

`CapabilityResult → dados visuais semânticos → primitive A2UI permitida → renderer Compose`

O domínio informa o que o resultado significa; o renderer decide espaçamento,
tipografia, ícones, hierarquia, estados de cor e comportamento responsivo. A
LLM não produz Compose, layout ou componentes arbitrários. O valor principal
fica maior e mais legível; contexto e apoio são curtos e opcionais; cards
operacionais não recebem explicações longas.

A família inicial de primitives é deliberadamente pequena: `MetricCard`,
`EntityCard`, `AlertCard`, `SummaryCard`, `List`, `Choice`,
`ConfirmationCard`, `StatusCard`, `MiniChart` e `Action/Button`. A migração
atual começa por `ReadListCard`, cujos itens já preservam esses campos no
codec; novas primitives entram somente quando houver resultado semântico,
mapper, renderer e teste de contrato.

### 2.3 Catálogo customizado v1

O vocabulário de domínio é registrado em `tino.catalog.v1` por
`TinoCustomComponentCatalog`. Ele descreve semântica, props, estados e ações;
não duplica `Text`, `Icon`, `Row`, `Column`, `List`, `Button` ou containers do
Basic Catalog. A lista inicial é:

`MetricCard`, `ProductCard`, `CustomerCard`, `DebtCard`, `InventoryAlertCard`,
`SaleCard`, `SummaryCard`, `QuickQueryCard`, `ConfirmationCard`, `StatusCard` e
`MiniChart`.

O registry central valida props e allowlist antes do renderer. Ações de detalhe
e consulta são agentic e passam pela fronteira de ações; confirmação e
cancelamento continuam explícitos. A superfície atual ainda renderiza alguns
tipos legados durante a migração, mas o catálogo novo já é a fonte versionada
para as próximas composições.

### 2.4 TinoUiPlanner — composição fechada pelo catálogo

O catálogo é o vocabulário; o `TinoUiPlanner` escreve a frase visual. Ele não
cria componentes em runtime e não conhece Compose, Room ou DAO:

```mermaid
flowchart TD
    Q["Intenção do usuário"] --> C["Capability"]
    C --> D["Dados reais / domínio"]
    D --> R["Resultado semântico tipado"]
    R --> P["TinoUiPlanner"]
    P --> K["Basic Catalog + TINO Custom Catalog"]
    K --> T["TinoA2UiTree"]
    T --> S["A2uiSurfaceMessage"]
    S --> COMPOSE["Compose renderer"]
    P -. "padrão não representável" .-> CANDIDATE["CatalogCandidate"]
```

`TinoUiPlannerContext` fornece intenção, largura, escala de fonte e dados
opcionais de série. A composição considera essas restrições sem alterar os
fatos: em tela compacta, por exemplo, o mini-gráfico é omitido se não couber
ou se a série não veio da capability. Cada saída é validada contra
`TinoComponentCatalog.core`; um tipo inventado pelo modelo não pode atravessar
essa fronteira.

Composições v1 cobertas:

- `ReplenishmentResult` → título, `InventoryAlertCard` por produto e ação para estoque;
- `ReceivablesListResult` → `SummaryCard` + `DebtCard` por cliente;
- `FinancialSummaryResult` → `MetricCard` e `MiniChart` somente com série real e espaço suficiente;
- `ProductListResult` e `CustomerListResult` → coleções de cards sem picker indevido;
- padrão não suportado → mensagem segura + `CatalogCandidate` para análise posterior.

`CatalogCandidate` é observação de design, não instrução executável. Telemetria
pode agrupar candidatos recorrentes; somente uma mudança versionada do catálogo
e um novo renderer podem transformá-los em componente disponível.

## 3. Fluxo dos agents

Os agents do TINO não são agentes autônomos com acesso livre ao sistema. São
componentes especializados que trabalham dentro de uma fronteira controlada.

```mermaid
sequenceDiagram
    actor Comerciante
    participant Input as Voz / texto
    participant Router as Fast Router / Command Router
    participant Gemma as Gemma Adapter
    participant Session as Session + Context Memory
    participant Resolve as Entity Resolver
    participant Boundary as Agent Boundary
    participant UseCase as Capability / Use Case
    participant A2UI as A2UI Mapper
    participant Room as Room / Domínio

    Comerciante->>Input: "Maria pagou cinquenta no Pix"
    Input->>Router: texto confirmado
    Router->>Router: tenta caminhos determinísticos
    Router-->>Gemma: usa Gemma se necessário
    Gemma-->>Router: intenção JSON validada
    Router->>Session: registra turno e referências
    Session->>Resolve: cliente, valor, forma de pagamento
    Resolve-->>Boundary: entidades resolvidas ou ambiguidade
    Boundary->>UseCase: prepara capability
    UseCase-->>A2UI: preview estruturado
    A2UI-->>Comerciante: Maria · R$ 50,00 · PIX
    Comerciante->>A2UI: Confirmar
    A2UI->>UseCase: confirmação explícita
    UseCase->>Room: transação local idempotente
    Room-->>Comerciante: sucesso + Activity / Undo quando suportado
```

### Responsabilidade de cada agent/camada

| Camada | Responsabilidade | Não faz |
|---|---|---|
| `LiveTranscriber` | Captura e entrega transcrição parcial/final | Não decide o que mutar |
| `FastIntentRouter` / `CommandIntentRouter` | Reconhece frases conhecidas rapidamente | Não consulta saldo ou estoque |
| `MediaPipeGemmaAgentIntentAdapter` | Classifica intenção em JSON estrito | Não acessa Room, não calcula dinheiro |
| `TinoAgentSession` | Estado de listening, understanding, preview, cancel e sucesso | Não é a fonte do domínio |
| `CommerceContextMemory` | Referências de tela, conversa e entidade recente | Não escolhe entidade ambígua silenciosamente |
| `EntityResolver` | Resolve cliente/produto e pede escolha quando necessário | Não grava mutação |
| `AgenticTextQueryCoordinator` | Coordena interpretação, contexto e resposta | Não substitui regras comerciais |
| `Capability Registry / Boundary` | Allowlist de capacidades disponíveis | Não permite tool arbitrária |
| `Use Case / CommerceRepository` | Consulta e mutação local com regras de negócio | Não renderiza Compose |
| `AgentActivityLedger` | Registra operação, estado e Undo | Não é o ledger financeiro |

## 4. Consulta e mutação

Consultas podem retornar dados diretamente. Mutações passam por preview e
confirmação quando a operação exige confirmação.

```mermaid
flowchart LR
    ASK["Pedido do comerciante"] --> CLASSIFY["Classificar intenção"]
    CLASSIFY --> ENTITY["Resolver entidades"]
    ENTITY --> DECIDE{"Consulta ou mutação?"}

    DECIDE -->|consulta| READ["Read capability"]
    READ --> FACTS["Room / projeção local"]
    FACTS --> CARD["A2UI informativa"]

    DECIDE -->|mutação| PREP["Preparar operação"]
    PREP --> PREVIEW["A2UI preview"]
    PREVIEW --> CONFIRM{"Confirmou?"}
    CONFIRM -->|não| CANCEL["Cancelar sem mutação"]
    CONFIRM -->|sim| TX["Transação Room"]
    TX --> EVENT["Domain Event + Outbox"]
    EVENT --> ACTIVITY["Activity / Undo"]
```

Exemplos atuais incluem saldo, timeline, produtos, estoque, preço, recebíveis,
entrada, alteração de preço, venda fiada e pagamento de fiado. Nem todas as
operações antigas foram promovidas ao catálogo agentic canônico.

## 5. A2UI do TINO

O A2UI atual é um contrato declarativo próprio do TINO (`tino.a2ui`, versão 1).
Ele não é o Google ADK, não é o Room e não é uma segunda camada de domínio.

```mermaid
flowchart LR
    RESPONSE["AgentA2uiResponse<br/>resultado ou preview"]
    MAPPER["Mapper semântico<br/>CommerceAction · Customer · DbFirstRead"]
    ENVELOPE["A2uiMessage<br/>schema + version + messageId"]
    POLICY["Semantic Registry / Presentation Policy<br/>allowlist · tamanho · superfície"]
    RENDERER["TinoA2UiRenderer<br/>Compose"]
    USER["Usuário<br/>confirma · cancela · escolhe · desfaz"]
    CALLBACK["callback tipado"]
    AGENT["Agent boundary / use case"]

    RESPONSE --> MAPPER --> ENVELOPE --> POLICY --> RENDERER --> USER
    USER --> CALLBACK --> AGENT
    ENVELOPE -. "tipo desconhecido" .-> FALLBACK["Fallback seguro<br/>sem executar instrução"]
```

Componentes principais:

- `FinancialSummaryCard`, `CustomerBalanceCard` e `CustomerTimelineCard` para
  consultas;
- `ReadListCard` e `EntityChoice` para listas e desambiguação;
- `ActionConfirmation` e previews de pagamento, fiado, preço e entrada;
- `InsightCard` para respostas do Intelligence Runtime com status, evidências e limitações;
- ações de cancelar, confirmar e Undo quando a capability oferece compensação;
- allowlist e fallback para que mensagem externa não vire código executável.

## 5.1 Intelligence Runtime — fatia executável atual

O runtime atual já possui uma porta única (`IntelligenceRuntimePort`) e uma
implementação determinística local. Ela consulta fatos por uma porta de
infraestrutura que lê Room/projeções, calcula analytics sem LLM e devolve
status explícito quando há ambiguidade, falta de dados ou conhecimento não
disponível.

```mermaid
flowchart LR
    QUESTION["Pergunta livre"] --> RUNTIME["IntelligenceRuntimePort"]
    RUNTIME --> ADAPTER["GoogleAdkRuntimeAdapter"]
    ADAPTER -->|ADK disponível| ADK["GoogleAdkOrchestratorPort"]
    ADAPTER -->|fallback atual| LOCAL["DeterministicIntelligenceRuntime"]
    LOCAL --> PLANNER["PlannerPort<br/>composition root"]
    PLANNER --> DET["DeterministicQueryPlanner"]
    PLANNER --> ADKPLANNER["AdkQueryPlanner<br/>official ADK adapter"]
    DET --> PLAN["ExecutionPlan"]
    ADKPLANNER --> PLAN
    PLAN --> VALIDATOR["PlanValidator<br/>allowlist · limites · sem mutação"]
    VALIDATOR --> EXECUTOR["PlanExecutor<br/>tools + analytics"]
    EXECUTOR --> HANDLERS["HandlerRegistry<br/>goal → handler"]
    HANDLERS --> FINANCE["FinancialPlanHandler"]
    HANDLERS --> CUSTOMER["CustomerPlanHandler"]
    HANDLERS --> INVENTORY["InventoryPlanHandler"]
    HANDLERS --> HELP["KnowledgePlanHandler"]
    EXECUTOR --> GROUNDING["GroundingComposer<br/>evidências + limitações"]
    FINANCE --> FACTS["RoomCommerceIntelligenceFacts"]
    CUSTOMER --> FACTS
    INVENTORY --> FACTS
    HELP --> KNOWLEDGE["KnowledgeQueryPort"]
    FINANCE --> ANALYTICS["DeterministicBusinessAnalytics"]
    CUSTOMER --> ANALYTICS
    INVENTORY --> ANALYTICS
    LOCAL --> CLARIFY["ClarificationPolicy<br/>ambiguidade explícita"]
    LOCAL --> MEMORY["MemoryPort<br/>process-local nesta fatia"]
    LOCAL --> TELEMETRY["IntelligenceTelemetryPort<br/>rota · plano · latência · erro"]
    FACTS --> ROOM["Room / projections"]
    TELEMETRY --> TELEMETRY_ROOM[("Room<br/>intelligence_telemetry")]
    EVAL["PlannerAbEvaluator<br/>corpus Gate 3.2"] -. "fora do caminho de produção" .-> DET
    EVAL -. "mesmo corpus" .-> ADKPLANNER
    RUNTIME --> RESPONSE["IntelligenceResponse<br/>status · plan · evidências · limitações"]
    GROUNDING --> RESPONSE
    RESPONSE --> A2UI["InsightCard A2UI"]
```

Regras preservadas nesta fronteira:

- fatos de saldo, venda, estoque e recebível vêm de Room/projeções, nunca de memória ou RAG;
- analytics como variação, atraso médio, velocidade e cobertura são determinísticos;
- o registry é allowlist de ferramentas com schema, versão, autorização e política de confirmação;
- este caminho é somente consulta nesta fatia; mutações continuam no pipeline preview → confirmação → capability;
- se o modelo/ADK estiver indisponível, o planner determinístico é usado com
  `plannerUsed=deterministic-fallback`; nenhuma resposta é inventada;
- o ADK oficial propõe somente JSON convertido em `ExecutionPlan`; não recebe
  DAO, repository, handler ou tool executável do TINO.
- telemetria registra `requestId`, `sessionId`, planner selecionado/usado,
  fallback, validação, grounding, ordem de steps, latência e estágio de erro em
  Room, mas não é fonte de fatos comerciais e falha de forma isolada se a
  persistência estiver indisponível;
- `PlannerAbEvaluator` mede os dois planners com o mesmo corpus sem executar
  ferramentas; grounding factual continua sendo responsabilidade do executor.

## 6. Voz e modelos open source

```mermaid
flowchart TB
    MIC["Microfone / Android permission"] --> ASR["Android SpeechRecognizer<br/>ou transcriber disponível"]
    ASR --> PARTIAL["partial / revised"]
    ASR --> COMMITTED["committed transcript"]
    COMMITTED --> ROUTER["Routers determinísticos"]
    ROUTER -->|sem match| LOCAL["Gemma via MediaPipe<br/>intent / extração estruturada"]
    ROUTER --> SCHEMA["AgentIntentSchema"]
    LOCAL --> SCHEMA
    SCHEMA --> CONTEXT["Context + entity resolution"]
    CONTEXT --> CAPABILITY["Capability autorizada"]

    OCR["ML Kit text recognition"] --> FISCAL["Fiscal adapter"]
    CAMERA["CameraX + frame analyzer"] --> FISCAL
    FISCAL --> REVIEW["Review fiscal"]
```

O papel dos componentes open source é delimitado:

- **Gemma/MediaPipe:** ajuda a interpretar linguagem e extrair campos; não é
  fonte de verdade e não executa operação comercial;
- **Android SpeechRecognizer:** transforma fala em texto quando o device possui
  reconhecimento on-device disponível;
- **ML Kit:** reconhece texto/documento fiscal; não decide matching de produto;
- **CameraX:** captura imagem e respeita lifecycle da tela;
- **Room, Compose, Hilt e WorkManager:** infraestrutura Android do produto;
- **Koog:** somente contratos/spike em `koog-spike/`; não é runtime oficial;
- **RAG:** não existe operacionalmente. Saldo, preço e estoque usam Room, não
  recuperação semântica.

## 7. Fluxo fiscal

```mermaid
flowchart LR
    SOURCE["Câmera ou XML"] --> INTAKE["Document intake"]
    INTAKE --> QUALITY["gates de qualidade<br/>quadro · luz · nitidez · estabilidade"]
    QUALITY --> INTERPRET["interpretação local"]
    INTERPRET --> MATCH["matching de produto/fornecedor"]
    MATCH --> PREVIEW["Produtos encontrados<br/>quantidade · custo · confiança"]
    PREVIEW --> REVIEW{"Revisão humana"}
    REVIEW -->|ambíguo / embalagem| NEEDS["NeedsReview<br/>sem commit automático"]
    REVIEW -->|confirmado| COMMIT["FiscalImportCommitService<br/>idempotente"]
    COMMIT --> STOCK["compra + movimentos de estoque + eventos"]
    REVIEW -->|cancelado| NONE["Nenhuma mutação"]
```

O contrato fiscal já impede que a superfície A2UI ofereça `COMMIT` direto para
um preview ambíguo. Câmera e XML ainda precisam convergir completamente para o
review canônico da `MainActivity` antes de o fluxo fiscal ser fim a fim.

## 8. Offline, eventos e sincronização

```mermaid
flowchart LR
    MUTATION["Mutação confirmada"] --> DBTX["Transação Room"]
    DBTX --> FACT["Dados locais"]
    DBTX --> EVENT["DomainEvent<br/>PENDING"]
    EVENT --> WORK["WorkManager / SyncScheduler"]
    WORK --> COORD["SyncCoordinator"]
    COORD --> BREAKER["timeout · retry · backoff · breaker"]
    BREAKER --> GATEWAY{"Gateway configurado?"}
    GATEWAY -->|não| LOCAL["UnavailableSyncGateway<br/>local continua funcionando"]
    GATEWAY -->|sim| REST["RestSyncGateway HTTPS"]
    REST --> CLOUD[("Cloud sync<br/>fora do ambiente atual")]
    CLOUD --> ACK["ack / rejeição / cursor"]
    ACK --> EVENT
```

O usuário trabalha no Room mesmo sem cloud. O outbox registra o que precisa
ser sincronizado; isso não deve ser exposto como vocabulário de produto.

## 9. ADK, Koog e RAG: fronteira atual

```mermaid
flowchart TB
    CURRENT["Runtime atual do TINO"] --> PORTS["Ports e contratos próprios"]
    PORTS --> ANDROID_ADAPTER["Adapters Android<br/>Gemma · Room · speech · sync"]

    ADK["Google ADK Kotlin 0.6.0<br/>LlmAgent + InMemoryRunner"] --> PROPOSAL["AdkPlanProposalPort<br/>JSON → ExecutionPlan"]
    PROPOSAL -. "sem execução direta" .-> PORTS
    KOOG["Koog<br/>spike de contratos"] -. "não conectado ao runtime" .-> PORTS
    RAG["RAG<br/>possível conhecimento não transacional"] -. "não substituir Room" .-> PORTS

    classDef current fill:#dff7e6,stroke:#159447,color:#123b22;
    classDef future fill:#f2f2f2,stroke:#999,color:#555,stroke-dasharray: 5 5;
    class CURRENT,PORTS,ANDROID_ADAPTER current;
    class ADK,PROPOSAL adapter;
    class KOOG,RAG future;
```

A decisão atual é manter o domínio independente de framework. O build contém
`google-adk-kotlin-core` e usa `LlmAgent`/`InMemoryRunner` em um adapter Android
que reaproveita o Gemma local como backend de texto. O ADK recebe pergunta,
contexto e catálogo descritivo; retorna uma proposta de plano. O domínio valida
allowlist, limites e mutações antes de qualquer handler. O `GoogleAdkRuntimeAdapter`
continua sendo a fronteira externa com fallback seguro. Firebase AI, LiteRT-LM,
RAG e Koog não estão conectados ao runtime produtivo.

## 10. Estado de implementação

| Área | Estado | Observação |
|---|---|---|
| Comércio local | PRONTO | Room, regras, estoque, fiado, pagamentos e fornecedores |
| Agentic Shell | PRONTO/PARCIAL | Sessão, contexto, multiturno e confirmação existem |
| Catálogo agentic | PARCIAL | Nem todas as mutações antigas estão no caminho canônico |
| A2UI | PRONTO como fundação | Contrato, mappers, allowlist e renderer existem |
| Voz real/Gemma | PARCIAL | Depende de modelo, permissão e suporte do device |
| Fiscal | PARCIAL | Intake/review existem; convergência e commit visual faltam |
| Offline/recovery | PRONTO localmente | Cloud produtivo e conflitos ainda não estão configurados |
| ADK | PARCIAL / Gate 3.2 | Core oficial conectado como planner, telemetria Room e eval A/B; backend dedicado, dashboard operacional e memória/RAG ainda faltam |
| Koog | SPIKE | Contratos exploratórios |
| RAG | SÓ NO PAPEL | Não deve substituir fatos transacionais do Room |

## 11. Diagnóstico arquitetural atual

Para esta arquitetura Android local-first, o diagnóstico de system design é
**4/10**. Três dos oito critérios estão atendidos ou parcialmente atendidos:
escopo funcional explícito, caminhos assíncronos de outbox/sync e observabilidade
local redigida.

Ainda faltam:

- estimativas de volume/QPS e retenção para cloud;
- redundância de backend e estratégia de escalabilidade do Room/cloud;
- cache para leituras remotas quando o backend existir;
- monitoramento agregado com alertas;
- estratégia de distribuição progressiva e rollback fora do `adb`.

As correções pertencem a uma futura camada cloud. Não devem ser adicionadas ao
aplicativo local apenas para fazer o diagrama parecer mais distribuído.

## 12. Mapa de código

- Host Android: `app/src/main/java/com/tino/app/MainActivity.kt`
- Composição e estado global (M01): `app/src/main/java/com/tino/app/TinoApp.kt`
- Navegação e shell de telas (segunda fatia M01): `app/src/main/java/com/tino/app/TinoNavigation.kt`
- Home e superfície agentic principal (terceira fatia M01): `app/src/main/java/com/tino/app/TinoHome.kt`
- Agentic Shell: `app/src/main/java/com/tino/app/domain/agent/`
- Contexto e linguagem: `app/src/main/java/com/tino/app/domain/language/`
- Voz/Gemma: `app/src/main/java/com/tino/app/core/speech/`
- A2UI contrato/codec: `app/src/main/java/com/tino/app/interfaceadapter/a2ui/`
- A2UI Compose: `app/src/main/java/com/tino/app/ui/a2ui/`
- Domínio comercial: `app/src/main/java/com/tino/app/domain/commerce/`
- Banco e DAOs: `app/src/main/java/com/tino/app/core/database/`
- Sync e snapshot: `app/src/main/java/com/tino/app/core/sync/`
- Fiscal: `app/src/main/java/com/tino/app/feature/fiscal/` e `tino-fiscal-core/`
- Contratos neutros do spike: `tino-agent-contracts/` e `koog-spike/`
