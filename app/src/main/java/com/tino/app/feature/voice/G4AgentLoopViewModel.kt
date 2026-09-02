package com.tino.app.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tino.app.domain.intelligence.DeterministicIntelligencePlanValidator
import com.tino.app.domain.intelligence.IntelligencePlanExecutor
import com.tino.app.domain.intelligence.IntelligenceRequest
import com.tino.app.domain.intelligence.IntelligenceResponse
import com.tino.app.domain.intelligence.IntelligenceResponseStatus
import com.tino.app.domain.intelligence.agent.AgentInteraction
import com.tino.app.domain.intelligence.agent.AgentLoopLimits
import com.tino.app.domain.intelligence.agent.AgentRuntimePort
import com.tino.app.domain.intelligence.agent.AgentTerminalState
import com.tino.app.domain.intelligence.agent.DefaultAgentRuntime
import com.tino.app.domain.intelligence.planning.IntelligenceGoal
import com.tino.app.domain.intelligence.planning.IntelligencePlan
import com.tino.app.domain.intelligence.planning.IntelligencePlanStep
import com.tino.app.domain.intelligence.planning.PlannerPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class G4AgentLoopState(
    val scenario: String = "",
    val status: String = "PRONTO",
    val terminalState: String = "—",
    val turns: Int = 0,
    val toolCalls: Int = 0,
    val replans: Int = 0,
    val trace: List<String> = emptyList(),
    val message: String = "Escolha um cenário para provar o ciclo do Agent Runtime.",
)

/**
 * Physical G4 harness. It uses the production loop and contracts, but fake
 * read-only planner/executor data so no commercial record can be changed.
 */
@HiltViewModel
class G4AgentLoopViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(G4AgentLoopState())
    val state: StateFlow<G4AgentLoopState> = _state

    fun runObserveReplan() = runScenario(G4Scenario.OBSERVE_REPLAN)

    fun runClarification() = runScenario(G4Scenario.CLARIFICATION)

    fun runLoopProtection() = runScenario(G4Scenario.LOOP_PROTECTION)

    private fun runScenario(scenario: G4Scenario) {
        _state.value = G4AgentLoopState(
            scenario = scenario.label,
            status = "EXECUTANDO",
            message = "Observe → Plan → Execute → Observe → Replan",
        )
        viewModelScope.launch {
            val planner = HarnessPlanner(scenario)
            val executor = HarnessExecutor(scenario)
            val runtime: AgentRuntimePort = DefaultAgentRuntime(
                planner = planner,
                validator = DeterministicIntelligencePlanValidator(),
                executor = executor,
            )
            val result = runtime.run(
                AgentInteraction(
                    request = IntelligenceRequest(
                        requestId = "g4-${scenario.name.lowercase()}",
                        sessionId = "g4-device-session",
                        utterance = scenario.utterance,
                    ),
                    maxTurns = 4,
                    timeoutMs = 4_000L,
                    limits = AgentLoopLimits(maxToolCalls = 8, maxReplans = 2),
                ),
            )
            val replanCount = result.trace.count { it.state.name == "REPLAN" }
            _state.value = G4AgentLoopState(
                scenario = scenario.label,
                status = if (result.terminalState == AgentTerminalState.TOOL_FAILURE && scenario == G4Scenario.LOOP_PROTECTION) {
                    "PROTEGIDO"
                } else {
                    result.terminalState.name
                },
                terminalState = result.terminalState.name,
                turns = result.turns,
                toolCalls = result.trace.count { it.state.name == "EXECUTE_READ" },
                replans = replanCount,
                trace = result.trace.map { "T${it.turn} ${it.state.name}" },
                message = result.response.answer,
            )
        }
    }

    private enum class G4Scenario(val label: String, val utterance: String) {
        OBSERVE_REPLAN("Multi-tool + replan", "Quem está me devendo e pagou recentemente?"),
        CLARIFICATION("Clarificação", "Bota dois Maratá para Maria."),
        LOOP_PROTECTION("Proteção de loop", "Repita uma leitura indisponível."),
    }

    private class HarnessPlanner(private val scenario: G4Scenario) : PlannerPort {
        override val id: String = "deterministic-harness"
        private var calls = 0

        override suspend fun plan(request: IntelligenceRequest): IntelligencePlan {
            calls++
            return when (scenario) {
                G4Scenario.OBSERVE_REPLAN -> if (calls == 1) {
                    plan("get_receivables")
                } else {
                    IntelligencePlan(
                        goal = IntelligenceGoal.RECENT_PAYMENTS,
                        steps = listOf(
                            IntelligencePlanStep("get_customer_payment_history", "observar histórico"),
                            IntelligencePlanStep("get_customer_balance", "observar saldo atual"),
                        ),
                        plannerId = id,
                    )
                }
                G4Scenario.CLARIFICATION -> plan("search_product")
                G4Scenario.LOOP_PROTECTION -> plan("get_receivables")
            }
        }

        private fun plan(tool: String) = IntelligencePlan(
            goal = IntelligenceGoal.RECEIVABLES,
            steps = listOf(IntelligencePlanStep(tool, "harness read-only")),
            plannerId = id,
        )
    }

    private class HarnessExecutor(private val scenario: G4Scenario) : IntelligencePlanExecutor {
        private var calls = 0

        override suspend fun execute(request: IntelligenceRequest, plan: IntelligencePlan): IntelligenceResponse {
            calls++
            return when (scenario) {
                G4Scenario.OBSERVE_REPLAN -> if (calls == 1) {
                    IntelligenceResponse(
                        status = IntelligenceResponseStatus.TOOL_UNAVAILABLE,
                        answer = "A primeira observação pediu replanejamento.",
                    )
                } else {
                    IntelligenceResponse(
                        status = IntelligenceResponseStatus.ANSWERED,
                        answer = "Observação concluída: cliente, histórico e saldo foram combinados.",
                        factsUsed = listOf("receivables", "payment_history", "customer_balance"),
                    )
                }
                G4Scenario.CLARIFICATION -> IntelligenceResponse(
                    status = IntelligenceResponseStatus.NEEDS_CLARIFICATION,
                    answer = "Encontrei dois produtos Maratá. Escolha um para continuar.",
                )
                G4Scenario.LOOP_PROTECTION -> IntelligenceResponse(
                    status = IntelligenceResponseStatus.TOOL_UNAVAILABLE,
                    answer = "A leitura não está disponível.",
                )
            }
        }
    }
}
