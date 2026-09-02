package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.agent.AgentDataSource
import com.tino.app.domain.agent.CustomerListItem
import com.tino.app.domain.agent.CustomerListResult
import com.tino.app.domain.agent.ProductListItem
import com.tino.app.domain.agent.ProductListResult
import com.tino.app.domain.agent.ReceivableItem
import com.tino.app.domain.agent.ReceivablesListResult
import com.tino.app.domain.agent.ReplenishmentResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TinoUiPlannerTest {
    private val planner = TinoUiPlanner()

    @Test
    fun replenishmentComposesRegisteredAlertCardsAndAction() = runBlocking {
        val plan = planner.compose(
            TinoUiPlannerResult.Replenishment(
                ReplenishmentResult(
                    items = listOf(ProductListItem("p1", "Café Maratá", 1250, 0, "unidade")),
                    dataSource = AgentDataSource.LOCAL_ONLY,
                ),
            ),
            TinoUiPlannerContext(intent = TinoUiIntent.REPLENISHMENT_QUERY),
        )

        val surface = plan.tree.toSurfaceMessage("replenishment")
        assertEquals(TinoCatalogVersion.ID, plan.catalogVersion)
        assertEquals(
            listOf(
                TinoCustomComponentCatalog.CATALOG_LIST_CARD,
            ),
            surface.components.map { it.type },
        )
        assertEquals(1, surface.components.single().items.size)
        assertEquals("replenishment", surface.components.single().props["variant"])
        assertEquals("OUT_OF_STOCK", surface.components.single().items.single().status)
        assertTrue(surface.components.single().actions.contains(CoreTinoComponentCatalog.SELECT_TAB.name))
    }

    @Test
    fun receivablesComposeSummaryAndDebtCardsWithoutInventingValues() = runBlocking {
        val surface = planner.compose(
            TinoUiPlannerResult.Receivables(
                ReceivablesListResult(
                    items = listOf(
                        ReceivableItem("c1", "Maria Lina", 12_500),
                        ReceivableItem("c2", "João", 60_500),
                    ),
                ),
            ),
            TinoUiPlannerContext(intent = TinoUiIntent.RECEIVABLES),
        ).tree.toSurfaceMessage("receivables")

        assertEquals(TinoCustomComponentCatalog.SUMMARY_CARD, surface.components[0].type)
        assertEquals("R$ 730,00", surface.components[0].props["receivedValue"])
        assertEquals(TinoCustomComponentCatalog.CATALOG_LIST_CARD, surface.components[1].type)
        assertEquals("receivables", surface.components[1].props["variant"])
        assertEquals(2, surface.components[1].items.size)
    }

    @Test
    fun customersUseCustomerCardFamilyInsteadOfGenericCatalog() = runBlocking {
        val surface = planner.compose(
            TinoUiPlannerResult.Customers(
                CustomerListResult(
                    items = listOf(CustomerListItem("c1", "Maria Lina", "86994209350")),
                ),
            ),
            TinoUiPlannerContext(intent = TinoUiIntent.LIST_CUSTOMERS),
        ).tree.toSurfaceMessage("customers")

        assertEquals(TinoCustomComponentCatalog.CATALOG_LIST_CARD, surface.components.single().type)
        assertEquals("customers", surface.components.single().props["variant"])
        assertEquals("Maria Lina", surface.components.single().items.single().title)
    }

    @Test
    fun compactDeviceOmitsChartUnlessItFitsAndHasDomainSeries() = runBlocking {
        val summary = com.tino.app.domain.agent.FinancialSummaryResult(
            period = com.tino.app.domain.finance.FinancialPeriod.today(java.time.Clock.systemUTC()),
            receivedTotalCents = 482_000,
            receivedCashCents = 0,
            receivedPixCents = 0,
            receivedCardCents = 0,
            receivedUnknownCents = 0,
            totalReceivableCents = 0,
            creditCreatedCents = 0,
            creditPaymentsReceivedCents = 0,
        )

        val compact = planner.compose(
            TinoUiPlannerResult.FinancialSummary(summary),
            TinoUiPlannerContext(intent = TinoUiIntent.WEEKLY_SALES, widthDp = 320, chartSeries = "▁▃▂"),
        ).tree.toSurfaceMessage("compact")
        val wide = planner.compose(
            TinoUiPlannerResult.FinancialSummary(summary),
            TinoUiPlannerContext(intent = TinoUiIntent.WEEKLY_SALES, widthDp = 412, chartSeries = "▁▃▂", chartLabels = "S T Q"),
        ).tree.toSurfaceMessage("wide")

        assertTrue(compact.components.none { it.type == TinoCustomComponentCatalog.MINI_CHART })
        assertNotNull(wide.components.firstOrNull { it.type == TinoCustomComponentCatalog.MINI_CHART })
    }

    @Test
    fun everyPlannerOutputIsAllowlistedAndUnsupportedPatternsBecomeCandidates() = runBlocking {
        val results = listOf(
            TinoUiPlannerResult.Products(ProductListResult(emptyList())),
            TinoUiPlannerResult.Customers(CustomerListResult(emptyList())),
            TinoUiPlannerResult.Unsupported("Não há uma composição segura.", "new-pattern"),
        )

        results.forEach { result ->
            val plan = planner.compose(result, TinoUiPlannerContext())
            assertTrue(plan.tree.toSurfaceMessage("test").components.all { TinoComponentCatalog.core.types.contains(it.type) })
        }
        assertEquals("new-pattern", planner.compose(
            TinoUiPlannerResult.Unsupported("x", "new-pattern"),
            TinoUiPlannerContext(),
        ).candidate?.requestedPattern)
    }
}
