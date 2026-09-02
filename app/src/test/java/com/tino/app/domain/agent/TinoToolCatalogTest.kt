package com.tino.app.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TinoToolCatalogTest {
    @Test
    fun creditPreparationCatalogIsOfflineAndCannotCommit() {
        val descriptor = TinoToolCatalog.descriptor(TinoToolId.PREPARE_CREDIT_SALE)

        assertEquals(TinoToolMode.PREPARE_ONLY, descriptor.mode)
        assertEquals(TinoToolRisk.HIGH, descriptor.risk)
        assertEquals(TinoEntityResolution.MULTIPLE, descriptor.entityResolution)
        assertTrue(descriptor.offline)
        assertEquals("credit_sale_preview", descriptor.a2uiComponent)
        assertFalse(TinoToolCatalog.all.any { it.name == "commitCreditSale" })
    }

    @Test
    fun everyPublishedToolDeclaresItsSourceAndIsOfflineFirst() {
        assertTrue(TinoToolCatalog.all.isNotEmpty())
        assertTrue(TinoToolCatalog.all.all { it.sourceOfTruth.isNotBlank() })
        assertTrue(TinoToolCatalog.all.all { it.offline })
    }

    @Test
    fun stockEntryIsPrepareOnlyAndResolvesProductAndSupplierLocally() {
        val descriptor = TinoToolCatalog.descriptor(TinoToolId.STOCK_ENTRY)

        assertEquals(TinoToolMode.PREPARE_ONLY, descriptor.mode)
        assertEquals(TinoToolRisk.HIGH, descriptor.risk)
        assertEquals(TinoEntityResolution.MULTIPLE, descriptor.entityResolution)
        assertEquals("stock_entry_preview", descriptor.a2uiComponent)
        assertTrue(descriptor.arguments.containsAll(setOf("product_ref", "quantity", "unit_cost_cents", "supplier_ref")))
    }
}
