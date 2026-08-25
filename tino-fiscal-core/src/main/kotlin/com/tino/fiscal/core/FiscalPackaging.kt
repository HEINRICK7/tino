package com.tino.fiscal.core

import java.math.BigDecimal
import java.util.Locale

/**
 * A packaging mapping is a learned/confirmed fact, never an assumption made
 * from the fiscal unit name alone.
 */
data class ProductPackaging(
    val productId: String,
    val supplierId: String?,
    val fiscalUnit: String,
    val unitsPerPackage: BigDecimal?,
    val confirmed: Boolean,
)

sealed interface FiscalPackagingResolution {
    data class Known(
        val stockQuantity: BigDecimal,
        val packaging: ProductPackaging,
    ) : FiscalPackagingResolution

    data class RequiresConfirmation(
        val productId: String,
        val fiscalQuantity: BigDecimal,
        val fiscalUnit: String,
        val options: List<ProductPackaging>,
    ) : FiscalPackagingResolution
}

/** Resolves only confirmed conversions and never guesses package contents. */
class FiscalPackagingResolver {
    fun resolve(
        productId: String,
        supplierId: String?,
        fiscalQuantity: BigDecimal,
        fiscalUnit: String,
        packagings: List<ProductPackaging>,
    ): FiscalPackagingResolution {
        val normalizedUnit = normalizeUnit(fiscalUnit)
        val options = packagings.filter {
            it.productId == productId && normalizeUnit(it.fiscalUnit) == normalizedUnit
        }
        val preferred = options.filter { it.supplierId == supplierId }
            .ifEmpty { options.filter { it.supplierId == null } }
            .filter { it.confirmed && it.unitsPerPackage != null && it.unitsPerPackage > BigDecimal.ZERO }

        return if (preferred.size == 1) {
            val packaging = preferred.single()
            FiscalPackagingResolution.Known(
                stockQuantity = fiscalQuantity * packaging.unitsPerPackage!!,
                packaging = packaging,
            )
        } else {
            FiscalPackagingResolution.RequiresConfirmation(
                productId = productId,
                fiscalQuantity = fiscalQuantity,
                fiscalUnit = fiscalUnit,
                options = options,
            )
        }
    }
}

private fun normalizeUnit(value: String): String = value.trim().uppercase(Locale.ROOT)
