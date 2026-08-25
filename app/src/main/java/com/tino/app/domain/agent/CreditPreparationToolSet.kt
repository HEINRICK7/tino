package com.tino.app.domain.agent

import com.tino.app.domain.commerce.CommerceRepository
import com.tino.app.domain.commerce.EntityResolutionMatch
import com.tino.app.domain.commerce.EntityResolutionService
import com.tino.app.domain.voice.CommerceToolName
import com.tino.app.domain.voice.ToolCall
import com.tino.app.domain.voice.ToolExecutor
import com.tino.app.domain.voice.ToolPreview
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Framework-independent tool boundary for the Koog/Gemma spike.
 *
 * The model only supplies textual references. This set resolves them locally
 * and never returns internal IDs. The write tool deliberately stops at a
 * preview; commit remains owned by the existing confirmation boundary.
 */
@Singleton
class CreditPreparationToolSet @Inject constructor(
    private val entityResolver: EntityResolutionService,
    private val commerceRepository: CommerceRepository,
    private val toolExecutor: ToolExecutor,
) {
    suspend fun findCustomer(reference: String): CreditToolResult =
        when (val match = entityResolver.resolveCustomer(reference)) {
            is EntityResolutionMatch.Resolved -> CreditToolResult.CustomerFound(match.value.name)
            is EntityResolutionMatch.Ambiguous -> CreditToolResult.Ambiguous(
                entityType = "customer",
                options = match.values.map { it.name },
            )
            EntityResolutionMatch.NotFound -> CreditToolResult.NotFound("customer")
        }

    suspend fun findProduct(reference: String): CreditToolResult =
        when (val match = entityResolver.resolveProduct(reference)) {
            is EntityResolutionMatch.Resolved -> CreditToolResult.ProductFound(
                name = match.value.name,
                priceCents = match.value.priceCents,
                stockQuantity = commerceRepository.stockBalance(match.value.id),
            )
            is EntityResolutionMatch.Ambiguous -> CreditToolResult.Ambiguous(
                entityType = "product",
                options = match.values.map { it.name },
            )
            EntityResolutionMatch.NotFound -> CreditToolResult.NotFound("product")
        }

    suspend fun getCustomerBalance(reference: String): CreditToolResult {
        val match = entityResolver.resolveCustomer(reference)
        return when (match) {
            is EntityResolutionMatch.Resolved -> CreditToolResult.CustomerBalance(
                customerName = match.value.name,
                balanceCents = commerceRepository.customerBalance(match.value.id),
            )
            is EntityResolutionMatch.Ambiguous -> CreditToolResult.Ambiguous(
                entityType = "customer",
                options = match.values.map { it.name },
            )
            EntityResolutionMatch.NotFound -> CreditToolResult.NotFound("customer")
        }
    }

    suspend fun getProductStock(reference: String): CreditToolResult {
        val match = entityResolver.resolveProduct(reference)
        return when (match) {
            is EntityResolutionMatch.Resolved -> CreditToolResult.ProductStock(
                productName = match.value.name,
                stockQuantity = commerceRepository.stockBalance(match.value.id),
            )
            is EntityResolutionMatch.Ambiguous -> CreditToolResult.Ambiguous(
                entityType = "product",
                options = match.values.map { it.name },
            )
            EntityResolutionMatch.NotFound -> CreditToolResult.NotFound("product")
        }
    }

    suspend fun getCurrentPrice(reference: String): CreditToolResult {
        val match = entityResolver.resolveProduct(reference)
        return when (match) {
            is EntityResolutionMatch.Resolved -> CreditToolResult.ProductPrice(
                productName = match.value.name,
                priceCents = match.value.priceCents,
            )
            is EntityResolutionMatch.Ambiguous -> CreditToolResult.Ambiguous(
                entityType = "product",
                options = match.values.map { it.name },
            )
            EntityResolutionMatch.NotFound -> CreditToolResult.NotFound("product")
        }
    }

    suspend fun prepareCreditSale(
        customerReference: String,
        productReference: String,
        quantity: Int,
    ): CreditToolResult {
        require(quantity > 0) { "A quantidade precisa ser maior que zero." }
        val call = ToolCall(
            name = CommerceToolName.ADD_CREDIT_ITEM,
            arguments = mapOf(
                "customer" to customerReference,
                "product" to productReference,
                "quantity" to quantity.toString(),
            ),
        )
        return runCatching {
            CreditToolResult.CreditSalePreview(
                call = call,
                preview = toolExecutor.preview(call),
            )
        }.getOrElse { error ->
            CreditToolResult.Rejected(error.message ?: "Não foi possível preparar o fiado.")
        }
    }
}

sealed interface CreditToolResult {
    data class CustomerFound(val name: String) : CreditToolResult

    data class ProductFound(
        val name: String,
        val priceCents: Long,
        val stockQuantity: Int,
    ) : CreditToolResult

    data class CustomerBalance(
        val customerName: String,
        val balanceCents: Long,
    ) : CreditToolResult

    data class ProductStock(
        val productName: String,
        val stockQuantity: Int,
    ) : CreditToolResult

    data class ProductPrice(
        val productName: String,
        val priceCents: Long,
    ) : CreditToolResult

    data class CreditSalePreview(
        val call: ToolCall,
        val preview: ToolPreview,
    ) : CreditToolResult

    data class Ambiguous(
        val entityType: String,
        val options: List<String>,
    ) : CreditToolResult

    data class NotFound(val entityType: String) : CreditToolResult

    data class Rejected(val message: String) : CreditToolResult
}
