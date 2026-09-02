package com.tino.app.domain.intelligence.agent

import com.tino.app.domain.intelligence.IntelligenceErrorStage
import com.tino.app.domain.intelligence.IntelligenceExecutionResult
import com.tino.app.domain.intelligence.IntelligenceGroundingCompleteness
import com.tino.app.domain.intelligence.IntelligencePlanExecutor
import com.tino.app.domain.intelligence.IntelligencePlanValidator
import com.tino.app.domain.intelligence.IntelligenceRequest
import com.tino.app.domain.intelligence.IntelligenceResponse
import com.tino.app.domain.intelligence.IntelligenceResponseStatus
import com.tino.app.domain.intelligence.IntelligenceTelemetryEvent
import com.tino.app.domain.intelligence.IntelligenceTelemetryPort
import com.tino.app.domain.intelligence.IntelligenceValidationResult
import com.tino.app.domain.intelligence.IntelligenceValidationRejectionKind
import com.tino.app.domain.intelligence.NoOpIntelligenceTelemetry
import com.tino.app.domain.intelligence.planning.IntelligencePlan
import com.tino.app.domain.intelligence.planning.PlannerPort
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

enum class AgentLoopState {
    PLAN,
    EXECUTE_READ,
    OBSERVE,
    REPLAN,
    CLARIFY,
    REQUEST_CONFIRMATION,
    FINAL,
}

/** Terminal outcomes are deliberately independent from renderer/A2UI states. */
enum class AgentTerminalState {
    ANSWERED,
    REQUEST_CLARIFICATION,
    REQUEST_CONFIRMATION,
    INSUFFICIENT_DATA,
    TOOL_FAILURE,
    UNSUPPORTED,
    TIMEOUT,
}

data class AgentLoopLimits(
    val maxToolCalls: Int = 12,
    val maxReplans: Int = 3,
    val detectDuplicateToolCalls: Boolean = true,
    val detectLoops: Boolean = true,
)

data class AgentInteraction(
    val request: IntelligenceRequest,
    val maxTurns: Int = 3,
    val timeoutMs: Long = 8_000L,
    val limits: AgentLoopLimits = AgentLoopLimits(),
)

sealed interface AgentDecision {
    data class Replan(val reason: String) : AgentDecision
    data class Clarify(val prompt: String) : AgentDecision
    data object RequestConfirmation : AgentDecision
    data object Final : AgentDecision
}

data class AgentLoopTraceEntry(
    val turn: Int,
    val state: AgentLoopState,
    val plannerUsed: String,
    val decision: String? = null,
    val responseStatus: IntelligenceResponseStatus? = null,
)

data class AgentTurnResult(
    val response: IntelligenceResponse,
    val finalState: AgentLoopState,
    val turns: Int,
    val trace: List<AgentLoopTraceEntry>,
    val loopId: String,
    val terminalState: AgentTerminalState = AgentTerminalStateFromResponse(response),
)

private fun AgentTerminalStateFromResponse(response: IntelligenceResponse): AgentTerminalState = when (response.status) {
    IntelligenceResponseStatus.ANSWERED -> AgentTerminalState.ANSWERED
    IntelligenceResponseStatus.NEEDS_CLARIFICATION,
    IntelligenceResponseStatus.AMBIGUOUS_ENTITY -> AgentTerminalState.REQUEST_CLARIFICATION
    IntelligenceResponseStatus.INSUFFICIENT_DATA,
    IntelligenceResponseStatus.KNOWLEDGE_UNAVAILABLE -> AgentTerminalState.INSUFFICIENT_DATA
    IntelligenceResponseStatus.UNSUPPORTED -> AgentTerminalState.UNSUPPORTED
    IntelligenceResponseStatus.TOOL_UNAVAILABLE,
    IntelligenceResponseStatus.ERROR -> AgentTerminalState.TOOL_FAILURE
}

interface AgentRuntimePort {
    suspend fun run(interaction: AgentInteraction): AgentTurnResult
}

interface AgentDecisionPolicy {
    fun decide(
        response: IntelligenceResponse,
        turn: Int,
        maxTurns: Int,
    ): AgentDecision
}

class DeterministicAgentDecisionPolicy @Inject constructor() : AgentDecisionPolicy {
    override fun decide(
        response: IntelligenceResponse,
        turn: Int,
        maxTurns: Int,
    ): AgentDecision = when {
        response.status == IntelligenceResponseStatus.NEEDS_CLARIFICATION ||
            response.status == IntelligenceResponseStatus.AMBIGUOUS_ENTITY ->
            AgentDecision.Clarify(response.answer)
        response.status == IntelligenceResponseStatus.TOOL_UNAVAILABLE ->
            AgentDecision.Replan("tool_unavailable_observation")
        else -> AgentDecision.Final
    }
}

@Singleton
class DefaultAgentRuntime @Inject constructor(
    private val planner: PlannerPort,
    private val validator: IntelligencePlanValidator,
    private val executor: IntelligencePlanExecutor,
    private val telemetry: IntelligenceTelemetryPort = NoOpIntelligenceTelemetry(),
    private val decisionPolicy: AgentDecisionPolicy = DeterministicAgentDecisionPolicy(),
) : AgentRuntimePort {
    override suspend fun run(interaction: AgentInteraction): AgentTurnResult = try {
        withTimeout(interaction.timeoutMs) {
            runLoop(interaction)
        }
    } catch (timeout: TimeoutCancellationException) {
        val loopId = UUID.randomUUID().toString()
        val response = IntelligenceResponse(
            status = IntelligenceResponseStatus.ERROR,
            answer = "Não consegui concluir essa operação dentro do tempo seguro.",
            limitations = listOf("tempo_global_excedido"),
        )
        AgentTurnResult(
            response = response,
            finalState = AgentLoopState.FINAL,
            turns = 0,
            trace = listOf(
                AgentLoopTraceEntry(
                    turn = 0,
                    state = AgentLoopState.FINAL,
                    plannerUsed = planner.id,
                    decision = "TIMEOUT",
                    responseStatus = response.status,
                ),
            ),
            loopId = loopId,
            terminalState = AgentTerminalState.TIMEOUT,
        )
    }

    private suspend fun runLoop(interaction: AgentInteraction): AgentTurnResult {
        require(interaction.maxTurns > 0) { "maxTurns deve ser maior que zero" }
        require(interaction.limits.maxToolCalls > 0) { "maxToolCalls deve ser maior que zero" }
        require(interaction.limits.maxReplans >= 0) { "maxReplans não pode ser negativo" }
        val loopId = UUID.randomUUID().toString()
        val trace = mutableListOf<AgentLoopTraceEntry>()
        var request = interaction.request
        var turn = 0
        var replanCount = 0
        var toolCallCount = 0
        val observedPlans = mutableSetOf<String>()
        var lastResponse: IntelligenceResponse? = null

        try {
            while (turn < interaction.maxTurns) {
                turn++
                trace += AgentLoopTraceEntry(turn, AgentLoopState.PLAN, planner.id)
                val planStartedAt = System.currentTimeMillis()
                val plan = planner.plan(request)
                val planningLatencyMs = System.currentTimeMillis() - planStartedAt

                val planFingerprint = planFingerprint(request, plan)
                if ((interaction.limits.detectDuplicateToolCalls || interaction.limits.detectLoops) &&
                    !observedPlans.add(planFingerprint)
                ) {
                    return loopStopped(
                        loopId = loopId,
                        turn = turn,
                        trace = trace,
                        lastResponse = lastResponse,
                        limitation = "chamada_de_tool_duplicada_detectada",
                        terminalState = AgentTerminalState.TOOL_FAILURE,
                    )
                }
                if (toolCallCount + plan.steps.size > interaction.limits.maxToolCalls) {
                    return loopStopped(
                        loopId = loopId,
                        turn = turn,
                        trace = trace,
                        lastResponse = lastResponse,
                        limitation = "limite_de_tool_calls_excedido",
                        terminalState = AgentTerminalState.TOOL_FAILURE,
                    )
                }
                val validation = validator.validate(plan)
                if (!validation.isValid) {
                    val response = IntelligenceResponse(
                        status = IntelligenceResponseStatus.TOOL_UNAVAILABLE,
                        answer = "Não consegui validar com segurança o plano para essa pergunta.",
                        plan = plan.steps.map { it.toolName },
                        plannerUsed = plan.plannerId,
                        limitations = validation.errors,
                    )
                    trace += AgentLoopTraceEntry(
                        turn = turn,
                        state = AgentLoopState.FINAL,
                        plannerUsed = plan.plannerId,
                        decision = AgentDecision.Final::class.simpleName,
                        responseStatus = response.status,
                    )
                    recordTelemetry(
                        loopId = loopId,
                        turn = turn,
                        request = request,
                        plan = plan,
                        validation = IntelligenceValidationResult.REJECTED,
                        validationErrors = validation.errors,
                        execution = IntelligenceExecutionResult.NOT_RUN,
                        planningLatencyMs = planningLatencyMs,
                        response = response,
                        errorStage = IntelligenceErrorStage.VALIDATION,
                    )
                    return AgentTurnResult(
                        response,
                        AgentLoopState.FINAL,
                        turn,
                        trace,
                        loopId,
                        AgentTerminalState.TOOL_FAILURE,
                    )
                }

                trace += AgentLoopTraceEntry(turn, AgentLoopState.EXECUTE_READ, plan.plannerId)
                toolCallCount += plan.steps.size
                val response = executor.execute(request, plan)
                lastResponse = response
                trace += AgentLoopTraceEntry(
                    turn = turn,
                    state = AgentLoopState.OBSERVE,
                    plannerUsed = plan.plannerId,
                    responseStatus = response.status,
                )
                val decision = decisionPolicy.decide(response, turn, interaction.maxTurns)
                val decisionState = decision.toState()
                trace += AgentLoopTraceEntry(
                    turn = turn,
                    state = decisionState,
                    plannerUsed = plan.plannerId,
                    decision = decision.label,
                    responseStatus = response.status,
                )
                recordTelemetry(
                    loopId = loopId,
                    turn = turn,
                    request = request,
                    plan = plan,
                    validation = IntelligenceValidationResult.ACCEPTED,
                    execution = IntelligenceExecutionResult.SUCCEEDED,
                    planningLatencyMs = planningLatencyMs,
                    response = response,
                    errorStage = if (decision is AgentDecision.Replan) IntelligenceErrorStage.EXECUTION else IntelligenceErrorStage.NONE,
                    decision = decision.label,
                )

                when (decision) {
                    is AgentDecision.Replan -> {
                        if (replanCount >= interaction.limits.maxReplans) {
                            return AgentTurnResult(
                                response = response.copy(
                                    status = IntelligenceResponseStatus.ERROR,
                                    answer = "Não consegui concluir com segurança após observar o resultado.",
                                    limitations = response.limitations + "limite_de_replans_excedido",
                                ),
                                finalState = AgentLoopState.FINAL,
                                turns = turn,
                                trace = trace,
                                loopId = loopId,
                                terminalState = AgentTerminalState.TOOL_FAILURE,
                            )
                        }
                        replanCount++
                        request = request.copy(
                            resolvedContext = request.resolvedContext +
                                ("agent_observation" to observationFor(response)),
                        )
                    }
                    is AgentDecision.Clarify -> {
                        return AgentTurnResult(response, AgentLoopState.CLARIFY, turn, trace, loopId, AgentTerminalState.REQUEST_CLARIFICATION)
                    }
                    AgentDecision.RequestConfirmation -> {
                        return AgentTurnResult(response, AgentLoopState.REQUEST_CONFIRMATION, turn, trace, loopId, AgentTerminalState.REQUEST_CONFIRMATION)
                    }
                    AgentDecision.Final -> {
                        return AgentTurnResult(response, AgentLoopState.FINAL, turn, trace, loopId, AgentTerminalStateFromResponse(response))
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }

        val exhausted = lastResponse ?: IntelligenceResponse(
            status = IntelligenceResponseStatus.ERROR,
            answer = "Não consegui iniciar o planejamento com segurança.",
        )
        val response = exhausted.copy(
            status = IntelligenceResponseStatus.ERROR,
            answer = "Não consegui concluir essa pergunta dentro do limite seguro de tentativas.",
            limitations = exhausted.limitations + "limite_de_turns_excedido",
        )
        trace += AgentLoopTraceEntry(
            turn = turn,
            state = AgentLoopState.FINAL,
            plannerUsed = planner.id,
            decision = AgentDecision.Final.label,
            responseStatus = response.status,
        )
        return AgentTurnResult(response, AgentLoopState.FINAL, turn, trace, loopId, AgentTerminalState.TOOL_FAILURE)
    }

    private fun loopStopped(
        loopId: String,
        turn: Int,
        trace: MutableList<AgentLoopTraceEntry>,
        lastResponse: IntelligenceResponse?,
        limitation: String,
        terminalState: AgentTerminalState,
    ): AgentTurnResult {
        val response = (lastResponse ?: IntelligenceResponse(
            status = IntelligenceResponseStatus.ERROR,
            answer = "Não consegui executar essa operação com segurança.",
        )).copy(
            status = IntelligenceResponseStatus.ERROR,
            answer = "Interrompi o ciclo para proteger a operação.",
            limitations = lastResponse?.limitations.orEmpty() + limitation,
        )
        trace += AgentLoopTraceEntry(
            turn = turn,
            state = AgentLoopState.FINAL,
            plannerUsed = planner.id,
            decision = limitation,
            responseStatus = response.status,
        )
        return AgentTurnResult(response, AgentLoopState.FINAL, turn, trace, loopId, terminalState)
    }

    private fun planFingerprint(request: IntelligenceRequest, plan: IntelligencePlan): String = buildString {
        append(plan.steps.joinToString(",") { it.toolName })
        append("|")
        append(request.resolvedContext["agent_observation"].orEmpty())
    }

    private suspend fun recordTelemetry(
        loopId: String,
        turn: Int,
        request: IntelligenceRequest,
        plan: IntelligencePlan,
        validation: IntelligenceValidationResult,
        validationErrors: List<String> = emptyList(),
        execution: IntelligenceExecutionResult,
        planningLatencyMs: Long,
        response: IntelligenceResponse?,
        errorStage: IntelligenceErrorStage,
        decision: String = AgentDecision.Final.label,
    ) {
        try {
            telemetry.record(
                IntelligenceTelemetryEvent(
                    requestId = request.requestId,
                    sessionId = request.sessionId,
                    plannerSelected = plannerLabel(planner.id),
                    plannerUsed = plannerLabel(plan.plannerId),
                    fallbackReason = plan.fallbackReason,
                    plan = plan.steps.map { it.toolName },
                    validationResult = validation,
                    validationErrors = validationErrors,
                    validationRejectionKinds = validationErrors.mapNotNull(::rejectionKind),
                    fallbackUsed = plan.plannerId.endsWith("-fallback"),
                    executionResult = execution,
                    groundingCompleteness = grounding(response, plan, validation, execution),
                    latencyMs = 0L,
                    planningLatencyMs = planningLatencyMs,
                    errorStage = errorStage,
                    occurredAtEpochMs = System.currentTimeMillis(),
                    loopId = loopId,
                    turnIndex = turn,
                    loopState = telemetryState(decision),
                    decision = decision,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Loop telemetry is observational and cannot break the operation.
        }
    }

    private fun observationFor(response: IntelligenceResponse): String = buildString {
        append("status=")
        append(response.status.name)
        if (response.limitations.isNotEmpty()) {
            append(";limitations=")
            append(response.limitations.joinToString("|"))
        }
        if (response.toolCalls.isNotEmpty()) {
            append(";tools=")
            append(response.toolCalls.joinToString(",") { it.name })
        }
    }

    private fun plannerLabel(value: String): String = "DETERMINISTIC"

    private fun telemetryState(decision: String): String = when (decision) {
        "REPLAN" -> AgentLoopState.REPLAN.name
        "CLARIFY" -> AgentLoopState.CLARIFY.name
        "REQUEST_CONFIRMATION" -> AgentLoopState.REQUEST_CONFIRMATION.name
        else -> AgentLoopState.FINAL.name
    }

    private fun grounding(
        response: IntelligenceResponse?,
        plan: IntelligencePlan,
        validation: IntelligenceValidationResult,
        execution: IntelligenceExecutionResult,
    ): IntelligenceGroundingCompleteness {
        if (validation != IntelligenceValidationResult.ACCEPTED || execution != IntelligenceExecutionResult.SUCCEEDED) {
            return IntelligenceGroundingCompleteness.NOT_RUN
        }
        if (response == null || plan.steps.isEmpty()) return IntelligenceGroundingCompleteness.NOT_APPLICABLE
        if (response.status == IntelligenceResponseStatus.ERROR) return IntelligenceGroundingCompleteness.MISSING
        if (response.status == IntelligenceResponseStatus.INSUFFICIENT_DATA ||
            response.status == IntelligenceResponseStatus.KNOWLEDGE_UNAVAILABLE
        ) return IntelligenceGroundingCompleteness.PARTIAL
        return if (response.factsUsed.isNotEmpty() || response.analyticsUsed.isNotEmpty() ||
            response.knowledgeUsed.isNotEmpty() || response.memoryUsed.isNotEmpty()
        ) IntelligenceGroundingCompleteness.COMPLETE else IntelligenceGroundingCompleteness.MISSING
    }

    private fun rejectionKind(error: String): IntelligenceValidationRejectionKind? = when {
        error.contains("não está registrada", ignoreCase = true) -> IntelligenceValidationRejectionKind.UNKNOWN_TOOL
        error.contains("argument", ignoreCase = true) -> IntelligenceValidationRejectionKind.INVALID_ARGUMENT
        error.contains("mutação", ignoreCase = true) || error.contains("policy", ignoreCase = true) -> IntelligenceValidationRejectionKind.POLICY
        error.contains("excede o limite", ignoreCase = true) -> IntelligenceValidationRejectionKind.PLAN_LIMIT
        else -> IntelligenceValidationRejectionKind.OTHER
    }

    private fun AgentDecision.toState(): AgentLoopState = when (this) {
        is AgentDecision.Replan -> AgentLoopState.REPLAN
        is AgentDecision.Clarify -> AgentLoopState.CLARIFY
        AgentDecision.RequestConfirmation -> AgentLoopState.REQUEST_CONFIRMATION
        AgentDecision.Final -> AgentLoopState.FINAL
    }

    private val AgentDecision.label: String
        get() = when (this) {
            is AgentDecision.Replan -> "REPLAN"
            is AgentDecision.Clarify -> "CLARIFY"
            AgentDecision.RequestConfirmation -> "REQUEST_CONFIRMATION"
            AgentDecision.Final -> "FINAL"
        }
}
