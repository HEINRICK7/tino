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
}
