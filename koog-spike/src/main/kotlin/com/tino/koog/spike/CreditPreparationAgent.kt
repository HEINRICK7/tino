package com.tino.koog.spike

import com.tino.agent.contracts.CreditPreparationFactsPort

data class CreditPreparationRequest(
    val customerReference: String,
    val productReference: String,
    val quantity: Int,
)

data class CreditPreparationToolStep(
    val name: String,
    val result: String,
)

sealed interface CreditPreparationAgentResult {
    val trace: List<CreditPreparationToolStep>

    data class Preview(
        val detail: String,
        override val trace: List<CreditPreparationToolStep>,
    ) : CreditPreparationAgentResult

    data class Blocked(
        val stage: String,
        val reason: String,
        override val trace: List<CreditPreparationToolStep>,
    ) : CreditPreparationAgentResult
}

/**
 * Koog-only orchestration harness. It receives a structured plan from a future
 * model adapter and executes only the allowlisted read/prepare tools. It has no
 * commit operation and cannot mutate the facts port.
 */
class CreditPreparationAgent(
    facts: CreditPreparationFactsPort,
) {
    private val registry = KoogCreditToolRegistry.create(facts)

    suspend fun prepare(request: CreditPreparationRequest): CreditPreparationAgentResult {
        require(request.quantity > 0) { "quantity must be greater than zero" }
        val trace = mutableListOf<CreditPreparationToolStep>()

        val customerResult = invoke(
            name = "findCustomer",
            args = FindCustomerTool.Args(request.customerReference),
            trace = trace,
        )
        customerResult.blockingReasonOrNull()?.let {
            return CreditPreparationAgentResult.Blocked("findCustomer", it, trace.toList())
        }

        val productResult = invoke(
            name = "findProduct",
            args = FindProductTool.Args(request.productReference),
            trace = trace,
        )
        productResult.blockingReasonOrNull()?.let {
            return CreditPreparationAgentResult.Blocked("findProduct", it, trace.toList())
        }

        val balanceResult = invoke(
            name = "getCustomerBalance",
            args = GetCustomerBalanceTool.Args(request.customerReference),
            trace = trace,
        )
        balanceResult.blockingReasonOrNull()?.let {
            return CreditPreparationAgentResult.Blocked("getCustomerBalance", it, trace.toList())
        }

        val previewResult = invoke(
            name = "prepareCreditSale",
            args = PrepareCreditSaleTool.Args(
                customerName = request.customerReference,
                productName = request.productReference,
                quantity = request.quantity,
            ),
            trace = trace,
        )
        return if (previewResult.startsWith("PREVIEW ")) {
            CreditPreparationAgentResult.Preview(
                detail = previewResult.removePrefix("PREVIEW "),
                trace = trace.toList(),
            )
        } else {
            CreditPreparationAgentResult.Blocked("prepareCreditSale", previewResult, trace.toList())
        }
    }

    private suspend fun <Args> invoke(
        name: String,
        args: Args,
        trace: MutableList<CreditPreparationToolStep>,
    ): String {
        val result = when (name) {
            "findCustomer" -> (registry.getTool(name) as FindCustomerTool).execute(args as FindCustomerTool.Args)
            "findProduct" -> (registry.getTool(name) as FindProductTool).execute(args as FindProductTool.Args)
            "getCustomerBalance" -> (registry.getTool(name) as GetCustomerBalanceTool).execute(args as GetCustomerBalanceTool.Args)
            "prepareCreditSale" -> (registry.getTool(name) as PrepareCreditSaleTool).execute(args as PrepareCreditSaleTool.Args)
            else -> error("Tool is not allowlisted: $name")
        }
        trace += CreditPreparationToolStep(name, result)
        return result
    }

    private fun String.blockingReasonOrNull(): String? = when {
        startsWith("AMBIGUOUS ") -> this
        startsWith("NOT_FOUND ") -> this
        else -> null
    }
}
