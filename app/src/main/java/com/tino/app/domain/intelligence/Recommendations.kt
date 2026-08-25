package com.tino.app.domain.intelligence

import com.tino.app.core.common.UuidV7
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class RecommendationType { STOCKOUT, REPLENISHMENT, SLOW_MOVING, RECURRENCE }
enum class RecommendationDecision { PENDING, ACCEPTED, REJECTED }

data class InventorySignal(
    val productId: String,
    val productName: String,
    val stockQuantity: Int,
    val unitsSoldLast30Days: Int,
)

data class Recommendation(
    val id: String,
    val type: RecommendationType,
    val productId: String,
    val message: String,
    val confidence: Double,
    val decision: RecommendationDecision = RecommendationDecision.PENDING,
    val createdAt: Instant = Instant.now(),
)

interface RecommendationEngine {
    fun generate(signals: List<InventorySignal>): List<Recommendation>
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
            )
        } else if (signal.unitsSoldLast30Days > 0 && signal.stockQuantity * 30 < signal.unitsSoldLast30Days) {
            recommendations += Recommendation(
                id = UuidV7.new(),
                type = RecommendationType.REPLENISHMENT,
                productId = signal.productId,
                message = "${signal.productName} pode acabar em menos de um mês.",
                confidence = 0.65,
            )
        }
        if (signal.stockQuantity >= 18 && signal.unitsSoldLast30Days <= 3) {
            recommendations += Recommendation(
                id = UuidV7.new(),
                type = RecommendationType.SLOW_MOVING,
                productId = signal.productId,
                message = "Não compre mais ${signal.productName} agora; o estoque está parado.",
                confidence = 0.70,
            )
        }
        recommendations
    }
}

class RecommendationDecisionService {
    fun decide(recommendation: Recommendation, accepted: Boolean): Recommendation =
        recommendation.copy(
            decision = if (accepted) RecommendationDecision.ACCEPTED else RecommendationDecision.REJECTED,
        )
}
