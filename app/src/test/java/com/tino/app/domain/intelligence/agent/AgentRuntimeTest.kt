package com.tino.app.domain.intelligence.agent

import com.tino.app.domain.intelligence.DeterministicIntelligencePlanValidator
import com.tino.app.domain.intelligence.IntelligencePlanExecutor
import com.tino.app.domain.intelligence.IntelligencePlanStep
import com.tino.app.domain.intelligence.IntelligenceRequest
import com.tino.app.domain.intelligence.IntelligenceResponse
import com.tino.app.domain.intelligence.IntelligenceResponseStatus
import com.tino.app.domain.intelligence.IntelligenceTelemetryEvent
import com.tino.app.domain.intelligence.IntelligenceTelemetryPort
import com.tino.app.domain.intelligence.planning.IntelligenceGoal
import com.tino.app.domain.intelligence.planning.IntelligencePlan
import com.tino.app.domain.intelligence.planning.PlannerPort
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun standardPlan(tool: String = "get_financial_summary") = IntelligencePlan(
    goal = IntelligenceGoal.PERIOD_COMPARISON,
    steps = listOf(IntelligencePlanStep(tool, "ler dados")),
)

class AgentRuntimeTest {
    private val request = IntelligenceRequest(
        requestId = "request-agent-1",
        sessionId = "session-agent-1",
        utterance = "qual produto precisa de atenção?",
    )

    @Test
    fun observesToolFailureReplansAndFinishes() = runBlocking {
        val planner = ReplanningPlanner()
        val executor = SequencedExecutor()
        val telemetry = RecordingTelemetry()
        val result = DefaultAgentRuntime(
            planner = planner,
            validator = DeterministicIntelligencePlanValidator(),
            executor = executor,
            telemetry = telemetry,
        ).run(AgentInteraction(request, maxTurns = 3))

        assertEquals(2, result.turns)
        assertEquals(2, planner.calls)
        assertEquals(2, executor.calls)
        assertEquals(IntelligenceResponseStatus.ANSWERED, result.response.status)
        assertTrue(result.trace.any { it.state == AgentLoopState.OBSERVE })
        assertTrue(result.trace.any { it.state == AgentLoopState.REPLAN })
        assertEquals(2, telemetry.events.size)
        assertEquals(result.loopId, telemetry.events[0].loopId)
        assertEquals("REPLAN", telemetry.events[0].decision)
        assertEquals("FINAL", telemetry.events[1].decision)
    }

    @Test
    fun hardTurnLimitStopsRepeatedReplanning() = runBlocking {
        val result = DefaultAgentRuntime(
            planner = StaticPlanner(),
            validator = DeterministicIntelligencePlanValidator(),
            executor = AlwaysUnavailableExecutor(),
        ).run(AgentInteraction(request, maxTurns = 2))

        assertEquals(2, result.turns)
        assertEquals(AgentLoopState.FINAL, result.finalState)
        assertEquals(IntelligenceResponseStatus.ERROR, result.response.status)
        assertTrue(result.response.limitations.contains("limite_de_turns_excedido"))
    }

    @Test
    fun clarificationStopsLoopWithoutReplanning() = runBlocking {
        val result = DefaultAgentRuntime(
            planner = StaticPlanner(),
            validator = DeterministicIntelligencePlanValidator(),
            executor = ClarificationExecutor(),
        ).run(AgentInteraction(request, maxTurns = 3))

        assertEquals(1, result.turns)
        assertEquals(AgentLoopState.CLARIFY, result.finalState)
        assertTrue(result.trace.any { it.state == AgentLoopState.CLARIFY })
    }

    @Test
    fun invalidPlanIsRejectedBeforeExecutor() = runBlocking {
        val executor = CountingExecutor()
        val result = DefaultAgentRuntime(
            planner = object : PlannerPort {
                override val id = "deterministic"
                override suspend fun plan(request: IntelligenceRequest) = IntelligencePlan(
                    goal = IntelligenceGoal.INVENTORY,
                    steps = listOf(IntelligencePlanStep("delete_everything", "não permitido")),
                )
            },
            validator = DeterministicIntelligencePlanValidator(),
            executor = executor,
        ).run(AgentInteraction(request))

        assertEquals(IntelligenceResponseStatus.TOOL_UNAVAILABLE, result.response.status)
        assertEquals(0, executor.calls)
        assertEquals(AgentLoopState.FINAL, result.finalState)
    }

    @Test
    fun telemetryFailureDoesNotBreakTheLoop() = runBlocking {
        val result = DefaultAgentRuntime(
            planner = StaticPlanner(),
            validator = DeterministicIntelligencePlanValidator(),
            executor = SequencedExecutor(),
            telemetry = object : IntelligenceTelemetryPort {
                override suspend fun record(event: IntelligenceTelemetryEvent) = error("telemetry unavailable")
                override suspend fun recent(limit: Int) = emptyList<IntelligenceTelemetryEvent>()
            },
        ).run(AgentInteraction(request, maxTurns = 2))

        assertFalse(result.response.answer.isBlank())
    }

    @Test
    fun timeoutBecomesTerminalTimeout() = runBlocking {
        val result = DefaultAgentRuntime(
            planner = StaticPlanner(),
            validator = DeterministicIntelligencePlanValidator(),
            executor = SlowExecutor(),
        ).run(AgentInteraction(request, timeoutMs = 20L))

        assertEquals(AgentTerminalState.TIMEOUT, result.terminalState)
        assertTrue(result.response.limitations.contains("tempo_global_excedido"))
    }

    @Test
    fun toolBudgetStopsBeforeExecutor() = runBlocking {
        val executor = CountingExecutor()
        val result = DefaultAgentRuntime(
            planner = TwoStepPlanner(),
            validator = DeterministicIntelligencePlanValidator(),
            executor = executor,
        ).run(AgentInteraction(request, limits = AgentLoopLimits(maxToolCalls = 1)))

        assertEquals(AgentTerminalState.TOOL_FAILURE, result.terminalState)
        assertEquals(0, executor.calls)
    }

    @Test
    fun repeatedPlanIsStoppedAsLoopProtection() = runBlocking {
        val result = DefaultAgentRuntime(
            planner = StaticPlanner(),
            validator = DeterministicIntelligencePlanValidator(),
            executor = AlwaysUnavailableExecutor(),
        ).run(AgentInteraction(request, maxTurns = 3, limits = AgentLoopLimits(maxReplans = 3)))

        assertEquals(AgentTerminalState.TOOL_FAILURE, result.terminalState)
        assertTrue(result.response.limitations.contains("chamada_de_tool_duplicada_detectada"))
    }

    @Test
    fun replanBudgetStopsAgentAfterAllowedReplans() = runBlocking {
        val result = DefaultAgentRuntime(
            planner = ReplanningPlanner(),
            validator = DeterministicIntelligencePlanValidator(),
            executor = AlwaysUnavailableExecutor(),
        ).run(AgentInteraction(request, maxTurns = 3, limits = AgentLoopLimits(maxReplans = 1, detectLoops = false)))

        assertEquals(AgentTerminalState.TOOL_FAILURE, result.terminalState)
        assertTrue(result.response.limitations.contains("limite_de_replans_excedido"))
    }

    private class StaticPlanner : PlannerPort {
        override val id: String = "deterministic"
        override suspend fun plan(request: IntelligenceRequest) = standardPlan()
    }

    private class TwoStepPlanner : PlannerPort {
        override val id: String = "deterministic"
        override suspend fun plan(request: IntelligenceRequest) = standardPlan().copy(
            steps = listOf(
                IntelligencePlanStep("get_financial_summary", "ler dados"),
                IntelligencePlanStep("compare_financial_periods", "comparar dados"),
            ),
        )
    }

    private class ReplanningPlanner : PlannerPort {
        override val id: String = "deterministic"
        var calls = 0

        override suspend fun plan(request: IntelligenceRequest): IntelligencePlan {
            calls++
            return if (request.resolvedContext["agent_observation"] == null) {
                standardPlan("get_financial_summary").copy(plannerId = id)
            } else {
                standardPlan("get_financial_summary").copy(plannerId = id)
            }
        }
    }

    private class SequencedExecutor : IntelligencePlanExecutor {
        var calls = 0

        override suspend fun execute(request: IntelligenceRequest, plan: IntelligencePlan): IntelligenceResponse {
            calls++
            return if (calls == 1) {
                IntelligenceResponse(
                    status = IntelligenceResponseStatus.TOOL_UNAVAILABLE,
                    answer = "A ferramenta temporariamente indisponível.",
                )
            } else {
                IntelligenceResponse(
                    status = IntelligenceResponseStatus.ANSWERED,
                    answer = "Resultado observado e replanejado.",
                    factsUsed = listOf("financial_projection"),
                    confidence = 0.9,
                )
            }
        }
    }

    private class AlwaysUnavailableExecutor : IntelligencePlanExecutor {
        override suspend fun execute(request: IntelligenceRequest, plan: IntelligencePlan) =
            IntelligenceResponse(
                status = IntelligenceResponseStatus.TOOL_UNAVAILABLE,
                answer = "indisponível",
            )
    }

    private class ClarificationExecutor : IntelligencePlanExecutor {
        override suspend fun execute(request: IntelligenceRequest, plan: IntelligencePlan) =
            IntelligenceResponse(
                status = IntelligenceResponseStatus.NEEDS_CLARIFICATION,
                answer = "Qual produto você quis dizer?",
            )
    }

    private class CountingExecutor : IntelligencePlanExecutor {
        var calls = 0
        override suspend fun execute(request: IntelligenceRequest, plan: IntelligencePlan): IntelligenceResponse {
            calls++
            return IntelligenceResponse(IntelligenceResponseStatus.ANSWERED, "não deveria executar")
        }
    }

    private class SlowExecutor : IntelligencePlanExecutor {
        override suspend fun execute(request: IntelligenceRequest, plan: IntelligencePlan): IntelligenceResponse {
            delay(100L)
            return IntelligenceResponse(IntelligenceResponseStatus.ANSWERED, "tarde")
        }
    }

    private class RecordingTelemetry : IntelligenceTelemetryPort {
        val events = mutableListOf<IntelligenceTelemetryEvent>()
        override suspend fun record(event: IntelligenceTelemetryEvent) {
            events += event
        }
        override suspend fun recent(limit: Int): List<IntelligenceTelemetryEvent> = events.takeLast(limit)
    }
}
