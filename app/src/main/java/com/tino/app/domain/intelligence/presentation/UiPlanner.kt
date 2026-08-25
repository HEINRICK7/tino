package com.tino.app.domain.intelligence.presentation

import com.tino.app.domain.intelligence.IntelligenceResponse
import com.tino.app.domain.intelligence.IntelligenceResponseStatus
import javax.inject.Inject
import javax.inject.Singleton

/** Context relevant to presentation policy; it contains no Android/UI objects. */
data class UiContext(
    val currentScreen: String? = null,
    val activeSurfaceId: String? = null,
    val activeSurfaceSemanticType: UiSurfaceSemanticType? = null,
    val locale: String = "pt-BR",
)

enum class UiSurfaceSemanticType {
    RECEIVABLE_RANKING,
    COMPARISON,
    INVENTORY_RISK,
    CUSTOMER_TIMELINE,
    RESULT_LIST,
    INSIGHT,
    CONFIRMATION,
    CLARIFICATION,
    ERROR,
}

enum class UiDecisionKind {
    TEXT,
    CREATE_SURFACE,
    UPDATE_SURFACE,
    REQUEST_INPUT,
    REQUEST_CLARIFICATION,
    REQUEST_CONFIRMATION,
    SHOW_RESULT,
    SHOW_ERROR,
    NO_UI,
}

/** A grounded fact suitable for presentation; it is not a commerce command. */
data class GroundedEvidence(
    val label: String,
    val value: String,
    val source: String,
)

enum class GroundedPresentationHint {
    TEXT,
    LIST,
    COMPARISON,
    RANKING,
    INSIGHT,
}

/**
 * Domain-side result consumed by the planner. A response is grounded only when
 * its evidence/facts have already been produced by the runtime pipeline.
 */
data class GroundedResult(
    val status: IntelligenceResponseStatus,
    val answer: String,
    val evidence: List<GroundedEvidence> = emptyList(),
    val presentationHint: GroundedPresentationHint = GroundedPresentationHint.TEXT,
    val missingInputs: List<String> = emptyList(),
    val clarificationOptions: List<String> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val operationId: String? = null,
    val limitations: List<String> = emptyList(),
)

/** Converts runtime output at the domain boundary, before any A2UI mapping. */
fun IntelligenceResponse.toGroundedResult(
    presentationHint: GroundedPresentationHint = inferPresentationHint(this),
    requiresConfirmation: Boolean = status == IntelligenceResponseStatus.NEEDS_CLARIFICATION &&
        plan.any { it.contains("mutation", ignoreCase = true) },
): GroundedResult = GroundedResult(
    status = status,
    answer = answer,
    evidence = buildList {
        factsUsed.forEach { add(GroundedEvidence("Fato", it, "LOCAL_FACTS")) }
        analyticsUsed.forEach { add(GroundedEvidence("Cálculo", it, "DETERMINISTIC_ANALYTICS")) }
        knowledgeUsed.forEach { add(GroundedEvidence("Fonte", it, "APPROVED_KNOWLEDGE")) }
    },
    presentationHint = presentationHint,
    requiresConfirmation = requiresConfirmation,
    limitations = limitations,
)

private fun inferPresentationHint(response: IntelligenceResponse): GroundedPresentationHint {
    val plan = response.plan.joinToString(" ").lowercase()
    return when {
        "sort" in plan || "ranking" in plan || "lowest" in plan -> GroundedPresentationHint.RANKING
        "compare" in plan || "trend" in plan -> GroundedPresentationHint.COMPARISON
        "list" in plan || "receivable" in plan || "stock" in plan -> GroundedPresentationHint.LIST
        response.analyticsUsed.size > 1 -> GroundedPresentationHint.INSIGHT
        else -> GroundedPresentationHint.TEXT
    }
}

sealed interface UiDecision {
    val kind: UiDecisionKind

    data class Text(val value: String) : UiDecision {
        override val kind: UiDecisionKind = UiDecisionKind.TEXT
    }

    data class CreateSurface(
        val semanticType: UiSurfaceSemanticType,
        val title: String,
        val result: GroundedResult,
    ) : UiDecision {
        override val kind: UiDecisionKind = UiDecisionKind.CREATE_SURFACE
    }

    data class UpdateSurface(
        val surfaceId: String,
        val semanticType: UiSurfaceSemanticType,
        val result: GroundedResult,
    ) : UiDecision {
        override val kind: UiDecisionKind = UiDecisionKind.UPDATE_SURFACE
    }

    data class RequestInput(
        val fields: List<String>,
        val prompt: String,
    ) : UiDecision {
        override val kind: UiDecisionKind = UiDecisionKind.REQUEST_INPUT
    }

    data class RequestClarification(
        val prompt: String,
        val options: List<String> = emptyList(),
    ) : UiDecision {
        override val kind: UiDecisionKind = UiDecisionKind.REQUEST_CLARIFICATION
    }

    data class RequestConfirmation(
        val prompt: String,
        val operationId: String?,
    ) : UiDecision {
        override val kind: UiDecisionKind = UiDecisionKind.REQUEST_CONFIRMATION
    }

    data class ShowResult(val result: GroundedResult) : UiDecision {
        override val kind: UiDecisionKind = UiDecisionKind.SHOW_RESULT
    }

    data class ShowError(
        val message: String,
        val status: IntelligenceResponseStatus,
    ) : UiDecision {
        override val kind: UiDecisionKind = UiDecisionKind.SHOW_ERROR
    }

    data object NoUi : UiDecision {
        override val kind: UiDecisionKind = UiDecisionKind.NO_UI
    }
}

interface UiPlannerPort {
    suspend fun plan(context: UiContext, result: GroundedResult): UiDecision
}

/**
 * Deterministic fallback policy. It decides semantic presentation only; A2UI
 * composition and Compose rendering remain outside this module.
 */
@Singleton
class DeterministicUiPlanner @Inject constructor() : UiPlannerPort {
    override suspend fun plan(context: UiContext, result: GroundedResult): UiDecision {
        if (result.missingInputs.isNotEmpty()) {
            return UiDecision.RequestInput(
                fields = result.missingInputs,
                prompt = result.answer.ifBlank { "Preciso de mais um detalhe para continuar." },
            )
        }
        if (result.requiresConfirmation) {
            return UiDecision.RequestConfirmation(
                prompt = result.answer.ifBlank { "Confirma esta operação?" },
                operationId = result.operationId,
            )
        }
        when (result.status) {
            IntelligenceResponseStatus.NEEDS_CLARIFICATION,
            IntelligenceResponseStatus.AMBIGUOUS_ENTITY,
            -> return UiDecision.RequestClarification(
                prompt = result.answer.ifBlank { "Qual opção você quis dizer?" },
                options = result.clarificationOptions,
            )

            IntelligenceResponseStatus.INSUFFICIENT_DATA,
            IntelligenceResponseStatus.KNOWLEDGE_UNAVAILABLE,
            IntelligenceResponseStatus.TOOL_UNAVAILABLE,
            IntelligenceResponseStatus.UNSUPPORTED,
            IntelligenceResponseStatus.ERROR,
            -> return UiDecision.ShowError(
                message = result.answer.ifBlank { "Não consegui confirmar essa resposta." },
                status = result.status,
            )

            IntelligenceResponseStatus.ANSWERED -> Unit
        }

        if (result.answer.isBlank() && result.evidence.isEmpty()) return UiDecision.NoUi
        if (result.evidence.isEmpty()) return UiDecision.Text(result.answer)

        val semanticType = semanticTypeFor(result.presentationHint)
        if (result.presentationHint == GroundedPresentationHint.TEXT) {
            return UiDecision.Text(result.answer)
        }
        if (context.activeSurfaceId != null && context.activeSurfaceSemanticType == semanticType) {
            return UiDecision.UpdateSurface(
                surfaceId = context.activeSurfaceId,
                semanticType = semanticType,
                result = result,
            )
        }
        return UiDecision.CreateSurface(
            semanticType = semanticType,
            title = titleFor(semanticType),
            result = result,
        )
    }

    private fun semanticTypeFor(hint: GroundedPresentationHint): UiSurfaceSemanticType = when (hint) {
        GroundedPresentationHint.RANKING -> UiSurfaceSemanticType.RECEIVABLE_RANKING
        GroundedPresentationHint.COMPARISON -> UiSurfaceSemanticType.COMPARISON
        GroundedPresentationHint.LIST -> UiSurfaceSemanticType.RESULT_LIST
        GroundedPresentationHint.INSIGHT -> UiSurfaceSemanticType.INSIGHT
        GroundedPresentationHint.TEXT -> UiSurfaceSemanticType.INSIGHT
    }

    private fun titleFor(type: UiSurfaceSemanticType): String = when (type) {
        UiSurfaceSemanticType.RECEIVABLE_RANKING -> "Ranking de recebimentos"
        UiSurfaceSemanticType.COMPARISON -> "Comparação"
        UiSurfaceSemanticType.RESULT_LIST -> "Resultado"
        UiSurfaceSemanticType.INSIGHT -> "Análise do TINO"
        else -> "Resultado do TINO"
    }
}

/** A replaceable planner boundary with deterministic safe fallback. */
class FallbackUiPlanner(
    private val primary: UiPlannerPort,
    private val fallback: UiPlannerPort = DeterministicUiPlanner(),
) : UiPlannerPort {
    override suspend fun plan(context: UiContext, result: GroundedResult): UiDecision =
        runCatching { primary.plan(context, result) }
            .getOrElse { fallback.plan(context, result) }
}
