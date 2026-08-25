package com.tino.app.domain.fiscal

import java.math.BigDecimal

data class FiscalLineItem(
    val productCode: String?,
    val barcode: String?,
    val description: String,
    val ncm: String?,
    val unit: String,
    val quantity: BigDecimal,
    val unitCostCents: Long,
)

data class ParsedFiscalDocument(
    val accessKey: String?,
    val supplierName: String,
    val totalCents: Long,
    val items: List<FiscalLineItem>,
    val source: String,
    val rawXml: String,
)

interface FiscalProvider {
    suspend fun issue(document: FiscalDocumentDraft): FiscalResult
}

data class FiscalDocumentDraft(
    val saleId: String,
    val customerDocument: String?,
    val totalCents: Long,
)

sealed interface FiscalResult {
    data class Issued(val accessKey: String, val protocol: String) : FiscalResult
    data class Unavailable(val reason: String) : FiscalResult
}
