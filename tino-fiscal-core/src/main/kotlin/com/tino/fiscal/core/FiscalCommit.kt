package com.tino.fiscal.core

import java.math.BigDecimal
import java.time.Instant

sealed interface FiscalSupplierCommitDecision {
    data class UseExisting(val supplierId: String) : FiscalSupplierCommitDecision

    data class Create(
        val legalName: String?,
        val tradeName: String?,
        val taxId: String?,
    ) : FiscalSupplierCommitDecision
}

sealed interface FiscalItemCommitDecision {
    val lineNumber: Int
    val stockQuantity: BigDecimal

    data class UseExisting(
        override val lineNumber: Int,
        val productId: String,
        override val stockQuantity: BigDecimal,
    ) : FiscalItemCommitDecision

    data class CreateProduct(
        override val lineNumber: Int,
        val productName: String,
        val salePriceCents: Long,
        val inventoryUnit: String,
        override val stockQuantity: BigDecimal,
    ) : FiscalItemCommitDecision
}

data class FiscalImportConfirmation(
    val operationId: String,
    val confirmedAt: Instant,
    val humanConfirmed: Boolean,
    val supplier: FiscalSupplierCommitDecision,
    val items: List<FiscalItemCommitDecision>,
    val payableConfirmed: Boolean = false,
)

data class FiscalCommitItemPlan(
    val lineNumber: Int,
    val productId: String?,
    val newProductName: String?,
    val newProductSalePriceCents: Long?,
    val inventoryUnit: String,
    val fiscalQuantity: BigDecimal,
    val stockQuantity: Int,
    val fiscalUnit: String?,
    val supplierProductCode: String?,
    val gtin: String?,
    val ncm: String?,
    val description: String,
    val unitCostCents: Long,
    val totalCostCents: Long,
)

sealed interface FiscalSupplierCommitPlan {
    data class Existing(val supplierId: String) : FiscalSupplierCommitPlan

    data class New(
        val legalName: String?,
        val tradeName: String?,
        val taxId: String?,
    ) : FiscalSupplierCommitPlan
}

data class FiscalImportCommitPlan(
    val operationId: String,
    val documentId: String,
    val supplier: FiscalSupplierCommitPlan,
    val items: List<FiscalCommitItemPlan>,
    val invoiceTotalCents: Long,
    val confirmedAt: Instant,
)

sealed interface FiscalCommitValidationResult {
    data class Valid(val plan: FiscalImportCommitPlan) : FiscalCommitValidationResult

    data class Rejected(val reasons: List<String>) : FiscalCommitValidationResult
}

/**
 * The commit boundary is fail-closed. It validates the human decision against
 * the exact preview and converts decimal fiscal values without rounding.
 */
class FiscalImportCommitValidator {
    fun validate(
        document: CanonicalFiscalDocument,
        preview: FiscalImportPreview,
        confirmation: FiscalImportConfirmation,
    ): FiscalCommitValidationResult {
        val reasons = mutableListOf<String>()
        if (!confirmation.humanConfirmed) reasons += "HUMAN_CONFIRMATION_REQUIRED"
        if (confirmation.operationId.isBlank()) reasons += "OPERATION_ID_REQUIRED"
        if (confirmation.payableConfirmed) reasons += "PAYABLE_COMMIT_NOT_SUPPORTED_IN_THIS_SLICE"
        if (!preview.canCommit) reasons += "PREVIEW_NOT_COMMITTABLE"
        if (document.items.size != confirmation.items.size) reasons += "ITEM_DECISION_COUNT_MISMATCH"

        val supplierPlan = validateSupplier(preview.supplier, confirmation.supplier, reasons)
        val itemPlans = document.items.mapIndexed { index, item ->
            val decision = confirmation.items.firstOrNull { it.lineNumber == item.lineNumber }
            if (decision == null) {
                reasons += "MISSING_ITEM_DECISION:${item.lineNumber}"
                return@mapIndexed null
            }
            if (decision.lineNumber != item.lineNumber) {
                reasons += "ITEM_LINE_MISMATCH:${item.lineNumber}"
                return@mapIndexed null
            }
            val previewItem = preview.items.getOrNull(index)
            if (previewItem == null) {
                reasons += "MISSING_PREVIEW_ITEM:${item.lineNumber}"
                return@mapIndexed null
            }
            validateItem(item, previewItem, decision, reasons)
        }.filterNotNull()

        if (reasons.isNotEmpty() || supplierPlan == null || itemPlans.size != document.items.size) {
            return FiscalCommitValidationResult.Rejected(reasons.distinct())
        }

        val invoiceTotalCents = exactCents(document.totals.invoiceValue, "invoice total", reasons)
        if (invoiceTotalCents == null) return FiscalCommitValidationResult.Rejected(reasons.distinct())

        return FiscalCommitValidationResult.Valid(
            FiscalImportCommitPlan(
                operationId = confirmation.operationId,
                documentId = document.id,
                supplier = supplierPlan,
                items = itemPlans,
                invoiceTotalCents = invoiceTotalCents,
                confirmedAt = confirmation.confirmedAt,
            ),
        )
    }

    private fun validateSupplier(
        preview: FiscalImportSupplierPreview,
        decision: FiscalSupplierCommitDecision,
        reasons: MutableList<String>,
    ): FiscalSupplierCommitPlan? = when (preview) {
        is FiscalImportSupplierPreview.Ambiguous -> {
            reasons += "AMBIGUOUS_SUPPLIER"
            null
        }

        is FiscalImportSupplierPreview.Existing -> when (decision) {
            is FiscalSupplierCommitDecision.UseExisting -> {
                if (decision.supplierId != preview.supplierId) reasons += "SUPPLIER_DECISION_MISMATCH"
                FiscalSupplierCommitPlan.Existing(decision.supplierId)
            }

            is FiscalSupplierCommitDecision.Create -> {
                reasons += "EXISTING_SUPPLIER_CANNOT_BE_RECREATED"
                null
            }
        }

        is FiscalImportSupplierPreview.NewSupplier -> when (decision) {
            is FiscalSupplierCommitDecision.Create -> {
                if (decision.legalName.isNullOrBlank() && decision.tradeName.isNullOrBlank()) {
                    reasons += "SUPPLIER_NAME_REQUIRED"
                }
                FiscalSupplierCommitPlan.New(decision.legalName, decision.tradeName, decision.taxId)
            }

            is FiscalSupplierCommitDecision.UseExisting -> {
                reasons += "NEW_SUPPLIER_REQUIRES_CREATE_OR_EXPLICIT_MATCH"
                null
            }
        }
    }

    private fun validateItem(
        item: CanonicalFiscalItem,
        preview: FiscalItemImportPreview,
        decision: FiscalItemCommitDecision,
        reasons: MutableList<String>,
    ): FiscalCommitItemPlan? {
        val stockQuantity = exactWholeQuantity(decision.stockQuantity, item.lineNumber, reasons) ?: return null
        val unitCostCents = exactCents(item.unitValue, "item ${item.lineNumber} unit cost", reasons) ?: return null
        val totalCostCents = exactCents(item.totalValue, "item ${item.lineNumber} total cost", reasons) ?: return null

        return when (preview) {
            is FiscalItemImportPreview.AmbiguousProduct -> {
                reasons += "AMBIGUOUS_PRODUCT:${item.lineNumber}"
                null
            }

            is FiscalItemImportPreview.PackagingRequired -> {
                reasons += "PACKAGING_CONFIRMATION_REQUIRED:${item.lineNumber}"
                null
            }

            is FiscalItemImportPreview.ExistingProduct -> when (decision) {
                is FiscalItemCommitDecision.UseExisting -> {
                    if (decision.productId != preview.productId) reasons += "PRODUCT_DECISION_MISMATCH:${item.lineNumber}"
                    FiscalCommitItemPlan(
                        lineNumber = item.lineNumber,
                        productId = decision.productId,
                        newProductName = null,
                        newProductSalePriceCents = null,
                        inventoryUnit = preview.fiscalUnit ?: "UN",
                        fiscalQuantity = item.quantity,
                        stockQuantity = stockQuantity,
                        fiscalUnit = item.commercialUnit,
                        supplierProductCode = item.supplierProductCode,
                        gtin = item.gtin,
                        ncm = item.ncm,
                        description = item.description,
                        unitCostCents = unitCostCents,
                        totalCostCents = totalCostCents,
                    )
                }

                is FiscalItemCommitDecision.CreateProduct -> {
                    reasons += "EXISTING_PRODUCT_CANNOT_BE_RECREATED:${item.lineNumber}"
                    null
                }
            }

            is FiscalItemImportPreview.NewProduct -> when (decision) {
                is FiscalItemCommitDecision.CreateProduct -> {
                    if (decision.productName.isBlank()) reasons += "PRODUCT_NAME_REQUIRED:${item.lineNumber}"
                    if (decision.salePriceCents <= 0) reasons += "SALE_PRICE_REQUIRED:${item.lineNumber}"
                    if (decision.inventoryUnit.isBlank()) reasons += "INVENTORY_UNIT_REQUIRED:${item.lineNumber}"
                    FiscalCommitItemPlan(
                        lineNumber = item.lineNumber,
                        productId = null,
                        newProductName = decision.productName.trim(),
                        newProductSalePriceCents = decision.salePriceCents,
                        inventoryUnit = decision.inventoryUnit.trim(),
                        fiscalQuantity = item.quantity,
                        stockQuantity = stockQuantity,
                        fiscalUnit = item.commercialUnit,
                        supplierProductCode = item.supplierProductCode,
                        gtin = item.gtin,
                        ncm = item.ncm,
                        description = item.description,
                        unitCostCents = unitCostCents,
                        totalCostCents = totalCostCents,
                    )
                }

                is FiscalItemCommitDecision.UseExisting -> {
                    reasons += "NEW_PRODUCT_REQUIRES_CREATE_DECISION:${item.lineNumber}"
                    null
                }
            }
        }
    }

    private fun exactWholeQuantity(
        value: BigDecimal,
        lineNumber: Int,
        reasons: MutableList<String>,
    ): Int? = try {
        value.toBigIntegerExact().intValueExact().also {
            if (it <= 0) reasons += "STOCK_QUANTITY_MUST_BE_POSITIVE:$lineNumber"
        }
    } catch (_: ArithmeticException) {
        reasons += "NON_INTEGER_STOCK_QUANTITY:$lineNumber"
        null
    }

    private fun exactCents(
        value: BigDecimal?,
        label: String,
        reasons: MutableList<String>,
    ): Long? = try {
        value?.movePointRight(2)?.longValueExact()
            ?: run {
                reasons += "MISSING_$label"
                null
            }
    } catch (_: ArithmeticException) {
        reasons += "NON_EXACT_CENTS:$label"
        null
    }
}
