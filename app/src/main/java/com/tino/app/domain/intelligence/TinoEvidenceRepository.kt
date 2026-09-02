package com.tino.app.domain.intelligence

interface TinoEvidenceRepository {
    suspend fun upsertAll(evidence: List<TinoBusinessEvidence>)
    suspend fun list(limit: Int = 200): List<TinoBusinessEvidence>
    suspend fun find(id: String): TinoBusinessEvidence?
}
