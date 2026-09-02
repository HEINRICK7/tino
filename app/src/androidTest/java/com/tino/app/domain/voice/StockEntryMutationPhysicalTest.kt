package com.tino.app.domain.voice

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.observability.NoOpAuditLogger
import com.tino.app.domain.commerce.CommerceRepository
import com.tino.app.domain.usecase.RegisterStockEntryUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level mutation gate test. The database is in-memory by design, so
 * this validates the production Room/use-case/safety path without touching the
 * installed pilot's commercial database.
 */
@RunWith(AndroidJUnit4::class)
class StockEntryMutationPhysicalTest {
    private lateinit var database: TinoDatabase
    private lateinit var repository: CommerceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
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
            syncScheduler = object : com.tino.app.core.sync.SyncScheduler {
                override fun schedule() = Unit
            },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun previewConfirmationPersistenceAndReplayBlockWorkOnDevice() = runBlocking {
        repository.createProduct("Produto físico de teste", 850, 2)
        repository.createSupplier("Fornecedor físico de teste")
        val product = database.productDao().all().single()
        val supplier = database.supplierDao().all().single()
        val productId = product.id
        val call = ToolCall(
            name = CommerceToolName.REGISTER_STOCK_RECEIPT,
            arguments = mapOf(
                "product" to product.name,
                "quantity" to "12",
                "unit_cost_cents" to "500",
                "supplier" to supplier.name,
            ),
        )
        val safety = MutationSafetyCoordinator(
            clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC),
            store = InMemoryMutationOperationStore(),
            auditLogger = NoOpAuditLogger,
        )
        val guarded = MutationSafeToolExecutor(
            delegate = CommerceToolDispatcher(repository),
            safety = safety,
        )

        val preview = guarded.preview(call)
        assertEquals(2, database.stockMovementDao().balance(productId))
        val prepared = requireNotNull(preview.preparedMutation)
        assertEquals("12 un", preview.presentation?.let { (it as ToolPreviewPresentation.StockEntry).quantityText })

        assertThrows(IllegalStateException::class.java) {
            runBlocking { guarded.execute(call, confirmed = true) }
        }

        val result = guarded.confirm(call, prepared.confirmation)
        assertEquals("Entrada de mercadoria registrada.", result.message)
        assertEquals(14, database.stockMovementDao().balance(productId))
        assertEquals(1, database.purchaseDao().all().size)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { guarded.confirm(call, prepared.confirmation) }
        }
        assertEquals(14, database.stockMovementDao().balance(productId))
    }
}
