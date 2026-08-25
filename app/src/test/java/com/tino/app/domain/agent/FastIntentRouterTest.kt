package com.tino.app.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastIntentRouterTest {
    private val router = FastIntentRouter()

    @Test
    fun normalizesPixAndRoutesFinancialSummaryWithoutPeriodDrift() {
        val result = router.route("Quanto entrou no PÍX hoje?") as FastIntentResult.Match

        assertEquals(TinoToolId.FINANCIAL_SUMMARY, result.tool)
        assertEquals(AgentCapability.READ_FINANCIAL_SUMMARY, result.intent.capability)
        assertEquals(FinancialPaymentMethod.PIX, result.intent.paymentMethod)
        assertEquals(AgentIntentPeriod.TODAY, result.intent.period)
    }

    @Test
    fun routesCashCardAndReceivableQueries() {
        assertEquals(
            FinancialPaymentMethod.CASH,
            (router.route("quanto entrou em dinheiro") as FastIntentResult.Match).intent.paymentMethod,
        )
        assertEquals(
            FinancialPaymentMethod.CARD,
            (router.route("quanto entrou na maquininha") as FastIntentResult.Match).intent.paymentMethod,
        )
        val receivable = router.route("quanto tenho pra receber") as FastIntentResult.Match
        assertEquals(FinancialPaymentMethod.ALL, receivable.intent.paymentMethod)
        assertEquals(FinancialMetric.RECEIVABLE, receivable.intent.metric)
    }

    @Test
    fun routesComposedFinancialQuestionsBeforeProductResolution() {
        listOf(
            "Quanto eu recebi hoje no Pix e no dinheiro e quanto ainda tenho para receber?",
            "recebi hoje no Pix e no dinheiro e quanto ainda tenho para receber",
            "Quanto recebi hoje?",
            "Como estão minhas vendas hoje?",
            "Me mostra o financeiro de hoje",
            "Qual foi meu movimento hoje?",
        ).forEach { phrase ->
            val result = router.route(phrase) as FastIntentResult.Match

            assertEquals("$phrase deve consultar o resumo financeiro", TinoToolId.FINANCIAL_SUMMARY, result.tool)
            assertEquals(AgentCapability.READ_FINANCIAL_SUMMARY, result.intent.capability)
        }

        val combined = router.route("Quanto recebi hoje no Pix e no dinheiro?") as FastIntentResult.Match
        assertEquals(FinancialPaymentMethod.ALL, combined.intent.paymentMethod)

        val combinedWithReceivable = router.route(
            "Quanto eu recebi hoje no Pix e no dinheiro e quanto ainda tenho para receber?",
        ) as FastIntentResult.Match
        assertEquals(FinancialMetric.SUMMARY, combinedWithReceivable.intent.metric)
    }

    @Test
    fun extractsCustomerReferenceWithoutResolvingAnId() {
        val result = router.route("Quanto a Maria Lina está devendo?") as FastIntentResult.Match

        assertEquals(TinoToolId.CUSTOMER_BALANCE, result.tool)
        assertEquals("maria lina", result.intent.customerRef)
    }

    @Test
    fun routesTimelineAndLeavesComplexRequestsForGemma() {
        val timeline = router.route("Mostra a caderneta da Maria Lina") as FastIntentResult.Match
        assertEquals(TinoToolId.CUSTOMER_TIMELINE, timeline.tool)
        assertEquals("maria lina", timeline.intent.customerRef)

        assertTrue(router.route("Quanto eu costumo vender quando chove?") is FastIntentResult.NoMatch)
        assertTrue(router.route("João levou café e açúcar ontem") is FastIntentResult.NoMatch)
    }

    @Test
    fun routesDbFirstProductAndCustomerReadsWithoutGemma() {
        listOf(
            "Quais produtos eu tenho?",
            "Quais produtos eu tenho cadastrado no meu estoque?",
            "Me mostra meu estoque",
            "Lista meu estoque",
            "O que tenho cadastrado?",
            "Lista meus produtos",
        ).forEach { phrase ->
            val products = router.route(phrase) as FastIntentResult.Match
            assertEquals("$phrase deve listar produtos", TinoToolId.LIST_PRODUCTS, products.tool)
            assertEquals(AgentCapability.LIST_PRODUCTS, products.intent.capability)
            assertEquals(null, products.intent.productRef)
        }

        val stock = router.route("Quanto de Café Maratá tenho?") as FastIntentResult.Match
        assertEquals(TinoToolId.PRODUCT_STOCK, stock.tool)
        assertEquals(AgentCapability.GET_PRODUCT_STOCK, stock.intent.capability)
        assertEquals("cafe marata", stock.intent.productRef)

        val price = router.route("Quanto custa o Café Maratá?") as FastIntentResult.Match
        assertEquals(TinoToolId.PRODUCT_PRICE, price.tool)
        assertEquals(AgentCapability.GET_PRODUCT_PRICE, price.intent.capability)
        assertEquals("cafe marata", price.intent.productRef)

        listOf(
            "Quais clientes tenho?",
            "Quero ver meus clientes",
            "Lista meus clientes",
            "Me mostra todos os meus clientes",
            "Me mostre todos os clientes cadastrados",
        ).forEach { phrase ->
            assertEquals(AgentCapability.LIST_CUSTOMERS, (router.route(phrase) as FastIntentResult.Match).intent.capability)
        }
        assertEquals(AgentCapability.LIST_RECEIVABLES, (router.route("Quem está me devendo?") as FastIntentResult.Match).intent.capability)
        assertEquals(AgentCapability.LIST_OVERDUE, (router.route("Quais fiados estão vencidos?") as FastIntentResult.Match).intent.capability)
    }

    @Test
    fun productFactRequiresAProductReferenceAndDoesNotHijackGlobalInventoryList() {
        val stock = router.route("Quanto tenho de Café Maratá?") as FastIntentResult.Match
        assertEquals(AgentCapability.GET_PRODUCT_STOCK, stock.intent.capability)
        assertEquals("cafe marata", stock.intent.productRef)

        val global = router.route("Quais produtos eu tenho cadastrados no estoque?") as FastIntentResult.Match
        assertEquals(AgentCapability.LIST_PRODUCTS, global.intent.capability)
        assertEquals(null, global.intent.productRef)
    }

    @Test
    fun routesPurchaseQuestionsToReplenishmentInsteadOfCatalogListing() {
        listOf(
            "Quais produtos tenho que comprar?",
            "O que preciso repor?",
            "Quais produtos estão acabando?",
            "O que acabou?",
            "O que está zerado?",
        ).forEach { phrase ->
            val result = router.route(phrase) as FastIntentResult.Match
            assertEquals("$phrase deve consultar reposição", TinoToolId.REPLENISHMENT_QUERY, result.tool)
            assertEquals(AgentCapability.REPLENISHMENT_QUERY, result.intent.capability)
        }
        assertEquals("Verificando reposição…", router.contextLabel("Quais produtos tenho que comprar?"))
    }

    @Test
    fun catalogContainsTheToolsUsedByFastRouter() {
        assertTrue(TinoToolCatalog.contains(TinoToolId.FINANCIAL_SUMMARY))
        assertTrue(TinoToolCatalog.contains(TinoToolId.CUSTOMER_BALANCE))
        assertTrue(TinoToolCatalog.contains(TinoToolId.CUSTOMER_TIMELINE))
        assertTrue(TinoToolCatalog.contains(TinoToolId.LIST_PRODUCTS))
        assertTrue(TinoToolCatalog.contains(TinoToolId.REPLENISHMENT_QUERY))
        assertTrue(TinoToolCatalog.contains(TinoToolId.PRODUCT_STOCK))
        assertTrue(TinoToolCatalog.contains(TinoToolId.PRODUCT_PRICE))
        assertTrue(TinoToolCatalog.contains(TinoToolId.LIST_CUSTOMERS))
        assertTrue(TinoToolCatalog.contains(TinoToolId.LIST_RECEIVABLES))
        assertTrue(TinoToolCatalog.contains(TinoToolId.LIST_OVERDUE))
    }

    @Test
    fun exposesContextualReadStatusInsteadOfGenericUnderstanding() {
        assertEquals("Consultando produtos…", router.contextLabel("Quais produtos temos?"))
        assertEquals("Consultando estoque…", router.contextLabel("Quanto de Café Maratá tenho?"))
        assertEquals("Abrindo clientes…", router.contextLabel("clientes"))
        assertEquals("Consultando a caderneta…", router.contextLabel("Quem está me devendo?"))
        assertEquals("Consultando recebimentos…", router.contextLabel("Quanto entrou no PIX hoje?"))
    }

    @Test
    fun routesBareOperationalLabelsWithoutInvokingTheAgent() {
        assertEquals(AgentCapability.LIST_CUSTOMERS, (router.route("clientes") as FastIntentResult.Match).intent.capability)
        assertEquals(AgentCapability.LIST_PRODUCTS, (router.route("estoque") as FastIntentResult.Match).intent.capability)
        assertEquals(AgentCapability.LIST_PRODUCTS, (router.route("produtos") as FastIntentResult.Match).intent.capability)
    }

    @Test
    fun routesDeterministicNavigationWithoutAnAgentQuery() {
        assertEquals(FastNavigationTarget.QUICK_SALE, router.navigationTarget("Vender"))
        assertEquals(FastNavigationTarget.CUSTOMERS, router.navigationTarget("Abrir clientes"))
        assertEquals(FastNavigationTarget.CUSTOMERS, router.navigationTarget("clientes"))
        assertEquals(FastNavigationTarget.PRODUCTS, router.navigationTarget("Estoque"))
        assertEquals(FastNavigationTarget.CREDIT_LIST, router.navigationTarget("Fiado"))
        assertEquals("Abrindo clientes…", router.contextLabel("Abrir clientes"))
    }

    @Test
    fun navigationTargetsDeclareTheCapabilityThatAuthorizesThem() {
        assertEquals(TinoCapabilityId.NAVIGATE, FastNavigationTarget.QUICK_SALE.requiredCapability())
        assertEquals(TinoCapabilityId.LIST_CUSTOMERS, FastNavigationTarget.CUSTOMERS.requiredCapability())
        assertEquals(TinoCapabilityId.LIST_PRODUCTS, FastNavigationTarget.PRODUCTS.requiredCapability())
        assertEquals(TinoCapabilityId.LIST_RECEIVABLES, FastNavigationTarget.CREDIT_LIST.requiredCapability())
        assertEquals(TinoCapabilityId.REGISTER_STOCK_ENTRY, FastNavigationTarget.STOCK_ENTRY.requiredCapability())
    }
}
