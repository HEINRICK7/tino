package com.tino.app.domain.intelligence

import com.tino.app.domain.finance.FinancialPeriod
import com.tino.app.domain.language.BusinessMemoryPort
import com.tino.app.domain.language.MemoryLifecycle
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the intelligence context from governed application ports.
 * It deliberately does not write commerce state and keeps missing history explicit.
 */
@Singleton
class TinoEvidenceSnapshotBuilder @Inject constructor(
    private val facts: IntelligenceFactsPort,
    private val analytics: BusinessAnalyticsPort,
    private val businessMemory: BusinessMemoryPort,
    private val clock: Clock,
) {
    suspend fun build(
        screen: String,
        recommendations: List<Recommendation> = emptyList(),
        todayReceivedCents: Long = 0L,
        todayPixCents: Long = 0L,
        todaySales: Int = 0,
        entityProductId: String? = null,
        entityCustomerId: String? = null,
        scopeKey: String = DEFAULT_BUSINESS_SCOPE_KEY,
    ): TinoEvidenceSnapshot {
        val now = clock.millis()
        val zone = clock.zone
        val productFacts = facts.products()
        val supplierLinks = facts.supplierLinks()
        val supplierByProduct = supplierLinks
            .groupBy { it.productId }
            .mapValues { (_, links) -> links.maxByOrNull { it.linkedAtEpochMs }?.supplierName }
        val supplierIdByProduct = supplierLinks
            .groupBy { it.productId }
            .mapValues { (_, links) -> links.maxByOrNull { it.linkedAtEpochMs }?.supplierId }
        val purchasesByProduct = facts.supplierPurchases().groupBy { it.productId }
        val deliveriesByProduct = facts.supplierDeliveries().groupBy { it.productId }
        val ninetyDaysAgo = now - NINETY_DAYS_MS
        val oneEightyDaysAgo = now - 2L * NINETY_DAYS_MS
        val products = productFacts.map { product ->
            val movements = facts.stockMovements(product.id)
            val velocity = analytics.calculateStockVelocity(product, movements, now)
            val unitsSoldByDate = movements
                .asSequence()
                .filter { it.reason == SALE_REASON && it.quantityDelta < 0 }
                .groupBy { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate() }
                .mapValues { (_, entries) -> entries.sumOf { -it.quantityDelta } }
            val purchases = purchasesByProduct[product.id].orEmpty().sortedByDescending { it.purchasedAtEpochMs }
            val recentPurchases = purchases.filter { it.purchasedAtEpochMs >= ninetyDaysAgo }
            val deliveries = deliveriesByProduct[product.id].orEmpty()
            val supplierDelivery = deliveries.firstOrNull()
            val expectedDelivery = deliveries
                .filter { it.receivedAtEpochMs == null && it.expectedDeliveryAtEpochMs != null }
                .minByOrNull { it.expectedDeliveryAtEpochMs!! }
            val completedDeliveries = deliveries.filter { it.receivedAtEpochMs != null && it.expectedDeliveryAtEpochMs != null }
            val lateDeliveries = completedDeliveries.count { it.receivedAtEpochMs!! > it.expectedDeliveryAtEpochMs!! }
            val onTimeDeliveries = completedDeliveries.size - lateDeliveries
            TinoEvidenceProduct(
                id = product.id,
                name = product.name,
                stockQuantity = product.stockQuantity,
                unitsSoldPrevious30Days = velocity.unitsPreviousPeriod.takeIf { velocity.featureQuality != FeatureQuality.INSUFFICIENT },
                unitsSoldLast30Days = velocity.unitsLastPeriod.takeIf { velocity.featureQuality != FeatureQuality.INSUFFICIENT },
                unitsSoldByWeekday = movements
                    .asSequence()
                    .filter { it.reason == SALE_REASON && it.quantityDelta < 0 }
                    .groupBy { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).dayOfWeek }
                    .mapValues { (_, entries) -> entries.sumOf { -it.quantityDelta } },
                unitsSoldByDate = unitsSoldByDate,
                demandModelEvaluation = TinoDemandModelValidator.evaluate(unitsSoldByDate),
                lastMovementAtEpochMs = movements.maxOfOrNull { it.occurredAtEpochMs },
                supplierId = supplierIdByProduct[product.id] ?: supplierDelivery?.supplierId,
                supplierName = supplierByProduct[product.id] ?: supplierDelivery?.supplierName,
                supplierPurchaseCountLast90Days = recentPurchases.size,
                lastPurchaseAtEpochMs = purchases.firstOrNull()?.purchasedAtEpochMs,
                lastPurchaseCostCents = purchases.getOrNull(0)?.unitCostCents,
                previousPurchaseCostCents = purchases.getOrNull(1)?.unitCostCents,
                supplierExpectedDeliveryAtEpochMs = expectedDelivery?.expectedDeliveryAtEpochMs,
                supplierLastReceivedAtEpochMs = deliveries.maxOfOrNull { it.receivedAtEpochMs ?: Long.MIN_VALUE }
                    ?.takeIf { it != Long.MIN_VALUE },
                supplierLateDeliveryCount = lateDeliveries,
                supplierOnTimeDeliveryCount = onTimeDeliveries,
            )
        }
        val balances = facts.receivables().associateBy { it.customerId }
        val customers = facts.customers().map { customer ->
            val events = facts.paymentEvents(customer.id)
            val behavior = analytics.calculatePaymentBehavior(events)
            val saleTimes = events.filter { it.type == PaymentEventType.SALE }
                .map { it.occurredAtEpochMs }
                .sorted()
            val recentPurchases = saleTimes.count { it >= ninetyDaysAgo }
            val previousPurchases = saleTimes.count { it in oneEightyDaysAgo until ninetyDaysAgo }
            val averageIntervalDays = saleTimes.zipWithNext()
                .map { (earlier, later) -> (later - earlier).toDouble() / DAY_MS }
                .takeIf { it.isNotEmpty() }
                ?.average()
            val balanceChangeLast30Cents = events
                .filter { it.occurredAtEpochMs >= ninetyDaysAgo + 60L * DAY_MS }
                .sumOf { if (it.type == PaymentEventType.SALE) it.amountCents else -it.amountCents }
            val lastPaymentAtEpochMs = events.filter { it.type == PaymentEventType.PAYMENT }
                .maxOfOrNull { it.occurredAtEpochMs }
            TinoEvidenceCustomer(
                id = customer.id,
                name = customer.name,
                balanceCents = balances[customer.id]?.outstandingCents ?: 0L,
                lastActivityAtEpochMs = events.maxOfOrNull { it.occurredAtEpochMs },
                promisedPaymentAtEpochMs = events
                    .asSequence()
                    .filter { it.type == PaymentEventType.SALE }
                    .mapNotNull { it.dueAtEpochMs }
                    .maxOrNull(),
                averagePaymentDelayDays = behavior.averagePaymentDelayDays,
                balanceChangeLast30Cents = balanceChangeLast30Cents,
                purchaseCountLast90Days = recentPurchases,
                purchaseCountPrevious90Days = previousPurchases,
                averagePurchaseIntervalDays = averageIntervalDays,
                lastPaymentAtEpochMs = lastPaymentAtEpochMs,
            )
        }
        val currentWeek = FinancialPeriod.thisWeek(clock)
        val previousWeek = FinancialPeriod(
            startAt = currentWeek.startAt - Duration.ofDays(7).toMillis(),
            endAtExclusive = currentWeek.startAt,
            zoneId = currentWeek.zoneId,
        )
        val currentSummary = facts.financialSummary(currentWeek)
        val previousSummary = facts.financialSummary(previousWeek)
        val memories = businessMemory.list(scopeKey)
            .filter { it.lifecycle == MemoryLifecycle.LEARNED || it.lifecycle == MemoryLifecycle.TRUSTED }
            .map { TinoEvidenceMemory(it.memoryKey, it.value, it.confidence.value) }

        return TinoEvidenceSnapshot(
            screen = screen,
            products = products,
            customers = customers,
            recommendations = recommendations,
            todayReceivedCents = todayReceivedCents,
            todayPixCents = todayPixCents,
            todaySales = todaySales,
            weekday = Instant.ofEpochMilli(now).atZone(zone).dayOfWeek,
            entityProductId = entityProductId,
            entityCustomerId = entityCustomerId,
            nowEpochMs = now,
            currentWeekReceivedCents = currentSummary.receivedTotalCents,
            previousWeekReceivedCents = previousSummary.receivedTotalCents,
            currentWeekElapsedDays = ((now - currentWeek.startAt) / DAY_MS).toInt().coerceIn(1, 7),
            receivedByMethod = currentSummary.receivedBreakdownCents,
            memories = memories,
        )
    }

    private companion object {
        const val SALE_REASON = "sale"
        const val DEFAULT_BUSINESS_SCOPE_KEY = "default-store"
        const val DAY_MS = 24L * 60L * 60L * 1_000L
        const val NINETY_DAYS_MS = 90L * DAY_MS
    }
}
