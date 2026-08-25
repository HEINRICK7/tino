package com.tino.app.domain.agent

import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.core.observability.NoOpAuditLogger
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Typed progress lifecycle emitted by the agent runtime, not diagnostic logging. */
sealed interface AgentProgressEvent {
    val runId: String
    val executionId: String
    val sequence: Long
    val occurredAtEpochMs: Long

    data class RunStarted(
        override val runId: String,
        override val executionId: String,
        override val sequence: Long,
        override val occurredAtEpochMs: Long,
    ) : AgentProgressEvent

    data class CapabilityStarted(
        override val runId: String,
        override val executionId: String,
        override val sequence: Long,
        override val occurredAtEpochMs: Long,
        val capability: TinoCapabilityId,
    ) : AgentProgressEvent

    data class ToolStarted(
        override val runId: String,
        override val executionId: String,
        override val sequence: Long,
        override val occurredAtEpochMs: Long,
        val toolName: String,
    ) : AgentProgressEvent

    data class ToolProgress(
        override val runId: String,
        override val executionId: String,
        override val sequence: Long,
        override val occurredAtEpochMs: Long,
        val toolName: String,
        val message: String,
        val fraction: Float? = null,
    ) : AgentProgressEvent

    data class ToolCompleted(
        override val runId: String,
        override val executionId: String,
        override val sequence: Long,
        override val occurredAtEpochMs: Long,
        val toolName: String,
        val succeeded: Boolean,
    ) : AgentProgressEvent

    data class WaitingForUser(
        override val runId: String,
        override val executionId: String,
        override val sequence: Long,
        override val occurredAtEpochMs: Long,
        val reason: String,
    ) : AgentProgressEvent

    data class RunCompleted(
        override val runId: String,
        override val executionId: String,
        override val sequence: Long,
        override val occurredAtEpochMs: Long,
    ) : AgentProgressEvent

    data class RunFailed(
        override val runId: String,
        override val executionId: String,
        override val sequence: Long,
        override val occurredAtEpochMs: Long,
        val reason: String,
    ) : AgentProgressEvent

    data class RunCancelled(
        override val runId: String,
        override val executionId: String,
        override val sequence: Long,
        override val occurredAtEpochMs: Long,
        val reason: String = "cancelled",
    ) : AgentProgressEvent
}

enum class AgentProgressTerminalState { ACTIVE, WAITING_FOR_USER, COMPLETED, FAILED, CANCELLED }

data class AgentProgressSnapshot(
    val runId: String? = null,
    val executionId: String? = null,
    val sequence: Long = 0L,
    val terminalState: AgentProgressTerminalState = AgentProgressTerminalState.ACTIVE,
    val lastEvent: AgentProgressEvent? = null,
)

/**
 * In-process progress bus with balanced terminal transitions. Consumers can
 * collect events in realtime while the snapshot gives lifecycle recovery.
 */
class AgentProgressRuntime(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val auditLogger: AuditLogger = NoOpAuditLogger,
) {
    private val _events = MutableSharedFlow<AgentProgressEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentProgressEvent> = _events.asSharedFlow()

    private val _snapshot = MutableStateFlow(AgentProgressSnapshot())
    val snapshot: StateFlow<AgentProgressSnapshot> = _snapshot.asStateFlow()

    @Synchronized
    fun start(runId: String = UUID.randomUUID().toString(), executionId: String = UUID.randomUUID().toString()): AgentProgressEvent.RunStarted {
        val current = _snapshot.value
        check(current.runId == null || current.terminalState != AgentProgressTerminalState.ACTIVE) {
            "Já existe uma execução ativa."
        }
        if (current.runId != null) {
            _snapshot.value = AgentProgressSnapshot()
        }
        return emit(AgentProgressEvent.RunStarted(runId, executionId, 1L, clock())) as AgentProgressEvent.RunStarted
    }

    fun capabilityStarted(capability: TinoCapabilityId): AgentProgressEvent.CapabilityStarted =
        emitActive { sequence, runId, executionId ->
            AgentProgressEvent.CapabilityStarted(runId, executionId, sequence, clock(), capability)
        } as AgentProgressEvent.CapabilityStarted

    fun toolStarted(toolName: String): AgentProgressEvent.ToolStarted =
        emitActive { sequence, runId, executionId ->
            AgentProgressEvent.ToolStarted(runId, executionId, sequence, clock(), toolName)
        } as AgentProgressEvent.ToolStarted

    fun toolProgress(toolName: String, message: String, fraction: Float? = null): AgentProgressEvent.ToolProgress =
        emitActive { sequence, runId, executionId ->
            AgentProgressEvent.ToolProgress(runId, executionId, sequence, clock(), toolName, message, fraction)
        } as AgentProgressEvent.ToolProgress

    fun toolCompleted(toolName: String, succeeded: Boolean): AgentProgressEvent.ToolCompleted =
        emitActive { sequence, runId, executionId ->
            AgentProgressEvent.ToolCompleted(runId, executionId, sequence, clock(), toolName, succeeded)
        } as AgentProgressEvent.ToolCompleted

    fun waitingForUser(reason: String): AgentProgressEvent.WaitingForUser =
        emitActive { sequence, runId, executionId ->
            AgentProgressEvent.WaitingForUser(runId, executionId, sequence, clock(), reason)
        } as AgentProgressEvent.WaitingForUser

    @Synchronized
    fun complete(): AgentProgressEvent.RunCompleted = terminal { sequence, runId, executionId ->
        AgentProgressEvent.RunCompleted(runId, executionId, sequence, clock())
    } as AgentProgressEvent.RunCompleted

    @Synchronized
    fun fail(reason: String): AgentProgressEvent.RunFailed = terminal { sequence, runId, executionId ->
        AgentProgressEvent.RunFailed(runId, executionId, sequence, clock(), reason)
    } as AgentProgressEvent.RunFailed

    @Synchronized
    fun cancel(reason: String = "cancelled"): AgentProgressEvent.RunCancelled = terminal { sequence, runId, executionId ->
        AgentProgressEvent.RunCancelled(runId, executionId, sequence, clock(), reason)
    } as AgentProgressEvent.RunCancelled

    @Synchronized
    private fun emit(event: AgentProgressEvent): AgentProgressEvent {
        check(_snapshot.value.runId == null || _snapshot.value.runId == event.runId) { "runId diferente da execução ativa." }
        check(_snapshot.value.executionId == null || _snapshot.value.executionId == event.executionId) { "executionId diferente da execução ativa." }
        _events.tryEmit(event)
        val nextSnapshot = AgentProgressSnapshot(
            runId = event.runId,
            executionId = event.executionId,
            sequence = event.sequence,
            terminalState = when (event) {
                is AgentProgressEvent.WaitingForUser -> AgentProgressTerminalState.WAITING_FOR_USER
                is AgentProgressEvent.RunCompleted -> AgentProgressTerminalState.COMPLETED
                is AgentProgressEvent.RunFailed -> AgentProgressTerminalState.FAILED
                is AgentProgressEvent.RunCancelled -> AgentProgressTerminalState.CANCELLED
                else -> AgentProgressTerminalState.ACTIVE
            },
            lastEvent = event,
        )
        _snapshot.value = nextSnapshot
        auditLogger.record(
            AuditEventType.AGENT_PROGRESS,
            mapOf(
                "progress_event" to event::class.simpleName.orEmpty(),
                "progress_sequence" to event.sequence.toString(),
                "terminal_state" to nextSnapshot.terminalState.name,
                "run_id" to event.runId,
                "execution_id" to event.executionId,
            ),
        )
        return event
    }

    @Synchronized
    private fun emitActive(factory: (Long, String, String) -> AgentProgressEvent): AgentProgressEvent {
        val current = _snapshot.value
        check(current.runId != null && current.executionId != null) { "Inicie uma execução antes de publicar progresso." }
        check(current.terminalState == AgentProgressTerminalState.ACTIVE || current.terminalState == AgentProgressTerminalState.WAITING_FOR_USER) {
            "A execução já terminou."
        }
        return emit(factory(current.sequence + 1L, current.runId, current.executionId))
    }

    private fun terminal(factory: (Long, String, String) -> AgentProgressEvent): AgentProgressEvent {
        val current = _snapshot.value
        check(current.runId != null && current.executionId != null) { "Inicie uma execução antes de encerrá-la." }
        check(current.terminalState != AgentProgressTerminalState.COMPLETED && current.terminalState != AgentProgressTerminalState.FAILED && current.terminalState != AgentProgressTerminalState.CANCELLED) {
            "A execução já foi encerrada."
        }
        return emit(factory(current.sequence + 1L, current.runId, current.executionId))
    }
}
