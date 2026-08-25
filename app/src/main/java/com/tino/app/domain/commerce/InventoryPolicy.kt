package com.tino.app.domain.commerce

/**
 * Business rule for deciding when an inventory item needs replenishment.
 *
 * The default is intentionally conservative until these values are persisted
 * per product: only zero stock is presented as a purchase recommendation.
 */
data class InventoryPolicy(
    val minimumStock: Int,
    val reorderPoint: Int,
) {
    init {
        require(minimumStock >= 0) { "O estoque mínimo não pode ser negativo." }
        require(reorderPoint >= 0) { "O ponto de reposição não pode ser negativo." }
    }

    fun needsReplenishment(stockQuantity: Int): Boolean =
        stockQuantity <= reorderPoint || stockQuantity < minimumStock

    companion object {
        val conservativeDefault = InventoryPolicy(minimumStock = 0, reorderPoint = 0)
    }
}
