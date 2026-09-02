package com.tino.app.domain.agent

/** Maps the agent vocabulary to the capability ids controlled by BusinessProfile. */
fun AgentCapability.toTinoCapabilityId(): TinoCapabilityId? = when (this) {
    AgentCapability.READ_FINANCIAL_SUMMARY -> TinoCapabilityId.READ_FINANCIAL_SUMMARY
    AgentCapability.LIST_PRODUCTS -> TinoCapabilityId.LIST_PRODUCTS
    AgentCapability.REPLENISHMENT_QUERY -> TinoCapabilityId.REPLENISHMENT_QUERY
    AgentCapability.GET_PRODUCT_STOCK -> TinoCapabilityId.GET_PRODUCT_STOCK
    AgentCapability.GET_PRODUCT_PRICE -> TinoCapabilityId.GET_PRODUCT_PRICE
    AgentCapability.LIST_CUSTOMERS -> TinoCapabilityId.LIST_CUSTOMERS
    AgentCapability.LIST_SUPPLIERS -> TinoCapabilityId.LIST_SUPPLIERS
    AgentCapability.LIST_RECEIVABLES -> TinoCapabilityId.LIST_RECEIVABLES
    AgentCapability.LIST_OVERDUE -> TinoCapabilityId.LIST_OVERDUE
    AgentCapability.ADD_CREDIT_ITEM -> TinoCapabilityId.ADD_CREDIT_ITEM
    AgentCapability.REGISTER_CREDIT_PAYMENT -> TinoCapabilityId.RECEIVE_CREDIT_PAYMENT
    AgentCapability.GET_CUSTOMER_BALANCE -> TinoCapabilityId.READ_CUSTOMER_BALANCE
    AgentCapability.GET_CUSTOMER_TIMELINE -> TinoCapabilityId.READ_CUSTOMER_BALANCE
    AgentCapability.GET_CUSTOMER_CONTACT -> TinoCapabilityId.GET_CUSTOMER_CONTACT
    AgentCapability.CREATE_CUSTOMER -> TinoCapabilityId.CREATE_CUSTOMER
    AgentCapability.UPDATE_PRODUCT_PRICE -> TinoCapabilityId.CHANGE_PRODUCT_PRICE
    AgentCapability.REGISTER_STOCK_ENTRY -> TinoCapabilityId.REGISTER_STOCK_ENTRY
    AgentCapability.GLOBAL_TOOL -> null
}
