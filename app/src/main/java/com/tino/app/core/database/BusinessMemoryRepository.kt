package com.tino.app.core.database

import com.tino.app.domain.language.BusinessMemoryRecord
import com.tino.app.domain.language.BusinessMemoryStorePort
import com.tino.app.domain.language.BusinessMemoryKind
import com.tino.app.domain.language.MemoryConfidence
import com.tino.app.domain.language.MemoryLifecycle
import com.tino.app.domain.language.MemoryProvenance
import com.tino.app.domain.language.MemoryProvenanceType
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBusinessMemoryRepository @Inject constructor(
    private val dao: BusinessMemoryDao,
) : BusinessMemoryStorePort {
    override suspend fun find(scopeKey: String, memoryKey: String, value: String): BusinessMemoryRecord? =
        dao.find(scopeKey, memoryKey, value)?.toDomain()

    override suspend fun findByKey(scopeKey: String, memoryKey: String): List<BusinessMemoryRecord> =
        dao.findByKey(scopeKey, memoryKey).map { it.toDomain() }

    override suspend fun upsert(record: BusinessMemoryRecord) {
        dao.upsert(record.toEntity())
    }

    override suspend fun list(scopeKey: String): List<BusinessMemoryRecord> =
        dao.list(scopeKey).map { it.toDomain() }

    private fun BusinessMemoryEntity.toDomain() = BusinessMemoryRecord(
        id = id,
        scopeKey = scopeKey,
        memoryKey = memoryKey,
        value = value,
        kind = BusinessMemoryKind.valueOf(kind),
        lifecycle = MemoryLifecycle.valueOf(lifecycle),
        confidence = MemoryConfidence(confidence),
        supportCount = supportCount,
        contradictionCount = contradictionCount,
        provenance = JSONArray(provenanceJson).let { array -> (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            MemoryProvenance(MemoryProvenanceType.valueOf(item.getString("type")), item.optString("source").ifBlank { null }, item.getLong("at"))
        } },
        sourceEventIds = JSONArray(sourceEventIdsJson).let { array -> (0 until array.length()).map(array::getString) },
        updatedAtEpochMs = updatedAtEpochMs,
        demotionReason = demotionReason,
    )

    private fun BusinessMemoryRecord.toEntity() = BusinessMemoryEntity(
        id = id,
        scopeKey = scopeKey,
        memoryKey = memoryKey,
        value = value,
        kind = kind.name,
        lifecycle = lifecycle.name,
        confidence = confidence.value,
        supportCount = supportCount,
        contradictionCount = contradictionCount,
        provenanceJson = JSONArray().also { array -> provenance.forEach { item -> array.put(JSONObject().apply { put("type", item.type.name); put("source", item.sourceInteractionId); put("at", item.occurredAtEpochMs) }) } }.toString(),
        sourceEventIdsJson = JSONArray(sourceEventIds).toString(),
        updatedAtEpochMs = updatedAtEpochMs,
        demotionReason = demotionReason,
    )
}

