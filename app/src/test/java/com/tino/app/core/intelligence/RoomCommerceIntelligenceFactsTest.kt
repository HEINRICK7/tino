package com.tino.app.core.intelligence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.database.ProductPurchaseHistoryEntity
import com.tino.app.core.database.PurchaseStatus
import com.tino.app.core.database.SupplierProductMappingEntity
import com.tino.app.core.sync.SyncScheduler
import com.tino.app.domain.commerce.CommerceRepository
import com.tino.app.domain.commerce.PaymentMethod
import com.tino.app.domain.finance.FinancialProjectionRepository
import com.tino.app.domain.intelligence.PaymentEventType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode
import java.time.Clock

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RoomCommerceIntelligenceFactsTest {
    private lateinit var database: TinoDatabase
    private lateinit var repository: CommerceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CommerceRepository(
            database = database,
            productDao = database.productDao(),
            saleDao = database.saleDao(),
            directReceiptDao = database.directReceiptDao(),
            stockMovementDao = database.stockMovementDao(),
            customerDao = database.customerDao(),
            creditDao = database.creditDao(),
            supplierDao = database.supplierDao(),
            purchaseDao = database.purchaseDao(),
            identityProvider = IdentityProvider(context),
            syncScheduler = object : SyncScheduler {
                override fun schedule() = Unit
            },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun intelligenceFactsUseLedgerSemanticsForReceivablesAndPaymentHistory() = runBlocking {
        repository.createCustomer("Maria")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 1_000, operationId = "facts-purchase")
        repository.registerCreditPayment(customerId, 200, PaymentMethod.PIX, "facts-payment")
        repository.registerCreditAdjustment(
            customerId = customerId,
            amountCents = -100,
            reason = "Abatimento autorizado",
            operationId = "facts-adjustment",
        )

        val facts = RoomCommerceIntelligenceFacts(
            commerce = repository,
            financial = FinancialProjectionRepository(database.financialProjectionDao()),
            clock = Clock.systemUTC(),
        )

        val receivable = facts.receivables().single()
        val events = facts.paymentEvents(customerId)

        assertEquals(700L, receivable.outstandingCents)
        assertEquals(2, events.size)
        assertEquals(PaymentEventType.SALE, events[0].type)
        assertEquals(PaymentEventType.PAYMENT, events[1].type)
        assertEquals(1_000L, events[0].amountCents)
        assertEquals(200L, events[1].amountCents)
    }

    @Test
    fun intelligenceFactsExposeSupplierLinksAndPurchaseHistory() = runBlocking {
        repository.createProduct("Café", 1_200, 2)
        repository.createSupplier("Distribuidora Norte")
        val product = database.productDao().all().single()
        val supplier = database.supplierDao().all().single()
        database.supplierProductMappingDao().insert(
            SupplierProductMappingEntity(
                id = "mapping-1",
                supplierId = supplier.id,
                supplierProductCode = "CAF-1",
                gtin = null,
                supplierDescription = "Café",
                productId = product.id,
                confirmedAt = 1_000L,
                matchMethod = "test",
            ),
        )
        database.productPurchaseHistoryDao().insert(
            ProductPurchaseHistoryEntity(
                id = "purchase-history-1",
                fiscalDocumentId = "doc-1",
                supplierId = supplier.id,
                productId = product.id,
                purchasedAt = 2_000L,
                fiscalQuantity = "10",
                stockQuantity = 10,
                unitPurchaseCostCents = 900L,
                totalCostCents = 9_000L,
            ),
        )

        val facts = RoomCommerceIntelligenceFacts(
            commerce = repository,
            financial = FinancialProjectionRepository(database.financialProjectionDao()),
            clock = Clock.systemUTC(),
        )

        assertEquals("Distribuidora Norte", facts.supplierLinks().single().supplierName)
        assertEquals(900L, facts.supplierPurchases().single().unitCostCents)
    }

    @Test
    fun intelligenceFactsExposeExpectedAndActualSupplierDelivery() = runBlocking {
        repository.createProduct("Café", 1_200, 0)
        repository.createSupplier("Distribuidora Norte")
        val product = database.productDao().all().single()
        val supplier = database.supplierDao().all().single()
        val expectedAt = System.currentTimeMillis() + 3L * 24L * 60L * 60L * 1_000L
        val purchaseId = repository.createSupplierOrder(product.id, 10, 900L, supplier.id, expectedAt)

        val facts = RoomCommerceIntelligenceFacts(
            commerce = repository,
            financial = FinancialProjectionRepository(database.financialProjectionDao()),
            clock = Clock.systemUTC(),
        )
        val pending = facts.supplierDeliveries().single()
        assertEquals(purchaseId, pending.purchaseId)
        assertEquals(expectedAt, pending.expectedDeliveryAtEpochMs)
        assertEquals(null, pending.receivedAtEpochMs)

        repository.receiveSupplierOrder(purchaseId)
        val received = facts.supplierDeliveries().single()
        assertEquals(PurchaseStatus.RECEIVED.name, database.purchaseDao().findById(purchaseId)!!.status.name)
        assertTrue(received.receivedAtEpochMs != null)
        assertEquals(10, repository.stockBalance(product.id))
    }
}
