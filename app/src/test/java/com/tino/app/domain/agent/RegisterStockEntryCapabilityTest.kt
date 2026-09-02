package com.tino.app.domain.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.sync.SyncScheduler
import com.tino.app.domain.commerce.CommerceRepository
import com.tino.app.domain.usecase.RegisterStockEntryCommand
import com.tino.app.domain.usecase.RegisterStockEntryUseCase
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
class RegisterStockEntryCapabilityTest {
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
    fun handlerRegistersStockThroughUseCaseAndRoom() = runBlocking {
        repository.createProduct("Café Maratá", 850, 2)
        repository.createSupplier("Distribuidora Central")
        val productId = database.productDao().all().single().id
        val supplierId = database.supplierDao().all().single().id
        val handler = RegisterStockEntryCapabilityHandler(RegisterStockEntryUseCase(repository))

        val result = handler.execute(
            RegisterStockEntryCommand(
                productId = productId,
                quantity = 12,
                unitCostCents = 500,
                supplierId = supplierId,
            ),
        )

        assertEquals(TinoCapabilityId.REGISTER_STOCK_ENTRY, handler.capability)
        assertEquals(productId, result.productId)
        assertEquals(12, result.quantity)
        assertEquals(500L, result.unitCostCents)
        assertEquals(supplierId, result.supplierId)
        assertEquals(14, database.stockMovementDao().balance(productId))
        assertEquals(1, database.purchaseDao().all().size)
    }
}
