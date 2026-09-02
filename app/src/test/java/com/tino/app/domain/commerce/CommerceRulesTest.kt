package com.tino.app.domain.commerce

import org.junit.Assert.assertEquals
import org.junit.Test

class CommerceRulesTest {
    @Test
    fun calculatesSaleInCents() {
        assertEquals(1_599L, CommerceRules.saleTotal(533L, 3, 10, "Café"))
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsSaleAboveAvailableStock() {
        CommerceRules.saleTotal(500L, 3, 2, "Café")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveQuantity() {
        CommerceRules.saleTotal(500L, 0, 2, "Café")
    }

    @Test
    fun allowsMadeToOrderSaleWithoutStock() {
        assertEquals(1_500L, CommerceRules.saleTotal(500L, 3, 0, "Bolo", stockTracked = false))
    }
}
