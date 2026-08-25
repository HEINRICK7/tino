package com.tino.app.domain.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.sync.SyncScheduler
import com.tino.app.domain.commerce.CommerceRepository
import com.tino.app.domain.commerce.EntityResolutionService
import com.tino.app.domain.commerce.NoopAuditLogger
import com.tino.app.domain.voice.CommerceToolDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CreditPreparationToolSetTest {
    private lateinit var database: TinoDatabase
    private lateinit var repository: CommerceRepository

    @Before
    fun setUp() = runBlocking {
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
        repository.createProduct("Café Maratá", 850, 24)
        repository.createCustomer("Maria Lina")
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun toolsResolveRealFactsWithoutReturningInternalIds() = runBlocking {
        val set = toolSet()

        val customer = set.findCustomer("Dona Maria Lina") as CreditToolResult.CustomerFound
        val product = set.findProduct("café maratá") as CreditToolResult.ProductFound
        val balance = set.getCustomerBalance("Maria Lina") as CreditToolResult.CustomerBalance

        assertEquals("Maria Lina", customer.name)
        assertEquals("Café Maratá", product.name)
        assertEquals(850L, product.priceCents)
        assertEquals(24, product.stockQuantity)
        assertEquals(0L, balance.balanceCents)
    }

    @Test
    fun prepareCreditSaleReturnsPreviewAndDoesNotMutate() = runBlocking {
        val set = toolSet()
        val result = set.prepareCreditSale("Maria Lina", "Café Maratá", 1)

        assertTrue(result is CreditToolResult.CreditSalePreview)
        val preview = (result as CreditToolResult.CreditSalePreview).preview
        assertTrue(preview.detail.contains("R$ 8,50"))
        assertTrue(preview.detail.contains("Estoque depois: 23"))
        assertEquals(0L, repository.customerBalance(database.customerDao().all().single().id))
        assertEquals(24, database.stockMovementDao().balance(database.productDao().all().single().id))
        assertFalse(database.domainEventDao().all().any { it.type == "credit.sale.created" })
    }

    @Test
    fun unknownEntityNeverBecomesARealFact() = runBlocking {
        val set = toolSet()

        assertTrue(set.findCustomer("João inexistente") is CreditToolResult.NotFound)
        assertTrue(set.findProduct("Produto inexistente") is CreditToolResult.NotFound)
    }

    private fun toolSet(): CreditPreparationToolSet {
        val resolver = EntityResolutionService(repository, NoopAuditLogger)
        return CreditPreparationToolSet(
            entityResolver = resolver,
            commerceRepository = repository,
            toolExecutor = CommerceToolDispatcher(repository, resolver),
        )
    }
}
