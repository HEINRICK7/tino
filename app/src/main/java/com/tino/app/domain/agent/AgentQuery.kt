package com.tino.app.domain.agent

import com.tino.app.domain.finance.FinancialPeriod
import com.tino.app.domain.finance.FinancialProjectionRepository
import com.tino.app.domain.finance.FinancialSummary
import com.tino.app.domain.voice.CommerceToolName
import com.tino.app.domain.voice.ToolCall
import com.tino.app.domain.voice.ToolExecutor
import com.tino.app.domain.voice.ToolExecutionResult
import com.tino.app.domain.voice.ToolPreview
import com.tino.app.domain.voice.MutationConfirmation
import com.tino.app.domain.voice.isReadOnly
import java.text.Normalizer
import java.time.Clock
import java.util.Locale
import com.tino.app.domain.commerce.PaymentMethod
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

enum class AgentCapability {
    GLOBAL_TOOL,
    READ_FINANCIAL_SUMMARY,
    LIST_PRODUCTS,
    REPLENISHMENT_QUERY,
    GET_PRODUCT_STOCK,
    GET_PRODUCT_PRICE,
    LIST_CUSTOMERS,
    LIST_RECEIVABLES,
    LIST_OVERDUE,
    ADD_CREDIT_ITEM,
    REGISTER_CREDIT_PAYMENT,
    GET_CUSTOMER_BALANCE,
    GET_CUSTOMER_TIMELINE,
}

data class AgentRequest(val text: String)

interface AgentQueryBoundary {
    suspend fun ask(intent: AgentIntent): AgentResponse

    suspend fun askGlobal(call: ToolCall): AgentResponse =
        AgentResponse.Unsupported("A operação global ainda não está disponível.")

    suspend fun confirm(call: ToolCall): ToolExecutionResult =
        error("Esta boundary não possui ações confirmáveis.")

    suspend fun confirm(call: ToolCall, confirmation: MutationConfirmation?): ToolExecutionResult =
        confirm(call)
}

sealed interface AgentResponse {
    data class SurfaceReady(
        val capability: AgentCapability,
        val result: FinancialSummaryResult,
        val surface: AgentSurface,
        val dataSource: AgentDataSource,
    ) : AgentResponse

    data class ActionPreviewReady(
        val capability: AgentCapability,
        val call: ToolCall,
        val preview: ToolPreview,
        val dataSource: AgentDataSource,
    ) : AgentResponse

    data class GlobalAnswerReady(
        val call: ToolCall,
        val result: ToolExecutionResult,
        val dataSource: AgentDataSource,
    ) : AgentResponse

    data class CustomerBalanceReady(
        val capability: AgentCapability,
        val result: CustomerBalanceResult,
        val dataSource: AgentDataSource,
        val customerResolutionMs: Long,
    ) : AgentResponse

    data class CustomerTimelineReady(
        val capability: AgentCapability,
        val result: CustomerTimelineResult,
        val dataSource: AgentDataSource,
        val customerResolutionMs: Long,
    ) : AgentResponse

    data class ReadListReady(
        val capability: AgentCapability,
        val result: DbFirstReadResult,
        val dataSource: AgentDataSource,
    ) : AgentResponse

    data class EntityChoiceReady(
        val capability: AgentCapability,
        val entityType: String,
        val options: List<String>,
        val dataSource: AgentDataSource,
    ) : AgentResponse

    data class Unsupported(val message: String) : AgentResponse
}

enum class AgentDataSource {
    LOCAL_ONLY,
}

data class FinancialSummaryResult(
    val period: FinancialPeriod,
    val receivedTotalCents: Long,
    val receivedCashCents: Long,
    val receivedPixCents: Long,
    val receivedCardCents: Long,
    val receivedUnknownCents: Long,
    val totalReceivableCents: Long,
    val creditCreatedCents: Long,
    val creditPaymentsReceivedCents: Long,
)

data class CustomerBalanceResult(
    val customerName: String,
    val currentBalanceCents: Long,
    val openCents: Long,
    val overdueCents: Long,
    val oldestOpenDays: Long?,
    val dataSource: AgentDataSource = AgentDataSource.LOCAL_ONLY,
)

data class CustomerTimelineItem(
    val occurredAt: Long,
    val dateText: String,
    val label: String,
    val amountText: String,
    val amountCents: Long,
)

data class CustomerTimelineResult(
    val customerName: String,
    val currentBalanceCents: Long,
    val currentBalanceText: String,
    val items: List<CustomerTimelineItem>,
    val emptyMessage: String?,
    val dataSource: AgentDataSource = AgentDataSource.LOCAL_ONLY,
)

sealed interface AgentSurface {
    data class FinancialSummaryCard(
        val title: String,
        val primaryLabel: String,
        val primaryValueCents: Long,
        val primaryValueText: String,
        val metrics: List<SurfaceMetric>,
        val emptyMessage: String?,
        val dataSource: AgentDataSource,
    ) : AgentSurface

    data class CustomerBalanceCard(
        val title: String,
        val customerName: String,
        val currentBalanceCents: Long,
        val currentBalanceText: String,
        val openText: String,
        val overdueText: String,
        val oldestOpenText: String?,
        val emptyMessage: String?,
        val dataSource: AgentDataSource,
    ) : AgentSurface

    data class CustomerTimelineCard(
        val title: String,
        val customerName: String,
        val currentBalanceText: String,
        val items: List<CustomerTimelineItem>,
        val emptyMessage: String?,
        val dataSource: AgentDataSource,
    ) : AgentSurface
}

data class SurfaceMetric(
    val key: String,
    val label: String,
    val valueCents: Long,
    val valueText: String,
)

@Singleton
class FinancialSummaryQueryTool @Inject constructor(
    private val projection: FinancialProjectionRepository,
    private val clock: Clock,
) {
    suspend fun execute(): FinancialSummaryResult =
        projection.summary(FinancialPeriod.today(clock)).toResult()

    private fun FinancialSummary.toResult() = FinancialSummaryResult(
        period = period,
        receivedTotalCents = receivedTotalCents,
        receivedCashCents = receivedCashCents,
        receivedPixCents = receivedPixCents,
        receivedCardCents = receivedCardCents,
        receivedUnknownCents = receivedUnknownCents,
        totalReceivableCents = totalReceivableCents,
        creditCreatedCents = creditCreatedCents,
        creditPaymentsReceivedCents = creditPaymentsReceivedCents,
    )
}

@Singleton
class CustomerBalanceQueryTool @Inject constructor(
    private val entityResolver: com.tino.app.domain.commerce.EntityResolutionService,
    private val temporalCredit: com.tino.app.domain.commerce.TemporalCreditService,
) : CustomerBalanceQueryPort {
    override
    suspend fun execute(reference: String): CustomerBalanceQueryResult {
        val startedAt = System.nanoTime()
        return when (val match = entityResolver.resolveCustomer(reference)) {
            is com.tino.app.domain.commerce.EntityResolutionMatch.Resolved -> {
                val timeline = temporalCredit.customerTimeline(match.value.id)
                CustomerBalanceQueryResult.Ready(
                    result = CustomerBalanceResult(
                        customerName = match.value.name,
                        currentBalanceCents = timeline.currentBalanceCents.coerceAtLeast(0L),
                        openCents = timeline.openCents,
                        overdueCents = timeline.overdueCents,
                        oldestOpenDays = timeline.entries
                            .filter { it.outstandingCents > 0L }
                            .maxOfOrNull { it.daysOpen },
                    ),
                    customerResolutionMs = elapsedMs(startedAt),
                )
            }
            is com.tino.app.domain.commerce.EntityResolutionMatch.Ambiguous ->
                CustomerBalanceQueryResult.Ambiguous(match.values.map { it.name })
            com.tino.app.domain.commerce.EntityResolutionMatch.NotFound ->
                CustomerBalanceQueryResult.NotFound
        }
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt).coerceAtLeast(0L) / 1_000_000L
}

interface CustomerBalanceQueryPort {
    suspend fun execute(reference: String): CustomerBalanceQueryResult
}

sealed interface CustomerBalanceQueryResult {
    data class Ready(
        val result: CustomerBalanceResult,
        val customerResolutionMs: Long,
    ) : CustomerBalanceQueryResult

    data class Ambiguous(val options: List<String>) : CustomerBalanceQueryResult
    data object NotFound : CustomerBalanceQueryResult
}

@Singleton
class CustomerTimelineQueryTool @Inject constructor(
    private val entityResolver: com.tino.app.domain.commerce.EntityResolutionService,
    private val temporalCredit: com.tino.app.domain.commerce.TemporalCreditService,
    private val clock: Clock,
) : CustomerTimelineQueryPort {
    override suspend fun execute(reference: String): CustomerTimelineQueryResult {
        val startedAt = System.nanoTime()
        return when (val match = entityResolver.resolveCustomer(reference)) {
            is com.tino.app.domain.commerce.EntityResolutionMatch.Resolved -> {
                val timeline = temporalCredit.customerTimeline(
                    customerId = match.value.id,
                    now = clock.millis(),
                    zone = clock.zone,
                )
                val items = buildList {
                    timeline.entries.forEach { entry ->
                        add(
                            CustomerTimelineItem(
                                occurredAt = entry.occurredAt,
                                dateText = formatDate(entry.occurredAt, clock.zone),
                                label = "Fiado",
                                amountText = "+${formatCents(entry.amountCents)}",
                                amountCents = entry.amountCents,
                            ),
                        )
                    }
                    timeline.payments.forEach { payment ->
                        add(
                            CustomerTimelineItem(
                                occurredAt = payment.occurredAt,
                                dateText = formatDate(payment.occurredAt, clock.zone),
                                label = "Pagou ${paymentMethodLabel(payment.paymentMethod)}",
                                amountText = "-${formatCents(payment.amountCents)}",
                                amountCents = -payment.amountCents,
                            ),
                        )
                    }
                }.sortedByDescending { it.occurredAt }
                CustomerTimelineQueryResult.Ready(
                    result = CustomerTimelineResult(
                        customerName = match.value.name,
                        currentBalanceCents = timeline.currentBalanceCents.coerceAtLeast(0L),
                        currentBalanceText = formatCents(timeline.currentBalanceCents.coerceAtLeast(0L)),
                        items = items,
                        emptyMessage = if (items.isEmpty()) "Ainda não há movimentos nesta conta." else null,
                    ),
                    customerResolutionMs = elapsedMs(startedAt),
                )
            }
            is com.tino.app.domain.commerce.EntityResolutionMatch.Ambiguous ->
                CustomerTimelineQueryResult.Ambiguous(match.values.map { it.name })
            com.tino.app.domain.commerce.EntityResolutionMatch.NotFound ->
                CustomerTimelineQueryResult.NotFound
        }
    }

    private fun formatDate(timestamp: Long, zone: java.time.ZoneId): String =
        java.time.Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().let {
            "%02d %s".format(java.util.Locale("pt", "BR"), it.dayOfMonth, monthName(it.monthValue))
        }

    private fun monthName(month: Int): String = listOf(
        "jan", "fev", "mar", "abr", "mai", "jun",
        "jul", "ago", "set", "out", "nov", "dez",
    )[month - 1]

    private fun paymentMethodLabel(value: String): String = when (value.lowercase()) {
        "pix" -> "no PIX"
        "card" -> "na maquininha"
        "cash" -> "em dinheiro"
        else -> "(forma não identificada)"
    }

    private fun formatCents(cents: Long): String =
        "R$ %.2f".format(Locale("pt", "BR"), cents / 100.0)

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt).coerceAtLeast(0L) / 1_000_000L
}

interface CustomerTimelineQueryPort {
    suspend fun execute(reference: String): CustomerTimelineQueryResult
}

sealed interface CustomerTimelineQueryResult {
    data class Ready(
        val result: CustomerTimelineResult,
        val customerResolutionMs: Long,
    ) : CustomerTimelineQueryResult

    data class Ambiguous(val options: List<String>) : CustomerTimelineQueryResult
    data object NotFound : CustomerTimelineQueryResult
}

@Singleton
class AgentSurfaceRenderer @Inject constructor() {
    fun render(
        result: FinancialSummaryResult,
        paymentMethod: FinancialPaymentMethod = FinancialPaymentMethod.ALL,
        metric: FinancialMetric = FinancialMetric.RECEIVED,
    ): AgentSurface.FinancialSummaryCard {
        val primary = when (metric) {
            FinancialMetric.RECEIVABLE -> "receivableCents" to "A receber"
            FinancialMetric.SUMMARY,
            FinancialMetric.RECEIVED,
            -> when (paymentMethod) {
                FinancialPaymentMethod.ALL -> "receivedTotalCents" to "Recebido hoje"
                FinancialPaymentMethod.CASH -> "receivedCashCents" to "Dinheiro hoje"
                FinancialPaymentMethod.PIX -> "receivedPixCents" to "PIX hoje"
                FinancialPaymentMethod.CARD -> "receivedCardCents" to "Maquininha hoje"
            }
        }
        val primaryCents = when (primary.first) {
            "receivableCents" -> result.totalReceivableCents
            "receivedCashCents" -> result.receivedCashCents
            "receivedPixCents" -> result.receivedPixCents
            "receivedCardCents" -> result.receivedCardCents
            else -> result.receivedTotalCents
        }
        val metrics = when (metric) {
            FinancialMetric.SUMMARY -> listOf(
                SurfaceMetric("cash", "Dinheiro", result.receivedCashCents, formatCents(result.receivedCashCents)),
                SurfaceMetric("pix", "PIX", result.receivedPixCents, formatCents(result.receivedPixCents)),
                SurfaceMetric("card", "Maquininha", result.receivedCardCents, formatCents(result.receivedCardCents)),
                SurfaceMetric("unknown", "Não identificado", result.receivedUnknownCents, formatCents(result.receivedUnknownCents)),
                SurfaceMetric("receivable", "A receber", result.totalReceivableCents, formatCents(result.totalReceivableCents)),
            )
            FinancialMetric.RECEIVABLE -> listOf(
                SurfaceMetric("receivable", "A receber", result.totalReceivableCents, formatCents(result.totalReceivableCents)),
            )
            FinancialMetric.RECEIVED -> listOf(
                SurfaceMetric("cash", "Dinheiro", result.receivedCashCents, formatCents(result.receivedCashCents)),
                SurfaceMetric("pix", "PIX", result.receivedPixCents, formatCents(result.receivedPixCents)),
                SurfaceMetric("card", "Maquininha", result.receivedCardCents, formatCents(result.receivedCardCents)),
                SurfaceMetric("unknown", "Não identificado", result.receivedUnknownCents, formatCents(result.receivedUnknownCents)),
            )
        }
        return AgentSurface.FinancialSummaryCard(
            title = when (metric) {
                FinancialMetric.SUMMARY -> "Resumo financeiro de hoje"
                FinancialMetric.RECEIVABLE -> "A receber"
                FinancialMetric.RECEIVED -> "Entrou hoje"
            },
            primaryLabel = primary.second,
            primaryValueCents = primaryCents,
            primaryValueText = formatCents(primaryCents),
            metrics = metrics,
            emptyMessage = if (primaryCents == 0L) {
                if (metric == FinancialMetric.RECEIVABLE) {
                    "Não há valores a receber."
                } else {
                    "Hoje ainda não entrou nada."
                }
            } else {
                null
            },
            dataSource = AgentDataSource.LOCAL_ONLY,
        )
    }

    fun render(result: CustomerBalanceResult): AgentSurface.CustomerBalanceCard =
        AgentSurface.CustomerBalanceCard(
            title = "Fiado de ${result.customerName}",
            customerName = result.customerName,
            currentBalanceCents = result.currentBalanceCents,
            currentBalanceText = formatCents(result.currentBalanceCents),
            openText = "Em aberto: ${formatCents(result.openCents)}",
            overdueText = "Vencido: ${formatCents(result.overdueCents)}",
            oldestOpenText = result.oldestOpenDays?.let { "Em aberto há $it dias" },
            emptyMessage = if (result.currentBalanceCents == 0L) {
                "Este cliente não tem saldo em aberto."
            } else {
                null
            },
            dataSource = result.dataSource,
        )

    fun render(result: CustomerTimelineResult): AgentSurface.CustomerTimelineCard =
        AgentSurface.CustomerTimelineCard(
            title = "Conta de ${result.customerName}",
            customerName = result.customerName,
            currentBalanceText = result.currentBalanceText,
            items = result.items,
            emptyMessage = result.emptyMessage,
            dataSource = result.dataSource,
        )

    private fun formatCents(cents: Long): String =
        "R$ %.2f".format(Locale("pt", "BR"), cents / 100.0)
}

@Singleton
class TinoAgentBoundary @Inject constructor(
    private val financialSummaryTool: FinancialSummaryQueryTool,
    private val renderer: AgentSurfaceRenderer,
    private val toolExecutor: ToolExecutor,
    private val customerBalanceTool: CustomerBalanceQueryPort,
    private val customerTimelineTool: CustomerTimelineQueryPort,
    private val dbFirstRead: DbFirstReadCapabilities,
) : AgentQueryBoundary {
    constructor(
        financialSummaryTool: FinancialSummaryQueryTool,
        renderer: AgentSurfaceRenderer,
    ) : this(
        financialSummaryTool,
        renderer,
        UnavailableToolExecutor,
        UnavailableCustomerBalanceQuery,
        UnavailableCustomerTimelineQuery,
        UnavailableDbFirstReadCapabilities(),
    )

    constructor(
        financialSummaryTool: FinancialSummaryQueryTool,
        renderer: AgentSurfaceRenderer,
        dbFirstRead: DbFirstReadCapabilities,
    ) : this(
        financialSummaryTool,
        renderer,
        UnavailableToolExecutor,
        UnavailableCustomerBalanceQuery,
        UnavailableCustomerTimelineQuery,
        dbFirstRead,
    )

    constructor(
        financialSummaryTool: FinancialSummaryQueryTool,
        renderer: AgentSurfaceRenderer,
        toolExecutor: ToolExecutor,
    ) : this(
        financialSummaryTool,
        renderer,
        toolExecutor,
        UnavailableCustomerBalanceQuery,
        UnavailableCustomerTimelineQuery,
        UnavailableDbFirstReadCapabilities(),
    )

    constructor(
        financialSummaryTool: FinancialSummaryQueryTool,
        renderer: AgentSurfaceRenderer,
        toolExecutor: ToolExecutor,
        customerBalanceTool: CustomerBalanceQueryPort,
    ) : this(
        financialSummaryTool,
        renderer,
        toolExecutor,
        customerBalanceTool,
        UnavailableCustomerTimelineQuery,
        UnavailableDbFirstReadCapabilities(),
    )

    constructor(
        financialSummaryTool: FinancialSummaryQueryTool,
        renderer: AgentSurfaceRenderer,
        toolExecutor: ToolExecutor,
        customerBalanceTool: CustomerBalanceQueryPort,
        customerTimelineTool: CustomerTimelineQueryPort,
    ) : this(
        financialSummaryTool,
        renderer,
        toolExecutor,
        customerBalanceTool,
        customerTimelineTool,
        UnavailableDbFirstReadCapabilities(),
    )

    suspend fun ask(request: AgentRequest): AgentResponse {
        val capability = resolveCapability(request.text)
            ?: return AgentResponse.Unsupported("Ainda não consigo responder essa pergunta.")
        return ask(AgentIntent(AgentIntentSchema.VERSION, capability, AgentIntentPeriod.TODAY))
    }

    override suspend fun ask(intent: AgentIntent): AgentResponse {
        if (intent.schemaVersion != AgentIntentSchema.VERSION ||
            intent.period != AgentIntentPeriod.TODAY
        ) {
            return AgentResponse.Unsupported("Ainda não consigo responder essa pergunta.")
        }
        return when (intent.capability) {
            AgentCapability.GLOBAL_TOOL -> AgentResponse.Unsupported(
                "Não consegui preparar essa operação global. Tente falar de outra forma.",
            )
            AgentCapability.READ_FINANCIAL_SUMMARY -> {
                val result = financialSummaryTool.execute()
                AgentResponse.SurfaceReady(
                    capability = intent.capability,
                    result = result,
                    surface = renderer.render(result, intent.paymentMethod, intent.metric),
                    dataSource = AgentDataSource.LOCAL_ONLY,
                )
            }
            AgentCapability.LIST_PRODUCTS -> readList(intent) { dbFirstRead.listProducts() }
            AgentCapability.REPLENISHMENT_QUERY -> readList(intent) { dbFirstRead.listReplenishment() }
            AgentCapability.GET_PRODUCT_STOCK,
            AgentCapability.GET_PRODUCT_PRICE,
            -> readList(intent) {
                val reference = intent.productRef
                    ?: return@readList DbFirstReadResult.NotFound("Não identifiquei o produto da consulta.")
                dbFirstRead.productFact(intent.capability, reference)
            }
            AgentCapability.LIST_CUSTOMERS -> readList(intent) { dbFirstRead.listCustomers() }
            AgentCapability.LIST_RECEIVABLES -> readList(intent) { dbFirstRead.listReceivables() }
            AgentCapability.LIST_OVERDUE -> readList(intent) { dbFirstRead.listOverdue() }
            AgentCapability.ADD_CREDIT_ITEM -> previewCreditItem(intent)
            AgentCapability.REGISTER_CREDIT_PAYMENT -> previewCreditPayment(intent)
            AgentCapability.GET_CUSTOMER_BALANCE -> queryCustomerBalance(intent)
            AgentCapability.GET_CUSTOMER_TIMELINE -> queryCustomerTimeline(intent)
        }
    }

    private suspend fun readList(
        intent: AgentIntent,
        query: suspend () -> DbFirstReadResult,
    ): AgentResponse {
        return when (val result = query()) {
            is DbFirstReadResult.Ambiguous -> AgentResponse.EntityChoiceReady(
                capability = intent.capability,
                entityType = result.entityType,
                options = result.options,
                dataSource = AgentDataSource.LOCAL_ONLY,
            )
            is DbFirstReadResult.NotFound -> AgentResponse.Unsupported(result.message)
            else -> AgentResponse.ReadListReady(
                capability = intent.capability,
                result = result,
                dataSource = AgentDataSource.LOCAL_ONLY,
            )
        }
    }

    override suspend fun confirm(call: ToolCall): ToolExecutionResult =
        toolExecutor.confirm(call, confirmation = null)

    override suspend fun confirm(
        call: ToolCall,
        confirmation: MutationConfirmation?,
    ): ToolExecutionResult = toolExecutor.confirm(call, confirmation)

    override suspend fun askGlobal(call: ToolCall): AgentResponse = try {
        if (call.name.isReadOnly) {
            AgentResponse.GlobalAnswerReady(
                call = call,
                result = toolExecutor.execute(call, confirmed = true),
                dataSource = AgentDataSource.LOCAL_ONLY,
            )
        } else {
            AgentResponse.ActionPreviewReady(
                capability = AgentCapability.GLOBAL_TOOL,
                call = call,
                preview = toolExecutor.preview(call),
                dataSource = AgentDataSource.LOCAL_ONLY,
            )
        }
    } catch (error: com.tino.app.domain.voice.ToolClarificationException) {
        AgentResponse.EntityChoiceReady(
            capability = AgentCapability.GLOBAL_TOOL,
            entityType = error.argumentKey ?: "entity",
            options = error.options,
            dataSource = AgentDataSource.LOCAL_ONLY,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        AgentResponse.Unsupported(error.message ?: "Não consegui preparar essa operação.")
    }

    private suspend fun previewCreditItem(intent: AgentIntent): AgentResponse {
        val customer = intent.customerRef
            ?: return AgentResponse.Unsupported("Não identifiquei o cliente do fiado.")
        val product = intent.productRef
            ?: return AgentResponse.Unsupported("Não identifiquei o produto do fiado.")
        val quantity = intent.quantity ?: 1
        val call = ToolCall(
            name = CommerceToolName.ADD_CREDIT_ITEM,
            arguments = mapOf(
                "customer" to customer,
                "product" to product,
                "quantity" to quantity.toString(),
            ),
        )
        return try {
            AgentResponse.ActionPreviewReady(
                capability = intent.capability,
                call = call,
                preview = toolExecutor.preview(call),
                dataSource = AgentDataSource.LOCAL_ONLY,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AgentResponse.Unsupported(error.message ?: "Não consegui preparar esse fiado.")
        }
    }

    private suspend fun previewCreditPayment(intent: AgentIntent): AgentResponse {
        val customer = intent.customerRef
            ?: return AgentResponse.Unsupported("Não identifiquei o cliente do pagamento.")
        val amountCents = intent.amountCents
            ?: return AgentResponse.Unsupported("Não identifiquei o valor recebido.")
        val paymentMethod = intent.creditPaymentMethod
        if (paymentMethod == null) {
            return AgentResponse.EntityChoiceReady(
                capability = intent.capability,
                entityType = "payment_method",
                options = listOf("Dinheiro", "PIX", "Maquininha"),
                dataSource = AgentDataSource.LOCAL_ONLY,
            )
        }
        val call = ToolCall(
            name = CommerceToolName.REGISTER_CREDIT_PAYMENT,
            arguments = mapOf(
                "customer" to customer,
                "amount_cents" to amountCents.toString(),
                "payment_method" to paymentMethod.storageValue,
            ),
        )
        return try {
            AgentResponse.ActionPreviewReady(
                capability = intent.capability,
                call = call,
                preview = toolExecutor.preview(call),
                dataSource = AgentDataSource.LOCAL_ONLY,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AgentResponse.Unsupported(error.message ?: "Não consegui preparar esse pagamento.")
        }
    }

    private suspend fun queryCustomerBalance(intent: AgentIntent): AgentResponse {
        val reference = intent.customerRef
            ?: return AgentResponse.Unsupported("Não identifiquei o cliente do fiado.")
        return when (val result = customerBalanceTool.execute(reference)) {
            is CustomerBalanceQueryResult.Ready -> AgentResponse.CustomerBalanceReady(
                capability = intent.capability,
                result = result.result,
                dataSource = AgentDataSource.LOCAL_ONLY,
                customerResolutionMs = result.customerResolutionMs,
            )
            is CustomerBalanceQueryResult.Ambiguous -> AgentResponse.EntityChoiceReady(
                capability = intent.capability,
                entityType = "customer",
                options = result.options,
                dataSource = AgentDataSource.LOCAL_ONLY,
            )
            CustomerBalanceQueryResult.NotFound -> AgentResponse.Unsupported(
                "Não encontrei esse cliente. Confira o nome ou cadastre o cliente antes de consultar.",
            )
        }
    }

    private suspend fun queryCustomerTimeline(intent: AgentIntent): AgentResponse {
        val reference = intent.customerRef
            ?: return AgentResponse.Unsupported("Não identifiquei o cliente da conta.")
        return when (val result = customerTimelineTool.execute(reference)) {
            is CustomerTimelineQueryResult.Ready -> AgentResponse.CustomerTimelineReady(
                capability = intent.capability,
                result = result.result,
                dataSource = AgentDataSource.LOCAL_ONLY,
                customerResolutionMs = result.customerResolutionMs,
            )
            is CustomerTimelineQueryResult.Ambiguous -> AgentResponse.EntityChoiceReady(
                capability = intent.capability,
                entityType = "customer",
                options = result.options,
                dataSource = AgentDataSource.LOCAL_ONLY,
            )
            CustomerTimelineQueryResult.NotFound -> AgentResponse.Unsupported(
                "Não encontrei esse cliente. Confira o nome ou cadastre o cliente antes de consultar.",
            )
        }
    }

    private fun resolveCapability(text: String): AgentCapability? {
        val normalized = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()
        return if (
            normalized == "quanto entrou hoje" ||
                normalized == "quanto entrou hoje no comercio" ||
                normalized == "quanto entrou hoje no meu comercio"
        ) {
            AgentCapability.READ_FINANCIAL_SUMMARY
        } else {
            null
        }
    }

}

private object UnavailableToolExecutor : ToolExecutor {
    override suspend fun preview(call: ToolCall): ToolPreview =
        error("A capacidade de comércio não está disponível neste teste.")

    override suspend fun execute(call: ToolCall, confirmed: Boolean) =
        error("A capacidade de comércio não está disponível neste teste.")
}

private object UnavailableCustomerBalanceQuery : CustomerBalanceQueryPort {
    override suspend fun execute(reference: String): CustomerBalanceQueryResult =
        CustomerBalanceQueryResult.NotFound
}

private object UnavailableCustomerTimelineQuery : CustomerTimelineQueryPort {
    override suspend fun execute(reference: String): CustomerTimelineQueryResult =
        CustomerTimelineQueryResult.NotFound
}
