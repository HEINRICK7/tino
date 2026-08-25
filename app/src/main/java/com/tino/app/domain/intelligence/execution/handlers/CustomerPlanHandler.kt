package com.tino.app.domain.intelligence.execution.handlers

import com.tino.app.domain.intelligence.execution.BasePlanHandler
import com.tino.app.domain.intelligence.execution.PlanHandlerContext
import com.tino.app.domain.intelligence.*
import com.tino.app.domain.intelligence.clarification.IntelligenceClarificationPolicy
import com.tino.app.domain.intelligence.planning.IntelligenceGoal
import com.tino.app.domain.intelligence.planning.IntelligencePlan
import java.time.Clock
import java.time.Duration
import java.util.Locale

class CustomerPlanHandler(
    context: PlanHandlerContext,
) : BasePlanHandler(context) {
    override fun supports(goal: IntelligenceGoal): Boolean = goal in setOf(
        IntelligenceGoal.RECEIVABLES,
        IntelligenceGoal.PAYMENT_BEHAVIOR,
        IntelligenceGoal.RECENT_PAYMENTS,
    )

    override suspend fun execute(
        request: IntelligenceRequest,
        plan: IntelligencePlan,
        normalized: String,
    ): IntelligenceResponse = when (plan.goal) {
        IntelligenceGoal.RECEIVABLES -> answerReceivables(normalized)
        IntelligenceGoal.PAYMENT_BEHAVIOR -> answerPaymentBehavior(request, normalized)
        IntelligenceGoal.RECENT_PAYMENTS -> answerRecentPayments(request)
        else -> unsupported()
    }

    private suspend fun answerReceivables(normalized: String): IntelligenceResponse {
        val receivables = facts.receivables().filter { it.outstandingCents > 0 }.sortedByDescending { it.outstandingCents }
        if (receivables.isEmpty()) return insufficient("Não encontrei recebíveis em aberto.", "get_receivables")
        val top = receivables.take(3).joinToString { "${it.customerName} (${money(it.outstandingCents)})" }
        val behavior = if (normalized.contains("demora") || normalized.contains("pagar mais rapido") || normalized.contains("paga mais rapido") || normalized.contains("tempo para pagar")) {
            receivables.take(3).mapNotNull { receivable ->
                val result = analytics.calculatePaymentBehavior(facts.paymentEvents(receivable.customerId))
                result.averagePaymentDelayDays?.let { receivable.customerName to it }
            }.sortedBy { it.second }
        } else emptyList()
        val behaviorText = behavior.takeIf { it.isNotEmpty() }?.joinToString { "${it.first}: ${"%.1f".format(Locale.US, it.second)} dias" }
        return IntelligenceResponse(
            status = IntelligenceResponseStatus.ANSWERED,
            answer = buildString {
                append("Quem mais está devendo: $top.")
                behaviorText?.let { append(" Entre os que têm histórico, quem costuma pagar mais rápido é ${behavior.first().first} (${behavior.first().second.formatDays()}). Histórico: $it.") }
            },
            plan = if (behavior.isEmpty()) listOf("get_receivables", "ordenar por saldo") else listOf("get_receivables", "get_customer_payment_history", "calculate_customer_payment_behavior", "ordenar por comportamento"),
            toolCalls = if (behavior.isEmpty()) listOf(call("get_receivables", 1)) else listOf(call("get_receivables", 1), call("get_customer_payment_history", 2), call("calculate_customer_payment_behavior", 3)),
            factsUsed = listOf("credit_entries", "customer_balances"),
            analyticsUsed = behavior.takeIf { it.isNotEmpty() }?.let { listOf("average_payment_delay_days") }.orEmpty(),
            confidence = if (behavior.isEmpty()) 0.96 else 0.88,
            limitations = if (behavior.isEmpty()) listOf("Não há histórico de pagamento suficiente para comparar velocidade.") else emptyList(),
        )
    }

    private suspend fun answerPaymentBehavior(request: IntelligenceRequest, normalized: String): IntelligenceResponse {
        val reference = extractCustomerReference(request.utterance, normalized) ?: request.resolvedContext["customer"]
        if (reference != null) {
            return when (val resolved = facts.resolveCustomer(reference)) {
                is IntelligenceEntityResolution.Ambiguous -> IntelligenceResponse(
                    status = IntelligenceResponseStatus.AMBIGUOUS_ENTITY,
                    answer = "Encontrei mais de um cliente com esse nome: ${resolved.values.joinToString { it.name }}.",
                    plan = listOf("search_customer"),
                    limitations = listOf("Escolha um cliente antes de consultar o histórico."),
                )
                IntelligenceEntityResolution.NotFound -> insufficient("Não encontrei esse cliente no comércio.", "search_customer")
                is IntelligenceEntityResolution.Resolved -> {
                    val behavior = analytics.calculatePaymentBehavior(facts.paymentEvents(resolved.value.id))
                    val days = behavior.averagePaymentDelayDays
                    if (days == null) insufficient("Ainda não há histórico suficiente para calcular o tempo médio de pagamento de ${resolved.value.name}.", "get_customer_payment_history") else IntelligenceResponse(
                        status = IntelligenceResponseStatus.ANSWERED,
                        answer = "${resolved.value.name} costuma pagar em aproximadamente ${days.formatDays()}, com ${behavior.paymentCount} pagamento(s) registrado(s).",
                        plan = listOf("search_customer", "get_customer_payment_history", "calculate_customer_payment_behavior"),
                        toolCalls = listOf(call("search_customer", 1), call("get_customer_payment_history", 2), call("calculate_customer_payment_behavior", 3)),
                        factsUsed = listOf("credit_entries"),
                        analyticsUsed = listOf("average_payment_delay_days"),
                        confidence = 0.86,
                    )
                }
            }
        }
        return answerReceivables(normalized)
    }

    private suspend fun answerRecentPayments(request: IntelligenceRequest): IntelligenceResponse {
        val receivables = facts.receivables().filter { it.outstandingCents > 0L }
        if (receivables.isEmpty()) return insufficient("Não encontrei recebíveis em aberto.", "get_receivables")
        val cutoff = request.timestampEpochMs - Duration.ofDays(30).toMillis()
        val recent = receivables.mapNotNull { receivable ->
            facts.paymentEvents(receivable.customerId).filter { it.type == PaymentEventType.PAYMENT && it.occurredAtEpochMs >= cutoff }.maxByOrNull { it.occurredAtEpochMs }?.let { receivable to it }
        }.sortedByDescending { it.second.occurredAtEpochMs }
        val answer = if (recent.isEmpty()) "Nenhum cliente com saldo em aberto fez pagamento nos últimos 30 dias." else "Entre os que ainda estão devendo, fizeram pagamento recentemente: " + recent.joinToString { "${it.first.customerName} (${money(it.second.amountCents)})" } + "."
        return IntelligenceResponse(
            status = IntelligenceResponseStatus.ANSWERED,
            answer = answer,
            plan = listOf("get_receivables", "get_customer_payment_history", "filtrar pagamentos dos últimos 30 dias"),
            toolCalls = listOf(call("get_receivables", 1), call("get_customer_payment_history", 2)),
            factsUsed = listOf("customer_balances", "credit_entries"),
            analyticsUsed = listOf("recent_payment_filter"),
            confidence = 0.94,
        )
    }

}
