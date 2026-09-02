package com.tino.app.domain.receiving

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.common.UuidV7
import com.tino.app.core.database.GoodsReceiptOperationDao
import com.tino.app.core.database.GoodsReceiptOperationEntity
import com.tino.app.core.database.ProductDao
import com.tino.app.core.database.ProductEntity
import com.tino.app.core.database.RemoteGoodsReceiptDao
import com.tino.app.core.database.RemoteGoodsReceiptEntity
import com.tino.app.core.database.RemoteGoodsReceiptItemEntity
import com.tino.app.core.database.RemoteProductMappingDao
import com.tino.app.core.database.RemoteProductMappingEntity
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.network.GoodsReceiptApi
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Contract-backed receiving use case. Remote confirmation is projected, never re-received locally. */
@Singleton
class GoodsReceiptRepository @Inject constructor(
    private val api: GoodsReceiptApi,
    private val database: TinoDatabase,
    private val productDao: ProductDao,
    private val operations: GoodsReceiptOperationDao,
    private val remoteReceipts: RemoteGoodsReceiptDao,
    private val productMappings: RemoteProductMappingDao,
    private val identityProvider: IdentityProvider,
) {
    private val confirmationMutex = Mutex()
    private val gson = Gson()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val remoteItems: Flow<List<RemoteGoodsReceiptItemEntity>> = identityProvider.businessId.flatMapLatest { businessId ->
        businessId?.let(remoteReceipts::observeItems) ?: flowOf(emptyList())
    }

    fun businessId(): String = identityProvider.current().businessId
        ?: error("O comércio ainda não foi vinculado ao TINO Backend.")

    suspend fun retrieve(accessKey: String): NfeDocumentState {
        val normalized = NfeAccessKeyValidator.normalizeAndValidate(accessKey)
        val operation = operation(
            type = OperationType.RETRIEVE,
            logicalReference = normalized,
        )
        return try {
            api.retrieveNfe(businessId(), normalized, operation.idempotencyKey).also { state ->
                operations.markRetrieved(
                    operationId = operation.operationId,
                    documentId = state.documentId,
                    previewId = state.preview?.previewId,
                    status = if (state.retrievalStatus == NfeRetrievalStatus.SUCCESS) OperationStatus.SUCCEEDED.name else OperationStatus.PENDING.name,
                    updatedAt = now(),
                )
            }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            operations.updateStatus(operation.operationId, OperationStatus.PENDING.name, now())
            throw error
        }
    }

    suspend fun getDocument(documentId: String): NfeDocumentState = api.getNfeDocument(businessId(), documentId)

    suspend fun getPreview(documentId: String): GoodsReceiptPreview = api.getPreview(businessId(), documentId)

    suspend fun reprocess(documentId: String): NfeDocumentState {
        val operation = operation(OperationType.REPROCESS, documentId)
        return try {
            api.reprocessNfe(businessId(), documentId, operation.idempotencyKey).also { state ->
                operations.markRetrieved(operation.operationId, state.documentId, state.preview?.previewId, OperationStatus.SUCCEEDED.name, now())
            }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            operations.updateStatus(operation.operationId, OperationStatus.PENDING.name, now())
            throw error
        }
    }

    suspend fun searchProducts(query: String? = null, gtin: String? = null): List<ProductSearchItem> =
        api.searchProducts(businessId(), query, gtin)

    suspend fun confirm(preview: GoodsReceiptPreview, confirmation: GoodsReceiptConfirmation): GoodsReceiptResult =
        confirmationMutex.withLock {
            require(confirmation.previewVersion == preview.version) { "A prévia mudou. Atualize antes de confirmar." }
            val operation = operation(
                type = OperationType.CONFIRM,
                logicalReference = preview.previewId,
                previewId = preview.previewId,
                documentId = preview.documentId,
                requestJson = confirmation.toStoredJson(),
            )
            try {
                val result = api.confirmGoodsReceipt(
                    businessId = businessId(),
                    previewId = preview.previewId,
                    confirmation = confirmation,
                    idempotencyKey = operation.idempotencyKey,
                )
                project(
                    preview = preview,
                    result = result,
                    operation = operation,
                )
                result
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                operations.updateStatus(operation.operationId, OperationStatus.PENDING.name, now())
                throw error
            }
        }

    /** Replays the persisted confirmation key after timeout/process recreation. */
    suspend fun retryPendingConfirmation(previewId: String): GoodsReceiptResult {
        val operation = operations.find(businessId(), OperationType.CONFIRM.name, previewId)
            ?: error("Não existe confirmação pendente para esta prévia.")
        return confirmationMutex.withLock { replayPendingConfirmation(operation) }
    }

    /** Recovery hook for a process restart: it reuses the durable operation key and body. */
    suspend fun retryPendingConfirmationIfPresent(): GoodsReceiptResult? {
        val operation = operations.findPendingConfirmation(businessId(), OperationType.CONFIRM.name)
            ?: return null
        return confirmationMutex.withLock { replayPendingConfirmation(operation) }
    }

    suspend fun reconcile(receiptId: String): GoodsReceiptResult {
        val result = api.getGoodsReceipt(businessId(), receiptId)
        project(null, result, operations.findByReceiptId(receiptId))
        return result
    }

    private suspend fun project(
        preview: GoodsReceiptPreview?,
        result: GoodsReceiptResult,
        operation: GoodsReceiptOperationEntity?,
    ) {
        database.withTransaction {
            val projectedAt = now()
            remoteReceipts.upsert(
                RemoteGoodsReceiptEntity(
                    receiptId = result.receiptId,
                    businessId = businessId(),
                    previewId = preview?.previewId ?: operation?.previewId,
                    documentId = preview?.documentId ?: operation?.documentId,
                    status = result.status.name,
                    itemCount = result.itemCount,
                    projectedAt = projectedAt,
                ),
            )
            val itemRows = result.items.map { item ->
                val localProductId = projectProduct(item.productId, item.productName, item.baseUnit, projectedAt)
                RemoteGoodsReceiptItemEntity(
                    receiptId = result.receiptId,
                    lineNumber = item.lineNumber,
                    remoteProductId = item.productId,
                    localProductId = localProductId,
                    productName = item.productName,
                    baseUnit = item.baseUnit,
                    quantityAdded = item.quantityAdded.toPlainString(),
                    unitCost = item.unitCost.toPlainString(),
                    projectedAt = projectedAt,
                )
            }
            remoteReceipts.deleteItems(result.receiptId)
            remoteReceipts.upsertItems(itemRows)
            operation?.let {
                operations.markConfirmed(it.operationId, result.receiptId, OperationStatus.SUCCEEDED.name, projectedAt)
            }
        }
    }

    private suspend fun replayPendingConfirmation(operation: GoodsReceiptOperationEntity): GoodsReceiptResult {
        val previewId = operation.previewId ?: error("A confirmação pendente não possui preview_id.")
        val requestJson = operation.requestJson ?: error("A confirmação pendente não possui decisão salva.")
        val result = api.confirmGoodsReceipt(businessId(), previewId, requestJson.toConfirmation(), operation.idempotencyKey)
        project(null, result, operation)
        return result
    }

    private suspend fun projectProduct(remoteProductId: String, name: String, baseUnit: String, timestamp: Long): String {
        productMappings.find(businessId(), remoteProductId)?.let { return it.localProductId }
        productDao.findById(remoteProductId)?.let {
            saveMapping(remoteProductId, it.id, timestamp)
            return it.id
        }
        productDao.findByName(name)?.let {
            saveMapping(remoteProductId, it.id, timestamp)
            return it.id
        }
        val product = ProductEntity(remoteProductId, name, 0L, baseUnit, timestamp)
        runCatching { productDao.insert(product) }.onFailure {
            productDao.findByName(name)?.let { existing ->
                saveMapping(remoteProductId, existing.id, timestamp)
                return existing.id
            }
            throw it
        }
        saveMapping(remoteProductId, remoteProductId, timestamp)
        return remoteProductId
    }

    private suspend fun saveMapping(remoteProductId: String, localProductId: String, timestamp: Long) {
        productMappings.insertIfAbsent(
            RemoteProductMappingEntity(
                mappingId = "${businessId()}:$remoteProductId",
                businessId = businessId(),
                remoteProductId = remoteProductId,
                localProductId = localProductId,
                createdAt = timestamp,
            ),
        )
    }

    private suspend fun operation(
        type: OperationType,
        logicalReference: String,
        documentId: String? = null,
        previewId: String? = null,
        requestJson: String? = null,
    ): GoodsReceiptOperationEntity {
        val businessId = businessId()
        operations.find(businessId, type.name, logicalReference)?.let { existing ->
            if (requestJson != null && existing.requestJson == null) {
                // The operation identity already won; only enrich its durable request payload.
                operations.enrich(existing.operationId, requestJson, documentId, previewId, now())
                return operations.findById(existing.operationId) ?: existing
            }
            return existing
        }
        val timestamp = now()
        val created = GoodsReceiptOperationEntity(
            operationId = UuidV7.new(),
            businessId = businessId,
            operationType = type.name,
            logicalReference = logicalReference,
            idempotencyKey = UuidV7.new(),
            documentId = documentId,
            previewId = previewId,
            receiptId = null,
            requestJson = requestJson,
            status = OperationStatus.PENDING.name,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        operations.insertIfAbsent(created)
        return operations.find(businessId, type.name, logicalReference) ?: created
    }

    private fun GoodsReceiptConfirmation.toStoredJson(): String = gson.toJson(
        mapOf(
            "preview_version" to previewVersion,
            "items" to items.map { item ->
                mapOf(
                    "line_number" to item.lineNumber,
                    "action" to item.action.name,
                    "product_id" to item.productId,
                    "base_unit" to item.baseUnit,
                    "conversion_factor" to item.conversionFactor,
                )
            },
        ),
    )

    private fun String.toConfirmation(): GoodsReceiptConfirmation {
        val root = JsonParser.parseString(this).asJsonObject
        return GoodsReceiptConfirmation(
            previewVersion = root.get("preview_version").asLong,
            items = root.getAsJsonArray("items").map { value ->
                val item = value.asJsonObject
                GoodsReceiptDecision(
                    lineNumber = item.get("line_number").asInt,
                    action = GoodsReceiptDecisionAction.valueOf(item.get("action").asString),
                    productId = item.get("product_id")?.takeUnless { it.isJsonNull }?.asString,
                    baseUnit = item.get("base_unit")?.takeUnless { it.isJsonNull }?.asString,
                    conversionFactor = item.get("conversion_factor")?.takeUnless { it.isJsonNull }?.asBigDecimal,
                )
            },
        )
    }

    private fun now(): Long = System.currentTimeMillis()

    private enum class OperationType { RETRIEVE, REPROCESS, CONFIRM }
    private enum class OperationStatus { PENDING, SUCCEEDED }
}

object NfeAccessKeyValidator {
    fun normalizeAndValidate(value: String): String {
        val digits = value.filter(Char::isDigit)
        require(digits.length == 44 && digits == value.filterNot(Char::isWhitespace)) {
            "A chave de acesso precisa ter 44 dígitos."
        }
        val expected = digits.take(43).reversed().mapIndexed { index, char ->
            char.digitToInt() * (2 + index % 8)
        }.sum().let { total -> (11 - total % 11).let { if (it >= 10) 0 else it } }
        require(expected == digits.last().digitToInt()) { "A chave de acesso é inválida." }
        return digits
    }
}
