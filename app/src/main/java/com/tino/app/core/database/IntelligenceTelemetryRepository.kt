package com.tino.app.core.database

import com.tino.app.domain.intelligence.IntelligenceErrorStage
import com.tino.app.domain.intelligence.IntelligenceExecutionResult
import com.tino.app.domain.intelligence.IntelligenceGroundingCompleteness
import com.tino.app.domain.intelligence.IntelligenceTelemetryEvent
import com.tino.app.domain.intelligence.IntelligenceTelemetryPort
import com.tino.app.domain.intelligence.IntelligenceValidationResult
import com.tino.app.domain.intelligence.IntelligenceValidationRejectionKind
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomIntelligenceTelemetryRepository @Inject constructor(
    private val dao: IntelligenceTelemetryDao,
) : IntelligenceTelemetryPort {
    override suspend fun record(event: IntelligenceTelemetryEvent) {
        dao.insert(event.toEntity())
    }

    override suspend fun recent(limit: Int): List<IntelligenceTelemetryEvent> =
        dao.recent(limit.coerceIn(1, 1_000)).map { it.toDomain() }

    private fun IntelligenceTelemetryEvent.toEntity() = IntelligenceTelemetryEntity(
        id = id,
        requestId = requestId,
        sessionId = sessionId,
        plannerSelected = plannerSelected,
        plannerUsed = plannerUsed,
        fallbackReason = fallbackReason,
        planJson = plan.toJson(),
        validationResult = validationResult.name,
        validationErrorsJson = validationErrors.toJson(),
        validationRejectionKindsJson = validationRejectionKinds.map { it.name }.toJson(),
        fallbackUsed = fallbackUsed,
        executionResult = executionResult.name,
        groundingCompleteness = groundingCompleteness.name,
        latencyMs = latencyMs,
        planningLatencyMs = planningLatencyMs,
        errorStage = errorStage.name,
        occurredAtEpochMs = occurredAtEpochMs,
        loopId = loopId,
        turnIndex = turnIndex,
        loopState = loopState,
        decision = decision,
    )

    private fun IntelligenceTelemetryEntity.toDomain() = IntelligenceTelemetryEvent(
        id = id,
        requestId = requestId,
        sessionId = sessionId,
        plannerSelected = plannerSelected,
        plannerUsed = plannerUsed,
        fallbackReason = fallbackReason,
        plan = planJson.fromJsonArray(),
        validationResult = validationResult.toEnum(IntelligenceValidationResult.NOT_RUN),
        validationErrors = validationErrorsJson.fromJsonArray(),
        validationRejectionKinds = validationRejectionKindsJson.fromJsonArray()
            .mapNotNull { it.toEnumOrNull<IntelligenceValidationRejectionKind>() },
        fallbackUsed = fallbackUsed,
        executionResult = executionResult.toEnum(IntelligenceExecutionResult.NOT_RUN),
        groundingCompleteness = groundingCompleteness.toEnum(IntelligenceGroundingCompleteness.NOT_RUN),
        latencyMs = latencyMs,
        planningLatencyMs = planningLatencyMs,
        errorStage = errorStage.toEnum(IntelligenceErrorStage.NONE),
        occurredAtEpochMs = occurredAtEpochMs,
        loopId = loopId,
        turnIndex = turnIndex,
        loopState = loopState,
        decision = decision,
    )

    private fun List<String>.toJson(): String = JSONArray().also { array -> forEach(array::put) }.toString()

    private fun String.fromJsonArray(): List<String> {
        val array = JSONArray(this)
        return (0 until array.length()).map { array.getString(it) }
    }

    private inline fun <reified T : Enum<T>> String.toEnum(fallback: T): T =
        runCatching { enumValueOf<T>(this) }.getOrDefault(fallback)

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
        runCatching { enumValueOf<T>(this) }.getOrNull()
}
