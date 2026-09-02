package com.tino.app.domain.agent

import com.tino.app.domain.voice.ToolCall
import com.tino.app.domain.commerce.PaymentMethod
import java.util.Locale

data class RawAgentIntent(
    val schema: String?,
    val schemaVersion: Int?,
    val capability: String?,
    val period: String?,
    val customerRef: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val productRef: String? = null,
    val supplierRef: String? = null,
    val newPriceCents: Long? = null,
    val unitCostCents: Long? = null,
    val quantity: Int? = null,
    val amountCents: Long? = null,
    val paymentMethod: String? = null,
    val metric: String? = null,
    val keys: Set<String>,
)

data class AgentIntentDebugInfo(
    val code: String,
    val capability: String?,
    val observedKeys: Set<String>,
    val unexpectedKeys: Set<String> = emptySet(),
    val rawOutput: String? = null,
)

enum class AgentIntentPeriod {
    TODAY,
}

enum class FinancialPaymentMethod {
    ALL,
    CASH,
    PIX,
    CARD,
}

enum class FinancialMetric {
    RECEIVED,
    RECEIVABLE,
    SUMMARY,
}

data class AgentIntent(
    val schemaVersion: Int,
    val capability: AgentCapability,
    val period: AgentIntentPeriod,
    val customerRef: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val productRef: String? = null,
    val supplierRef: String? = null,
    val newPriceCents: Long? = null,
    val unitCostCents: Long? = null,
    val quantity: Int? = null,
    val amountCents: Long? = null,
    val paymentMethod: FinancialPaymentMethod = FinancialPaymentMethod.ALL,
    val creditPaymentMethod: PaymentMethod? = null,
    val metric: FinancialMetric = FinancialMetric.RECEIVED,
    val globalToolCall: ToolCall? = null,
)

sealed interface AgentIntentResult {
    data class Supported(val intent: AgentIntent) : AgentIntentResult
    data class Unsupported(
        val reason: String,
        val userMessage: String = reason,
        val debug: AgentIntentDebugInfo? = null,
    ) : AgentIntentResult
}

object AgentIntentSchema {
    const val VERSION = 1
    const val SCHEMA = "tino.agent-intent"

    private val allowedKeys = setOf(
        "schema",
        "schema_version",
        "capability",
        "period",
        "customer_ref",
        "customer_name",
        "phone",
        "product_ref",
        "supplier_ref",
        "new_price_cents",
        "unit_cost_cents",
        "unit_cost_cents",
        "quantity",
        "amount_cents",
        "payment_method",
        "metric",
    )
    private val allowedCapabilities = setOf(
        AgentCapability.READ_FINANCIAL_SUMMARY,
        AgentCapability.LIST_PRODUCTS,
        AgentCapability.REPLENISHMENT_QUERY,
        AgentCapability.GET_PRODUCT_STOCK,
        AgentCapability.GET_PRODUCT_PRICE,
        AgentCapability.LIST_CUSTOMERS,
        AgentCapability.LIST_SUPPLIERS,
        AgentCapability.LIST_RECEIVABLES,
        AgentCapability.LIST_OVERDUE,
        AgentCapability.ADD_CREDIT_ITEM,
        AgentCapability.GET_CUSTOMER_BALANCE,
        AgentCapability.GET_CUSTOMER_TIMELINE,
        AgentCapability.GET_CUSTOMER_CONTACT,
        AgentCapability.CREATE_CUSTOMER,
        AgentCapability.UPDATE_PRODUCT_PRICE,
        AgentCapability.REGISTER_STOCK_ENTRY,
        AgentCapability.REGISTER_CREDIT_PAYMENT,
    )

    fun validate(raw: RawAgentIntent): AgentIntentResult {
        if (raw.schemaVersion != VERSION) {
            return AgentIntentResult.Unsupported(
                reason = "UNSUPPORTED_SCHEMA_VERSION",
                userMessage = "Não consegui entender exatamente o que você quer fazer.",
            )
        }
        if (raw.schema != SCHEMA) {
            return AgentIntentResult.Unsupported(
                reason = "UNSUPPORTED_SCHEMA",
                userMessage = "Não consegui entender exatamente o que você quer fazer.",
            )
        }
        val capability = when (raw.capability?.trim()?.uppercase(Locale.ROOT)) {
            "READ_CUSTOMER_BALANCE" -> AgentCapability.GET_CUSTOMER_BALANCE
            "RECEIVE_CREDIT_PAYMENT" -> AgentCapability.REGISTER_CREDIT_PAYMENT
            "CHANGE_PRODUCT_PRICE", "UPDATE_PRODUCT_PRICE" -> AgentCapability.UPDATE_PRODUCT_PRICE
            else -> AgentCapability.entries.firstOrNull {
                it.name.equals(raw.capability?.trim(), ignoreCase = true)
            }
        } ?: return AgentIntentResult.Unsupported(
            reason = "UNSUPPORTED_CAPABILITY",
            userMessage = "Não consegui entender exatamente o que você quer fazer.",
        )
        if (capability !in allowedCapabilities) {
            return AgentIntentResult.Unsupported(
                reason = "UNSUPPORTED_CAPABILITY",
                userMessage = "Não consegui entender exatamente o que você quer fazer.",
            )
        }
        val allowedKeysForCapability = when (capability) {
            AgentCapability.GLOBAL_TOOL -> allowedKeys
            AgentCapability.READ_FINANCIAL_SUMMARY ->
                allowedKeys - "customer_ref" - "product_ref" - "quantity" - "amount_cents"
            AgentCapability.ADD_CREDIT_ITEM -> allowedKeys - "payment_method" - "metric" - "amount_cents"
            AgentCapability.REGISTER_CREDIT_PAYMENT -> allowedKeys - "product_ref" - "quantity" - "metric"
            AgentCapability.CREATE_CUSTOMER -> allowedKeys - "customer_ref" - "product_ref" - "quantity" - "amount_cents" - "payment_method" - "metric"
            AgentCapability.UPDATE_PRODUCT_PRICE -> allowedKeys - "customer_ref" - "customer_name" - "phone" - "quantity" - "amount_cents" - "payment_method" - "metric"
            AgentCapability.REGISTER_STOCK_ENTRY ->
                allowedKeys - "customer_ref" - "customer_name" - "phone" - "new_price_cents" - "amount_cents" - "payment_method" - "metric"
            AgentCapability.GET_PRODUCT_STOCK,
            AgentCapability.GET_PRODUCT_PRICE,
            -> allowedKeys - "payment_method" - "metric" - "customer_ref" - "supplier_ref" - "quantity" - "amount_cents" - "new_price_cents"
            AgentCapability.LIST_CUSTOMERS,
            AgentCapability.LIST_RECEIVABLES,
            AgentCapability.LIST_OVERDUE,
            -> allowedKeys - "payment_method" - "metric" - "customer_ref" - "product_ref" - "supplier_ref" - "quantity" - "amount_cents"
            AgentCapability.LIST_SUPPLIERS ->
                allowedKeys - "payment_method" - "metric" - "customer_ref" - "product_ref" - "quantity" - "amount_cents"
            AgentCapability.LIST_PRODUCTS ->
                allowedKeys - "payment_method" - "metric" - "customer_ref" - "supplier_ref" - "quantity" - "amount_cents"
            AgentCapability.REPLENISHMENT_QUERY ->
                allowedKeys - "payment_method" - "metric" - "customer_ref" - "supplier_ref" - "quantity" - "amount_cents"
            AgentCapability.GET_CUSTOMER_BALANCE,
            AgentCapability.GET_CUSTOMER_TIMELINE,
            AgentCapability.GET_CUSTOMER_CONTACT,
            -> allowedKeys - "payment_method" - "metric" - "product_ref" - "quantity" - "amount_cents"
        }
        val unexpectedKeys = raw.keys - allowedKeysForCapability
        if (unexpectedKeys.isNotEmpty()) {
            return AgentIntentResult.Unsupported(
                reason = "UNKNOWN_INTENT_FIELDS",
                userMessage = when (capability) {
                    AgentCapability.ADD_CREDIT_ITEM -> "Não consegui entender exatamente o que você quer anotar."
                    AgentCapability.REGISTER_CREDIT_PAYMENT -> "Não consegui entender exatamente qual pagamento você quer registrar."
                    else -> "Não consegui entender exatamente o que você quer consultar."
                },
                debug = AgentIntentDebugInfo(
                    code = "UNKNOWN_INTENT_FIELDS",
                    capability = raw.capability,
                    observedKeys = raw.keys,
                    unexpectedKeys = unexpectedKeys,
                ),
            )
        }
        val period = AgentIntentPeriod.entries.firstOrNull {
            it.name.equals(raw.period?.trim(), ignoreCase = true)
        } ?: return AgentIntentResult.Unsupported(
            reason = "UNSUPPORTED_PERIOD",
            userMessage = "Não consegui identificar o período dessa consulta.",
        )
        if (capability == AgentCapability.CREATE_CUSTOMER) {
            val customerName = raw.customerName?.trim().orEmpty()
            if (customerName.isBlank()) {
                return AgentIntentResult.Unsupported(
                    reason = "MISSING_CUSTOMER_NAME",
                    userMessage = "Preciso do nome do cliente para preparar o cadastro.",
                )
            }
            return AgentIntentResult.Supported(
                AgentIntent(
                    schemaVersion = VERSION,
                    capability = capability,
                    period = period,
                    customerName = customerName,
                    customerPhone = raw.customerPhone?.trim()?.ifBlank { null },
                ),
            )
        }
        if (capability == AgentCapability.UPDATE_PRODUCT_PRICE) {
            val productRef = raw.productRef?.trim().orEmpty()
            val newPriceCents = raw.newPriceCents
            if (productRef.isBlank() || newPriceCents == null || newPriceCents <= 0L) {
                return AgentIntentResult.Unsupported(
                    reason = "INCOMPLETE_PRODUCT_PRICE_UPDATE",
                    userMessage = "Preciso do produto e do novo preço para preparar a alteração.",
                )
            }
            return AgentIntentResult.Supported(
                AgentIntent(
                    schemaVersion = VERSION,
                    capability = capability,
                    period = period,
                    productRef = productRef,
                    newPriceCents = newPriceCents,
                ),
            )
        }
        if (capability == AgentCapability.REGISTER_STOCK_ENTRY) {
            val productRef = raw.productRef?.trim().orEmpty()
            val quantity = raw.quantity
            val unitCostCents = raw.unitCostCents
            if (productRef.isBlank() || quantity == null || quantity <= 0 || unitCostCents == null || unitCostCents < 0L) {
                return AgentIntentResult.Unsupported(
                    reason = "INCOMPLETE_STOCK_ENTRY",
                    userMessage = "Preciso do produto, quantidade e custo unitário para preparar a entrada.",
                )
            }
            return AgentIntentResult.Supported(
                AgentIntent(
                    schemaVersion = VERSION,
                    capability = capability,
                    period = period,
                    productRef = productRef,
                    quantity = quantity,
                    unitCostCents = unitCostCents,
                    supplierRef = raw.supplierRef?.trim()?.ifBlank { null },
                ),
            )
        }
        if (capability == AgentCapability.ADD_CREDIT_ITEM) {
            val customerRef = raw.customerRef?.trim().orEmpty()
            val productRef = raw.productRef?.trim().orEmpty()
            val quantity = raw.quantity ?: 1
            if (customerRef.isBlank() || productRef.isBlank() || quantity <= 0) {
                return AgentIntentResult.Unsupported(
                    reason = "INCOMPLETE_CREDIT_ITEM",
                    userMessage = "Preciso do cliente, produto e quantidade para preparar o fiado.",
                )
            }
            return AgentIntentResult.Supported(
                AgentIntent(
                    schemaVersion = VERSION,
                    capability = capability,
                    period = period,
                    customerRef = customerRef,
                    productRef = productRef,
                    quantity = quantity,
                ),
            )
        }
        if (capability == AgentCapability.REGISTER_CREDIT_PAYMENT) {
            val customerRef = raw.customerRef?.trim().orEmpty()
            val amountCents = raw.amountCents
            if (customerRef.isBlank() || amountCents == null || amountCents <= 0L) {
                return AgentIntentResult.Unsupported(
                    reason = "INCOMPLETE_CREDIT_PAYMENT",
                    userMessage = "Preciso do cliente e do valor recebido para preparar a baixa do fiado.",
                )
            }
            val paymentMethod = raw.paymentMethod?.let { value ->
                parseCreditPaymentMethod(value) ?: return AgentIntentResult.Unsupported(
                    reason = "UNSUPPORTED_PAYMENT_METHOD",
                    userMessage = "Não consegui identificar como você recebeu esse pagamento.",
                )
            }
            return AgentIntentResult.Supported(
                AgentIntent(
                    schemaVersion = VERSION,
                    capability = capability,
                    period = period,
                    customerRef = customerRef,
                    amountCents = amountCents,
                    creditPaymentMethod = paymentMethod,
                ),
            )
        }
        if (capability == AgentCapability.GET_PRODUCT_STOCK ||
            capability == AgentCapability.GET_PRODUCT_PRICE ||
            capability == AgentCapability.REPLENISHMENT_QUERY && !raw.productRef.isNullOrBlank() ||
            capability == AgentCapability.LIST_PRODUCTS && !raw.productRef.isNullOrBlank()
        ) {
            val productRef = raw.productRef?.trim().orEmpty()
            if (productRef.isBlank()) {
                return AgentIntentResult.Unsupported(
                    reason = "MISSING_PRODUCT_REFERENCE",
                    userMessage = "Preciso saber qual produto você quer consultar.",
                )
            }
            return AgentIntentResult.Supported(
                AgentIntent(
                    schemaVersion = VERSION,
                    capability = capability,
                    period = period,
                    productRef = productRef,
                ),
            )
        }
        if (capability == AgentCapability.LIST_SUPPLIERS && !raw.supplierRef.isNullOrBlank()) {
            return AgentIntentResult.Supported(
                AgentIntent(
                    schemaVersion = VERSION,
                    capability = capability,
                    period = period,
                    supplierRef = raw.supplierRef.trim(),
                ),
            )
        }
        if (capability == AgentCapability.LIST_PRODUCTS ||
            capability == AgentCapability.LIST_CUSTOMERS ||
            capability == AgentCapability.LIST_SUPPLIERS ||
            capability == AgentCapability.LIST_RECEIVABLES ||
            capability == AgentCapability.LIST_OVERDUE
        ) {
            return AgentIntentResult.Supported(
                AgentIntent(
                    schemaVersion = VERSION,
                    capability = capability,
                    period = period,
                ),
            )
        }
        if (capability == AgentCapability.GET_CUSTOMER_BALANCE ||
            capability == AgentCapability.GET_CUSTOMER_TIMELINE
            || capability == AgentCapability.GET_CUSTOMER_CONTACT
        ) {
            val customerRef = raw.customerRef?.trim().orEmpty()
            if (customerRef.isBlank()) {
                return AgentIntentResult.Unsupported(
                    reason = "MISSING_CUSTOMER_REFERENCE",
                    userMessage = if (capability == AgentCapability.GET_CUSTOMER_CONTACT) {
                        "Preciso saber de qual cliente você quer consultar o contato."
                    } else {
                        "Preciso saber de qual cliente você quer consultar o fiado."
                    },
                )
            }
            return AgentIntentResult.Supported(
                AgentIntent(
                    schemaVersion = VERSION,
                    capability = capability,
                    period = period,
                    customerRef = customerRef,
                ),
            )
        }
        val paymentMethod = raw.paymentMethod?.let { value ->
            FinancialPaymentMethod.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: return AgentIntentResult.Unsupported(
                    reason = "UNSUPPORTED_PAYMENT_METHOD",
                    userMessage = "Não consegui identificar a forma de pagamento.",
                )
        } ?: FinancialPaymentMethod.ALL
        val metric = raw.metric?.let { value ->
            FinancialMetric.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: return AgentIntentResult.Unsupported(
                    reason = "UNSUPPORTED_FINANCIAL_METRIC",
                    userMessage = "Não consegui identificar o que você quer consultar.",
                )
        } ?: FinancialMetric.RECEIVED
        return AgentIntentResult.Supported(
            AgentIntent(
                schemaVersion = VERSION,
                capability = capability,
                period = period,
                paymentMethod = paymentMethod,
                metric = metric,
            ),
        )
    }

    private fun parseCreditPaymentMethod(value: String): PaymentMethod? = when (value.trim().lowercase()) {
        "cash", "dinheiro" -> PaymentMethod.CASH
        "pix" -> PaymentMethod.PIX
        "card", "maquininha", "cartao", "cartão" -> PaymentMethod.CARD
        else -> null
    }
}

interface AgentIntentInterpreter {
    suspend fun interpret(input: String): AgentIntentResult

    /** Optional runtime context used to keep model/tool vocabulary within the active profile. */
    suspend fun interpret(
        input: String,
        availableCapabilities: Set<TinoCapabilityId>,
    ): AgentIntentResult = interpret(input)
}

sealed interface AgentA2uiResponse {
    val latencyMs: Long
    val intentLatencyMs: Long
    val capabilityLatencyMs: Long
    val a2uiLatencyMs: Long
    val fastRouterHit: Boolean
        get() = false
    val fastRouterMs: Long
        get() = 0L
    val commandRouterHit: Boolean
        get() = false
    val commandRouterMs: Long
        get() = 0L

    data class Ready(
        val intent: AgentIntent,
        val result: FinancialSummaryResult,
        val message: com.tino.app.interfaceadapter.a2ui.A2uiMessage,
        override val latencyMs: Long,
        override val intentLatencyMs: Long,
        override val capabilityLatencyMs: Long = 0L,
        override val a2uiLatencyMs: Long = 0L,
        override val fastRouterHit: Boolean = false,
        override val fastRouterMs: Long = 0L,
        override val commandRouterHit: Boolean = false,
        override val commandRouterMs: Long = 0L,
    ) : AgentA2uiResponse

    data class ActionPreview(
        val intent: AgentIntent,
        val call: com.tino.app.domain.voice.ToolCall,
        val preview: com.tino.app.domain.voice.ToolPreview,
        val message: com.tino.app.interfaceadapter.a2ui.A2uiMessage,
        override val latencyMs: Long,
        override val intentLatencyMs: Long,
        override val capabilityLatencyMs: Long = 0L,
        override val a2uiLatencyMs: Long = 0L,
        override val fastRouterHit: Boolean = false,
        override val fastRouterMs: Long = 0L,
        override val commandRouterHit: Boolean = false,
        override val commandRouterMs: Long = 0L,
    ) : AgentA2uiResponse

    data class ActionCompleted(
        val intent: AgentIntent,
        val result: com.tino.app.domain.voice.ToolExecutionResult,
        val message: com.tino.app.interfaceadapter.a2ui.A2uiMessage,
        val activityId: String? = null,
        override val latencyMs: Long,
        override val intentLatencyMs: Long,
        override val capabilityLatencyMs: Long = 0L,
        override val a2uiLatencyMs: Long = 0L,
        override val fastRouterHit: Boolean = false,
        override val fastRouterMs: Long = 0L,
        override val commandRouterHit: Boolean = false,
        override val commandRouterMs: Long = 0L,
    ) : AgentA2uiResponse

    data class CustomerBalanceReady(
        val intent: AgentIntent,
        val result: CustomerBalanceResult,
        val message: com.tino.app.interfaceadapter.a2ui.A2uiMessage,
        val customerResolutionMs: Long,
        override val latencyMs: Long,
        override val intentLatencyMs: Long,
        override val capabilityLatencyMs: Long = 0L,
        override val a2uiLatencyMs: Long = 0L,
        override val fastRouterHit: Boolean = false,
        override val fastRouterMs: Long = 0L,
        override val commandRouterHit: Boolean = false,
        override val commandRouterMs: Long = 0L,
    ) : AgentA2uiResponse

    data class CustomerTimelineReady(
        val intent: AgentIntent,
        val result: CustomerTimelineResult,
        val message: com.tino.app.interfaceadapter.a2ui.A2uiMessage,
        val customerResolutionMs: Long,
        override val latencyMs: Long,
        override val intentLatencyMs: Long,
        override val capabilityLatencyMs: Long = 0L,
        override val a2uiLatencyMs: Long = 0L,
        override val fastRouterHit: Boolean = false,
        override val fastRouterMs: Long = 0L,
        override val commandRouterHit: Boolean = false,
        override val commandRouterMs: Long = 0L,
    ) : AgentA2uiResponse

    data class ReadListReady(
        val intent: AgentIntent,
        val result: DbFirstReadResult,
        val message: com.tino.app.interfaceadapter.a2ui.A2uiMessage,
        override val latencyMs: Long,
        override val intentLatencyMs: Long,
        override val capabilityLatencyMs: Long = 0L,
        override val a2uiLatencyMs: Long = 0L,
        override val fastRouterHit: Boolean = false,
        override val fastRouterMs: Long = 0L,
        override val commandRouterHit: Boolean = false,
        override val commandRouterMs: Long = 0L,
    ) : AgentA2uiResponse

    data class IntelligenceReady(
        val response: com.tino.app.domain.intelligence.IntelligenceResponse,
        val message: com.tino.app.interfaceadapter.a2ui.A2uiMessage,
        override val latencyMs: Long,
        override val intentLatencyMs: Long,
        override val capabilityLatencyMs: Long = 0L,
        override val a2uiLatencyMs: Long = 0L,
        override val fastRouterHit: Boolean = false,
        override val fastRouterMs: Long = 0L,
        override val commandRouterHit: Boolean = false,
        override val commandRouterMs: Long = 0L,
    ) : AgentA2uiResponse

    data class EntityChoice(
        val intent: AgentIntent,
        val entityType: String,
        val options: List<String>,
        val message: com.tino.app.interfaceadapter.a2ui.A2uiMessage,
        override val latencyMs: Long,
        override val intentLatencyMs: Long,
        override val capabilityLatencyMs: Long = 0L,
        override val a2uiLatencyMs: Long = 0L,
        override val fastRouterHit: Boolean = false,
        override val fastRouterMs: Long = 0L,
        override val commandRouterHit: Boolean = false,
        override val commandRouterMs: Long = 0L,
    ) : AgentA2uiResponse

    data class Unsupported(
        val message: String,
        val debug: AgentIntentDebugInfo? = null,
        override val latencyMs: Long,
        override val intentLatencyMs: Long,
        override val capabilityLatencyMs: Long = 0L,
        override val a2uiLatencyMs: Long = 0L,
        override val fastRouterHit: Boolean = false,
        override val fastRouterMs: Long = 0L,
        override val commandRouterHit: Boolean = false,
        override val commandRouterMs: Long = 0L,
    ) : AgentA2uiResponse
}
