package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.intelligence.IntelligenceResponse
import com.tino.app.domain.intelligence.IntelligenceResponseStatus
import com.tino.app.domain.intelligence.IntelligenceRequest
import com.tino.app.domain.intelligence.agent.AgentInteraction
import com.tino.app.domain.intelligence.agent.AgentRuntimePort
import com.tino.app.domain.intelligence.agent.AgentTurnResult
import com.tino.app.domain.intelligence.agent.AgentLoopState
import com.tino.app.domain.agent.PendingAgentAction
import com.tino.app.domain.agent.TinoAgentSession
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.voice.MutationConfirmation
import com.tino.app.domain.voice.MutationConfirmationPort
import com.tino.app.domain.voice.ToolExecutionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class A2uiActionProtocolTest {
    private val surface = A2uiSurfaceState(
        surfaceId = "receivables",
        components = listOf(
            A2uiSurfaceComponent(
                componentId = "filter",
                type = CoreTinoComponentCatalog.CHOICE,
                actions = listOf(CoreTinoComponentCatalog.APPLY_FILTER.name),
                actionPayloads = mapOf(
                    CoreTinoComponentCatalog.APPLY_FILTER.name to mapOf("filter" to "atrasados"),
                ),
            ),
        ),
        dataModel = mapOf("filter" to "todos"),
    )

    private fun event(
        actionName: String = CoreTinoComponentCatalog.APPLY_FILTER.name,
        payload: Map<String, Any?> = mapOf("filter" to "atrasados"),
    ) = A2uiActionEvent(
        surfaceId = "receivables",
        componentId = "filter",
        actionName = actionName,
        payload = payload,
        sessionId = "default",
    )

    @Test
    fun applyFilterIsValidatedAndReturnedToAgentRuntime() = runBlocking {
        val runtime = RecordingAgentRuntime()
        val session = com.tino.app.domain.agent.TinoAgentSession()
        val result = A2uiActionRouter(
            A2uiActionRuntimeBridge(runtime, session),
        ).dispatch(event(), surface)

        assertTrue(result is A2uiActionDispatchResult.Agent)
        assertEquals(1, runtime.calls)
        assertEquals("apply_filter", runtime.lastRequest?.resolvedContext?.get("a2ui_action"))
        assertEquals("atrasados", runtime.lastRequest?.resolvedContext?.get("a2ui_filter"))
        assertEquals("receivables", session.snapshot.value.sessionMemory.activeSurfaceId)
    }

    @Test
    fun unknownActionIsDeniedBeforeAgentRuntime() = runBlocking {
        val runtime = RecordingAgentRuntime()
        val result = A2uiActionRouter(
            A2uiActionRuntimeBridge(runtime, com.tino.app.domain.agent.TinoAgentSession()),
        ).dispatch(event(actionName = "execute_sql", payload = emptyMap()), surface)

        assertTrue(result is A2uiActionDispatchResult.Rejected)
        assertEquals(0, runtime.calls)
    }

    @Test
    fun wrongComponentSurfaceAndPayloadAreDenied() {
        val validator = A2uiActionValidator()
        assertTrue(validator.validate(event().copy(componentId = "missing"), surface) is A2uiActionValidation.Denied)
        assertTrue(validator.validate(event().copy(surfaceId = "other"), surface) is A2uiActionValidation.Denied)
        assertTrue(validator.validate(event(payload = emptyMap()), surface) is A2uiActionValidation.Denied)
        assertTrue(validator.validate(event(), surface, expectedSessionId = "another") is A2uiActionValidation.Denied)
    }

    @Test
    fun localActionDoesNotEnterAgentRuntime() = runBlocking {
        val runtime = RecordingAgentRuntime()
        val localSurface = surface.copy(
            components = listOf(
                surface.components.single().copy(
                    type = CoreTinoComponentCatalog.BUTTON,
                    actions = listOf(CoreTinoComponentCatalog.DISMISS.name),
                ),
            ),
        )
        val result = A2uiActionRouter(
            A2uiActionRuntimeBridge(runtime, com.tino.app.domain.agent.TinoAgentSession()),
        ).dispatch(event(CoreTinoComponentCatalog.DISMISS.name, emptyMap()), localSurface)

        assertTrue(result is A2uiActionDispatchResult.Local)
        assertEquals(0, runtime.calls)
    }

    @Test
    fun cancelReturnsThroughRuntimeAndClearsPendingInteractionWithoutMutation() = runBlocking {
        val runtime = RecordingAgentRuntime()
        val session = TinoAgentSession()
        session.updateDraft(
            PendingAgentAction(
                capability = TinoCapabilityId.ADD_CREDIT,
                summary = "Operação pendente",
                requiresConfirmation = true,
            ),
        )
        val cancelSurface = surface.copy(
            components = listOf(
                A2uiSurfaceComponent(
                    componentId = "preview",
                    type = CoreTinoComponentCatalog.OPERATION_PREVIEW,
                    actions = listOf(CoreTinoComponentCatalog.CANCEL_OPERATION.name),
                ),
            ),
        )
        val result = A2uiActionRouter(
            A2uiActionRuntimeBridge(runtime, session),
        ).dispatch(
            event(
                actionName = CoreTinoComponentCatalog.CANCEL_OPERATION.name,
                payload = emptyMap(),
            ).copy(componentId = "preview"),
            cancelSurface,
        )

        assertTrue(result is A2uiActionDispatchResult.Agent)
        assertEquals(1, runtime.calls)
        assertEquals(null, session.snapshot.value.pendingAction)
        assertEquals("cancelled", session.snapshot.value.lastAgentResult)
    }

    @Test
    fun confirmOperationCrossesA2uiValidatorIntoMutationConfirmationPort() = runBlocking {
        val runtime = RecordingAgentRuntime()
        val mutationPort = RecordingMutationConfirmationPort()
        val confirmationSurface = surface.copy(
            components = listOf(
                A2uiSurfaceComponent(
                    componentId = "confirmation",
                    type = CoreTinoComponentCatalog.CONFIRMATION,
                    actions = listOf(CoreTinoComponentCatalog.CONFIRM_OPERATION.name),
                ),
            ),
        )
        val result = A2uiActionRouter(
            A2uiActionRuntimeBridge(runtime, TinoAgentSession(), mutationPort),
        ).dispatch(
            event(
                actionName = CoreTinoComponentCatalog.CONFIRM_OPERATION.name,
                payload = mapOf("operationId" to "debug-mutation-001", "confirmationToken" to "token-001"),
            ).copy(componentId = "confirmation"),
            confirmationSurface,
        )

        assertTrue(result is A2uiActionDispatchResult.Mutation)
        assertEquals(0, runtime.calls)
        assertEquals(
            MutationConfirmation("debug-mutation-001", "token-001"),
            mutationPort.lastConfirmation,
        )
    }

    @Test
    fun confirmOperationWithoutTokenIsRejectedBeforeMutationPort() {
        val surface = surface.copy(
            components = listOf(
                A2uiSurfaceComponent(
                    componentId = "confirmation",
                    type = CoreTinoComponentCatalog.CONFIRMATION,
                    actions = listOf(CoreTinoComponentCatalog.CONFIRM_OPERATION.name),
                ),
            ),
        )
        val result = A2uiActionValidator().validate(
            event(
                actionName = CoreTinoComponentCatalog.CONFIRM_OPERATION.name,
                payload = mapOf("operationId" to "debug-mutation-001"),
            ).copy(componentId = "confirmation"),
            surface,
        )

        assertTrue(result is A2uiActionValidation.Denied)
    }

    @Test
    fun actionPayloadSurvivesSurfaceCodecRoundTrip() {
        val message = A2uiSurfaceMessage(
            messageId = "action-message",
            surfaceId = "receivables",
            operation = A2uiSurfaceOperation.CREATE_SURFACE,
            components = listOf(
                surface.components.single(),
            ),
        )
        val decoded = TinoA2UiSurfaceJsonCodec.decode(TinoA2UiSurfaceJsonCodec.encode(message))
        assertEquals(message, decoded)
    }

    private class RecordingAgentRuntime : AgentRuntimePort {
        var calls: Int = 0
        var lastRequest: IntelligenceRequest? = null

        override suspend fun run(interaction: AgentInteraction): AgentTurnResult {
            calls++
            lastRequest = interaction.request
            return AgentTurnResult(
                response = IntelligenceResponse(
                    status = IntelligenceResponseStatus.ANSWERED,
                    answer = "Filtro aplicado.",
                ),
                finalState = AgentLoopState.FINAL,
                turns = 1,
                trace = emptyList(),
                loopId = "loop-action-test",
            )
        }
    }

    private class RecordingMutationConfirmationPort : MutationConfirmationPort {
        var lastConfirmation: MutationConfirmation? = null

        override suspend fun confirm(confirmation: MutationConfirmation): ToolExecutionResult {
            lastConfirmation = confirmation
            return ToolExecutionResult("confirmado")
        }

        override suspend fun cancel(confirmation: MutationConfirmation): Boolean {
            lastConfirmation = confirmation
            return true
        }
    }
}
