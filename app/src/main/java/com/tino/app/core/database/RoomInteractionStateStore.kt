package com.tino.app.core.database

import com.tino.app.domain.agent.AgentVoiceState
import com.tino.app.domain.agent.ConfirmationState
import com.tino.app.domain.agent.InteractionState
import com.tino.app.domain.agent.InteractionStatePersistencePolicy
import com.tino.app.domain.agent.InteractionStateStore
import com.tino.app.domain.agent.PendingActionStage
import com.tino.app.domain.agent.PendingAgentAction
import com.tino.app.domain.agent.PendingDraftItem
import com.tino.app.domain.agent.PendingClarification
import com.tino.app.domain.agent.SessionMemory
import com.tino.app.domain.agent.ScreenAgentContext
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.agent.TinoCapabilityRisk
import com.tino.app.domain.agent.WorkingMemory
import com.tino.app.domain.language.EntityReference
import com.tino.app.domain.language.LanguageEntityType
import com.tino.app.domain.language.TinoIntent
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomInteractionStateStore @Inject constructor(
    private val dao: InteractionStateDao,
) : InteractionStateStore {
    override suspend fun load(sessionId: String): InteractionState? {
        val entity = dao.findBySessionId(sessionId) ?: return null
        val state = runCatching { entity.toDomain() }.getOrNull()
        if (state == null || state.isExpired(System.currentTimeMillis())) {
            dao.deleteBySessionId(sessionId)
            return null
        }
        return state
    }

    override suspend fun save(state: InteractionState) {
        val existing = dao.findBySessionId(state.sessionId)
        val existingVersion = existing
            ?.let { runCatching { JSONObject(it.stateJson).optLong("stateVersion", 0L) }.getOrDefault(0L) }
            ?: Long.MIN_VALUE
        if (existingVersion > state.stateVersion) return
        dao.upsert(
            InteractionStateEntity(
                sessionId = state.sessionId,
                stateJson = state.toJson().toString(),
                persistencePolicy = state.persistencePolicy.name,
                updatedAtEpochMs = state.updatedAtEpochMs,
                expiresAtEpochMs = state.expiresAtEpochMs,
            ),
        )
    }

    override suspend fun clear(sessionId: String) {
        dao.deleteBySessionId(sessionId)
    }

    override suspend fun expire(nowEpochMs: Long): Int = dao.deleteExpired(nowEpochMs)

    private fun InteractionStateEntity.toDomain(): InteractionState {
        val json = JSONObject(stateJson)
        val currentScreen = json.getJSONObject("currentScreen").toScreenContext()
        val activeSurfaces = json.optJSONArray("activeSurfaces")?.toScreens().orEmpty()
        val pendingAction = json.optJSONObject("pendingAction")?.toPendingAction()
        val collectedSlots = json.optJSONObject("collectedSlots")?.toStringMap().orEmpty()
        val missingSlots = json.optJSONArray("missingSlots")?.toStringSet().orEmpty()
        val workingMemory = json.optJSONObject("workingMemory")?.toWorkingMemory()
            ?: WorkingMemory(
                operationIntent = pendingAction?.intent,
                pendingAction = pendingAction,
                collectedSlots = collectedSlots,
                missingSlots = missingSlots,
            )
        val sessionMemory = json.optJSONObject("sessionMemory")?.toSessionMemory()
            ?: SessionMemory(currentScreen = currentScreen)
        return InteractionState(
            sessionId = sessionId,
            stateVersion = json.optLong("stateVersion", 0L),
            currentScreen = currentScreen,
            activeSurfaces = activeSurfaces,
            pendingAction = pendingAction,
            collectedSlots = collectedSlots,
            missingSlots = missingSlots,
            confirmationState = json.optString("confirmationState").toEnumOrNull<ConfirmationState>(),
            voiceState = json.optString("voiceState").toEnumOrNull<AgentVoiceState>() ?: AgentVoiceState.IDLE,
            updatedAtEpochMs = updatedAtEpochMs,
            expiresAtEpochMs = expiresAtEpochMs,
            persistencePolicy = persistencePolicy.toEnumOrNull<InteractionStatePersistencePolicy>()
                ?: InteractionStatePersistencePolicy.SESSION,
            workingMemory = workingMemory,
            sessionMemory = sessionMemory,
        )
    }
}

private fun InteractionState.toJson(): JSONObject = JSONObject().apply {
    put("stateVersion", stateVersion)
    put("currentScreen", currentScreen.toJson())
    put("activeSurfaces", JSONArray().also { array -> activeSurfaces.forEach { array.put(it.toJson()) } })
    put("pendingAction", pendingAction?.toJson() ?: JSONObject.NULL)
    put("collectedSlots", collectedSlots.toJson())
    put("missingSlots", missingSlots.toJson())
    put("confirmationState", confirmationState?.name ?: JSONObject.NULL)
    put("voiceState", voiceState.name)
    put("workingMemory", workingMemory.toJson())
    put("sessionMemory", sessionMemory.toJson())
}

private fun WorkingMemory.toJson(): JSONObject = JSONObject().apply {
    put("operationIntent", operationIntent?.name ?: JSONObject.NULL)
    put("pendingAction", pendingAction?.toJson() ?: JSONObject.NULL)
    put("collectedSlots", collectedSlots.toJson())
    put("missingSlots", missingSlots.toJson())
    put("pendingClarification", pendingClarification?.toJson() ?: JSONObject.NULL)
    put("updatedAtEpochMs", updatedAtEpochMs)
    put("expiresAtEpochMs", expiresAtEpochMs ?: JSONObject.NULL)
}

private fun JSONObject.toWorkingMemory(): WorkingMemory = WorkingMemory(
    operationIntent = optStringOrNull("operationIntent")?.toEnumOrNull<TinoIntent>(),
    pendingAction = optJSONObject("pendingAction")?.toPendingAction(),
    collectedSlots = optJSONObject("collectedSlots")?.toStringMap().orEmpty(),
    missingSlots = optJSONArray("missingSlots")?.toStringSet().orEmpty(),
    pendingClarification = optJSONObject("pendingClarification")?.toPendingClarification(),
    updatedAtEpochMs = optLong("updatedAtEpochMs"),
    expiresAtEpochMs = optLongOrNull("expiresAtEpochMs"),
)

private fun SessionMemory.toJson(): JSONObject = JSONObject().apply {
    put("currentScreen", currentScreen.toJson())
    put("recentEntities", JSONArray().also { array -> recentEntities.forEach { array.put(it.toJson()) } })
    put("lastObjective", lastObjective?.name ?: JSONObject.NULL)
    put("activeSurfaceId", activeSurfaceId ?: JSONObject.NULL)
    put("lastResultSummary", lastResultSummary ?: JSONObject.NULL)
    put("turnCount", turnCount)
    put("updatedAtEpochMs", updatedAtEpochMs)
    put("expiresAtEpochMs", expiresAtEpochMs ?: JSONObject.NULL)
}

private fun JSONObject.toSessionMemory(): SessionMemory = SessionMemory(
    currentScreen = optJSONObject("currentScreen")?.toScreenContext()
        ?: ScreenAgentContext("UNKNOWN"),
    recentEntities = optJSONArray("recentEntities")?.toEntityReferences().orEmpty(),
    lastObjective = optStringOrNull("lastObjective")?.toEnumOrNull<TinoIntent>(),
    activeSurfaceId = optStringOrNull("activeSurfaceId"),
    lastResultSummary = optStringOrNull("lastResultSummary"),
    turnCount = optInt("turnCount"),
    updatedAtEpochMs = optLong("updatedAtEpochMs"),
    expiresAtEpochMs = optLongOrNull("expiresAtEpochMs"),
)

private fun PendingClarification.toJson(): JSONObject = JSONObject().apply {
    put("entityType", entityType)
    put("slot", slot ?: JSONObject.NULL)
    put("prompt", prompt)
    put("options", JSONArray().also { array -> options.forEach(array::put) })
}

private fun JSONObject.toPendingClarification(): PendingClarification = PendingClarification(
    entityType = getString("entityType"),
    slot = optStringOrNull("slot"),
    prompt = getString("prompt"),
    options = optJSONArray("options")?.toStringList().orEmpty(),
)

private fun ScreenAgentContext.toJson(): JSONObject = JSONObject().apply {
    put("screen", screen)
    put("activeCustomerId", activeCustomerId ?: JSONObject.NULL)
    put("activeProductId", activeProductId ?: JSONObject.NULL)
    put("activeSupplierId", activeSupplierId ?: JSONObject.NULL)
    put("tags", tags.toJson())
    put("primaryEntity", primaryEntity?.toJson() ?: JSONObject.NULL)
    put("secondaryEntities", JSONArray().also { array -> secondaryEntities.forEach { array.put(it.toJson()) } })
    put("availableCapabilities", availableCapabilities.map { it.name }.toJson())
}

private fun JSONObject.toScreenContext(): ScreenAgentContext = ScreenAgentContext(
    screen = getString("screen"),
    activeCustomerId = optStringOrNull("activeCustomerId"),
    activeProductId = optStringOrNull("activeProductId"),
    activeSupplierId = optStringOrNull("activeSupplierId"),
    tags = getJSONArray("tags").toStringSet(),
    primaryEntity = optJSONObject("primaryEntity")?.toEntityReference(),
    secondaryEntities = getJSONArray("secondaryEntities").toEntityReferences(),
    availableCapabilities = getJSONArray("availableCapabilities").toStringSet()
        .mapNotNull { it.toEnumOrNull<TinoCapabilityId>() }
        .toSet(),
)

private fun JSONArray.toScreens(): List<ScreenAgentContext> =
    (0 until length()).map { getJSONObject(it).toScreenContext() }

private fun EntityReference.toJson(): JSONObject = JSONObject().apply {
    put("type", type.name)
    put("text", text)
}

private fun JSONObject.toEntityReference(): EntityReference = EntityReference(
    type = getString("type").toEnumOrNull<LanguageEntityType>() ?: LanguageEntityType.PRODUCT,
    text = getString("text"),
)

private fun JSONArray.toEntityReferences(): List<EntityReference> =
    (0 until length()).map { getJSONObject(it).toEntityReference() }

private fun PendingAgentAction.toJson(): JSONObject = JSONObject().apply {
    put("capability", capability.name)
    put("summary", summary)
    put("requiresConfirmation", requiresConfirmation)
    put("intent", intent?.name ?: JSONObject.NULL)
    put("collectedSlots", collectedSlots.toJson())
    put("missingSlots", missingSlots.toJson())
    put("resolvedEntities", JSONArray().also { array -> resolvedEntities.forEach { array.put(it.toJson()) } })
    put("risk", risk.name)
    put("stage", stage.name)
    put("draftItems", JSONArray().also { array -> draftItems.forEach { array.put(it.toJson()) } })
}

private fun JSONObject.toPendingAction(): PendingAgentAction = PendingAgentAction(
    capability = getString("capability").toEnumOrNull<TinoCapabilityId>() ?: TinoCapabilityId.SEARCH,
    summary = getString("summary"),
    requiresConfirmation = getBoolean("requiresConfirmation"),
    intent = optStringOrNull("intent")?.toEnumOrNull<TinoIntent>(),
    collectedSlots = getJSONObject("collectedSlots").toStringMap(),
    missingSlots = getJSONArray("missingSlots").toStringSet(),
    resolvedEntities = getJSONArray("resolvedEntities").toEntityReferences(),
    risk = optString("risk").toEnumOrNull<TinoCapabilityRisk>() ?: TinoCapabilityRisk.LOW,
    stage = optString("stage").toEnumOrNull<PendingActionStage>() ?: PendingActionStage.DRAFT,
    draftItems = getJSONArray("draftItems").toDraftItems(),
)

private fun PendingDraftItem.toJson(): JSONObject = JSONObject().apply {
    put("customer", customer ?: JSONObject.NULL)
    put("product", product ?: JSONObject.NULL)
    put("quantity", quantity ?: JSONObject.NULL)
    put("amount", amount ?: JSONObject.NULL)
}

private fun JSONArray.toDraftItems(): List<PendingDraftItem> = (0 until length()).map { index ->
    getJSONObject(index).let { json ->
        PendingDraftItem(
            customer = json.optStringOrNull("customer"),
            product = json.optStringOrNull("product"),
            quantity = json.optStringOrNull("quantity"),
            amount = json.optStringOrNull("amount"),
        )
    }
}

private fun Map<String, String>.toJson(): JSONObject = JSONObject().also { json ->
    forEach { (key, value) -> json.put(key, value) }
}

private fun JSONObject.toStringMap(): Map<String, String> = keys().asSequence().associateWith { getString(it) }

private fun Collection<String>.toJson(): JSONArray = JSONArray().also { array -> forEach(array::put) }

private fun JSONArray.toStringSet(): Set<String> = (0 until length()).map { getString(it) }.toSet()

private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != JSONObject.NULL.toString() }

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)

private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
    runCatching { enumValueOf<T>(this) }.getOrNull()
