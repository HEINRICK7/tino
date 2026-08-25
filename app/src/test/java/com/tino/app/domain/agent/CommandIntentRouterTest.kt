package com.tino.app.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandIntentRouterTest {
    private val router = CommandIntentRouter()

    @Test
    fun routesAddToAccountWithoutInventingEntityFacts() {
        val result = router.route("adicionar um café maratá na conta da dona Maria Lina")

        assertTrue(result is CommandIntentResult.Match)
        val match = result as CommandIntentResult.Match
        assertEquals(TinoToolId.CREDIT_ADD, match.tool)
        assertEquals(AgentCapability.ADD_CREDIT_ITEM, match.intent.capability)
        assertEquals("maria lina", match.intent.customerRef)
        assertEquals("cafe marata", match.intent.productRef)
        assertEquals(1, match.intent.quantity)
    }

    @Test
    fun routesCommonCreditPhrasesToTheSameIntent() {
        val phrases = listOf(
            "Maria Lina comprou fiado um café Maratá",
            "Maria Lina levou um café Maratá fiado",
            "anota um café Maratá pra Maria Lina",
        )

        phrases.forEach { phrase ->
            val result = router.route(phrase) as CommandIntentResult.Match
            assertEquals(AgentCapability.ADD_CREDIT_ITEM, result.intent.capability)
            assertEquals("maria lina", result.intent.customerRef)
            assertEquals("cafe marata", result.intent.productRef)
            assertEquals(1, result.intent.quantity)
        }
    }

    @Test
    fun leavesComplexOrNonCreditTextForGemma() {
        assertTrue(router.route("quanto entrou hoje") is CommandIntentResult.NoMatch)
        assertTrue(router.route("Maria Lina comprou café ontem") is CommandIntentResult.NoMatch)
    }

    @Test
    fun routesCreditPaymentWithAmountAndMethod() {
        val result = router.route("Maria Lina pagou 10 reais no pix")

        assertTrue(result is CommandIntentResult.Match)
        val match = result as CommandIntentResult.Match
        assertEquals(TinoToolId.CREDIT_PAYMENT, match.tool)
        assertEquals(AgentCapability.REGISTER_CREDIT_PAYMENT, match.intent.capability)
        assertEquals("maria lina", match.intent.customerRef)
        assertEquals(1_000L, match.intent.amountCents)
        assertEquals(com.tino.app.domain.commerce.PaymentMethod.PIX, match.intent.creditPaymentMethod)
    }

    @Test
    fun ignoresPaymentWithoutCustomerReference() {
        assertTrue(router.route("recebi 10 reais no pix") is CommandIntentResult.NoMatch)
    }
}
