package com.tino.app.interfaceadapter.a2ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class A2uiSurfacePolicyTest {
    @Test
    fun confirmationsStayCompactAndRiseFromTheFooter() {
        val spec = A2uiSurfacePolicy.forComponent(TinoA2UiComponentCatalog.PAYMENT_PREVIEW)
        assertEquals(A2uiSurfaceKind.BOTTOM_RISE, spec.kind)
        assertEquals(A2uiSurfaceSize.COMPACT, spec.size)
        assertEquals(A2uiSurfaceStage.PEEK, A2uiSurfacePolicy.initialStage(spec.size))
    }

    @Test
    fun customerAndProductListsUseALargeBottomRise() {
        val customers = A2uiSurfacePolicy.forComponent(TinoA2UiComponentCatalog.CUSTOMER_LIST)
        val products = A2uiSurfacePolicy.forComponent(TinoA2UiComponentCatalog.PRODUCT_LIST)
        assertEquals(A2uiSurfaceKind.BOTTOM_RISE, customers.kind)
        assertEquals(A2uiSurfaceSize.LARGE, customers.size)
        assertEquals(A2uiSurfaceSize.LARGE, products.size)
        assertEquals(A2uiSurfaceStage.EXPANDED, A2uiSurfacePolicy.initialStage(customers.size))
    }

    @Test
    fun timelineAndBalanceUseAMediumPanel() {
        val timeline = A2uiSurfacePolicy.forComponent(TinoA2UiComponentCatalog.CUSTOMER_TIMELINE_CARD)
        val balance = A2uiSurfacePolicy.forComponent(TinoA2UiComponentCatalog.CUSTOMER_BALANCE_CARD)
        assertEquals(A2uiSurfaceSize.MEDIUM, timeline.size)
        assertEquals(A2uiSurfaceSize.MEDIUM, balance.size)
        assertEquals(A2uiSurfaceKind.BOTTOM_RISE, timeline.kind)
    }

    @Test
    fun agentNeverSelectsCoordinatesOrCurves() {
        val peek = A2uiSurfacePolicy.fraction(A2uiSurfaceStage.PEEK, A2uiSurfaceSize.LARGE)
        val expanded = A2uiSurfacePolicy.fraction(A2uiSurfaceStage.EXPANDED, A2uiSurfaceSize.LARGE)
        val full = A2uiSurfacePolicy.fraction(A2uiSurfaceStage.FULL, A2uiSurfaceSize.LARGE)
        assertEquals(true, peek < expanded)
        assertEquals(true, expanded < full)
        assertNotEquals(A2uiSurfaceKind.FLOATING, A2uiSurfacePolicy.forComponent("unknown.catalog").kind)
    }

    @Test
    fun errorsAndEmptyStatesStayCompact() {
        val error = A2uiSurfacePolicy.forComponent(TinoA2UiComponentCatalog.ERROR_RECOVERY)
        val empty = A2uiSurfacePolicy.forComponent(TinoCustomComponentCatalog.EMPTY_STATE_CARD)
        assertEquals(A2uiSurfaceSize.COMPACT, error.size)
        assertEquals(A2uiSurfaceSize.COMPACT, empty.size)
    }
}
