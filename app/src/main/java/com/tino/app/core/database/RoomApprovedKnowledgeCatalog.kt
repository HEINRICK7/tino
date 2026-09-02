package com.tino.app.core.database

import androidx.room.withTransaction
import com.tino.app.domain.intelligence.ApprovedKnowledgeCatalog
import com.tino.app.domain.intelligence.ApprovedKnowledgeCatalogPort
import com.tino.app.domain.intelligence.ApprovedKnowledgeCatalogValidator
import com.tino.app.domain.intelligence.BuiltInApprovedKnowledgeCatalog
import com.tino.app.domain.intelligence.KnowledgeCatalogUpdateResult
import com.tino.app.domain.intelligence.KnowledgeCatalogUpdateStatus
import com.tino.app.domain.intelligence.ApprovedKnowledgeEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable adapter for reviewed knowledge. The domain port stays independent
 * from Room, while activation and rollback are committed as one transaction.
 */
@Singleton
class RoomApprovedKnowledgeCatalog @Inject constructor(
    private val dao: ApprovedKnowledgeCatalogDao,
    private val database: TinoDatabase,
) : ApprovedKnowledgeCatalogPort {
    private val lock = Mutex()
    private var loaded = false
    private var active = BuiltInApprovedKnowledgeCatalog.current
    private var previous: ApprovedKnowledgeCatalog? = null

    override suspend fun current(): ApprovedKnowledgeCatalog {
        ensureLoaded()
        return lock.withLock { active }
    }

    override suspend fun activate(candidate: ApprovedKnowledgeCatalog): KnowledgeCatalogUpdateResult {
        val validation = ApprovedKnowledgeCatalogValidator.validate(candidate)
        if (!validation.isValid) {
            return KnowledgeCatalogUpdateResult(
                status = KnowledgeCatalogUpdateStatus.REJECTED,
                activeVersion = current().version,
                errors = validation.errors,
            )
        }
        ensureLoaded()
        return lock.withLock {
            if (candidate.version == active.version) {
                KnowledgeCatalogUpdateResult(
                    status = KnowledgeCatalogUpdateStatus.REJECTED,
                    activeVersion = active.version,
                    errors = listOf("A versão ${candidate.version} já está ativa."),
                )
            } else {
                val oldActive = active
                val nextActive = candidate.copy(activatedAtEpochMs = System.currentTimeMillis())
                persist(nextActive, oldActive)
                previous = oldActive
                active = nextActive
                KnowledgeCatalogUpdateResult(
                    status = KnowledgeCatalogUpdateStatus.ACTIVATED,
                    activeVersion = active.version,
                )
            }
        }
    }

    override suspend fun rollback(): KnowledgeCatalogUpdateResult {
        ensureLoaded()
        return lock.withLock {
            val prior = previous ?: return@withLock KnowledgeCatalogUpdateResult(
                status = KnowledgeCatalogUpdateStatus.REJECTED,
                activeVersion = active.version,
                errors = listOf("Não há uma versão anterior para reverter."),
            )
            val oldActive = active
            val nextActive = prior.copy(activatedAtEpochMs = System.currentTimeMillis())
            persist(nextActive, oldActive)
            previous = oldActive
            active = nextActive
            KnowledgeCatalogUpdateResult(
                status = KnowledgeCatalogUpdateStatus.ROLLED_BACK,
                activeVersion = active.version,
            )
        }
    }

    private suspend fun ensureLoaded() {
        lock.withLock {
            if (loaded) return@withLock
            val persistedActive = dao.active()?.toDomain()
            if (persistedActive != null && ApprovedKnowledgeCatalogValidator.validate(persistedActive).isValid) {
                active = persistedActive
                previous = dao.previous()?.toDomain()?.takeIf {
                    ApprovedKnowledgeCatalogValidator.validate(it).isValid
                }
            } else {
                previous = null
                persist(active, null)
            }
            loaded = true
        }
    }

    private suspend fun persist(nextActive: ApprovedKnowledgeCatalog, nextPrevious: ApprovedKnowledgeCatalog?) {
        database.withTransaction {
            dao.clear()
            dao.upsert(nextActive.toEntity(CATALOG_ACTIVE))
            nextPrevious?.let { dao.upsert(it.toEntity(CATALOG_PREVIOUS)) }
        }
    }

    private fun ApprovedKnowledgeCatalog.toEntity(state: String) = ApprovedKnowledgeCatalogEntity(
        version = version,
        payloadJson = toJson(),
        activatedAtEpochMs = activatedAtEpochMs,
        state = state,
    )

    private fun ApprovedKnowledgeCatalog.toJson(): String = JSONObject().apply {
        put("version", version)
        put("activatedAtEpochMs", activatedAtEpochMs)
        put("entries", JSONArray().also { entriesArray ->
            entries.forEach { entry ->
                entriesArray.put(JSONObject().apply {
                    put("id", entry.id)
                    put("collection", entry.collection)
                    put("phrases", JSONArray(entry.phrases))
                    put("keywords", JSONArray(entry.keywords))
                    put("answer", entry.answer)
                    put("sourceRef", entry.sourceRef ?: JSONObject.NULL)
                })
            }
        })
    }.toString()

    private fun ApprovedKnowledgeCatalogEntity.toDomain(): ApprovedKnowledgeCatalog? = runCatching {
        val json = JSONObject(payloadJson)
        val entriesJson = json.getJSONArray("entries")
        val entries = buildList {
            for (index in 0 until entriesJson.length()) {
                val item = entriesJson.getJSONObject(index)
                add(
                    ApprovedKnowledgeEntry(
                        id = item.getString("id"),
                        collection = item.getString("collection"),
                        phrases = item.getJSONArray("phrases").toStringList(),
                        keywords = item.getJSONArray("keywords").toStringList(),
                        answer = item.getString("answer"),
                        sourceRef = if (item.isNull("sourceRef")) {
                            null
                        } else {
                            item.optString("sourceRef").ifBlank { null }
                        },
                    ),
                )
            }
        }
        ApprovedKnowledgeCatalog(
            version = json.getString("version").takeIf { it == version } ?: return@runCatching null,
            entries = entries,
            activatedAtEpochMs = json.optLong("activatedAtEpochMs", activatedAtEpochMs),
        )
    }.getOrNull()

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) add(getString(index))
    }

    private companion object {
        const val CATALOG_ACTIVE = "ACTIVE"
        const val CATALOG_PREVIOUS = "PREVIOUS"
    }
}
