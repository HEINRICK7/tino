package com.tino.app.domain.agent

enum class TinoCapabilityId {
    READ_FINANCIAL_SUMMARY,
    READ_RECEIVABLES,
    READ_CUSTOMER_BALANCE,
    GET_CUSTOMER_CONTACT,
    SEARCH_CUSTOMER,
    CREATE_CUSTOMER,
    LIST_PRODUCTS,
    REPLENISHMENT_QUERY,
    GET_PRODUCT_STOCK,
    GET_PRODUCT_PRICE,
    LIST_CUSTOMERS,
    LIST_SUPPLIERS,
    LIST_RECEIVABLES,
    LIST_OVERDUE,
    SEARCH_PRODUCT,
    READ_PRODUCT,
    READ_STOCK,
    REGISTER_STOCK_ENTRY,
    ADD_CREDIT,
    ADD_CREDIT_ITEM,
    RECEIVE_CREDIT_PAYMENT,
    REVERSE_CREDIT_PAYMENT,
    CHANGE_PRODUCT_PRICE,
    SEARCH_SUPPLIER,
    NAVIGATE,
    OPEN_ENTITY,
    FOCUS,
    FILTER,
    SEARCH,
}

enum class TinoCapabilityRisk {
    LOW,
    MEDIUM,
    HIGH,
}

enum class TinoCapabilityType { QUERY, MUTATION, NAVIGATION }

enum class TinoConfirmationPolicy { NONE, REQUIRED }

enum class TinoPresentationMode {
    INLINE,
    OVERLAY,
    BOTTOM_SHEET,
    NAVIGATE,
    FULLSCREEN,
}

data class TinoCapabilityDescriptor(
    val id: TinoCapabilityId,
    val requiredSlots: Set<String>,
    val risk: TinoCapabilityRisk,
    val presentation: TinoPresentationMode,
    val offline: Boolean,
    val sourceOfTruth: String,
    val type: TinoCapabilityType = TinoCapabilityType.QUERY,
    val confirmation: TinoConfirmationPolicy = TinoConfirmationPolicy.NONE,
    val operationIdRequired: Boolean = false,
    val a2uiComponent: String? = null,
)

typealias TinoCapabilityDefinition = TinoCapabilityDescriptor

object TinoCapabilityRegistry {
    val all: Map<TinoCapabilityId, TinoCapabilityDescriptor> = listOf(
        capability(TinoCapabilityId.READ_FINANCIAL_SUMMARY, risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.OVERLAY, sourceOfTruth = "FinancialProjectionRepository"),
        capability(TinoCapabilityId.READ_RECEIVABLES, risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.OVERLAY, sourceOfTruth = "CreditLedger"),
        capability(TinoCapabilityId.LIST_RECEIVABLES, risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.OVERLAY, sourceOfTruth = "CreditProjection"),
        capability(TinoCapabilityId.READ_CUSTOMER_BALANCE, "customer", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.OVERLAY, sourceOfTruth = "CreditLedger"),
        capability(TinoCapabilityId.GET_CUSTOMER_CONTACT, "customer", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.OVERLAY, sourceOfTruth = "CustomerRepository", a2uiComponent = "customer_contact"),
        capability(TinoCapabilityId.SEARCH_CUSTOMER, "customer", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.OVERLAY, sourceOfTruth = "CustomerRepository"),
        capability(TinoCapabilityId.CREATE_CUSTOMER, "name", risk = TinoCapabilityRisk.MEDIUM, presentation = TinoPresentationMode.BOTTOM_SHEET, sourceOfTruth = "CustomerRepository"),
        capability(TinoCapabilityId.LIST_PRODUCTS, risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.BOTTOM_SHEET, sourceOfTruth = "ProductRepository", a2uiComponent = "product_list"),
        capability(TinoCapabilityId.REPLENISHMENT_QUERY, risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.BOTTOM_SHEET, sourceOfTruth = "InventoryPolicy / InventoryProjection", a2uiComponent = "product_replenishment"),
        capability(TinoCapabilityId.GET_PRODUCT_STOCK, "product", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.OVERLAY, sourceOfTruth = "InventoryProjection", a2uiComponent = "stock_status"),
        capability(TinoCapabilityId.GET_PRODUCT_PRICE, "product", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.OVERLAY, sourceOfTruth = "ProductRepository", a2uiComponent = "product_price"),
        capability(TinoCapabilityId.LIST_CUSTOMERS, risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.BOTTOM_SHEET, sourceOfTruth = "CustomerRepository", a2uiComponent = "customer_list"),
        capability(TinoCapabilityId.LIST_SUPPLIERS, risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.BOTTOM_SHEET, sourceOfTruth = "SupplierRepository", a2uiComponent = "supplier_summary"),
        capability(TinoCapabilityId.LIST_RECEIVABLES, risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.BOTTOM_SHEET, sourceOfTruth = "CreditProjection", a2uiComponent = "receivables_list"),
        capability(TinoCapabilityId.LIST_OVERDUE, risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.BOTTOM_SHEET, sourceOfTruth = "CreditProjection", a2uiComponent = "overdue_list"),
        capability(TinoCapabilityId.SEARCH_PRODUCT, "product", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.OVERLAY, sourceOfTruth = "ProductRepository"),
        capability(TinoCapabilityId.READ_PRODUCT, "product", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.OVERLAY, sourceOfTruth = "ProductRepository"),
        capability(TinoCapabilityId.READ_STOCK, "product", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.OVERLAY, sourceOfTruth = "InventoryRepository"),
        capability(TinoCapabilityId.REGISTER_STOCK_ENTRY, "product", "quantity", "unitCost", risk = TinoCapabilityRisk.MEDIUM, presentation = TinoPresentationMode.BOTTOM_SHEET, sourceOfTruth = "CommerceRepository / Room", a2uiComponent = "stock_entry_preview"),
        capability(TinoCapabilityId.ADD_CREDIT, "customer", "amount", risk = TinoCapabilityRisk.MEDIUM, presentation = TinoPresentationMode.BOTTOM_SHEET, sourceOfTruth = "CreditLedger"),
        capability(TinoCapabilityId.ADD_CREDIT_ITEM, "customer", "product", "quantity", risk = TinoCapabilityRisk.MEDIUM, presentation = TinoPresentationMode.BOTTOM_SHEET, sourceOfTruth = "CreditLedger"),
        capability(TinoCapabilityId.RECEIVE_CREDIT_PAYMENT, "customer", "amount", "paymentMethod", risk = TinoCapabilityRisk.HIGH, presentation = TinoPresentationMode.BOTTOM_SHEET, sourceOfTruth = "CreditLedger", a2uiComponent = "payment_preview"),
        capability(TinoCapabilityId.REVERSE_CREDIT_PAYMENT, "operationId", risk = TinoCapabilityRisk.HIGH, presentation = TinoPresentationMode.BOTTOM_SHEET, sourceOfTruth = "CreditLedger"),
        capability(TinoCapabilityId.CHANGE_PRODUCT_PRICE, "product", "price", risk = TinoCapabilityRisk.HIGH, presentation = TinoPresentationMode.BOTTOM_SHEET, sourceOfTruth = "ProductRepository"),
        capability(TinoCapabilityId.SEARCH_SUPPLIER, "supplier", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.OVERLAY, sourceOfTruth = "SupplierRepository"),
        capability(TinoCapabilityId.NAVIGATE, "destination", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.NAVIGATE, sourceOfTruth = "NavigationState"),
        capability(TinoCapabilityId.OPEN_ENTITY, "entity", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.NAVIGATE, sourceOfTruth = "NavigationState"),
        capability(TinoCapabilityId.FOCUS, "entity", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.INLINE, sourceOfTruth = "NavigationState"),
        capability(TinoCapabilityId.FILTER, "filter", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.INLINE, sourceOfTruth = "ScreenState"),
        capability(TinoCapabilityId.SEARCH, "query", risk = TinoCapabilityRisk.LOW, presentation = TinoPresentationMode.INLINE, sourceOfTruth = "ScreenState"),
    ).associateBy { it.id }

    fun require(id: TinoCapabilityId): TinoCapabilityDescriptor =
        all[id] ?: error("Capability não registrada: $id")

    fun isAvailableOffline(id: TinoCapabilityId): Boolean = require(id).offline

    private fun capability(
        id: TinoCapabilityId,
        vararg requiredSlots: String,
        risk: TinoCapabilityRisk,
        presentation: TinoPresentationMode,
        offline: Boolean = true,
        sourceOfTruth: String,
        type: TinoCapabilityType = typeFor(id),
        confirmation: TinoConfirmationPolicy = confirmationFor(id),
        operationIdRequired: Boolean = confirmation == TinoConfirmationPolicy.REQUIRED,
        a2uiComponent: String? = null,
    ): TinoCapabilityDescriptor = TinoCapabilityDescriptor(
        id = id,
        requiredSlots = requiredSlots.toSet(),
        risk = risk,
        presentation = presentation,
        offline = offline,
        sourceOfTruth = sourceOfTruth,
        type = type,
        confirmation = confirmation,
        operationIdRequired = operationIdRequired,
        a2uiComponent = a2uiComponent,
    )

    private fun typeFor(id: TinoCapabilityId): TinoCapabilityType = when (id) {
        TinoCapabilityId.NAVIGATE,
        TinoCapabilityId.OPEN_ENTITY,
        TinoCapabilityId.FOCUS,
        TinoCapabilityId.FILTER,
        TinoCapabilityId.SEARCH,
        -> TinoCapabilityType.NAVIGATION
        TinoCapabilityId.CREATE_CUSTOMER,
        TinoCapabilityId.REGISTER_STOCK_ENTRY,
        TinoCapabilityId.ADD_CREDIT,
        TinoCapabilityId.ADD_CREDIT_ITEM,
        TinoCapabilityId.RECEIVE_CREDIT_PAYMENT,
        TinoCapabilityId.REVERSE_CREDIT_PAYMENT,
        TinoCapabilityId.CHANGE_PRODUCT_PRICE,
        -> TinoCapabilityType.MUTATION
        else -> TinoCapabilityType.QUERY
    }

    private fun confirmationFor(id: TinoCapabilityId): TinoConfirmationPolicy = when (typeFor(id)) {
        TinoCapabilityType.MUTATION -> TinoConfirmationPolicy.REQUIRED
        else -> TinoConfirmationPolicy.NONE
    }
}
