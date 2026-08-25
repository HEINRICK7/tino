package com.tino.app.core.database

import com.tino.app.domain.intelligence.Recommendation
import com.tino.app.domain.intelligence.RecommendationDecision
import com.tino.app.domain.intelligence.RecommendationEvidence
import com.tino.app.domain.intelligence.RecommendationRepository
import com.tino.app.domain.intelligence.RecommendationType
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRecommendationRepository @Inject constructor(
    private val dao: RecommendationDao,
) : RecommendationRepository {
    override suspend fun saveAll(recommendations: List<Recommendation>) {
        if (recommendations.isEmpty()) return
        dao.upsertAll(recommendations.map { it.toEntity() })
    }

    override suspend fun pending(): List<Recommendation> = dao.pending().map { it.toDomain() }

    override suspend fun updateDecision(id: String, decision: RecommendationDecision): Recommendation? {
        if (dao.updateDecision(id, decision.name) == 0) return null
        return dao.findById(id)?.toDomain()
    }

    private fun Recommendation.toEntity() = RecommendationEntity(
        id = id,
        type = type.name,
        productId = productId,
        message = message,
        confidence = confidence,
        decision = decision.name,
        createdAtEpochMs = createdAt.toEpochMilli(),
        stockQuantity = evidence?.stockQuantity,
        unitsSoldLast30Days = evidence?.unitsSoldLast30Days,
        rule = evidence?.rule,
        windowDays = evidence?.windowDays,
    )

    private fun RecommendationEntity.toDomain() = Recommendation(
        id = id,
        type = runCatching { enumValueOf<RecommendationType>(type) }
            .getOrDefault(RecommendationType.REPLENISHMENT),
        productId = productId,
        message = message,
        confidence = confidence,
        decision = runCatching { enumValueOf<RecommendationDecision>(decision) }
            .getOrDefault(RecommendationDecision.PENDING),
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        evidence = if (stockQuantity != null && unitsSoldLast30Days != null && rule != null) {
            RecommendationEvidence(stockQuantity, unitsSoldLast30Days, rule, windowDays ?: 30)
        } else null,
    )
}
