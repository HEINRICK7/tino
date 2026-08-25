package com.tino.app.domain.language

data class CorrectionSignal(
    val spoken: String,
    val canonical: String,
    val entityType: LanguageEntityType,
)

/** Compatibility facade over the scoped CorrectionLearningEngine. */
class LearnedAliasMemory(
    private val engine: CorrectionLearningEngine = CorrectionLearningEngine(),
) {
    private val scope = "legacy-${System.identityHashCode(this)}"

    fun record(signal: CorrectionSignal, promotionThreshold: Int = 2): String? {
        val entry = engine.record(
            CorrectionEvent(
                spoken = signal.spoken,
                canonical = signal.canonical,
                entityType = signal.entityType,
                scopeKey = scope,
                provenance = CorrectionProvenance.USER_CORRECTION,
            ),
        )
        return entry.takeIf { entry.supportCount >= promotionThreshold &&
            entry.status in setOf(CorrectionLearningStatus.LEARNED, CorrectionLearningStatus.TRUSTED) }
            ?.canonical
    }

    fun resolve(spoken: String, entityType: LanguageEntityType): String? =
        engine.resolve(spoken, entityType, CorrectionLearningScope.SESSION, scope)

    fun promotedAliases(): Map<String, String> = engine.entries(CorrectionLearningScope.SESSION, scope)
        .filter { it.status in setOf(CorrectionLearningStatus.LEARNED, CorrectionLearningStatus.TRUSTED) }
        .associate { "${it.entityType}:${it.spoken}" to it.canonical }

    fun clear() = engine.clearScope(CorrectionLearningScope.SESSION, scope)
}
