package com.tino.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.sync.CommerceSnapshotRepository
import com.tino.app.core.sync.SyncScheduler
import com.tino.app.domain.commerce.CommerceRepository
import com.tino.app.domain.commerce.PaymentMethod
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class ArchitectureAcceptanceTest {
    private val databaseName = "tino-acceptance.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun offlineHoursReopenSyncAndDeviceRecoveryPreserveEquivalentState() = runBlocking {
        var deviceA = openPersistentDatabase()
        val commerceA = repository(deviceA)
        commerceA.createProduct("Café", 100, 100)
        val productId = deviceA.productDao().all().single().id
        commerceA.createCustomer("João")
        val customerId = deviceA.customerDao().all().single().id

        repeat(20) { commerceA.registerSale(productId, 1) }
        repeat(5) { commerceA.registerCreditSale(customerId, productId, 1) }
        repeat(3) { commerceA.registerCreditPayment(customerId, 100, PaymentMethod.CASH) }
        repeat(2) { commerceA.registerStockReceipt(productId, 10, 70) }
        deviceA.close()

        // Reopen the same local database after an offline app restart.
        deviceA = openPersistentDatabase()
        assertEquals(95, deviceA.stockMovementDao().balance(productId))
        assertEquals(200L, deviceA.creditDao().balance(customerId))
        assertEquals(40, deviceA.domainEventDao().all().size)

        // A cloud snapshot can reconstruct an equivalent second device.
        val snapshot = CommerceSnapshotRepository(deviceA).export()
        val deviceB = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            CommerceSnapshotRepository(deviceB).restore(snapshot)
            assertEquals(95, deviceB.stockMovementDao().balance(productId))
            assertEquals(200L, deviceB.creditDao().balance(customerId))
            assertEquals(40, deviceB.domainEventDao().all().size)
        } finally {
            deviceB.close()
            deviceA.close()
        }
    }

    private fun openPersistentDatabase(): TinoDatabase = Room.databaseBuilder(
        context,
        TinoDatabase::class.java,
        databaseName,
    ).allowMainThreadQueries().build()

    private fun repository(database: TinoDatabase) = CommerceRepository(
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
