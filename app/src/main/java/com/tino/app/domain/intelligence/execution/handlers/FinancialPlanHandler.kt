package com.tino.app.domain.intelligence.execution.handlers

import com.tino.app.domain.intelligence.execution.BasePlanHandler
import com.tino.app.domain.intelligence.execution.PlanHandlerContext
import com.tino.app.domain.intelligence.*
import com.tino.app.domain.intelligence.clarification.IntelligenceClarificationPolicy
import com.tino.app.domain.intelligence.planning.IntelligenceGoal
import com.tino.app.domain.intelligence.planning.IntelligencePlan
import com.tino.app.domain.finance.FinancialPeriod
import java.time.Clock
import java.time.Duration
import java.util.Locale

class FinancialPlanHandler(
    context: PlanHandlerContext,
) : BasePlanHandler(context) {
    override fun supports(goal: IntelligenceGoal): Boolean = goal in setOf(
        IntelligenceGoal.PERIOD_COMPARISON,
        IntelligenceGoal.PAYMENT_METHOD_BREAKDOWN,
        IntelligenceGoal.PAYMENT_METHOD_AND_TREND,
    )

    override suspend fun execute(
        request: IntelligenceRequest,
        plan: IntelligencePlan,
        normalized: String,
    ): IntelligenceResponse = when (plan.goal) {
        IntelligenceGoal.PERIOD_COMPARISON -> answerPeriodComparison()
        IntelligenceGoal.PAYMENT_METHOD_BREAKDOWN -> answerPaymentMethodBreakdown()
        IntelligenceGoal.PAYMENT_METHOD_AND_TREND -> answerPaymentMethodAndTrend()
        else -> unsupported()
    }

    private suspend fun answerPeriodComparison(): IntelligenceResponse {
        val current = FinancialPeriod.thisWeek(clock)
        val previous = FinancialPeriod(current.startAt - Duration.ofDays(7).toMillis(), current.endAtExclusive - Duration.ofDays(7).toMillis(), clock.zone.id)
        val currentSummary = facts.financialSummary(current)
        val previousSummary = facts.financialSummary(previous)
        val variation = analytics.compareFinancialPeriods(currentSummary, previousSummary) ?: return insufficient("Ainda não há base suficiente para comparar as semanas.", "compare_financial_periods")
        val direction = if (variation >= 0) "acima" else "abaixo"
        return IntelligenceResponse(
            status = IntelligenceResponseStatus.ANSWERED,
            answer = "Nesta semana entrou ${money(currentSummary.receivedTotalCents)}, ${formatPercent(variation)} $direction da semana passada (${money(previousSummary.receivedTotalCents)}).",
            plan = listOf("get_financial_summary", "compare_financial_periods"),
            toolCalls = listOf(call("get_financial_summary", 1), call("compare_financial_periods", 2)),
            factsUsed = listOf("sales", "direct_receipts", "credit_entries"),
            analyticsUsed = listOf("period_variation_percent"),
            confidence = 0.98,
        )
    }

    private suspend fun answerPaymentMethodBreakdown(): IntelligenceResponse {
        val summary = facts.financialSummary(FinancialPeriod.today(clock))
        val values = listOf("PIX" to summary.receivedPixCents, "dinheiro" to summary.receivedCashCents, "cartão" to summary.receivedCardCents).sortedByDescending { it.second }
        val top = values.firstOrNull { it.second > 0L } ?: return insufficient("Ainda não há recebimentos para comparar as formas de pagamento.", "get_payment_method_breakdown")
        return IntelligenceResponse(
            status = IntelligenceResponseStatus.ANSWERED,
            answer = "Hoje você está recebendo mais em ${top.first}: ${money(top.second)}. " + values.joinToString(", ") { "${it.first} ${money(it.second)}" } + ".",
            plan = listOf("get_financial_summary", "get_payment_method_breakdown"),
            toolCalls = listOf(call("get_financial_summary", 1), call("get_payment_method_breakdown", 2)),
            factsUsed = listOf("direct_receipts", "sales"),
            analyticsUsed = listOf("payment_method_breakdown"),
            confidence = 0.98,
        )
    }

    private suspend fun answerPaymentMethodAndTrend(): IntelligenceResponse {
        val today = facts.financialSummary(FinancialPeriod.today(clock))
        val current = FinancialPeriod.thisWeek(clock)
        val previous = FinancialPeriod(current.startAt - Duration.ofDays(7).toMillis(), current.endAtExclusive - Duration.ofDays(7).toMillis(), clock.zone.id)
        val currentWeek = facts.financialSummary(current)
        val previousWeek = facts.financialSummary(previous)
        val variation = analytics.compareFinancialPeriods(currentWeek, previousWeek)
        val direction = variation?.let { if (it >= 0) "aumentou" else "diminuiu" } ?: "não pôde ser comparado"
        val method = if (today.receivedPixCents >= today.receivedCashCents) "PIX" else "dinheiro"
        return IntelligenceResponse(
            status = IntelligenceResponseStatus.ANSWERED,
            answer = "Hoje o maior recebimento entre PIX e dinheiro está no $method. No total da semana, o recebido $direction em relação à semana passada.",
            plan = listOf("get_financial_summary", "get_payment_method_breakdown", "compare_financial_periods"),
            toolCalls = listOf(call("get_financial_summary", 1), call("get_payment_method_breakdown", 2), call("compare_financial_periods", 3)),
            factsUsed = listOf("direct_receipts", "sales", "credit_entries"),
            analyticsUsed = listOf("payment_method_breakdown", "period_variation_percent"),
            confidence = if (variation == null) 0.78 else 0.96,
            limitations = if (variation == null) listOf("Não há dados anteriores suficientes para calcular a variação semanal.") else emptyList(),
        )
    }


}
