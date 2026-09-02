package com.tino.app.domain.intelligence

import javax.inject.Inject
import javax.inject.Singleton
import java.time.Clock

enum class AttentionState { ACTIVE, DISMISSED, SNOOZED, RESOLVED }
enum class AttentionOutcome { SHOWN, DISMISSED, SNOOZED, ACTIONED, RESOLVED }

data class AttentionMetrics(
    val counts: Map<AttentionOutcome, Int> = emptyMap(),
) {
    fun count(outcome: AttentionOutcome): Int = counts[outcome] ?: 0
}

data class AttentionDigest(
    val generatedAtEpochMs: Long,
    val items: List<AttentionRecord>,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

data class AttentionRecord(
    val id: String,
    val insightId: String,
    val subjectId: String?,
    val title: String,
    val explanation: String,
    val evidenceIds: List<String>,
    val relevance: Int,
    val urgency: Int,
    val confidence: Double,
    val state: AttentionState = AttentionState.ACTIVE,
    val snoozedUntilEpochMs: Long? = null,
    val createdAtEpochMs: Long,
    val lastSeenAtEpochMs: Long,
)

interface AttentionRepository {
    suspend fun upsertAll(records: List<AttentionRecord>)
    suspend fun list(): List<AttentionRecord>
    suspend fun find(id: String): AttentionRecord?
    fun observeActive(): kotlinx.coroutines.flow.Flow<List<AttentionRecord>>
    suspend fun updateState(id: String, state: AttentionState, snoozedUntilEpochMs: Long? = null)
    suspend fun recordOutcome(attentionId: String, outcome: AttentionOutcome) = Unit
    suspend fun outcomeMetrics(): AttentionMetrics = AttentionMetrics()
}

/**
 * Reconciles the current candidate set with durable attention state. The
 * visible ranking controls which new items count as shown; candidates outside
 * the top N remain auditable without flooding the surface.
 */
@Singleton
class TinoAttentionEngine @Inject constructor(
    private val repository: AttentionRepository,
    private val clock: Clock,
) {
    suspend fun reconcile(analysis: TinoIntelligenceAnalysis, nowEpochMs: Long): List<AttentionRecord> {
        val existing = repository.list().associateBy { it.id }
        val currentInsights = analysis.candidateInsights.ifEmpty { analysis.insights }
        val currentInsightIds = currentInsights.mapTo(mutableSetOf()) { it.id }
        val visibleInsightIds = analysis.insights.mapTo(mutableSetOf()) { it.id }
        val stale = existing.values.filter { it.insightId !in currentInsightIds && it.state == AttentionState.ACTIVE }
        stale.forEach { record ->
            repository.updateState(record.id, AttentionState.RESOLVED)
            repository.recordOutcome(record.id, AttentionOutcome.RESOLVED)
        }
        val reconciled = currentInsights.map { insight ->
            val previous = existing[insight.id]
            val state = when {
                previous == null -> AttentionState.ACTIVE
                previous.state == AttentionState.SNOOZED &&
                    (previous.snoozedUntilEpochMs ?: Long.MAX_VALUE) <= nowEpochMs -> AttentionState.ACTIVE
                previous.state == AttentionState.DISMISSED && previous.explanation != insight.explanation -> AttentionState.ACTIVE
                previous.state == AttentionState.RESOLVED -> AttentionState.ACTIVE
                else -> previous.state
            }
            AttentionRecord(
                id = insight.id,
                insightId = insight.id,
                subjectId = insight.subjectId,
                title = insight.title,
                explanation = insight.explanation,
                evidenceIds = insight.evidenceIds,
                relevance = insight.relevance,
                urgency = insight.urgency,
                confidence = insight.confidence,
                state = state,
                snoozedUntilEpochMs = previous?.snoozedUntilEpochMs,
                createdAtEpochMs = previous?.createdAtEpochMs ?: nowEpochMs,
                lastSeenAtEpochMs = nowEpochMs,
            )
        }
        repository.upsertAll(reconciled)
        reconciled.filter { existing[it.id] == null && it.id in visibleInsightIds }.forEach {
            repository.recordOutcome(it.id, AttentionOutcome.SHOWN)
        }
        return repository.list().filter { it.isVisibleAt(nowEpochMs) }.sortedWith(
            compareByDescending<AttentionRecord> { it.urgency }
                .thenByDescending { it.relevance }
                .thenByDescending { it.confidence },
        )
    }

    suspend fun dismiss(id: String) {
        repository.updateState(id, AttentionState.DISMISSED)
        repository.recordOutcome(id, AttentionOutcome.DISMISSED)
    }

    suspend fun resolve(id: String) {
        repository.updateState(id, AttentionState.RESOLVED)
        repository.recordOutcome(id, AttentionOutcome.RESOLVED)
    }

    suspend fun actioned(id: String) = repository.recordOutcome(id, AttentionOutcome.ACTIONED)

    suspend fun snooze(id: String, untilEpochMs: Long) {
        require(untilEpochMs > clock.millis()) { "O adiamento precisa estar no futuro." }
        repository.updateState(id, AttentionState.SNOOZED, untilEpochMs)
        repository.recordOutcome(id, AttentionOutcome.SNOOZED)
    }

    suspend fun digest(nowEpochMs: Long, limit: Int = 3): AttentionDigest =
        AttentionDigest(
            generatedAtEpochMs = nowEpochMs,
            items = repository.list()
                .filter { it.isVisibleAt(nowEpochMs) }
                .sortedWith(
                    compareByDescending<AttentionRecord> { it.urgency }
                        .thenByDescending { it.relevance }
                        .thenByDescending { it.confidence },
                )
                .take(limit.coerceAtLeast(0)),
        )

    private fun AttentionRecord.isVisibleAt(nowEpochMs: Long): Boolean = when (state) {
        AttentionState.ACTIVE -> true
        AttentionState.SNOOZED -> (snoozedUntilEpochMs ?: Long.MAX_VALUE) <= nowEpochMs
        AttentionState.DISMISSED, AttentionState.RESOLVED -> false
    }
}
