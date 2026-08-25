package com.tino.app.domain.orders

import java.time.Instant

enum class OrderChannel { WHATSAPP, MANUAL }
enum class FulfillmentType { PICKUP, DELIVERY }
enum class OrderStatus { DRAFT, CONFIRMED, PREPARING, READY, DELIVERED, CANCELLED }

data class CatalogItem(
    val productId: String,
    val name: String,
    val priceCents: Long,
)

data class IncomingOrderLine(val productName: String, val quantity: Int)

data class OrderLine(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPriceCents: Long,
)

data class OrderDraft(
    val id: String,
    val channel: OrderChannel,
    val lines: List<OrderLine>,
    val totalCents: Long,
    val fulfillment: FulfillmentType,
    val addressReference: String?,
    val status: OrderStatus = OrderStatus.DRAFT,
    val createdAt: Instant = Instant.now(),
)

interface CatalogLookup {
    suspend fun findProduct(name: String): CatalogItem?
}

interface CustomerOrderChannel {
    suspend fun sendConfirmation(order: OrderDraft): String
    suspend fun sendStatus(order: OrderDraft): String
}
