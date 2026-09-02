# TINO Android Backend Discovery

Auditoria somente leitura do working tree em 2026-08-26. O código atual é a fonte de verdade. Documentos de especificação foram usados apenas para identificar intenções e conflitos; não foram tratados como comportamento implementado.

Legenda usada neste relatório:

- **IMPLEMENTADO**: existe código executável e caminho de uso no app.
- **PARCIALMENTE IMPLEMENTADO**: há modelo, contrato ou infraestrutura, mas o fluxo está incompleto, desativado, dependente de fallback ou sem integração de produção.
- **PLANEJADO / DOCUMENTADO**: aparece em specs, ADRs, runbooks ou documentação, sem implementação equivalente confirmada no Android.
- **NÃO ENCONTRADO**: não foi localizado no código, manifest, Gradle ou testes auditados.

## Executive Summary

### Estado atual

- **IMPLEMENTADO**: aplicativo Android Compose em um único módulo de app, com Hilt, Room, WorkManager, processamento local de voz/Gemma, agente local, A2UI local e domínio comercial local.
- **IMPLEMENTADO**: cadastro local de um comércio por instalação, com nome do comércio, nome do responsável, celular, vertical, módulos, padrões operacionais e capabilities permanentes não mutáveis.
- **IMPLEMENTADO**: clientes, produtos, vendas, estoque, fornecedores, compras, pedidos, recebíveis/fiado, pagamentos de fiado, ajustes, reversões, quitações, contestação e projeção financeira local.
- **IMPLEMENTADO**: eventos de domínio persistidos com `storeId` e `deviceId`, usados como base de outbox e aplicação de eventos remotos.
- **PARCIALMENTE IMPLEMENTADO**: sync possui contrato, gateway REST HTTPS, cursor, estados de evento, WorkManager, retry e applier; a build atual deixa `TINO_SYNC_BASE_URL` vazio e injeta `UnavailableSyncGateway`. Portanto, o transporte cloud não está ativo no build auditado.
- **PARCIALMENTE IMPLEMENTADO**: existe armazenamento criptografado de um token Bearer em Android Keystore, mas não há fluxo de login, emissão, refresh, logout ou associação do token a usuário.
- **IMPLEMENTADO**: Google ADK está no Gradle e é usado na proposta de planos do planner local baseado em Gemma; o orchestrator Google ADK abstrato está ligado ao fallback indisponível, e a execução continua local/determinística.
- **IMPLEMENTADO**: A2UI é um protocolo/modelo local, com catálogo, allowlist, mapeadores, surfaces e validação de ações. Não há evidência de backend gerando A2UI.
- **NÃO ENCONTRADO**: login, registro de conta, Google Sign-In, email/senha, telefone/OTP, OIDC/Keycloak executável, recuperação de conta, múltiplos usuários, múltiplos comércios, configuração de Pix, QR Pix, webhook/notificação Pix, reconciliação de pagamentos ou `PaymentEvidence`.

### Consequência para o backend

O backend precisa inicialmente reproduzir o vocabulário e os eventos comerciais já consumidos pelo Android, mantendo IDs string/UUID, valores em centavos, timestamps epoch em milissegundos e escopo por `store_id`. A autenticação e o bootstrap de conta/comércio serão uma nova camada; não existe contrato Android atual de login a preservar.

O app **não está pronto para uma integração backend completa sem adaptação**. A classificação final é **PARTIALLY READY**: o domínio e a fundação de sync oferecem uma base útil, mas identidade de usuário, tenant real, transporte cloud habilitado, versionamento/conflict handling de projeções e contratos de autenticação ainda faltam.

## Current Architecture

### Módulos Gradle

```text
root : tino
├── :app                    Android application, Compose, Room, Hilt, WorkManager, Gemma, ADK
├── :tino-agent-contracts   Kotlin/JVM contract module
├── :tino-fiscal-core       Kotlin/JVM fiscal/product-import core
├── koog-spike/              projeto separado de spike; não incluído no settings principal
└── tino-fiscal-service/    serviço TypeScript separado para fiscal; não é backend Java/Spring
```

### Pacotes relevantes

```text
com.tino.app
├── core
│   ├── common       IdentityProvider, UuidV7
│   ├── database     Room entities, DAOs, database, migrations, repositories
│   ├── di           Hilt bindings
│   ├── intelligence ADK adapter, planner proposal, Room facts
│   ├── observability audit logging
│   ├── security     SecureTokenStore
│   ├── speech       Android speech, MediaPipe/Gemma adapters and service
│   └── sync         event outbox, REST gateway, cursor, worker and applier
├── domain
│   ├── agent        capabilities, boundary, session, tools, agent activities
│   ├── commerce     repository, credit, ledger, payment methods, entity resolution
│   ├── finance      financial projection and summary
│   ├── fiscal       fiscal import
│   ├── intelligence planner, runtime, analytics, recommendations, memory
│   ├── language     deterministic language interpretation and learning
│   ├── orders       order model/draft
│   ├── profile      BusinessProfile, modules, context resolution
│   ├── usecase      query and mutation use cases
│   └── voice        tool calls, mutation gate, global command routing
├── feature
│   ├── home         TinoViewModel and home flows
│   ├── voice        agent/voice ViewModels and A2UI action ViewModel
│   └── fiscal       camera/OCR/document import UI
├── interfaceadapter/a2ui  protocol, codecs, catalog, semantic mappers
├── presentation/splash
└── ui                      Compose renderers, navigation, theme and components
```

### Limites arquiteturais observados

- UI chama `TinoViewModel`, `AgenticVoiceViewModel`, `TinoAgentSessionViewModel` e `TinoA2uiActionViewModel`.
- ViewModels chamam use cases, `CommerceRepository`, `StoreProfileRepository` e boundaries de agente.
- Repositories chamam DAOs Room e, em mutações comerciais, escrevem evento de domínio na mesma transação.
- A camada de domínio não depende de Retrofit/Ktor/GraphQL; o único cliente HTTP atual é o gateway de sync baseado em `HttpURLConnection`.
- Não há módulo Android separado por feature; as features estão em pacotes dentro de `:app`.

## Authentication

**Status: NÃO ENCONTRADO para login; PARCIALMENTE IMPLEMENTADO para token técnico de sync.**

### O que existe

`SecureTokenStore` usa Android Keystore para criptografar uma string em `SharedPreferences` (`tino_secure_session`, chaves `token` e `iv`). `RestSyncGateway` lê esse valor e, se presente, envia `Authorization: Bearer <token>`.

Isso é apenas um ponto de armazenamento/transporte de token. Não existe código que faça login, registre usuário, valide sessão, renove token ou associe token a uma pessoa.

### O que não existe

Não foram encontrados:

- tela ou ViewModel de login;
- tela de registro de conta;
- email/senha;
- telefone/OTP;
- Google Sign-In;
- OAuth/OIDC executável;
- Keycloak SDK/configuração executável;
- JWT parser/claims/session model;
- refresh token;
- logout conectado ao estado do app;
- recuperação de conta;
- modo convidado explicitamente modelado;
- PIN de acesso;
- múltiplas contas por instalação.

### Fluxo ativo relacionado à identidade

```text
MainActivity
  -> TinoApp
     -> TinoViewModel.profileLoaded/storeProfile
        -> StoreProfileRepository.observe()
           -> StoreProfileDao.observe()
              -> Room: store_profile, id = 'default'
```

Esse fluxo decide somente se o app mostra `FirstAccess` ou `Home`; não autentica ninguém.

### Fluxo de sincronização que usa token, quando configurado

```text
mutação local
  -> CommerceRepository / FiscalImportCommitService
     -> DomainEventDao.insert
     -> WorkManagerSyncScheduler.schedule
        -> SyncWorker
           -> SyncCoordinator.syncOnce
              -> RestSyncGateway
                 -> SecureTokenStore.read
                 -> POST /v1/sync/events ou GET /v1/sync/changes
```

Na build atual, `BuildConfig.TINO_SYNC_BASE_URL = ""`; o binding Hilt escolhe `UnavailableSyncGateway`. Logo, esse caminho REST está implementado como infraestrutura, mas não ativo.

### Documentação conflitante

Specs como `specs/TINO-BACKEND-001 — Backend Foundation.md`, `specs/TINO-BACKEND-002-sync-contracts.md` e `specs/TINO-BACKEND-008-production-hardening.md` descrevem Keycloak/OIDC/JWT e autorização server-side. Isso é **PLANEJADO / DOCUMENTADO**, não uma capacidade presente no Android.

## User Identity

**Status: PARCIALMENTE IMPLEMENTADO; identidade de instalação, não identidade de usuário.**

`InstallationIdentity` possui:

| Campo | Tipo | Origem | Persistência |
|---|---|---|---|
| `storeId` | `String` UUID v7 | gerado localmente por `UuidV7.new()` | `SharedPreferences("tino_identity")`, chave `store_id` |
| `deviceId` | `String` UUID v7 | gerado localmente por `UuidV7.new()` | mesma preferência, chave `device_id` |

`IdentityProvider.current()` cria os dois valores sob demanda e os reutiliza na instalação. Não há `userId`, `ownerId`, `merchantId`, `profileId`, `accountId` ou `externalId` no modelo de identidade executável.

Implicações observáveis:

- os IDs permanecem estáveis enquanto as preferências da instalação forem preservadas;
- reinstalação/limpeza de dados não tem mecanismo de recuperação desses IDs;
- `storeId` é tratado como escopo lógico mesmo antes de existir uma conta remota;
- `deviceId` representa a instalação/aparelho;
- não há suporte modelado para dois usuários ou dois comércios na mesma instalação;
- as entidades comerciais não carregam `storeId` próprio; o escopo aparece principalmente nos eventos.

O `UuidV7` local retorna `String` no formato UUID e usa timestamp de `System.currentTimeMillis()` com bits de versão 7 e aleatoriedade. O backend deve aceitar IDs string compatíveis, mas não deve presumir que esse gerador seja uma implementação normativa completa de UUID v7 sem validação adicional.

## Business Model

**Status: IMPLEMENTADO localmente; NÃO IMPLEMENTADO como cadastro remoto/merchant.**

### Tipos e persistência

`BusinessProfile` possui:

- `primaryVertical: BusinessVertical`;
- `enabledModules: Set<BusinessModule>`;
- `storeName: String`;
- `ownerName: String?`;
- `phone: String?`;
- `version: Int`;
- `operationalPatterns: Set<OperationalPattern>`;
- `permanentCapabilities: Set<TinoCapabilityId>`.

O Room armazena isso em uma única `StoreProfileEntity` com `id = "default"`. Sets são serializados como strings separadas por vírgula. `createdAt` é salvo, mas não existe `updatedAt`.

### Campos solicitados

| Campo | Estado atual |
|---|---|
| nome/razão de comércio | IMPLEMENTADO como `storeName` |
| nome do proprietário | IMPLEMENTADO como `ownerName` |
| telefone | IMPLEMENTADO como `phone` obrigatório no primeiro cadastro |
| chave Pix | NÃO ENCONTRADO |
| tipo da chave Pix | NÃO ENCONTRADO |
| logo | NÃO ENCONTRADO |
| endereço | NÃO ENCONTRADO no perfil; `addressReference` existe somente em pedido |
| CPF/CNPJ/documento do comércio | NÃO ENCONTRADO no perfil; `taxId` existe somente em fornecedor |
| tipo de negócio | IMPLEMENTADO como `primaryVertical` |
| módulos/capabilities | IMPLEMENTADO localmente |
| onboarding concluído | NÃO há booleano; conclusão é inferida pela existência da linha `store_profile` |

### Regra de cardinalidade

O DAO consulta apenas `id = 'default'` e o repositório usa `PROFILE_ID = "default"`. Portanto, o código suporta um único perfil/estabelecimento por instalação. Não há lista de businesses, membership ou active business selector.

### Repositórios e chamadas

```text
FirstAccessScreen
  -> TinoViewModel.saveStoreProfile
     -> StoreProfileRepository.save
        -> BusinessProfile validation
        -> StoreProfileDao.upsert(StoreProfileEntity)

BusinessProfileSettingsScreen
  -> TinoViewModel.updateBusinessProfileAndWait
     -> StoreProfileRepository.updateProfile
        -> StoreProfileDao.observe().first()
        -> StoreProfileDao.upsert
```

Atualização de perfil não cria `DomainEventEntity` e não chama `SyncScheduler`.

## Business Types

**Status: IMPLEMENTADO como configuração local; escopo funcional ainda composto por capabilities compartilhadas.**

### Verticais existentes

O enum `BusinessVertical` contém exatamente:

- `RETAIL` — exibido como “Loja / varejo”;
- `BAKERY` — “Padaria”;
- `RESTAURANT` — “Restaurante”;
- `STORE` — “Comércio”;
- `OTHER` — “Outro”.

Não foram encontrados enums específicos para mercado, mercearia, confeitaria, feirante, produtor, açougueiro, peixeiro ou MEI.

`BusinessType` é apenas typealias para `BusinessVertical`.

### Padrões operacionais

`OperationalPattern` contém `TURNOVER_COMMERCE`, `PRODUCTION_AND_SALES`, `FOOD_SERVICE`, `SERVICES_WITH_APPOINTMENTS`, `SERVICES_WITH_WORK_ORDER` e `GENERAL`. Os dois padrões de serviço existem no enum e catálogo, mas não são associados a uma opção específica de onboarding além de `OTHER` cair em `GENERAL`.

### Presets

`VerticalPresetCatalog` dá a todas as verticais, exceto `OTHER`, o mesmo conjunto padrão:

`CORE, SALES, INVENTORY, CUSTOMERS, CREDIT, STOCK_ENTRY, FISCAL`.

`OTHER` recebe `CORE, CUSTOMERS`.

Isso significa que escolher Padaria ou Restaurante não ativa um pacote de telas específico; os módulos base são compartilhados. Os módulos verticais `BAKERY`, `RESTAURANT` e `STORE` funcionam como marcadores/capabilities adicionais.

## Onboarding

**Status: IMPLEMENTADO localmente; sem bootstrap remoto ou verificação de conta.**

### Sequência ativa

```text
Primeiro launch
  -> TinoApplication.onCreate
     -> agenda sync best-effort
     -> inicia GemmaInferenceService
  -> MainActivity / splash
  -> TinoApp.TinoScreen.Splash
  -> espera profileLoaded e splashAnimationFinished
  -> se StoreProfileDao.observe() == null: FirstAccess
  -> caso contrário: Home
```

### FirstAccess

Campos:

- obrigatórios na UI: nome do comércio, seu nome, celular;
- seleção obrigatória por estado inicial: vertical, default `RETAIL`;
- módulos: preset automático, com opção de personalizar;
- voz contextual de onboarding: pode preencher `store_name`, `owner_name`, `phone`;
- restauração: botão “Já tenho um comércio”, abre `RestoreStore`.

Validações:

- store name não pode ser vazio;
- owner name não pode ser vazio;
- phone não pode ser vazio na UI;
- repositório exige entre 10 e 13 dígitos no telefone;
- `BusinessProfile` exige `CORE`;
- módulo `CREDIT` exige `CUSTOMERS`;
- módulo `STOCK_ENTRY` exige `INVENTORY`;
- capabilities permanentes desconhecidas ou mutações permanentes são rejeitadas.

Ao continuar: `FirstAccessScreen -> TinoViewModel.saveStoreProfile -> StoreProfileRepository.save -> Room`. A tela troca para Home imediatamente; a falha de persistência é comunicada pelo ViewModel.

### RestoreStore

`RestoreStoreScreen` existe como tela de navegação, mas não há fluxo UI que injete um snapshot ou execute `CommerceSnapshotRepository.restore`. A restauração existe como serviço/testes de snapshot e como cenário de debug de mutation safety, não como bootstrap de conta cloud confirmado.

### Fluxos por vertical

Não existem fluxos de onboarding separados por vertical. A vertical altera preset, padrões, vocabulary, analytics e capabilities resolvidas.

## Room Schema

**Status: IMPLEMENTADO; banco `tino.db`, versão 21, schema exportado.**

### Database e migrações

- classe: `TinoDatabase`;
- versão atual: `21`;
- `exportSchema = true`;
- schemas JSON presentes de `1.json` a `21.json`;
- converters para `SyncStatus`, `CreditEntryType`, `PurchaseStatus` e `FiscalImportStatus`;
- migrações lineares `MIGRATION_1_2` até `MIGRATION_20_21` registradas no `AppModule`;
- não há foreign keys declaradas nas entidades auditadas;
- transações explícitas usam `RoomDatabase.withTransaction` em repositórios e sync/applier.

### Entidades

| Entidade/tabela | Finalidade | PK | Campos importantes | Relações | Campo de tenant |
|---|---|---|---|---|---|
| `ProductEntity` / `products` | catálogo | `id:String` | `name`, `priceCents`, `unit`, `createdAt` | referenciada por estoque, venda, compra | não |
| `SaleEntity` / `sales` | venda | `id:String` | `totalCents`, `paymentMethod`, `createdAt` | itens por `saleId` | não |
| `SaleItemEntity` / `sale_items` | linhas de venda | `(saleId,lineNumber)` | `productId`, `quantity`, `unitPriceCents` | lógica, sem FK | não |
| `DirectReceiptEntity` / `direct_receipts` | recebimento direto | `id:String` | amount, method, occurredAt, source, note, operationId | operação/idempotência | não |
| `StockMovementEntity` / `stock_movements` | movimentos/estoque | `id:String` | productId, quantityDelta, reason, referenceId, occurredAt | produto por ID lógico | não |
| `CustomerEntity` / `customers` | cliente | `id:String` | name, phone, createdAt | crédito por `customerId` | não |
| `SupplierEntity` / `suppliers` | fornecedor | `id:String` | name, phone, createdAt, taxId | compras/mapeamentos | não |
| `CreditEntryEntity` / `credit_entries` | fatos de crédito/ledger | `id:String` | customerId, amountCents, type, referenceId, occurredAt, paymentMethod, dueAt, ledgerType, provenance, reason | cliente por ID lógico | não |
| `PurchaseEntity` / `purchases` | compra | `id:String` | supplierId, status, totalCostCents, createdAt | itens | não |
| `PurchaseItemEntity` / `purchase_items` | linhas de compra | `(purchaseId,lineNumber)` | productId, quantity, unitCostCents | lógica, sem FK | não |
| `OrderEntity` / `orders` | pedido | `id:String` | channel, fulfillment, customerName, addressReference, status, totalCents, createdAt | itens | não |
| `OrderItemEntity` / `order_items` | linhas de pedido | `(orderId,lineNumber)` | productId, productName, quantity, unitPriceCents | lógica, sem FK | não |
| `FiscalImportEntity` / `fiscal_imports` | importação fiscal | `id:String` | documentId, accessKey, hash, operationId, status, committedAt, XML | supplier/product history | não |
| `SupplierProductMappingEntity` | mapeamento fiscal | `id:String` | supplierId, codes, gtin, productId, confirmedAt, matchMethod | IDs lógicos | não |
| `ProductPurchaseHistoryEntity` | histórico de compra | `id:String` | fiscalDocumentId, supplierId, productId, quantities, costs | IDs lógicos | não |
| `StoreProfileEntity` / `store_profile` | perfil local | `id:String`, sempre default no DAO | storeName, ownerName, phone, createdAt, vertical, modules, patterns, capabilities | nenhum | não; singleton local |
| `DomainEventEntity` / `domain_events` | outbox/event log | `eventId:String` | storeId, deviceId, aggregateId, type, schemaVersion, occurredAt, payloadJson, syncStatus, attempts, lastError | escopo no evento | `storeId` |
| `SyncCursorEntity` / `sync_cursors` | cursor de pull | `scope:String` | cursor, updatedAt | escopo textual | scope |
| `AgentActivityEntity` | projeção de atividade do agente | `id:String` | capability, operationId, summary, undo metadata, status | operação opcional | não |
| `IntelligenceTelemetryEntity` | telemetria de planejamento | `id:String` | request/session/planner/plan/latencies/loop | nenhuma | não |
| `InteractionStateEntity` | estado resumível de sessão | `sessionId:String` | stateJson, policy, updatedAt, expiresAt | sessão local | não |
| `MutationOperationEntity` | gate de confirmação/idempotência | `operationId:String` | capability, args, risk, idempotencyKey, fingerprint, token hash, status, expiry | operação local | não |
| `BusinessMemoryEntity` | memória governada | `id:String` | scopeKey, key/value, lifecycle, confidence, provenance, sourceEventIds | escopo lógico | scopeKey |
| `RecommendationEntity` | recomendação local | `id:String` | productId, decision, confidence, versions, createdAt | outcome por recommendationId | não |
| `RecommendationOutcomeEntity` | resultado de recomendação | `id:String` | recommendationId, outcome, occurredAt | lógica, sem FK | não |

### DAOs

`ProductDao`, `SaleDao`, `DirectReceiptDao`, `FinancialProjectionDao`, `StockMovementDao`, `CustomerDao`, `CreditDao`, `SupplierDao`, `PurchaseDao`, `OrderDao`, `FiscalImportDao`, `SupplierProductMappingDao`, `ProductPurchaseHistoryDao`, `DomainEventDao`, `SyncCursorDao`, `StoreProfileDao`, `AgentActivityDao`, `IntelligenceTelemetryDao`, `InteractionStateDao`, `MutationOperationDao`, `BusinessMemoryDao` e `RecommendationDao`.

### Índices e integridade observada

Há unicidade por nome em produtos, clientes e fornecedores; unicidade por `operationId` em recebimentos diretos e importações; unicidade por idempotency key em mutation operations; índices de cliente/ocorrência em crédito, productId em estoque e índices de status/cursor/eventos. Não há constraints Room de foreign key nem cascade delete. Exclusões DAO são operações globais de limpeza, principalmente para restore/testes.

### Evolução relevante

- v1→v2: clientes, fornecedores, crédito, compras, cursor;
- v2→v3: `store_profile`;
- v3→v4: recebimento direto;
- v4→v6: método de pagamento e vencimento em crédito;
- v6→v7: fiscal, taxId, mappings e histórico;
- v7→v8: atividades do agente;
- v8→v11: telemetria e campos de sessão/loop;
- v11→v13: interaction state e mutation operations;
- v13→v16: business memory e campos de profile/modules/patterns;
- v16→v19: pedidos e recomendações/outcomes;
- v19→v20: versionamento/qualidade de recomendações;
- v20→v21: `ledgerType`, `provenance` e `reason` em crédito.

## Customers

**Status: IMPLEMENTADO localmente.**

`CustomerEntity` possui somente `id:String`, `name:String`, `phone:String?` e `createdAt:Long`. Existe índice único case-sensitive de Room em `name`; resolução de nome usa comparação `LOWER`. O ID é gerado localmente por `UuidV7.new()` em `CommerceRepository.createCustomer`.

Há criação, edição, listagem, busca por nome, saldo derivado, contato e timeline. Não há nickname, email, documento, endereço, consentimento, status, `updatedAt`, `deletedAt` ou soft delete. O delete DAO é somente limpeza global; não existe exclusão de cliente no fluxo atual.

Fluxo ativo de criação manual:

```text
Customers UI
  -> TinoViewModel.addCustomer
     -> CommerceRepository.createCustomer
        -> CustomerDao.insert
        -> DomainEventDao.insert(customer.created)
        -> SyncScheduler.schedule
```

Fluxo de leitura do saldo:

```text
CustomerDao / CreditDao
  -> CommerceRepository.observeCustomerBalances
     -> TinoViewModel.customers
        -> CustomerAccount/CustomerDetail/A2UI
```

Para os fluxos atuais, nome e telefone são suficientes: telefone é opcional nas telas de cliente e criação via agente. O backend não deve exigir campos extras para reproduzir o comportamento atual.

## Credit/Caderneta

**Status: IMPLEMENTADO localmente, com ledger semântico e projeções temporais.**

### Fatos persistidos

`CreditEntryType` contém `SALE` e `PAYMENT`. O sinal do valor é importante:

- venda/recebível: `amountCents > 0`;
- pagamento: `amountCents < 0`;
- saldo: soma de `credit_entries.amountCents` por cliente.

Desde a versão 21, `ledgerType` pode ser `PURCHASE`, `PAYMENT`, `ADJUSTMENT`, `REVERSAL`, `DISPUTE` ou `SETTLEMENT`. `provenance` contém JSON de `LedgerProvenance`, com source, actor, transcript opcional, agentExecutionId opcional e createdAt. `reason` justifica ajustes/compensações.

### Operações do repositório

`CommerceRepository` implementa:

- `registerCreditSale(customerId, productId, quantity, dueAt?, provenance)`: cria sale, sale item, baixa estoque, crédito positivo e eventos `sale.created`/`credit.sale.created`;
- `registerCreditByAmount(customerId, amountCents, operationId, dueAt?, provenance)`: crédito sem produto, idempotente por operationId, evento `credit.receivable.created`;
- `registerCreditPayment(customerId, amountCents, paymentMethod, operationId, provenance)`: pagamento negativo, não pode exceder saldo, método não pode ser `CREDIT`/`UNKNOWN`, evento `credit.payment.received`;
- `registerCreditAdjustment`: ajuste positivo/negativo com motivo, sem permitir saldo negativo;
- `disputeCreditEntry`: lançamento de contestação com valor zero, sem alterar saldo;
- `settleCredit`: cria quitação negativa pelo saldo aberto;
- `reverseCreditPayment`: mantém pagamento original e adiciona compensação positiva;
- `correctCreditPayment`: reversão + pagamento corrigido atomicamente;
- timeline temporal e projection de Shared Ledger.

### TemporalCredit

`TemporalCreditService` deriva `OPEN`, `OVERDUE` e `SETTLED`. Pagamentos sem referência são alocados FIFO às vendas/recebíveis mais antigos. `dueAt` define vencimento; a comparação usa `System.currentTimeMillis()` e `ZoneId.systemDefault()` por padrão. A timeline separa entradas de crédito, pagamentos, saldo atual, aberto, vencido e eventos semânticos.

### Shared Ledger

`SharedLedgerProjector` ordena por `occurredAtEpochMs` e `id`, soma os valores assinados e deriva eventos contestados. `SharedLedgerStatement` é um read model de cliente; transcript e IDs internos de execução não são exibidos pelo formatter.

### Fluxos solicitados

#### “TINO, João ficou de pagar R$ 180 sexta.”

O domínio tem suporte ao equivalente estruturado via `registerCreditByAmount(customerId, 18000, dueAt)`, e também a venda fiada por produto via `registerCreditSale`. Porém, no caminho atual do agente canônico, a capacidade `ADD_CREDIT` existe no registry, enquanto o `AgentCapability` exposto no `AgentQuery` não tem uma operação textual de valor livre equivalente. Portanto:

- **IMPLEMENTADO** no repository/domain;
- **PARCIALMENTE IMPLEMENTADO** no agente/UX para frase livre de valor sem produto;
- `dueAt` existe, mas a resolução de “sexta” precisa vir convertida em epoch pelo interpretador/caller.

#### “João pagou R$ 180.”

Fluxo manual/agente:

```text
voz/UI
  -> AgentIntent / CommandIntentRouter / AgenticTextQueryCoordinator
  -> resolução de Customer por nome
  -> ToolCall.REGISTER_CREDIT_PAYMENT
  -> preview de pagamento
  -> confirmação humana via MutationSafeToolExecutor
  -> RegisterCreditPaymentUseCase
  -> CommerceRepository.registerCreditPayment
  -> Room transaction: CreditEntry + DomainEvent
  -> WorkManager sync scheduling
  -> saldo/timeline/FinancialProjection reativos
```

O pagamento via UI `ReceivePayment` usa dinheiro como default. O agente aceita dinheiro, PIX ou maquininha/cartão conforme o intent/tool call.

### Contrato backend necessário para preservar o comportamento

O backend futuro deverá suportar, no mínimo, cliente por ID, recebível com valor em centavos e vencimento opcional, pagamento com método e operation/idempotency key, eventos imutáveis, ajustes/reversões/quitações/contestação e projeção de saldo. Não deve transformar o saldo em campo autoritativo sem preservar os eventos, pois o Android reconstrói saldo e timeline a partir dos lançamentos.

## Financial Domain

**Status: IMPLEMENTADO como projeção local; não há ledger financeiro geral separado.**

`FinancialProjectionDao` e `FinancialProjectionRepository` são a fonte do resumo financeiro. `FinancialSummary` expõe total recebido, breakdown cash/PIX/card/unknown, total a receber, crédito criado e pagamentos de crédito recebidos.

As fontes são:

- `sales.totalCents` para vendas pagas em cash/pix/card/unknown;
- `direct_receipts.amountCents` para recebimentos diretos;
- `credit_entries` do tipo PAYMENT, somando `-amountCents` para recebimentos de crédito.

Não existe entidade `FinancialLedger` ou `Transaction` genérica. O resumo é uma query derivada; `FinancialSummary` não é fonte autoritativa.

Métodos de pagamento existentes: `cash`, `pix`, `card`, `credit`, `unknown`. `credit` representa venda fiada e não entrada de caixa imediata. Não há reversão genérica de venda, embora haja reversões/correções no domínio de pagamento de crédito.

Períodos usam `FinancialPeriod` com limites epoch em milissegundos e `ZoneId`; há presets today/week/month baseados na zona do `Clock.systemDefaultZone()`.

## Pix

**Status: NÃO ENCONTRADO como configuração ou integração Pix; IMPLEMENTADO apenas como método de pagamento contábil/local.**

Existe `PaymentMethod.PIX("pix")`, filtros e breakdown financeiro, vocabulário de voz e opções de UI. Isso registra que uma venda ou pagamento foi marcado como PIX; não comprova que um Pix foi recebido.

Não foram encontrados:

- chave Pix no perfil;
- tipo de chave;
- QR code estático ou dinâmico;
- EMV/BR Code generator/parser;
- txid;
- descrição/additional information;
- PSP/provedor Stone, Ton ou outro;
- payment intent;
- webhook de confirmação;
- notification listener de banco;
- conciliação automática;
- comprovante/evidência de pagamento.

Qualquer Pix em specs de backend é **PLANEJADO / DOCUMENTADO** e não contrato ativo do Android.

## Notifications

**Status: NÃO ENCONTRADO.**

O manifest declara internet, áudio e câmera, mas não `NotificationListenerService`, `BIND_NOTIFICATION_LISTENER_SERVICE`, `POST_NOTIFICATIONS` ou fluxo de acesso a notificações. Não existem referências executáveis auditadas a `StatusBarNotification`, `NotificationListener`, `NotificationManager` para leitura de comprovantes ou permission UX de notification access.

Consequentemente não há apps-alvo lidos, metadados persistidos, redaction/privacy policy ou background restriction específica para notificações.

## Reconciliation

**Status: NÃO ENCONTRADO como domínio de pagamento.**

Não existem classes/campos executáveis `PaymentEvidence`, `PaymentIntent`, `Reconciliation`, `Match`, `PaymentConfirmed` ou `PixReceived`. `Settlement` existe somente como `SharedLedgerEventType.SETTLEMENT`/`settleCredit`, significando quitação de saldo de cliente, não liquidação bancária.

`DirectReceiptEntity` e `CreditEntryEntity` são os conceitos mais próximos, porém registram um fato manual/local; não carregam external transaction ID, end-to-end ID, evidence, confidence, matching rule ou confirmedAt separado.

## Agents / ADK

**Status: IMPLEMENTADO localmente; backend adapter ainda não existe.**

### Caminho ativo de composição

Hilt fornece:

- `MediaPipeGemmaAgentIntentAdapter` como `AgentIntentInterpreter`;
- `TinoAgentBoundary` como boundary de queries/actions;
- `AgenticTextQueryCoordinator` como porta de consultas textuais;
- `GoogleAdkGemmaPlanProposal` como proposta de planos ADK/Gemma;
- `AdkQueryPlanner` com fallback `DeterministicIntelligenceQueryPlanner`;
- `GoogleAdkRuntimeAdapter` como runtime de inteligência;
- `AdkAgentRuntime` como runtime do loop de agente;
- `DbFirstReadCapabilityService` para leituras comerciais;
- `CommerceActionA2uiMapper`/mapeadores A2UI para apresentação.

### Dados expostos ao agente

O agente lê via repositories/use cases:

- resumo financeiro;
- produtos, estoque e preços;
- clientes, contatos e saldos;
- fornecedores;
- recebíveis e vencidos;
- timeline e Shared Ledger de cliente;
- recomendações/analytics locais.

Mutações relevantes são `REGISTER_CREDIT_SALE`, `REGISTER_SALE`, `REGISTER_STOCK_RECEIPT`, `REGISTER_CREDIT_PAYMENT`, `CREATE_CUSTOMER` e `CHANGE_PRODUCT_PRICE`, dependendo do caminho de tool/runtime. O registry também possui capacidades de ajustes/undo e crédito.

### Confirmação

`MutationSafeToolExecutor` exige preview token para mutation. `MutationOperationEntity` persiste operação, fingerprint, hash do token, risco, status e expiração. `A2uiActionValidator` valida session/surface/component/action/payload antes do bridge. A execução comercial continua local e grava `AgentActivityEntity` como projeção de apresentação.

### Contrato de backend para agentes

O backend adapter futuro deverá preservar nomes/capabilities e semântica de leitura/mutação, mas não deve executar uma mutation somente porque recebeu uma intenção do LLM. A confirmação humana, idempotência, operationId e autorização por tenant precisam continuar sendo gates independentes do texto/LLM.

## A2UI

**Status: IMPLEMENTADO localmente; geração remota NÃO ENCONTRADA.**

### Protocolo

- schema: `tino.a2ui`;
- versão: `1`;
- envelope: `A2uiMessage(messageId, component, schema, version)`;
- componentes não permitidos são tratados como dados `Unsupported`, não como instruções executáveis.

### Catálogo relevante

O catálogo/allowlist inclui:

`financial_summary_card`, `entity_choice`, `action_confirmation`, `operation_success`, `undo_action`, `error_recovery`, `payment_preview`, `stock_entry_preview`, `price_change_preview`, `credit_preview`, `stock_status`, `supplier_summary`, `clarification_selector`, `customer_balance_card`, `customer_timeline_card`, `product_list`, `product_replenishment`, `product_stock`, `product_price`, `customer_list`, `customer_contact`, `receivables_list`, `overdue_list` e `insight_card`.

`CommerceActionA2uiMapper` transforma previews locais de pagamento, criação de cliente e mudança de preço em confirmation/payment/price preview. `DbFirstReadA2uiMapper`, `CustomerBalanceA2uiMapper`, `CustomerTimelineA2uiMapper` e `FinancialSummaryA2uiMapper` transformam resultados locais em cards.

### Consumo

```text
Agent/Query result
  -> AgenticVoiceViewModel / VoiceViewModel
     -> A2UI mapper/planner
        -> A2uiMessage ou A2uiSurfaceState
           -> TinoA2UiRenderer / TinoCatalogSurfaceRenderer

ação declarativa
  -> TinoA2uiActionViewModel
     -> A2uiActionRouter
        -> A2uiActionValidator
           -> A2uiActionRuntimeBridge
              -> AgentRuntime ou MutationConfirmationPort
```

IDs de ação incluem confirmação, cancelamento, seleção de entidade, filtro, request details, continue e ações locais catalogadas. O backend não deve mandar actions arbitrárias; se futuramente enviar A2UI, deverá aderir ao schema/allowlist e à validação existente.

## Modules / Capabilities

**Status: IMPLEMENTADO como configuração local.**

### Módulos

`BusinessModule`: `CORE`, `SALES`, `INVENTORY`, `CUSTOMERS`, `CREDIT`, `STOCK_ENTRY`, `FISCAL`, `RETAIL`, `BAKERY`, `RESTAURANT`, `STORE`.

### Relação

`BusinessProfile.enabledModules` é validado por `BusinessProfileValidator`; `TinoModuleRegistry` transforma módulos em `TinoVerticalModule`, capabilities, vocabulary e analytics. `DefaultBusinessContextResolver` soma capabilities dos módulos, capabilities permanentes e ativações efêmeras válidas. `TinoApp.navigate` e a navegação verificam a capability requerida.

Mutações não podem ser `permanentCapabilities`; continuam exigindo confirmação. Os módulos são persistidos como nomes CSV em `StoreProfileEntity`; capabilities permanentes também como CSV.

### Capabilities principais

Queries: `READ_FINANCIAL_SUMMARY`, `READ_RECEIVABLES`, `READ_CUSTOMER_BALANCE`, `GET_CUSTOMER_CONTACT`, `SEARCH_CUSTOMER`, `LIST_PRODUCTS`, `REPLENISHMENT_QUERY`, `GET_PRODUCT_STOCK`, `GET_PRODUCT_PRICE`, `LIST_CUSTOMERS`, `LIST_SUPPLIERS`, `LIST_RECEIVABLES`, `LIST_OVERDUE`, `SEARCH_PRODUCT`, `READ_PRODUCT`, `READ_STOCK`, `SEARCH_SUPPLIER`.

Mutations: `CREATE_CUSTOMER`, `REGISTER_STOCK_ENTRY`, `ADD_CREDIT`, `ADD_CREDIT_ITEM`, `RECEIVE_CREDIT_PAYMENT`, `REVERSE_CREDIT_PAYMENT`, `CHANGE_PRODUCT_PRICE`.

Navegação/contexto: `NAVIGATE`, `OPEN_ENTITY`, `FOCUS`, `FILTER`, `SEARCH`.

Há também o enum legado/alternativo `AgentCapability` em `AgentQuery`, com subconjunto e aliases diferentes. Esse é um acoplamento de compatibilidade que o backend não deve expor sem uma camada de normalização.

## IDs / Time

### Identificadores

| Objeto | ID atual | Geração | Observação de compatibilidade |
|---|---|---|---|
| Store/business | `String` | UUID v7 local em `IdentityProvider` | é installation scope, ainda não cadastro remoto |
| User | não existe | — | backend terá de introduzir sem quebrar `storeId` |
| Device | `String` | UUID v7 local | está no evento, não nas entidades comerciais |
| Customer | `String` | UUID v7 local | estável enquanto dados/preferences/db persistirem |
| Product | `String` | UUID v7 local | sem updated/version |
| CreditEntry | `String` | UUID v7 local; payment usa operationId como ID | operação e entidade ficam fortemente acopladas |
| Sale | `String` | UUID v7 local | saleId separado de creditEntryId em venda fiada |
| Payment | não há entidade Payment | credit entry negativa | operationId identifica o lançamento de pagamento |
| Supplier/Purchase/Order | `String` | UUID v7 local | sem tenant column/version |
| Sale/Purchase/Order line | chave composta String+Int | linha local | não são entidades com UUID |

UUID v7 de backend pode mapear diretamente para os campos `String`, desde que o Android receba o mesmo ID e não seja necessário converter para `Long`. IDs externos/não UUID exigiriam apenas manter String, mas podem afetar ordenação e resolução.

### Tempo

- entidades comerciais usam `Long` epoch milliseconds;
- campos são `createdAt`, `occurredAt`, `committedAt`, `updatedAtEpochMs`, `expiresAtEpochMs` conforme o contexto;
- `FinancialPeriod` usa epoch boundaries mais `zoneId` textual;
- formatação de ledger converte epoch com `Instant.ofEpochMilli(...).atZone(zone)`;
- default de negócio é `ZoneId.systemDefault()`/`Clock.systemDefaultZone()`;
- não há ISO-8601 persistido nas entidades Room auditadas;
- há inconsistência nominal entre `createdAt`, `occurredAt` e `*EpochMs`; o backend adapter deve normalizar com cuidado, sem alterar o payload existente.

## Networking

**Status: PARCIALMENTE IMPLEMENTADO.**

Não foram encontrados Retrofit, Ktor client, OkHttp, GraphQL ou WebSocket. `RestSyncGateway` usa `java.net.HttpURLConnection`.

### Contrato HTTP atual do gateway

- base URL: `BuildConfig.TINO_SYNC_BASE_URL`;
- exige prefixo `https://`;
- `POST /v1/sync/events` com `{ "events": [...] }`;
- `GET /v1/sync/changes?cursor=...&limit=100`;
- headers `Accept`, `Content-Type`, `X-Request-Id` e Authorization Bearer se token existir;
- eventos usam snake_case: `event_id`, `store_id`, `device_id`, `aggregate_id`, `event_type`, `schema_version`, `occurred_at`, `payload`;
- push espera `acknowledged_event_ids`, `already_processed_event_ids` e opcional `rejected` com eventId/code/retryable/message;
- timeouts: connect 10s, read 15s;
- resposta limitada a 1.000.000 caracteres;
- 401/403 viram `SyncAuthRequiredException`, 408/429/5xx são retryable, outros erros são permanentes;
- circuit breaker local abre após três falhas consecutivas.

Na configuração atual, a base URL vazia seleciona `UnavailableSyncGateway`, então não há API real ativa no APK auditado.

## Offline / Sync

**Status: PARCIALMENTE IMPLEMENTADO, com forte fundação local-first.**

### Fundamentos existentes

- transação local é fonte de sucesso da UI;
- `DomainEventEntity` funciona como outbox/event log;
- `SyncStatus`: `PENDING`, `SYNCING`, `SYNCED`, `FAILED`, `REJECTED`, `BLOCKED`, `CONFLICT`;
- pending query busca PENDING/FAILED;
- processo interrompido recupera SYNCING para PENDING;
- attempts/lastError persistem falha;
- `SyncCursorEntity` guarda cursor por scope;
- `SyncCoordinator` faz push antes de pull e aplica eventos em transação;
- `RemoteEventApplier` ignora eventos duplicados por eventId;
- `WorkManagerSyncScheduler` exige rede conectada e exponential backoff;
- `CommerceSnapshotRepository` exporta/restaura estado local com validação;
- `InMemoryCloudSyncGateway` existe para testes de dois dispositivos;
- `SyncCircuitBreaker` protege o transporte.

### Lacunas de sync

- transporte cloud desabilitado na build;
- não há `updatedAt`/version em produtos, clientes, perfil, vendas ou créditos;
- não há tombstones/deletes sincronizáveis;
- não há foreign keys para reconstrução automática;
- não há estratégia de conflito materializada além do enum `CONFLICT`;
- `store_profile` não gera domain event;
- dados comerciais não carregam tenant column, dependem do evento/escopo;
- o cursor é textual e a semântica server-side não existe no Android;
- não há confirmação de que o servidor idempotente aceite toda a lista de tipos que o applier conhece.

### Eventos conhecidos pelo applier

`product.created`, `customer.created`, `customer.updated`, `supplier.created`, `product.created.from_fiscal_document`, `supplier.product.mapping.created`, `sale.created`, `direct.receipt.created`, `credit.receivable.created`, `stock.received`, `inventory.purchase.received`, `credit.payment.received`, `credit.payment.reversed`, `credit.adjustment.created`, `credit.entry.disputed`, `credit.settled`, `purchase.created`. Eventos desconhecidos são persistidos como `BLOCKED`.

## Settings

**Status: IMPLEMENTADO parcialmente como telas locais.**

Configurações ativas:

- “Meu negócio”: vertical, módulos, capabilities permanentes ativadas/desativadas;
- “Backup e sincronização”: navega para detalhes de sync;
- “Trabalhar sem internet”: mostra modo offline;
- importação fiscal;
- telas/debug de A2UI, mutation safety, memória e agent loop.

Há um cartão informativo para som, voz, impressora e acessibilidade, declarando que opções serão configuradas quando disponíveis. Não há controles funcionais de tema, notificações, voz, impressora, conta ou backup remoto.

O backend futuramente precisaria refletir no perfil: business name, owner/phone, vertical, módulos/capabilities e estado de sincronização. Pix, logo, endereço, documento e conta não têm representação Android atual.

## Development and Future Discovery

Esta seção registra o que aparece como trabalho em andamento ou futuro nos
checkpoints, backlog e specs. Não mistura intenção com implementação: quando
uma spec diz “ready”, “draft” ou “implemented”, isso descreve o documento ou o
gate do trabalho; somente código integrado foi considerado implementado nas
seções anteriores.

### Em desenvolvimento / parcial

| Frente | Evidência atual | O que já existe | O que ainda falta |
|---|---|---|---|
| `CAP-001` catálogo agentic | `TINO-INCOMPLETE-VALIDATION-BACKLOG.md`, `TINO-CAPABILITY-MATRIX.md` | use cases, registry, mapeadores e promoções recentes | uniformizar o catálogo canônico; reduzir dependência do dispatcher legado; provas físicas de voz para capabilities recentes |
| Gate de voz real | `TINO-AGENT-012-REAL-SPEECH-GATE.md` | permissão, ASR/Gemma, fallback, review, confirmação e testes | validação física contínua de microfone, fala longa, latência e disponibilidade do modelo por aparelho |
| Predictive Replenishment / G6.1 | `TINO-EVIDENCE-G6.1-2026-08-26.md`, `Recommendations.kt` | heurística local, persistência, decisão, outcomes, expiração e versões | validação física da interação; G6 completa não está fechada |
| A2UI semântico | status do projeto, catálogo e renderers | protocolo v1, allowlist, surfaces, actions, cards e mapeadores | componente dedicado para todos os tipos semânticos, activity UX, cobertura visual e estados restantes |
| Activity/Undo | `AgentActivityEntity`, `MutationOperationEntity`, `AgentUndoService` | activity persistida; Undo/compensação de pagamento de fiado | compensadores seguros para preço, estoque, venda, venda fiada e criação de cliente; UX de histórico |
| Sync cloud | B002/B003 + `RestSyncGateway` | envelope, outbox, cursor, retry, applier, in-memory gateway e testes | backend cloud, storage, autenticação end-to-end, observabilidade, conflitos de produção e restore remoto |
| Fiscal scanner | `TINO-DOCUMENT-INTAKE-001.md`, `TINO-DOCUMENT-SCANNER-001.md` | quality gate, CameraX/análise, captura/retificação e revisão em partes | smoke final em device, contorno robusto, OCR estrutural e adapter mobile/externo aprovado |
| Serviço fiscal externo | `tino-fiscal-service/` | servidor TypeScript, health check, XML handoff, retry e `NOT_CONFIGURED` seguro | adapter NFeWizard/certificado, homologação real e integração completa com Android |
| UX/UI P0 | `TINO-UX-UI-P0-HARDENING.md` e status do projeto | Home/design system e telas principais | revisão tela a tela, estados offline/erro/sucesso, screenshots baseline e acabamento secundário |

### Implementado no código, mas ainda com gate de produto

Criação de cliente pelo agente, alteração de preço, pagamento de fiado,
contato de cliente, fornecedores, reposição preditiva e vários fluxos de voz
possuem código/testes locais. A documentação ainda registra validação física,
promoção canônica ou integração pendente em alguns deles. O backend não deve
considerar uma capability pronta para produção somente porque seu enum/use case
existe.

### Planejado / documentado para o backend

Os documentos B001–B008 formam uma sequência planejada de backend cloud:

| Documento | Escopo documentado | Estado factual no workspace |
|---|---|---|
| B001 | fundação cloud, store/business, autenticação e operação base | spec “ready for implementation”; nenhum backend Java/Spring correspondente encontrado |
| B002 | envelope de eventos, push/pull, idempotência, cursor e conflitos | draft implementável; client Android parcial, servidor não encontrado |
| B003 | projeções cloud de comércio e endpoints de leitura | draft implementável; projeções continuam locais no Android |
| B004 | intake fiscal externo | contrato/spec; serviço TypeScript parcial, sem integração produtiva |
| B005 | pedidos WhatsApp, webhook, confirmação e entrega | draft implementável; apenas modelos/parser/draft local |
| B006 | orchestrator TINO | spec; runtime Android local possui boundaries, não orchestrator cloud |
| B007 | dados de inteligência, evidência e privacidade | spec; baseline heurístico local parcial |
| B008 | hardening, autorização, observabilidade e produção | spec; alguns controles client-side existem, gates cloud pendentes |

“Backend pronto para implementação” nos documentos não significa que o backend
exista. O workspace contém `tino-fiscal-service` em TypeScript, mas não contém
um módulo Java/Spring Boot ou serviço cloud geral correspondente aos B001–B008.

### Futuro de produto documentado

O `specs/PRD.md` registra como P1/P2 ou visão futura:

- pedidos por WhatsApp, confirmação, acompanhamento e integração ao app do comerciante;
- retirada/entrega, localização GPS, ponto salvo e referência textual;
- fornecedor e operação via WhatsApp;
- previsão de demanda e previsão de ruptura;
- recomendação de reposição e otimização de compras;
- análise de produtos associados/market basket;
- estoque parado/slow moving;
- recorrência de cliente;
- RAG para contexto textual/histórico, sem substituir consultas estruturadas;
- Attention Engine A3 com sinais, ranking, dismiss, snooze, recorrência,
  central de pendências, digest e notificações operacionais;
- observabilidade centralizada, crash reporting conectado, pipeline de release,
  restore cloud e validação multi-device/densidade.

Esses itens não são contratos Android existentes. São candidatos a milestones
futuros e exigem decisão própria de escopo, modelo e autoridade dos dados.

### Multi-vertical e multi-store

`BusinessProfile`, módulos e verticais já existem localmente. A epic de
integração multi-vertical documentada pretende transformar esse perfil em fonte
de verdade para Home, navegação e Agentic Shell. Isso não equivale a
multi-store remoto: a instalação continua com um único `store_profile` default e
sem User/membership. Multi-store, multi-tenant real e retomada entre aparelhos
permanecem futuros ou dependentes do backend de identidade.

### Ordem de dependências observada

```text
prova física de voz/capabilities
  -> catálogo agentic canônico e A2UI completo
  -> sync cloud + autenticação + projeções
  -> restore/multi-device/conflitos
  -> WhatsApp e integrações externas
  -> Attention Engine, RAG e ML avançado
```

Essa ordem é uma leitura das dependências registradas no backlog/status; não é
uma autorização para implementar qualquer etapa nesta tarefa.

## Android ↔ Backend Mapping

As linhas abaixo são propostas de compatibilidade, não implementação.

| Android model | Backend model sugerido | Compatibilidade | Transformação necessária | Risco |
|---|---|---|---|---|
| `InstallationIdentity.storeId` | `Business`/`Store` | parcial | tratar como ID inicial de tenant; associar a conta depois | alto: hoje é local e singleton |
| ausência de User | `User`/`Account` | inexistente | introduzir bootstrap/auth sem exigir campo inexistente no primeiro APK | alto |
| `StoreProfileEntity` | `BusinessProfile` | parcial | mapear CSV de módulos/patterns para arrays; preservar default local | alto: não há evento de perfil |
| `BusinessProfile` | `Business` + capabilities | parcial | separar identidade comercial de configuração de runtime | médio/alto |
| `CustomerEntity` | `Customer` | direta para campos atuais | String UUID, name, nullable phone, createdAt | médio: falta tenant/version/update |
| `CreditEntryEntity` | `CreditEntry`/`LedgerEvent` | direta no fato | preservar sinal, ledgerType, provenance, reason e dueAt | alto: saldo depende da soma |
| `CreditAccount` | não existe entidade separada | adapter | derivar por customerId ou introduzir read model compatível | alto |
| venda fiada | `Sale` + `Receivable` | parcial | manter saleId e creditEntryId relacionados | alto |
| pagamento de crédito | `Payment` + ledger event | parcial | mapear operationId/idempotency e método; não criar confirmação automática | alto |
| `PaymentMethod` | enum backend | direta | `cash`, `pix`, `card`, `credit`, `unknown` | médio: PIX é apenas marcação |
| `DirectReceiptEntity` | `Receipt`/cash entry | parcial | manter source/note/operationId | médio |
| `FinancialSummary` | projection/read model | derivada | backend deve reproduzir a fórmula ou entregar fatos equivalentes | alto |
| `ProductEntity` | `Product` | direta no shape básico | String ID, cents, unit, createdAt; adicionar version no adapter | médio |
| `Module`/`BusinessModule` | `BusinessModule` | parcial | normalizar enums e presets no bootstrap | médio |
| `Capability`/`TinoCapabilityId` | `CapabilityGrant`/catalog | parcial | backend fornece autorização; Android continua gate local | alto |
| Pix inexistente | `PixConfiguration` | inexistente | não fazer mapping ainda; novo contrato futuro | alto se for assumido |

## Backend Contract Requirements

### A. Data the backend must support from day one

Baseado somente no que o Android efetivamente grava/consome:

1. tenant/store identificável por `storeId` e device/install identity;
2. customers com ID string, name, nullable phone e timestamps;
3. products, sales, sale items, stock movements;
4. credit entries com sinal, tipo, customerId, referenceId, dueAt, paymentMethod e metadata de ledger;
5. credit payment idempotente por operationId;
6. direct receipts idempotentes;
7. suppliers e compras se o sync cobrir todo o snapshot;
8. domain events com os campos e event types aceitos pelo applier;
9. push acknowledgment, already-processed e rejeições retryable/permanent;
10. pull incremental com cursor;
11. autorização por tenant/device no servidor;
12. eventual bootstrap que entregue perfil de negócio e módulos sem obrigar campos que o Android não conhece;
13. valores monetários inteiros em centavos;
14. timestamps compatíveis com epoch milliseconds no adapter atual.

### B. Data the backend should not add yet

Não adicionar como requisito do primeiro contrato Android sem mudança explícita de produto:

- Pix QR/dinâmico, PSP, txid ou conciliação automática;
- múltiplos businesses por instalação;
- memberships/roles complexos no cliente, que hoje não existem;
- logo/endereço/CPF/CNPJ como campos obrigatórios;
- entidade PaymentEvidence;
- ledger financeiro genérico substituindo os fatos existentes;
- A2UI server-driven fora do catálogo v1;
- campos obrigatórios de email/nickname/documento em Customer;
- exigir `updatedAt`/version nas payloads antigas sem estratégia de default.

### C. Android models that can map directly

`CustomerEntity` básico, `ProductEntity` básico, `SupplierEntity` básico, `SaleEntity`, `SaleItemEntity`, `StockMovementEntity`, `DirectReceiptEntity`, `CreditEntryEntity` e `DomainEventEntity` podem ser transportados quase diretamente, respeitando nomes snake_case do gateway e conversão de enums.

### D. Models requiring adapter/migration

`StoreProfileEntity`/`BusinessProfile`, ausência de User, singleton `default`, capacidades/módulos CSV, `CreditAccount` implícito, `FinancialSummary` derivada, `Payment` representado por crédito negativo, orders/fiscal snapshots e qualquer projeção sem tenant/version precisam de adapter.

### E. Missing stable identifiers

- User/account ID;
- business ID server-side distinto de installation-generated storeId;
- membership ID/role assignment;
- Pix transaction/e2e ID;
- payment evidence ID;
- reconciliation ID;
- version/aggregate revision;
- IDs de linhas compostas para sincronização independente.

### F. Missing created_at / updated_at / version

Comércio, cliente, produto, supplier, sale, order e profile normalmente têm `createdAt` ou `occurredAt`, mas não `updatedAt` nem `version`. Clientes são atualizados por evento, mas a entidade não guarda quando foi atualizada. Produtos mudam preço sem campo de atualização. Perfil tem `createdAt` e `profileVersion`, mas `profileVersion` é versão do formato local, não revisão concorrente.

### G. Login/onboarding gaps

Não há login nem registro remoto; FirstAccess é cadastro local e sua conclusão é presença de linha default. Não há vínculo owner↔business, seleção de negócio ativo ou recuperação após reinstall.

### H. Business registration gaps

Não há documento, endereço, logo, Pix config, status remoto, timezone persistido no profile, legal identity ou business membership. `phone` é o único contato do owner/profile e é obrigatório somente no onboarding local.

### I. Sync risks

- dois aparelhos podem criar o mesmo nome de cliente/produto localmente e violar índices únicos ao aplicar;
- ausência de versionamento deixa update/update e price change sujeitos a last-write/ordem ambígua;
- perfil não entra no outbox;
- storeId local pode ser perdido em reinstalação;
- eventos desconhecidos ficam BLOCKED sem resolução automática;
- o saldo pode divergir se eventos duplicados ou reversões forem aplicados fora das mesmas regras;
- `occurredAt` e ordenação UUID podem divergir entre relógios;
- snapshot restore não inclui algumas entidades de inteligência e não é protocolo de bootstrap cloud.

### J. High-risk couplings

1. `operationId` frequentemente é simultaneamente ID do lançamento, chave de idempotência e referência de mutation gate;
2. saldo de cliente é soma de eventos assinados, não coluna autoritativa;
3. `CreditEntryType.SALE` também carrega ajustes, reversões, disputas e quitações, diferenciados por `ledgerType`;
4. método `pix` no app não significa pagamento confirmado;
5. capabilities têm dois vocabulários (`AgentCapability` e `TinoCapabilityId`) e aliases;
6. `store_profile` singleton `default` mistura perfil, tenant local e onboarding;
7. entidades comerciais não têm tenant field;
8. resolução de entidades depende de nome e índice único, não de ID remoto apenas;
9. UI/A2UI espera preview e confirmação antes de mutation;
10. REST gateway está pronto para paths/event envelope, mas não está conectado na configuração atual.

## Login + Business Bootstrap Contract

Esta é uma proposta mínima futura, adaptada ao estado real, não um contrato existente:

```text
Login/OIDC futuro
  -> backend resolve User
  -> backend retorna businesses autorizados
  -> Android associa/confirmar storeId local
  -> backend retorna active Business/Profile
  -> backend retorna modules/capabilities autorizadas
  -> Android preserva/mescla StoreProfile local
  -> pull inicial por cursor/eventos
  -> Room projections locais
  -> Home
```

O Android já suporta localmente: perfil, módulos, capabilities, Room, eventos, cursor, WorkManager, token Bearer e aplicação de mudanças. Não suporta: login/OIDC, lista de businesses, escolha de business ativo, merge de perfil remoto, token lifecycle e bootstrap remoto.

Compatibilidade mínima: o backend deve aceitar a identidade local existente como device/install context, retornar `store_id` estável e não exigir que o Android já possua `userId` antes do primeiro login. A autorização server-side deve prevalecer sobre `deviceId` enviado pelo cliente.

## Gaps

### Implementado

- domínio local de comércio e caderneta;
- Room v21 e migrações 1→21;
- eventos de domínio/outbox e cursor;
- sync protocol client-side e Worker;
- perfil/módulos/verticals locais;
- agente, confirmação e A2UI locais;
- SecureTokenStore técnico.

### Parcialmente implementado

- REST sync: código existe, build atual usa gateway indisponível;
- ADK: biblioteca/planner proposal existem, orchestrator remoto/externo não;
- onboarding: completo para cadastro local, inexistente para conta/tenant remoto;
- snapshot restore: repository existe, tela de restore não executa o fluxo;
- caderneta por valor via agente: repository existe, cobertura do caminho textual canônico é incompleta;
- modules/capabilities: locais, sem autoridade backend;
- profile sync: StoreProfile não gera evento.

### Planejado/documentado apenas

- Keycloak/OIDC/JWT e endpoints de backend nas specs;
- store memberships/roles server-side;
- projections backend por `store_id`;
- WhatsApp/orders backend, inteligência server-side e hardening descritos em specs;
- Pix/reconciliation se mencionados apenas em documentação.

### Não encontrado

- login/registro/logout/recovery;
- User/account model;
- multi-tenant real/multi-business;
- Pix config/QR/provider/webhook;
- notification access;
- payment evidence/reconciliation;
- generic payment entity;
- tombstone/delete sync;
- entity version/conflict resolver completo.

## Risks

- **Risco crítico de identidade**: `storeId` nasceu localmente e não é autenticado; associá-lo diretamente a um tenant remoto sem bootstrap pode permitir colisão, perda após reinstalação ou vínculo incorreto.
- **Risco crítico de saldo**: qualquer backend que trate `balance` como verdade independente dos lançamentos pode divergir de `TemporalCreditService`/`SharedLedgerProjector`.
- **Risco crítico de pagamento**: PIX é apenas um enum/marca local; tratar como confirmação bancária criaria falso positivo financeiro.
- **Risco crítico de autorização**: capabilities locais controlam UI/agent, mas não substituem autorização do backend.
- **Risco alto de concorrência**: falta de revision/version em Customer/Product/Profile/CreditEntry.
- **Risco alto de contrato**: payloads têm eventos e aliases em mais de um vocabulário.
- **Risco alto de migração**: índices únicos por nome e ausência de foreign keys dificultam merge entre instalações.
- **Risco médio de tempo**: relógio local e timezone default afetam vencido, período financeiro e ordenação.
- **Risco médio de perfil**: mudanças no perfil não estão no outbox e não possuem `updatedAt`.

## Recommended Compatibility Strategy

1. Tratar os eventos e entidades atuais como contrato de compatibilidade, principalmente `DomainEventEntity`, `CreditEntryEntity` e `PaymentMethod`.
2. Criar no backend uma separação explícita entre `User`, `Business`, `Device` e as projeções comerciais, mesmo que o Android inicial ainda só conheça `storeId/deviceId`.
3. Fazer o bootstrap remoto devolver IDs/capabilities sem substituir silenciosamente o `store_profile` local.
4. Manter `amountCents` e sinais atuais; mapear pagamento de crédito como fato de ledger, não como simples update de saldo.
5. Implementar idempotência por `event_id` e `operation_id` no servidor; aceitar reenvio após timeout.
6. Preservar endpoints/envelope já codificados pelo `RestSyncGateway` ou fornecer adapter Android explícito antes de mudar nomes.
7. Introduzir `updatedAt`/revision no backend como extensão compatível e usar defaults/adapters para payloads antigos.
8. Não ativar Pix, conciliação, notification listener ou multi-business por inferência da existência do enum `PIX`.
9. Não mover lógica de autorização/confirmation para o LLM ou para A2UI; manter preview, token, idempotência e autorização separados.
10. Antes de habilitar `TINO_SYNC_BASE_URL`, validar em ambiente de integração todos os event types do `RemoteEventApplier`, conflitos de índices e comportamento de cursor.

## Files Inspected

### Android/build

- `settings.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/tino/app/MainActivity.kt`
- `app/src/main/java/com/tino/app/TinoApplication.kt`
- `app/src/main/java/com/tino/app/TinoApp.kt`
- `app/src/main/java/com/tino/app/TinoNavigation.kt`
- `app/src/main/java/com/tino/app/feature/home/TinoViewModel.kt`

### Identity/security/sync

- `app/src/main/java/com/tino/app/core/common/IdentityProvider.kt`
- `app/src/main/java/com/tino/app/core/common/UuidV7.kt`
- `app/src/main/java/com/tino/app/core/security/SecureTokenStore.kt`
- `app/src/main/java/com/tino/app/core/sync/SyncGateway.kt`
- `app/src/main/java/com/tino/app/core/sync/RestSyncGateway.kt`
- `app/src/main/java/com/tino/app/core/sync/SyncCoordinator.kt`
- `app/src/main/java/com/tino/app/core/sync/SyncWorker.kt`
- `app/src/main/java/com/tino/app/core/sync/SyncScheduler.kt`
- `app/src/main/java/com/tino/app/core/sync/SyncCircuitBreaker.kt`
- `app/src/main/java/com/tino/app/core/sync/RemoteEventApplier.kt`
- `app/src/main/java/com/tino/app/core/sync/CommerceSnapshot.kt`
- `app/src/main/java/com/tino/app/core/sync/InMemoryCloudSyncGateway.kt`

### Room/domain

- `app/src/main/java/com/tino/app/core/database/Entities.kt`
- `app/src/main/java/com/tino/app/core/database/Daos.kt`
- `app/src/main/java/com/tino/app/core/database/TinoDatabase.kt`
- `app/src/main/java/com/tino/app/domain/profile/BusinessProfile.kt`
- `app/src/main/java/com/tino/app/domain/profile/StoreProfileRepository.kt`
- `app/src/main/java/com/tino/app/domain/profile/BusinessContextResolver.kt`
- `app/src/main/java/com/tino/app/domain/commerce/CommerceRepository.kt`
- `app/src/main/java/com/tino/app/domain/commerce/PaymentMethod.kt`
- `app/src/main/java/com/tino/app/domain/commerce/TemporalCredit.kt`
- `app/src/main/java/com/tino/app/domain/commerce/SharedLedger.kt`
- `app/src/main/java/com/tino/app/domain/finance/FinancialSummary.kt`
- `app/src/main/java/com/tino/app/domain/finance/FinancialProjectionRepository.kt`
- `app/src/main/java/com/tino/app/domain/usecase/CommerceQueries.kt`
- `app/src/main/java/com/tino/app/domain/orders/OrderModels.kt`
- `app/src/main/java/com/tino/app/domain/fiscal/FiscalImportCommitService.kt`

### Agent/A2UI/ADK

- `app/src/main/java/com/tino/app/domain/agent/AgentQuery.kt`
- `app/src/main/java/com/tino/app/domain/agent/AgentIntent.kt`
- `app/src/main/java/com/tino/app/domain/agent/TinoCapabilityRegistry.kt`
- `app/src/main/java/com/tino/app/domain/agent/AgenticTextQueryCoordinator.kt`
- `app/src/main/java/com/tino/app/domain/agent/DbFirstReadCapabilities.kt`
- `app/src/main/java/com/tino/app/domain/agent/CanonicalCapabilityHandlers.kt`
- `app/src/main/java/com/tino/app/domain/voice/ToolCalling.kt`
- `app/src/main/java/com/tino/app/domain/voice/MutationSafety.kt`
- `app/src/main/java/com/tino/app/domain/voice/GlobalCommandRouter.kt`
- `app/src/main/java/com/tino/app/core/intelligence/AdkPlanProposal.kt`
- `app/src/main/java/com/tino/app/core/intelligence/AdkModelAdapter.kt`
- `app/src/main/java/com/tino/app/domain/intelligence/IntelligenceRuntime.kt`
- `app/src/main/java/com/tino/app/domain/intelligence/agent/AgentRuntime.kt`
- `app/src/main/java/com/tino/app/interfaceadapter/a2ui/A2uiProtocol.kt`
- `app/src/main/java/com/tino/app/interfaceadapter/a2ui/A2uiActionProtocol.kt`
- `app/src/main/java/com/tino/app/interfaceadapter/a2ui/TinoComponentCatalog.kt`
- `app/src/main/java/com/tino/app/interfaceadapter/a2ui/CommerceActionA2uiMapper.kt`
- `app/src/main/java/com/tino/app/ui/a2ui/TinoA2UiRenderer.kt`

### Schemas/tests/docs used for cross-check

- `app/schemas/com.tino.app.core.database.TinoDatabase/1.json` through `21.json`
- sync, Room, commerce, agent, ADK and A2UI tests under `app/src/test/java`
- `TINO-ARCHITECTURE.md`
- `TINO-PROJECT-STATUS.md`
- `TINO-CAPABILITY-MATRIX.md`
- `specs/SYNC-API.md`
- `specs/TINO-BACKEND-001 — Backend Foundation.md`
- `specs/TINO-BACKEND-002-sync-contracts.md`
- `specs/TINO-BACKEND-003-commerce-projections.md`
- `specs/TINO-BACKEND-006-tino-orchestrator.md`
- `specs/TINO-BACKEND-007-intelligence-data.md`
- `specs/TINO-BACKEND-008-production-hardening.md`
- `TINO-INCOMPLETE-VALIDATION-BACKLOG.md`
- `TINO-CONTINUOUS-EXECUTION.md`
- `TINO-UX-UI-P0-HARDENING.md`
- `specs/PRD.md`
- `specs/TINO-DOCUMENT-INTAKE-001.md`
- `specs/TINO-DOCUMENT-SCANNER-001.md`
- `specs/TINO-BACKEND-005-whatsapp-orders.md`

## Final Status

**BACKEND READINESS: PARTIALLY READY**

Justificativa: o Android já oferece modelos comerciais, Room v21, eventos, idempotência local, cursor, worker, gateway REST e adapters de agente/A2UI suficientes para orientar um backend compatível. Porém, a integração ainda não está operacional no build atual, não existe autenticação de usuário, o tenant é apenas uma identidade local de instalação, o perfil não sincroniza por evento, não há versionamento/conflict model completo e Pix/reconciliação não existem. O backend deve ser desenhado em torno dessas limitações; não há base factual para assumir login, payment intent, confirmação Pix ou multi-business.

## Terminal Summary

1. **O que já existe:** domínio local de clientes, produtos, vendas, estoque, fornecedores, pedidos, fiado/caderneta, pagamentos, ajustes, ledger semântico, Room v21, migrações, eventos/outbox, cursor, WorkManager, agent/ADK local e A2UI.
2. **O que o backend precisa suportar:** store/device scope, clientes/produtos/vendas/estoque/crédito/pagamentos, eventos idempotentes, cents, dueAt, ledger metadata, push/pull/cursor, acknowledgments/rejections e bootstrap de perfil/capabilities.
3. **O que o Android eventualmente terá de adaptar:** User/account, business remoto, login/OIDC, active business, merge de perfil, tenant/version fields, conflict handling e transporte cloud habilitado.
4. **O que não deve ser mudado agora:** entidades/sinais atuais da caderneta, nomes e IDs dos eventos, confirmação humana de mutações, semântica local de saldo, enum PIX como simples método de registro e contrato A2UI v1.
5. **Maiores pontos de risco:** identidade local vs tenant remoto, saldo derivado de eventos, `operationId`/idempotência, falta de versionamento, perfil singleton `default`, ausência de tenant nas entidades e falsa interpretação de PIX como pagamento confirmado.
