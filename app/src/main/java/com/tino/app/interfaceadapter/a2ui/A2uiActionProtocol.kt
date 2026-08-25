package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.agent.TinoAgentSession
import com.tino.app.domain.voice.MutationConfirmation
import com.tino.app.domain.voice.MutationConfirmationPort
import com.tino.app.domain.voice.ToolExecutionResult
import com.tino.app.domain.intelligence.IntelligenceRequest
import com.tino.app.domain.intelligence.agent.AgentInteraction
import com.tino.app.domain.intelligence.agent.AgentRuntimePort
import com.tino.app.domain.intelligence.agent.AgentTurnResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Input boundary from a declarative surface. It contains no Compose callback. */
data class A2uiActionEvent(
    val surfaceId: String,
    val componentId: String,
    val actionName: String,
    val payload: Map<String, Any?> = emptyMap(),
    val sessionId: String,
)

data class ValidatedA2uiAction(
    val event: A2uiActionEvent,
    val descriptor: TinoActionDescriptor,
    val surface: A2uiSurfaceState,
)

sealed interface A2uiActionValidation {
    data class Allowed(val action: ValidatedA2uiAction) : A2uiActionValidation
    data class Denied(val reason: String) : A2uiActionValidation
}

/** Validates the complete declarative boundary before it can reach the agent. */
class A2uiActionValidator(
    private val catalog: TinoEffectiveComponentCatalog = TinoComponentCatalog.core,
) {
    fun validate(
        event: A2uiActionEvent,
        surface: A2uiSurfaceState?,
        expectedSessionId: String? = null,
    ): A2uiActionValidation {
        if (event.sessionId.isBlank()) return A2uiActionValidation.Denied("sessionId ausente.")
        if (expectedSessionId != null && event.sessionId != expectedSessionId) {
            return A2uiActionValidation.Denied("sessionId não corresponde à sessão ativa.")
        }
        if (event.surfaceId.isBlank()) return A2uiActionValidation.Denied("surfaceId ausente.")
        if (event.componentId.isBlank()) return A2uiActionValidation.Denied("componentId ausente.")
        if (event.actionName.isBlank()) return A2uiActionValidation.Denied("actionName ausente.")
        val state = surface ?: return A2uiActionValidation.Denied("Surface inexistente ou expirada.")
        if (state.surfaceId != event.surfaceId) return A2uiActionValidation.Denied("surfaceId não corresponde à surface ativa.")
        val component = state.components.firstOrNull { it.componentId == event.componentId }
            ?: return A2uiActionValidation.Denied("componentId não pertence à surface ativa.")
        val descriptor = catalog.descriptor(component.type)
            ?: return A2uiActionValidation.Denied("Componente fora do catálogo permitido.")
        if (event.actionName !in component.actions) {
            return A2uiActionValidation.Denied("Ação não declarada pelo componente.")
        }
        val action = descriptor.actions.firstOrNull { it.name == event.actionName }
            ?: return A2uiActionValidation.Denied("Ação não permitida para este tipo de componente.")
        val payloadKeys = event.payload.keys
        if (payloadKeys.any { it !in action.allowedPayloadKeys }) {
            return A2uiActionValidation.Denied("Payload contém campos não permitidos.")
        }
        if (action.requiredPayloadKeys.any { key -> event.payload[key] == null || event.payload[key].toString().isBlank() }) {
            return A2uiActionValidation.Denied("Payload obrigatório ausente.")
        }
        return A2uiActionValidation.Allowed(ValidatedA2uiAction(event, action, state))
    }
}

sealed interface A2uiActionDispatchResult {
    data class Rejected(val reason: String) : A2uiActionDispatchResult
    data class Local(val action: ValidatedA2uiAction) : A2uiActionDispatchResult
    data class Agent(val action: ValidatedA2uiAction, val turn: AgentTurnResult) : A2uiActionDispatchResult
    data class Mutation(val action: ValidatedA2uiAction, val result: ToolExecutionResult) : A2uiActionDispatchResult
}

/** Adapter that converts an accepted UI action into the existing agent input port. */
@Singleton
class A2uiActionRuntimeBridge @Inject constructor(
    private val agentRuntime: AgentRuntimePort,
    private val agentSession: TinoAgentSession,
    private val mutationConfirmation: MutationConfirmationPort,
) {
    constructor(
        agentRuntime: AgentRuntimePort,
        agentSession: TinoAgentSession,
    ) : this(agentRuntime, agentSession, RejectingMutationConfirmationPort)

    suspend fun dispatch(action: ValidatedA2uiAction): A2uiActionDispatchResult {
        val event = action.event
        agentSession.rememberSurface(event.surfaceId)
        if (event.actionName == CoreTinoComponentCatalog.CONFIRM_OPERATION.name) {
            return A2uiActionDispatchResult.Mutation(
                action = action,
                result = mutationConfirmation.confirm(event.confirmation()),
            )
        }
        if (event.actionName == CoreTinoComponentCatalog.CANCEL_OPERATION.name && event.hasConfirmationPayload()) {
            mutationConfirmation.cancel(event.confirmation())
            agentSession.cancel()
            return A2uiActionDispatchResult.Mutation(
                action = action,
                result = ToolExecutionResult("Operação cancelada sem mutação.", title = "OPERAÇÃO CANCELADA"),
            )
        }
        val snapshot = agentSession.snapshot.value
        val request = IntelligenceRequest(
            requestId = "a2ui-${UUID.randomUUID()}",
            sessionId = event.sessionId,
            utterance = utteranceFor(event),
            screenContext = snapshot.screenContext.screen,
            resolvedContext = buildMap {
                put("a2ui_surface_id", event.surfaceId)
                put("a2ui_component_id", event.componentId)
                put("a2ui_action", event.actionName)
                event.payload.forEach { (key, value) -> put("a2ui_$key", value?.toString().orEmpty()) }
            },
            availableCapabilities = snapshot.screenContext.availableCapabilities.map { it.name }.toSet(),
        )
        val turn = agentRuntime.run(AgentInteraction(request))
        agentSession.rememberResult(turn.response.answer)
        if (event.actionName == CoreTinoComponentCatalog.CANCEL_OPERATION.name) {
            agentSession.cancel()
        }
        return A2uiActionDispatchResult.Agent(action, turn)
    }

    private fun A2uiActionEvent.hasConfirmationPayload(): Boolean =
        payload["operationId"]?.toString()?.isNotBlank() == true &&
            payload["confirmationToken"]?.toString()?.isNotBlank() == true

    private fun A2uiActionEvent.confirmation(): MutationConfirmation = MutationConfirmation(
        operationId = payload["operationId"]?.toString().orEmpty(),
        confirmationToken = payload["confirmationToken"]?.toString().orEmpty(),
    )

    private fun utteranceFor(event: A2uiActionEvent): String = when (event.actionName) {
        CoreTinoComponentCatalog.APPLY_FILTER.name -> "Liste os clientes que estão devendo, aplicando o filtro selecionado."
        CoreTinoComponentCatalog.SELECT_ENTITY.name -> "Continue a operação com a entidade selecionada."
        CoreTinoComponentCatalog.REQUEST_DETAILS.name -> "Mostre os detalhes da entidade selecionada."
        CoreTinoComponentCatalog.CONTINUE_OPERATION.name -> "Continue a operação pendente."
        CoreTinoComponentCatalog.CANCEL_OPERATION.name -> "Cancele a operação pendente sem fazer mutação."
        CoreTinoComponentCatalog.CONFIRM_OPERATION.name -> "Recebi a intenção de confirmar a operação; aguarde a política de confirmação."
        else -> event.actionName
    }
}

private object RejectingMutationConfirmationPort : MutationConfirmationPort {
    override suspend fun confirm(confirmation: MutationConfirmation): ToolExecutionResult =
        error("MutationConfirmationPort não configurado.")

    override suspend fun cancel(confirmation: MutationConfirmation): Boolean = false
}

@Singleton
class A2uiActionRouter @Inject constructor(
    private val runtimeBridge: A2uiActionRuntimeBridge,
) {
    suspend fun dispatch(
        event: A2uiActionEvent,
        surface: A2uiSurfaceState?,
    ): A2uiActionDispatchResult {
        return when (val validation = A2uiActionValidator().validate(event, surface, TinoAgentSession.DEFAULT_SESSION_ID)) {
            is A2uiActionValidation.Denied -> A2uiActionDispatchResult.Rejected(validation.reason)
            is A2uiActionValidation.Allowed -> when (validation.action.descriptor.kind) {
                TinoActionKind.UI_LOCAL -> A2uiActionDispatchResult.Local(validation.action)
                TinoActionKind.AGENT -> runtimeBridge.dispatch(validation.action)
            }
        }
    }
}
