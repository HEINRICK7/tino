package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.intelligence.presentation.GroundedResult
import com.tino.app.domain.intelligence.presentation.UiDecision
import com.tino.app.domain.intelligence.presentation.UiSurfaceSemanticType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_A2UI_SURFACE_ID = "tino-primary-surface"

/** Declarative protocol for incremental TINO surfaces. It contains no Compose or Room types. */
object TinoA2UiSurfaceProtocol {
    const val SCHEMA = "tino.a2ui.surface"
    const val VERSION = 1
}

enum class A2uiSurfaceOperation {
    CREATE_SURFACE,
    UPDATE_COMPONENTS,
    UPDATE_DATA_MODEL,
    DELETE_SURFACE,
}

data class A2uiSurfaceComponent(
    val componentId: String,
    val type: String,
    val props: Map<String, String> = emptyMap(),
    /** Structured collection data for canonical catalog/list components. */
    val items: List<A2uiCatalogItem> = emptyList(),
    /** Property name -> data-model path. Paths are relative to the surface model. */
    val bindings: Map<String, String> = emptyMap(),
    /** Declarative actions; the renderer only forwards the selected event. */
    val actions: List<String> = emptyList(),
    val actionLabels: Map<String, String> = emptyMap(),
    val actionPayloads: Map<String, Map<String, String>> = emptyMap(),
)

data class A2uiCatalogItem(
    val itemId: String,
    val iconKey: String? = null,
    val title: String,
    val context: String? = null,
    val primaryText: String = "",
    val secondaryText: String? = null,
    val supportingText: String? = null,
    val status: String = "NORMAL",
    val actionName: String? = null,
    val actionLabel: String? = null,
    val actionPayload: Map<String, String> = emptyMap(),
)

data class A2uiSurfaceMessage(
    val messageId: String,
    val surfaceId: String,
    val operation: A2uiSurfaceOperation,
    val components: List<A2uiSurfaceComponent> = emptyList(),
    val dataModel: Map<String, String> = emptyMap(),
    val schema: String = TinoA2UiSurfaceProtocol.SCHEMA,
    val version: Int = TinoA2UiSurfaceProtocol.VERSION,
    /** Monotonic surface revision; older patches are rejected. */
    val sequence: Long = 0L,
    /** Explicit terminal marker for incremental rendering. */
    val isFinal: Boolean = false,
) {
    val hasSupportedEnvelope: Boolean
        get() = schema == TinoA2UiSurfaceProtocol.SCHEMA && version == TinoA2UiSurfaceProtocol.VERSION
}

sealed interface A2uiSurfaceValidation {
    data object Valid : A2uiSurfaceValidation
    data class Invalid(val reason: String) : A2uiSurfaceValidation
}

object A2uiSurfaceValidator {
    fun validate(message: A2uiSurfaceMessage): A2uiSurfaceValidation {
        if (!message.hasSupportedEnvelope) return A2uiSurfaceValidation.Invalid("Envelope A2UI incompatível.")
        if (message.surfaceId.isBlank()) return A2uiSurfaceValidation.Invalid("surfaceId ausente.")
        if (message.messageId.isBlank()) return A2uiSurfaceValidation.Invalid("messageId ausente.")
        if (message.sequence < 0L) return A2uiSurfaceValidation.Invalid("sequence inválida.")
        if (message.operation == A2uiSurfaceOperation.DELETE_SURFACE && message.isFinal) {
            return A2uiSurfaceValidation.Invalid("DeleteSurface não pode ser um evento final.")
        }
        if (message.components.any { it.componentId.isBlank() || it.type.isBlank() }) {
            return A2uiSurfaceValidation.Invalid("Componente sem componentId ou type.")
        }
        if (message.components.map { it.componentId }.distinct().size != message.components.size) {
            return A2uiSurfaceValidation.Invalid("componentId duplicado na surface.")
        }
        if (message.components.any { component ->
                component.bindings.values.any { path -> path.isBlank() || path.startsWith("/") }
            }) {
            return A2uiSurfaceValidation.Invalid("Binding deve usar caminho relativo ao modelo.")
        }
        return when (message.operation) {
            A2uiSurfaceOperation.CREATE_SURFACE -> if (message.components.isEmpty()) {
                A2uiSurfaceValidation.Invalid("CreateSurface precisa de pelo menos um componente.")
            } else {
                A2uiSurfaceValidation.Valid
            }
            A2uiSurfaceOperation.UPDATE_COMPONENTS -> if (message.components.isEmpty()) {
                A2uiSurfaceValidation.Invalid("UpdateComponents precisa de componentes.")
            } else {
                A2uiSurfaceValidation.Valid
            }
            A2uiSurfaceOperation.UPDATE_DATA_MODEL -> if (message.dataModel.isEmpty()) {
                A2uiSurfaceValidation.Invalid("UpdateDataModel precisa de dados.")
            } else {
                A2uiSurfaceValidation.Valid
            }
            A2uiSurfaceOperation.DELETE_SURFACE -> if (message.components.isNotEmpty() || message.dataModel.isNotEmpty()) {
                A2uiSurfaceValidation.Invalid("DeleteSurface não aceita payload de componentes ou dados.")
            } else {
                A2uiSurfaceValidation.Valid
            }
        }
    }
}

data class A2uiSurfaceState(
    val surfaceId: String,
    val components: List<A2uiSurfaceComponent>,
    val dataModel: Map<String, String>,
    val lastMessageId: String? = null,
    val sequence: Long = 0L,
    val isFinal: Boolean = false,
    /** Distinguishes an explicit wire revision from legacy sequence=0 input. */
    val hasExplicitSequence: Boolean = false,
)

sealed interface A2uiSurfaceApplyResult {
    data class Applied(val state: A2uiSurfaceState?) : A2uiSurfaceApplyResult
    data class Rejected(val reason: String) : A2uiSurfaceApplyResult
}

/** In-memory host state; the renderer is free to consume the resulting snapshot. */
class A2uiSurfaceHost {
    private val surfaces = linkedMapOf<String, A2uiSurfaceState>()

    fun apply(message: A2uiSurfaceMessage): A2uiSurfaceApplyResult {
        when (val validation = A2uiSurfaceValidator.validate(message)) {
            is A2uiSurfaceValidation.Invalid -> return A2uiSurfaceApplyResult.Rejected(validation.reason)
            A2uiSurfaceValidation.Valid -> Unit
        }
        message.components.forEach { component ->
            when (val validation = TinoComponentCatalogValidator.validate(component)) {
                is TinoComponentValidation.InvalidProps -> {
                    return A2uiSurfaceApplyResult.Rejected(validation.reason)
                }
                TinoComponentValidation.Allowed,
                is TinoComponentValidation.Unknown,
                -> Unit
            }
        }

        val current = surfaces[message.surfaceId]
        if (current?.lastMessageId == message.messageId) {
            return A2uiSurfaceApplyResult.Applied(current)
        }
        if (current?.isFinal == true && message.operation != A2uiSurfaceOperation.DELETE_SURFACE) {
            return A2uiSurfaceApplyResult.Rejected("Surface já finalizada; patch descartado.")
        }
        val effectiveSequence = if (message.sequence > 0L) {
            message.sequence
        } else {
            (current?.sequence ?: 0L) + 1L
        }
        if (message.sequence > 0L && current?.hasExplicitSequence == true && message.sequence <= current.sequence) {
            return A2uiSurfaceApplyResult.Rejected("Patch A2UI repetido ou antigo descartado.")
        }
        val next = when (message.operation) {
            A2uiSurfaceOperation.CREATE_SURFACE -> A2uiSurfaceState(
                surfaceId = message.surfaceId,
                components = message.components,
                dataModel = message.dataModel,
                lastMessageId = message.messageId,
                sequence = effectiveSequence,
                isFinal = message.isFinal,
                hasExplicitSequence = message.sequence > 0L,
            )
            A2uiSurfaceOperation.UPDATE_COMPONENTS -> {
                if (current == null) return A2uiSurfaceApplyResult.Rejected("Surface inexistente.")
                val updates = message.components.associateBy { it.componentId }
                current.copy(
                    components = current.components.map { updates[it.componentId] ?: it },
                    lastMessageId = message.messageId,
                    sequence = effectiveSequence,
                    isFinal = message.isFinal,
                    hasExplicitSequence = current.hasExplicitSequence || message.sequence > 0L,
                )
            }
            A2uiSurfaceOperation.UPDATE_DATA_MODEL -> {
                if (current == null) return A2uiSurfaceApplyResult.Rejected("Surface inexistente.")
                current.copy(
                    dataModel = current.dataModel + message.dataModel,
                    lastMessageId = message.messageId,
                    sequence = effectiveSequence,
                    isFinal = message.isFinal,
                    hasExplicitSequence = current.hasExplicitSequence || message.sequence > 0L,
                )
            }
            A2uiSurfaceOperation.DELETE_SURFACE -> {
                if (current == null) return A2uiSurfaceApplyResult.Rejected("Surface inexistente.")
                surfaces.remove(message.surfaceId)
                null
            }
        }
        if (next != null) surfaces[message.surfaceId] = next
        return A2uiSurfaceApplyResult.Applied(next)
    }

    fun snapshot(surfaceId: String): A2uiSurfaceState? = surfaces[surfaceId]
}

interface A2uiComposerPort {
    fun compose(decision: UiDecision, surfaceId: String = DEFAULT_A2UI_SURFACE_ID): A2uiSurfaceMessage
}

@Singleton
class DeterministicA2uiComposer @Inject constructor() : A2uiComposerPort {
    private val nextSequenceBySurface = linkedMapOf<String, Long>()

    override fun compose(decision: UiDecision, surfaceId: String): A2uiSurfaceMessage {
        val effectiveSurfaceId = (decision as? UiDecision.UpdateSurface)?.surfaceId ?: surfaceId
        return sequence(effectiveSurfaceId, when (decision) {
            is UiDecision.CreateSurface -> create(
                surfaceId = surfaceId,
                semanticType = decision.semanticType,
                title = decision.title,
                result = decision.result,
            )
            is UiDecision.UpdateSurface -> A2uiSurfaceMessage(
                messageId = UUID.randomUUID().toString(),
                surfaceId = decision.surfaceId,
                operation = A2uiSurfaceOperation.UPDATE_DATA_MODEL,
                dataModel = modelFor(decision.result),
            )
            is UiDecision.Text -> createText(surfaceId, decision.value)
            is UiDecision.ShowResult -> createText(surfaceId, decision.result.answer, decision.result)
            is UiDecision.ShowError -> A2uiSurfaceMessage(
                messageId = UUID.randomUUID().toString(),
                surfaceId = surfaceId,
                operation = A2uiSurfaceOperation.CREATE_SURFACE,
                components = listOf(component("error", TinoA2UiComponentCatalog.INSIGHT_CARD, "Erro", decision.message)),
                dataModel = mapOf("answer" to decision.message, "status" to decision.status.name),
            )
            is UiDecision.RequestClarification -> createText(surfaceId, decision.prompt)
            is UiDecision.RequestInput -> createText(surfaceId, decision.prompt)
            is UiDecision.RequestConfirmation -> createText(surfaceId, decision.prompt)
            UiDecision.NoUi -> A2uiSurfaceMessage(
                messageId = UUID.randomUUID().toString(),
                surfaceId = surfaceId,
                operation = A2uiSurfaceOperation.DELETE_SURFACE,
            )
        })
    }

    fun updateComponents(
        surfaceId: String,
        components: List<A2uiSurfaceComponent>,
    ): A2uiSurfaceMessage = sequence(surfaceId, A2uiSurfaceMessage(
        messageId = UUID.randomUUID().toString(),
        surfaceId = surfaceId,
        operation = A2uiSurfaceOperation.UPDATE_COMPONENTS,
        components = components,
    ))

    fun finalDataModel(
        surfaceId: String,
        dataModel: Map<String, String>,
    ): A2uiSurfaceMessage = sequence(surfaceId, A2uiSurfaceMessage(
        messageId = UUID.randomUUID().toString(),
        surfaceId = surfaceId,
        operation = A2uiSurfaceOperation.UPDATE_DATA_MODEL,
        dataModel = dataModel,
        isFinal = true,
    ))

    private fun sequence(surfaceId: String, message: A2uiSurfaceMessage): A2uiSurfaceMessage = synchronized(nextSequenceBySurface) {
        val next = (nextSequenceBySurface[surfaceId] ?: 0L) + 1L
        nextSequenceBySurface[surfaceId] = next
        message.copy(sequence = next)
    }

    private fun create(
        surfaceId: String,
        semanticType: UiSurfaceSemanticType,
        title: String,
        result: GroundedResult,
    ): A2uiSurfaceMessage {
        val answer = result.answer.ifBlank { "Não há dados suficientes para exibir." }
        return A2uiSurfaceMessage(
            messageId = UUID.randomUUID().toString(),
            surfaceId = surfaceId,
            operation = A2uiSurfaceOperation.CREATE_SURFACE,
            components = listOf(component("primary", componentType(semanticType), title, answer)),
            dataModel = modelFor(result),
        )
    }

    private fun createText(surfaceId: String, answer: String, result: GroundedResult? = null): A2uiSurfaceMessage {
        val model = result?.let(::modelFor).orEmpty() + ("answer" to answer)
        return A2uiSurfaceMessage(
            messageId = UUID.randomUUID().toString(),
            surfaceId = surfaceId,
            operation = A2uiSurfaceOperation.CREATE_SURFACE,
            components = listOf(component("primary", TinoA2UiComponentCatalog.INSIGHT_CARD, "TINO", answer)),
            dataModel = model,
        )
    }

    private fun component(id: String, type: String, title: String, answer: String) = A2uiSurfaceComponent(
        componentId = id,
        type = type,
        props = mapOf("title" to title, "status" to "ANSWERED", "dataSource" to "LOCAL_FACTS"),
        bindings = mapOf("answer" to "answer"),
    )

    private fun componentType(type: UiSurfaceSemanticType): String = when (type) {
        UiSurfaceSemanticType.INVENTORY_RISK -> TinoA2UiComponentCatalog.STOCK_STATUS
        UiSurfaceSemanticType.CUSTOMER_TIMELINE -> TinoA2UiComponentCatalog.CUSTOMER_TIMELINE_CARD
        UiSurfaceSemanticType.RECEIVABLE_RANKING -> TinoA2UiComponentCatalog.RECEIVABLES_LIST
        UiSurfaceSemanticType.RESULT_LIST -> TinoA2UiComponentCatalog.PRODUCT_LIST
        else -> TinoA2UiComponentCatalog.INSIGHT_CARD
    }

    private fun modelFor(result: GroundedResult): Map<String, String> = buildMap {
        put("answer", result.answer)
        put("status", result.status.name)
        result.evidence.forEachIndexed { index, evidence ->
            put("evidence.$index.label", evidence.label)
            put("evidence.$index.value", evidence.value)
            put("evidence.$index.source", evidence.source)
        }
        if (result.limitations.isNotEmpty()) put("limitations", result.limitations.joinToString("\n"))
    }

}

private fun Map<String, String>.resolve(path: String): String? = this[path]

/** Resolves a safe declarative component into the existing typed renderer envelope. */
fun A2uiSurfaceState.toRenderableMessage(): A2uiMessage {
    val component = components.firstOrNull()
    if (component == null) {
        return A2uiMessage(
            messageId = "surface-empty",
            component = A2uiComponent.Unsupported("empty", "A surface não possui componentes."),
        )
    }
    if (!TinoA2UiComponentCatalog.isAllowed(component.type)) {
        return A2uiMessage(
            messageId = "surface-unsupported",
            component = A2uiComponent.Unsupported(component.type, "Componente fora da allowlist TINO."),
        )
    }
    val answer = component.bindings["answer"]?.let(dataModel::resolve).orEmpty()
    return A2uiMessage(
        messageId = "surface-${surfaceId}-${component.componentId}",
        component = A2uiComponent.InsightCard(
            title = component.props["title"] ?: "TINO",
            answer = answer.ifBlank { component.props["answer"].orEmpty() },
            status = dataModel["status"] ?: component.props["status"] ?: "ANSWERED",
            evidence = dataModel.entries
                .filter { it.key.startsWith("evidence.") && it.key.endsWith(".value") }
                .sortedBy { it.key }
                .map { entry ->
                    val index = entry.key.removePrefix("evidence.").removeSuffix(".value")
                    A2uiDetailRow(dataModel["evidence.$index.label"] ?: "Fato", entry.value)
                },
            limitations = dataModel["limitations"]?.split("\n").orEmpty(),
            dataSource = component.props["dataSource"] ?: "LOCAL_FACTS",
        ),
    )
}

/** Bridges the already-supported typed message into the declarative surface host. */
fun A2uiMessage.toSurfaceMessage(
    surfaceId: String,
    operation: A2uiSurfaceOperation = A2uiSurfaceOperation.CREATE_SURFACE,
): A2uiSurfaceMessage {
    val (title, answer, status, dataSource) = when (val value = component) {
        is A2uiComponent.InsightCard -> Quad(value.title, value.answer, value.status, value.dataSource)
        is A2uiComponent.FinancialSummaryCard -> Quad(value.title, value.primaryValueText, "ANSWERED", value.dataSource)
        is A2uiComponent.CustomerBalanceCard -> Quad(value.title, value.currentBalanceText, "ANSWERED", value.dataSource)
        is A2uiComponent.CustomerTimelineCard -> Quad(value.title, value.currentBalanceText, "ANSWERED", value.dataSource)
        is A2uiComponent.ReadListCard -> Quad(
            value.title,
            value.items.joinToString { it.title },
            if (value.type == TinoA2UiComponentCatalog.PRODUCT_REPLENISHMENT) "ATTENTION" else "ANSWERED",
            value.dataSource,
        )
        is A2uiComponent.EntityChoice -> Quad(value.title, value.prompt, "NEEDS_CLARIFICATION", "LOCAL_FACTS")
        is A2uiComponent.ActionConfirmation -> Quad(value.title, value.detail, "NEEDS_CONFIRMATION", "LOCAL_FACTS")
        is A2uiComponent.ErrorStatusCard -> Quad(value.title, value.message, "ERROR", "LOCAL_FACTS")
        is A2uiComponent.Unsupported -> Quad("TINO", value.reason, "ERROR", "LOCAL_FACTS")
    }
    return A2uiSurfaceMessage(
        messageId = messageId,
        surfaceId = surfaceId,
        operation = operation,
        components = listOf(
            A2uiSurfaceComponent(
                componentId = "primary",
                type = component.type,
                props = mapOf("title" to title, "status" to status, "dataSource" to dataSource),
                bindings = mapOf("answer" to "answer"),
            ),
        ),
        dataModel = mapOf("answer" to answer, "status" to status),
    )
}

private data class Quad(
    val title: String,
    val answer: String,
    val status: String,
    val dataSource: String,
)
