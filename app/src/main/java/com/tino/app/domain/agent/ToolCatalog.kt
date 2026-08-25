package com.tino.app.domain.agent

enum class TinoToolId {
    FIND_CUSTOMER,
    FIND_PRODUCT,
    FINANCIAL_SUMMARY,
    LIST_PRODUCTS,
    REPLENISHMENT_QUERY,
    PRODUCT_STOCK,
    PRODUCT_PRICE,
    LIST_CUSTOMERS,
    LIST_RECEIVABLES,
    LIST_OVERDUE,
    CUSTOMER_BALANCE,
    CUSTOMER_TIMELINE,
    CREDIT_ADD,
    PREPARE_CREDIT_SALE,
    CREDIT_PAYMENT,
}

enum class TinoToolMode {
    READ_ONLY,
    PREPARE_ONLY,
}

enum class TinoToolRisk {
    LOW,
    MEDIUM,
    HIGH,
}

enum class TinoEntityResolution {
    NONE,
    CUSTOMER,
    PRODUCT,
    MULTIPLE,
}

data class TinoToolDescriptor(
    val id: TinoToolId,
    val name: String,
    val arguments: Set<String>,
    val mode: TinoToolMode = TinoToolMode.READ_ONLY,
    val risk: TinoToolRisk = TinoToolRisk.LOW,
    val sourceOfTruth: String = "TINO_DOMAIN",
    val entityResolution: TinoEntityResolution = TinoEntityResolution.NONE,
    val a2uiComponent: String? = null,
    val offline: Boolean = true,
    val capabilityId: TinoCapabilityId? = null,
)

object TinoToolCatalog {
    val all: Set<TinoToolDescriptor> = setOf(
        TinoToolDescriptor(
            TinoToolId.FIND_CUSTOMER,
            "findCustomer",
            setOf("customer_ref"),
            sourceOfTruth = "EntityResolutionService / CustomerRepository",
            entityResolution = TinoEntityResolution.CUSTOMER,
        ),
        TinoToolDescriptor(
            TinoToolId.FIND_PRODUCT,
            "findProduct",
            setOf("product_ref"),
            sourceOfTruth = "EntityResolutionService / ProductRepository",
            entityResolution = TinoEntityResolution.PRODUCT,
        ),
        TinoToolDescriptor(
            TinoToolId.FINANCIAL_SUMMARY,
            "financial.summary",
            setOf("period", "metric", "payment_method"),
            sourceOfTruth = "FinancialProjectionRepository",
            a2uiComponent = "financial_summary",
            capabilityId = TinoCapabilityId.READ_FINANCIAL_SUMMARY,
        ),
        TinoToolDescriptor(
            TinoToolId.LIST_PRODUCTS,
            "products.list",
            emptySet(),
            sourceOfTruth = "ProductRepository / Room",
            a2uiComponent = "product_list",
            capabilityId = TinoCapabilityId.LIST_PRODUCTS,
        ),
        TinoToolDescriptor(
            TinoToolId.REPLENISHMENT_QUERY,
            "inventory.replenishment",
            emptySet(),
            sourceOfTruth = "InventoryPolicy / Room",
            a2uiComponent = "product_replenishment",
            capabilityId = TinoCapabilityId.REPLENISHMENT_QUERY,
        ),
        TinoToolDescriptor(
            TinoToolId.PRODUCT_STOCK,
            "product.stock",
            setOf("product_ref"),
            sourceOfTruth = "StockMovementRepository / Room",
            entityResolution = TinoEntityResolution.PRODUCT,
            a2uiComponent = "product_stock",
            capabilityId = TinoCapabilityId.GET_PRODUCT_STOCK,
        ),
        TinoToolDescriptor(
            TinoToolId.PRODUCT_PRICE,
            "product.price",
            setOf("product_ref"),
            sourceOfTruth = "ProductRepository / Room",
            entityResolution = TinoEntityResolution.PRODUCT,
            a2uiComponent = "product_price",
            capabilityId = TinoCapabilityId.GET_PRODUCT_PRICE,
        ),
        TinoToolDescriptor(
            TinoToolId.LIST_CUSTOMERS,
            "customers.list",
            emptySet(),
            sourceOfTruth = "CustomerRepository / Room",
            a2uiComponent = "customer_list",
            capabilityId = TinoCapabilityId.LIST_CUSTOMERS,
        ),
        TinoToolDescriptor(
            TinoToolId.LIST_RECEIVABLES,
            "receivables.list",
            emptySet(),
            sourceOfTruth = "TemporalCreditService / Room",
            a2uiComponent = "receivables_list",
            capabilityId = TinoCapabilityId.LIST_RECEIVABLES,
        ),
        TinoToolDescriptor(
            TinoToolId.LIST_OVERDUE,
            "overdue.list",
            emptySet(),
            sourceOfTruth = "TemporalCreditService / Room",
            a2uiComponent = "overdue_list",
            capabilityId = TinoCapabilityId.LIST_OVERDUE,
        ),
        TinoToolDescriptor(
            TinoToolId.CUSTOMER_BALANCE,
            "customer.balance",
            setOf("customer_ref"),
            sourceOfTruth = "TemporalCreditService / Room",
            entityResolution = TinoEntityResolution.CUSTOMER,
            a2uiComponent = "customer_balance",
            capabilityId = TinoCapabilityId.READ_CUSTOMER_BALANCE,
        ),
        TinoToolDescriptor(
            TinoToolId.CUSTOMER_TIMELINE,
            "customer.timeline",
            setOf("customer_ref"),
            sourceOfTruth = "TemporalCreditService / Room",
            entityResolution = TinoEntityResolution.CUSTOMER,
            a2uiComponent = "customer_timeline",
            capabilityId = TinoCapabilityId.READ_CUSTOMER_BALANCE,
        ),
        TinoToolDescriptor(
            TinoToolId.CREDIT_ADD,
            "credit.add",
            setOf("customer_ref", "product_ref", "quantity"),
            mode = TinoToolMode.PREPARE_ONLY,
            risk = TinoToolRisk.HIGH,
            sourceOfTruth = "CommerceToolDispatcher / Credit domain",
            entityResolution = TinoEntityResolution.MULTIPLE,
            a2uiComponent = "credit_sale_preview",
        ),
        TinoToolDescriptor(
            TinoToolId.PREPARE_CREDIT_SALE,
            "prepareCreditSale",
            setOf("customer_ref", "product_ref", "quantity"),
            mode = TinoToolMode.PREPARE_ONLY,
            risk = TinoToolRisk.HIGH,
            sourceOfTruth = "CommerceToolDispatcher / Credit domain",
            entityResolution = TinoEntityResolution.MULTIPLE,
            a2uiComponent = "credit_sale_preview",
        ),
        TinoToolDescriptor(
            TinoToolId.CREDIT_PAYMENT,
            "credit.payment",
            setOf("customer_ref", "amount_cents", "payment_method"),
            mode = TinoToolMode.PREPARE_ONLY,
            risk = TinoToolRisk.HIGH,
            sourceOfTruth = "CommerceToolDispatcher / Credit domain",
            entityResolution = TinoEntityResolution.CUSTOMER,
            a2uiComponent = "credit_payment_preview",
            capabilityId = TinoCapabilityId.RECEIVE_CREDIT_PAYMENT,
        ),
    )

    fun contains(id: TinoToolId): Boolean = all.any { it.id == id }

    fun descriptor(id: TinoToolId): TinoToolDescriptor =
        all.first { it.id == id }

    fun descriptorFor(capability: TinoCapabilityId): TinoToolDescriptor =
        all.first { it.capabilityId == capability }
}
