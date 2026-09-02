package com.tino.app.core.database

import com.tino.app.domain.intelligence.TinoBusinessEvidence
import com.tino.app.domain.intelligence.TinoEvidenceRepository
import com.tino.app.domain.intelligence.TinoEvidenceSource
import com.tino.app.domain.intelligence.TinoEvidenceType
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTinoEvidenceRepository @Inject constructor(
    private val dao: IntelligenceEvidenceDao,
) : TinoEvidenceRepository {
    override suspend fun upsertAll(evidence: List<TinoBusinessEvidence>) {
        if (evidence.isEmpty()) return
        dao.upsertAll(evidence.map { it.toEntity() })
    }

    override suspend fun list(limit: Int): List<TinoBusinessEvidence> =
        dao.list(limit.coerceIn(1, MAX_LIMIT)).map { it.toDomain() }

    override suspend fun find(id: String): TinoBusinessEvidence? = dao.findById(id)?.toDomain()

    private fun TinoBusinessEvidence.toEntity() = IntelligenceEvidenceEntity(
        id = id,
        type = type.name,
        subjectId = subjectId,
        factsJson = JSONObject().apply { facts.forEach { (key, value) -> put(key, value) } }.toString(),
        source = source.name,
        confidence = confidence,
        occurredAtEpochMs = occurredAtEpochMs,
        detectedAtEpochMs = detectedAtEpochMs,
    )

    private fun IntelligenceEvidenceEntity.toDomain() = TinoBusinessEvidence(
        id = id,
        type = runCatching { TinoEvidenceType.valueOf(type) }.getOrDefault(TinoEvidenceType.OBSERVATION),
        subjectId = subjectId,
        facts = runCatching {
            val json = JSONObject(factsJson)
            json.keys().asSequence().associateWith { key -> json.optString(key) }
        }.getOrDefault(emptyMap()),
        source = runCatching { TinoEvidenceSource.valueOf(source) }.getOrDefault(TinoEvidenceSource.ROOM),
        confidence = confidence,
        occurredAtEpochMs = occurredAtEpochMs,
        detectedAtEpochMs = detectedAtEpochMs,
    )

    private companion object {
        const val MAX_LIMIT = 1_000
    }
}
