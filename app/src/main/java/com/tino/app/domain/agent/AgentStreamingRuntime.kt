package com.tino.app.domain.agent

import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AgentStreamEventType {
    SPEECH,
    TRANSCRIPT_PARTIAL,
    TRANSCRIPT_COMMITTED,
    AGENT_STARTED,
    STATE_CHANGED,
    TOOL_STARTED,
    TOOL_PROGRESS,
    TOOL_COMPLETED,
    A2UI_UPDATED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class AgentStreamEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val runId: String,
    val sequence: Long,
    val occurredAtEpochMs: Long,
    val type: AgentStreamEventType,
    val payloadVersion: Int = 1,
    val payload: Map<String, String> = emptyMap(),
)

enum class AgentStreamTerminalState { IDLE, ACTIVE, COMPLETED, FAILED, CANCELLED }

data class AgentStreamSnapshot(
    val runId: String? = null,
    val sequence: Long = 0L,
    val terminalState: AgentStreamTerminalState = AgentStreamTerminalState.IDLE,
    val lastEvent: AgentStreamEvent? = null,
)

/** Ordered, cancellable event envelope for the full speech-to-A2UI stream. */
class AgentStreamingRuntime(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()
    // Replay keeps the latest burst available to a host attaching after a
    // recreation; a collector that is slow still applies normal backpressure.
    private val _events = MutableSharedFlow<AgentStreamEvent>(replay = 64)
    val events: SharedFlow<AgentStreamEvent> = _events.asSharedFlow()
    private val _snapshot = MutableStateFlow(AgentStreamSnapshot())
    val snapshot: StateFlow<AgentStreamSnapshot> = _snapshot.asStateFlow()
    private val closedRuns = mutableSetOf<String>()
    private val nextSequences = mutableMapOf<String, Long>()
    private var activeRunId: String? = null

    suspend fun activeRunIdOrNull(): String? = mutex.withLock { activeRunId }

    suspend fun emit(
        runId: String,
        type: AgentStreamEventType,
        payload: Map<String, String> = emptyMap(),
        payloadVersion: Int = 1,
    ): AgentStreamEvent = mutex.withLock {
        check(runId.isNotBlank()) { "runId ausente." }
        require(payloadVersion > 0) { "payloadVersion inválido." }
        require(payload.size <= 32) { "Payload do stream excede o limite." }
        require(payload.keys.all { it.isNotBlank() && it.length <= 64 }) { "Chave de payload inválida." }
        require(payload.values.all { it.length <= 2_048 }) { "Valor de payload excede o limite." }
        check(runId !in closedRuns) { "O stream já foi encerrado." }
        check(activeRunId == null || activeRunId == runId) {
            "Já existe outro stream ativo."
        }
        if (activeRunId == null) activeRunId = runId
        val sequence = (nextSequences[runId] ?: 0L) + 1L
        val event = AgentStreamEvent(
            runId = runId,
            sequence = sequence,
            occurredAtEpochMs = clock(),
            type = type,
            payloadVersion = payloadVersion,
            payload = payload,
        )
        _events.emit(event)
        nextSequences[runId] = sequence
        val terminalState = when (type) {
            AgentStreamEventType.COMPLETED -> AgentStreamTerminalState.COMPLETED
            AgentStreamEventType.FAILED -> AgentStreamTerminalState.FAILED
            AgentStreamEventType.CANCELLED -> AgentStreamTerminalState.CANCELLED
            else -> AgentStreamTerminalState.ACTIVE
        }
        _snapshot.value = AgentStreamSnapshot(
            runId = runId,
            sequence = sequence,
            terminalState = terminalState,
            lastEvent = event,
        )
        if (terminalState != AgentStreamTerminalState.ACTIVE) {
            closedRuns += runId
            activeRunId = null
        }
        event
    }

    suspend fun close(
        runId: String,
        type: AgentStreamEventType,
        payload: Map<String, String> = emptyMap(),
        payloadVersion: Int = 1,
    ): AgentStreamEvent {
        require(type in setOf(AgentStreamEventType.COMPLETED, AgentStreamEventType.FAILED, AgentStreamEventType.CANCELLED)) {
            "close exige um evento terminal."
        }
        return emit(runId, type, payload, payloadVersion)
    }
}
