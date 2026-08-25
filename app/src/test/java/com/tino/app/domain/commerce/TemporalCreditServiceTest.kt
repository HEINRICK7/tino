package com.tino.app.domain.commerce

import com.tino.app.core.database.CreditEntryEntity
import com.tino.app.core.database.CreditEntryType
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalCreditServiceTest {
    private val zone = ZoneId.of("America/Fortaleza")
    private val now = Instant.parse("2026-08-17T15:00:00Z").toEpochMilli()
    private val customerId = "customer-maria"

    @Test
    fun legacyCreditWithoutDueDateIsOpenButNotOverdue() {
        val result = TemporalCreditService.buildTimeline(
            customerId = customerId,
            customerName = "Maria Lina",
            entries = listOf(sale("sale-legacy", 101_00, daysAgo = 73)),
            now = now,
            zone = zone,
        )

        val entry = result.entries.single()
        assertEquals(CreditTemporalStatus.OPEN, entry.status)
        assertEquals(73L, entry.daysOpen)
        assertEquals(0L, result.overdueCents)
        assertNull(entry.dueAt)
    }

    @Test
    fun realDueDateCreatesOverdueStatusAndDays() {
        val result = TemporalCreditService.buildTimeline(
            customerId = customerId,
            customerName = "João Ferreira",
            entries = listOf(sale("sale-overdue", 82_00, daysAgo = 30, dueDaysAgo = 18)),
            now = now,
            zone = zone,
        )

        val entry = result.entries.single()
        assertEquals(CreditTemporalStatus.OVERDUE, entry.status)
        assertEquals(18L, entry.daysOverdue)
        assertEquals(8_200L, result.overdueCents)
    }

    @Test
    fun paymentIsAllocatedFifoAndDoesNotInventOverdueStatus() {
        val result = TemporalCreditService.buildTimeline(
            customerId = customerId,
            customerName = "Maria Lina",
            entries = listOf(
                sale("sale-old", 120_00, daysAgo = 73),
                sale("sale-new", 101_00, daysAgo = 10),
                payment("payment", 120_00, daysAgo = 2),
            ),
            now = now,
            zone = zone,
        )

        assertEquals(CreditTemporalStatus.SETTLED, result.entries[0].status)
        assertEquals(0L, result.entries[0].outstandingCents)
        assertEquals(CreditTemporalStatus.OPEN, result.entries[1].status)
        assertEquals(10_100L, result.entries[1].outstandingCents)
        assertEquals(10_100L, result.currentBalanceCents)
        assertEquals(1, result.payments.size)
        assertTrue(result.payments.single().amountCents == 12_000L)
    }

    @Test
    fun futureDueDateIsOpenAndNotOverdue() {
        val result = TemporalCreditService.buildTimeline(
            customerId = customerId,
            customerName = "Maria Lina",
            entries = listOf(sale("sale-future", 50_00, daysAgo = 2, dueDaysFromNow = 5)),
            now = now,
            zone = zone,
        )

        assertEquals(CreditTemporalStatus.OPEN, result.entries.single().status)
        assertEquals(0L, result.overdueCents)
    }

    private fun sale(
        id: String,
        amountCents: Long,
        daysAgo: Long,
        dueDaysAgo: Long? = null,
        dueDaysFromNow: Long? = null,
    ) = CreditEntryEntity(
        id = id,
        customerId = customerId,
        amountCents = amountCents,
        type = CreditEntryType.SALE,
        referenceId = null,
        occurredAt = now - daysAgo * DAY,
        paymentMethod = "credit",
        dueAt = dueDaysAgo?.let { now - it * DAY } ?: dueDaysFromNow?.let { now + it * DAY },
    )

    private fun payment(id: String, amountCents: Long, daysAgo: Long) = CreditEntryEntity(
        id = id,
        customerId = customerId,
        amountCents = -amountCents,
        type = CreditEntryType.PAYMENT,
        referenceId = null,
        occurredAt = now - daysAgo * DAY,
        paymentMethod = "pix",
    )

    companion object {
        private const val DAY = 86_400_000L
    }
}
