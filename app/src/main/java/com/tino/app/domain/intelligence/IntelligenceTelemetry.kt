package com.tino.app.domain.intelligence

import java.util.UUID
import javax.inject.Inject

enum class IntelligenceValidationResult {
    NOT_RUN,
    ACCEPTED,
    REJECTED,
}

enum class IntelligenceExecutionResult {
    NOT_RUN,
    SUCCEEDED,
    FAILED,
}

enum class IntelligenceErrorStage {
    NONE,
    PLANNING,
    VALIDATION,
    EXECUTION,
    GROUNDING,
}

enum class IntelligenceGroundingCompleteness {
    NOT_RUN,
    NOT_APPLICABLE,
    COMPLETE,
    PARTIAL,
    MISSING,
}

enum class IntelligenceValidationRejectionKind {
    UNKNOWN_TOOL,
    INVALID_ARGUMENT,
    POLICY,
    PLAN_LIMIT,
    OTHER,
}

data class IntelligenceTelemetryEvent(
    val id: String = UUID.randomUUID().toString(),
    val requestId: String,
    val sessionId: String = "",
    val plannerSelected: String = "DETERMINISTIC",
    val plannerUsed: String,
    val fallbackReason: String? = null,
    val plan: List<String> = emptyList(),
    val validationResult: IntelligenceValidationResult,
    val validationErrors: List<String> = emptyList(),
    val validationRejectionKinds: List<IntelligenceValidationRejectionKind> = emptyList(),
    val fallbackUsed: Boolean,
    val executionResult: IntelligenceExecutionResult,
    val groundingCompleteness: IntelligenceGroundingCompleteness = IntelligenceGroundingCompleteness.NOT_RUN,
    val latencyMs: Long,
    val planningLatencyMs: Long,
    val errorStage: IntelligenceErrorStage,
    val occurredAtEpochMs: Long,
    val loopId: String = "",
    val turnIndex: Int = 0,
    val loopState: String = "",
    val decision: String = "",
)

interface IntelligenceTelemetryPort {
    suspend fun record(event: IntelligenceTelemetryEvent)

    suspend fun recent(limit: Int = 100): List<IntelligenceTelemetryEvent>
}

class NoOpIntelligenceTelemetry @Inject constructor() : IntelligenceTelemetryPort {
    override suspend fun record(event: IntelligenceTelemetryEvent) = Unit

    override suspend fun recent(limit: Int): List<IntelligenceTelemetryEvent> = emptyList()
}
