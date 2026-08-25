package com.tino.app.domain.agent

enum class TinoPresenceMode {
    IDLE,
    LISTENING,
    THINKING,
    RESOLVING,
    WAITING_FOR_USER,
    COMPLETED,
    ERROR,
}

data class TinoPresenceState(
    val mode: TinoPresenceMode = TinoPresenceMode.IDLE,
    val progressFraction: Float? = null,
    val message: String? = null,
    val stateVersion: Long = 0L,
)

/** Pure projection from real runtime signals to the visual presence state. */
object TinoPresenceResolver {
    fun resolve(
        agentState: TinoAgentSessionSnapshot,
        progress: AgentProgressSnapshot = AgentProgressSnapshot(),
        gate: HumanGateResult? = null,
    ): TinoPresenceState {
        if (gate is HumanGateResult.ConfirmationRequired) {
            return TinoPresenceState(
                mode = TinoPresenceMode.WAITING_FOR_USER,
                message = gate.request.summary,
                stateVersion = agentState.stateVersion,
            )
        }
        if (agentState.confirmationState == ConfirmationState.REQUIRED) {
            return TinoPresenceState(
                mode = TinoPresenceMode.WAITING_FOR_USER,
                message = agentState.pendingAction?.summary,
                stateVersion = agentState.stateVersion,
            )
        }
        val progressMode = when (progress.terminalState) {
            AgentProgressTerminalState.WAITING_FOR_USER -> TinoPresenceMode.WAITING_FOR_USER
            AgentProgressTerminalState.COMPLETED -> TinoPresenceMode.COMPLETED
            AgentProgressTerminalState.FAILED -> TinoPresenceMode.ERROR
            AgentProgressTerminalState.CANCELLED -> TinoPresenceMode.IDLE
            AgentProgressTerminalState.ACTIVE -> if (progress.runId != null) TinoPresenceMode.THINKING else null
        }
        val voiceMode = when (agentState.voiceState) {
            AgentVoiceState.IDLE -> TinoPresenceMode.IDLE
            AgentVoiceState.LISTENING -> TinoPresenceMode.LISTENING
            AgentVoiceState.UNDERSTANDING,
            AgentVoiceState.RESOLVING,
            AgentVoiceState.EXECUTING,
            -> TinoPresenceMode.THINKING
            AgentVoiceState.NEEDS_INPUT,
            AgentVoiceState.NEEDS_CLARIFICATION,
            AgentVoiceState.READY_TO_CONFIRM,
            AgentVoiceState.AWAITING_CONFIRMATION,
            AgentVoiceState.PREVIEW_READY,
            -> TinoPresenceMode.WAITING_FOR_USER
            AgentVoiceState.SUCCESS -> TinoPresenceMode.COMPLETED
            AgentVoiceState.FAILED -> TinoPresenceMode.ERROR
        }
        val currentVoiceState = agentState.voiceState
        val mode = when {
            currentVoiceState in setOf(
                AgentVoiceState.LISTENING,
                AgentVoiceState.UNDERSTANDING,
                AgentVoiceState.RESOLVING,
                AgentVoiceState.NEEDS_INPUT,
                AgentVoiceState.NEEDS_CLARIFICATION,
                AgentVoiceState.PREVIEW_READY,
                AgentVoiceState.AWAITING_CONFIRMATION,
                AgentVoiceState.READY_TO_CONFIRM,
                AgentVoiceState.EXECUTING,
            ) -> voiceMode
            currentVoiceState == AgentVoiceState.SUCCESS || currentVoiceState == AgentVoiceState.FAILED -> voiceMode
            else -> progressMode ?: voiceMode
        }
        return TinoPresenceState(
            mode = mode,
            progressFraction = (progress.lastEvent as? AgentProgressEvent.ToolProgress)?.fraction,
            message = when (val event = progress.lastEvent) {
                is AgentProgressEvent.ToolProgress -> event.message
                is AgentProgressEvent.WaitingForUser -> event.reason
                is AgentProgressEvent.RunFailed -> event.reason
                else -> null
            },
            stateVersion = agentState.stateVersion,
        )
    }
}
