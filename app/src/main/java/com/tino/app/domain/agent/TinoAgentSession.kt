package com.tino.app.domain.agent

import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.core.observability.NoOpAuditLogger
import com.tino.app.domain.language.EntityReference
import com.tino.app.domain.language.ContextReferenceSource
import com.tino.app.domain.language.TinoIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AgentVoiceState {
    IDLE,
    LISTENING,
    UNDERSTANDING,
    RESOLVING,
    NEEDS_INPUT,
    NEEDS_CLARIFICATION,
    PREVIEW_READY,
    AWAITING_CONFIRMATION,
    READY_TO_CONFIRM,
    EXECUTING,
    SUCCESS,
    FAILED,
}

data class ScreenAgentContext(
    val screen: String,
    val activeCustomerId: String? = null,
    val activeProductId: String? = null,
    val activeSupplierId: String? = null,
    val tags: Set<String> = emptySet(),
    val primaryEntity: EntityReference? = null,
    val secondaryEntities: List<EntityReference> = emptyList(),
    val availableCapabilities: Set<TinoCapabilityId> = emptySet(),
)

enum class PendingActionStage {
    DRAFT,
    PREVIEW_READY,
    AWAITING_CONFIRMATION,
    EXECUTING,
    COMPLETED,
    CANCELLED,
}

enum class ConfirmationState {
    NOT_REQUIRED,
    REQUIRED,
    CONFIRMED,
    CANCELLED,
}

data class AgentOperationReference(
    val id: String,
    val capability: TinoCapabilityId,
)

data class PendingAgentAction(
    val capability: TinoCapabilityId,
    val summary: String,
    val requiresConfirmation: Boolean,
    val intent: TinoIntent? = null,
    val collectedSlots: Map<String, String> = emptyMap(),
    val missingSlots: Set<String> = emptySet(),
    val resolvedEntities: List<EntityReference> = emptyList(),
    val risk: TinoCapabilityRisk = TinoCapabilityRegistry.require(capability).risk,
    val stage: PendingActionStage = PendingActionStage.DRAFT,
    val draftItems: List<PendingDraftItem> = emptyList(),
)

data class PendingDraftItem(
    val customer: String? = null,
    val product: String? = null,
    val quantity: String? = null,
    val amount: String? = null,
)

data class TinoAgentSessionSnapshot(
    /** Monotonic revision used to identify the latest shared interaction state. */
    val stateVersion: Long = 0L,
    val screenContext: ScreenAgentContext = ScreenAgentContext("UNKNOWN"),
    val activeSurfaces: List<ScreenAgentContext> = emptyList(),
    val activeCustomerId: String? = null,
    val activeProductId: String? = null,
    val activeSupplierId: String? = null,
    val recentCapability: TinoCapabilityId? = null,
    val voiceState: AgentVoiceState = AgentVoiceState.IDLE,
    val pendingAction: PendingAgentAction? = null,
    val collectedSlots: Map<String, String> = emptyMap(),
    val missingSlots: Set<String> = emptySet(),
    val recentIntent: TinoIntent? = null,
    val recentEntities: List<EntityReference> = emptyList(),
    val confirmationState: ConfirmationState? = null,
    val lastOperation: AgentOperationReference? = null,
    val lastResolvedReference: EntityReference? = null,
    val lastReferenceSource: ContextReferenceSource? = null,
    val lastAgentResult: String? = null,
    val lastTurnAtEpochMs: Long? = null,
    val workingMemory: WorkingMemory = WorkingMemory(),
    val sessionMemory: SessionMemory = SessionMemory(),
)

/**
 * Single observation port for the live agent interaction.
 *
 * UI and A2UI adapters must observe this flow instead of keeping a second
 * mutable copy of the agent session. Room remains the persistence adapter for
 * this state and never becomes the source of operational commerce facts.
 */
interface SharedAgentState {
    val snapshot: StateFlow<TinoAgentSessionSnapshot>
}

/** Application-scoped session shared by every screen and every voice entry point. */
@Singleton
class TinoAgentSession @Inject constructor(
    private val interactionStateStore: InteractionStateStore,
    private val auditLogger: AuditLogger,
) : SharedAgentState {
    constructor() : this(InMemoryInteractionStateStore(), NoOpAuditLogger)

    companion object {
        const val DEFAULT_SESSION_ID = "default"
    }

    private val _snapshot = MutableStateFlow(TinoAgentSessionSnapshot())
    override val snapshot: StateFlow<TinoAgentSessionSnapshot> = _snapshot.asStateFlow()
    private val ephemeralCapabilities = mutableSetOf<TinoCapabilityId>()
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistenceMutex = Mutex()
    @Volatile
    private var locallyMutated = false

    init {
        persistenceScope.launch {
            interactionStateStore.load(DEFAULT_SESSION_ID)?.let { restored ->
                if (!locallyMutated && !restored.isExpired(System.currentTimeMillis())) {
                    _snapshot.value = restored.toSnapshot(System.currentTimeMillis())
                }
            }
        }
    }

    @Synchronized
    fun enterScreen(context: ScreenAgentContext) {
        val current = _snapshot.value
        if (current.screenContext.screen != context.screen) ephemeralCapabilities.clear()
        val activeSurfaces = (current.activeSurfaces.filterNot { it.screen == context.screen } + context)
            .takeLast(8)
        applySnapshot(current.copy(
            screenContext = context,
            activeSurfaces = activeSurfaces,
            activeCustomerId = context.activeCustomerId ?: current.activeCustomerId,
            activeProductId = context.activeProductId ?: current.activeProductId,
            activeSupplierId = context.activeSupplierId ?: current.activeSupplierId,
            recentEntities = (listOfNotNull(context.primaryEntity) + context.secondaryEntities + current.recentEntities)
                .distinctBy { it.type to it.text }
                .take(8),
        ))
    }

    @Synchronized
    fun availableCapabilities(): Set<TinoCapabilityId> =
        _snapshot.value.screenContext.availableCapabilities + ephemeralCapabilities

    @Synchronized
    fun grantEphemeralCapability(capability: TinoCapabilityId) {
        ephemeralCapabilities += capability
    }

    @Synchronized
    fun rememberIntent(intent: TinoIntent, entities: List<EntityReference> = emptyList()) {
        val current = _snapshot.value
        applySnapshot(current.copy(
            recentIntent = intent,
            recentEntities = (entities + current.recentEntities)
                .distinctBy { it.type to it.text }
                .take(8),
            lastResolvedReference = entities.firstOrNull() ?: current.lastResolvedReference,
            lastTurnAtEpochMs = System.currentTimeMillis(),
            sessionMemory = current.sessionMemory.copy(turnCount = current.sessionMemory.turnCount + 1),
        ))
    }

    @Synchronized
    fun rememberIntent(
        intent: TinoIntent,
        entities: List<EntityReference>,
        source: ContextReferenceSource?,
    ) {
        rememberIntent(intent, entities)
        applySnapshot(_snapshot.value.copy(lastReferenceSource = source))
    }

    @Synchronized
    fun updateDraft(action: PendingAgentAction) {
        val current = _snapshot.value
        applySnapshot(current.copy(
            pendingAction = action,
            collectedSlots = action.collectedSlots,
            missingSlots = action.missingSlots,
            confirmationState = if (action.requiresConfirmation) ConfirmationState.REQUIRED else ConfirmationState.NOT_REQUIRED,
            recentCapability = action.capability,
            lastTurnAtEpochMs = System.currentTimeMillis(),
        ))
    }

    /**
     * Applies a correction only if the snapshot read by the patcher is still
     * current. This prevents a late correction from overwriting a newer turn
     * or an execution transition.
     */
    @Synchronized
    fun updateDraftIfVersion(expectedStateVersion: Long, action: PendingAgentAction): Boolean {
        if (_snapshot.value.stateVersion != expectedStateVersion) return false
        updateDraft(action)
        return true
    }

    @Synchronized
    fun beginListening() = updateVoiceState(AgentVoiceState.LISTENING)

    @Synchronized
    fun beginUnderstanding() = updateVoiceState(AgentVoiceState.UNDERSTANDING)

    @Synchronized
    fun beginResolving() = updateVoiceState(AgentVoiceState.RESOLVING)

    @Synchronized
    fun needsInput() = updateVoiceState(AgentVoiceState.NEEDS_INPUT)

    @Synchronized
    fun needsClarification() = updateVoiceState(AgentVoiceState.NEEDS_CLARIFICATION)

    @Synchronized
    fun readyToConfirm(action: PendingAgentAction) {
        updateDraft(action.copy(stage = PendingActionStage.AWAITING_CONFIRMATION))
        applySnapshot(_snapshot.value.copy(voiceState = AgentVoiceState.READY_TO_CONFIRM))
    }

    @Synchronized
    fun beginExecuting() = updateVoiceState(AgentVoiceState.EXECUTING)

    @Synchronized
    fun markPreviewReady(action: PendingAgentAction) {
        updateDraft(action.copy(stage = PendingActionStage.PREVIEW_READY))
        applySnapshot(_snapshot.value.copy(voiceState = AgentVoiceState.PREVIEW_READY))
    }

    @Synchronized
    fun markSuccess() {
        ephemeralCapabilities.clear()
        applySnapshot(_snapshot.value.copy(
            voiceState = AgentVoiceState.SUCCESS,
            pendingAction = null,
            collectedSlots = emptyMap(),
            missingSlots = emptySet(),
            confirmationState = null,
            lastAgentResult = "success",
            lastTurnAtEpochMs = System.currentTimeMillis(),
        ))
    }

    @Synchronized
    fun markFailed() {
        ephemeralCapabilities.clear()
        applySnapshot(_snapshot.value.copy(
            voiceState = AgentVoiceState.FAILED,
            lastAgentResult = "failed",
            lastTurnAtEpochMs = System.currentTimeMillis(),
        ))
    }

    @Synchronized
    fun recordOperation(reference: AgentOperationReference) {
        applySnapshot(_snapshot.value.copy(lastOperation = reference))
    }

    @Synchronized
    fun cancel() {
        ephemeralCapabilities.clear()
        applySnapshot(_snapshot.value.copy(
            voiceState = AgentVoiceState.IDLE,
            pendingAction = null,
            collectedSlots = emptyMap(),
            missingSlots = emptySet(),
            confirmationState = ConfirmationState.CANCELLED,
            lastAgentResult = "cancelled",
            lastTurnAtEpochMs = System.currentTimeMillis(),
        ))
    }

    @Synchronized
    fun rememberResult(result: String) {
        applySnapshot(_snapshot.value.copy(
            lastAgentResult = result,
            lastTurnAtEpochMs = System.currentTimeMillis(),
        ))
    }

    @Synchronized
    fun rememberSurface(surfaceId: String) {
        if (surfaceId.isBlank()) return
        applySnapshot(_snapshot.value.copy(
            sessionMemory = _snapshot.value.sessionMemory.copy(activeSurfaceId = surfaceId),
        ))
    }

    @Synchronized
    fun rememberClarification(clarification: PendingClarification) {
        applySnapshot(_snapshot.value.copy(
            workingMemory = _snapshot.value.workingMemory.copy(pendingClarification = clarification),
        ))
    }

    @Synchronized
    fun clearClarification() {
        applySnapshot(_snapshot.value.copy(
            workingMemory = _snapshot.value.workingMemory.copy(pendingClarification = null),
        ))
    }

    /** Expire only conversational state; the current screen remains a valid anchor. */
    @Synchronized
    fun expireConversation(nowEpochMs: Long = System.currentTimeMillis(), ttlMs: Long = 10 * 60 * 1_000L) {
        val lastTurn = _snapshot.value.lastTurnAtEpochMs ?: return
        if (nowEpochMs - lastTurn <= ttlMs) return
        applySnapshot(_snapshot.value.copy(
            pendingAction = null,
            collectedSlots = emptyMap(),
            missingSlots = emptySet(),
            recentCapability = null,
            recentIntent = null,
            lastResolvedReference = null,
            lastReferenceSource = null,
            confirmationState = null,
            workingMemory = _snapshot.value.workingMemory.copy(
                operationIntent = null,
                pendingAction = null,
                collectedSlots = emptyMap(),
                missingSlots = emptySet(),
                pendingClarification = null,
            ),
            sessionMemory = _snapshot.value.sessionMemory.copy(
                recentEntities = emptyList(),
                lastObjective = null,
                activeSurfaceId = null,
                lastResultSummary = null,
                turnCount = 0,
            ),
        ))
    }

    @Synchronized
    fun reset() {
        ephemeralCapabilities.clear()
        locallyMutated = true
        _snapshot.value = TinoAgentSessionSnapshot(
            stateVersion = _snapshot.value.stateVersion + 1,
        )
        persistenceScope.launch {
            persistenceMutex.withLock { interactionStateStore.clear(DEFAULT_SESSION_ID) }
        }
    }

    private fun updateVoiceState(state: AgentVoiceState) {
        applySnapshot(_snapshot.value.copy(voiceState = state))
    }

    private fun applySnapshot(next: TinoAgentSessionSnapshot) {
        locallyMutated = true
        val now = System.currentTimeMillis()
        val normalized = next.copy(
            stateVersion = maxOf(next.stateVersion, _snapshot.value.stateVersion + 1),
            workingMemory = next.workingMemory.copy(
                operationIntent = next.pendingAction?.intent,
                pendingAction = next.pendingAction,
                collectedSlots = next.collectedSlots,
                missingSlots = next.missingSlots,
                updatedAtEpochMs = now,
                expiresAtEpochMs = if (next.pendingAction != null || next.workingMemory.pendingClarification != null) {
                    now + CommerceContextTtl.DEFAULT_MS
                } else {
                    null
                },
            ),
            sessionMemory = next.sessionMemory.copy(
                currentScreen = next.screenContext,
                recentEntities = next.recentEntities,
                lastObjective = next.recentIntent,
                lastResultSummary = next.lastAgentResult,
                updatedAtEpochMs = now,
                expiresAtEpochMs = now + SessionMemory.DEFAULT_TTL_MS,
            ),
        )
        _snapshot.value = normalized
        auditLogger.record(
            AuditEventType.AGENT_STATE_CHANGED,
            mapOf(
                "state_version" to normalized.stateVersion.toString(),
                "voice_state" to normalized.voiceState.name,
                "pending_action_stage" to (normalized.pendingAction?.stage?.name ?: "NONE"),
                "confirmation_state" to (normalized.confirmationState?.name ?: "NONE"),
            ),
        )
        val interaction = InteractionState.fromSnapshot(
            sessionId = DEFAULT_SESSION_ID,
            snapshot = normalized,
            nowEpochMs = now,
        )
        persistenceScope.launch {
            persistenceMutex.withLock { interactionStateStore.save(interaction) }
        }
    }
}

interface AgentContextProvider {
    fun agentContext(): ScreenAgentContext
}
