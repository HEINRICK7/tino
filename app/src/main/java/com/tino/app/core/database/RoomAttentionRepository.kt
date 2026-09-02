package com.tino.app.core.database

import com.tino.app.domain.intelligence.AttentionRecord
import com.tino.app.domain.intelligence.AttentionMetrics
import com.tino.app.domain.intelligence.AttentionOutcome
import com.tino.app.domain.intelligence.AttentionRepository
import com.tino.app.domain.intelligence.AttentionState
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomAttentionRepository @Inject constructor(
    private val dao: AttentionDao,
) : AttentionRepository {
    override suspend fun upsertAll(records: List<AttentionRecord>) {
        if (records.isEmpty()) return
        dao.upsertAll(records.map { it.toEntity() })
    }

    override suspend fun list(): List<AttentionRecord> = dao.all().map { it.toDomain() }

    override suspend fun find(id: String): AttentionRecord? = dao.findById(id)?.toDomain()

    override fun observeActive() = dao.observeActive().map { items -> items.map { it.toDomain() } }

    override suspend fun updateState(id: String, state: AttentionState, snoozedUntilEpochMs: Long?) {
        dao.updateState(id, state.name, snoozedUntilEpochMs)
    }

    override suspend fun recordOutcome(attentionId: String, outcome: AttentionOutcome) {
        dao.insertOutcome(
            AttentionOutcomeEntity(
                id = UUID.randomUUID().toString(),
                attentionId = attentionId,
                outcome = outcome.name,
                occurredAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun outcomeMetrics(): AttentionMetrics = AttentionMetrics(
        counts = dao.outcomeCounts().mapNotNull { row ->
            runCatching { AttentionOutcome.valueOf(row.outcome) to row.count }.getOrNull()
        }.toMap(),
    )

    private fun AttentionRecord.toEntity() = AttentionEntity(
        id = id,
        insightId = insightId,
        subjectId = subjectId,
        title = title,
        explanation = explanation,
        evidenceIdsJson = evidenceIds.joinToString(","),
        relevance = relevance,
        urgency = urgency,
        confidence = confidence,
        state = state.name,
        snoozedUntilEpochMs = snoozedUntilEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        lastSeenAtEpochMs = lastSeenAtEpochMs,
    )

    private fun AttentionEntity.toDomain() = AttentionRecord(
        id = id,
        insightId = insightId,
        subjectId = subjectId,
        title = title,
        explanation = explanation,
        evidenceIds = evidenceIdsJson.split(',').filter { it.isNotBlank() },
        relevance = relevance,
        urgency = urgency,
        confidence = confidence,
        state = runCatching { AttentionState.valueOf(state) }.getOrDefault(AttentionState.ACTIVE),
        snoozedUntilEpochMs = snoozedUntilEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        lastSeenAtEpochMs = lastSeenAtEpochMs,
    )
}
