package com.tino.app.core.database

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "products", indices = [Index(value = ["name"], unique = true)])
data class ProductEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val priceCents: Long,
    val unit: String,
    val createdAt: Long,
    val gtin: String? = null,
    val stockTracked: Boolean = true,
)

@Entity(tableName = "catalog_sync_state")
data class CatalogSyncStateEntity(
    @androidx.room.PrimaryKey val businessId: String,
    val status: String,
    val lastSuccessfulAt: Long?,
    val completedAt: Long?,
    val total: Int,
    val accepted: Int,
    val rejected: Int,
    val possiblyPartial: Boolean,
    val errorMessage: String?,
)

@Entity(tableName = "sales")
data class SaleEntity(
    @androidx.room.PrimaryKey val id: String,
    val totalCents: Long,
    val paymentMethod: String,
    val createdAt: Long,
)

@Entity(
    tableName = "direct_receipts",
    indices = [Index(value = ["operationId"], unique = true), Index("occurredAt")],
)
data class DirectReceiptEntity(
    @androidx.room.PrimaryKey val id: String,
    val amountCents: Long,
    val paymentMethod: String,
    val occurredAt: Long,
    val source: String,
    val note: String?,
    val operationId: String,
)

@Entity(tableName = "sale_items", primaryKeys = ["saleId", "lineNumber"])
data class SaleItemEntity(
    val saleId: String,
    val lineNumber: Int,
    val productId: String,
    val quantity: Int,
    val unitPriceCents: Long,
)

@Entity(tableName = "stock_movements", indices = [Index("productId")])
data class StockMovementEntity(
    @androidx.room.PrimaryKey val id: String,
    val productId: String,
    val quantityDelta: Int,
    val reason: String,
    val referenceId: String?,
    val occurredAt: Long,
)

@Entity(tableName = "customers", indices = [Index(value = ["name"], unique = true)])
data class CustomerEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "suppliers",
    indices = [Index(value = ["name"], unique = true), Index("taxId")],
)
data class SupplierEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val createdAt: Long,
    val taxId: String? = null,
)

enum class FiscalImportStatus { COMMITTED }

@Entity(
    tableName = "fiscal_imports",
    indices = [Index(value = ["documentId"], unique = true), Index(value = ["operationId"], unique = true)],
)
data class FiscalImportEntity(
    @androidx.room.PrimaryKey val id: String,
    val documentId: String,
    val accessKey: String?,
    val documentHashSha256: String,
    val operationId: String,
    val status: FiscalImportStatus,
    val committedAt: Long,
    val originalXml: ByteArray,
)

/** Durable remote receiving operation. It stores replay identity and payload, never fiscal raw data. */
@Entity(
    tableName = "goods_receipt_operations",
    indices = [
        Index(value = ["businessId", "operationType", "logicalReference"], unique = true),
        Index("status"),
        Index("receiptId", unique = true),
    ],
)
data class GoodsReceiptOperationEntity(
    @androidx.room.PrimaryKey val operationId: String,
    val businessId: String,
    val operationType: String,
    val logicalReference: String,
    val idempotencyKey: String,
    val documentId: String?,
    val previewId: String?,
    val receiptId: String?,
    val requestJson: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "remote_goods_receipts")
data class RemoteGoodsReceiptEntity(
    @androidx.room.PrimaryKey val receiptId: String,
    val businessId: String,
    val previewId: String?,
    val documentId: String?,
    val status: String,
    val itemCount: Int,
    val projectedAt: Long,
)

@Entity(
    tableName = "remote_goods_receipt_items",
    primaryKeys = ["receiptId", "lineNumber"],
    indices = [Index("localProductId"), Index("remoteProductId")],
)
data class RemoteGoodsReceiptItemEntity(
    val receiptId: String,
    val lineNumber: Int,
    val remoteProductId: String,
    val localProductId: String,
    val productName: String,
    val baseUnit: String,
    /** Decimal wire value retained as canonical plain text; never converted to Int/Double. */
    val quantityAdded: String,
    /** Decimal wire value retained as canonical plain text; never converted to cents. */
    val unitCost: String,
    val projectedAt: Long,
)

@Entity(
    tableName = "remote_product_mappings",
    indices = [Index(value = ["businessId", "remoteProductId"], unique = true), Index("localProductId")],
)
data class RemoteProductMappingEntity(
    @androidx.room.PrimaryKey val mappingId: String,
    val businessId: String,
    val remoteProductId: String,
    val localProductId: String,
    val createdAt: Long,
)

@Entity(tableName = "supplier_product_mappings", indices = [Index("supplierId"), Index("productId")])
data class SupplierProductMappingEntity(
    @androidx.room.PrimaryKey val id: String,
    val supplierId: String,
    val supplierProductCode: String?,
    val gtin: String?,
    val supplierDescription: String,
    val productId: String,
    val confirmedAt: Long,
    val matchMethod: String,
)

@Entity(tableName = "product_purchase_history", indices = [Index("productId"), Index("fiscalDocumentId")])
data class ProductPurchaseHistoryEntity(
    @androidx.room.PrimaryKey val id: String,
    val fiscalDocumentId: String,
    val supplierId: String,
    val productId: String,
    val purchasedAt: Long,
    val fiscalQuantity: String,
    val stockQuantity: Int,
    val unitPurchaseCostCents: Long,
    val totalCostCents: Long,
)

enum class CreditEntryType { SALE, PAYMENT }

@Entity(tableName = "credit_entries", indices = [Index("customerId"), Index("occurredAt")])
data class CreditEntryEntity(
    @androidx.room.PrimaryKey val id: String,
    val customerId: String,
    val amountCents: Long,
    val type: CreditEntryType,
    val referenceId: String?,
    val occurredAt: Long,
    val paymentMethod: String = "unknown",
    val dueAt: Long? = null,
    /** Semantic Shared Ledger type; null keeps legacy rows backward compatible. */
    val ledgerType: String? = null,
    /** JSON encoded LedgerProvenance. Kept nullable for legacy data. */
    val provenance: String? = null,
    val reason: String? = null,
)

enum class PurchaseStatus { DRAFT, ORDERED, RECEIVED, COMPLETED }

@Entity(tableName = "purchases", indices = [Index("supplierId")])
data class PurchaseEntity(
    @androidx.room.PrimaryKey val id: String,
    val supplierId: String?,
    val status: PurchaseStatus,
    val totalCostCents: Long,
    val createdAt: Long,
    val expectedDeliveryAt: Long? = null,
    val receivedAt: Long? = null,
)

@Entity(tableName = "purchase_items", primaryKeys = ["purchaseId", "lineNumber"])
data class PurchaseItemEntity(
    val purchaseId: String,
    val lineNumber: Int,
    val productId: String,
    val quantity: Int,
    val unitCostCents: Long,
)

@Entity(tableName = "orders", indices = [Index("createdAt"), Index("status")])
data class OrderEntity(
    @androidx.room.PrimaryKey val id: String,
    val channel: String,
    val fulfillment: String,
    val customerName: String?,
    val addressReference: String?,
    val status: String,
    val totalCents: Long,
    val createdAt: Long,
)

@Entity(tableName = "order_items", primaryKeys = ["orderId", "lineNumber"])
data class OrderItemEntity(
    val orderId: String,
    val lineNumber: Int,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPriceCents: Long,
)

@Entity(
    tableName = "recommendations",
    indices = [Index("productId"), Index("createdAtEpochMs"), Index("decision")],
)
data class RecommendationEntity(
    @androidx.room.PrimaryKey val id: String,
    val type: String,
    val productId: String,
    val message: String,
    val confidence: Double,
    val decision: String,
    val createdAtEpochMs: Long,
    val stockQuantity: Int?,
    val unitsSoldLast30Days: Int?,
    val rule: String?,
    val windowDays: Int?,
    val quality: String = "COMPLETE",
    val featureVersion: String = "inventory-features-v1",
    val modelVersion: String = "local-heuristic-v1",
)

@Entity(
    tableName = "attention_items",
    indices = [Index("state"), Index("lastSeenAtEpochMs"), Index("subjectId")],
)
data class AttentionEntity(
    @androidx.room.PrimaryKey val id: String,
    val insightId: String,
    val subjectId: String?,
    val title: String,
    val explanation: String,
    val evidenceIdsJson: String,
    val relevance: Int,
    val urgency: Int,
    val confidence: Double,
    val state: String,
    val snoozedUntilEpochMs: Long?,
    val createdAtEpochMs: Long,
    val lastSeenAtEpochMs: Long,
)

@Entity(
    tableName = "intelligence_evidence",
    indices = [Index("subjectId"), Index("detectedAtEpochMs"), Index("type")],
)
data class IntelligenceEvidenceEntity(
    @androidx.room.PrimaryKey val id: String,
    val type: String,
    val subjectId: String?,
    val factsJson: String,
    val source: String,
    val confidence: Double,
    val occurredAtEpochMs: Long?,
    val detectedAtEpochMs: Long,
)

/** Durable, reviewed knowledge catalogs; transactional commerce facts stay elsewhere. */
@Entity(
    tableName = "approved_knowledge_catalogs",
    indices = [androidx.room.Index("state")],
)
data class ApprovedKnowledgeCatalogEntity(
    @androidx.room.PrimaryKey val version: String,
    val payloadJson: String,
    val activatedAtEpochMs: Long,
    val state: String,
)

@Entity(
    tableName = "attention_outcomes",
    indices = [Index("attentionId"), Index("occurredAtEpochMs"), Index("outcome")],
)
data class AttentionOutcomeEntity(
    @androidx.room.PrimaryKey val id: String,
    val attentionId: String,
    val outcome: String,
    val occurredAtEpochMs: Long,
)

@Entity(
    tableName = "recommendation_outcomes",
    indices = [Index(value = ["recommendationId", "outcome"], unique = true), Index("occurredAtEpochMs")],
)
data class RecommendationOutcomeEntity(
    @androidx.room.PrimaryKey val id: String,
    val recommendationId: String,
    val outcome: String,
    val occurredAtEpochMs: Long,
)

@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @androidx.room.PrimaryKey val scope: String,
    val cursor: String,
    val updatedAt: Long,
)

@Entity(tableName = "store_profile")
data class StoreProfileEntity(
    @androidx.room.PrimaryKey val id: String,
    val storeName: String,
    val ownerName: String,
    val phone: String,
    val createdAt: Long,
    val businessVertical: String = "RETAIL",
    val activeModules: String = "CORE,SALES,INVENTORY,CUSTOMERS,CREDIT,STOCK_ENTRY,FISCAL",
    val profileVersion: Int = 1,
    val operationalPatterns: String = "",
    val permanentCapabilities: String = "",
)

enum class SyncStatus { PENDING, SYNCING, SYNCED, FAILED, REJECTED, BLOCKED, CONFLICT }

@Entity(tableName = "domain_events", indices = [Index("syncStatus"), Index("occurredAt")])
data class DomainEventEntity(
    @androidx.room.PrimaryKey val eventId: String,
    val storeId: String,
    val deviceId: String,
    val aggregateId: String,
    val type: String,
    val schemaVersion: Int,
    val occurredAt: Long,
    val payloadJson: String,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
)

/** Presentation projection of a successful agent operation; domain facts remain authoritative. */
@Entity(
    tableName = "agent_activities",
    indices = [
        Index("occurredAt"),
        Index("operationId", unique = true),
        Index("undoState"),
    ],
)
data class AgentActivityEntity(
    @androidx.room.PrimaryKey val id: String,
    val capability: String,
    val operationId: String?,
    val occurredAt: Long,
    val source: String,
    val summary: String,
    val summaryKind: String?,
    val summaryPayloadJson: String?,
    val undoPolicy: String?,
    val compensatingCapability: String?,
    val undoDeadline: Long?,
    val undoState: String,
    val status: String,
    val compensatesActivityId: String?,
)

/** Operational trace of planning and execution; it never stores commerce facts. */
@Entity(
    tableName = "intelligence_telemetry",
    indices = [
        Index("requestId"),
        Index("plannerUsed"),
        Index("occurredAtEpochMs"),
    ],
)
data class IntelligenceTelemetryEntity(
    @androidx.room.PrimaryKey val id: String,
    val requestId: String,
    val sessionId: String,
    val plannerSelected: String,
    val plannerUsed: String,
    val fallbackReason: String?,
    val planJson: String,
    val validationResult: String,
    val validationErrorsJson: String,
    val validationRejectionKindsJson: String,
    val fallbackUsed: Boolean,
    val executionResult: String,
    val groundingCompleteness: String,
    val latencyMs: Long,
    val planningLatencyMs: Long,
    val errorStage: String,
    val occurredAtEpochMs: Long,
    val loopId: String,
    val turnIndex: Int,
    val loopState: String,
    val decision: String,
)

/** Persisted interaction boundary; it stores resumable context, not commerce facts. */
@Entity(tableName = "interaction_states")
data class InteractionStateEntity(
    @androidx.room.PrimaryKey val sessionId: String,
    val stateJson: String,
    val persistencePolicy: String,
    val updatedAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
)

/** Durable mutation gate; it stores authorization metadata, never commerce facts. */
@Entity(
    tableName = "mutation_operations",
    indices = [
        Index("idempotencyKey", unique = true),
        Index("status"),
    ],
)
data class MutationOperationEntity(
    @androidx.room.PrimaryKey val operationId: String,
    val capabilityId: String,
    val argumentsJson: String,
    val risk: String,
    val requiresConfirmation: Boolean,
    val idempotencyKey: String,
    val previewFingerprint: String,
    val confirmationTokenHash: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val status: String,
)

/** Durable governed business memory; never stores current commerce facts. */
@Entity(tableName = "business_memory", indices = [Index(value = ["scopeKey", "memoryKey", "value"], unique = true), Index("scopeKey")])
data class BusinessMemoryEntity(
    @androidx.room.PrimaryKey val id: String,
    val scopeKey: String,
    val memoryKey: String,
    val value: String,
    val kind: String,
    val lifecycle: String,
    val confidence: Double,
    val supportCount: Int,
    val contradictionCount: Int,
    val provenanceJson: String,
    val sourceEventIdsJson: String,
    val updatedAtEpochMs: Long,
    val demotionReason: String?,
)
