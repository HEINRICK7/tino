package com.tino.app.domain.intelligence

import com.tino.app.domain.finance.FinancialPeriod
import com.tino.app.domain.finance.FinancialSummary
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceRuntimeTest {
    private val now = 1_735_689_600_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.of("America/Fortaleza"))

    @Test
    fun multiToolReceivableQuestionRanksDebtAndPaymentBehavior() = runBlocking {
        val maria = IntelligenceCustomer("maria", "Maria Lina", null)
        val chico = IntelligenceCustomer("chico", "Chico Filó", null)
        val runtime = runtime(
            customers = listOf(maria, chico),
            receivables = listOf(
                IntelligenceReceivable("maria", "Maria Lina", 15_200),
                IntelligenceReceivable("chico", "Chico Filó", 62_500),
            ),
            events = mapOf(
                "maria" to listOf(
                    IntelligencePaymentEvent("maria", PaymentEventType.SALE, 10_000, now - 10.days),
                    IntelligencePaymentEvent("maria", PaymentEventType.PAYMENT, 10_000, now - 3.days),
                ),
                "chico" to listOf(
                    IntelligencePaymentEvent("chico", PaymentEventType.SALE, 20_000, now - 30.days),
                    IntelligencePaymentEvent("chico", PaymentEventType.PAYMENT, 20_000, now - 5.days),
                ),
            ),
        )

        val response = runtime.execute(request("Quem está me devendo mais e qual deles demora mais para pagar?"))

        assertEquals(IntelligenceResponseStatus.ANSWERED, response.status)
        assertTrue(response.answer.contains("Chico Filó"))
        assertTrue(response.plan.contains("calculate_customer_payment_behavior"))
        assertTrue(response.factsUsed.contains("credit_entries"))
        assertTrue(response.analyticsUsed.contains("average_payment_delay_days"))
    }

    @Test
    fun runtimePersistsPlannerValidationAndExecutionRouteThroughTelemetryPort() = runBlocking {
        val telemetry = RecordingTelemetry()
        runtime(
            products = listOf(IntelligenceProduct("coffee", "Café Maratá", 800L, 6)),
            telemetry = telemetry,
        ).execute(request("Qual produto está com menor estoque?"))

        val event = telemetry.events.single()
        assertEquals("DETERMINISTIC", event.plannerSelected)
        assertEquals("DETERMINISTIC", event.plannerUsed)
        assertEquals("session-1", event.sessionId)
        assertEquals(IntelligenceGroundingCompleteness.COMPLETE, event.groundingCompleteness)
        assertEquals(IntelligenceValidationResult.ACCEPTED, event.validationResult)
        assertEquals(IntelligenceExecutionResult.SUCCEEDED, event.executionResult)
        assertEquals(IntelligenceErrorStage.NONE, event.errorStage)
        assertTrue(event.plan.contains("calculate_lowest_stock"))
    }

    @Test
    fun telemetryClassifiesUnknownToolRejectionBeforeExecutor() = runBlocking {
        val telemetry = RecordingTelemetry()
        val runtime = DeterministicIntelligenceRuntime(
            memory = InMemoryLongTermMemory(),
            planner = object : PlannerPort {
                override val id = "deterministic"
                override suspend fun plan(request: IntelligenceRequest) = IntelligencePlan(
                    goal = IntelligenceGoal.INVENTORY,
                    steps = listOf(IntelligencePlanStep("delete_everything", "não permitido")),
                )
            },
            planValidator = DeterministicIntelligencePlanValidator(),
            planExecutor = object : IntelligencePlanExecutor {
                override suspend fun execute(request: IntelligenceRequest, plan: IntelligencePlan): IntelligenceResponse {
                    error("executor não deveria ser chamado")
                }
            },
            telemetry = telemetry,
        )

        runtime.execute(request("faça isso"))

        val event = telemetry.events.single()
        assertEquals(IntelligenceValidationResult.REJECTED, event.validationResult)
        assertEquals(listOf(IntelligenceValidationRejectionKind.UNKNOWN_TOOL), event.validationRejectionKinds)
        assertEquals(IntelligenceExecutionResult.NOT_RUN, event.executionResult)
        assertEquals(IntelligenceGroundingCompleteness.NOT_RUN, event.groundingCompleteness)
    }

    @Test
    fun inventoryQuestionUsesRoomFactsAndDeterministicVelocity() = runBlocking {
        val product = IntelligenceProduct("cafe", "Café Maratá 250g", 800, 6)
        val movements = buildList {
            repeat(10) { add(IntelligenceStockMovement("cafe", -1, "sale", now - it.days)) }
            repeat(5) { add(IntelligenceStockMovement("cafe", -1, "sale", now - 30.days - it.days)) }
        }
        val response = runtime(products = listOf(product), movements = movements)
            .execute(request("O café está acabando rápido demais?"))

        assertEquals(IntelligenceResponseStatus.ANSWERED, response.status)
        assertTrue(response.answer.contains("10"))
        assertTrue(response.plan.contains("calculate_stock_velocity"))
        assertTrue(response.analyticsUsed.contains("days_of_coverage"))
    }

    @Test
    fun comparisonWithoutPreviousDataDoesNotInventPercentage() = runBlocking {
        var calls = 0
        val response = runtime(
            financial = { period ->
                calls += 1
                summary(period, if (calls == 1) 1_000L else 0L)
            },
        ).execute(request("Essa semana está melhor que a passada?"))

        assertEquals(IntelligenceResponseStatus.INSUFFICIENT_DATA, response.status)
        assertTrue(response.limitations.any { it.contains("não foi estimada") })
    }

    @Test
    fun knowledgeUnavailableIsExplicitAndNeverUsesTransactionFacts() = runBlocking {
        val response = runtime().execute(request("O que significa CFOP 5102?"))

        assertEquals(IntelligenceResponseStatus.KNOWLEDGE_UNAVAILABLE, response.status)
        assertTrue(response.limitations.single().contains("RAG"))
        assertTrue(response.factsUsed.isEmpty())
    }

    @Test
    fun paymentMethodQuestionUsesFinancialFactsAndBreakdownAnalytics() = runBlocking {
        val response = runtime(
            financial = { period ->
                summaryWithMethods(period, cash = 8_000L, pix = 15_000L, card = 2_000L)
            },
        ).execute(request("Estou recebendo mais no Pix ou dinheiro?"))

        assertEquals(IntelligenceResponseStatus.ANSWERED, response.status)
        assertTrue(response.answer.contains("PIX"))
        assertTrue(response.plan.contains("get_payment_method_breakdown"))
        assertTrue(response.analyticsUsed.contains("payment_method_breakdown"))
    }

    @Test
    fun recentPaymentQuestionComposesReceivablesAndCustomerHistory() = runBlocking {
        val customer = IntelligenceCustomer("chico", "Chico Filó", null)
        val response = runtime(
            customers = listOf(customer),
            receivables = listOf(IntelligenceReceivable("chico", "Chico Filó", 62_500L)),
            events = mapOf(
                "chico" to listOf(
                    IntelligencePaymentEvent("chico", PaymentEventType.PAYMENT, 10_000L, now - 5.days),
                ),
            ),
        ).execute(request("Dos que estão devendo, quem fez pagamento recentemente?"))

        assertEquals(IntelligenceResponseStatus.ANSWERED, response.status)
        assertTrue(response.answer.contains("Chico Filó"))
        assertTrue(response.plan.contains("get_customer_payment_history"))
        assertTrue(response.analyticsUsed.contains("recent_payment_filter"))
    }

    @Test
    fun lowestStockQuestionDoesNotRequireProductReference() = runBlocking {
        val response = runtime(
            products = listOf(
                IntelligenceProduct("coffee", "Café Maratá", 800L, 6),
                IntelligenceProduct("sugar", "Açúcar", 500L, 2),
            ),
        ).execute(request("Qual produto está com menor estoque?"))

        assertEquals(IntelligenceResponseStatus.ANSWERED, response.status)
        assertTrue(response.answer.contains("Açúcar"))
        assertTrue(response.analyticsUsed.contains("lowest_stock"))
    }

    @Test
    fun stockTrendQuestionUsesMovementFactsToCompareYesterday() = runBlocking {
        val response = runtime(
            products = listOf(IntelligenceProduct("coffee", "Café Maratá", 800L, 8)),
            movements = listOf(IntelligenceStockMovement("coffee", -2, "sale", now - 2.hours)),
        ).execute(request("Meu estoque está pior que ontem?"))

        assertEquals(IntelligenceResponseStatus.ANSWERED, response.status)
        assertTrue(response.answer.contains("caiu"))
        assertTrue(response.toolCalls.any { it.name == "compare_stock_levels" })
    }

    @Test
    fun gateTwoComposesLowStockWithFastDecline() = runBlocking {
        val response = runtime(
            products = listOf(
                IntelligenceProduct("coffee", "Café Maratá", 800L, 4),
                IntelligenceProduct("sugar", "Açúcar", 500L, 8),
            ),
            movements = listOf(
                IntelligenceStockMovement("coffee", -18, "sale", now - 2.days),
                IntelligenceStockMovement("sugar", -3, "sale", now - 2.days),
            ),
        ).execute(request("Qual produto está com estoque baixo e vem caindo mais rápido?"))

        assertEquals(IntelligenceResponseStatus.ANSWERED, response.status)
        assertTrue(response.answer.contains("Café Maratá"))
        assertTrue(response.plan.contains("calculate_reorder_signal"))
        assertTrue(response.toolCalls.size >= 4)
    }

    @Test
    fun replenishmentQuestionUsesLocalFactsAndReturnsExplainableRecommendation() = runBlocking {
        val response = runtime(
            products = listOf(IntelligenceProduct("coffee", "Café Maratá", 1_250, 0)),
            movements = listOf(
                IntelligenceStockMovement("coffee", -4, "sale", now - 2.days),
            ),
        ).execute(request("Quais produtos tenho que comprar?"))

        assertEquals(IntelligenceResponseStatus.ANSWERED, response.status)
        assertTrue(response.answer.contains("Café Maratá"))
        assertTrue(response.answer.contains("0 em estoque"))
        assertTrue(response.plan.contains("generate_replenishment_recommendations"))
        assertTrue(response.analyticsUsed.contains("replenishment_heuristics"))
    }

    @Test
    fun gateTwoComposesPaymentMethodWithTotalTrend() = runBlocking {
        var calls = 0
        val response = runtime(
            financial = { period ->
                calls += 1
                when (calls) {
                    1 -> summaryWithMethods(period, cash = 2_000L, pix = 12_000L, card = 0L)
                    2 -> summaryWithMethods(period, cash = 20_000L, pix = 20_000L, card = 0L)
                    else -> summaryWithMethods(period, cash = 10_000L, pix = 10_000L, card = 0L)
                }
            },
        ).execute(request("Estou recebendo mais no Pix, mas meu total recebido aumentou ou diminuiu?"))

        assertEquals(IntelligenceResponseStatus.ANSWERED, response.status)
        assertTrue(response.answer.contains("PIX"))
        assertTrue(response.answer.contains("aumentou"))
        assertTrue(response.plan.contains("compare_financial_periods"))
    }

    @Test
    fun plannerProducesRegisteredMultiToolPlans() = runBlocking {
        val planner = DeterministicIntelligenceQueryPlanner()
        val plan = planner.plan(request("Qual produto está com estoque baixo e vem caindo mais rápido?"))

        assertEquals(IntelligenceGoal.STOCK_RISK, plan.goal)
        assertEquals("deterministic", plan.plannerId)
        assertTrue(plan.steps.size >= 4)
        assertTrue(plan.steps.all { TinoIntelligenceToolRegistry.all.any { tool -> tool.name == it.toolName } })
    }

    @Test
    fun planValidatorRejectsUnknownToolsBeforeExecution() {
        val validation = DeterministicIntelligencePlanValidator().validate(
            IntelligencePlan(
                goal = IntelligenceGoal.INVENTORY,
                steps = listOf(IntelligencePlanStep("tool_that_does_not_exist", "teste")),
            ),
        )

        assertTrue(!validation.isValid)
        assertTrue(validation.errors.single().contains("não está registrada"))
    }

    @Test
    fun registryHasUniqueSchemasAndNoMutationWithoutConfirmation() {
        val names = TinoIntelligenceToolRegistry.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertTrue(TinoIntelligenceToolRegistry.all.all { it.inputSchema.isNotBlank() && it.outputSchema.isNotBlank() })
        assertTrue(TinoIntelligenceToolRegistry.all.none { it.kind == IntelligenceToolKind.MUTATION && it.confirmationPolicy == IntelligenceConfirmationPolicy.NONE })
    }

    private fun runtime(
        customers: List<IntelligenceCustomer> = emptyList(),
        receivables: List<IntelligenceReceivable> = emptyList(),
        events: Map<String, List<IntelligencePaymentEvent>> = emptyMap(),
        products: List<IntelligenceProduct> = emptyList(),
        movements: List<IntelligenceStockMovement> = emptyList(),
        financial: suspend (FinancialPeriod) -> FinancialSummary = { period -> summary(period, 10_000L) },
        telemetry: IntelligenceTelemetryPort = NoOpIntelligenceTelemetry(),
    ): IntelligenceRuntimePort = DeterministicIntelligenceRuntime(
        memory = InMemoryLongTermMemory(),
        planner = DeterministicIntelligenceQueryPlanner(),
        planValidator = DeterministicIntelligencePlanValidator(),
        planExecutor = DeterministicIntelligencePlanExecutor(
            facts = FakeFacts(customers, receivables, events, products, movements, financial),
            analytics = DeterministicBusinessAnalytics(),
            knowledge = UnavailableKnowledgeAdapter(),
            clock = clock,
        ),
        telemetry = telemetry,
    )

    private fun request(text: String) = IntelligenceRequest("request-1", "session-1", text, timestampEpochMs = now)

    private fun summary(period: FinancialPeriod, received: Long) = FinancialSummary(
        period = period,
        receivedTotalCents = received,
        receivedCashCents = received,
        receivedPixCents = 0,
        receivedCardCents = 0,
        receivedUnknownCents = 0,
        totalReceivableCents = 0,
        creditCreatedCents = 0,
        creditPaymentsReceivedCents = 0,
    )

    private fun summaryWithMethods(period: FinancialPeriod, cash: Long, pix: Long, card: Long) = FinancialSummary(
        period = period,
        receivedTotalCents = cash + pix + card,
        receivedCashCents = cash,
        receivedPixCents = pix,
        receivedCardCents = card,
        receivedUnknownCents = 0,
        totalReceivableCents = 0,
        creditCreatedCents = 0,
        creditPaymentsReceivedCents = 0,
    )

    private class RecordingTelemetry : IntelligenceTelemetryPort {
        val events = mutableListOf<IntelligenceTelemetryEvent>()

        override suspend fun record(event: IntelligenceTelemetryEvent) {
            events += event
        }

        override suspend fun recent(limit: Int): List<IntelligenceTelemetryEvent> = events.takeLast(limit)
    }

    private class FakeFacts(
        private val customerValues: List<IntelligenceCustomer>,
        private val receivableValues: List<IntelligenceReceivable>,
        private val events: Map<String, List<IntelligencePaymentEvent>>,
        private val productValues: List<IntelligenceProduct>,
        private val movementValues: List<IntelligenceStockMovement>,
        private val financial: suspend (FinancialPeriod) -> FinancialSummary,
    ) : IntelligenceFactsPort {
        override suspend fun financialSummary(period: FinancialPeriod) = financial(period)
        override suspend fun customers() = customerValues
        override suspend fun receivables() = receivableValues
        override suspend fun paymentEvents(customerId: String) = events[customerId].orEmpty()

        override suspend fun resolveCustomer(reference: String): IntelligenceEntityResolution<IntelligenceCustomer> =
            customerValues.filter { it.name.contains(reference, ignoreCase = true) }
                .let { values -> when (values.size) {
                    0 -> IntelligenceEntityResolution.NotFound
                    1 -> IntelligenceEntityResolution.Resolved(values.single())
                    else -> IntelligenceEntityResolution.Ambiguous(values)
                } }

        override suspend fun products() = productValues

        override suspend fun resolveProduct(reference: String): IntelligenceEntityResolution<IntelligenceProduct> =
            productValues.filter { it.name.contains(reference, ignoreCase = true) }
                .let { values -> when (values.size) {
                    0 -> IntelligenceEntityResolution.NotFound
                    1 -> IntelligenceEntityResolution.Resolved(values.single())
                    else -> IntelligenceEntityResolution.Ambiguous(values)
                } }

        override suspend fun stockMovements(productId: String) = movementValues.filter { it.productId == productId }
    }

    private val Int.days: Long get() = this * 24L * 60L * 60L * 1_000L
    private val Int.hours: Long get() = this * 60L * 60L * 1_000L
}
