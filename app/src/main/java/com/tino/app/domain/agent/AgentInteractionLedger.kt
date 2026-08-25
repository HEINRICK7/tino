package com.tino.app.domain.agent

import com.tino.app.domain.language.ContextReferenceSource
import com.tino.app.domain.language.ContextTurnClassification
import com.tino.app.domain.language.LanguageEntityType
import com.tino.app.domain.language.TinoIntent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Redacted operational trace for debugging context decisions; it never stores chain-of-thought. */
data class AgentInteractionTrace(
    val interactionId: String = UUID.randomUUID().toString(),
    val turnId: String = UUID.randomUUID().toString(),
    val occurredAtEpochMs: Long = System.currentTimeMillis(),
    val classification: ContextTurnClassification,
    val intent: TinoIntent? = null,
    val contextSources: Map<LanguageEntityType, ContextReferenceSource> = emptyMap(),
    val capability: TinoCapabilityId? = null,
    val clarification: String? = null,
    val result: String? = null,
)

/** Small process-local observability surface for context quality metrics. */
@Singleton
class AgentInteractionLedger @Inject constructor() {
    companion object {
        const val CONTEXT_RESOLUTION_ACCURACY = "CONTEXT_RESOLUTION_ACCURACY"
        const val NECESSARY_CLARIFICATION = "necessaryClarification"
        const val UNNECESSARY_CLARIFICATION = "unnecessaryClarification"
        const val MULTITURN_SUCCESS_RATE = "multiturnSuccessRate"
        const val WRONG_CONTEXT_RESOLUTION_RATE = "WRONG_CONTEXT_RESOLUTION_RATE"
    }

    private val traces = mutableListOf<AgentInteractionTrace>()

    @Synchronized
    fun record(trace: AgentInteractionTrace): AgentInteractionTrace {
        traces += trace
        if (traces.size > 200) traces.removeAt(0)
        return trace
    }

    @Synchronized
    fun recent(): List<AgentInteractionTrace> = traces.toList()
}
