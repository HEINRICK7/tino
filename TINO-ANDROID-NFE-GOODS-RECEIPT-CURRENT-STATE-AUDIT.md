# TINO Android — auditoria do estado atual de entrada de mercadoria por NF-e

**Data da auditoria:** 2026-08-30  
**Escopo:** código Android/JVM incluído no build principal, manifest, Gradle e testes locais.  
**Regra:** somente leitura do comportamento existente; nenhuma funcionalidade nova foi implementada.

## 1. Conclusão executiva

O Android é um app Compose em um único módulo `:app`, com Hilt, Room, WorkManager, processamento local de voz/Gemma, A2UI local e domínio comercial local. Os módulos JVM `:tino-fiscal-core` e `:tino-agent-contracts` são dependências do app; `tino-fiscal-service` é um projeto TypeScript separado e não é consumido pelo Android.

Para entrada de mercadoria há dois caminhos diferentes:

1. **Entrada manual funcional:** produto existente + quantidade inteira + custo unitário em centavos + fornecedor opcional; grava compra recebida, item de compra, movimentação e eventos de outbox no Room.
2. **Importação visual de DANFE parcial:** câmera ou foto → retificação/crop → ML Kit OCR local → linhas `ImportedProduct` → tela de revisão. Não grava produto, fornecedor ou estoque. O botão seguinte envia o usuário para a entrada manual.

Também existe um **núcleo fiscal mais completo** em `:tino-fiscal-core` e um `FiscalImportCommitService` no app, com XML, matching, embalagem, confirmação humana, criação local de fornecedor/produto, idempotência documental e escrita transacional. Contudo, a busca de referências de produção mostra que esse caminho não está ligado às telas atuais: seu uso aparece no próprio código/testes, mas não há ViewModel, rota ou ação de UI que o execute.

O transporte cloud também não está ativo na build auditada: [`AppModule.kt`](app/src/main/java/com/tino/app/core/di/AppModule.kt:263) injeta `UnavailableSyncGateway` porque [`app/build.gradle.kts`](app/build.gradle.kts:17) define `TINO_SYNC_BASE_URL` como string vazia.

## 2. Arquitetura e módulos

### Módulos Gradle

| Módulo | Estado | Conteúdo relevante |
|---|---|---|
| `:app` | Implementado | Android Compose, Hilt, Room, WorkManager, câmera, ML Kit, domínio comercial, sync e UI |
| `:tino-fiscal-core` | Implementado como biblioteca JVM | modelos fiscais, parser XML seguro, matching, embalagem, preview, validação de commit e mapper A2UI fiscal |
| `:tino-agent-contracts` | Implementado como biblioteca JVM | contratos do agente de crédito; não contém contrato de NF-e |
| `tino-fiscal-service` | Projeto separado | TypeScript; não está em `settings.gradle.kts` e não é dependência Android |

Não há módulos Android separados por feature. A organização é por packages dentro de `:app`:

```text
com.tino.app
├── core/database       Room entities, DAOs, database e migrações
├── core/di             bindings Hilt
├── core/security       token cifrado em Android Keystore
├── core/sync           outbox, REST, cursor, WorkManager e applier
├── core/speech         SpeechRecognizer, Gemma e MediaPipe
├── domain/commerce     CommerceRepository, estoque, compras, vendas e fornecedor
├── domain/fiscal       parser legado e adaptador de commit fiscal
├── domain/usecase      comandos/consultas
├── feature/fiscal      câmera, upload, retificação e OCR
├── feature/home        TinoViewModel
├── feature/voice       ViewModels de voz/agente
├── interfaceadapter/a2ui
└── ui                  Compose, componentes e tema
```

Fluxo arquitetural observado:

```text
Compose screens
  → TinoViewModel / ViewModels de voz
  → use cases ou CommerceRepository
  → DAOs Room
  → entidades operacionais + DomainEventEntity na mesma transação
  → WorkManager → SyncCoordinator → SyncGateway
```

No fluxo fiscal visual, a cadeia é:

```text
FiscalFoundScreen
  → DocumentScannerScreen ou DocumentUploadScreen
  → processDocumentFile/processDocumentUri
  → DocumentVisionPort / MlKitDanfeVisionAdapter
  → DanfeProductMapper
  → ProductImportResult
  → FiscalReviewScreen
  → entrada manual
```

## 3. Produtos, estoque, compras e fornecedores

### Modelo persistido

[`ProductEntity`](app/src/main/java/com/tino/app/core/database/Entities.kt:6) contém somente:

| Campo | Representação |
|---|---|
| identidade | `id: String` |
| nome | `name: String`, único |
| preço de venda | `priceCents: Long` |
| unidade | `unit: String`, criado hoje como `"un"` |
| criação | `createdAt: Long` |

Não há GTIN/EAN, código interno, código do fornecedor, custo corrente, fornecedor ou NCM em `ProductEntity`.

[`ProductSummary`](app/src/main/java/com/tino/app/core/database/Daos.kt:9) expõe `id`, nome, preço, unidade e estoque calculado. A fonte de verdade do estoque exibido é a soma de `StockMovementEntity.quantityDelta` por produto ([`ProductDao.observeAll`](app/src/main/java/com/tino/app/core/database/Daos.kt:43)).

### Movimentação e entrada

[`StockMovementEntity`](app/src/main/java/com/tino/app/core/database/Entities.kt:46) contém `productId`, `quantityDelta: Int`, `reason`, `referenceId` e timestamp. Portanto, o estoque operacional só suporta quantidade inteira.

[`PurchaseEntity`](app/src/main/java/com/tino/app/core/database/Entities.kt:137) tem `supplierId`, `PurchaseStatus` (`DRAFT`, `ORDERED`, `RECEIVED`, `COMPLETED`), total em centavos e datas. [`PurchaseItemEntity`](app/src/main/java/com/tino/app/core/database/Entities.kt:150) usa quantidade inteira e custo unitário em centavos.

A entrada manual em [`CommerceRepository.registerStockReceipt`](app/src/main/java/com/tino/app/domain/commerce/CommerceRepository.kt:1006) faz, numa transação:

- valida produto, fornecedor, quantidade `> 0` e custo `>= 0`;
- cria `PurchaseEntity` como `RECEIVED`;
- cria `PurchaseItemEntity`;
- cria `StockMovementEntity` com razão `purchase_receipt`;
- cria eventos `purchase.created` e `stock.received`;
- agenda sync após o commit.

### Produtos, custo e unidade na UX

- [`NewProductScreen`](app/src/main/java/com/tino/app/TinoApp.kt:1989) pede nome, preço e estoque inicial; o repositório grava unidade fixa `"un"` ([`CommerceRepository.createProduct`](app/src/main/java/com/tino/app/domain/commerce/CommerceRepository.kt:215)).
- O estoque inicial também é `Int` e gera `initial_stock`.
- [`ProductDetailScreen`](app/src/main/java/com/tino/app/TinoApp.kt:1232) exibe preço e estoque em “unidades”; não exibe custo, GTIN ou código.
- [`AdjustStockScreen`](app/src/main/java/com/tino/app/TinoApp.kt:2057) declara ajuste manual indisponível e encaminha para entrada de mercadoria.
- [`StockEntryScreen`](app/src/main/java/com/tino/app/TinoApp.kt:2076) busca produto por nome exato, aceita quantidade inteira, custo unitário em texto monetário e fornecedor opcional por nome exato.
- O ViewModel converte quantidade com `toInt()` e custo com `BigDecimal` para centavos ([`TinoViewModel.receiveStockAndWait`](app/src/main/java/com/tino/app/feature/home/TinoViewModel.kt:535)).

### Fornecedor

[`SupplierEntity`](app/src/main/java/com/tino/app/core/database/Entities.kt:64) tem nome, telefone e `taxId` opcional. A tela atual de fornecedores ([`SuppliersScreen`](app/src/main/java/com/tino/app/TinoApp.kt:2566)) cadastra apenas nome e telefone; não permite informar CNPJ/CPF. Há persistência e DAO, mas não há fornecedor remoto ativo.

### Fonte de verdade

| Dado | Fonte atual |
|---|---|
| produto exibido | Room `products` |
| estoque atual | projeção local pela soma de `stock_movements` |
| entrada manual | Room + eventos locais |
| compra/pedido | Room `purchases`/`purchase_items` |
| fornecedor | Room `suppliers` |
| sincronização | outbox `domain_events`, atualmente sem gateway cloud ativo |
| preço de venda | Room `products.priceCents` |
| custo histórico fiscal | somente no caminho fiscal, `product_purchase_history` |

## 4. Fiscal, NF-e, DANFE, XML e scanner

### O que existe no `:app`

Há dois conjuntos de modelos:

- [`domain/fiscal/FiscalModels.kt`](app/src/main/java/com/tino/app/domain/fiscal/FiscalModels.kt:5): `FiscalLineItem`, `ParsedFiscalDocument`, `FiscalDocumentDraft` e `FiscalResult`.
- [`domain/fiscal/NfeXmlParser.kt`](app/src/main/java/com/tino/app/domain/fiscal/NfeXmlParser.kt:10): parser XML local simples, extraindo `infNFe/@Id`, primeiro `xNome`, `vNF`, `det/prod`, `cProd`, `cEAN`, `xProd`, `NCM`, `uCom`, `qCom` e `vUnCom`.

O parser legado usa `BigDecimal`, bloqueia DOCTYPE/entidades externas e está coberto por teste, mas não aparece ligado às telas atuais.

Há também [`FiscalImportCommitService`](app/src/main/java/com/tino/app/domain/fiscal/FiscalImportCommitService.kt:48), que adapta o núcleo fiscal a Room. Quando chamado com um plano validado, ele pode criar fornecedor novo, produto novo, mapping fornecedor-produto, compra recebida, movimentação, histórico de compra, eventos fiscais e marcador `fiscal_imports`. A operação é transacional e o documento/hash é a fronteira de idempotência.

### O que existe no `:tino-fiscal-core`

O núcleo contém:

- [`FiscalXmlParser`](tino-fiscal-core/src/main/kotlin/com/tino/fiscal/core/FiscalXmlParser.kt:17): XML para `CanonicalFiscalDocument`, com chave, emitente/destinatário, itens, impostos, totais, parcelas, origem e hash SHA-256.
- [`FiscalMatching`](tino-fiscal-core/src/main/kotlin/com/tino/fiscal/core/FiscalMatching.kt:1): `FiscalSupplierCandidate`, `FiscalProductCandidate`, `FiscalProductMatcher`, `ProductResolution`, candidatos, métodos de matching e `FiscalImportPreview`.
- [`FiscalPackaging`](tino-fiscal-core/src/main/kotlin/com/tino/fiscal/core/FiscalPackaging.kt:10): conversão de unidade/embalagem somente quando existe mapeamento confirmado; caso contrário, `RequiresConfirmation`.
- [`FiscalCommit`](tino-fiscal-core/src/main/kotlin/com/tino/fiscal/core/FiscalCommit.kt:35): confirmação humana, plano de commit e validação fail-closed; exige decisão de fornecedor/item e converte valores/quantidades sem arredondamento.
- [`DocumentIntake`](tino-fiscal-core/src/main/kotlin/com/tino/fiscal/core/DocumentIntake.kt:5): `ImportedProduct`, `ProductImportPort`, `DocumentVisionPort`, mapper restrito de tabela DANFE e estados da captura.
- [`FiscalImportA2ui`](tino-fiscal-core/src/main/kotlin/com/tino/fiscal/core/FiscalImportA2ui.kt:5): mensagem declarativa fiscal própria, com itens existing/new/ambiguous/packaging e ações somente `REVIEW`/`CANCEL`.

Esses modelos existem e têm testes no módulo JVM. Não são DTOs HTTP e não há endpoint Android de NF-e que os consuma.

### Pipeline visual realmente conectado

O manifest declara câmera e internet; o Gradle inclui CameraX e ML Kit. [`DocumentScannerScreen`](app/src/main/java/com/tino/app/feature/fiscal/DocumentScannerScreen.kt:75) usa `LifecycleCameraController`, `ImageCapture` e `ImageAnalysis`, pede permissão de câmera, aplica gate de qualidade e salva foto temporária no cache.

[`MlKitDanfeVisionAdapter`](app/src/main/java/com/tino/app/feature/fiscal/MlKitDanfeVisionAdapter.kt:35) executa reconhecimento de texto no aparelho e passa as linhas para `DanfeProductMapper`. [`DocumentImportProcessor`](app/src/main/java/com/tino/app/feature/fiscal/DocumentImportProcessor.kt:20) faz resize/retificação/crop e calcula hash da imagem processada.

O resultado é `ProductImportResult.Success`, `NeedsReview` ou `Unavailable`. A tela [`FiscalReviewScreen`](app/src/main/java/com/tino/app/TinoApp.kt:2215) apenas apresenta os itens. Em caso de sucesso/revisão, a ação é explicitamente `CONTINUAR ENTRADA MANUAL` ou `CORRIGIR ENTRADA MANUALMENTE` ([`TinoApp.kt`](app/src/main/java/com/tino/app/TinoApp.kt:2272)). Não há confirmação fiscal que chame `FiscalImportCommitService`.

### O que não foi encontrado no caminho Android

- leitura de XML por picker ou deep link;
- leitura de QR Code/código de barras da chave de acesso;
- biblioteca de barcode/ZXing/ML Kit barcode scanning;
- captura ou validação de chave de acesso de 44 dígitos pela câmera;
- consulta a SEFAZ, Serpro ou serviço fiscal remoto;
- OCR de fornecedor, impostos, vencimentos ou total fiscal no pipeline DANFE conectado;
- Product Matching conectado à tela visual;
- confirmação de `GoodsReceipt` por NF-e na UI.

Os campos `accessKey`/`gtin` existem em modelos fiscais e persistência, mas isso não constitui scanner ou integração ativa.

## 5. Fluxo de navegação atual

As rotas estão em [`TinoNavigation.kt`](app/src/main/java/com/tino/app/TinoNavigation.kt:238) e são renderizadas em [`TinoNavigation.kt`](app/src/main/java/com/tino/app/TinoNavigation.kt:802):

```text
Home / Produtos
  → Nova entrada / Entrada de mercadoria
  → produto + quantidade inteira + custo + fornecedor opcional
  → grava localmente e volta a Produtos

Mais → Notas ou Configurações → Importar nota fiscal
  → Ler nota
     ├── Escanear nota → DocumentCamera → captura → FiscalReview
     ├── Escolher uma foto → DocumentUpload → FiscalReview
     └── Preencher manualmente → StockEntry
  → FiscalReview
     ├── sucesso/revisão → entrada manual
     └── indisponível → tentar outra foto
```

Pontos naturais para o futuro “Dar entrada por NF-e” já estão presentes em `FiscalFoundScreen`, acessível por “Notas”, “Importar nota fiscal” e pela ação de entrada em produtos. A auditoria não alterou nenhum deles.

Não existe uma tela dedicada de histórico/detalhe de importação fiscal, status de recuperação, preview remoto ou recebimento assíncrono.

## 6. Mapa de conceitos do futuro fluxo

| Conceito | Estado no Android | Evidência |
|---|---|---|
| `NfeAccessKey` | **PARCIAL** — `String?` sem value object | `ParsedFiscalDocument.accessKey`, `CanonicalFiscalDocument.accessKey`, `FiscalImportEntity.accessKey` |
| `GoodsReceipt` | **PARCIAL** — compra `RECEIVED` + movimentos; não há agregado dedicado | `PurchaseEntity`, `PurchaseStatus`, `FiscalImportCommitService` |
| `GoodsReceiptPreview` | **PARCIAL** — `FiscalImportPreview`, mas não conectado à UI/HTTP | `tino-fiscal-core/FiscalMatching.kt:296` |
| `GoodsReceiptItem` | **PARCIAL** — `FiscalCommitItemPlan`/`PurchaseItemEntity`; não há tipo dedicado | `FiscalCommit.kt:44`, `Entities.kt:150` |
| `InventoryMovement` | **EXISTE** com outro nome | `StockMovementEntity` |
| `ProductResolution` | **EXISTE** no núcleo fiscal | `FiscalMatching.kt:71` |
| `ProductCandidate` | **EXISTE** com variantes fiscais | `FiscalProductCandidate`, `ProductMatchCandidate` |
| `PackagingConversion` | **EXISTE** com outro nome | `ProductPackaging`, `FiscalPackagingResolver` |
| `SupplierProductMapping` | **EXISTE** | `SupplierProductMappingEntity` e `SupplierProductMapping` |
| `FiscalStatus` | **PARCIAL** — somente `FiscalImportStatus.COMMITTED`; sem estados de documento | `Entities.kt:76` |
| `RetrievalStatus` | **NÃO EXISTE** | nenhum tipo/campo equivalente encontrado |
| `GoodsReceiptStatus` | **PARCIAL** — `PurchaseStatus` serve para compra, não recebimento fiscal | `Entities.kt:137` |
| `ImportedProduct` | **EXISTE** | `tino-fiscal-core/DocumentIntake.kt:12` |
| `ProductImportPort` | **EXISTE como contrato**, sem adapter HTTP | `tino-fiscal-core/DocumentIntake.kt:48` |
| integração A2UI fiscal | **PARCIAL** — mapper próprio/testado, sem origem remota | `FiscalImportA2ui.kt:77` |

## 7. Rede, backend e autenticação

### Cliente e configuração

Não há Retrofit, Ktor, OkHttp, GraphQL, Moshi, Gson ou kotlinx serialization no app. O único cliente HTTP é [`RestSyncGateway`](app/src/main/java/com/tino/app/core/sync/RestSyncGateway.kt:12), baseado em `HttpURLConnection`.

Configuração encontrada:

- `compileSdk 36`, `minSdk 26`, `targetSdk 35`;
- sem `productFlavors` ou configuração por ambiente;
- `TINO_BUILD_CHANNEL = "pilot"`;
- `TINO_BUILD_ID = "0.1.0-pilot.1"`;
- `TINO_SYNC_BASE_URL = ""`;
- HTTPS obrigatório no construtor de `RestSyncGateway`;
- `usesCleartextTraffic="false"` no manifest.

### Todos os endpoints consumidos pelo Android

O resultado da busca por chamadas HTTP/annotations/paths encontrou somente estes dois endpoints. Eles são genéricos de sync; não são endpoints de NF-e.

| Método | Path | Request | Response | Camada consumidora | Tela/funcionalidade | Status |
|---|---|---|---|---|---|---|
| `POST` | `/v1/sync/events` | JSON `{events: [{event_id, store_id, device_id, aggregate_id, event_type, schema_version, occurred_at, payload}]}` | JSON `acknowledged_event_ids`, `already_processed_event_ids`, opcional `rejected[]` com `event_id`, `code`, `retryable`, `message` | `RestSyncGateway.push` → `SyncCoordinator` | outbox de vendas, estoque, compras, fiscal e demais eventos | **Infraestrutura implementada, inativa na build atual** |
| `GET` | `/v1/sync/changes?limit=100` ou `?cursor=...&limit=100` | nenhum body | JSON `{changes: [evento], next_cursor}` | `RestSyncGateway.pull` → `SyncCoordinator` → `RemoteEventApplier` | projeção local multi-device | **Infraestrutura implementada, inativa na build atual** |

Não há classes `RequestDto`/`ResponseDto`; a serialização é manual com `JSONObject`/`JSONArray`. Não há endpoint `POST /goods-receipts`, `GET /goods-receipts/{id}`, `GET /status`, `POST /document-intake` ou equivalente no código Android.

### Headers e autenticação

Para cada request REST o gateway envia:

- `Accept: application/json`;
- `X-Request-Id` novo via `UuidV7.new()`;
- `Authorization: Bearer <token>` somente se um token já estiver salvo;
- `Content-Type: application/json` quando há body.

[`SecureTokenStore`](app/src/main/java/com/tino/app/core/security/SecureTokenStore.kt:16) cifra uma string com AES/GCM usando Android Keystore e a guarda em `SharedPreferences`. Não existe login, registro, refresh, logout, JWT parsing, user ID, membership, tenant remoto ou fluxo que salve o token. Portanto, autenticação é **parcial: armazenamento/transporte de token técnico, sem bootstrap de sessão**.

### Retry, timeout e erros

- connect timeout: 10 s;
- read timeout: 15 s;
- `SyncCircuitBreaker`: 3 falhas consecutivas, abertura por 30 s;
- WorkManager: rede conectada, backoff exponencial de 30 s, trabalho único `tino-sync`;
- `408/429/5xx`: `SyncUnavailableException`, retry;
- `401/403`: `SyncAuthRequiredException`, bloqueia eventos locais;
- demais não-2xx: `SyncPermanentException`, rejeita;
- resposta limitada a 1.000.000 caracteres.

Há estados `PENDING`, `SYNCING`, `SYNCED`, `FAILED`, `REJECTED`, `BLOCKED`, `CONFLICT` para eventos. O `RemoteEventApplier` entende eventos de produto, fornecedor, compra, estoque, fiscal, vendas e crédito, mas isso é aplicação de eventos, não API de recursos.

## 8. Idempotência, persistência e serialização

Há duas formas distintas:

1. **Outbox/eventos:** `eventId` é chave primária; o gateway envia eventos e combina acknowledgements com `already_processed_event_ids`. O Android não envia header `Idempotency-Key`.
2. **Mutação local de agente:** `MutationOperationEntity` possui `operationId` e `idempotencyKey` únicos, com reserva/commit/release. Isso protege operações do agente no Room; não é contrato HTTP.

O commit fiscal usa o `documentId` e `documentHashSha256` em `fiscal_imports` como idempotência documental. Também não envia `Idempotency-Key` para backend.

Persistência local relevante em [`TinoDatabase`](app/src/main/java/com/tino/app/core/database/TinoDatabase.kt:36): versão **26**, `exportSchema=true`, migrações 1→26, com tabelas de produtos, movimentos, compras, fornecedores, imports fiscais, mappings, histórico de compras e eventos. O documento XML original fica como BLOB em `fiscal_imports`; no applier remoto o XML fica deliberadamente vazio porque não é enviado no payload de sync ([`RemoteEventApplier.kt`](app/src/main/java/com/tino/app/core/sync/RemoteEventApplier.kt:275)).

## 9. Acoplamentos que podem pertencer ao backend

Relatados, sem correção nesta auditoria:

- parsing completo de NF-e e decisão de validade do XML no `:tino-fiscal-core`;
- matching de fornecedor/produto por CNPJ, GTIN, código, alias e similaridade;
- decisão de produto novo/existente/ambíguo;
- resolução de embalagem e conversão fiscal → estoque;
- validação de confirmação humana e cálculo exato de centavos;
- criação automática de fornecedor/produto mediante confirmação;
- decisão local do status `COMMITTED` e geração de eventos fiscais;
- OCR/DANFE executado no aparelho;
- nenhum acesso Android direto a serviço externo fiscal foi encontrado.

O código contém salvaguardas importantes — confirmação humana, fail-closed, hash e transação —, mas elas não significam que o backend já esteja integrado.

## 10. Backend readiness — respostas objetivas

| Pergunta | Resposta | Evidência/limite |
|---|---|---|
| A. Pronto para fluxo assíncrono/preview? | **PARCIALMENTE** | há `FiscalImportPreview` e estados locais; não há cliente/estado HTTP de preview/status |
| B. POST + GET/status? | **PARCIALMENTE** | há POST/GET genéricos de sync; não há POST + GET/status de goods receipt/NF-e |
| C. Idempotency key? | **PARCIALMENTE, somente local** | existe para mutações do agente e documento fiscal; nenhum header/DTO HTTP |
| D. Erros de negócio? | **PARCIALMENTE** | rejeições de sync com `code/retryable/message` e exceções locais; sem envelope de erro de API de negócio |
| E. loading/retry/error? | **SIM, localmente** | Compose possui loading/error/retry; WorkManager e circuit breaker cobrem sync |
| F. BigDecimal sem Double? | **SIM para valores fiscais/monetários** | fiscal core usa `BigDecimal`; operação usa `Long` em centavos. `Double` existe em confiança/recomendações, não como dinheiro |
| G. Quantidade decimal? | **PARCIAL** | fiscal core preserva `BigDecimal`, mas commit exige inteiro e Room/entrada manual usam `Int` |
| H. Feature flag/environment? | **PARCIAL** | `BuildConfig` tem canal, ID e base URL; não há flavors ou feature-flag service |
| I. Autenticação compatível com backend? | **PARCIAL/indeterminada** | só Bearer opcional + Keystore; não há login/refresh/claims ou contrato OIDC |
| J. Scanner de chave NF-e? | **NÃO** | há CameraX/OCR de linhas DANFE; não há barcode/QR/key scanner |

## 11. SERPRO

Foi feita busca no Android por `SERPRO`, `Consumer Key`, `Consumer Secret`, OAuth/OIDC, certificados e segredos. Não foram encontrados **Consumer Key SERPRO** nem **Consumer Secret SERPRO** no Android.

O estado atual não deve receber essas credenciais. Qualquer integração fiscal/Serpro deve permanecer atrás do backend, sem segredo de consumidor no APK.

## 12. Observações sobre documentação existente

`TINO-ANDROID-BACKEND-DISCOVERY.md` e as specs foram tratados apenas como contexto, não como prova. Há divergência verificável: o documento anterior registra Room versão 21, enquanto o código atual declara versão 26 em [`TinoDatabase.kt`](app/src/main/java/com/tino/app/core/database/TinoDatabase.kt:36). Para o alinhamento Android/backend, este relatório deve ser lido junto do código da revisão auditada.

## 13. Artefatos de código mais relevantes

- [`app/build.gradle.kts`](app/build.gradle.kts)
- [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml)
- [`app/src/main/java/com/tino/app/core/database/Entities.kt`](app/src/main/java/com/tino/app/core/database/Entities.kt)
- [`app/src/main/java/com/tino/app/core/database/Daos.kt`](app/src/main/java/com/tino/app/core/database/Daos.kt)
- [`app/src/main/java/com/tino/app/core/database/TinoDatabase.kt`](app/src/main/java/com/tino/app/core/database/TinoDatabase.kt)
- [`app/src/main/java/com/tino/app/domain/commerce/CommerceRepository.kt`](app/src/main/java/com/tino/app/domain/commerce/CommerceRepository.kt)
- [`app/src/main/java/com/tino/app/domain/fiscal/FiscalImportCommitService.kt`](app/src/main/java/com/tino/app/domain/fiscal/FiscalImportCommitService.kt)
- [`app/src/main/java/com/tino/app/feature/fiscal/DocumentScannerScreen.kt`](app/src/main/java/com/tino/app/feature/fiscal/DocumentScannerScreen.kt)
- [`app/src/main/java/com/tino/app/feature/fiscal/MlKitDanfeVisionAdapter.kt`](app/src/main/java/com/tino/app/feature/fiscal/MlKitDanfeVisionAdapter.kt)
- [`app/src/main/java/com/tino/app/TinoApp.kt`](app/src/main/java/com/tino/app/TinoApp.kt)
- [`app/src/main/java/com/tino/app/TinoNavigation.kt`](app/src/main/java/com/tino/app/TinoNavigation.kt)
- [`app/src/main/java/com/tino/app/core/sync/RestSyncGateway.kt`](app/src/main/java/com/tino/app/core/sync/RestSyncGateway.kt)
- [`app/src/main/java/com/tino/app/core/sync/SyncCoordinator.kt`](app/src/main/java/com/tino/app/core/sync/SyncCoordinator.kt)
- [`tino-fiscal-core/src/main/kotlin/com/tino/fiscal/core/FiscalXmlParser.kt`](tino-fiscal-core/src/main/kotlin/com/tino/fiscal/core/FiscalXmlParser.kt)
- [`tino-fiscal-core/src/main/kotlin/com/tino/fiscal/core/FiscalMatching.kt`](tino-fiscal-core/src/main/kotlin/com/tino/fiscal/core/FiscalMatching.kt)
- [`tino-fiscal-core/src/main/kotlin/com/tino/fiscal/core/FiscalCommit.kt`](tino-fiscal-core/src/main/kotlin/com/tino/fiscal/core/FiscalCommit.kt)
