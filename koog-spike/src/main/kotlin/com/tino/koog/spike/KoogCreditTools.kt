package com.tino.koog.spike

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.serialization.typeToken
import com.tino.agent.contracts.BalanceLookup
import com.tino.agent.contracts.CreditPreparationFactsPort
import com.tino.agent.contracts.CreditPreparationLookup
import com.tino.agent.contracts.CustomerLookup
import com.tino.agent.contracts.ProductLookup
import kotlinx.serialization.Serializable

class FindCustomerTool(
    private val facts: CreditPreparationFactsPort,
) : SimpleTool<FindCustomerTool.Args>(
    argsType = typeToken<Args>(),
    name = "findCustomer",
    description = "Resolve a customer reference using the local TINO data. Never invent a customer.",
) {
    @Serializable
    data class Args(val customerRef: String)

    override suspend fun execute(args: Args): String = when (val result = facts.findCustomer(args.customerRef)) {
        is CustomerLookup.Resolved -> "customer=${result.name}"
        is CustomerLookup.Ambiguous -> "AMBIGUOUS customer=${result.options.joinToString()}"
        CustomerLookup.NotFound -> "NOT_FOUND customer"
    }
}

class FindProductTool(
    private val facts: CreditPreparationFactsPort,
) : SimpleTool<FindProductTool.Args>(
    argsType = typeToken<Args>(),
    name = "findProduct",
    description = "Resolve a product reference using the local TINO data. Never invent price or stock.",
) {
    @Serializable
    data class Args(val productRef: String)

    override suspend fun execute(args: Args): String = when (val result = facts.findProduct(args.productRef)) {
        is ProductLookup.Resolved -> "product=${result.name};price_cents=${result.priceCents};stock=${result.stockQuantity}"
        is ProductLookup.Ambiguous -> "AMBIGUOUS product=${result.options.joinToString()}"
        ProductLookup.NotFound -> "NOT_FOUND product"
    }
}

class GetCustomerBalanceTool(
    private val facts: CreditPreparationFactsPort,
) : SimpleTool<GetCustomerBalanceTool.Args>(
    argsType = typeToken<Args>(),
    name = "getCustomerBalance",
    description = "Read a customer's current balance from TINO local data.",
) {
    @Serializable
    data class Args(val customerName: String)

    override suspend fun execute(args: Args): String = when (val result = facts.getCustomerBalance(args.customerName)) {
        is BalanceLookup.Resolved -> "balance_cents=${result.balanceCents}"
        is BalanceLookup.Ambiguous -> "AMBIGUOUS customer=${result.options.joinToString()}"
        BalanceLookup.NotFound -> "NOT_FOUND customer"
    }
}

class PrepareCreditSaleTool(
    private val facts: CreditPreparationFactsPort,
) : SimpleTool<PrepareCreditSaleTool.Args>(
    argsType = typeToken<Args>(),
    name = "prepareCreditSale",
    description = "Prepare a credit-sale preview. Never commits a sale or changes the database.",
) {
    @Serializable
    data class Args(
        val customerName: String,
        val productName: String,
        val quantity: Int,
    )

    override suspend fun execute(args: Args): String {
        require(args.quantity > 0) { "quantity must be greater than zero" }
        return when (val result = facts.prepareCreditSale(args.customerName, args.productName, args.quantity)) {
            is CreditPreparationLookup.Preview -> "PREVIEW ${result.detail.replace('\n', ';')}"
            is CreditPreparationLookup.Ambiguous ->
                "AMBIGUOUS ${result.entityType}=${result.options.joinToString()}"
            is CreditPreparationLookup.NotFound -> "NOT_FOUND ${result.entityType}"
            is CreditPreparationLookup.Rejected -> "REJECTED ${result.message}"
        }
    }
}

object KoogCreditToolRegistry {
    val allowlistedNames = setOf(
        "findCustomer",
        "findProduct",
        "getCustomerBalance",
        "prepareCreditSale",
    )

    fun create(facts: CreditPreparationFactsPort): ToolRegistry = ToolRegistry {
        tool(FindCustomerTool(facts))
        tool(FindProductTool(facts))
        tool(GetCustomerBalanceTool(facts))
        tool(PrepareCreditSaleTool(facts))
    }
}
