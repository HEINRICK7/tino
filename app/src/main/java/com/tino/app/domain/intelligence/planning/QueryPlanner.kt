package com.tino.app.domain.intelligence.planning

import com.tino.app.domain.intelligence.IntelligenceRequest
import com.tino.app.domain.intelligence.IntelligenceTextNormalizer
import com.tino.app.domain.intelligence.IntelligenceToolDefinition
import com.tino.app.domain.intelligence.IntelligenceToolKind
import com.tino.app.domain.intelligence.TinoIntelligenceToolRegistry

import com.tino.app.domain.finance.FinancialPeriod
import java.text.Normalizer
import java.time.Clock
import java.time.Duration
import java.util.Locale

enum class IntelligenceGoal {
    KNOWLEDGE,
    PAYMENT_METHOD_BREAKDOWN,
    PAYMENT_METHOD_AND_TREND,
    PERIOD_COMPARISON,
    RECENT_PAYMENTS,
    LOWEST_STOCK,
    STOCK_TREND,
    STOCK_RISK,
    REPLENISHMENT_RECOMMENDATION,
    PAYMENT_BEHAVIOR,
    RECEIVABLES,
    INVENTORY,
    UNSUPPORTED,
}

data class IntelligencePlanStep(
    val toolName: String,
    val purpose: String,
)

data class IntelligencePlan(
    val goal: IntelligenceGoal,
    val steps: List<IntelligencePlanStep>,
    val requiresClarification: Boolean = false,
    val confidence: Float = if (steps.isEmpty()) 0.2f else 0.9f,
    val plannerId: String = "deterministic",
    val fallbackReason: String? = null,
)

interface PlannerPort {
    val id: String
    suspend fun plan(request: IntelligenceRequest): IntelligencePlan
}

interface IntelligenceQueryPlanner : PlannerPort

class DeterministicIntelligenceQueryPlanner : IntelligenceQueryPlanner {
    override val id: String = "deterministic"

    override suspend fun plan(request: IntelligenceRequest): IntelligencePlan {
        val text = IntelligenceTextNormalizer.normalize(request.utterance)
        val goal = when {
            asksKnowledge(text) -> IntelligenceGoal.KNOWLEDGE
            asksPaymentMethodAndTrend(text) -> IntelligenceGoal.PAYMENT_METHOD_AND_TREND
            asksPaymentMethodBreakdown(text) -> IntelligenceGoal.PAYMENT_METHOD_BREAKDOWN
            asksPeriodComparison(text) -> IntelligenceGoal.PERIOD_COMPARISON
            asksRecentPayment(text) -> IntelligenceGoal.RECENT_PAYMENTS
            asksReplenishment(text) -> IntelligenceGoal.REPLENISHMENT_RECOMMENDATION
            asksStockRisk(text) -> IntelligenceGoal.STOCK_RISK
            asksLowestStock(text) -> IntelligenceGoal.LOWEST_STOCK
            asksStockTrend(text) -> IntelligenceGoal.STOCK_TREND
            asksPaymentBehavior(text) -> IntelligenceGoal.PAYMENT_BEHAVIOR
            asksReceivables(text) -> IntelligenceGoal.RECEIVABLES
            asksInventory(text) -> IntelligenceGoal.INVENTORY
            else -> IntelligenceGoal.UNSUPPORTED
        }
        val steps = stepsFor(goal)
        return IntelligencePlan(
            goal = goal,
            steps = steps,
            requiresClarification = goal == IntelligenceGoal.INVENTORY && !hasProductReference(text),
            confidence = when {
                goal == IntelligenceGoal.UNSUPPORTED -> 0.2f
                goal == IntelligenceGoal.INVENTORY && !hasProductReference(text) -> 0.7f
                else -> 0.9f
            },
        )
    }

    private fun stepsFor(goal: IntelligenceGoal): List<IntelligencePlanStep> = when (goal) {
        IntelligenceGoal.KNOWLEDGE -> steps("search_knowledge" to "buscar fonte aprovada")
        IntelligenceGoal.PAYMENT_METHOD_BREAKDOWN -> steps(
            "get_financial_summary" to "ler recebimentos",
            "get_payment_method_breakdown" to "comparar formas de pagamento",
        )
        IntelligenceGoal.PAYMENT_METHOD_AND_TREND -> steps(
            "get_financial_summary" to "ler recebimentos e períodos",
            "get_payment_method_breakdown" to "comparar formas de pagamento",
            "compare_financial_periods" to "calcular tendência do total",
        )
        IntelligenceGoal.PERIOD_COMPARISON -> steps(
            "get_financial_summary" to "ler períodos",
            "compare_financial_periods" to "calcular variação",
        )
        IntelligenceGoal.RECENT_PAYMENTS -> steps(
            "get_receivables" to "filtrar clientes em aberto",
            "get_customer_payment_history" to "ler históricos",
            "filter_recent_payments" to "filtrar últimos 30 dias",
        )
        IntelligenceGoal.LOWEST_STOCK -> steps(
            "search_product" to "ler catálogo",
            "get_product_stock" to "ler saldos",
            "calculate_lowest_stock" to "ordenar saldos",
        )
        IntelligenceGoal.STOCK_TREND -> steps(
            "get_product_stock" to "ler saldos atuais",
            "get_stock_movements" to "ler movimentos",
            "compare_stock_levels" to "comparar com ontem",
        )
        IntelligenceGoal.STOCK_RISK -> steps(
            "search_product" to "ler catálogo",
            "get_product_stock" to "ler saldos",
            "get_stock_movements" to "ler histórico de saída",
            "calculate_stock_velocity" to "calcular velocidade de queda",
            "calculate_reorder_signal" to "classificar risco de reposição",
        )
        IntelligenceGoal.REPLENISHMENT_RECOMMENDATION -> steps(
            "search_product" to "ler catálogo",
            "get_product_stock" to "ler saldos",
            "get_stock_movements" to "ler histórico de saída",
            "calculate_stock_velocity" to "calcular demanda recente",
            "generate_replenishment_recommendations" to "gerar recomendações explicáveis",
        )
        IntelligenceGoal.PAYMENT_BEHAVIOR -> steps(
            "search_customer" to "resolver cliente",
            "get_customer_payment_history" to "ler histórico",
            "calculate_customer_payment_behavior" to "calcular atraso",
        )
        IntelligenceGoal.RECEIVABLES -> steps(
            "get_receivables" to "ler recebíveis",
            "sort_receivables" to "ordenar por saldo",
        )
        IntelligenceGoal.INVENTORY -> steps(
            "search_product" to "resolver produto",
            "get_product_stock" to "ler estoque",
            "get_stock_movements" to "ler movimentos",
            "calculate_stock_velocity" to "calcular velocidade",
            "calculate_stock_coverage" to "calcular cobertura",
        )
        IntelligenceGoal.UNSUPPORTED -> emptyList()
    }

    private fun steps(vararg values: Pair<String, String>) = values.map { (tool, purpose) ->
        IntelligencePlanStep(tool, purpose)
    }

    private fun asksReceivables(text: String) =
        text.contains("devendo") || text.contains("deve") || text.contains("receber") || text.contains("fiado")

    private fun asksPaymentBehavior(text: String) =
        text.contains("demora") || text.contains("pagar mais rapido") ||
            text.contains("paga mais rapido") || text.contains("tempo para pagar")

    private fun asksPaymentMethodBreakdown(text: String) =
        (text.contains("pix") && text.contains("dinheiro")) ||
            (text.contains("forma de pagamento") && (text.contains("mais") || text.contains("receb")))

    private fun asksPaymentMethodAndTrend(text: String) =
        text.contains("pix") && text.contains("total recebido") &&
            (text.contains("aument") || text.contains("diminui") || text.contains("melhor"))

    private fun asksRecentPayment(text: String) =
        text.contains("pagamento recentemente") || text.contains("fez pagamento") || text.contains("pagou recentemente")

    private fun asksReplenishment(text: String) =
        text.contains("preciso comprar") || text.contains("tenho que comprar") ||
            text.contains("o que comprar") || text.contains("o que devo comprar") ||
            text.contains("preciso repor") || text.contains("tenho que repor") ||
            text.contains("para repor") || text.contains("reposição") ||
            text.contains("repor estoque") || text.contains("o que acabou") ||
            text.contains("o que está faltando")

    private fun asksLowestStock(text: String) =
        text.contains("menor estoque") || text.contains("menos estoque")

    private fun asksStockRisk(text: String) =
        text.contains("estoque baixo") && (text.contains("caindo") || text.contains("rapido"))

    private fun asksStockTrend(text: String) =
        text.contains("estoque") && (text.contains("pior que ontem") || text.contains("melhor que ontem") || text.contains("ontem"))

    private fun asksPeriodComparison(text: String) =
        text.contains("semana") && (text.contains("melhor") || text.contains("compar") || text.contains("passada"))

    private fun asksInventory(text: String) =
        text.contains("estoque") || text.contains("acabando") || text.contains("repor") || text.contains("cafe")

    private fun asksKnowledge(text: String) =
        text.contains("o que significa") || text.contains("explique") || text.contains("cfop") || text.contains("ncm")

    private fun hasProductReference(text: String): Boolean =
        text.contains("cafe") || Regex("(?:de|do|da|produto)\\s+[a-z0-9 ]+").containsMatchIn(text)
}

data class IntelligencePlanValidation(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
)

interface IntelligencePlanValidator {
    fun validate(plan: IntelligencePlan): IntelligencePlanValidation
}

class DeterministicIntelligencePlanValidator(
    private val registry: List<IntelligenceToolDefinition> = TinoIntelligenceToolRegistry.all,
    private val maxSteps: Int = 8,
) : IntelligencePlanValidator {
    override fun validate(plan: IntelligencePlan): IntelligencePlanValidation {
        val errors = buildList {
            if (plan.steps.size > maxSteps) {
                add("O plano excede o limite seguro de $maxSteps etapas.")
            }
            plan.steps.forEach { step ->
                val tool = registry.firstOrNull { it.name == step.toolName }
                if (tool == null) {
                    add("A ferramenta '${step.toolName}' não está registrada.")
                } else if (tool.kind == IntelligenceToolKind.MUTATION) {
                    add("O plano não pode executar mutações sem confirmação explícita.")
                }
            }
        }
        return IntelligencePlanValidation(errors.isEmpty(), errors)
    }
}
