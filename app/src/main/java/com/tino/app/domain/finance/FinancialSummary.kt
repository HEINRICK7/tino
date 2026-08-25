package com.tino.app.domain.finance

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

data class FinancialPeriod(
    val startAt: Long,
    val endAtExclusive: Long,
    val zoneId: String,
) {
    init {
        require(endAtExclusive > startAt) { "O período financeiro precisa ser válido." }
    }

    val zone: ZoneId get() = ZoneId.of(zoneId)

    companion object {
        fun today(clock: Clock = Clock.systemDefaultZone()): FinancialPeriod {
            val zone = clock.zone
            val date = Instant.now(clock).atZone(zone).toLocalDate()
            return forDates(date, date.plusDays(1), zone)
        }

        fun thisWeek(clock: Clock = Clock.systemDefaultZone()): FinancialPeriod {
            val zone = clock.zone
            val date = Instant.now(clock).atZone(zone).toLocalDate()
            val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return forDates(start, start.plusDays(7), zone)
        }

        fun thisMonth(clock: Clock = Clock.systemDefaultZone()): FinancialPeriod {
            val zone = clock.zone
            val date = Instant.now(clock).atZone(zone).toLocalDate()
            val start = date.withDayOfMonth(1)
            return forDates(start, start.plusMonths(1).withDayOfMonth(1), zone)
        }

        private fun forDates(
            start: java.time.LocalDate,
            endExclusive: java.time.LocalDate,
            zone: ZoneId,
        ) = FinancialPeriod(
            startAt = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            endAtExclusive = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
            zoneId = zone.id,
        )
    }
}

data class FinancialSummary(
    val period: FinancialPeriod,
    val receivedTotalCents: Long,
    val receivedCashCents: Long,
    val receivedPixCents: Long,
    val receivedCardCents: Long,
    val receivedUnknownCents: Long,
    val totalReceivableCents: Long,
    val creditCreatedCents: Long,
    val creditPaymentsReceivedCents: Long,
) {
    val receivedBreakdownCents: Map<String, Long> = mapOf(
        "cash" to receivedCashCents,
        "pix" to receivedPixCents,
        "card" to receivedCardCents,
        "unknown" to receivedUnknownCents,
    )
}
