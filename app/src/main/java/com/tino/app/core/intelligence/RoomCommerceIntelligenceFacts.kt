package com.tino.app.core.intelligence

import com.tino.app.domain.commerce.CommerceRepository
import com.tino.app.domain.commerce.SharedLedgerEventType
import com.tino.app.domain.commerce.SharedLedgerProjector
import com.tino.app.domain.finance.FinancialPeriod
import com.tino.app.domain.finance.FinancialProjectionRepository
import com.tino.app.domain.intelligence.IntelligenceCustomer
import com.tino.app.domain.intelligence.IntelligenceEntityResolution
import com.tino.app.domain.intelligence.IntelligenceFactsPort
import com.tino.app.domain.intelligence.IntelligencePaymentEvent
import com.tino.app.domain.intelligence.IntelligenceProduct
import com.tino.app.domain.intelligence.IntelligenceReceivable
import com.tino.app.domain.intelligence.IntelligenceStockMovement
import com.tino.app.domain.intelligence.IntelligenceSupplierLink
import com.tino.app.domain.intelligence.IntelligenceSupplierPurchase
import com.tino.app.domain.intelligence.IntelligenceSupplierDelivery
import com.tino.app.core.database.PurchaseStatus
import com.tino.app.domain.intelligence.PaymentEventType
import java.time.Clock
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

    override suspend fun receivables(): List<IntelligenceReceivable> {
        val customers = commerce.allCustomersForResolution().associateBy { it.id }
        return commerce.allSharedLedgerProjections().mapNotNull { projection ->
            customers[projection.customerId]?.let { customer ->
                IntelligenceReceivable(customer.id, customer.name, projection.balanceCents)
            }
        }
    }

    override suspend fun paymentEvents(customerId: String): List<IntelligencePaymentEvent> =
        commerce.creditEntriesForTimeline()
            .filter { it.customerId == customerId }
            .mapNotNull { entry ->
                when (SharedLedgerProjector.fromCreditEntry(entry).type) {
                    SharedLedgerEventType.PURCHASE -> IntelligencePaymentEvent(
                        customerId = entry.customerId,
                        type = PaymentEventType.SALE,
                        amountCents = kotlin.math.abs(entry.amountCents),
                        occurredAtEpochMs = entry.occurredAt,
                        dueAtEpochMs = entry.dueAt,
                    )
                    SharedLedgerEventType.PAYMENT -> IntelligencePaymentEvent(
                        customerId = entry.customerId,
                        type = PaymentEventType.PAYMENT,
                        amountCents = kotlin.math.abs(entry.amountCents),
                        occurredAtEpochMs = entry.occurredAt,
                        dueAtEpochMs = entry.dueAt,
                    )
                    else -> null
                }
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

    override suspend fun supplierLinks(): List<IntelligenceSupplierLink> {
        val suppliers = commerce.allSuppliersForResolution().associateBy { it.id }
        return commerce.supplierProductMappingsForIntelligence().mapNotNull { mapping ->
            suppliers[mapping.supplierId]?.let { supplier ->
                IntelligenceSupplierLink(
                    productId = mapping.productId,
                    supplierId = supplier.id,
                    supplierName = supplier.name,
                    linkedAtEpochMs = mapping.confirmedAt,
                )
            }
        }
    }

    override suspend fun supplierPurchases(): List<IntelligenceSupplierPurchase> {
        val suppliers = commerce.allSuppliersForResolution().associateBy { it.id }
        return commerce.productPurchaseHistoryForIntelligence().mapNotNull { purchase ->
            suppliers[purchase.supplierId]?.let { supplier ->
                IntelligenceSupplierPurchase(
                    productId = purchase.productId,
                    supplierId = supplier.id,
                    supplierName = supplier.name,
                    purchasedAtEpochMs = purchase.purchasedAt,
                    quantity = purchase.stockQuantity,
                    unitCostCents = purchase.unitPurchaseCostCents,
                )
            }
        }
    }

    override suspend fun supplierDeliveries(): List<IntelligenceSupplierDelivery> {
        val suppliers = commerce.allSuppliersForResolution().associateBy { it.id }
        val itemsByPurchase = commerce.purchaseItemsForIntelligence().groupBy { it.purchaseId }
        return commerce.purchasesForIntelligence().flatMap { purchase ->
            val supplier = purchase.supplierId?.let(suppliers::get) ?: return@flatMap emptyList()
            val receivedAt = purchase.receivedAt ?: purchase.createdAt.takeIf {
                purchase.status == PurchaseStatus.RECEIVED || purchase.status == PurchaseStatus.COMPLETED
            }
            itemsByPurchase[purchase.id].orEmpty().map { item ->
                IntelligenceSupplierDelivery(
                    purchaseId = purchase.id,
                    productId = item.productId,
                    supplierId = supplier.id,
                    supplierName = supplier.name,
                    orderedAtEpochMs = purchase.createdAt,
                    expectedDeliveryAtEpochMs = purchase.expectedDeliveryAt,
                    receivedAtEpochMs = receivedAt,
                    quantity = item.quantity,
                )
            }
        }
    }

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
