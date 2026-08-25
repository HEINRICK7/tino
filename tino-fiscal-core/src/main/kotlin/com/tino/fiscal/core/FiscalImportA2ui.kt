package com.tino.fiscal.core

import java.math.BigDecimal

/** TINO-owned declarative contract; it is not the official Google A2UI protocol. */
data class FiscalImportA2uiMessage(
    val schema: String = "tino.fiscal.a2ui.v1",
    val surfaceId: String,
    val component: String,
    val title: String,
    val supplier: FiscalImportA2uiSupplier,
    val summary: FiscalImportA2uiSummary,
    val items: List<FiscalImportA2uiItem>,
    val warnings: List<String>,
    val actions: List<FiscalImportA2uiAction>,
)

data class FiscalImportA2uiSupplier(
    val label: String?,
    val status: FiscalImportA2uiSupplierStatus,
)

enum class FiscalImportA2uiSupplierStatus {
    EXISTING,
    NEW,
    AMBIGUOUS,
}

data class FiscalImportA2uiSummary(
    val totalItems: Int,
    val existingItems: Int,
    val newItems: Int,
    val ambiguousItems: Int,
    val packagingItems: Int,
    val invoiceValue: BigDecimal?,
)

sealed interface FiscalImportA2uiItem {
    val description: String

    data class Existing(
        override val description: String,
        val productId: String,
        val quantity: BigDecimal,
        val fiscalUnit: String?,
        val purchaseUnitValue: BigDecimal,
        val matchMethod: ProductMatchMethod,
    ) : FiscalImportA2uiItem

    data class New(
        override val description: String,
        val gtin: String?,
        val ncm: String?,
        val quantity: BigDecimal,
        val fiscalUnit: String?,
        val purchaseUnitValue: BigDecimal,
    ) : FiscalImportA2uiItem

    data class Ambiguous(
        override val description: String,
        val candidates: List<ProductMatchCandidate>,
    ) : FiscalImportA2uiItem

    data class PackagingRequired(
        override val description: String,
        val quantity: BigDecimal,
        val fiscalUnit: String,
        val productId: String,
    ) : FiscalImportA2uiItem
}

enum class FiscalImportA2uiAction {
    REVIEW,
    CANCEL,
}

class FiscalImportA2uiMapper {
    fun map(preview: FiscalImportPreview): FiscalImportA2uiMessage {
        val items = preview.items.map { item ->
            when (item) {
                is FiscalItemImportPreview.ExistingProduct -> FiscalImportA2uiItem.Existing(
                    description = item.name,
                    productId = item.productId,
                    quantity = item.incomingQuantity,
                    fiscalUnit = item.fiscalUnit,
                    purchaseUnitValue = item.purchaseUnitValue,
                    matchMethod = item.matchMethod,
                )

                is FiscalItemImportPreview.NewProduct -> FiscalImportA2uiItem.New(
                    description = item.supplierDescription,
                    gtin = item.gtin,
                    ncm = item.ncm,
                    quantity = item.fiscalQuantity,
                    fiscalUnit = item.fiscalUnit,
                    purchaseUnitValue = item.purchaseUnitValue,
                )

                is FiscalItemImportPreview.AmbiguousProduct -> FiscalImportA2uiItem.Ambiguous(
                    description = item.supplierDescription,
                    candidates = item.candidates,
                )

                is FiscalItemImportPreview.PackagingRequired -> FiscalImportA2uiItem.PackagingRequired(
                    description = item.supplierDescription,
                    quantity = item.fiscalQuantity,
                    fiscalUnit = item.fiscalUnit,
                    productId = item.matchedProduct.id,
                )
            }
        }

        val supplier = when (val supplier = preview.supplier) {
            is FiscalImportSupplierPreview.Existing -> FiscalImportA2uiSupplier(
                label = supplier.name,
                status = FiscalImportA2uiSupplierStatus.EXISTING,
            )

            is FiscalImportSupplierPreview.NewSupplier -> FiscalImportA2uiSupplier(
                label = supplier.tradeName ?: supplier.legalName,
                status = FiscalImportA2uiSupplierStatus.NEW,
            )

            is FiscalImportSupplierPreview.Ambiguous -> FiscalImportA2uiSupplier(
                label = supplier.tradeName ?: supplier.legalName,
                status = FiscalImportA2uiSupplierStatus.AMBIGUOUS,
            )
        }

        val summary = FiscalImportA2uiSummary(
            totalItems = items.size,
            existingItems = items.count { it is FiscalImportA2uiItem.Existing },
            newItems = items.count { it is FiscalImportA2uiItem.New },
            ambiguousItems = items.count { it is FiscalImportA2uiItem.Ambiguous },
            packagingItems = items.count { it is FiscalImportA2uiItem.PackagingRequired },
            invoiceValue = preview.invoiceValue,
        )

        return FiscalImportA2uiMessage(
            surfaceId = preview.previewId,
            component = "fiscal_import_summary",
            title = "Mercadoria encontrada",
            supplier = supplier,
            summary = summary,
            items = items,
            warnings = preview.warnings.map(FiscalImportWarning::name),
            actions = listOf(FiscalImportA2uiAction.REVIEW, FiscalImportA2uiAction.CANCEL),
        )
    }
}
