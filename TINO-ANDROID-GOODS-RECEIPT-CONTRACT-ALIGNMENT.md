# TINO Android — Goods Receipt / Backend Contract Alignment

Status: `A0–A14 PASS — checkpoint Android concluído`

Data: 2026-08-30  
Backend checkpoint: `4737fd4 feat: align goods receipt api with android contract`

## Autoridade

- Contrato: `/home/carlos-henrique/Documentos/workspace/backend-tino/docs/contracts/TINO-ANDROID-GOODS-RECEIPT-API.md`
- Evidência backend: `/home/carlos-henrique/Documentos/workspace/backend-tino/docs/evidence/NFE-ANDROID-CONTRACT-ALIGNMENT-EVIDENCE.md`

O Android segue o contrato real em `snake_case`, sem endpoints ou enums adicionais. O backend continua sendo a única fronteira SERPRO; nenhum segredo, token SERPRO, URL SERPRO ou raw fiscal é enviado ao app.

## Checklist

- [x] A0 — checkpoint backend, contrato e evidência lidos; bloqueio removido.
- [x] A1 — boundary HTTP HTTPS com Bearer JWT armazenado no `SecureTokenStore`.
- [x] A2 — DTOs tipados para Nfe, preview, Product Search, confirmação, resultado e erros estáveis.
- [x] A3 — Room `26→27` aditiva; valores decimais remotos preservados como texto decimal exato.
- [x] A4 — chave de acesso normalizada e validada com dígito verificador.
- [x] A5 — máquina de estados para consulta, espera, revisão, confirmação e falhas.
- [x] A6 — preview rico exibido antes de qualquer confirmação.
- [x] A7 — `NEEDS_REVIEW` usa Product Search e exige seleção humana.
- [x] A8 — conversão de embalagem é explícita, positiva e nunca inventada pelo Android.
- [x] A9 — confirmação usa `Idempotency-Key` persistido por operação lógica.
- [x] A10 — resultado autoritativo é projetado no Room; não chama `registerStockReceipt()`.
- [x] A11 — replay e reconciliação por `receipt_id`; sem segunda mutação local.
- [x] A12 — entrada manual continua `UI → Room → InventoryMovement → outbox → sync` e offline.
- [x] A13 — testes, build, lint, scan manual e backend checkpoint executados.
- [x] A14 — E2E instrumental executado no AVD API 35; manual offline, projeção NF-e e fluxo HTTP real passaram.

## Implementação principal

- `app/src/main/java/com/tino/app/core/network/GoodsReceiptApi.kt`
- `app/src/main/java/com/tino/app/core/network/BackendNetwork.kt`
- `app/src/main/java/com/tino/app/domain/receiving/GoodsReceiptModels.kt`
- `app/src/main/java/com/tino/app/domain/receiving/GoodsReceiptRepository.kt`
- `app/src/main/java/com/tino/app/feature/receiving/GoodsReceiptViewModel.kt`
- `app/src/main/java/com/tino/app/feature/receiving/GoodsReceiptScreens.kt`
- `app/src/main/java/com/tino/app/core/database/Entities.kt`
- `app/src/main/java/com/tino/app/core/database/Daos.kt`
- `app/src/main/java/com/tino/app/core/database/TinoDatabase.kt`

## Garantias de segurança

- A confirmação remota não cria `StockMovementEntity`, `DomainEventEntity` nem outbox local.
- A projeção grava `receipt_id`, linhas, produto remoto/local, unidade, quantidade e custo decimal sem `Int`, `Double` ou arredondamento.
- O `Idempotency-Key` é criado uma vez, persistido, reutilizado em retry e recuperável após recriação do repositório/processo.
- O resultado de confirmação é retornado imediatamente; `GET /goods-receipts/{receiptId}` é o caminho de reconciliação.
- Product Search só seleciona produto existente; produto novo só é enviado após ação humana explícita.
- A projeção é filtrada pelo `businessId`; nenhum header de tenant é criado.

## Fiscal-core legado

- `KEEP`: scanner/OCR e importação manual offline existentes, até haver substituição validada.
- `REPURPOSE`: componentes puros de revisão/validação que possam servir à entrada manual, sem conectá-los ao fluxo NF-e remoto.
- `DEPRECATE`: `FiscalImportCommitService`, `FiscalImportEntity` e fluxo `FiscalImport*` quando usados para representar NF-e conectada; eles não são usados pela nova confirmação backend.
- `REMOVE_LATER`: parser/raw/canonical fiscal local e telas de foto/OCR somente após E2E remoto comprovado e decisão explícita de produto.

## Evidência executada

- `gradle :app:testDebugUnitTest --no-daemon` — PASS.
- Testes específicos de contrato, erros, autenticação, migration, decimal, projeção, retry e idempotência — PASS.
- `gradle :app:lintDebug --no-daemon` — PASS.
- `gradle :app:assembleDebug --no-daemon` — PASS; APK gerado em `app/build/outputs/apk/debug/app-debug.apk`.
- `backend-tino/./gradlew test --no-daemon` no checkpoint `4737fd4` — PASS.
- Scan manual de padrões de chave privada/API key — nenhum match.
- `gradle :app:assembleDebugAndroidTest --no-daemon` — PASS.
- `gradle :app:connectedDebugAndroidTest --no-daemon` — PASS em `tino-api35(AVD) - 15`, 3 testes, 0 falhas; 1 teste parametrizado foi `SKIPPED` sem credenciais.
- XML de resultado: `app/build/outputs/androidTest-results/connected/debug/TEST-tino-api35(AVD) - 15-_app-.xml`.
- `GoodsReceiptBackendHttpPhysicalTest#authenticatedBackendFlowRetrievesPreviewsConfirmsAndReconciles` — PASS contra o backend Docker reconstruído a partir de `4737fd4`, via proxy HTTPS local e AVD (`retrieve → preview → confirm → GET receipt`), verificando decimal preservado e ausência de movimento/evento local.
- O primeiro E2E foi bloqueado por uma imagem Docker anterior ao checkpoint; a imagem foi reconstruída e o mesmo teste passou no runtime correto.

## Estado do worktree

O worktree já estava com alterações e artefatos não relacionados antes desta retomada. Eles foram preservados; portanto não é seguro declarar `git status` limpo nesta rodada.
