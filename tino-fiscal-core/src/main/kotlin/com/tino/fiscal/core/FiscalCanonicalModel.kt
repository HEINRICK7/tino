package com.tino.fiscal.core

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class FiscalSource {
    SEFAZ_XML,
    PROVIDED_XML,
    DANFE_BARCODE,
    DANFE_OCR,
    MANUAL,
}

enum class FiscalDocumentModel {
    NFE,
    UNKNOWN,
}

enum class FiscalOperationType {
    ENTRY,
    EXIT,
    UNKNOWN,
}

data class FiscalProvenance(
    val source: FiscalSource,
    val documentHashSha256: String,
    val parserVersion: String,
)

data class FiscalEvidence(
    val originalXml: ByteArray,
    val provenance: FiscalProvenance,
)

data class FiscalAddress(
    val street: String?,
    val number: String?,
    val neighborhood: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
)

data class FiscalParty(
    val taxId: String?,
    val stateRegistration: String?,
    val legalName: String?,
    val tradeName: String?,
    val address: FiscalAddress?,
)

data class FiscalItemTaxes(
    val totalTaxValue: BigDecimal?,
    val icmsValue: BigDecimal?,
    val pisValue: BigDecimal?,
    val cofinsValue: BigDecimal?,
)

data class CanonicalFiscalItem(
    val lineNumber: Int,
    val supplierProductCode: String?,
    val description: String,
    val gtin: String?,
    val ncm: String?,
    val cfop: String?,
    val commercialUnit: String?,
    val quantity: BigDecimal,
    val unitValue: BigDecimal,
    val totalValue: BigDecimal,
    val taxes: FiscalItemTaxes?,
    val provenance: FiscalProvenance,
)

data class FiscalTotals(
    val productsValue: BigDecimal?,
    val freightValue: BigDecimal?,
    val discountValue: BigDecimal?,
    val otherValue: BigDecimal?,
    val invoiceValue: BigDecimal?,
)

data class FiscalInstallment(
    val number: String?,
    val dueDate: LocalDate?,
    val value: BigDecimal?,
)

data class CanonicalFiscalDocument(
    val id: String,
    val accessKey: String?,
    val model: FiscalDocumentModel,
    val number: String?,
    val series: String?,
    val issuedAt: Instant?,
    val operationType: FiscalOperationType,
    val issuer: FiscalParty,
    val recipient: FiscalParty?,
    val items: List<CanonicalFiscalItem>,
    val totals: FiscalTotals,
    val installments: List<FiscalInstallment>,
    val evidence: FiscalEvidence,
)

sealed interface FiscalParseResult {
    data class Success(val document: CanonicalFiscalDocument) : FiscalParseResult
    data class Failure(val code: String, val message: String) : FiscalParseResult
}
