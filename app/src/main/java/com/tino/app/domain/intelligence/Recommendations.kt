package com.tino.app.domain.intelligence

import com.tino.app.core.common.UuidV7
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class RecommendationType { STOCKOUT, REPLENISHMENT, SLOW_MOVING, RECURRENCE }
enum class RecommendationDecision { PENDING, ACCEPTED, REJECTED }
enum class RecommendationOutcome {
    SHOWN,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    STOCKOUT_AFTER_RECOMMENDATION,
    FALSE_POSITIVE,
}

data class InventorySignal(
    val productId: String,
    val productName: String,
    val stockQuantity: Int,
    val unitsSoldLast30Days: Int,
)

data class RecommendationEvidence(
    val stockQuantity: Int,
    val unitsSoldLast30Days: Int,
    val rule: String,
    val windowDays: Int = 30,
)

data class Recommendation(
    val id: String,
    val type: RecommendationType,
    val productId: String,
    val message: String,
    val confidence: Double,
    val decision: RecommendationDecision = RecommendationDecision.PENDING,
    val createdAt: Instant = Instant.now(),
    val evidence: RecommendationEvidence? = null,
)

data class RecommendationOutcomeMetrics(
    val counts: Map<RecommendationOutcome, Int> = emptyMap(),
) {
    fun count(outcome: RecommendationOutcome): Int = counts[outcome] ?: 0
}

interface RecommendationEngine {
    fun generate(signals: List<InventorySignal>): List<Recommendation>
}

interface RecommendationRepository {
    suspend fun saveAll(recommendations: List<Recommendation>)
    suspend fun pending(): List<Recommendation>
    fun observePending(): Flow<List<Recommendation>>
    suspend fun updateDecision(id: String, decision: RecommendationDecision): Recommendation?
    suspend fun recordOutcome(
        recommendationId: String,
        outcome: RecommendationOutcome,
        occurredAt: Instant = Instant.now(),
    )
    fun observeOutcomeMetrics(): Flow<RecommendationOutcomeMetrics>
}

object NoOpRecommendationRepository : RecommendationRepository {
    override suspend fun saveAll(recommendations: List<Recommendation>) = Unit
    override suspend fun pending(): List<Recommendation> = emptyList()
    override fun observePending(): Flow<List<Recommendation>> = emptyFlow()
    override suspend fun updateDecision(id: String, decision: RecommendationDecision): Recommendation? = null
    override suspend fun recordOutcome(
        recommendationId: String,
        outcome: RecommendationOutcome,
        occurredAt: Instant,
    ) = Unit
    override fun observeOutcomeMetrics(): Flow<RecommendationOutcomeMetrics> = emptyFlow()
}

@Singleton
class LocalHeuristicRecommendationEngine @Inject constructor() : RecommendationEngine {
    override fun generate(signals: List<InventorySignal>): List<Recommendation> = signals.flatMap { signal ->
        val recommendations = mutableListOf<Recommendation>()
        if (signal.stockQuantity == 0 && signal.unitsSoldLast30Days > 0) {
            recommendations += Recommendation(
                id = UuidV7.new(),
                type = RecommendationType.STOCKOUT,
                productId = signal.productId,
                message = "${signal.productName} está sem estoque.",
                confidence = 0.99,
                evidence = RecommendationEvidence(
                    stockQuantity = signal.stockQuantity,
                    unitsSoldLast30Days = signal.unitsSoldLast30Days,
                    rule = "stock_zero_with_recent_sales",
                ),
            )
        } else if (signal.unitsSoldLast30Days > 0 && signal.stockQuantity * 30 < signal.unitsSoldLast30Days) {
            recommendations += Recommendation(
                id = UuidV7.new(),
                type = RecommendationType.REPLENISHMENT,
                productId = signal.productId,
                message = "${signal.productName} pode acabar em menos de um mês.",
                confidence = 0.65,
                evidence = RecommendationEvidence(
                    stockQuantity = signal.stockQuantity,
                    unitsSoldLast30Days = signal.unitsSoldLast30Days,
                    rule = "stock_below_thirty_day_demand",
                ),
            )
        }
        if (signal.stockQuantity >= 18 && signal.unitsSoldLast30Days <= 3) {
            recommendations += Recommendation(
                id = UuidV7.new(),
                type = RecommendationType.SLOW_MOVING,
                productId = signal.productId,
                message = "Não compre mais ${signal.productName} agora; o estoque está parado.",
                confidence = 0.70,
                evidence = RecommendationEvidence(
                    stockQuantity = signal.stockQuantity,
                    unitsSoldLast30Days = signal.unitsSoldLast30Days,
                    rule = "high_stock_with_low_recent_sales",
                ),
            )
        }
        recommendations
    }
}

data class PredictiveInventoryResult(
    val signals: List<InventorySignal>,
    val recommendations: List<Recommendation>,
    val generatedAt: Instant,
)

/**
 * G6.1 boundary: facts and deterministic analytics in, explainable suggestions out.
 * It never writes commerce state and deliberately does not depend on an ML model.
 */
class PredictiveRecommendationService @Inject constructor(
    private val facts: IntelligenceFactsPort,
    private val analytics: BusinessAnalyticsPort,
    private val engine: RecommendationEngine,
    private val repository: RecommendationRepository = NoOpRecommendationRepository,
) {
    suspend fun generate(nowEpochMs: Long): PredictiveInventoryResult {
        val signals = facts.products().map { product ->
            val velocity = analytics.calculateStockVelocity(
                product = product,
                movements = facts.stockMovements(product.id),
                nowEpochMs = nowEpochMs,
            )
            InventorySignal(
                productId = product.id,
                productName = product.name,
                stockQuantity = product.stockQuantity,
                unitsSoldLast30Days = velocity.unitsLastPeriod,
            )
        }
        val generated = engine.generate(signals)
        val existingKeys = repository.pending()
            .mapTo(mutableSetOf()) { "${it.type.name}:${it.productId}" }
        val recommendations = generated.filterNot { "${it.type.name}:${it.productId}" in existingKeys }
        repository.saveAll(recommendations)
        return PredictiveInventoryResult(
            signals = signals,
            recommendations = generated,
            generatedAt = Instant.ofEpochMilli(nowEpochMs),
        )
    }
}

class RecommendationDecisionService(
    private val repository: RecommendationRepository = NoOpRecommendationRepository,
) {
    fun decide(recommendation: Recommendation, accepted: Boolean): Recommendation =
        recommendation.copy(
            decision = if (accepted) RecommendationDecision.ACCEPTED else RecommendationDecision.REJECTED,
        )

    suspend fun decideAndPersist(recommendation: Recommendation, accepted: Boolean): Recommendation {
        val decided = decide(recommendation, accepted)
        repository.updateDecision(decided.id, decided.decision)
        return decided
    }
}
