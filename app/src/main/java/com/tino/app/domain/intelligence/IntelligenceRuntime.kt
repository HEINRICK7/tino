package com.tino.app.domain.intelligence

import com.tino.app.domain.finance.FinancialPeriod
import com.tino.app.domain.finance.FinancialSummary
import com.tino.app.domain.intelligence.planning.IntelligencePlan
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeout

enum class IntelligenceResponseStatus {
    ANSWERED,
    NEEDS_CLARIFICATION,
    AMBIGUOUS_ENTITY,
    INSUFFICIENT_DATA,
    KNOWLEDGE_UNAVAILABLE,
    TOOL_UNAVAILABLE,
    UNSUPPORTED,
    ERROR,
}

data class IntelligenceRequest(
    val requestId: String,
    val sessionId: String,
    val utterance: String,
    val screenContext: String? = null,
    val resolvedContext: Map<String, String> = emptyMap(),
    val availableCapabilities: Set<String> = emptySet(),
    val locale: String = "pt-BR",
    val timestampEpochMs: Long = System.currentTimeMillis(),
)

data class IntelligenceToolCall(
    val name: String,
    val order: Int,
    val outcome: String,
    val durationMs: Long = 0L,
)

data class IntelligenceResponse(
    val status: IntelligenceResponseStatus,
    val answer: String,
    val plan: List<String> = emptyList(),
    val plannerUsed: String? = null,
    val toolCalls: List<IntelligenceToolCall> = emptyList(),
    val factsUsed: List<String> = emptyList(),
    val analyticsUsed: List<String> = emptyList(),
    val memoryUsed: List<String> = emptyList(),
    val knowledgeUsed: List<String> = emptyList(),
    val confidence: Double = 0.0,
    val limitations: List<String> = emptyList(),
)

interface IntelligenceRuntimePort {
    suspend fun execute(request: IntelligenceRequest): IntelligenceResponse
}

/** Test/default fallback used when the optional runtime is not wired. */
class UnavailableIntelligenceRuntime : IntelligenceRuntimePort {
    override suspend fun execute(request: IntelligenceRequest): IntelligenceResponse = IntelligenceResponse(
        status = IntelligenceResponseStatus.UNSUPPORTED,
        answer = "Ainda não tenho dados e ferramentas suficientes para responder isso com segurança.",
    )
}

/** The official orchestrator boundary. It deliberately contains no ADK types. */
interface GoogleAdkOrchestratorPort {
    suspend fun execute(
        request: IntelligenceRequest,
        tools: List<IntelligenceToolDefinition>,
    ): IntelligenceResponse?
}

/** Explicit fallback until an approved Google ADK engine is wired into Android. */
@Singleton
class UnavailableGoogleAdkOrchestrator @Inject constructor() : GoogleAdkOrchestratorPort {
    override suspend fun execute(
        request: IntelligenceRequest,
        tools: List<IntelligenceToolDefinition>,
    ): IntelligenceResponse? = null
}

/**
 * Isolates the optional Google ADK engine from the rest of the app.
 * Until an approved Android ADK dependency is supplied, the deterministic
 * local runtime remains the safe execution path.
 */
@Singleton
class GoogleAdkRuntimeAdapter @Inject constructor(
    private val orchestrator: GoogleAdkOrchestratorPort,
    private val localRuntime: DeterministicIntelligenceRuntime,
) : IntelligenceRuntimePort {
    override suspend fun execute(request: IntelligenceRequest): IntelligenceResponse =
        orchestrator.execute(request, TinoIntelligenceToolRegistry.all) ?: localRuntime.execute(request)
}

enum class IntelligenceToolKind { FACT, ANALYTIC, MEMORY, KNOWLEDGE, MUTATION }
enum class IntelligenceConfirmationPolicy { NONE, REQUIRED }

data class IntelligenceToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: String,
    val outputSchema: String,
    val kind: IntelligenceToolKind,
    val confirmationPolicy: IntelligenceConfirmationPolicy,
    val timeoutMs: Long = 2_000L,
    val authorization: String = "COMMERCE_READ",
    val version: Int = 1,
)

object TinoIntelligenceToolRegistry {
    val all: List<IntelligenceToolDefinition> = listOf(
        tool("get_financial_summary", "Consulta o resumo financeiro em um período."),
        tool("get_receivables", "Lista recebíveis atuais por cliente."),
        tool("compare_financial_periods", "Compara recebimentos entre períodos."),
        tool("get_payment_method_breakdown", "Calcula a participação por forma de pagamento."),
        tool("project_receivable_cash", "Calcula a projeção dos recebíveis abertos."),
        tool("search_customer", "Resolve uma referência de cliente sem inventar entidade."),
        tool("get_customer_balance", "Consulta saldo atual de um cliente."),
        tool("get_customer_payment_history", "Consulta fatos de pagamentos de um cliente."),
        tool("calculate_customer_payment_behavior", "Calcula atraso e frequência de pagamento."),
        tool("filter_recent_payments", "Filtra pagamentos dentro de uma janela de tempo."),
        tool("search_product", "Resolve uma referência de produto."),
        tool("get_product_stock", "Consulta estoque atual do produto."),
        tool("get_stock_movements", "Consulta movimentações de estoque."),
        tool("calculate_stock_velocity", "Calcula velocidade de saída em períodos."),
        tool("calculate_stock_coverage", "Calcula cobertura estimada do estoque."),
        tool("calculate_reorder_signal", "Calcula sinal determinístico de reposição."),
        tool("compare_stock_levels", "Compara o saldo do estoque em dois momentos."),
        tool("calculate_lowest_stock", "Encontra o menor saldo do catálogo."),
        tool("sort_receivables", "Ordena recebíveis por saldo ou comportamento."),
        tool("search_knowledge", "Busca conhecimento aprovado, sem fatos transacionais."),
        tool("get_document_context", "Recupera contexto de documento aprovado."),
        tool("explain_term", "Explica um termo de ajuda ou fiscal."),
    ).distinctBy { it.name }

    fun require(name: String): IntelligenceToolDefinition =
        all.firstOrNull { it.name == name } ?: error("Intelligence tool não registrada: $name")

    private fun tool(name: String, description: String) = IntelligenceToolDefinition(
        name = name,
        description = description,
        inputSchema = "object",
        outputSchema = "grounded_result",
        kind = if (name.startsWith("calculate_") || name.startsWith("compare_")) {
            IntelligenceToolKind.ANALYTIC
        } else if (name.contains("knowledge") || name == "explain_term" || name == "get_document_context") {
            IntelligenceToolKind.KNOWLEDGE
        } else {
            IntelligenceToolKind.FACT
        },
        confirmationPolicy = IntelligenceConfirmationPolicy.NONE,
    )
}

data class IntelligenceCustomer(
    val id: String,
    val name: String,
    val phone: String?,
)

data class IntelligenceReceivable(
    val customerId: String,
    val customerName: String,
    val outstandingCents: Long,
)

data class IntelligencePaymentEvent(
    val customerId: String,
    val type: PaymentEventType,
    val amountCents: Long,
    val occurredAtEpochMs: Long,
)

enum class PaymentEventType { SALE, PAYMENT }

data class IntelligenceProduct(
    val id: String,
    val name: String,
    val priceCents: Long,
    val stockQuantity: Int,
)

data class IntelligenceStockMovement(
    val productId: String,
    val quantityDelta: Int,
    val reason: String,
    val occurredAtEpochMs: Long,
)

sealed interface IntelligenceEntityResolution<out T> {
    data class Resolved<T>(val value: T) : IntelligenceEntityResolution<T>
    data class Ambiguous<T>(val values: List<T>) : IntelligenceEntityResolution<T>
    data object NotFound : IntelligenceEntityResolution<Nothing>
}

interface IntelligenceFactsPort {
    suspend fun financialSummary(period: FinancialPeriod): FinancialSummary
    suspend fun customers(): List<IntelligenceCustomer>
    suspend fun receivables(): List<IntelligenceReceivable>
    suspend fun paymentEvents(customerId: String): List<IntelligencePaymentEvent>
    suspend fun resolveCustomer(reference: String): IntelligenceEntityResolution<IntelligenceCustomer>
    suspend fun products(): List<IntelligenceProduct>
    suspend fun resolveProduct(reference: String): IntelligenceEntityResolution<IntelligenceProduct>
    suspend fun stockMovements(productId: String): List<IntelligenceStockMovement>
}

enum class MemoryType { ENTITY_ALIAS, INTERACTION_PREFERENCE, BUSINESS_CONTEXT, USER_WORKFLOW }

data class MemoryRecord(
    val id: String,
    val key: String,
    val value: String,
    val type: MemoryType,
    val source: String,
    val confidence: Double,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
)

interface MemoryPort {
    suspend fun remember(record: MemoryRecord)
    suspend fun search(query: String, type: MemoryType? = null): List<MemoryRecord>
    suspend fun forget(id: String)
    suspend fun expire(nowEpochMs: Long = System.currentTimeMillis())
}

@Singleton
class InMemoryLongTermMemory @Inject constructor() : MemoryPort {
    private val records = linkedMapOf<String, MemoryRecord>()

    override suspend fun remember(record: MemoryRecord) {
        synchronized(records) { records[record.id] = record }
    }

    override suspend fun search(query: String, type: MemoryType?): List<MemoryRecord> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        return synchronized(records) {
            records.values.filter { record ->
                (type == null || record.type == type) &&
                    (normalized.isBlank() || record.key.lowercase(Locale.ROOT).contains(normalized) ||
                        record.value.lowercase(Locale.ROOT).contains(normalized))
            }
        }
    }

    override suspend fun forget(id: String) {
        synchronized(records) { records.remove(id) }
    }

    override suspend fun expire(nowEpochMs: Long) {
        synchronized(records) {
            records.values.removeIf { it.expiresAtEpochMs != null && it.expiresAtEpochMs <= nowEpochMs }
        }
    }
}

data class KnowledgeQuery(
    val query: String,
    val allowedCollections: Set<String> = emptySet(),
)

data class KnowledgeAnswer(
    val answer: String,
    val sources: List<String>,
    val confidence: Double,
    val timestampEpochMs: Long,
)

interface KnowledgeQueryPort {
    suspend fun query(request: KnowledgeQuery): KnowledgeAnswer?
}

@Singleton
class UnavailableKnowledgeAdapter @Inject constructor() : KnowledgeQueryPort {
    override suspend fun query(request: KnowledgeQuery): KnowledgeAnswer? = null
}

data class PaymentBehavior(
    val customerId: String,
    val averagePaymentDelayDays: Double?,
    val paymentCount: Int,
)

data class StockVelocity(
    val productId: String,
    val unitsLastPeriod: Int,
    val unitsPreviousPeriod: Int,
    val daysOfCoverage: Double?,
    val variationPercent: Double?,
)

interface BusinessAnalyticsPort {
    fun compareFinancialPeriods(current: FinancialSummary, previous: FinancialSummary): Double?
    fun calculatePaymentBehavior(events: List<IntelligencePaymentEvent>): PaymentBehavior
    fun calculateStockVelocity(
        product: IntelligenceProduct,
        movements: List<IntelligenceStockMovement>,
        nowEpochMs: Long,
    ): StockVelocity
}

@Singleton
class DeterministicBusinessAnalytics @Inject constructor() : BusinessAnalyticsPort {
    override fun compareFinancialPeriods(current: FinancialSummary, previous: FinancialSummary): Double? {
        if (previous.receivedTotalCents == 0L) return null
        return ((current.receivedTotalCents - previous.receivedTotalCents).toDouble() /
            previous.receivedTotalCents.toDouble()) * 100.0
    }

    override fun calculatePaymentBehavior(events: List<IntelligencePaymentEvent>): PaymentBehavior {
        val sales = events.filter { it.type == PaymentEventType.SALE }.sortedBy { it.occurredAtEpochMs }
        val payments = events.filter { it.type == PaymentEventType.PAYMENT }.sortedBy { it.occurredAtEpochMs }
        val delays = sales.mapNotNull { sale ->
            payments.firstOrNull { payment ->
                payment.occurredAtEpochMs >= sale.occurredAtEpochMs && payment.amountCents >= sale.amountCents
            }?.let { payment ->
                Duration.ofMillis(payment.occurredAtEpochMs - sale.occurredAtEpochMs).toHours() / 24.0
            }
        }
        return PaymentBehavior(
            customerId = events.firstOrNull()?.customerId.orEmpty(),
            averagePaymentDelayDays = delays.takeIf { it.isNotEmpty() }?.average(),
            paymentCount = payments.size,
        )
    }

    override fun calculateStockVelocity(
        product: IntelligenceProduct,
        movements: List<IntelligenceStockMovement>,
        nowEpochMs: Long,
    ): StockVelocity {
        val periodMs = Duration.ofDays(30).toMillis()
        val currentStart = nowEpochMs - periodMs
        val previousStart = nowEpochMs - periodMs * 2
        fun soldBetween(start: Long, end: Long) = movements
            .filter { it.reason == "sale" && it.occurredAtEpochMs in start until end }
            .sumOf { -it.quantityDelta }
            .coerceAtLeast(0)
        val current = soldBetween(currentStart, nowEpochMs)
        val previous = soldBetween(previousStart, currentStart)
        val dailyRate = current / 30.0
        val variation = if (previous == 0) null else ((current - previous).toDouble() / previous) * 100.0
        return StockVelocity(
            productId = product.id,
            unitsLastPeriod = current,
            unitsPreviousPeriod = previous,
            daysOfCoverage = dailyRate.takeIf { it > 0 }?.let { product.stockQuantity / it },
            variationPercent = variation,
        )
    }
}

@Singleton
class DeterministicIntelligenceRuntime @Inject constructor(
    private val memory: MemoryPort,
    private val planner: PlannerPort,
    private val planValidator: IntelligencePlanValidator,
    private val planExecutor: IntelligencePlanExecutor,
    private val telemetry: IntelligenceTelemetryPort = NoOpIntelligenceTelemetry(),
) : IntelligenceRuntimePort {
    override suspend fun execute(request: IntelligenceRequest): IntelligenceResponse = withTimeout(GLOBAL_TIMEOUT_MS) {
        val startedAt = System.currentTimeMillis()
        var plan: IntelligencePlan? = null
        var planningLatencyMs = 0L
        var errorStage = IntelligenceErrorStage.PLANNING
        try {
            memory.expire(request.timestampEpochMs)
            val planningStartedAt = System.currentTimeMillis()
            plan = planner.plan(request)
            planningLatencyMs = System.currentTimeMillis() - planningStartedAt
            errorStage = IntelligenceErrorStage.VALIDATION
            val validation = planValidator.validate(plan)
            if (!validation.isValid) {
                val response = IntelligenceResponse(
                    status = IntelligenceResponseStatus.TOOL_UNAVAILABLE,
                    answer = "Não consegui validar com segurança o plano para essa pergunta.",
                    plan = plan.steps.map { it.toolName },
                    plannerUsed = plan.plannerId,
                    limitations = validation.errors,
                )
                recordSafely(
                    event = telemetryEvent(
                        request = request,
                        plan = plan,
                        validationResult = IntelligenceValidationResult.REJECTED,
                        validationErrors = validation.errors,
                        executionResult = IntelligenceExecutionResult.NOT_RUN,
                        startedAt = startedAt,
                        planningLatencyMs = planningLatencyMs,
                        errorStage = IntelligenceErrorStage.VALIDATION,
                    ),
                )
                response
            } else {
                errorStage = IntelligenceErrorStage.EXECUTION
                val response = planExecutor.execute(request, plan)
                recordSafely(
                    event = telemetryEvent(
                        request = request,
                        plan = plan,
                        validationResult = IntelligenceValidationResult.ACCEPTED,
                        executionResult = IntelligenceExecutionResult.SUCCEEDED,
                        response = response,
                        startedAt = startedAt,
                        planningLatencyMs = planningLatencyMs,
                        errorStage = IntelligenceErrorStage.NONE,
                    ),
                )
                response
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            recordSafely(
                event = telemetryEvent(
                    request = request,
                    plan = plan,
                    validationResult = if (errorStage == IntelligenceErrorStage.PLANNING) {
                        IntelligenceValidationResult.NOT_RUN
                    } else {
                        IntelligenceValidationResult.REJECTED
                    },
                    executionResult = IntelligenceExecutionResult.FAILED,
                    startedAt = startedAt,
                    planningLatencyMs = planningLatencyMs,
                    errorStage = errorStage,
                ),
            )
            throw error
        }
    }

    private suspend fun recordSafely(event: IntelligenceTelemetryEvent) {
        try {
            telemetry.record(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Telemetry must never become a commerce runtime dependency.
        }
    }

    private fun telemetryEvent(
        request: IntelligenceRequest,
        plan: IntelligencePlan?,
        validationResult: IntelligenceValidationResult,
        validationErrors: List<String> = emptyList(),
        executionResult: IntelligenceExecutionResult,
        response: IntelligenceResponse? = null,
        startedAt: Long,
        planningLatencyMs: Long,
        errorStage: IntelligenceErrorStage,
    ) = IntelligenceTelemetryEvent(
        requestId = request.requestId,
        sessionId = request.sessionId,
        plannerSelected = plannerSelectedLabel(),
        plannerUsed = plannerUsedLabel(plan),
        fallbackReason = plan?.fallbackReason,
        plan = plan?.steps?.map { it.toolName }.orEmpty(),
        validationResult = validationResult,
        validationErrors = validationErrors,
        validationRejectionKinds = classifyValidationRejections(validationErrors),
        fallbackUsed = plannerUsedLabel(plan) == "ADK_FALLBACK_DETERMINISTIC",
        executionResult = executionResult,
        groundingCompleteness = groundingCompleteness(plan, validationResult, executionResult, response),
        latencyMs = System.currentTimeMillis() - startedAt,
        planningLatencyMs = planningLatencyMs,
        errorStage = errorStage,
        occurredAtEpochMs = System.currentTimeMillis(),
    )

    private fun plannerSelectedLabel(): String = when (planner.id.lowercase(Locale.ROOT)) {
        "adk" -> "ADK"
        else -> "DETERMINISTIC"
    }

    private fun plannerUsedLabel(plan: IntelligencePlan?): String = when {
        plan?.plannerId == "adk" -> "ADK"
        plan?.plannerId?.endsWith("-fallback") == true -> "ADK_FALLBACK_DETERMINISTIC"
        else -> "DETERMINISTIC"
    }

    private fun groundingCompleteness(
        plan: IntelligencePlan?,
        validationResult: IntelligenceValidationResult,
        executionResult: IntelligenceExecutionResult,
        response: IntelligenceResponse?,
    ): IntelligenceGroundingCompleteness {
        if (validationResult != IntelligenceValidationResult.ACCEPTED ||
            executionResult != IntelligenceExecutionResult.SUCCEEDED
        ) return IntelligenceGroundingCompleteness.NOT_RUN
        if (plan?.steps.isNullOrEmpty()) return IntelligenceGroundingCompleteness.NOT_APPLICABLE
        if (response == null || response.status == IntelligenceResponseStatus.ERROR) {
            return IntelligenceGroundingCompleteness.MISSING
        }
        if (response.status == IntelligenceResponseStatus.INSUFFICIENT_DATA ||
            response.status == IntelligenceResponseStatus.KNOWLEDGE_UNAVAILABLE
        ) return IntelligenceGroundingCompleteness.PARTIAL
        val evidenceCount = response.factsUsed.size + response.analyticsUsed.size +
            response.memoryUsed.size + response.knowledgeUsed.size
        return if (evidenceCount > 0) {
            IntelligenceGroundingCompleteness.COMPLETE
        } else {
            IntelligenceGroundingCompleteness.MISSING
        }
    }

    private fun classifyValidationRejections(errors: List<String>): List<IntelligenceValidationRejectionKind> =
        errors.map { error ->
            when {
                error.contains("não está registrada", ignoreCase = true) ->
                    IntelligenceValidationRejectionKind.UNKNOWN_TOOL
                error.contains("argument", ignoreCase = true) ->
                    IntelligenceValidationRejectionKind.INVALID_ARGUMENT
                error.contains("mutação", ignoreCase = true) || error.contains("policy", ignoreCase = true) ->
                    IntelligenceValidationRejectionKind.POLICY
                error.contains("excede o limite", ignoreCase = true) ->
                    IntelligenceValidationRejectionKind.PLAN_LIMIT
                else -> IntelligenceValidationRejectionKind.OTHER
            }
        }.distinct()

    companion object {
        private const val GLOBAL_TIMEOUT_MS = 8_000L
    }
}
