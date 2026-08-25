package com.tino.app.domain.intelligence.planning

import com.tino.app.domain.intelligence.IntelligenceRequest

data class PlannerEvaluationCase(
    val id: String,
    val utterance: String,
    val expectedGoal: IntelligenceGoal,
    val expectedTools: List<String>,
)

/** Small, versioned corpus for comparing planners without executing commerce tools. */
object IntelligencePlannerCorpus {
    val gate32: List<PlannerEvaluationCase> = listOf(
        PlannerEvaluationCase(
            id = "recent_payment_among_debtors",
            utterance = "Dos clientes que ainda estão me devendo, quem pagou recentemente?",
            expectedGoal = IntelligenceGoal.RECENT_PAYMENTS,
            expectedTools = listOf(
                "get_receivables",
                "get_customer_payment_history",
                "filter_recent_payments",
            ),
        ),
        PlannerEvaluationCase(
            id = "lowest_stock_velocity",
            utterance = "Qual produto está com estoque baixo e vem caindo mais rápido?",
            expectedGoal = IntelligenceGoal.STOCK_RISK,
            expectedTools = listOf(
                "search_product",
                "get_product_stock",
                "get_stock_movements",
                "calculate_stock_velocity",
                "calculate_reorder_signal",
            ),
        ),
        PlannerEvaluationCase(
            id = "pix_and_total_trend",
            utterance = "Estou recebendo mais no Pix, mas meu total recebido aumentou ou diminuiu?",
            expectedGoal = IntelligenceGoal.PAYMENT_METHOD_AND_TREND,
            expectedTools = listOf(
                "get_financial_summary",
                "get_payment_method_breakdown",
                "compare_financial_periods",
            ),
        ),
        PlannerEvaluationCase(
            id = "weekly_comparison",
            utterance = "Essa semana entrou mais que a semana passada?",
            expectedGoal = IntelligenceGoal.PERIOD_COMPARISON,
            expectedTools = listOf(
                "get_financial_summary",
                "compare_financial_periods",
            ),
        ),
    )
}

data class PlannerEvaluationMetrics(
    val plannerId: String,
    val totalCases: Int,
    val planCorrectCount: Int,
    val toolSelectionCorrectCount: Int,
    val toolOrderingCorrectCount: Int,
    val fallbackCount: Int,
    val validationRejectionCount: Int,
    val groundingReadyCount: Int,
    val errorCount: Int,
    val averageLatencyMs: Long,
) {
    val planCorrectRate: Double get() = rate(planCorrectCount)
    val toolSelectionCorrectRate: Double get() = rate(toolSelectionCorrectCount)
    val toolOrderingCorrectRate: Double get() = rate(toolOrderingCorrectCount)
    val fallbackRate: Double get() = rate(fallbackCount)
    val validationRejectionRate: Double get() = rate(validationRejectionCount)
    val groundingReadyRate: Double get() = rate(groundingReadyCount)

    private fun rate(value: Int): Double = if (totalCases == 0) 0.0 else value.toDouble() / totalCases
}

data class PlannerEvaluationReport(
    val corpusId: String,
    val byPlanner: Map<String, PlannerEvaluationMetrics>,
)

class PlannerAbEvaluator(
    private val validator: IntelligencePlanValidator,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun evaluate(
        corpus: List<PlannerEvaluationCase> = IntelligencePlannerCorpus.gate32,
        planners: Map<String, PlannerPort>,
    ): PlannerEvaluationReport {
        val results = planners.mapValues { (label, planner) ->
            evaluatePlanner(label, planner, corpus)
        }
        return PlannerEvaluationReport(corpusId = "gate-3.2", byPlanner = results)
    }

    private suspend fun evaluatePlanner(
        label: String,
        planner: PlannerPort,
        corpus: List<PlannerEvaluationCase>,
    ): PlannerEvaluationMetrics {
        var planCorrect = 0
        var toolSelectionCorrect = 0
        var toolOrderingCorrect = 0
        var fallback = 0
        var validationRejected = 0
        var groundingReady = 0
        var errors = 0
        var latencyTotal = 0L

        corpus.forEach { testCase ->
            val started = now()
            try {
                val plan = planner.plan(
                    IntelligenceRequest(
                        requestId = "eval-${testCase.id}",
                        sessionId = "eval-gate-3-2",
                        utterance = testCase.utterance,
                    ),
                )
                latencyTotal += (now() - started).coerceAtLeast(0L)
                val expectedTools = testCase.expectedTools
                val actualTools = plan.steps.map { it.toolName }
                val goalCorrect = plan.goal == testCase.expectedGoal
                val selectionCorrect = actualTools.toSet() == expectedTools.toSet() &&
                    actualTools.size == expectedTools.size
                val orderingCorrect = actualTools == expectedTools
                val validation = validator.validate(plan)
                if (goalCorrect && orderingCorrect) planCorrect++
                if (selectionCorrect) toolSelectionCorrect++
                if (orderingCorrect) toolOrderingCorrect++
                if (plan.plannerId.endsWith("-fallback")) fallback++
                if (!validation.isValid) validationRejected++
                if (goalCorrect && orderingCorrect && validation.isValid) groundingReady++
            } catch (_: Exception) {
                latencyTotal += (now() - started).coerceAtLeast(0L)
                errors++
            }
        }
        return PlannerEvaluationMetrics(
            plannerId = label,
            totalCases = corpus.size,
            planCorrectCount = planCorrect,
            toolSelectionCorrectCount = toolSelectionCorrect,
            toolOrderingCorrectCount = toolOrderingCorrect,
            fallbackCount = fallback,
            validationRejectionCount = validationRejected,
            groundingReadyCount = groundingReady,
            errorCount = errors,
            averageLatencyMs = if (corpus.isEmpty()) 0L else latencyTotal / corpus.size,
        )
    }
}
