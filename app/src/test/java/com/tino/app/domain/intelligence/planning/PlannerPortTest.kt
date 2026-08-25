package com.tino.app.domain.intelligence.planning

import com.tino.app.domain.intelligence.IntelligencePlanStep
import com.tino.app.domain.intelligence.IntelligenceRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerPortTest {
    private val request = IntelligenceRequest(
        requestId = "request-1",
        sessionId = "session-1",
        utterance = "Qual produto está com menor estoque?",
    )

    @Test
    fun adkPlannerReturnsValidatedPlanContractWithoutExecutingTools() = runBlocking {
        val planner = AdkQueryPlanner(
            proposalPort = object : AdkPlanProposalPort {
                override suspend fun propose(request: IntelligenceRequest): IntelligencePlan =
                    IntelligencePlan(
                        goal = IntelligenceGoal.LOWEST_STOCK,
                        steps = listOf(IntelligencePlanStep("calculate_lowest_stock", "ordenar saldos")),
                    )
            },
            deterministicFallback = DeterministicIntelligenceQueryPlanner(),
        )

        val plan = planner.plan(request)

        assertEquals("adk", plan.plannerId)
        assertEquals(IntelligenceGoal.LOWEST_STOCK, plan.goal)
        assertEquals("calculate_lowest_stock", plan.steps.single().toolName)
    }

    @Test
    fun adkPlannerFallsBackWhenProposalIsUnavailable() = runBlocking {
        val plan = AdkQueryPlanner(
            proposalPort = UnavailableAdkPlanProposal(),
            deterministicFallback = DeterministicIntelligenceQueryPlanner(),
        ).plan(request)

        assertTrue(plan.plannerId.endsWith("-fallback"))
        assertEquals("adk_no_plan", plan.fallbackReason)
        assertEquals(IntelligenceGoal.LOWEST_STOCK, plan.goal)
    }

    @Test
    fun selectorRecordsWhichPlannerProducedThePlan() = runBlocking {
        val selected = mutableListOf<String>()
        val plan = PlannerSelector(
            deterministic = DeterministicIntelligenceQueryPlanner(),
            adk = null,
            preferAdk = true,
            observation = object : PlannerObservationPort {
                override fun record(plannerId: String) {
                    selected += plannerId
                }
            },
        ).plan(request)

        assertEquals("deterministic", plan.plannerId)
        assertEquals(listOf("deterministic"), selected)
    }

    @Test
    fun knownA2uiActionUsesDeterministicPathWithoutInvokingModelProposal() = runBlocking {
        var proposalCalls = 0
        val plan = AdkQueryPlanner(
            proposalPort = object : AdkPlanProposalPort {
                override suspend fun propose(request: IntelligenceRequest): IntelligencePlan {
                    proposalCalls++
                    error("A known UI action must not invoke the model proposal")
                }
            },
            deterministicFallback = DeterministicIntelligenceQueryPlanner(),
        ).plan(
            request.copy(
                utterance = "Liste os clientes que estão devendo",
                resolvedContext = mapOf("a2ui_action" to "apply_filter"),
            ),
        )

        assertEquals(0, proposalCalls)
        assertEquals(IntelligenceGoal.RECEIVABLES, plan.goal)
        assertEquals("deterministic", plan.plannerId)
    }
}
