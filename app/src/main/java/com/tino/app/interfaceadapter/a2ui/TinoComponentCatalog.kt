package com.tino.app.interfaceadapter.a2ui

/** Semantic groups keep the catalog extensible without exposing layout details. */
enum class TinoComponentGroup {
    LAYOUT,
    DISPLAY,
    BUSINESS,
    INTELLIGENCE,
    INTERACTION,
    OPERATIONS,
}

enum class TinoComponentPropType {
    TEXT,
    MONEY,
    NUMBER,
    BOOLEAN,
}

enum class TinoActionKind {
    UI_LOCAL,
    AGENT,
}

data class TinoActionDescriptor(
    val name: String,
    val kind: TinoActionKind,
    val allowedPayloadKeys: Set<String> = emptySet(),
    val requiredPayloadKeys: Set<String> = emptySet(),
)

data class TinoComponentPropDescriptor(
    val name: String,
    val type: TinoComponentPropType,
    val required: Boolean = false,
)

data class TinoComponentDescriptor(
    val type: String,
    val group: TinoComponentGroup,
    val version: Int = 1,
    val props: List<TinoComponentPropDescriptor> = emptyList(),
    val actions: List<TinoActionDescriptor> = emptyList(),
) {
    val propNames: Set<String> get() = props.map { it.name }.toSet()
    val requiredProps: Set<String> get() = props.filter { it.required }.map { it.name }.toSet()
}

interface TinoComponentCatalogContributor {
    fun components(): List<TinoComponentDescriptor>
}

/** Versioned domain vocabulary; it composes Basic Catalog primitives. */
object TinoCatalogVersion {
    const val ID = "tino.catalog.v1"
    const val SCHEMA = "tino.catalog"
    const val VERSION = 1
}

/** Core vocabulary. Vertical packs contribute through the same interface. */
object CoreTinoComponentCatalog : TinoComponentCatalogContributor {
    const val ROW = "core.row"
    const val COLUMN = "core.column"
    const val SECTION = "core.section"
    const val DIVIDER = "core.divider"
    const val SPACER = "core.spacer"
    const val TEXT = "display.text"
    const val BADGE = "display.badge"
    const val METRIC = "display.metric"
    const val MONEY = "display.money"
    const val ICON = "display.icon"
    const val PRODUCT = "business.product"
    const val PRODUCT_LIST = "business.product_list"
    const val CUSTOMER = "business.customer"
    const val CUSTOMER_BALANCE = "business.customer_balance"
    const val INVENTORY_LEVEL = "business.inventory_level"
    const val PAYMENT_METHOD = "business.payment_method"
    const val TRANSACTION = "business.transaction"
    const val CREDIT_TIMELINE = "business.credit_timeline"
    const val INSIGHT = "intelligence.insight"
    const val COMPARISON = "intelligence.comparison"
    const val TREND = "intelligence.trend"
    const val RECOMMENDATION = "intelligence.recommendation"
    const val EVIDENCE = "intelligence.evidence"
    const val BUTTON = "interaction.button"
    const val CHOICE = "interaction.choice"
    const val TEXT_FIELD = "interaction.text_field"
    const val NUMBER_FIELD = "interaction.number_field"
    const val DATE_PICKER = "interaction.date_picker"
    const val CONFIRMATION = "operation.confirmation"
    const val OPERATION_PREVIEW = "operation.preview"
    const val OPERATION_SUCCESS = "operation.success"
    const val OPERATION_FAILURE = "operation.failure"

    val DISMISS = TinoActionDescriptor("dismiss", TinoActionKind.UI_LOCAL)
    val EXPAND = TinoActionDescriptor("expand", TinoActionKind.UI_LOCAL)
    val COLLAPSE = TinoActionDescriptor("collapse", TinoActionKind.UI_LOCAL)
    val SELECT_TAB = TinoActionDescriptor("select_tab", TinoActionKind.UI_LOCAL, setOf("tab"), setOf("tab"))
    val SELECT_ENTITY = TinoActionDescriptor("select_entity", TinoActionKind.AGENT, setOf("entityId", "label"), setOf("entityId"))
    val REQUEST_DETAILS = TinoActionDescriptor("request_details", TinoActionKind.AGENT, setOf("entityId"), setOf("entityId"))
    val APPLY_FILTER = TinoActionDescriptor("apply_filter", TinoActionKind.AGENT, setOf("filter"), setOf("filter"))
    val CONTINUE_OPERATION = TinoActionDescriptor("continue_operation", TinoActionKind.AGENT)
    val CANCEL_OPERATION = TinoActionDescriptor(
        "cancel_operation",
        TinoActionKind.AGENT,
        allowedPayloadKeys = setOf("operationId", "confirmationToken"),
    )
    val CONFIRM_OPERATION = TinoActionDescriptor(
        "confirm_operation",
        TinoActionKind.AGENT,
        allowedPayloadKeys = setOf("operationId", "confirmationToken"),
        requiredPayloadKeys = setOf("operationId", "confirmationToken"),
    )

    override fun components(): List<TinoComponentDescriptor> = listOf(
        descriptor(ROW, TinoComponentGroup.LAYOUT),
        descriptor(COLUMN, TinoComponentGroup.LAYOUT),
        descriptor(SECTION, TinoComponentGroup.LAYOUT, "title"),
        descriptor(DIVIDER, TinoComponentGroup.LAYOUT),
        descriptor(SPACER, TinoComponentGroup.LAYOUT, "size" to TinoComponentPropType.NUMBER),
        descriptor(TEXT, TinoComponentGroup.DISPLAY, "text" to TinoComponentPropType.TEXT),
        descriptor(BADGE, TinoComponentGroup.DISPLAY, "text" to TinoComponentPropType.TEXT),
        descriptor(METRIC, TinoComponentGroup.DISPLAY, "label" to TinoComponentPropType.TEXT, "value" to TinoComponentPropType.TEXT),
        descriptor(MONEY, TinoComponentGroup.DISPLAY, "label" to TinoComponentPropType.TEXT, "value" to TinoComponentPropType.MONEY),
        descriptor(ICON, TinoComponentGroup.DISPLAY, "name" to TinoComponentPropType.TEXT),
        descriptor(PRODUCT, TinoComponentGroup.BUSINESS, "name" to TinoComponentPropType.TEXT),
        descriptor(PRODUCT_LIST, TinoComponentGroup.BUSINESS),
        descriptor(CUSTOMER, TinoComponentGroup.BUSINESS, "name" to TinoComponentPropType.TEXT),
        descriptor(CUSTOMER_BALANCE, TinoComponentGroup.BUSINESS, "name" to TinoComponentPropType.TEXT, "balance" to TinoComponentPropType.MONEY),
        descriptor(INVENTORY_LEVEL, TinoComponentGroup.BUSINESS, "product" to TinoComponentPropType.TEXT, "quantity" to TinoComponentPropType.NUMBER),
        descriptor(PAYMENT_METHOD, TinoComponentGroup.BUSINESS, "method" to TinoComponentPropType.TEXT, "value" to TinoComponentPropType.MONEY),
        descriptor(TRANSACTION, TinoComponentGroup.BUSINESS),
        descriptor(CREDIT_TIMELINE, TinoComponentGroup.BUSINESS),
        descriptor(INSIGHT, TinoComponentGroup.INTELLIGENCE, "title" to TinoComponentPropType.TEXT, "answer" to TinoComponentPropType.TEXT),
        descriptor(COMPARISON, TinoComponentGroup.INTELLIGENCE, "title" to TinoComponentPropType.TEXT, "value" to TinoComponentPropType.TEXT),
        descriptor(TREND, TinoComponentGroup.INTELLIGENCE, "title" to TinoComponentPropType.TEXT, "value" to TinoComponentPropType.TEXT),
        descriptor(RECOMMENDATION, TinoComponentGroup.INTELLIGENCE, "title" to TinoComponentPropType.TEXT, "answer" to TinoComponentPropType.TEXT),
        descriptor(EVIDENCE, TinoComponentGroup.INTELLIGENCE, "label" to TinoComponentPropType.TEXT, "value" to TinoComponentPropType.TEXT),
        descriptor(BUTTON, TinoComponentGroup.INTERACTION, "label" to TinoComponentPropType.TEXT)
            .copy(actions = listOf(DISMISS, EXPAND, COLLAPSE, SELECT_TAB)),
        descriptor(CHOICE, TinoComponentGroup.INTERACTION, "label" to TinoComponentPropType.TEXT, "value" to TinoComponentPropType.TEXT)
            .copy(actions = listOf(SELECT_ENTITY, APPLY_FILTER, REQUEST_DETAILS)),
        descriptor(TEXT_FIELD, TinoComponentGroup.INTERACTION, "label" to TinoComponentPropType.TEXT),
        descriptor(NUMBER_FIELD, TinoComponentGroup.INTERACTION, "label" to TinoComponentPropType.TEXT),
        descriptor(DATE_PICKER, TinoComponentGroup.INTERACTION, "label" to TinoComponentPropType.TEXT),
        descriptor(CONFIRMATION, TinoComponentGroup.OPERATIONS, "title" to TinoComponentPropType.TEXT, "detail" to TinoComponentPropType.TEXT)
            .copy(actions = listOf(CONFIRM_OPERATION, CANCEL_OPERATION)),
        descriptor(OPERATION_PREVIEW, TinoComponentGroup.OPERATIONS)
            .copy(actions = listOf(CONTINUE_OPERATION, CANCEL_OPERATION)),
        descriptor(OPERATION_SUCCESS, TinoComponentGroup.OPERATIONS, "message" to TinoComponentPropType.TEXT),
        descriptor(OPERATION_FAILURE, TinoComponentGroup.OPERATIONS, "message" to TinoComponentPropType.TEXT),
    )

    private fun descriptor(
        type: String,
        group: TinoComponentGroup,
        vararg props: Any,
    ): TinoComponentDescriptor {
        val descriptors = props.map { prop ->
            when (prop) {
                is Pair<*, *> -> TinoComponentPropDescriptor(
                    name = prop.first as String,
                    type = prop.second as TinoComponentPropType,
                )
                is String -> TinoComponentPropDescriptor(prop, TinoComponentPropType.TEXT)
                else -> error("Unsupported catalog prop descriptor")
            }
        }
        return TinoComponentDescriptor(type, group, props = descriptors)
    }
}

/**
 * Small, domain-specific vocabulary for recurring TINO meanings.
 * These descriptors do not define Compose: they constrain semantic props and actions.
 */
object TinoCustomComponentCatalog : TinoComponentCatalogContributor {
    const val METRIC_CARD = "tino.metric_card"
    const val PRODUCT_CARD = "tino.product_card"
    const val CUSTOMER_CARD = "tino.customer_card"
    const val DEBT_CARD = "tino.debt_card"
    const val INVENTORY_ALERT_CARD = "tino.inventory_alert_card"
    const val SALE_CARD = "tino.sale_card"
    const val SUMMARY_CARD = "tino.summary_card"
    const val QUICK_QUERY_CARD = "tino.quick_query_card"
    const val CONFIRMATION_CARD = "tino.confirmation_card"
    const val STATUS_CARD = "tino.status_card"
    const val MINI_CHART = "tino.mini_chart"

    private val REQUEST_DETAILS = TinoActionDescriptor(
        name = "request_details",
        kind = TinoActionKind.AGENT,
        allowedPayloadKeys = setOf("entityId"),
        requiredPayloadKeys = setOf("entityId"),
    )
    private val APPLY_QUERY = TinoActionDescriptor(
        name = "apply_query",
        kind = TinoActionKind.AGENT,
        allowedPayloadKeys = setOf("query"),
        requiredPayloadKeys = setOf("query"),
    )
    private val RETRY = TinoActionDescriptor("retry", TinoActionKind.UI_LOCAL)

    override fun components(): List<TinoComponentDescriptor> = listOf(
        descriptor(METRIC_CARD, TinoComponentGroup.DISPLAY, "icon", "title", "value", "supportingText", "trend")
            .copy(actions = listOf(REQUEST_DETAILS)),
        descriptor(PRODUCT_CARD, TinoComponentGroup.BUSINESS, "icon", "title", "context", "value", "supportingText", "status")
            .copy(actions = listOf(REQUEST_DETAILS)),
        descriptor(CUSTOMER_CARD, TinoComponentGroup.BUSINESS, "icon", "title", "context", "supportingText", "status")
            .copy(actions = listOf(REQUEST_DETAILS)),
        descriptor(DEBT_CARD, TinoComponentGroup.BUSINESS, "icon", "title", "context", "value", "supportingText", "status")
            .copy(actions = listOf(REQUEST_DETAILS)),
        descriptor(INVENTORY_ALERT_CARD, TinoComponentGroup.BUSINESS, "icon", "title", "value", "supportingText", "status")
            .copy(actions = listOf(REQUEST_DETAILS)),
        descriptor(SALE_CARD, TinoComponentGroup.BUSINESS, "icon", "title", "value", "supportingText", "status")
            .copy(actions = listOf(REQUEST_DETAILS)),
        descriptor(SUMMARY_CARD, TinoComponentGroup.DISPLAY, "title", "salesValue", "receivedValue", "creditValue"),
        descriptor(QUICK_QUERY_CARD, TinoComponentGroup.INTERACTION, "icon", "title", "supportingText")
            .copy(actions = listOf(APPLY_QUERY)),
        descriptor(CONFIRMATION_CARD, TinoComponentGroup.OPERATIONS, "title", "entity", "value", "detail")
            .copy(actions = listOf(CoreTinoComponentCatalog.CONFIRM_OPERATION, CoreTinoComponentCatalog.CANCEL_OPERATION)),
        descriptor(STATUS_CARD, TinoComponentGroup.DISPLAY, "title", "message", "status")
            .copy(actions = listOf(RETRY)),
        descriptor(MINI_CHART, TinoComponentGroup.DISPLAY, "title", "value", "series", "labels"),
    )

    private fun descriptor(
        type: String,
        group: TinoComponentGroup,
        vararg props: String,
    ): TinoComponentDescriptor = TinoComponentDescriptor(
        type = type,
        group = group,
        version = TinoCatalogVersion.VERSION,
        props = props.map { TinoComponentPropDescriptor(it, TinoComponentPropType.TEXT, required = it in REQUIRED_PROPS) },
    )

    private val REQUIRED_PROPS = setOf("title", "value", "message", "entity")
}

/** Existing typed cards remain part of the core wire vocabulary during migration. */
object LegacyTinoComponentCatalog : TinoComponentCatalogContributor {
    override fun components(): List<TinoComponentDescriptor> = TinoA2UiComponentCatalog.allowlist.map { type ->
        TinoComponentDescriptor(
            type = type,
            group = when {
                type.contains("preview") || type.contains("confirmation") || type.contains("undo") -> TinoComponentGroup.OPERATIONS
                type.contains("customer") || type.contains("product") || type.contains("stock") || type.contains("receivable") -> TinoComponentGroup.BUSINESS
                type.contains("error") -> TinoComponentGroup.OPERATIONS
                else -> TinoComponentGroup.DISPLAY
            },
        )
    }
}

data class TinoEffectiveComponentCatalog(
    val contributors: List<TinoComponentCatalogContributor>,
) {
    val descriptors: List<TinoComponentDescriptor> = contributors
        .flatMap { it.components() }
        .distinctBy { it.type }
    val types: Set<String> = descriptors.map { it.type }.toSet()
    val duplicateTypes: Set<String> = contributors
        .flatMap { it.components() }
        .groupBy { it.type }
        .filterValues { it.size > 1 }
        .keys

    fun descriptor(type: String): TinoComponentDescriptor? = descriptors.firstOrNull { it.type == type }
}

object TinoComponentCatalog {
    val core: TinoEffectiveComponentCatalog by lazy {
        TinoEffectiveComponentCatalog(
            listOf(CoreTinoComponentCatalog, TinoCustomComponentCatalog, LegacyTinoComponentCatalog),
        )
    }

    fun effective(contributors: List<TinoComponentCatalogContributor> = emptyList()): TinoEffectiveComponentCatalog =
        TinoEffectiveComponentCatalog(
            listOf(CoreTinoComponentCatalog, TinoCustomComponentCatalog, LegacyTinoComponentCatalog) + contributors,
        )
}

sealed interface TinoComponentValidation {
    data object Allowed : TinoComponentValidation
    data class Unknown(val reason: String) : TinoComponentValidation
    data class InvalidProps(val reason: String) : TinoComponentValidation
}

object TinoComponentCatalogValidator {
    fun validate(
        component: A2uiSurfaceComponent,
        catalog: TinoEffectiveComponentCatalog = TinoComponentCatalog.core,
    ): TinoComponentValidation {
        val descriptor = catalog.descriptor(component.type)
            ?: return TinoComponentValidation.Unknown("Componente ${component.type} não pertence ao catálogo.")
        val provided = component.props.keys + component.bindings.keys
        val missing = descriptor.requiredProps - provided
        if (missing.isNotEmpty()) {
            return TinoComponentValidation.InvalidProps("Props obrigatórias ausentes: ${missing.joinToString()}.")
        }
        val unknown = component.props.keys - descriptor.propNames
        if (unknown.isNotEmpty() && descriptor.props.isNotEmpty()) {
            return TinoComponentValidation.InvalidProps("Props não declaradas: ${unknown.joinToString()}.")
        }
        return TinoComponentValidation.Allowed
    }
}
