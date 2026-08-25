package com.tino.app.domain.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FullRuntimeRequest(
    val runId: String,
    val executionId: String,
    val capability: TinoCapabilityId,
    val timeoutMs: Long = 8_000L,
    val humanGate: HumanGateResult? = null,
) {
    init {
        require(runId.isNotBlank()) { "runId ausente." }
        require(executionId.isNotBlank()) { "executionId ausente." }
        require(timeoutMs > 0L) { "timeoutMs deve ser positivo." }
    }
}

sealed interface FullRuntimeResult<out T> {
    data class Completed<T>(val value: T) : FullRuntimeResult<T>
    data class WaitingForUser(val request: HumanGateRequest) : FullRuntimeResult<Nothing>
    data class Failed(val reason: String, val timedOut: Boolean = false) : FullRuntimeResult<Nothing>
    data object Cancelled : FullRuntimeResult<Nothing>
}

/** Small end-to-end seam joining state, progress, stream and capability execution. */
class FullAgentRuntimeIntegration(
    private val sharedState: TinoAgentSession,
    private val progress: AgentProgressRuntime,
    private val streaming: AgentStreamingRuntime,
) {
    private val executionMutex = Mutex()

    suspend fun <T> execute(
        request: FullRuntimeRequest,
        operation: suspend () -> T,
    ): FullRuntimeResult<T> = executionMutex.withLock {
        progress.start(request.runId, request.executionId)
        streaming.emit(request.runId, AgentStreamEventType.AGENT_STARTED)
        sharedState.beginResolving()
        progress.capabilityStarted(request.capability)
        streaming.emit(
            request.runId,
            AgentStreamEventType.STATE_CHANGED,
            mapOf("state" to AgentVoiceState.RESOLVING.name),
        )

        val gate = request.humanGate
        if (gate is HumanGateResult.ConfirmationRequired) {
            sharedState.needsClarification()
            progress.waitingForUser(gate.request.summary)
            streaming.emit(
                request.runId,
                AgentStreamEventType.STATE_CHANGED,
                mapOf("state" to AgentVoiceState.NEEDS_CLARIFICATION.name),
            )
            streaming.emit(request.runId, AgentStreamEventType.A2UI_UPDATED)
            return FullRuntimeResult.WaitingForUser(gate.request)
        }
        if (gate is HumanGateResult.Denied) {
            sharedState.markFailed()
            progress.fail(gate.reason)
            streaming.emit(request.runId, AgentStreamEventType.A2UI_UPDATED)
            streaming.close(request.runId, AgentStreamEventType.FAILED, mapOf("reason" to gate.reason))
            return FullRuntimeResult.Failed(gate.reason)
        }

        return try {
            progress.toolStarted(request.capability.name)
            streaming.emit(request.runId, AgentStreamEventType.TOOL_STARTED, mapOf("tool" to request.capability.name))
            val value = withTimeout(request.timeoutMs) { operation() }
            progress.toolCompleted(request.capability.name, succeeded = true)
            progress.complete()
            sharedState.markSuccess()
            streaming.emit(request.runId, AgentStreamEventType.TOOL_COMPLETED, mapOf("tool" to request.capability.name))
            streaming.emit(request.runId, AgentStreamEventType.A2UI_UPDATED)
            streaming.close(request.runId, AgentStreamEventType.COMPLETED)
            FullRuntimeResult.Completed(value)
        } catch (_: TimeoutCancellationException) {
            sharedState.markFailed()
            progress.toolCompleted(request.capability.name, succeeded = false)
            progress.fail("timeout")
            streaming.emit(request.runId, AgentStreamEventType.TOOL_COMPLETED, mapOf("tool" to request.capability.name, "succeeded" to "false"))
            streaming.emit(request.runId, AgentStreamEventType.A2UI_UPDATED, mapOf("state" to "ERROR"))
            streaming.close(request.runId, AgentStreamEventType.FAILED, mapOf("reason" to "timeout"))
            FullRuntimeResult.Failed("A operação demorou mais que o esperado.", timedOut = true)
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                sharedState.cancel()
                progress.toolCompleted(request.capability.name, succeeded = false)
                progress.cancel()
                streaming.emit(request.runId, AgentStreamEventType.TOOL_COMPLETED, mapOf("tool" to request.capability.name, "succeeded" to "false"))
                streaming.emit(request.runId, AgentStreamEventType.A2UI_UPDATED, mapOf("state" to "IDLE"))
                streaming.close(request.runId, AgentStreamEventType.CANCELLED)
            }
            FullRuntimeResult.Cancelled
        } catch (error: Throwable) {
            sharedState.markFailed()
            progress.toolCompleted(request.capability.name, succeeded = false)
            progress.fail(error.message ?: "Falha na operação.")
            streaming.emit(request.runId, AgentStreamEventType.TOOL_COMPLETED, mapOf("tool" to request.capability.name, "succeeded" to "false"))
            streaming.emit(request.runId, AgentStreamEventType.A2UI_UPDATED, mapOf("state" to "ERROR"))
            streaming.close(
                request.runId,
                AgentStreamEventType.FAILED,
                mapOf("reason" to (error.message ?: "unknown")),
            )
            FullRuntimeResult.Failed(error.message ?: "Falha na operação.")
        }
    }
}
