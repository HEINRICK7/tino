package com.tino.app.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.common.UuidV7
import com.tino.app.core.database.DomainEventEntity
import com.tino.app.core.database.CustomerEntity
import com.tino.app.core.database.CreditEntryEntity
import com.tino.app.core.database.CreditEntryType
import com.tino.app.core.database.DirectReceiptEntity
import com.tino.app.domain.finance.FinancialPeriod
import com.tino.app.domain.finance.FinancialProjectionRepository
import com.tino.app.core.database.TinoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class TwoDeviceSyncTest {
    private lateinit var first: TinoDatabase
    private lateinit var second: TinoDatabase
    private lateinit var cloud: InMemoryCloudSyncGateway

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        first = database(context)
        second = database(context)
        cloud = InMemoryCloudSyncGateway()
    }

    @After
    fun tearDown() {
        first.close()
        second.close()
    }

    @Test
    fun secondDevicePullsSameEventOnceAfterDuplicatePush() = runBlocking {
        val productEvent = DomainEventEntity(
            eventId = UuidV7.new(),
            storeId = "store-1",
            deviceId = "device-a",
            aggregateId = "product-1",
            type = "product.created",
            schemaVersion = 1,
            occurredAt = System.currentTimeMillis(),
            payloadJson = JSONObject().put("name", "Café").put("price_cents", 800).toString(),
        )
        first.domainEventDao().insert(productEvent)
        val firstSync = coordinator(first)
        firstSync.syncOnce()
        cloud.push(listOf(productEvent))

        val secondSync = coordinator(second)
        secondSync.syncOnce()
        secondSync.syncOnce()

        assertEquals(1, cloud.storedEventCount())
        assertEquals(1, second.productDao().all().size)
        assertEquals(1, second.domainEventDao().all().size)
    }

    @Test
    fun directReceiptReplayDoesNotCreateSaleOrDuplicateReceipt() = runBlocking {
        val receiptEvent = DomainEventEntity(
            eventId = UuidV7.new(),
            storeId = "store-1",
            deviceId = "device-a",
            aggregateId = "operation-receipt-187",
            type = "direct.receipt.created",
            schemaVersion = 1,
            occurredAt = System.currentTimeMillis(),
            payloadJson = JSONObject()
                .put("receipt_id", "operation-receipt-187")
                .put("operation_id", "operation-receipt-187")
                .put("amount_cents", 18_700)
                .put("payment_method", "card")
                .put("source", "manual")
                .toString(),
        )
        first.domainEventDao().insert(receiptEvent)

        coordinator(first).syncOnce()
        coordinator(second).syncOnce()
        coordinator(second).syncOnce()

        assertEquals(1, second.directReceiptDao().all().size)
        assertEquals(18_700L, second.directReceiptDao().all().single().amountCents)
        assertEquals(0, second.saleDao().all().size)
        assertEquals(0, second.stockMovementDao().all().size)
    }

    @Test
    fun amountOnlyCreditReplayDoesNotCreateSaleOrDuplicateCredit() = runBlocking {
        second.customerDao().insert(CustomerEntity("customer-joao", "João Ferreira", null, System.currentTimeMillis()))
        val creditEvent = DomainEventEntity(
            eventId = UuidV7.new(),
            storeId = "store-1",
            deviceId = "device-a",
            aggregateId = "credit-joao-70",
            type = "credit.receivable.created",
            schemaVersion = 1,
            occurredAt = System.currentTimeMillis(),
            payloadJson = JSONObject()
                .put("entry_id", "credit-joao-70")
                .put("operation_id", "credit-joao-70")
                .put("customer_id", "customer-joao")
                .put("amount_cents", 7_000)
                .toString(),
        )
        first.domainEventDao().insert(creditEvent)

        coordinator(first).syncOnce()
        coordinator(second).syncOnce()
        coordinator(second).syncOnce()

        assertEquals(7_000L, second.creditDao().balance("customer-joao"))
        assertEquals(7_000L, second.creditDao().observeTotalBalance().first())
        assertEquals(1, second.creditDao().all().size)
        assertEquals(0, second.saleDao().all().size)
        assertEquals(0, second.stockMovementDao().all().size)
    }

    @Test
    fun creditDueDateSurvivesRemoteReplay() = runBlocking {
        val now = System.currentTimeMillis()
        val dueAt = now + 5 * 86_400_000L
        second.customerDao().insert(CustomerEntity("customer-maria", "Maria Lina", null, now))
        val creditEvent = DomainEventEntity(
            eventId = UuidV7.new(),
            storeId = "store-1",
            deviceId = "device-a",
            aggregateId = "credit-maria-101",
            type = "credit.receivable.created",
            schemaVersion = 1,
            occurredAt = now,
            payloadJson = JSONObject()
                .put("entry_id", "credit-maria-101")
                .put("customer_id", "customer-maria")
                .put("amount_cents", 10_100)
                .put("due_at", dueAt)
                .toString(),
        )
        first.domainEventDao().insert(creditEvent)

        coordinator(first).syncOnce()
        coordinator(second).syncOnce()

        assertEquals(dueAt, second.creditDao().findById("credit-maria-101")?.dueAt)
    }

    @Test
    fun creditPaymentReplayUpdatesDebtAndPixProjectionOnce() = runBlocking {
        val now = System.currentTimeMillis()
        second.customerDao().insert(CustomerEntity("customer-joao", "João Ferreira", null, now))
        second.creditDao().insert(
            CreditEntryEntity("credit-joao-101", "customer-joao", 10_100, CreditEntryType.SALE, null, now, "credit"),
        )
        val paymentEvent = DomainEventEntity(
            eventId = UuidV7.new(),
            storeId = "store-1",
            deviceId = "device-a",
            aggregateId = "payment-joao-50-pix",
            type = "credit.payment.received",
            schemaVersion = 1,
            occurredAt = now,
            payloadJson = JSONObject()
                .put("entry_id", "payment-joao-50-pix")
                .put("operation_id", "payment-joao-50-pix")
                .put("customer_id", "customer-joao")
                .put("amount_cents", 5_000)
                .put("payment_method", "pix")
                .toString(),
        )
        first.domainEventDao().insert(paymentEvent)

        coordinator(first).syncOnce()
        coordinator(second).syncOnce()
        coordinator(second).syncOnce()

        assertEquals(5_100L, second.creditDao().balance("customer-joao"))
        assertEquals(5_000L, second.creditDao().observeTodayPaymentReceived(now - 1_000, "pix").first())
        assertEquals(2, second.creditDao().all().size)
        assertTrue(second.directReceiptDao().all().isEmpty())
        assertEquals(0, second.saleDao().all().size)
        assertEquals(0, second.stockMovementDao().all().size)
    }

    @Test
    fun legacyCreditPaymentEventWithoutMethodBecomesUnknown() = runBlocking {
        val now = System.currentTimeMillis()
        second.customerDao().insert(CustomerEntity("customer-joao", "João Ferreira", null, now))
        val legacyEvent = DomainEventEntity(
            eventId = UuidV7.new(),
            storeId = "store-1",
            deviceId = "device-a",
            aggregateId = "legacy-payment-100",
            type = "credit.payment.received",
            schemaVersion = 1,
            occurredAt = now,
            payloadJson = JSONObject()
                .put("entry_id", "legacy-payment-100")
                .put("customer_id", "customer-joao")
                .put("amount_cents", 10_000)
                .toString(),
        )
        first.domainEventDao().insert(legacyEvent)

        coordinator(first).syncOnce()
        coordinator(second).syncOnce()

        assertEquals("unknown", second.creditDao().findById("legacy-payment-100")?.paymentMethod)
        assertEquals(10_000L, second.creditDao().observeTodayPaymentReceived(now - 1_000, "unknown").first())
        assertEquals(0L, second.creditDao().observeTodayPaymentReceived(now - 1_000, "cash").first())
    }

    @Test
    fun syncedFactsProduceEquivalentFinancialSummary() = runBlocking {
        val now = System.currentTimeMillis()
        val period = FinancialPeriod(now - 1_000, now + 1_000, ZoneId.systemDefault().id)
        val customerId = "customer-joao"
        second.customerDao().insert(CustomerEntity(customerId, "João Ferreira", null, now))
        first.directReceiptDao().insert(DirectReceiptEntity("receipt-pix", 400, "pix", now, "manual", null, "receipt-pix"))
        first.creditDao().insert(CreditEntryEntity("credit-opening", customerId, 1_000, CreditEntryType.SALE, null, now, "credit"))
        second.creditDao().insert(CreditEntryEntity("credit-opening", customerId, 1_000, CreditEntryType.SALE, null, now, "credit"))
        first.creditDao().insert(CreditEntryEntity("payment-pix", customerId, -200, CreditEntryType.PAYMENT, null, now, "pix"))

        first.domainEventDao().insert(
            DomainEventEntity(
                eventId = UuidV7.new(),
                storeId = "store-1",
                deviceId = "device-a",
                aggregateId = "receipt-pix",
                type = "direct.receipt.created",
                schemaVersion = 1,
                occurredAt = now,
                payloadJson = JSONObject()
                    .put("receipt_id", "receipt-pix")
                    .put("operation_id", "receipt-pix")
                    .put("amount_cents", 400)
                    .put("payment_method", "pix")
                    .put("source", "manual")
                    .toString(),
            ),
        )
        first.domainEventDao().insert(
            DomainEventEntity(
                eventId = UuidV7.new(),
                storeId = "store-1",
                deviceId = "device-a",
                aggregateId = "payment-pix",
                type = "credit.payment.received",
                schemaVersion = 1,
                occurredAt = now,
                payloadJson = JSONObject()
                    .put("entry_id", "payment-pix")
                    .put("operation_id", "payment-pix")
                    .put("customer_id", customerId)
                    .put("amount_cents", 200)
                    .put("payment_method", "pix")
                    .toString(),
            ),
        )

        coordinator(first).syncOnce()
        coordinator(second).syncOnce()
        coordinator(second).syncOnce()

        val firstSummary = FinancialProjectionRepository(first.financialProjectionDao()).summary(period)
        val secondSummary = FinancialProjectionRepository(second.financialProjectionDao()).summary(period)
        assertEquals(firstSummary, secondSummary)
    }

    private fun coordinator(database: TinoDatabase) = SyncCoordinator(
        database = database,
        eventDao = database.domainEventDao(),
        cursorDao = database.syncCursorDao(),
        gateway = cloud,
        remoteEventApplier = RemoteEventApplier(database),
    )

    private fun database(context: Context) = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
        .allowMainThreadQueries()
        .build()
}
