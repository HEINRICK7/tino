package com.tino.app.domain.commerce

/**
 * Payment methods are intentionally distinct because they represent different
 * cash-flow realities for the merchant.
 */
enum class PaymentMethod(val storageValue: String) {
    CASH("cash"),
    PIX("pix"),
    CARD("card"),
    CREDIT("credit"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromStorage(value: String?): PaymentMethod = entries.firstOrNull {
            it.storageValue.equals(value, ignoreCase = true)
        } ?: UNKNOWN
    }
}
