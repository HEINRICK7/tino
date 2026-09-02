package com.tino.app.domain.nfce

import java.math.BigDecimal
import java.time.LocalDateTime

/** Canonical purchase data produced by a fiscal source, independent of HTML or SEFAZ. */
data class PurchaseDocument(
    val source: Source,
    val documentType: DocumentType,
    val accessKey: String,
    val issuedAt: LocalDateTime?,
    val issuer: PurchaseIssuer,
    val items: List<PurchaseItem>,
    val total: BigDecimal?,
) {
    enum class Source { NFCE }
    enum class DocumentType { NFCE }
}

data class PurchaseIssuer(
    val name: String?,
    val taxId: String?,
)

data class PurchaseItem(
    val lineNumber: Int,
    val externalCode: String?,
    val gtin: String?,
    val description: String,
    val quantity: BigDecimal?,
    val unit: String?,
    val unitPrice: BigDecimal?,
    val totalPrice: BigDecimal?,
)
