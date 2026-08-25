package com.tino.app.domain.intelligence.execution

import com.tino.app.domain.intelligence.*
import com.tino.app.domain.intelligence.clarification.IntelligenceClarificationPolicy
import com.tino.app.domain.intelligence.planning.IntelligenceGoal
import com.tino.app.domain.intelligence.planning.IntelligencePlan
import java.time.Clock
import java.util.Locale

data class PlanHandlerContext(
    val facts: IntelligenceFactsPort,
    val analytics: BusinessAnalyticsPort,
    val recommendationEngine: RecommendationEngine,
    val recommendationRepository: RecommendationRepository,
    val knowledge: KnowledgeQueryPort,
    val clock: Clock,
    val clarificationPolicy: IntelligenceClarificationPolicy,
)

interface IntelligencePlanHandler {
    fun supports(goal: IntelligenceGoal): Boolean
    suspend fun execute(
        request: IntelligenceRequest,
        plan: IntelligencePlan,
        normalized: String,
    ): IntelligenceResponse
}

abstract class BasePlanHandler(
    private val context: PlanHandlerContext,
) : IntelligencePlanHandler {
    protected val facts: IntelligenceFactsPort get() = context.facts
    protected val analytics: BusinessAnalyticsPort get() = context.analytics
    protected val recommendationEngine: RecommendationEngine get() = context.recommendationEngine
    protected val recommendationRepository: RecommendationRepository get() = context.recommendationRepository
    protected val knowledge: KnowledgeQueryPort get() = context.knowledge
    protected val clock: Clock get() = context.clock
    protected val clarificationPolicy: IntelligenceClarificationPolicy get() = context.clarificationPolicy

    protected fun unsupported() = IntelligenceResponse(
        IntelligenceResponseStatus.UNSUPPORTED,
        "Ainda não tenho dados e ferramentas suficientes para responder isso com segurança.",
        limitations = listOf("A pergunta não foi mapeada para um plano seguro de consultas."),
    )

    protected fun insufficient(message: String, tool: String) = IntelligenceResponse(
        IntelligenceResponseStatus.INSUFFICIENT_DATA,
        message,
        plan = listOf(tool),
        toolCalls = listOf(call(tool, 1)),
        limitations = listOf("A resposta não foi estimada; faltam fatos locais suficientes."),
    )

    protected fun call(name: String, order: Int) = IntelligenceToolCall(name, order, "succeeded")

    protected fun extractCustomerReference(raw: String, normalized: String): String? {
        if (normalized.contains("qual deles")) return null
        return Regex("(?:cliente|a)\\s+([a-z0-9 ]+?)(?:\\s+deve|\\s+pagou|\\s+demora|$)")
            .find(normalized)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    protected fun extractProductReference(raw: String, normalized: String): String? {
        val candidate = Regex("(?:de|do|da|produto)\\s+([a-z0-9 ]+?)(?:\\s+esta|\\s+acabando|\\s+rapido|$)")
            .find(normalized)?.groupValues?.getOrNull(1)?.trim()
        return candidate?.takeIf { it.isNotBlank() } ?: if (normalized.contains("cafe")) "café" else null
    }

    protected fun money(cents: Long): String =
        java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0)

    protected fun formatPercent(value: Double): String =
        "%.1f%%".format(Locale.US, kotlin.math.abs(value))

    protected fun Double.formatDays(): String =
        if (this < 1.0) "menos de 1 dia" else "%.1f dias".format(Locale.US, this)

    protected fun Long.formatDays(): String =
        if (this < 1L) "menos de 1 dia" else "$this dias"
}

class IntelligenceHandlerRegistry(
    private val handlers: List<IntelligencePlanHandler>,
) {
    fun handlerFor(goal: IntelligenceGoal): IntelligencePlanHandler? =
        handlers.firstOrNull { it.supports(goal) }

    suspend fun execute(
        request: IntelligenceRequest,
        plan: IntelligencePlan,
        normalized: String,
    ): IntelligenceResponse =
        handlerFor(plan.goal)?.execute(request, plan, normalized)
            ?: IntelligenceResponse(
                status = IntelligenceResponseStatus.UNSUPPORTED,
                answer = "Ainda não tenho dados e ferramentas suficientes para responder isso com segurança.",
                limitations = listOf("Nenhum handler seguro foi registrado para este objetivo."),
            )
}
