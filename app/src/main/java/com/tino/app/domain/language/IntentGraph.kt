package com.tino.app.domain.language

enum class CommerceConcept {
    CUSTOMER,
    PRODUCT,
    FINANCIAL,
    SUPPLIER,
}

data class IntentGraphNode(
    val intent: TinoIntent,
    val concept: CommerceConcept,
    val relatedIntents: Set<TinoIntent>,
)

object IntentGraph {
    private val nodes: Map<TinoIntent, IntentGraphNode> = mapOf(
        TinoIntent.READ_CUSTOMER_BALANCE to node(TinoIntent.READ_CUSTOMER_BALANCE, CommerceConcept.CUSTOMER, TinoIntent.ADD_CREDIT_ITEM, TinoIntent.RECEIVE_CREDIT_PAYMENT, TinoIntent.SEARCH_CUSTOMER),
        TinoIntent.ADD_CREDIT to node(TinoIntent.ADD_CREDIT, CommerceConcept.CUSTOMER, TinoIntent.ADD_CREDIT_ITEM, TinoIntent.READ_CUSTOMER_BALANCE),
        TinoIntent.ADD_CREDIT_ITEM to node(TinoIntent.ADD_CREDIT_ITEM, CommerceConcept.CUSTOMER, TinoIntent.READ_CUSTOMER_BALANCE, TinoIntent.RECEIVE_CREDIT_PAYMENT),
        TinoIntent.RECEIVE_CREDIT_PAYMENT to node(TinoIntent.RECEIVE_CREDIT_PAYMENT, CommerceConcept.CUSTOMER, TinoIntent.READ_CUSTOMER_BALANCE, TinoIntent.ADD_CREDIT_ITEM),
        TinoIntent.SEARCH_CUSTOMER to node(TinoIntent.SEARCH_CUSTOMER, CommerceConcept.CUSTOMER, TinoIntent.READ_CUSTOMER_BALANCE, TinoIntent.ADD_CREDIT),
        TinoIntent.READ_STOCK to node(TinoIntent.READ_STOCK, CommerceConcept.PRODUCT, TinoIntent.REGISTER_STOCK_ENTRY, TinoIntent.READ_PRODUCT),
        TinoIntent.REGISTER_STOCK_ENTRY to node(TinoIntent.REGISTER_STOCK_ENTRY, CommerceConcept.PRODUCT, TinoIntent.READ_STOCK, TinoIntent.CHANGE_PRICE),
        TinoIntent.READ_PRODUCT to node(TinoIntent.READ_PRODUCT, CommerceConcept.PRODUCT, TinoIntent.READ_STOCK, TinoIntent.CHANGE_PRICE),
        TinoIntent.SEARCH_PRODUCT to node(TinoIntent.SEARCH_PRODUCT, CommerceConcept.PRODUCT, TinoIntent.READ_PRODUCT, TinoIntent.READ_STOCK),
        TinoIntent.CHANGE_PRICE to node(TinoIntent.CHANGE_PRICE, CommerceConcept.PRODUCT, TinoIntent.READ_PRODUCT, TinoIntent.READ_STOCK),
        TinoIntent.SEARCH_SUPPLIER to node(TinoIntent.SEARCH_SUPPLIER, CommerceConcept.SUPPLIER, TinoIntent.REGISTER_STOCK_ENTRY),
        TinoIntent.READ_FINANCIAL_SUMMARY to node(TinoIntent.READ_FINANCIAL_SUMMARY, CommerceConcept.FINANCIAL, TinoIntent.READ_RECEIVABLES),
        TinoIntent.READ_RECEIVABLES to node(TinoIntent.READ_RECEIVABLES, CommerceConcept.FINANCIAL, TinoIntent.READ_FINANCIAL_SUMMARY, TinoIntent.READ_CUSTOMER_BALANCE),
    )

    fun node(intent: TinoIntent): IntentGraphNode? = nodes[intent]

    fun relatedTo(intent: TinoIntent): Set<TinoIntent> = nodes[intent]?.relatedIntents.orEmpty()

    private fun node(intent: TinoIntent, concept: CommerceConcept, vararg related: TinoIntent) =
        IntentGraphNode(intent, concept, related.toSet())
}
