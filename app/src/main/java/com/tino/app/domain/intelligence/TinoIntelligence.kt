package com.tino.app.domain.intelligence

import java.time.DayOfWeek
import kotlin.math.ceil

enum class ThoughtUncertainty {
    KNOW,
    SUSPECT,
    AMBIGUOUS,
}

data class PaymentMatchCandidate(
    val customerId: String,
    val customerName: String,
    val balanceCents: Long,
    val confidence: Double,
)

sealed interface PaymentMatchResult {
    data object None : PaymentMatchResult
    data class UniqueSuspect(
        val candidate: PaymentMatchCandidate,
    ) : PaymentMatchResult
    data class Ambiguous(
        val candidates: List<PaymentMatchCandidate>,
    ) : PaymentMatchResult
}

/**
 * Relates an unmatched Pix amount to open debts. Amount equality is never
 * enough to KNOW; two equal debts make the match AMBIGUOUS.
 */
object TinoPaymentMatcher {
    const val UNIQUE_AMOUNT_CONFIDENCE = 0.61
    const val AMBIGUOUS_AMOUNT_CONFIDENCE = 0.58
    const val KNOW_THRESHOLD = 0.95

    fun match(pixCents: Long, debtors: List<TinoEvidenceCustomer>): PaymentMatchResult {
        if (pixCents <= 0L) return PaymentMatchResult.None
        val exact = debtors.filter { it.balanceCents == pixCents }
        return when {
            exact.size >= 2 -> PaymentMatchResult.Ambiguous(
                exact.map {
                    PaymentMatchCandidate(it.id, it.name, it.balanceCents, AMBIGUOUS_AMOUNT_CONFIDENCE)
                },
            )
            exact.size == 1 -> PaymentMatchResult.UniqueSuspect(
                PaymentMatchCandidate(
                    exact.single().id,
                    exact.single().name,
                    exact.single().balanceCents,
                    UNIQUE_AMOUNT_CONFIDENCE,
                ),
            )
            else -> PaymentMatchResult.None
        }
    }

    fun knows(confidence: Double): Boolean = confidence >= KNOW_THRESHOLD
}

data class StockoutForecast(
    val days: Int,
    val confidence: Double,
    val dailyRate: Double,
)

object TinoStockoutForecast {
    fun estimate(stockQuantity: Int, soldLast30Days: Int): StockoutForecast? {
        if (stockQuantity <= 0 || soldLast30Days < 3) return null
        val dailyRate = soldLast30Days / 30.0
        if (dailyRate <= 0.0) return null
        val days = ceil(stockQuantity / dailyRate).toInt().coerceAtLeast(1)
        val confidence = when {
            soldLast30Days >= 16 -> 0.82
            soldLast30Days >= 8 -> 0.70
            else -> 0.55
        }
        return StockoutForecast(days = days, confidence = confidence, dailyRate = dailyRate)
    }
}

fun DayOfWeek.isNearWeekend(): Boolean =
    this == DayOfWeek.THURSDAY || this == DayOfWeek.FRIDAY
