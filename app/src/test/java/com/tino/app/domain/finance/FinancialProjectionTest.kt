package com.tino.app.domain.finance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.database.CreditEntryEntity
import com.tino.app.core.database.CreditEntryType
import com.tino.app.core.database.DirectReceiptEntity
import com.tino.app.core.database.SaleEntity
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.sync.CommerceSnapshotRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode
import java.time.ZoneId
import java.time.Clock
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class FinancialProjectionTest {
    private lateinit var database: TinoDatabase
    private lateinit var projection: FinancialProjectionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projection = FinancialProjectionRepository(database.financialProjectionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun summaryCombinesFactsOnceAndKeepsUnknownSeparateFromCash() = runBlocking {
        val now = System.currentTimeMillis()
        insertFacts(now)
        val summary = projection.summary(period(now))

        assertEquals(725L, summary.receivedTotalCents)
        assertEquals(240L, summary.receivedCashCents)
        assertEquals(300L, summary.receivedPixCents)
        assertEquals(0L, summary.receivedCardCents)
        assertEquals(185L, summary.receivedUnknownCents)
        assertEquals(775L, summary.totalReceivableCents)
        assertEquals(1_000L, summary.creditCreatedCents)
        assertEquals(225L, summary.creditPaymentsReceivedCents)
        assertEquals(725L, summary.receivedBreakdownCents.values.sum())
    }

    @Test
    fun summaryFiltersFlowsByPeriodButKeepsReceivableAsCurrentStock() = runBlocking {
        val now = System.currentTimeMillis()
        val old = now - 10 * 24 * 60 * 60 * 1_000L
        database.saleDao().insert(SaleEntity("current-sale", 100, "cash", now))
        database.saleDao().insert(SaleEntity("old-sale", 999, "pix", old))
        database.directReceiptDao().insert(DirectReceiptEntity("current-receipt", 50, "cash", now, "manual", null, "current-receipt"))
        database.directReceiptDao().insert(DirectReceiptEntity("old-receipt", 500, "pix", old, "manual", null, "old-receipt"))
        database.creditDao().insert(CreditEntryEntity("current-credit", "joao", 700, CreditEntryType.SALE, null, now, "credit"))
        database.creditDao().insert(CreditEntryEntity("old-credit", "joao", 800, CreditEntryType.SALE, null, old, "credit"))
        database.creditDao().insert(CreditEntryEntity("current-payment", "joao", -200, CreditEntryType.PAYMENT, null, now, "pix"))
        database.creditDao().insert(CreditEntryEntity("old-payment", "joao", -300, CreditEntryType.PAYMENT, null, old, "cash"))

        val summary = projection.summary(period(now))

        assertEquals(350L, summary.receivedTotalCents)
        assertEquals(150L, summary.receivedCashCents)
        assertEquals(200L, summary.receivedPixCents)
        assertEquals(1_000L, summary.totalReceivableCents)
        assertEquals(700L, summary.creditCreatedCents)
        assertEquals(200L, summary.creditPaymentsReceivedCents)
    }

    @Test
    fun snapshotRestoreProducesEquivalentSummary() = runBlocking {
        val now = System.currentTimeMillis()
        insertFacts(now)
        val expected = projection.summary(period(now))
        val snapshot = CommerceSnapshotRepository(database).export()
        val restored = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TinoDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            CommerceSnapshotRepository(restored).restore(snapshot)
            val restoredSummary = FinancialProjectionRepository(restored.financialProjectionDao()).summary(period(now))
            assertEquals(expected, restoredSummary)
        } finally {
            restored.close()
        }
    }

    @Test
    fun periodFactoriesUseExplicitTimezoneBoundaries() {
        val zone = ZoneId.of("America/Fortaleza")
        val clock = Clock.fixed(Instant.parse("2026-08-17T01:30:00Z"), zone)

        val today = FinancialPeriod.today(clock)

        assertEquals(zone.id, today.zoneId)
        assertEquals("2026-08-16T00:00-03:00[America/Fortaleza]", Instant.ofEpochMilli(today.startAt).atZone(zone).toString())
        assertEquals("2026-08-17T00:00-03:00[America/Fortaleza]", Instant.ofEpochMilli(today.endAtExclusive).atZone(zone).toString())
    }

    private suspend fun insertFacts(now: Long) {
        database.saleDao().insert(SaleEntity("sale-cash", 100, "cash", now))
        database.saleDao().insert(SaleEntity("sale-pix", 200, "pix", now))
        database.saleDao().insert(SaleEntity("sale-credit", 300, "credit", now))
        database.saleDao().insert(SaleEntity("sale-unknown", 50, "unknown", now))
        database.directReceiptDao().insert(DirectReceiptEntity("receipt-cash", 40, "cash", now, "manual", null, "receipt-cash"))
        database.directReceiptDao().insert(DirectReceiptEntity("receipt-pix", 50, "pix", now, "manual", null, "receipt-pix"))
        database.directReceiptDao().insert(DirectReceiptEntity("receipt-unknown", 60, "unknown", now, "manual", null, "receipt-unknown"))
        database.creditDao().insert(CreditEntryEntity("credit-created", "joao", 1_000, CreditEntryType.SALE, null, now, "credit"))
        database.creditDao().insert(CreditEntryEntity("payment-cash", "joao", -100, CreditEntryType.PAYMENT, null, now, "cash"))
        database.creditDao().insert(CreditEntryEntity("payment-pix", "joao", -50, CreditEntryType.PAYMENT, null, now, "pix"))
        database.creditDao().insert(CreditEntryEntity("payment-unknown", "joao", -75, CreditEntryType.PAYMENT, null, now, "unknown"))
    }

    private fun period(now: Long) = FinancialPeriod(
        startAt = now - 1_000,
        endAtExclusive = now + 1_000,
        zoneId = ZoneId.systemDefault().id,
    )
}
