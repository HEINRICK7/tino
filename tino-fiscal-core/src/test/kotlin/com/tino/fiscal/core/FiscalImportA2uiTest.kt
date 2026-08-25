package com.tino.fiscal.core

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FiscalImportA2uiTest {
    private val document = assertIs<FiscalParseResult.Success>(
        FiscalXmlParser().parse(
            javaClass.getResourceAsStream("/fixture-nfe-purchase-001.xml")!!.readBytes(),
        ),
    ).document

    @Test
    fun mapsPreviewToTypedFiscalImportSurfaceWithoutCommitAction() {
        val preview = FiscalImportPreviewBuilder().build(
            document = document,
            suppliers = emptyList(),
            products = listOf(
                FiscalProductCandidate("coffee", "Café Maratá 250g", "7891234567890", "UN", BigDecimal.ZERO),
            ),
        )

        val message = FiscalImportA2uiMapper().map(preview)

        assertEquals("tino.fiscal.a2ui.v1", message.schema)
        assertEquals("fiscal_import_summary", message.component)
        assertEquals(2, message.summary.totalItems)
        assertEquals(1, message.summary.existingItems)
        assertEquals(1, message.summary.newItems)
        assertEquals(BigDecimal("208.80"), message.summary.invoiceValue)
        assertIs<FiscalImportA2uiItem.Existing>(message.items[0])
        assertIs<FiscalImportA2uiItem.New>(message.items[1])
        assertTrue(FiscalImportA2uiAction.REVIEW in message.actions)
        assertFalse(FiscalImportA2uiAction.entries.any { it.name == "COMMIT" && it in message.actions })
    }

    @Test
    fun preservesAmbiguityAndWarningsInSurface() {
        val preview = FiscalImportPreviewBuilder().build(
            document = document,
            suppliers = listOf(
                FiscalSupplierCandidate("one", document.issuer.taxId, "One", null),
                FiscalSupplierCandidate("two", document.issuer.taxId, "Two", null),
            ),
            products = listOf(
                FiscalProductCandidate("coffee-a", "Café A", "7891234567890", "UN", BigDecimal.ZERO),
                FiscalProductCandidate("coffee-b", "Café B", "7891234567890", "UN", BigDecimal.ZERO),
            ),
        )

        val message = FiscalImportA2uiMapper().map(preview)

        assertEquals(FiscalImportA2uiSupplierStatus.AMBIGUOUS, message.supplier.status)
        assertEquals(1, message.summary.ambiguousItems)
        assertTrue("AMBIGUOUS_SUPPLIER_REQUIRES_SELECTION" in message.warnings)
        assertTrue("AMBIGUOUS_PRODUCT_REQUIRES_SELECTION" in message.warnings)
    }
}
