package com.tino.app.domain.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

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

    @Test
    fun decisionIsPersistedSeparatelyFromOperationalData() = runBlocking {
        val repository = RecordingRecommendationRepository()
        val recommendation = Recommendation(
            id = "rec-1",
            type = RecommendationType.STOCKOUT,
            productId = "p1",
            message = "Café está sem estoque.",
            confidence = 0.99,
        )

        val decided = RecommendationDecisionService(repository).decideAndPersist(recommendation, accepted = true)

        assertEquals(RecommendationDecision.ACCEPTED, decided.decision)
        assertEquals("rec-1", repository.updatedId)
        assertEquals(RecommendationDecision.ACCEPTED, repository.updatedDecision)
        assertTrue(repository.operationalMutations == 0)
    }

    private class RecordingRecommendationRepository : RecommendationRepository {
        var updatedId: String? = null
        var updatedDecision: RecommendationDecision? = null
        var operationalMutations: Int = 0

        override suspend fun saveAll(recommendations: List<Recommendation>) = Unit
        override suspend fun pending(): List<Recommendation> = emptyList()
        override suspend fun updateDecision(id: String, decision: RecommendationDecision): Recommendation? {
            updatedId = id
            updatedDecision = decision
            return null
        }
    }
}
