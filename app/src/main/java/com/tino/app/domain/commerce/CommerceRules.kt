package com.tino.app.domain.commerce

object CommerceRules {
    fun saleTotal(
        unitPriceCents: Long,
        quantity: Int,
        availableStock: Int,
        productName: String,
        stockTracked: Boolean = true,
    ): Long {
        require(unitPriceCents > 0) { "O preço precisa ser maior que zero." }
        require(quantity > 0) { "A quantidade precisa ser maior que zero." }
        if (stockTracked) {
            check(availableStock >= quantity) { "Estoque insuficiente para $productName." }
        }
        return unitPriceCents * quantity
    }
}
