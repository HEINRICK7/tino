package com.tino.app.domain.agent

import com.tino.app.domain.language.EntityReference
import com.tino.app.domain.language.LanguageEntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TinoContextCatalogTest {
    @Test
    fun homeShowsFewPriorityActionsAndNeverInventsCapabilities() {
        val catalog = TinoContextCatalog.forContext(ScreenAgentContext("Home"))
        assertEquals("Hoje", catalog.title)
        assertEquals(
            listOf("Quem está devendo?", "Quanto entrou hoje?", "O que está acabando?"),
            catalog.primary.map { it.title },
        )
        assertTrue(catalog.more.any { it.capability == AgentCapability.LIST_OVERDUE })
        assertTrue(catalog.primary.none { it.speak })
        assertTrue(catalog.primary.all { it.capability != null })
    }

    @Test
    fun customersCatalogPrefersDebtorsAndKeepsMutationsBehindTheFoldOrMarked() {
        val catalog = TinoContextCatalog.forContext(ScreenAgentContext("Customers"))
        assertEquals("Clientes", catalog.title)
        assertEquals("Quem está devendo?", catalog.primary.first().title)
        assertEquals(AgentCapability.LIST_RECEIVABLES, catalog.primary.first().capability)
        assertTrue(catalog.primary.any { it.capability == AgentCapability.REGISTER_CREDIT_PAYMENT && it.mutation })
        assertFalse(catalog.primary.any { it.requiresEntity })
    }

    @Test
    fun selectedCustomerReplacesGenericScreenActions() {
        val catalog = TinoContextCatalog.forContext(
            ScreenAgentContext(
                screen = "CustomerDetail",
                primaryEntity = EntityReference(LanguageEntityType.CUSTOMER, "Maria Lina"),
            ),
        )
        assertEquals("Maria Lina", catalog.title)
        assertEquals("Quanto Maria Lina deve?", catalog.primary.first().title)
        assertEquals(AgentCapability.GET_CUSTOMER_BALANCE, catalog.primary.first().capability)
        assertTrue(catalog.primary.all { it.requiresEntity || it.capability == AgentCapability.GET_CUSTOMER_BALANCE })
        assertFalse(catalog.primary.any { it.title == "Quem está devendo?" })
    }

    @Test
    fun productActionsThatNeedAnEntityStayHiddenOnTheList() {
        val catalog = TinoContextCatalog.forContext(ScreenAgentContext("Products"))
        assertTrue(catalog.primary.none { it.requiresEntity })
        assertTrue((catalog.primary + catalog.more).none { it.capability == AgentCapability.GET_PRODUCT_STOCK })
    }

    @Test
    fun productDetailWithEntityPrioritizesStockAndPrice() {
        val catalog = TinoContextCatalog.forContext(
            ScreenAgentContext(
                screen = "ProductDetail",
                primaryEntity = EntityReference(LanguageEntityType.PRODUCT, "Café Maratá"),
            ),
        )
        assertEquals("Café Maratá", catalog.title)
        assertEquals(AgentCapability.GET_PRODUCT_STOCK, catalog.primary.first().capability)
        assertTrue(catalog.primary.any { it.capability == AgentCapability.GET_PRODUCT_PRICE })
    }

    @Test
    fun unavailableCapabilitiesAreFilteredOut() {
        val catalog = TinoContextCatalog.forContext(
            ScreenAgentContext(
                screen = "Customers",
                availableCapabilities = setOf(TinoCapabilityId.LIST_CUSTOMERS),
            ),
        )
        val capabilities = (catalog.primary + catalog.more).mapNotNull { it.capability }
        assertEquals(listOf(AgentCapability.LIST_CUSTOMERS), capabilities)
    }

    @Test
    fun componentTagsCanSpecializeTheRanking() {
        val catalog = TinoContextCatalog.forContext(
            ScreenAgentContext(
                screen = "Home",
                tags = setOf("LOW_STOCK"),
            ),
        )
        assertEquals("O que está acabando?", catalog.primary.first().title)
        assertEquals(AgentCapability.REPLENISHMENT_QUERY, catalog.primary.first().capability)
    }

    @Test
    fun catalogNeverExceedsThePrimaryLimit() {
        val catalog = TinoContextCatalog.forContext(ScreenAgentContext("CreditList"))
        assertEquals(TinoContextCatalog.PRIMARY_LIMIT, catalog.primary.size)
        assertTrue(catalog.more.isNotEmpty())
    }
}
