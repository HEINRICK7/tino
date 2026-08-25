package com.tino.app.domain.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationsTest {
    @Test
    fun producesActionableStockoutAndSlowMovingRecommendationsWithoutMutatingState() {
        val input = listOf(
            InventorySignal("p1", "Café", stockQuantity = 0, unitsSoldLast30Days = 12),
            InventorySignal("p2", "Biscoito", stockQuantity = 18, unitsSoldLast30Days = 3),
        )

        val output = LocalHeuristicRecommendationEngine().generate(input)

        assertEquals(2, output.size)
        assertTrue(output.any { it.type == RecommendationType.STOCKOUT && it.productId == "p1" })
        assertTrue(output.any { it.type == RecommendationType.SLOW_MOVING && it.productId == "p2" })
        assertTrue(output.all { it.decision == RecommendationDecision.PENDING })
        assertEquals("stock_zero_with_recent_sales", output.first { it.type == RecommendationType.STOCKOUT }.evidence?.rule)
        assertEquals(12, output.first { it.type == RecommendationType.STOCKOUT }.evidence?.unitsSoldLast30Days)
    }
}
