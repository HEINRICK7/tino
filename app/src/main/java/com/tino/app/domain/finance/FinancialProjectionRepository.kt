package com.tino.app.domain.finance

import com.tino.app.core.database.FinancialProjectionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinancialProjectionRepository @Inject constructor(
    private val dao: FinancialProjectionDao,
) {
    fun observeSummary(period: FinancialPeriod): Flow<FinancialSummary> =
        dao.observeSummary(period.startAt, period.endAtExclusive).map { it.toDomain(period) }

    suspend fun summary(period: FinancialPeriod): FinancialSummary = observeSummary(period).first()

    private fun com.tino.app.core.database.FinancialSummaryRow.toDomain(
        period: FinancialPeriod,
    ) = FinancialSummary(
        period = period,
        receivedTotalCents = receivedTotalCents,
        receivedCashCents = receivedCashCents,
        receivedPixCents = receivedPixCents,
        receivedCardCents = receivedCardCents,
        receivedUnknownCents = receivedUnknownCents,
        totalReceivableCents = totalReceivableCents,
        creditCreatedCents = creditCreatedCents,
        creditPaymentsReceivedCents = creditPaymentsReceivedCents,
    )
}
