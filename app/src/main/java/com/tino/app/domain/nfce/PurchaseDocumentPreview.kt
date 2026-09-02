package com.tino.app.domain.nfce

import java.math.BigDecimal
import java.time.OffsetDateTime

/** Server preview of a canonical PurchaseDocument; it has no operational side effects. */
data class PurchaseDocumentPreview(
    val previewId: String,
    val documentId: String,
    val status: String,
    val version: Long,
    val source: PurchaseDocument.Source,
    val documentType: PurchaseDocument.DocumentType,
    val accessKey: String,
    val issuedAt: OffsetDateTime?,
    val issuer: PurchaseIssuer,
    val items: List<PurchaseItem>,
    val matches: List<PurchaseDocumentMatch>,
    val total: BigDecimal?,
    val summary: PurchaseDocumentPreviewSummary,
)

data class PurchaseDocumentPreviewSummary(
    val items: Int,
    val matched: Int,
    val newProducts: Int,
    val needsReview: Int,
    val purchaseTotal: BigDecimal?,
)

data class PurchaseDocumentMatch(
    val lineNumber: Int,
    val status: Status,
    val productId: String?,
    val candidateName: String?,
    val baseUnit: String?,
    val confidence: BigDecimal?,
    val requiresUserAction: Boolean,
) {
    enum class Status { EXACT_MATCH, HIGH_CONFIDENCE_MATCH, REVIEW_REQUIRED, NEW_PRODUCT }
}

data class PurchaseDocumentConfirmation(
    val previewVersion: Long,
    val items: List<PurchaseDocumentDecision>,
)

data class PurchaseDocumentDecision(
    val lineNumber: Int,
    val action: Action,
    val productId: String? = null,
    val conversionFactor: BigDecimal? = null,
    val baseUnit: String? = null,
) {
    enum class Action { USE_EXISTING, CREATE_PRODUCT, IGNORE }
}

data class PurchaseReceipt(
    val receiptId: String,
    val status: String,
    val itemCount: Int,
)

data class PurchaseHistory(
    val period: String,
    val from: OffsetDateTime,
    val to: OffsetDateTime,
    val purchases: List<PurchaseHistoryEntry>,
    val purchaseCount: Int,
    val itemCount: Int,
    val newProductCount: Int,
    val total: BigDecimal,
)

data class PurchaseHistoryEntry(
    val receiptId: String,
    val confirmedAt: OffsetDateTime,
    val issuerName: String?,
    val total: BigDecimal?,
    val itemCount: Int,
    val newProductCount: Int,
    val stockQuantity: BigDecimal?,
)

data class PurchaseHistoryDetail(
    val receiptId: String,
    val confirmedAt: OffsetDateTime,
    val issuerName: String?,
    val issuerTaxId: String?,
    val accessKey: String,
    val total: BigDecimal?,
    val items: List<PurchaseHistoryItem>,
)

data class PurchaseHistoryItem(
    val lineNumber: Int,
    val productId: String?,
    val description: String,
    val quantity: BigDecimal?,
    val unit: String?,
    val unitPrice: BigDecimal?,
    val stockQuantity: BigDecimal?,
    val matchStatus: String,
)

data class PurchaseInsight(
    val type: String,
    val message: String,
    val evidenceIds: List<String>,
)
