package com.tino.app.domain.receiving

import java.math.BigDecimal

enum class NfeRetrievalStatus {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    NOT_FOUND,
    FAILED,
    OUTCOME_UNKNOWN,
}

enum class FiscalStatus {
    AUTHORIZED,
    CANCELLED,
    DENIED,
    UNKNOWN,
}

enum class GoodsReceiptPreviewStatus {
    DRAFT,
    REVIEW_REQUIRED,
    READY,
    CONFIRMED,
    CANCELLED,
}

enum class ProductResolutionStatus {
    MATCHED,
    NEW_CANDIDATE,
    NEEDS_REVIEW,
    IGNORED,
}

enum class GoodsReceiptStatus {
    CONFIRMED,
    CANCELLED,
}

enum class GoodsReceiptDecisionAction {
    USE_EXISTING,
    CREATE_PRODUCT,
    IGNORE,
}

enum class GoodsReceiptErrorCode {
    INVALID_ACCESS_KEY,
    NFE_NOT_FOUND,
    RETRIEVAL_UNAVAILABLE,
    OUTCOME_UNKNOWN,
    FISCAL_CANCELLED,
    FISCAL_DENIED,
    PRODUCT_REVIEW_REQUIRED,
    PACKAGING_CONVERSION_REQUIRED,
    STALE_PREVIEW,
    INVALID_PRODUCT_SELECTION,
    BUSINESS_ACCESS_DENIED,
    IDEMPOTENCY_CONFLICT,
}

data class NfePreviewReference(
    val previewId: String,
    val status: GoodsReceiptPreviewStatus,
    val version: Long,
)

data class NfeDocumentState(
    val documentId: String,
    val accessKey: String,
    val retrievalStatus: NfeRetrievalStatus,
    val fiscalStatus: FiscalStatus,
    val itemCount: Int,
    val errorCode: GoodsReceiptErrorCode?,
    val retryable: Boolean,
    val preview: NfePreviewReference?,
)

data class GoodsReceiptPreviewSummary(
    val totalItems: Int,
    val matchedItems: Int,
    val newCandidateItems: Int,
    val reviewRequiredItems: Int,
)

data class GoodsReceiptPreviewIssuer(
    val legalName: String,
    val tradeName: String?,
)

data class GoodsReceiptPreviewItem(
    val lineNumber: Int,
    val description: String,
    val supplierProductCode: String?,
    val gtin: String?,
    val resolutionStatus: ProductResolutionStatus,
    val productId: String?,
    val candidateName: String?,
    val purchaseUnit: String,
    val purchaseQuantity: BigDecimal,
    val purchaseUnitCost: BigDecimal,
    val productTotal: BigDecimal,
    val baseUnit: String?,
    val conversionFactor: BigDecimal?,
    val stockQuantity: BigDecimal?,
    val requiresUserAction: Boolean,
)

data class GoodsReceiptPreview(
    val previewId: String,
    val documentId: String,
    val documentNumber: String?,
    val series: String?,
    val issuer: GoodsReceiptPreviewIssuer?,
    val retrievalStatus: NfeRetrievalStatus,
    val fiscalStatus: FiscalStatus,
    val status: GoodsReceiptPreviewStatus,
    val version: Long,
    val summary: GoodsReceiptPreviewSummary,
    val items: List<GoodsReceiptPreviewItem>,
)

data class ProductSearchItem(
    val productId: String,
    val name: String,
    val baseUnit: String,
    val gtin: String?,
)

data class GoodsReceiptDecision(
    val lineNumber: Int,
    val action: GoodsReceiptDecisionAction,
    val productId: String? = null,
    val baseUnit: String? = null,
    val conversionFactor: BigDecimal? = null,
)

data class GoodsReceiptConfirmation(
    val previewVersion: Long,
    val items: List<GoodsReceiptDecision>,
)

data class GoodsReceiptItemResult(
    val lineNumber: Int,
    val productId: String,
    val productName: String,
    val baseUnit: String,
    val quantityAdded: BigDecimal,
    val unitCost: BigDecimal,
)

data class GoodsReceiptResult(
    val receiptId: String,
    val status: GoodsReceiptStatus,
    val itemCount: Int,
    val items: List<GoodsReceiptItemResult>,
)

sealed interface GoodsReceiptRemoteState {
    data object Idle : GoodsReceiptRemoteState
    data object ReadingKey : GoodsReceiptRemoteState
    data object Retrieving : GoodsReceiptRemoteState
    data object Waiting : GoodsReceiptRemoteState
    data class PreviewReady(val preview: GoodsReceiptPreview) : GoodsReceiptRemoteState
    data class ReviewRequired(val preview: GoodsReceiptPreview) : GoodsReceiptRemoteState
    data class Confirming(val preview: GoodsReceiptPreview) : GoodsReceiptRemoteState
    data class Confirmed(val result: GoodsReceiptResult) : GoodsReceiptRemoteState
    data class RetryableError(val message: String) : GoodsReceiptRemoteState
    data class TerminalError(val message: String) : GoodsReceiptRemoteState
}
