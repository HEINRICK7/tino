package com.tino.app.core.agent

import com.tino.agent.contracts.BalanceLookup
import com.tino.agent.contracts.CreditFactsTiming
import com.tino.agent.contracts.CreditPreparationFactsPort
import com.tino.agent.contracts.CreditPreparationLookup
import com.tino.agent.contracts.CustomerLookup
import com.tino.agent.contracts.ProductLookup
import com.tino.app.domain.commerce.EntityResolutionMatch
import com.tino.app.domain.commerce.EntityResolutionService
import com.tino.app.domain.voice.ToolClarificationException
import com.tino.app.domain.voice.ToolExecutor
import com.tino.app.domain.voice.ToolCall
import com.tino.app.domain.voice.CommerceToolName
import com.tino.app.domain.commerce.CommerceRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-side implementation of the framework-neutral Koog spike port.
 * Koog never receives this class, Room, repository instances or internal IDs.
 */
@Singleton
class AndroidCreditPreparationFactsAdapter @Inject constructor(
    private val entityResolver: EntityResolutionService,
    private val commerceRepository: CommerceRepository,
    private val toolExecutor: ToolExecutor,
) : CreditPreparationFactsPort {
    override suspend fun findCustomer(reference: String): CustomerLookup {
        val startedAt = System.nanoTime()
        return when (val match = entityResolver.resolveCustomer(reference)) {
            is EntityResolutionMatch.Resolved -> CustomerLookup.Resolved(
                name = match.value.name,
                timing = CreditFactsTiming(customerResolutionMs = elapsedMs(startedAt)),
            )
            is EntityResolutionMatch.Ambiguous -> CustomerLookup.Ambiguous(match.values.map { it.name })
            EntityResolutionMatch.NotFound -> CustomerLookup.NotFound
        }
    }

    override suspend fun findProduct(reference: String): ProductLookup {
        val resolutionStartedAt = System.nanoTime()
        return when (val match = entityResolver.resolveProduct(reference)) {
            is EntityResolutionMatch.Resolved -> {
                val product = match.value
                val productResolutionMs = elapsedMs(resolutionStartedAt)
                val priceStartedAt = System.nanoTime()
                val priceCents = product.priceCents
                val priceMs = elapsedMs(priceStartedAt)
                val stockStartedAt = System.nanoTime()
                val stockQuantity = commerceRepository.stockBalance(product.id)
                ProductLookup.Resolved(
                    name = product.name,
                    priceCents = priceCents,
                    stockQuantity = stockQuantity,
                    timing = CreditFactsTiming(
                        productResolutionMs = productResolutionMs,
                        priceMs = priceMs,
                        stockMs = elapsedMs(stockStartedAt),
                    ),
                )
            }
            is EntityResolutionMatch.Ambiguous -> ProductLookup.Ambiguous(match.values.map { it.name })
            EntityResolutionMatch.NotFound -> ProductLookup.NotFound
        }
    }

    override suspend fun getCustomerBalance(reference: String): BalanceLookup {
        val resolutionStartedAt = System.nanoTime()
        return when (val match = entityResolver.resolveCustomer(reference)) {
            is EntityResolutionMatch.Resolved -> {
                val customerResolutionMs = elapsedMs(resolutionStartedAt)
                val balanceStartedAt = System.nanoTime()
                val balanceCents = commerceRepository.customerBalance(match.value.id)
                BalanceLookup.Resolved(
                    customerName = match.value.name,
                    balanceCents = balanceCents,
                    timing = CreditFactsTiming(
                        customerResolutionMs = customerResolutionMs,
                        balanceMs = elapsedMs(balanceStartedAt),
                    ),
                )
            }
            is EntityResolutionMatch.Ambiguous -> BalanceLookup.Ambiguous(match.values.map { it.name })
            EntityResolutionMatch.NotFound -> BalanceLookup.NotFound
        }
    }

    override suspend fun prepareCreditSale(
        customerReference: String,
        productReference: String,
        quantity: Int,
    ): CreditPreparationLookup {
        require(quantity > 0) { "A quantidade precisa ser maior que zero." }
        val startedAt = System.nanoTime()
        val call = ToolCall(
            name = CommerceToolName.ADD_CREDIT_ITEM,
            arguments = mapOf(
                "customer" to customerReference,
                "product" to productReference,
                "quantity" to quantity.toString(),
            ),
        )
        return try {
            val preview = toolExecutor.preview(call)
            val diagnostics = preview.diagnostics
            CreditPreparationLookup.Preview(
                title = preview.title,
                detail = preview.detail,
                confirmLabel = preview.confirmLabel,
                timing = CreditFactsTiming(
                    customerResolutionMs = diagnostics?.customerResolutionMs ?: 0L,
                    productResolutionMs = diagnostics?.productResolutionMs ?: 0L,
                    prepareMs = elapsedMs(startedAt),
                ),
            )
        } catch (error: ToolClarificationException) {
            when {
                error.options.isNotEmpty() -> CreditPreparationLookup.Ambiguous(
                    entityType = error.argumentKey ?: "entity",
                    options = error.options,
                )
                error.argumentKey != null -> CreditPreparationLookup.NotFound(error.argumentKey)
                else -> CreditPreparationLookup.Rejected(error.message ?: "Não foi possível preparar o fiado.")
            }
        } catch (error: IllegalArgumentException) {
            CreditPreparationLookup.Rejected(error.message ?: "Não foi possível preparar o fiado.")
        }
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt).coerceAtLeast(0L) / 1_000_000L
}
