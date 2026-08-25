package com.tino.app.domain.fiscal

import androidx.room.withTransaction
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.database.DomainEventEntity
import com.tino.app.core.database.FiscalImportDao
import com.tino.app.core.database.FiscalImportEntity
import com.tino.app.core.database.FiscalImportStatus
import com.tino.app.core.database.ProductEntity
import com.tino.app.core.database.ProductPurchaseHistoryDao
import com.tino.app.core.database.ProductPurchaseHistoryEntity
import com.tino.app.core.database.PurchaseDao
import com.tino.app.core.database.PurchaseEntity
import com.tino.app.core.database.PurchaseItemEntity
import com.tino.app.core.database.PurchaseStatus
import com.tino.app.core.database.StockMovementDao
import com.tino.app.core.database.StockMovementEntity
import com.tino.app.core.database.SupplierDao
import com.tino.app.core.database.SupplierEntity
import com.tino.app.core.database.SupplierProductMappingDao
import com.tino.app.core.database.SupplierProductMappingEntity
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.sync.SyncScheduler
import com.tino.fiscal.core.CanonicalFiscalDocument
import com.tino.fiscal.core.FiscalCommitItemPlan
import com.tino.fiscal.core.FiscalCommitValidationResult
import com.tino.fiscal.core.FiscalImportCommitPlan
import com.tino.fiscal.core.FiscalImportCommitValidator
import com.tino.fiscal.core.FiscalImportConfirmation
import com.tino.fiscal.core.FiscalImportPreview
import com.tino.fiscal.core.FiscalSupplierCommitPlan
import org.json.JSONObject
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FiscalImportCommitResult {
    data class Committed(
        val operationId: String,
        val purchaseId: String,
        val supplierId: String,
        val alreadyCommitted: Boolean,
    ) : FiscalImportCommitResult

    data class Rejected(val reasons: List<String>) : FiscalImportCommitResult
}

/**
 * Room adapter for the pure fiscal commit plan. All operational writes and
 * their outbox events share one transaction; the fiscal document is the
 * idempotency boundary.
 */
@Singleton
class FiscalImportCommitService @Inject constructor(
    private val database: TinoDatabase,
    private val fiscalImportDao: FiscalImportDao,
    private val supplierDao: SupplierDao,
    private val purchaseDao: PurchaseDao,
    private val stockMovementDao: StockMovementDao,
    private val supplierProductMappingDao: SupplierProductMappingDao,
    private val productPurchaseHistoryDao: ProductPurchaseHistoryDao,
    private val identityProvider: IdentityProvider,
    private val syncScheduler: SyncScheduler,
) {
    private val validator = FiscalImportCommitValidator()
    suspend fun commit(
        document: CanonicalFiscalDocument,
        preview: FiscalImportPreview,
        confirmation: FiscalImportConfirmation,
    ): FiscalImportCommitResult {
        return when (val validation = validator.validate(document, preview, confirmation)) {
            is FiscalCommitValidationResult.Rejected -> FiscalImportCommitResult.Rejected(validation.reasons)
            is FiscalCommitValidationResult.Valid -> commitPlan(document, validation.plan)
        }
    }

    private suspend fun commitPlan(
        document: CanonicalFiscalDocument,
        plan: FiscalImportCommitPlan,
    ): FiscalImportCommitResult {
        var result: FiscalImportCommitResult? = null
        database.withTransaction {
            val existingDocument = fiscalImportDao.findByDocumentId(document.id)
            if (existingDocument != null) {
                check(existingDocument.documentHashSha256 == document.evidence.provenance.documentHashSha256) {
                    "O documento fiscal tem a mesma identidade, mas outro hash."
                }
                result = FiscalImportCommitResult.Committed(
                    operationId = existingDocument.operationId,
                    purchaseId = purchaseId(existingDocument.operationId),
                    supplierId = supplierIdFromExistingPurchase(existingDocument.operationId),
                    alreadyCommitted = true,
                )
                return@withTransaction
            }

            fiscalImportDao.findByOperationId(plan.operationId)?.let {
                error("A operationId já foi usada por outro documento fiscal.")
            }

            val identity = identityProvider.current()
            val committedAt = plan.confirmedAt.toEpochMilli()
            val supplierId = resolveSupplier(plan, document, committedAt, identity)
            val purchaseId = purchaseId(plan.operationId)
            val fiscalItemRows = plan.items.map { item ->
                val productId = resolveProduct(item, plan.operationId, document.id, committedAt, identity)
                val mapping = SupplierProductMappingEntity(
                    id = mappingId(plan.operationId, item.lineNumber),
                    supplierId = supplierId,
                    supplierProductCode = item.supplierProductCode,
                    gtin = item.gtin,
                    supplierDescription = item.description,
                    productId = productId,
                    confirmedAt = committedAt,
                    matchMethod = "HUMAN_CONFIRMED",
                )
                if (mapping.supplierProductCode != null || mapping.gtin != null) {
                    supplierProductMappingDao.insert(mapping)
                    insertEvent(
                        identity = identity,
                        eventId = "event:fiscal.mapping:${mapping.id}",
                        aggregateId = productId,
                        type = "supplier.product.mapping.created",
                        occurredAt = committedAt,
                        payload = JSONObject()
                            .put("mapping_id", mapping.id)
                            .put("supplier_id", supplierId)
                            .put("product_id", productId)
                            .putOpt("supplier_product_code", mapping.supplierProductCode)
                            .putOpt("gtin", mapping.gtin)
                            .put("supplier_description", mapping.supplierDescription)
                            .put("confirmed_at", mapping.confirmedAt)
                            .put("match_method", mapping.matchMethod)
                            .put("fiscal_document_id", document.id),
                    )
                }
                item.copy(productId = productId)
            }

            purchaseDao.insert(
                PurchaseEntity(
                    id = purchaseId,
                    supplierId = supplierId,
                    status = PurchaseStatus.RECEIVED,
                    totalCostCents = plan.invoiceTotalCents,
                    createdAt = committedAt,
                ),
            )
            purchaseDao.insertItems(
                fiscalItemRows.map { row ->
                    PurchaseItemEntity(
                        purchaseId = purchaseId,
                        lineNumber = row.lineNumber,
                        productId = row.productId ?: error("Produto resolvido ausente."),
                        quantity = row.stockQuantity,
                        unitCostCents = row.unitCostCents,
                    )
                },
            )
            fiscalItemRows.forEach { row ->
                stockMovementDao.insert(
                    StockMovementEntity(
                        id = stockMovementId(plan.operationId, row.lineNumber),
                        productId = row.productId ?: error("Produto resolvido ausente."),
                        quantityDelta = row.stockQuantity,
                        reason = "fiscal_import",
                        referenceId = purchaseId,
                        occurredAt = committedAt,
                    ),
                )
                productPurchaseHistoryDao.insert(
                    ProductPurchaseHistoryEntity(
                        id = historyId(plan.operationId, row.lineNumber),
                        fiscalDocumentId = document.id,
                        supplierId = supplierId,
                        productId = row.productId ?: error("Produto resolvido ausente."),
                        purchasedAt = committedAt,
                        fiscalQuantity = row.fiscalQuantity.toPlainString(),
                        stockQuantity = row.stockQuantity,
                        unitPurchaseCostCents = row.unitCostCents,
                        totalCostCents = row.totalCostCents,
                    ),
                )
                insertEvent(
                    identity = identity,
                    eventId = "event:fiscal.stock:${plan.operationId}:${row.lineNumber}",
                    aggregateId = row.productId ?: error("Produto resolvido ausente."),
                    type = "inventory.purchase.received",
                    occurredAt = committedAt,
                    payload = JSONObject()
                        .put("product_id", row.productId)
                        .put("quantity", row.stockQuantity)
                        .put("purchase_id", purchaseId)
                        .put("history_id", historyId(plan.operationId, row.lineNumber))
                        .put("supplier_id", supplierId)
                        .put("fiscal_quantity", row.fiscalQuantity.toPlainString())
                        .put("unit_cost_cents", row.unitCostCents)
                        .put("total_cost_cents", row.totalCostCents)
                        .put("fiscal_document_id", document.id),
                )
            }
            val purchaseItemsPayload = JSONArray().apply {
                fiscalItemRows.forEach { row ->
                    put(
                        JSONObject()
                            .put("product_id", row.productId)
                            .put("quantity", row.stockQuantity)
                            .put("unit_cost_cents", row.unitCostCents),
                    )
                }
            }
            insertEvent(
                identity = identity,
                eventId = "event:fiscal.purchase:$purchaseId",
                aggregateId = purchaseId,
                type = "purchase.created",
                occurredAt = committedAt,
                payload = JSONObject()
                    .put("purchase_id", purchaseId)
                    .put("supplier_id", supplierId)
                    .put("total_cost_cents", plan.invoiceTotalCents)
                    .put("status", PurchaseStatus.RECEIVED.name)
                    .put("items", purchaseItemsPayload)
                    .put("fiscal_document_id", document.id),
            )
            fiscalImportDao.insert(
                FiscalImportEntity(
                    id = plan.operationId,
                    documentId = document.id,
                    accessKey = document.accessKey,
                    documentHashSha256 = document.evidence.provenance.documentHashSha256,
                    operationId = plan.operationId,
                    status = FiscalImportStatus.COMMITTED,
                    committedAt = committedAt,
                    originalXml = document.evidence.originalXml.copyOf(),
                ),
            )
            insertEvent(
                identity = identity,
                eventId = "event:fiscal.commit:${document.id}",
                aggregateId = document.id,
                type = "fiscal.import.committed",
                occurredAt = committedAt,
                payload = JSONObject()
                    .put("fiscal_document_id", document.id)
                    .putOpt("access_key", document.accessKey)
                    .put("operation_id", plan.operationId)
                    .put("purchase_id", purchaseId)
                    .put("document_hash_sha256", document.evidence.provenance.documentHashSha256)
                    .put("item_count", plan.items.size),
            )
            result = FiscalImportCommitResult.Committed(
                operationId = plan.operationId,
                purchaseId = purchaseId,
                supplierId = supplierId,
                alreadyCommitted = false,
            )
        }
        if (result is FiscalImportCommitResult.Committed && !result!!.alreadyCommitted) syncScheduler.schedule()
        return result ?: error("Fiscal commit did not produce a result.")
    }

    private suspend fun resolveSupplier(
        plan: FiscalImportCommitPlan,
        document: CanonicalFiscalDocument,
        committedAt: Long,
        identity: com.tino.app.core.common.InstallationIdentity,
    ): String = when (val supplier = plan.supplier) {
        is FiscalSupplierCommitPlan.Existing -> {
            check(supplierDao.findById(supplier.supplierId) != null) { "Fornecedor não encontrado." }
            supplier.supplierId
        }

        is FiscalSupplierCommitPlan.New -> {
            val id = "fiscal-supplier:${plan.operationId}"
            val name = supplier.tradeName ?: supplier.legalName
            check(!name.isNullOrBlank()) { "Nome do fornecedor é obrigatório." }
            supplierDao.insert(SupplierEntity(id, name.trim(), null, committedAt, supplier.taxId))
            insertEvent(
                identity = identity,
                eventId = "event:fiscal.supplier:$id",
                aggregateId = id,
                type = "supplier.created",
                occurredAt = committedAt,
                payload = JSONObject()
                    .put("supplier_id", id)
                    .put("name", name.trim())
                    .putOpt("tax_id", supplier.taxId)
                    .put("fiscal_document_id", document.id),
            )
            id
        }
    }

    private suspend fun resolveProduct(
        item: FiscalCommitItemPlan,
        operationId: String,
        documentId: String,
        committedAt: Long,
        identity: com.tino.app.core.common.InstallationIdentity,
    ): String {
        item.productId?.let {
            check(database.productDao().findById(it) != null) { "Produto não encontrado." }
            return it
        }
        val id = "fiscal-product:$operationId:${item.lineNumber}"
        database.productDao().insert(
            ProductEntity(
                id = id,
                name = item.newProductName ?: error("Nome do produto é obrigatório."),
                priceCents = item.newProductSalePriceCents ?: error("Preço de venda é obrigatório."),
                unit = item.inventoryUnit,
                createdAt = committedAt,
            ),
        )
        insertEvent(
            identity = identity,
            eventId = "event:fiscal.product:$id",
            aggregateId = id,
            type = "product.created.from_fiscal_document",
            occurredAt = committedAt,
            payload = JSONObject()
                .put("product_id", id)
                .put("name", item.newProductName)
                .put("price_cents", item.newProductSalePriceCents)
                .put("unit", item.inventoryUnit)
                .put("fiscal_document_id", documentId),
        )
        return id
    }

    private suspend fun insertEvent(
        identity: com.tino.app.core.common.InstallationIdentity,
        eventId: String,
        aggregateId: String,
        type: String,
        occurredAt: Long,
        payload: JSONObject,
    ) {
        database.domainEventDao().insert(
            DomainEventEntity(
                eventId = eventId,
                storeId = identity.storeId,
                deviceId = identity.deviceId,
                aggregateId = aggregateId,
                type = type,
                schemaVersion = 1,
                occurredAt = occurredAt,
                payloadJson = payload.toString(),
            ),
        )
    }

    private suspend fun supplierIdFromExistingPurchase(operationId: String): String =
        database.purchaseDao().findById(purchaseId(operationId))?.supplierId
            ?: "unknown"

    private fun purchaseId(operationId: String) = "fiscal-purchase:$operationId"
    private fun mappingId(operationId: String, lineNumber: Int) = "fiscal-mapping:$operationId:$lineNumber"
    private fun stockMovementId(operationId: String, lineNumber: Int) = "fiscal-stock:$operationId:$lineNumber"
    private fun historyId(operationId: String, lineNumber: Int) = "fiscal-history:$operationId:$lineNumber"
}
