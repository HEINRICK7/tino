package com.tino.app

import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.ui.components.TinoNavDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class TinoNavigationTest {
    @Test
    fun customerOnlyProfileKeepsHomeAndMoreAndHidesCommercialTabs() {
        assertEquals(
            setOf(TinoNavDestination.Hoje, TinoNavDestination.Mais),
            visibleNavigationDestinations(setOf(TinoCapabilityId.LIST_CUSTOMERS)),
        )
    }

    @Test
    fun inventoryCapabilityAddsOnlyProductsTab() {
        assertEquals(
            setOf(TinoNavDestination.Hoje, TinoNavDestination.Produtos, TinoNavDestination.Mais),
            visibleNavigationDestinations(setOf(TinoCapabilityId.LIST_PRODUCTS)),
        )
    }

    @Test
    fun receivablesCapabilityAddsFiadoTab() {
        assertEquals(
            setOf(TinoNavDestination.Hoje, TinoNavDestination.Fiado, TinoNavDestination.Mais),
            visibleNavigationDestinations(setOf(TinoCapabilityId.LIST_RECEIVABLES)),
        )
    }

    @Test
    fun protectedScreensDeclareTheSameCapabilitiesUsedByRuntimeNavigation() {
        assertEquals(TinoCapabilityId.NAVIGATE, TinoScreen.QuickSale.requiredCapability())
        assertEquals(TinoCapabilityId.LIST_PRODUCTS, TinoScreen.ProductDetail.requiredCapability())
        assertEquals(TinoCapabilityId.REGISTER_STOCK_ENTRY, TinoScreen.StockEntry.requiredCapability())
        assertEquals(TinoCapabilityId.LIST_RECEIVABLES, TinoScreen.ReceivePayment.requiredCapability())
        assertEquals(TinoCapabilityId.LIST_CUSTOMERS, TinoScreen.CustomerDetail.requiredCapability())
        assertEquals(null, TinoScreen.Settings.requiredCapability())
    }
}
