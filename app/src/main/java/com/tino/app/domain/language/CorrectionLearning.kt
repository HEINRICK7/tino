package com.tino.app.domain.language

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class CorrectionLearningStatus {
    CANDIDATE,
    LEARNED,
    TRUSTED,
    DEMOTED,
    REMOVED,
}

enum class CorrectionProvenance {
    USER_CORRECTION,
    USER_CONFIRMATION,
    USER_CONTRADICTION,
    SYSTEM_DEMOTION,
    USER_REMOVAL,
}

enum class CorrectionLearningScope {
    SESSION,
    STORE,
}

data class CorrectionEvent(
    val id: String = UUID.randomUUID().toString(),
    val spoken: String,
    val canonical: String,
    val entityType: LanguageEntityType,
    val scope: CorrectionLearningScope = CorrectionLearningScope.SESSION,
    val scopeKey: String,
    val provenance: CorrectionProvenance,
    val sourceInteractionId: String? = null,
    val occurredAtEpochMs: Long = System.currentTimeMillis(),
)

data class CorrectionLearningEntry(
    val id: String,
    val spoken: String,
    val canonical: String,
    val entityType: LanguageEntityType,
    val scope: CorrectionLearningScope,
    val scopeKey: String,
    val status: CorrectionLearningStatus,
    val supportCount: Int,
    val contradictionCount: Int,
    val provenance: Set<CorrectionProvenance>,
    val sourceEventIds: List<String>,
    val lastUpdatedAtEpochMs: Long,
    val demotionReason: String? = null,
)

interface CorrectionLearningPort {
    fun record(event: CorrectionEvent): CorrectionLearningEntry

    fun resolve(
        spoken: String,
        entityType: LanguageEntityType,
        scope: CorrectionLearningScope,
        scopeKey: String,
    ): String?

    fun demote(
        spoken: String,
        entityType: LanguageEntityType,
        scope: CorrectionLearningScope,
        scopeKey: String,
        reason: String,
    ): List<CorrectionLearningEntry>

    fun remove(
        spoken: String,
        canonical: String?,
        entityType: LanguageEntityType,
        scope: CorrectionLearningScope,
        scopeKey: String,
    ): List<CorrectionLearningEntry>

    fun entries(scope: CorrectionLearningScope, scopeKey: String): List<CorrectionLearningEntry>
}

/**
 * Local, scoped correction memory. It learns a mapping only after repeated
 * consistent evidence and never promotes a correction into a global catalog.
 */
@Singleton
class CorrectionLearningEngine @Inject constructor() : CorrectionLearningPort {
    companion object {
        const val DEFAULT_LEARNED_THRESHOLD = 2
        const val DEFAULT_TRUSTED_THRESHOLD = 3
    }

    private val entries = linkedMapOf<String, CorrectionLearningEntry>()

    @Synchronized
    override fun record(event: CorrectionEvent): CorrectionLearningEntry {
        val spoken = LanguageNormalizer.normalize(event.spoken)
        val canonical = LanguageNormalizer.normalize(event.canonical)
        require(spoken.isNotBlank()) { "spoken não pode ser vazio" }
        require(canonical.isNotBlank()) { "canonical não pode ser vazio" }

        val key = key(event.scope, event.scopeKey, event.entityType, spoken, canonical)
        val previous = entries[key]
        val support = (previous?.supportCount ?: 0) + 1
        val status = when {
            support >= DEFAULT_TRUSTED_THRESHOLD -> CorrectionLearningStatus.TRUSTED
            support >= DEFAULT_LEARNED_THRESHOLD -> CorrectionLearningStatus.LEARNED
            else -> CorrectionLearningStatus.CANDIDATE
        }
        val updated = CorrectionLearningEntry(
            id = previous?.id ?: event.id,
            spoken = spoken,
            canonical = canonical,
            entityType = event.entityType,
            scope = event.scope,
            scopeKey = event.scopeKey,
            status = status,
            supportCount = support,
            contradictionCount = previous?.contradictionCount ?: 0,
            provenance = (previous?.provenance.orEmpty() + event.provenance),
            sourceEventIds = (previous?.sourceEventIds.orEmpty() + event.id).takeLast(20),
            lastUpdatedAtEpochMs = event.occurredAtEpochMs,
            demotionReason = null,
        )

        if (event.provenance == CorrectionProvenance.USER_CONTRADICTION) {
            demoteInternal(
                spoken = spoken,
                entityType = event.entityType,
                scope = event.scope,
                scopeKey = event.scopeKey,
                exceptKey = key,
                reason = "correção contraditória recebida",
                event = event,
            )
        }
        entries[key] = updated
        return updated
    }

    @Synchronized
    override fun resolve(
        spoken: String,
        entityType: LanguageEntityType,
        scope: CorrectionLearningScope,
        scopeKey: String,
    ): String? = entries.values
        .asSequence()
        .filter {
            it.spoken == LanguageNormalizer.normalize(spoken) &&
                it.entityType == entityType &&
                it.scope == scope &&
                it.scopeKey == scopeKey &&
                it.status in setOf(CorrectionLearningStatus.LEARNED, CorrectionLearningStatus.TRUSTED)
        }
        .maxWithOrNull(compareBy<CorrectionLearningEntry> { it.supportCount }.thenBy { it.lastUpdatedAtEpochMs })
        ?.canonical

    @Synchronized
    override fun demote(
        spoken: String,
        entityType: LanguageEntityType,
        scope: CorrectionLearningScope,
        scopeKey: String,
        reason: String,
    ): List<CorrectionLearningEntry> = demoteInternal(
        spoken = LanguageNormalizer.normalize(spoken),
        entityType = entityType,
        scope = scope,
        scopeKey = scopeKey,
        exceptKey = null,
        reason = reason,
        event = null,
    )

    @Synchronized
    override fun remove(
        spoken: String,
        canonical: String?,
        entityType: LanguageEntityType,
        scope: CorrectionLearningScope,
        scopeKey: String,
    ): List<CorrectionLearningEntry> {
        val normalizedSpoken = LanguageNormalizer.normalize(spoken)
        val normalizedCanonical = canonical?.let(LanguageNormalizer::normalize)
        val matching = entries.filterValues {
            it.spoken == normalizedSpoken &&
                (normalizedCanonical == null || it.canonical == normalizedCanonical) &&
                it.entityType == entityType && it.scope == scope && it.scopeKey == scopeKey
        }
        val removed = matching.map { (key, current) ->
            val updated = current.copy(
                status = CorrectionLearningStatus.REMOVED,
                provenance = current.provenance + CorrectionProvenance.USER_REMOVAL,
                lastUpdatedAtEpochMs = System.currentTimeMillis(),
            )
            entries[key] = updated
            updated
        }
        return removed
    }

    @Synchronized
    override fun entries(scope: CorrectionLearningScope, scopeKey: String): List<CorrectionLearningEntry> =
        entries.values.filter { it.scope == scope && it.scopeKey == scopeKey }

    fun clear() {
        synchronized(this) { entries.clear() }
    }

    fun clearScope(scope: CorrectionLearningScope, scopeKey: String) {
        synchronized(this) {
            entries.keys.removeAll { it.startsWith("$scope:$scopeKey:") }
        }
    }

    private fun demoteInternal(
        spoken: String,
        entityType: LanguageEntityType,
        scope: CorrectionLearningScope,
        scopeKey: String,
        exceptKey: String?,
        reason: String,
        event: CorrectionEvent?,
    ): List<CorrectionLearningEntry> {
        val matching = entries.filterValues {
            it.spoken == spoken &&
                it.entityType == entityType &&
                it.scope == scope &&
                it.scopeKey == scopeKey &&
                it.status in setOf(CorrectionLearningStatus.LEARNED, CorrectionLearningStatus.TRUSTED) &&
                (exceptKey == null || key(scope, scopeKey, entityType, spoken, it.canonical) != exceptKey)
        }
        return matching.map { (key, current) ->
            val updated = current.copy(
                status = CorrectionLearningStatus.DEMOTED,
                contradictionCount = current.contradictionCount + 1,
                provenance = current.provenance + (event?.provenance ?: CorrectionProvenance.SYSTEM_DEMOTION),
                sourceEventIds = event?.let { (current.sourceEventIds + it.id).takeLast(20) } ?: current.sourceEventIds,
                lastUpdatedAtEpochMs = event?.occurredAtEpochMs ?: System.currentTimeMillis(),
                demotionReason = reason,
            )
            entries[key] = updated
            updated
        }
    }

    private fun key(
        scope: CorrectionLearningScope,
        scopeKey: String,
        entityType: LanguageEntityType,
        spoken: String,
        canonical: String,
    ): String = "$scope:$scopeKey:$entityType:$spoken:$canonical"
}
