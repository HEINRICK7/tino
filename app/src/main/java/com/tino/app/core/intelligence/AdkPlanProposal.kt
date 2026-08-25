package com.tino.app.core.intelligence

import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.tino.app.core.speech.GemmaTextInference
import com.tino.app.domain.intelligence.IntelligenceRequest
import com.tino.app.domain.intelligence.planning.AdkPlanProposalPort
import com.tino.app.domain.intelligence.planning.IntelligencePlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import javax.inject.Singleton

/** ADK proposal adapter; validation and execution remain outside this class. */
@Singleton
class GoogleAdkGemmaPlanProposal @Inject constructor(
    private val inference: GemmaTextInference,
) : AdkPlanProposalPort {
    private val sessionService = InMemorySessionService()

    override suspend fun propose(request: IntelligenceRequest): IntelligencePlan? {
        val runner = InMemoryRunner(
            agent = LlmAgent(
                name = AGENT_NAME,
                model = AdkModelAdapter(inference),
                instruction = Instruction(AdkPromptBuilder.instruction()),
                maxSteps = 2,
            ),
            appName = APP_NAME,
            sessionService = sessionService,
        )

        var rawOutput = ""
        try {
            runner.runAsync(
                userId = USER_ID,
                sessionId = "planner-${request.requestId}",
                newMessage = Content(
                    role = Role.USER,
                    parts = listOf(Part(text = AdkPromptBuilder.request(request))),
                ),
            ).collect { event ->
                if (event.author == AGENT_NAME && !event.partial) {
                    rawOutput += event.content?.parts?.mapNotNull { it.text }.orEmpty().joinToString()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        return AdkPlanParser.parse(rawOutput)
    }

    private companion object {
        const val APP_NAME = "TinoAdkPlanner"
        const val USER_ID = "tino-local-user"
        const val AGENT_NAME = "tino_plan_agent"
    }
}
