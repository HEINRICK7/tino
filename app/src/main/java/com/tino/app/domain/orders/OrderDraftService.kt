package com.tino.app.domain.orders

import com.tino.app.core.common.UuidV7
import com.tino.app.domain.commerce.CommerceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderDraftService @Inject constructor(
    private val catalog: CatalogLookup,
) {
    suspend fun createDraft(
        lines: List<IncomingOrderLine>,
        channel: OrderChannel = OrderChannel.MANUAL,
        fulfillment: FulfillmentType = FulfillmentType.PICKUP,
        addressReference: String? = null,
    ): OrderDraft {
        require(lines.isNotEmpty()) { "O pedido precisa ter pelo menos um item." }
        val resolved = lines.map { line ->
            require(line.quantity > 0) { "A quantidade do pedido precisa ser maior que zero." }
            val product = catalog.findProduct(line.productName.trim())
                ?: error("Produto não encontrado: ${line.productName}.")
            OrderLine(product.productId, product.name, line.quantity, product.priceCents)
        }
        return OrderDraft(
            id = UuidV7.new(),
            channel = channel,
            lines = resolved,
            totalCents = resolved.sumOf { it.quantity * it.unitPriceCents },
            fulfillment = fulfillment,
            addressReference = addressReference?.trim()?.ifBlank { null },
        )
    }

    fun confirm(draft: OrderDraft, confirmed: Boolean): OrderDraft {
        check(confirmed) { "O pedido precisa de confirmação humana." }
        check(draft.status == OrderStatus.DRAFT) { "O pedido não está aguardando confirmação." }
        return draft.copy(status = OrderStatus.CONFIRMED)
    }
}

@Singleton
class CommerceCatalogLookup @Inject constructor(
    private val commerceRepository: CommerceRepository,
) : CatalogLookup {
    override suspend fun findProduct(name: String): CatalogItem? = commerceRepository.findProductByName(name)
        ?.let { CatalogItem(it.id, it.name, it.priceCents) }
}

class WhatsAppOrderParser @Inject constructor() {
    fun parse(message: String): List<IncomingOrderLine> {
        val normalized = message
            .replace(Regex("\\s+e\\s+", RegexOption.IGNORE_CASE), ",")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return normalized.map { item ->
            val match = Regex("^(\\d+)\\s+(.+)$").find(item)
                ?: error("Não entendi o item: $item")
            IncomingOrderLine(match.groupValues[2].trim(), match.groupValues[1].toInt())
        }
    }
}
