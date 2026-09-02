package com.tino.app.interfaceadapter.a2ui

/**
 * Closed presentation contract. The agent names a catalog and facts;
 * Android chooses kind, size, motion and chrome. No coordinates, colors
 * or animation curves travel on the wire.
 */
enum class A2uiSurfaceKind {
    INLINE,
    BOTTOM_RISE,
    FULL_SCREEN,
    FLOATING,
}

enum class A2uiSurfaceSize {
    COMPACT,
    MEDIUM,
    LARGE,
    FULL,
}

enum class A2uiSurfaceStage {
    PEEK,
    EXPANDED,
    FULL,
}

data class A2uiSurfaceSpec(
    val kind: A2uiSurfaceKind,
    val size: A2uiSurfaceSize,
)

object A2uiSurfacePolicy {
    fun forComponent(type: String): A2uiSurfaceSpec {
        val size = sizeFor(type)
        val kind = if (size == A2uiSurfaceSize.FULL) {
            A2uiSurfaceKind.FULL_SCREEN
        } else {
            A2uiSurfaceKind.BOTTOM_RISE
        }
        return A2uiSurfaceSpec(kind, size)
    }

    fun sizeFor(type: String): A2uiSurfaceSize = when (type) {
        TinoA2UiComponentCatalog.ACTION_CONFIRMATION,
        TinoA2UiComponentCatalog.PAYMENT_PREVIEW,
        TinoA2UiComponentCatalog.STOCK_ENTRY_PREVIEW,
        TinoA2UiComponentCatalog.PRICE_CHANGE_PREVIEW,
        TinoA2UiComponentCatalog.CREDIT_PREVIEW,
        TinoA2UiComponentCatalog.OPERATION_SUCCESS,
        TinoA2UiComponentCatalog.UNDO_ACTION,
        TinoA2UiComponentCatalog.ERROR_RECOVERY,
        TinoA2UiComponentCatalog.ENTITY_CHOICE,
        TinoCustomComponentCatalog.CONFIRMATION_CARD,
        TinoCustomComponentCatalog.STATUS_CARD,
        TinoCustomComponentCatalog.QUICK_QUERY_CARD,
        TinoCustomComponentCatalog.EMPTY_STATE_CARD,
        TinoCustomComponentCatalog.ACTION_LIST_CARD,
        -> A2uiSurfaceSize.COMPACT

        TinoA2UiComponentCatalog.CUSTOMER_TIMELINE_CARD,
        TinoA2UiComponentCatalog.FINANCIAL_SUMMARY_CARD,
        TinoA2UiComponentCatalog.SUPPLIER_SUMMARY,
        TinoA2UiComponentCatalog.INSIGHT_CARD,
        TinoA2UiComponentCatalog.STOCK_STATUS,
        TinoA2UiComponentCatalog.PRODUCT_STOCK,
        TinoA2UiComponentCatalog.PRODUCT_PRICE,
        TinoA2UiComponentCatalog.CUSTOMER_CONTACT,
        TinoA2UiComponentCatalog.CUSTOMER_BALANCE_CARD,
        TinoCustomComponentCatalog.SUMMARY_CARD,
        TinoCustomComponentCatalog.TIMELINE_CARD,
        TinoCustomComponentCatalog.SALE_CARD,
        TinoCustomComponentCatalog.METRIC_CARD,
        -> A2uiSurfaceSize.MEDIUM

        TinoA2UiComponentCatalog.PRODUCT_LIST,
        TinoA2UiComponentCatalog.PRODUCT_REPLENISHMENT,
        TinoA2UiComponentCatalog.CUSTOMER_LIST,
        TinoA2UiComponentCatalog.RECEIVABLES_LIST,
        TinoA2UiComponentCatalog.OVERDUE_LIST,
        TinoCustomComponentCatalog.CATALOG_LIST_CARD,
        TinoCustomComponentCatalog.CATALOG_CARD,
        TinoCustomComponentCatalog.DEBT_CARD,
        TinoCustomComponentCatalog.INVENTORY_ALERT_CARD,
        TinoCustomComponentCatalog.PRODUCT_CARD,
        TinoCustomComponentCatalog.CUSTOMER_CARD,
        -> A2uiSurfaceSize.LARGE

        else -> A2uiSurfaceSize.MEDIUM
    }

    fun initialStage(size: A2uiSurfaceSize): A2uiSurfaceStage = when (size) {
        A2uiSurfaceSize.COMPACT -> A2uiSurfaceStage.PEEK
        A2uiSurfaceSize.MEDIUM, A2uiSurfaceSize.LARGE -> A2uiSurfaceStage.EXPANDED
        A2uiSurfaceSize.FULL -> A2uiSurfaceStage.FULL
    }

    fun fraction(stage: A2uiSurfaceStage, size: A2uiSurfaceSize): Float = when (stage) {
        A2uiSurfaceStage.PEEK -> 0.40f
        A2uiSurfaceStage.EXPANDED -> when (size) {
            A2uiSurfaceSize.COMPACT -> 0.42f
            A2uiSurfaceSize.MEDIUM -> 0.62f
            A2uiSurfaceSize.LARGE -> 0.78f
            A2uiSurfaceSize.FULL -> 0.86f
        }
        A2uiSurfaceStage.FULL -> 0.94f
    }

    fun titleFor(component: A2uiComponent): String = when (component) {
        is A2uiComponent.FinancialSummaryCard -> component.title
        is A2uiComponent.EntityChoice -> component.title
        is A2uiComponent.ActionConfirmation -> component.title
        is A2uiComponent.CustomerBalanceCard -> component.title
        is A2uiComponent.CustomerTimelineCard -> component.title
        is A2uiComponent.ReadListCard -> component.title
        is A2uiComponent.InsightCard -> component.title
        is A2uiComponent.ErrorStatusCard -> component.title
        is A2uiComponent.Unsupported -> "TINO"
    }

    fun subtitleFor(component: A2uiComponent): String? = when (component) {
        is A2uiComponent.ReadListCard ->
            if (component.items.isEmpty()) component.emptyMessage
            else "${component.items.size} ${if (component.items.size == 1) "item" else "itens"}"
        is A2uiComponent.EntityChoice -> component.prompt
        is A2uiComponent.CustomerBalanceCard -> component.customerName
        is A2uiComponent.CustomerTimelineCard -> component.customerName
        is A2uiComponent.ErrorStatusCard -> component.message
        else -> null
    }
}
