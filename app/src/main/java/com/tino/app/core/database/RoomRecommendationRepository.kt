package com.tino.app.core.database

import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.domain.intelligence.Recommendation
import com.tino.app.domain.intelligence.RecommendationDecision
import com.tino.app.domain.intelligence.RecommendationEvidence
import com.tino.app.domain.intelligence.RecommendationOutcome
import com.tino.app.domain.intelligence.RecommendationOutcomeMetrics
import com.tino.app.domain.intelligence.RecommendationRepository
import com.tino.app.domain.intelligence.RecommendationType
import com.tino.app.domain.intelligence.FeatureQuality
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRecommendationRepository @Inject constructor(
    private val dao: RecommendationDao,
    private val auditLogger: AuditLogger,
) : RecommendationRepository {
    override suspend fun saveAll(recommendations: List<Recommendation>) {
        if (recommendations.isEmpty()) return
        dao.upsertAll(recommendations.map { it.toEntity() })
        recommendations.forEach { recommendation ->
            auditLogger.record(
                AuditEventType.ML_RECOMMENDATION,
                mapOf(
                    "recommendation_type" to recommendation.type.name,
                    "status" to RecommendationDecision.PENDING.name,
                ),
            )
        }
    }

    override suspend fun pending(): List<Recommendation> = dao.pending().map { it.toDomain() }

    override fun observePending(): Flow<List<Recommendation>> = dao.observePending().map { values ->
        values.map { it.toDomain() }
    }

    override suspend fun updateDecision(id: String, decision: RecommendationDecision): Recommendation? {
        if (dao.updateDecision(id, decision.name) == 0) return null
        return dao.findById(id)?.toDomain()?.also { updated ->
            auditLogger.record(
                AuditEventType.ML_RECOMMENDATION,
                mapOf(
                    "recommendation_type" to updated.type.name,
                    "status" to updated.decision.name,
                ),
            )
        }
    }

    override suspend fun expirePending(beforeEpochMs: Long): Int {
        val staleIds = dao.stalePendingIds(beforeEpochMs)
        if (staleIds.isEmpty()) return 0
        val expired = dao.expirePending(beforeEpochMs)
        staleIds.forEach { id -> recordOutcome(id, RecommendationOutcome.EXPIRED) }
        return expired
    }

    override suspend fun recordOutcome(
        recommendationId: String,
        outcome: RecommendationOutcome,
        occurredAt: Instant,
    ) {
        val inserted = dao.insertOutcome(
            RecommendationOutcomeEntity(
                id = "${recommendationId}:${outcome.name}",
                recommendationId = recommendationId,
                outcome = outcome.name,
                occurredAtEpochMs = occurredAt.toEpochMilli(),
            ),
        )
        if (inserted != -1L) {
            auditLogger.record(
                AuditEventType.ML_RECOMMENDATION,
                mapOf(
                    "status" to "OUTCOME",
                    "outcome" to outcome.name,
                ),
            )
        }
    }

    override fun observeOutcomeMetrics() = dao.observeOutcomeCounts().map { rows ->
        RecommendationOutcomeMetrics(
            counts = rows.mapNotNull { row ->
                runCatching { RecommendationOutcome.valueOf(row.outcome) to row.count }.getOrNull()
            }.toMap(),
        )
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
        quality = evidence?.quality?.name ?: FeatureQuality.INSUFFICIENT.name,
        featureVersion = evidence?.featureVersion ?: "inventory-features-v1",
        modelVersion = modelVersion,
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
            RecommendationEvidence(
                stockQuantity = stockQuantity,
                unitsSoldLast30Days = unitsSoldLast30Days,
                rule = rule,
                windowDays = windowDays ?: 30,
                quality = runCatching { FeatureQuality.valueOf(quality) }.getOrDefault(FeatureQuality.INSUFFICIENT),
                featureVersion = featureVersion,
            )
        } else null,
        modelVersion = modelVersion,
    )
}
