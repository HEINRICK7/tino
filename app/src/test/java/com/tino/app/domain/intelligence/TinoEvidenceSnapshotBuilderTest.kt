package com.tino.app.domain.intelligence

import com.tino.app.domain.finance.FinancialPeriod
import com.tino.app.domain.finance.FinancialSummary
import com.tino.app.domain.language.BusinessMemoryKind
import com.tino.app.domain.language.BusinessMemoryRecord
import com.tino.app.domain.language.BusinessMemoryStorePort
import com.tino.app.domain.language.GovernedBusinessMemory
import com.tino.app.domain.language.MemoryCandidate
import com.tino.app.domain.language.MemoryConfidence
import com.tino.app.domain.language.MemoryProvenance
import com.tino.app.domain.language.MemoryProvenanceType
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TinoEvidenceSnapshotBuilderTest {
    private val now = 1_700_000_000_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.of("UTC"))

    @Test
    fun buildsTemporalFinancialCustomerAndGovernedMemoryContextFromPorts() = runBlocking {
        val memory = GovernedBusinessMemory(RecordingMemoryStore(), clock = clock)
        val candidate = MemoryCandidate(
            scopeKey = "default-store",
            memoryKey = "delivery_day",
            value = "quarta-feira",
            kind = BusinessMemoryKind.WORKFLOW_PREFERENCE,
            confidence = MemoryConfidence(0.9),
            provenance = MemoryProvenance(MemoryProvenanceType.USER_CONFIRMATION, occurredAtEpochMs = now),
        )
        memory.record(candidate)
        memory.record(candidate.copy(id = "second-confirmation"))

        val snapshot = TinoEvidenceSnapshotBuilder(
            facts = FakeFacts(now),
            analytics = DeterministicBusinessAnalytics(),
            businessMemory = memory,
            clock = clock,
        ).build(screen = "Products")

        val product = snapshot.products.single()
        assertEquals(8, product.unitsSoldLast30Days)
        assertEquals(3, product.unitsSoldPrevious30Days)
        assertTrue(product.unitsSoldByWeekday.isNotEmpty())
        assertEquals("Distribuidora Norte", product.supplierName)
        assertEquals(2, product.supplierPurchaseCountLast90Days)
        assertEquals(1_200L, product.lastPurchaseCostCents)
        assertEquals(1_000L, product.previousPurchaseCostCents)
        assertEquals(now - 500L, product.supplierExpectedDeliveryAtEpochMs)
        assertEquals(0, product.supplierLateDeliveryCount)
        assertEquals(500L, snapshot.customers.single().balanceCents)
        assertEquals(now - 1_000L, snapshot.customers.single().promisedPaymentAtEpochMs)
        assertEquals(800L, snapshot.currentWeekReceivedCents)
        assertEquals(400L, snapshot.previousWeekReceivedCents)
        assertTrue(snapshot.currentWeekElapsedDays != null)
        assertEquals(1, snapshot.memories.size)
        assertEquals("delivery day", snapshot.memories.single().key)
    }

    private class FakeFacts(private val now: Long) : IntelligenceFactsPort {
        private val product = IntelligenceProduct("p1", "Café", 1_200, 10)
        private val customer = IntelligenceCustomer("c1", "Maria", "999")

        override suspend fun financialSummary(period: FinancialPeriod): FinancialSummary {
            val current = period.endAtExclusive > now
            return FinancialSummary(
                period = period,
                receivedTotalCents = if (current) 800L else 400L,
                receivedCashCents = if (current) 600L else 300L,
                receivedPixCents = if (current) 200L else 100L,
                receivedCardCents = 0L,
                receivedUnknownCents = 0L,
                totalReceivableCents = 500L,
                creditCreatedCents = 0L,
                creditPaymentsReceivedCents = 0L,
            )
        }

        override suspend fun customers() = listOf(customer)

        override suspend fun receivables() = listOf(IntelligenceReceivable("c1", "Maria", 500L))

        override suspend fun paymentEvents(customerId: String) = listOf(
            IntelligencePaymentEvent("c1", PaymentEventType.SALE, 500L, now - 3_000L, now - 1_000L),
            IntelligencePaymentEvent("c1", PaymentEventType.PAYMENT, 500L, now - 1_000L),
        )

        override suspend fun resolveCustomer(reference: String) = IntelligenceEntityResolution.NotFound

        override suspend fun products() = listOf(product)

        override suspend fun resolveProduct(reference: String) = IntelligenceEntityResolution.NotFound

        override suspend fun supplierLinks() = listOf(
            IntelligenceSupplierLink("p1", "s1", "Distribuidora Norte", now),
        )

        override suspend fun supplierPurchases() = listOf(
            IntelligenceSupplierPurchase("p1", "s1", "Distribuidora Norte", now - 1_000L, 10, 1_200L),
            IntelligenceSupplierPurchase("p1", "s1", "Distribuidora Norte", now - 2_000L, 8, 1_000L),
        )

        override suspend fun supplierDeliveries() = listOf(
            IntelligenceSupplierDelivery(
                purchaseId = "order-1",
                productId = "p1",
                supplierId = "s1",
                supplierName = "Distribuidora Norte",
                orderedAtEpochMs = now - 2_000L,
                expectedDeliveryAtEpochMs = now - 500L,
                receivedAtEpochMs = null,
                quantity = 10,
            ),
        )

        override suspend fun stockMovements(productId: String) = listOf(
            IntelligenceStockMovement(productId, -8, "sale", now - 1_000L),
            IntelligenceStockMovement(productId, -3, "sale", now - 31L * 24L * 60L * 60L * 1_000L),
        )
    }

    private class RecordingMemoryStore : BusinessMemoryStorePort {
        private val records = linkedMapOf<String, BusinessMemoryRecord>()

        override suspend fun find(scopeKey: String, memoryKey: String, value: String): BusinessMemoryRecord? =
            records.values.firstOrNull { it.scopeKey == scopeKey && it.memoryKey == memoryKey && it.value == value }

        override suspend fun findByKey(scopeKey: String, memoryKey: String): List<BusinessMemoryRecord> =
            records.values.filter { it.scopeKey == scopeKey && it.memoryKey == memoryKey }

        override suspend fun upsert(record: BusinessMemoryRecord) {
            records[record.id] = record
        }

        override suspend fun list(scopeKey: String): List<BusinessMemoryRecord> =
            records.values.filter { it.scopeKey == scopeKey }
    }
}
