package com.tino.app.feature.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tino.app.domain.intelligence.IntelligenceResponse
import com.tino.app.domain.intelligence.IntelligenceResponseStatus
import com.tino.app.domain.intelligence.agent.AgentInteraction
import com.tino.app.domain.intelligence.agent.AgentLoopState
import com.tino.app.domain.intelligence.agent.AgentRuntimePort
import com.tino.app.domain.intelligence.agent.AgentTurnResult
import com.tino.app.domain.voice.CommerceToolName
import com.tino.app.domain.voice.MutationConfirmation
import com.tino.app.domain.voice.MutationConfirmationPort
import com.tino.app.domain.voice.MutationOperationStore
import com.tino.app.domain.voice.MutationOperationStatus
import com.tino.app.domain.voice.MutationSafetyPort
import com.tino.app.domain.voice.MutationSafeToolExecutor
import com.tino.app.domain.voice.ToolCall
import com.tino.app.domain.voice.ToolExecutor
import com.tino.app.domain.voice.ToolExecutionResult
import com.tino.app.domain.voice.ToolPreview
import com.tino.app.interfaceadapter.a2ui.A2uiActionEvent
import com.tino.app.interfaceadapter.a2ui.A2uiActionRuntimeBridge
import com.tino.app.interfaceadapter.a2ui.A2uiActionValidation
import com.tino.app.interfaceadapter.a2ui.A2uiActionValidator
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceComponent
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceState
import com.tino.app.interfaceadapter.a2ui.A2uiActionDispatchResult
import com.tino.app.interfaceadapter.a2ui.CoreTinoComponentCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val G311_DEBUG_OPERATION_A = "debug-mutation-001"

data class G311MutationSafetyState(
    val operationId: String = G311_DEBUG_OPERATION_A,
    val status: String = "Nenhuma operação preparada",
    val commitCount: Int = 0,
    val message: String = "Use a prévia para iniciar o smoke físico.",
    val token: String = "",
    val version: Int = 1,
)

@HiltViewModel
class G311MutationSafetyViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val safety: MutationSafetyPort,
    private val store: MutationOperationStore,
) : ViewModel() {
    private val preferences = context.getSharedPreferences("g311_debug_harness", Context.MODE_PRIVATE)
    private val delegate = DebugMutationDelegate()
    private val executor = MutationSafeToolExecutor(delegate, safety)
    private val session = com.tino.app.domain.agent.TinoAgentSession()
    private val bridge = A2uiActionRuntimeBridge(NoopAgentRuntime(), session, DebugMutationConfirmationPort())
    private val _state = MutableStateFlow(G311MutationSafetyState())
    val state: StateFlow<G311MutationSafetyState> = _state.asStateFlow()

    init {
        viewModelScope.launch { restore() }
    }

    fun prepare() = viewModelScope.launch {
        val existing = store.find(OPERATION_A)
        if (existing != null) safety.cancel(existing.prepared.confirmation)
        delegate.version = 1
        val prepared = safety.prepare(callA, delegate.preview(callA), OPERATION_A)
        preferences.edit().putString(KEY_TOKEN_A, prepared.confirmation.confirmationToken).apply()
        publish(prepared.operation.operationId, "PENDING", "Prévia criada. A operação ainda não foi executada.", prepared.confirmation.confirmationToken)
    }

    fun confirmViaA2ui() = viewModelScope.launch {
        val token = currentTokenA() ?: return@launch publish(OPERATION_A, "ERRO", "Token ausente; gere uma nova prévia.")
        val event = A2uiActionEvent(
            surfaceId = SURFACE_ID,
            componentId = COMPONENT_ID,
            actionName = CoreTinoComponentCatalog.CONFIRM_OPERATION.name,
            payload = mapOf("operationId" to OPERATION_A, "confirmationToken" to token),
            sessionId = com.tino.app.domain.agent.TinoAgentSession.DEFAULT_SESSION_ID,
        )
        val surface = confirmationSurface()
        when (val validation = A2uiActionValidator().validate(event, surface, event.sessionId)) {
            is A2uiActionValidation.Denied -> publish(OPERATION_A, "REJEITADO", validation.reason)
            is A2uiActionValidation.Allowed -> {
                when (val result = bridge.dispatch(validation.action)) {
                    is A2uiActionDispatchResult.Mutation -> publish(
                        OPERATION_A,
                        store.find(OPERATION_A)?.status?.name ?: "COMMITTED",
                        result.result.message,
                        token,
                    )
                    else -> publish(OPERATION_A, "ERRO", "O evento não percorreu o caminho de mutação.")
                }
            }
        }
    }

    fun replay() = viewModelScope.launch {
        val token = currentTokenA() ?: return@launch publish(OPERATION_A, "REJEITADO", "Token não disponível após restart.")
        val result = runCatching { executor.confirm(callA, MutationConfirmation(OPERATION_A, token)) }
        val stored = store.find(OPERATION_A)
        publish(
            OPERATION_A,
            stored?.status?.name ?: "COMMITTED",
            result.exceptionOrNull()?.message ?: "Replay inesperadamente aceito.",
            token,
            stored?.status == MutationOperationStatus.COMMITTED,
        )
    }

    fun cancel() = viewModelScope.launch {
        val token = currentTokenA() ?: return@launch publish(OPERATION_A, "CANCELADO", "Nenhuma confirmação pendente.")
        safety.cancel(MutationConfirmation(OPERATION_A, token))
        publish(OPERATION_A, "CANCELADO", "Cancelado sem mutação; a operação deixou de ser executável.", token)
    }

    fun testWrongToken() = viewModelScope.launch {
        val existing = store.find(OPERATION_B)
        if (existing != null) safety.cancel(existing.prepared.confirmation)
        val preparedB = safety.prepare(callB, delegate.preview(callB), OPERATION_B)
        val tokenA = currentTokenA() ?: "token-a-inexistente"
        val result = runCatching { executor.confirm(callB, MutationConfirmation(OPERATION_B, tokenA)) }
        publish(OPERATION_B, store.find(OPERATION_B)?.status?.name ?: "PENDING", result.exceptionOrNull()?.message ?: "ERRO", preparedB.confirmation.confirmationToken)
        safety.cancel(preparedB.confirmation)
    }

    fun testStale() = viewModelScope.launch {
        val existing = store.find(OPERATION_A)
        if (existing != null) safety.cancel(existing.prepared.confirmation)
        delegate.version = 1
        val prepared = safety.prepare(callA, delegate.preview(callA), OPERATION_A)
        preferences.edit().putString(KEY_TOKEN_A, prepared.confirmation.confirmationToken).apply()
        delegate.version = 2
        val result = runCatching { executor.confirm(callA, prepared.confirmation) }
        publish(OPERATION_A, store.find(OPERATION_A)?.status?.name ?: "INVALIDADO", result.exceptionOrNull()?.message ?: "ERRO", prepared.confirmation.confirmationToken)
    }

    private suspend fun restore() {
        val stored = store.find(OPERATION_A)
        if (stored == null) return
        val token = currentTokenA().orEmpty()
        val commitCount = if (stored.status == MutationOperationStatus.COMMITTED) 1 else 0
        publish(stored.prepared.operation.operationId, stored.status.name, "Estado restaurado do Room após restart.", token, commitCount == 1)
    }

    private fun publish(
        operationId: String,
        status: String,
        message: String,
        token: String = currentTokenA().orEmpty(),
        committed: Boolean = false,
    ) {
        _state.value = G311MutationSafetyState(
            operationId = operationId,
            status = status,
            commitCount = if (committed || status == MutationOperationStatus.COMMITTED.name) 1 else 0,
            message = message,
            token = token,
            version = delegate.version,
        )
    }

    private fun currentTokenA(): String? = preferences.getString(KEY_TOKEN_A, null)

    private fun confirmationSurface() = A2uiSurfaceState(
        surfaceId = SURFACE_ID,
        components = listOf(
            A2uiSurfaceComponent(
                componentId = COMPONENT_ID,
                type = CoreTinoComponentCatalog.CONFIRMATION,
                actions = listOf(CoreTinoComponentCatalog.CONFIRM_OPERATION.name),
            ),
        ),
        dataModel = emptyMap(),
    )

    private inner class DebugMutationConfirmationPort : MutationConfirmationPort {
        override suspend fun confirm(confirmation: MutationConfirmation): ToolExecutionResult {
            return executor.confirm(callFor(confirmation.operationId), confirmation)
        }

        override suspend fun cancel(confirmation: MutationConfirmation): Boolean {
            safety.cancel(confirmation)
            return true
        }
    }

    private fun callFor(operationId: String): ToolCall = if (operationId == OPERATION_B) callB else callA

    private class DebugMutationDelegate : ToolExecutor {
        var version: Int = 1
        var commitCount: Int = 0

        override suspend fun preview(call: ToolCall): ToolPreview = ToolPreview(
            title = "Mutation debug?",
            detail = "debug-mutation-${call.arguments["operation"]} · fingerprint v$version",
            confirmLabel = "CONFIRMAR DEBUG",
        )

        override suspend fun execute(call: ToolCall, confirmed: Boolean): ToolExecutionResult {
            commitCount++
            return ToolExecutionResult("Commit protegido realizado uma única vez.", title = "COMMITTED")
        }
    }

    private class NoopAgentRuntime : AgentRuntimePort {
        override suspend fun run(interaction: AgentInteraction): AgentTurnResult = AgentTurnResult(
            response = IntelligenceResponse(IntelligenceResponseStatus.ANSWERED, "Ação encaminhada."),
            finalState = AgentLoopState.FINAL,
            turns = 1,
            trace = emptyList(),
            loopId = "g311-debug",
        )
    }

    private companion object {
        const val OPERATION_A = G311_DEBUG_OPERATION_A
        const val OPERATION_B = "debug-mutation-002"
        const val SURFACE_ID = "g311-confirmation-surface"
        const val COMPONENT_ID = "g311-confirmation"
        const val KEY_TOKEN_A = "token-a"
        val callA = ToolCall(CommerceToolName.CHANGE_PRODUCT_PRICE, mapOf("operation" to "A"))
        val callB = ToolCall(CommerceToolName.CHANGE_PRODUCT_PRICE, mapOf("operation" to "B"))
    }
}
