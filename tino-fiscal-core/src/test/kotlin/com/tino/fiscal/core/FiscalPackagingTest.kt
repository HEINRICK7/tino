package com.tino.fiscal.core

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FiscalPackagingTest {
    @Test
    fun confirmedPackageMappingConvertsFiscalQuantityToStockQuantity() {
        val result = FiscalPackagingResolver().resolve(
            productId = "coffee",
            supplierId = "supplier",
            fiscalQuantity = BigDecimal("2"),
            fiscalUnit = "CX",
            packagings = listOf(
                ProductPackaging("coffee", "supplier", "CX", BigDecimal("12"), confirmed = true),
            ),
        )

        val known = assertIs<FiscalPackagingResolution.Known>(result)
        assertTrue(known.stockQuantity.compareTo(BigDecimal("24")) == 0)
        assertEquals(BigDecimal("12"), known.packaging.unitsPerPackage)
    }

    @Test
    fun unknownPackageContentsRequireHumanConfirmation() {
        val result = FiscalPackagingResolver().resolve(
            productId = "coffee",
            supplierId = "supplier",
            fiscalQuantity = BigDecimal("2"),
            fiscalUnit = "CX",
            packagings = emptyList(),
        )

        val pending = assertIs<FiscalPackagingResolution.RequiresConfirmation>(result)
        assertEquals(BigDecimal("2"), pending.fiscalQuantity)
        assertEquals("CX", pending.fiscalUnit)
        assertTrue(pending.options.isEmpty())
    }

    @Test
    fun unconfirmedMappingCannotCreateFakeStockQuantity() {
        val result = FiscalPackagingResolver().resolve(
            productId = "coffee",
            supplierId = "supplier",
            fiscalQuantity = BigDecimal("2"),
            fiscalUnit = "CX",
            packagings = listOf(
                ProductPackaging("coffee", "supplier", "CX", BigDecimal("12"), confirmed = false),
            ),
        )

        assertIs<FiscalPackagingResolution.RequiresConfirmation>(result)
    }

    @Test
    fun genericConfirmedMappingCanBeUsedWhenSupplierSpecificMappingIsAbsent() {
        val result = FiscalPackagingResolver().resolve(
            productId = "coffee",
            supplierId = "supplier-b",
            fiscalQuantity = BigDecimal("3"),
            fiscalUnit = "cx",
            packagings = listOf(
                ProductPackaging("coffee", null, "CX", BigDecimal("10"), confirmed = true),
            ),
        )

        val known = assertIs<FiscalPackagingResolution.Known>(result)
        assertTrue(known.stockQuantity.compareTo(BigDecimal("30")) == 0)
    }
}
