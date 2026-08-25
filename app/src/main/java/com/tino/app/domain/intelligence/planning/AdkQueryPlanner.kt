package com.tino.app.domain.intelligence.planning

import com.tino.app.domain.intelligence.IntelligenceRequest
import kotlinx.coroutines.CancellationException

interface AdkPlanProposalPort {
    suspend fun propose(request: IntelligenceRequest): IntelligencePlan?
}

class UnavailableAdkPlanProposal : AdkPlanProposalPort {
    override suspend fun propose(request: IntelligenceRequest): IntelligencePlan? = null
}

class AdkQueryPlanner(
    private val proposalPort: AdkPlanProposalPort,
    private val deterministicFallback: PlannerPort,
    private val observation: PlannerObservationPort = NoOpPlannerObservation(),
) : PlannerPort {
    override val id: String = "adk"

    override suspend fun plan(request: IntelligenceRequest): IntelligencePlan {
        val isKnownA2uiAction = request.resolvedContext["a2ui_action"] != null
        return try {
            if (isKnownA2uiAction) {
                deterministicFallback.plan(request)
            } else proposalPort.propose(request)?.let { proposal ->
                proposal.copy(plannerId = id).also { observation.record(it.plannerId) }
            } ?: deterministicFallback.plan(request).copy(
                plannerId = "${deterministicFallback.id}-fallback",
                fallbackReason = "adk_no_plan",
            ).also { observation.record(it.plannerId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            deterministicFallback.plan(request).copy(
                plannerId = "${deterministicFallback.id}-fallback",
                fallbackReason = "adk_exception",
            )
                .also { observation.record(it.plannerId) }
        }
    }
}

interface PlannerObservationPort {
    fun record(plannerId: String)
}

class NoOpPlannerObservation : PlannerObservationPort {
    override fun record(plannerId: String) = Unit
}

class PlannerSelector(
    private val deterministic: PlannerPort,
    private val adk: PlannerPort?,
    private val preferAdk: Boolean,
    private val observation: PlannerObservationPort = NoOpPlannerObservation(),
) : PlannerPort {
    override val id: String = "selector"

    override suspend fun plan(request: IntelligenceRequest): IntelligencePlan {
        // Typed A2UI actions already carry a validated goal/payload. They must
        // re-enter the same AgentRuntimePort without forcing local model
        // inference for a known operation.
        val isKnownA2uiAction = request.resolvedContext["a2ui_action"] != null
        val selected = if (preferAdk && adk != null && !isKnownA2uiAction) {
            adk.plan(request)
        } else {
            deterministic.plan(request)
        }
        observation.record(selected.plannerId)
        return selected
    }
}
