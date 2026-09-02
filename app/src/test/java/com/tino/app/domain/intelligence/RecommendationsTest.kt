package com.tino.app.domain.intelligence

import com.tino.app.domain.finance.FinancialPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
        assertEquals(MODEL_VERSION, output.first { it.type == RecommendationType.STOCKOUT }.modelVersion)
    }

    @Test
    fun incompleteHistoryDoesNotCreatePredictiveFalsePositive() {
        val output = LocalHeuristicRecommendationEngine().generate(
            listOf(
                InventorySignal(
                    productId = "p1",
                    productName = "Produto novo",
                    stockQuantity = 30,
                    unitsSoldLast30Days = 0,
                    featureQuality = FeatureQuality.INSUFFICIENT,
                ),
            ),
        )

        assertTrue(output.isEmpty())
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

    @Test
    fun outcomeMetricsKeepEvaluationSignalsSeparateFromDecisionState() {
        val metrics = RecommendationOutcomeMetrics(
            counts = mapOf(
                RecommendationOutcome.SHOWN to 3,
                RecommendationOutcome.ACCEPTED to 1,
                RecommendationOutcome.REJECTED to 2,
            ),
        )

        assertEquals(3, metrics.count(RecommendationOutcome.SHOWN))
        assertEquals(1, metrics.count(RecommendationOutcome.ACCEPTED))
        assertEquals(0, metrics.count(RecommendationOutcome.FALSE_POSITIVE))
    }

    @Test
    fun predictiveGenerationDoesNotReturnOrPersistDuplicatePendingRecommendations() = runBlocking {
        val existing = Recommendation(
            id = "existing-stockout",
            type = RecommendationType.STOCKOUT,
            productId = "p1",
            message = "Café está sem estoque.",
            confidence = 0.99,
        )
        val repository = RecordingRecommendationRepository(pendingRecommendations = listOf(existing))
        val facts = FakeFactsPort(
            products = listOf(IntelligenceProduct("p1", "Café", 1200, 0)),
            movements = mapOf(
                "p1" to listOf(
                    IntelligenceStockMovement(
                        productId = "p1",
                        quantityDelta = -3,
                        reason = "sale",
                        occurredAtEpochMs = 9_900L,
                    ),
                ),
            ),
        )
        val result = PredictiveRecommendationService(
            facts = facts,
            analytics = DeterministicBusinessAnalytics(),
            engine = LocalHeuristicRecommendationEngine(),
            repository = repository,
        ).generate(nowEpochMs = 10_000L)

        assertTrue(result.recommendations.isEmpty())
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun predictiveGenerationReturnsOnlyNewRecommendationsForTheUi() = runBlocking {
        val repository = RecordingRecommendationRepository()
        val facts = FakeFactsPort(
            products = listOf(IntelligenceProduct("p1", "Café", 1200, 0)),
            movements = mapOf(
                "p1" to listOf(
                    IntelligenceStockMovement("p1", -3, "sale", 9_900L),
                ),
            ),
        )
        val result = PredictiveRecommendationService(
            facts = facts,
            analytics = DeterministicBusinessAnalytics(),
            engine = LocalHeuristicRecommendationEngine(),
            repository = repository,
        ).generate(nowEpochMs = 10_000L)

        assertEquals(1, result.recommendations.size)
        assertEquals(result.recommendations, repository.saved)
    }

    private class RecordingRecommendationRepository(
        private val pendingRecommendations: List<Recommendation> = emptyList(),
    ) : RecommendationRepository {
        val saved = mutableListOf<Recommendation>()
        var updatedId: String? = null
        var updatedDecision: RecommendationDecision? = null
        var operationalMutations: Int = 0

        override suspend fun saveAll(recommendations: List<Recommendation>) {
            saved += recommendations
        }
        override suspend fun pending(): List<Recommendation> = pendingRecommendations
        override fun observePending(): Flow<List<Recommendation>> = flowOf(pendingRecommendations)
        override suspend fun updateDecision(id: String, decision: RecommendationDecision): Recommendation? {
            updatedId = id
            updatedDecision = decision
            return null
        }

        override suspend fun expirePending(beforeEpochMs: Long): Int = 0

        override suspend fun recordOutcome(
            recommendationId: String,
            outcome: RecommendationOutcome,
            occurredAt: java.time.Instant,
        ) = Unit

        override fun observeOutcomeMetrics() = emptyFlow<RecommendationOutcomeMetrics>()
    }

    private class FakeFactsPort(
        private val products: List<IntelligenceProduct>,
        private val movements: Map<String, List<IntelligenceStockMovement>>,
    ) : IntelligenceFactsPort {
        override suspend fun financialSummary(period: FinancialPeriod) = error("not used")
        override suspend fun customers() = emptyList<IntelligenceCustomer>()
        override suspend fun receivables() = emptyList<IntelligenceReceivable>()
        override suspend fun paymentEvents(customerId: String) = emptyList<IntelligencePaymentEvent>()
        override suspend fun resolveCustomer(reference: String) = IntelligenceEntityResolution.NotFound
        override suspend fun products() = products
        override suspend fun resolveProduct(reference: String) = IntelligenceEntityResolution.NotFound
        override suspend fun stockMovements(productId: String) = movements[productId].orEmpty()
    }
}
