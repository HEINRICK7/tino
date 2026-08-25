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

class InventoryPlanHandler(
    context: PlanHandlerContext,
) : BasePlanHandler(context) {
    override fun supports(goal: IntelligenceGoal): Boolean = goal in setOf(
        IntelligenceGoal.LOWEST_STOCK,
        IntelligenceGoal.STOCK_RISK,
        IntelligenceGoal.STOCK_TREND,
        IntelligenceGoal.REPLENISHMENT_RECOMMENDATION,
        IntelligenceGoal.INVENTORY,
    )

    override suspend fun execute(
        request: IntelligenceRequest,
        plan: IntelligencePlan,
        normalized: String,
    ): IntelligenceResponse = when (plan.goal) {
        IntelligenceGoal.LOWEST_STOCK -> answerLowestStock()
        IntelligenceGoal.STOCK_RISK -> answerStockRisk(request)
        IntelligenceGoal.STOCK_TREND -> answerStockTrend(request)
        IntelligenceGoal.REPLENISHMENT_RECOMMENDATION -> answerReplenishment(request)
        IntelligenceGoal.INVENTORY -> answerInventory(request, normalized)
        else -> unsupported()
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

    private suspend fun answerLowestStock(): IntelligenceResponse {
        val lowest = facts.products().minByOrNull { it.stockQuantity } ?: return insufficient("Ainda não há produtos cadastrados para comparar o estoque.", "search_product")
        return IntelligenceResponse(
            status = IntelligenceResponseStatus.ANSWERED,
            answer = "O menor estoque é o de ${lowest.name}: ${lowest.stockQuantity} unidade(s).",
            plan = listOf("search_product", "get_product_stock", "ordenar por menor estoque"),
            toolCalls = listOf(call("search_product", 1), call("get_product_stock", 2)),
            factsUsed = listOf("products"),
            analyticsUsed = listOf("lowest_stock"),
            confidence = 0.99,
        )
    }

    private suspend fun answerStockRisk(request: IntelligenceRequest): IntelligenceResponse {
        val risky = facts.products()
            .map { product ->
                product to analytics.calculateStockVelocity(
                    product,
                    facts.stockMovements(product.id),
                    request.timestampEpochMs,
                )
            }
            .filter { (product, velocity) -> product.stockQuantity <= 10 && velocity.unitsLastPeriod > 0 }
            .sortedByDescending { it.second.unitsLastPeriod }
        val top = risky.firstOrNull() ?: return insufficient("Ainda não encontrei produto com estoque baixo e saída suficiente para medir risco.", "calculate_reorder_signal")
        return IntelligenceResponse(
            status = IntelligenceResponseStatus.ANSWERED,
            answer = "O maior risco é ${top.first.name}: ${top.first.stockQuantity} unidade(s) em estoque e ${top.second.unitsLastPeriod} saída(s) nos últimos 30 dias.",
            plan = listOf("search_product", "get_product_stock", "get_stock_movements", "calculate_stock_velocity", "calculate_reorder_signal"),
            toolCalls = listOf(call("search_product", 1), call("get_product_stock", 2), call("get_stock_movements", 3), call("calculate_stock_velocity", 4), call("calculate_reorder_signal", 5)),
            factsUsed = listOf("products", "stock_movements"),
            analyticsUsed = listOf("stock_velocity", "reorder_signal"),
            confidence = 0.9,
        )
    }

    private suspend fun answerReplenishment(request: IntelligenceRequest): IntelligenceResponse {
        val predictive = PredictiveRecommendationService(
            facts = facts,
            analytics = analytics,
            engine = recommendationEngine,
        ).generate(request.timestampEpochMs)
        val actionable = predictive.recommendations.filter {
            it.type == RecommendationType.STOCKOUT || it.type == RecommendationType.REPLENISHMENT
        }
        if (actionable.isEmpty()) {
            return IntelligenceResponse(
                status = IntelligenceResponseStatus.ANSWERED,
                answer = "Não encontrei produtos que precisem de reposição com os dados dos últimos 30 dias.",
                plan = listOf("search_product", "get_product_stock", "get_stock_movements", "calculate_stock_velocity", "generate_replenishment_recommendations"),
                toolCalls = listOf(
                    call("search_product", 1),
                    call("get_product_stock", 2),
                    call("get_stock_movements", 3),
                    call("calculate_stock_velocity", 4),
                    call("generate_replenishment_recommendations", 5),
                ),
                factsUsed = listOf("products", "stock_movements"),
                analyticsUsed = listOf("stock_velocity", "replenishment_heuristics"),
                confidence = 0.88,
                limitations = listOf("A recomendação é conservadora: considera somente estoque zerado ou abaixo da demanda observada."),
            )
        }
        val detail = actionable.joinToString(separator = "; ") { recommendation ->
            val evidence = recommendation.evidence
            "${recommendation.message} (${evidence?.stockQuantity ?: "?"} em estoque, ${evidence?.unitsSoldLast30Days ?: "?"} saída(s) em 30 dias)"
        }
        return IntelligenceResponse(
            status = IntelligenceResponseStatus.ANSWERED,
            answer = "Produtos para repor: $detail.",
            plan = listOf("search_product", "get_product_stock", "get_stock_movements", "calculate_stock_velocity", "generate_replenishment_recommendations"),
            toolCalls = listOf(
                call("search_product", 1),
                call("get_product_stock", 2),
                call("get_stock_movements", 3),
                call("calculate_stock_velocity", 4),
                call("generate_replenishment_recommendations", 5),
            ),
            factsUsed = listOf("products", "stock_movements"),
            analyticsUsed = listOf("stock_velocity", "replenishment_heuristics"),
            confidence = actionable.minOf { it.confidence },
            limitations = listOf("A recomendação é baseada em fatos locais e na demanda observada nos últimos 30 dias; não é uma ordem de compra."),
        )
    }

    private suspend fun answerStockTrend(request: IntelligenceRequest): IntelligenceResponse {
        val products = facts.products()
        if (products.isEmpty()) return insufficient("Ainda não há produtos para comparar o estoque.", "get_product_stock")
        val movements = products.flatMap { facts.stockMovements(it.id) }
        val cutoff = request.timestampEpochMs - Duration.ofDays(1).toMillis()
        val delta = movements.filter { it.occurredAtEpochMs in cutoff..request.timestampEpochMs }.sumOf { it.quantityDelta }
        val current = products.sumOf { it.stockQuantity }
        val previous = current - delta
        val direction = when {
            current < previous -> "caiu"
            current > previous -> "subiu"
            else -> "não mudou"
        }
        return IntelligenceResponse(
            status = IntelligenceResponseStatus.ANSWERED,
            answer = "O estoque total $direction de ontem: ontem eram $previous unidade(s) e agora são $current.",
            plan = listOf("get_product_stock", "get_stock_movements", "compare_stock_levels"),
            toolCalls = listOf(call("get_product_stock", 1), call("get_stock_movements", 2), call("compare_stock_levels", 3)),
            factsUsed = listOf("products", "stock_movements"),
            analyticsUsed = listOf("stock_level_variation_24h"),
            confidence = 0.93,
            limitations = if (movements.isEmpty()) listOf("Não há movimentações nas últimas 24 horas; a comparação usa o saldo atual como referência.") else emptyList(),
        )
    }

    private suspend fun answerInventory(request: IntelligenceRequest, normalized: String): IntelligenceResponse {
        val reference = extractProductReference(request.utterance, normalized)
            ?: request.resolvedContext["product"]
            ?: return clarificationPolicy.missingProductReference()
        return when (val resolved = facts.resolveProduct(reference)) {
            is IntelligenceEntityResolution.Ambiguous -> IntelligenceResponse(IntelligenceResponseStatus.AMBIGUOUS_ENTITY, "Encontrei mais de um produto com essa referência: ${resolved.values.joinToString { it.name }}.", plan = listOf("search_product"), limitations = listOf("Escolha um produto antes de analisar o estoque."))
            IntelligenceEntityResolution.NotFound -> insufficient("Não encontrei esse produto no estoque.", "search_product")
            is IntelligenceEntityResolution.Resolved -> {
                val velocity = analytics.calculateStockVelocity(resolved.value, facts.stockMovements(resolved.value.id), request.timestampEpochMs)
                val coverage = velocity.daysOfCoverage?.let { "aproximadamente ${it.formatDays()} de estoque" } ?: "sem velocidade suficiente para estimar cobertura"
                IntelligenceResponse(
                    status = IntelligenceResponseStatus.ANSWERED,
                    answer = "${resolved.value.name} tem ${resolved.value.stockQuantity} unidade(s), vendeu ${velocity.unitsLastPeriod} nos últimos 30 dias e está com $coverage.",
                    plan = listOf("search_product", "get_product_stock", "get_stock_movements", "calculate_stock_velocity", "calculate_stock_coverage"),
                    toolCalls = listOf(call("search_product", 1), call("get_product_stock", 2), call("get_stock_movements", 3), call("calculate_stock_velocity", 4)),
                    factsUsed = listOf("products", "stock_movements"),
                    analyticsUsed = listOf("stock_velocity", "days_of_coverage"),
                    confidence = if (velocity.unitsLastPeriod > 0) 0.91 else 0.78,
                    limitations = if (velocity.unitsLastPeriod > 0) emptyList() else listOf("Não há saídas suficientes nos últimos 30 dias para medir velocidade."),
                )
            }
        }
    }


}
