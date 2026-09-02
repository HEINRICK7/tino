package com.tino.app.interfaceadapter.a2ui

object TinoA2UiProtocol {
    const val SCHEMA = "tino.a2ui"
    const val VERSION = 1
}

data class A2uiMessage(
    val messageId: String,
    val component: A2uiComponent,
    val schema: String = TinoA2UiProtocol.SCHEMA,
    val version: Int = TinoA2UiProtocol.VERSION,
) {
    val hasSupportedEnvelope: Boolean
        get() = schema == TinoA2UiProtocol.SCHEMA && version == TinoA2UiProtocol.VERSION
}

sealed interface A2uiComponent {
    val type: String

    data class FinancialSummaryCard(
        val title: String,
        val primaryLabel: String,
        val primaryValueText: String,
        val metrics: List<A2uiMetric>,
        val emptyMessage: String?,
        val dataSource: String,
    ) : A2uiComponent {
        override val type: String = TinoA2UiComponentCatalog.FINANCIAL_SUMMARY_CARD
    }

    /** A safe, label-based choice. The selected label is resolved again locally. */
    data class EntityChoice(
        val title: String,
        val entityType: String,
        val prompt: String,
        val options: List<A2uiChoiceOption>,
    ) : A2uiComponent {
        override val type: String = TinoA2UiComponentCatalog.ENTITY_CHOICE
    }

    data class ActionConfirmation(
        val title: String,
        val detail: String,
        val confirmLabel: String,
        val complete: Boolean,
        val semanticType: String = TinoA2UiComponentCatalog.ACTION_CONFIRMATION,
        val operationId: String? = null,
        val activityId: String? = null,
        val undoAvailable: Boolean = false,
        val undoLabel: String = "DESFAZER",
        val entityName: String? = null,
        val primaryValueText: String? = null,
        val detailRows: List<A2uiDetailRow> = emptyList(),
        val iconKey: String? = null,
    ) : A2uiComponent {
        override val type: String = semanticType
    }

    data class CustomerBalanceCard(
        val title: String,
        val customerName: String,
        val currentBalanceText: String,
        val openText: String,
        val overdueText: String,
        val oldestOpenText: String?,
        val emptyMessage: String?,
        val dataSource: String,
    ) : A2uiComponent {
        override val type: String = TinoA2UiComponentCatalog.CUSTOMER_BALANCE_CARD
    }

    data class CustomerTimelineCard(
        val title: String,
        val customerName: String,
        val currentBalanceText: String,
        val items: List<A2uiTimelineItem>,
        val emptyMessage: String?,
        val dataSource: String,
    ) : A2uiComponent {
        override val type: String = TinoA2UiComponentCatalog.CUSTOMER_TIMELINE_CARD
    }

    data class ReadListCard(
        val title: String,
        val items: List<A2uiListItem>,
        val emptyMessage: String?,
        val dataSource: String,
        override val type: String,
    ) : A2uiComponent

    /** Grounded answer produced by the Intelligence Runtime. */
    data class InsightCard(
        val title: String,
        val answer: String,
        val status: String,
        val evidence: List<A2uiDetailRow>,
        val limitations: List<String>,
        val dataSource: String,
    ) : A2uiComponent {
        override val type: String = TinoA2UiComponentCatalog.INSIGHT_CARD
    }

    data class ErrorStatusCard(
        val title: String,
        val message: String,
        val retryLabel: String = "TENTAR DE NOVO",
    ) : A2uiComponent {
        override val type: String = TinoA2UiComponentCatalog.ERROR_RECOVERY
    }

    /** Untrusted or unsupported wire components are data, never executable instructions. */
    data class Unsupported(
        override val type: String,
        val reason: String,
    ) : A2uiComponent
}

data class A2uiMetric(
    val key: String,
    val label: String,
    val valueText: String,
)

data class A2uiDetailRow(
    val label: String,
    val value: String,
)

data class A2uiChoiceOption(
    val label: String,
)

data class A2uiTimelineItem(
    val dateText: String,
    val label: String,
    val amountText: String,
)

/** Visual meaning carried by a semantic result; layout remains renderer-owned. */
enum class A2uiVisualStatus {
    NORMAL,
    SUCCESS,
    WARNING,
    ERROR,
    INFO,
    CREDIT,
}

data class A2uiListItem(
    val title: String,
    val primaryText: String,
    val secondaryText: String?,
    val context: String? = null,
    val supportingText: String? = null,
    val status: A2uiVisualStatus = A2uiVisualStatus.NORMAL,
    val iconKey: String? = null,
    val actionId: String? = null,
)

object TinoA2UiComponentCatalog {
    const val FINANCIAL_SUMMARY_CARD = "financial_summary_card"
    const val ENTITY_CHOICE = "entity_choice"
    const val ACTION_CONFIRMATION = "action_confirmation"
    const val OPERATION_SUCCESS = "operation_success"
    const val UNDO_ACTION = "undo_action"
    const val ERROR_RECOVERY = "error_recovery"
    const val PAYMENT_PREVIEW = "payment_preview"
    const val STOCK_ENTRY_PREVIEW = "stock_entry_preview"
    const val PRICE_CHANGE_PREVIEW = "price_change_preview"
    const val CREDIT_PREVIEW = "credit_preview"
    const val STOCK_STATUS = "stock_status"
    const val SUPPLIER_SUMMARY = "supplier_summary"
    const val CLARIFICATION_SELECTOR = "clarification_selector"
    const val CUSTOMER_BALANCE_CARD = "customer_balance_card"
    const val CUSTOMER_TIMELINE_CARD = "customer_timeline_card"
    const val PRODUCT_LIST = "product_list"
    const val PRODUCT_REPLENISHMENT = "product_replenishment"
    const val PRODUCT_STOCK = "product_stock"
    const val PRODUCT_PRICE = "product_price"
    const val CUSTOMER_LIST = "customer_list"
    const val CUSTOMER_CONTACT = "customer_contact"
    const val RECEIVABLES_LIST = "receivables_list"
    const val OVERDUE_LIST = "overdue_list"
    const val INSIGHT_CARD = "insight_card"

    val allowlist: Set<String> = setOf(
        FINANCIAL_SUMMARY_CARD,
        ENTITY_CHOICE,
        ACTION_CONFIRMATION,
        OPERATION_SUCCESS,
        UNDO_ACTION,
        ERROR_RECOVERY,
        PAYMENT_PREVIEW,
        STOCK_ENTRY_PREVIEW,
        PRICE_CHANGE_PREVIEW,
        CREDIT_PREVIEW,
        STOCK_STATUS,
        SUPPLIER_SUMMARY,
        CLARIFICATION_SELECTOR,
        CUSTOMER_BALANCE_CARD,
        CUSTOMER_TIMELINE_CARD,
        PRODUCT_LIST,
        PRODUCT_REPLENISHMENT,
        PRODUCT_STOCK,
        PRODUCT_PRICE,
        CUSTOMER_LIST,
        CUSTOMER_CONTACT,
        RECEIVABLES_LIST,
        OVERDUE_LIST,
        INSIGHT_CARD,
    )

    fun isAllowed(type: String): Boolean = type in allowlist
}
