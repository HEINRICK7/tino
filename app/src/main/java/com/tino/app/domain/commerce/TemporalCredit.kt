package com.tino.app.domain.commerce

import com.tino.app.core.database.CreditEntryEntity
import com.tino.app.core.database.CreditEntryType
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class CreditTemporalStatus {
    OPEN,
    OVERDUE,
    SETTLED,
}

data class CreditTimelineEntry(
    val id: String,
    val customerId: String,
    val customerName: String?,
    val amountCents: Long,
    val occurredAt: Long,
    val dueAt: Long?,
    val outstandingCents: Long,
    val status: CreditTemporalStatus,
    val daysOpen: Long,
    val daysOverdue: Long,
    val referenceId: String?,
)

data class CreditPaymentTimelineEntry(
    val id: String,
    val customerId: String,
    val amountCents: Long,
    val occurredAt: Long,
    val paymentMethod: String,
)

data class CustomerCreditTimeline(
    val customerId: String,
    val customerName: String?,
    val entries: List<CreditTimelineEntry>,
    val payments: List<CreditPaymentTimelineEntry>,
    val currentBalanceCents: Long,
    val openCents: Long,
    val overdueCents: Long,
    /** All immutable ledger events, kept separate from the temporal balance projection. */
    val ledgerEvents: List<CustomerLedgerTimelineEvent> = emptyList(),
)

data class CustomerLedgerTimelineEvent(
    val id: String,
    val type: SharedLedgerEventType,
    val occurredAt: Long,
    val signedAmountCents: Long,
    val paymentMethod: String?,
    val reason: String?,
)

/**
 * Temporal read model for the credit ledger. It does not rewrite ledger facts.
 * Payments without a reference are allocated FIFO to the oldest open credit,
 * which gives the TINO a deterministic and explainable timeline.
 */
@Singleton
class TemporalCreditService @Inject constructor(
    private val commerceRepository: CommerceRepository,
) {
    suspend fun customerTimeline(
        customerId: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): CustomerCreditTimeline {
        val customer = commerceRepository.allCustomersForResolution().firstOrNull { it.id == customerId }
        val entries = commerceRepository.creditEntriesForTimeline()
            .filter { it.customerId == customerId }
        return buildTimeline(customerId, customer?.name, entries, now, zone)
    }

    suspend fun allCustomerTimelines(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CustomerCreditTimeline> {
        val customers = commerceRepository.allCustomersForResolution()
        val entries = commerceRepository.creditEntriesForTimeline()
        return customers.map { customer ->
            buildTimeline(
                customerId = customer.id,
                customerName = customer.name,
                entries = entries.filter { it.customerId == customer.id },
                now = now,
                zone = zone,
            )
        }
    }

    companion object {
        fun buildTimeline(
            customerId: String,
            customerName: String?,
            entries: List<CreditEntryEntity>,
            now: Long,
            zone: ZoneId,
        ): CustomerCreditTimeline {
            val customerEntries = entries.filter { it.customerId == customerId }
            val sales = customerEntries
                .filter {
                    val event = SharedLedgerProjector.fromCreditEntry(it)
                    event.type == SharedLedgerEventType.PURCHASE ||
                        ((event.type == SharedLedgerEventType.ADJUSTMENT ||
                            event.type == SharedLedgerEventType.REVERSAL) &&
                            event.signedAmountCents > 0)
                }
                .sortedWith(compareBy<CreditEntryEntity> { it.occurredAt }.thenBy { it.id })
                .map { MutableCredit(it, it.amountCents) }
            val reductions = customerEntries
                .filter {
                    val event = SharedLedgerProjector.fromCreditEntry(it)
                    event.type == SharedLedgerEventType.PAYMENT ||
                        (event.type == SharedLedgerEventType.ADJUSTMENT &&
                            event.signedAmountCents < 0) ||
                        (event.type == SharedLedgerEventType.SETTLEMENT &&
                            event.signedAmountCents < 0)
                }
                .sortedWith(compareBy<CreditEntryEntity> { it.occurredAt }.thenBy { it.id })
            val payments = reductions.filter {
                SharedLedgerProjector.fromCreditEntry(it).type == SharedLedgerEventType.PAYMENT
            }

            reductions.forEach { payment ->
                var unapplied = (-payment.amountCents).coerceAtLeast(0)
                sales.forEach { sale ->
                    if (unapplied > 0 && sale.outstandingCents > 0) {
                        val applied = minOf(unapplied, sale.outstandingCents)
                        sale.outstandingCents -= applied
                        unapplied -= applied
                    }
                }
            }

            val timelineEntries = sales.map { sale ->
                val status = when {
                    sale.outstandingCents <= 0 -> CreditTemporalStatus.SETTLED
                    sale.entry.dueAt != null && now > sale.entry.dueAt -> CreditTemporalStatus.OVERDUE
                    else -> CreditTemporalStatus.OPEN
                }
                CreditTimelineEntry(
                    id = sale.entry.id,
                    customerId = customerId,
                    customerName = customerName,
                    amountCents = sale.entry.amountCents,
                    occurredAt = sale.entry.occurredAt,
                    dueAt = sale.entry.dueAt,
                    outstandingCents = sale.outstandingCents.coerceAtLeast(0),
                    status = status,
                    daysOpen = daysBetween(sale.entry.occurredAt, now, zone),
                    daysOverdue = if (status == CreditTemporalStatus.OVERDUE) {
                        daysBetween(sale.entry.dueAt ?: now, now, zone)
                    } else {
                        0
                    },
                    referenceId = sale.entry.referenceId,
                )
            }
            val paymentTimeline = payments.map { payment ->
                CreditPaymentTimelineEntry(
                    id = payment.id,
                    customerId = customerId,
                    amountCents = (-payment.amountCents).coerceAtLeast(0),
                    occurredAt = payment.occurredAt,
                    paymentMethod = payment.paymentMethod,
                )
            }
            return CustomerCreditTimeline(
                customerId = customerId,
                customerName = customerName,
                entries = timelineEntries,
                payments = paymentTimeline,
                currentBalanceCents = customerEntries.sumOf { it.amountCents },
                openCents = timelineEntries
                    .filter { it.status == CreditTemporalStatus.OPEN }
                    .sumOf { it.outstandingCents },
                overdueCents = timelineEntries
                    .filter { it.status == CreditTemporalStatus.OVERDUE }
                    .sumOf { it.outstandingCents },
                ledgerEvents = customerEntries
                    .map { entry ->
                        val event = SharedLedgerProjector.fromCreditEntry(entry)
                        CustomerLedgerTimelineEvent(
                            id = event.id,
                            type = event.type,
                            occurredAt = event.occurredAtEpochMs,
                            signedAmountCents = event.signedAmountCents,
                            paymentMethod = event.paymentMethod,
                            reason = event.reason,
                        )
                    }
                    .sortedByDescending { it.occurredAt },
            )
        }

        private fun daysBetween(start: Long, end: Long, zone: ZoneId): Long {
            val startDate = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
            val endDate = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
            return ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0)
        }

        private data class MutableCredit(
            val entry: CreditEntryEntity,
            var outstandingCents: Long,
        )
    }
}
