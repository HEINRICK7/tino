package com.tino.app.domain.commerce

object CommerceRules {
    fun saleTotal(
        unitPriceCents: Long,
        quantity: Int,
        availableStock: Int,
        productName: String,
    ): Long {
        require(unitPriceCents > 0) { "O preço precisa ser maior que zero." }
        require(quantity > 0) { "A quantidade precisa ser maior que zero." }
        check(availableStock >= quantity) { "Estoque insuficiente para $productName." }
        return unitPriceCents * quantity
    }
}
