package com.tino.app.core.intelligence

import com.tino.app.core.database.CreditEntryType
import com.tino.app.domain.commerce.CommerceRepository
import com.tino.app.domain.finance.FinancialPeriod
import com.tino.app.domain.finance.FinancialProjectionRepository
import com.tino.app.domain.intelligence.IntelligenceCustomer
import com.tino.app.domain.intelligence.IntelligenceEntityResolution
import com.tino.app.domain.intelligence.IntelligenceFactsPort
import com.tino.app.domain.intelligence.IntelligencePaymentEvent
import com.tino.app.domain.intelligence.IntelligenceProduct
import com.tino.app.domain.intelligence.IntelligenceReceivable
import com.tino.app.domain.intelligence.IntelligenceStockMovement
import com.tino.app.domain.intelligence.PaymentEventType
import java.time.Clock
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Infrastructure adapter: the runtime sees only this port, never Room. */
@Singleton
class RoomCommerceIntelligenceFacts @Inject constructor(
    private val commerce: CommerceRepository,
    private val financial: FinancialProjectionRepository,
    private val clock: Clock,
) : IntelligenceFactsPort {
    override suspend fun financialSummary(period: FinancialPeriod) = financial.summary(period)

    override suspend fun customers(): List<IntelligenceCustomer> = commerce.allCustomersForResolution().map {
        IntelligenceCustomer(it.id, it.name, it.phone)
    }

    override suspend fun receivables(): List<IntelligenceReceivable> =
        commerce.observeCustomerBalances().first().map {
            IntelligenceReceivable(it.id, it.name, it.balanceCents)
        }

    override suspend fun paymentEvents(customerId: String): List<IntelligencePaymentEvent> =
        commerce.creditEntriesForTimeline()
            .filter { it.customerId == customerId }
            .map {
                IntelligencePaymentEvent(
                    customerId = it.customerId,
                    type = if (it.type == CreditEntryType.SALE) PaymentEventType.SALE else PaymentEventType.PAYMENT,
                    amountCents = kotlin.math.abs(it.amountCents),
                    occurredAtEpochMs = it.occurredAt,
                )
            }

    override suspend fun resolveCustomer(reference: String): IntelligenceEntityResolution<IntelligenceCustomer> =
        resolve(reference, customers()) { it.name }

    override suspend fun products(): List<IntelligenceProduct> {
        val movements = commerce.stockMovementsForIntelligence()
        val byProduct = movements.groupingBy { it.productId }.fold(0) { total, movement -> total + movement.quantityDelta }
        return commerce.allProductsForResolution().map {
            IntelligenceProduct(it.id, it.name, it.priceCents, byProduct[it.id] ?: 0)
        }
    }

    override suspend fun resolveProduct(reference: String): IntelligenceEntityResolution<IntelligenceProduct> =
        resolve(reference, products()) { it.name }

    override suspend fun stockMovements(productId: String): List<IntelligenceStockMovement> =
        commerce.stockMovementsForIntelligence()
            .filter { it.productId == productId }
            .map { IntelligenceStockMovement(it.productId, it.quantityDelta, it.reason, it.occurredAt) }

    private fun <T> resolve(
        reference: String,
        values: List<T>,
        name: (T) -> String,
    ): IntelligenceEntityResolution<T> {
        val normalized = normalize(reference)
        val exact = values.filter { normalize(name(it)) == normalized }
        if (exact.size == 1) return IntelligenceEntityResolution.Resolved(exact.single())
        val partial = values.filter { normalize(name(it)).contains(normalized) || normalized.contains(normalize(name(it))) }
        return when (partial.size) {
            0 -> IntelligenceEntityResolution.NotFound
            1 -> IntelligenceEntityResolution.Resolved(partial.single())
            else -> IntelligenceEntityResolution.Ambiguous(partial)
        }
    }

    private fun normalize(value: String): String = java.text.Normalizer
        .normalize(value.trim(), java.text.Normalizer.Form.NFD)
        .replace("\\p{M}".toRegex(), "")
        .lowercase()
}
