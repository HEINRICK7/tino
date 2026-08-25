package com.tino.app.domain.orders

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderDraftServiceTest {
    private val service = OrderDraftService(object : CatalogLookup {
        override suspend fun findProduct(name: String): CatalogItem? = when (name.lowercase()) {
            "café" -> CatalogItem("p1", "Café Maratá", 800)
            "leite" -> CatalogItem("p2", "Leite", 600)
            else -> null
        }
    })

    @Test
    fun whatsappOrderIsDraftedWithTotalsAndNeedsConfirmation() = runBlocking {
        val lines = WhatsAppOrderParser().parse("2 café, 3 leite")
        val draft = service.createDraft(lines, OrderChannel.WHATSAPP, FulfillmentType.DELIVERY, "Casa da Maria")

        assertEquals(3_400L, draft.totalCents)
        assertEquals("Casa da Maria", draft.addressReference)
        assertTrue(draft.status == OrderStatus.DRAFT)
        assertEquals(OrderStatus.CONFIRMED, service.confirm(draft, true).status)
    }

    @Test
    fun draftCannotBeConfirmedWithoutHumanConfirmation() = runBlocking {
        val draft = service.createDraft(listOf(IncomingOrderLine("café", 1)))
        try {
            service.confirm(draft, false)
            fail("Expected confirmation to be required")
        } catch (error: IllegalStateException) {
            assertTrue(error.message?.contains("confirmação") == true)
        }
    }
}
