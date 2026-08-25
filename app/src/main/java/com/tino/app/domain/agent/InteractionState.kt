package com.tino.app.domain.agent

import com.tino.app.domain.language.TinoIntent

/** Defines how long an interaction state is allowed to survive process restarts. */
enum class InteractionStatePersistencePolicy {
    SESSION,
    UNTIL_RESOLVED,
}

/** Short-lived operation context. It is never a source of current commerce facts. */
data class WorkingMemory(
    val operationIntent: TinoIntent? = null,
    val pendingAction: PendingAgentAction? = null,
    val collectedSlots: Map<String, String> = emptyMap(),
    val missingSlots: Set<String> = emptySet(),
    val pendingClarification: PendingClarification? = null,
    val updatedAtEpochMs: Long = 0L,
    val expiresAtEpochMs: Long? = null,
) {
    fun isExpired(nowEpochMs: Long): Boolean = expiresAtEpochMs != null && nowEpochMs > expiresAtEpochMs
}

/** Session context used to resolve references such as “ela” and “esse produto”. */
data class SessionMemory(
    val currentScreen: ScreenAgentContext = ScreenAgentContext("UNKNOWN"),
    val recentEntities: List<com.tino.app.domain.language.EntityReference> = emptyList(),
    val lastObjective: TinoIntent? = null,
    val activeSurfaceId: String? = null,
    val lastResultSummary: String? = null,
    val turnCount: Int = 0,
    val updatedAtEpochMs: Long = 0L,
    val expiresAtEpochMs: Long? = null,
) {
    companion object {
        const val DEFAULT_TTL_MS: Long = 30 * 60 * 1_000L
    }

    fun isExpired(nowEpochMs: Long): Boolean = expiresAtEpochMs != null && nowEpochMs > expiresAtEpochMs
}

data class PendingClarification(
    val entityType: String,
    val slot: String? = null,
    val prompt: String,
    val options: List<String> = emptyList(),
)

/**
 * Persistible boundary for one active commerce interaction.
 *
 * This is deliberately a domain model: Room and JSON stay outside this package.
 * A pending operation is stored as a resumable draft, never as an executable
 * command or a commerce fact.
 */
data class InteractionState(
    val sessionId: String,
    val stateVersion: Long = 0L,
    val currentScreen: ScreenAgentContext,
    val activeSurfaces: List<ScreenAgentContext> = emptyList(),
    val pendingAction: PendingAgentAction? = null,
    val collectedSlots: Map<String, String> = emptyMap(),
    val missingSlots: Set<String> = emptySet(),
    val confirmationState: ConfirmationState? = null,
    val voiceState: AgentVoiceState = AgentVoiceState.IDLE,
    val updatedAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val persistencePolicy: InteractionStatePersistencePolicy =
        InteractionStatePersistencePolicy.SESSION,
    val workingMemory: WorkingMemory = WorkingMemory(),
    val sessionMemory: SessionMemory = SessionMemory(),
) {
    companion object

    fun isExpired(nowEpochMs: Long): Boolean =
        expiresAtEpochMs != null && nowEpochMs > expiresAtEpochMs
}

interface InteractionStateStore {
    suspend fun load(sessionId: String): InteractionState?

    suspend fun save(state: InteractionState)

    suspend fun clear(sessionId: String)

    suspend fun expire(nowEpochMs: Long): Int
}

/** Test/default adapter used by manually constructed domain objects. */
class InMemoryInteractionStateStore : InteractionStateStore {
    private val states = linkedMapOf<String, InteractionState>()

    override suspend fun load(sessionId: String): InteractionState? = synchronized(states) {
        states[sessionId]
    }

    override suspend fun save(state: InteractionState) {
        synchronized(states) {
            val current = states[state.sessionId]
            if (current == null || state.stateVersion >= current.stateVersion) {
                states[state.sessionId] = state
            }
        }
    }

    override suspend fun clear(sessionId: String) {
        synchronized(states) { states.remove(sessionId) }
    }

    override suspend fun expire(nowEpochMs: Long): Int = synchronized(states) {
        val expired = states.values.filter { it.isExpired(nowEpochMs) }.map { it.sessionId }
        expired.forEach(states::remove)
        expired.size
    }
}

internal fun InteractionState.Companion.fromSnapshot(
    sessionId: String,
    snapshot: TinoAgentSessionSnapshot,
    nowEpochMs: Long,
): InteractionState {
    val hasWorkingMemory = snapshot.pendingAction != null || snapshot.workingMemory.pendingClarification != null
    val workingMemory = snapshot.workingMemory.copy(
        operationIntent = snapshot.pendingAction?.intent ?: snapshot.workingMemory.operationIntent,
        pendingAction = snapshot.pendingAction,
        collectedSlots = snapshot.collectedSlots,
        missingSlots = snapshot.missingSlots,
        updatedAtEpochMs = nowEpochMs,
        expiresAtEpochMs = if (hasWorkingMemory) nowEpochMs + CommerceContextTtl.DEFAULT_MS else null,
    )
    val sessionMemory = snapshot.sessionMemory.copy(
        currentScreen = snapshot.screenContext,
        recentEntities = snapshot.recentEntities,
        lastObjective = snapshot.recentIntent,
        lastResultSummary = snapshot.lastAgentResult,
        turnCount = snapshot.sessionMemory.turnCount,
        updatedAtEpochMs = nowEpochMs,
        expiresAtEpochMs = nowEpochMs + SessionMemory.DEFAULT_TTL_MS,
    )
    return InteractionState(
        sessionId = sessionId,
        stateVersion = snapshot.stateVersion,
        currentScreen = snapshot.screenContext,
        activeSurfaces = snapshot.activeSurfaces,
        pendingAction = snapshot.pendingAction,
        collectedSlots = snapshot.collectedSlots,
        missingSlots = snapshot.missingSlots,
        confirmationState = snapshot.confirmationState,
        voiceState = snapshot.voiceState,
        updatedAtEpochMs = nowEpochMs,
        expiresAtEpochMs = if (hasWorkingMemory) {
            nowEpochMs + CommerceContextTtl.DEFAULT_MS
        } else {
            null
        },
        persistencePolicy = if (hasWorkingMemory) {
            InteractionStatePersistencePolicy.UNTIL_RESOLVED
        } else {
            InteractionStatePersistencePolicy.SESSION
        },
        workingMemory = workingMemory,
        sessionMemory = sessionMemory,
    )
}

internal object CommerceContextTtl {
    const val DEFAULT_MS: Long = 10 * 60 * 1_000L
}

internal fun InteractionState.toSnapshot(nowEpochMs: Long = System.currentTimeMillis()): TinoAgentSessionSnapshot {
    val working = if (workingMemory.isExpired(nowEpochMs)) WorkingMemory() else workingMemory
    val session = if (sessionMemory.isExpired(nowEpochMs)) {
        sessionMemory.copy(
            recentEntities = emptyList(),
            lastObjective = null,
            activeSurfaceId = null,
            lastResultSummary = null,
            turnCount = 0,
        )
    } else {
        sessionMemory
    }
    return TinoAgentSessionSnapshot(
    stateVersion = stateVersion,
    screenContext = session.currentScreen,
    activeSurfaces = activeSurfaces,
    activeCustomerId = session.currentScreen.activeCustomerId,
    activeProductId = session.currentScreen.activeProductId,
    activeSupplierId = session.currentScreen.activeSupplierId,
    voiceState = voiceState,
    pendingAction = working.pendingAction ?: pendingAction,
    collectedSlots = working.collectedSlots.ifEmpty { collectedSlots },
    missingSlots = working.missingSlots.ifEmpty { missingSlots },
    recentIntent = session.lastObjective,
    recentEntities = session.recentEntities,
    lastAgentResult = session.lastResultSummary,
    confirmationState = confirmationState,
    lastTurnAtEpochMs = updatedAtEpochMs,
    workingMemory = working,
    sessionMemory = session,
    )
}
