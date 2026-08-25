package com.tino.app.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalCommandRouterTest {
    private val router = GlobalCommandRouter()

    @Test
    fun routesSaleWithQuantityAndPaymentMethod() {
        val call = router.route("vendi três cafés no PIX")

        assertEquals(CommerceToolName.REGISTER_SALE, call?.name)
        assertEquals("3", call?.arguments?.get("quantity"))
        assertEquals("cafes", call?.arguments?.get("product"))
        assertEquals("pix", call?.arguments?.get("payment_method"))
    }

    @Test
    fun routesStockReceiptOnlyWhenCostIsExplicit() {
        val call = router.route("chegaram 24 cafés por 5 reais")

        assertEquals(CommerceToolName.REGISTER_STOCK_RECEIPT, call?.name)
        assertEquals("24", call?.arguments?.get("quantity"))
        assertEquals("cafes", call?.arguments?.get("product"))
        assertEquals("500", call?.arguments?.get("unit_cost_cents"))
        assertNull(router.route("chegaram 24 cafés"))
    }

    @Test
    fun routesQueriesAndProtectedMutationsAcrossTheCommerceSurface() {
        assertEquals(CommerceToolName.GET_TODAY_SALES, router.route("quanto vendi hoje")?.name)
        assertEquals(CommerceToolName.CHECK_STOCK, router.route("quanto tem de café no estoque")?.name)
        assertEquals(CommerceToolName.SEARCH_PRODUCT, router.route("quanto custa café maratá")?.name)
        assertEquals(CommerceToolName.GET_CUSTOMER_BALANCE, router.route("quanto Maria deve")?.name)
        assertEquals(CommerceToolName.PREPARE_PURCHASE, router.route("o que está acabando")?.name)
        assertEquals(CommerceToolName.CHANGE_PRODUCT_PRICE, router.route("muda o café para 8,75")?.name)
    }

    @Test
    fun neverInventsCustomerForCreditPayment() {
        assertNull(router.route("recebi 10 reais no PIX"))
        assertTrue(router.route("Maria pagou 10 reais no PIX")?.arguments?.get("customer") == "maria")
    }
}
