package com.tino.app

import com.tino.app.core.database.CustomerBalance
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.intelligence.TinoEvidenceProduct
import com.tino.app.domain.intelligence.TinoEvidenceSnapshot
import com.tino.app.domain.language.LanguageEntityType
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

    @Test
    fun productDetailIsDeeperThanTheStockList() {
        assertEquals(1, TinoScreen.Products.transitionLayer())
        assertEquals(2, TinoScreen.ProductDetail.transitionLayer())
        assertEquals(3, TinoScreen.AdjustStock.transitionLayer())
    }

    @Test
    fun screenContextTracksTheOpenEntityOnCustomerDetail() {
        val context = tinoScreenAgentContext(
            screen = TinoScreen.CustomerDetail,
            selectedCustomer = CustomerBalance(
                id = "maria-1",
                name = "Maria Lina",
                phone = null,
                balanceCents = 1_500,
            ),
            selectedProduct = null,
            activeCapabilities = setOf(TinoCapabilityId.LIST_CUSTOMERS, TinoCapabilityId.READ_CUSTOMER_BALANCE),
        )
        assertEquals("CustomerDetail", context.screen)
        assertEquals("maria-1", context.activeCustomerId)
        assertEquals("Maria Lina", context.primaryEntity?.text)
        assertEquals(LanguageEntityType.CUSTOMER, context.primaryEntity?.type)
    }

    @Test
    fun doesNotReuseAnotherScreenSnapshotAsIntelligenceContext() {
        val homeSnapshot = TinoEvidenceSnapshot(
            screen = TinoScreen.Home.name,
            products = listOf(TinoEvidenceProduct("p1", "Café", stockQuantity = 1)),
        )

        assertEquals(
            emptyList<Any>(),
            visibleIntelligenceThoughts(
                screen = TinoScreen.Products,
                snapshot = homeSnapshot,
                attentionItems = emptyList(),
                attentionInitialized = false,
            ),
        )
    }

    @Test
    fun screenSnapshotUsesTheFullEvidenceEngineForItsOwnContext() {
        val productsSnapshot = TinoEvidenceSnapshot(
            screen = TinoScreen.Products.name,
            products = listOf(TinoEvidenceProduct("p1", "Café", stockQuantity = 1)),
        )

        val thoughts = visibleIntelligenceThoughts(
            screen = TinoScreen.Products,
            snapshot = productsSnapshot,
            attentionItems = emptyList(),
            attentionInitialized = false,
        )

        assertEquals("p1", thoughts.single().subjectId)
    }
}
