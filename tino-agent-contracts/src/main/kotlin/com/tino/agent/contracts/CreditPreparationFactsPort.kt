package com.tino.agent.contracts

/**
 * The only contract shared by the Android adapter and the Koog sandbox.
 * It contains no Android, Room, Koog or TINO repository types.
 */
interface CreditPreparationFactsPort {
    suspend fun findCustomer(reference: String): CustomerLookup
    suspend fun findProduct(reference: String): ProductLookup
    suspend fun getCustomerBalance(reference: String): BalanceLookup
    suspend fun prepareCreditSale(
        customerReference: String,
        productReference: String,
        quantity: Int,
    ): CreditPreparationLookup
}

sealed interface CustomerLookup {
    data class Resolved(
        val name: String,
        val timing: CreditFactsTiming,
    ) : CustomerLookup

    data class Ambiguous(val options: List<String>) : CustomerLookup
    data object NotFound : CustomerLookup
}

sealed interface ProductLookup {
    data class Resolved(
        val name: String,
        val priceCents: Long,
        val stockQuantity: Int,
        val timing: CreditFactsTiming,
    ) : ProductLookup

    data class Ambiguous(val options: List<String>) : ProductLookup
    data object NotFound : ProductLookup
}

sealed interface BalanceLookup {
    data class Resolved(
        val customerName: String,
        val balanceCents: Long,
        val timing: CreditFactsTiming,
    ) : BalanceLookup

    data class Ambiguous(val options: List<String>) : BalanceLookup
    data object NotFound : BalanceLookup
}

sealed interface CreditPreparationLookup {
    data class Preview(
        val title: String,
        val detail: String,
        val confirmLabel: String,
        val timing: CreditFactsTiming,
    ) : CreditPreparationLookup

    data class Ambiguous(
        val entityType: String,
        val options: List<String>,
    ) : CreditPreparationLookup

    data class NotFound(val entityType: String) : CreditPreparationLookup
    data class Rejected(val message: String) : CreditPreparationLookup
}

data class CreditFactsTiming(
    val customerResolutionMs: Long = 0L,
    val productResolutionMs: Long = 0L,
    val balanceMs: Long = 0L,
    val priceMs: Long = 0L,
    val stockMs: Long = 0L,
    val prepareMs: Long = 0L,
)
