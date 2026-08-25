package com.tino.app.core.agent

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
import com.tino.app.domain.voice.ToolCall
import com.tino.app.domain.voice.ToolExecutionResult
import com.tino.app.domain.voice.ToolExecutor
import com.tino.app.domain.voice.ToolPreview
import com.tino.agent.contracts.BalanceLookup
import com.tino.agent.contracts.CreditPreparationLookup
import com.tino.agent.contracts.CustomerLookup
import com.tino.agent.contracts.ProductLookup
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
class AndroidCreditPreparationFactsAdapterTest {
    private lateinit var database: TinoDatabase
    private lateinit var repository: CommerceRepository
    private lateinit var adapter: AndroidCreditPreparationFactsAdapter

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
        val resolver = EntityResolutionService(repository, NoopAuditLogger)
        adapter = AndroidCreditPreparationFactsAdapter(
            entityResolver = resolver,
            commerceRepository = repository,
            toolExecutor = CommerceToolDispatcher(repository, resolver),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun resolvesRealCustomerProductPriceStockAndBalanceOffline() = runBlocking {
        val customer = adapter.findCustomer("Dona Maria Lina") as CustomerLookup.Resolved
        val product = adapter.findProduct("café maratá") as ProductLookup.Resolved
        val balance = adapter.getCustomerBalance("Maria Lina") as BalanceLookup.Resolved

        assertEquals("Maria Lina", customer.name)
        assertEquals("Café Maratá", product.name)
        assertEquals(850L, product.priceCents)
        assertEquals(24, product.stockQuantity)
        assertEquals(0L, balance.balanceCents)
        assertTrue(customer.timing.customerResolutionMs >= 0)
        assertTrue(product.timing.stockMs >= 0)
        assertTrue(balance.timing.balanceMs >= 0)
    }

    @Test
    fun preparesRealPreviewWithoutMutationOrCommitPath() = runBlocking {
        val result = adapter.prepareCreditSale("Maria Lina", "Café Maratá", 1)

        assertTrue(result is CreditPreparationLookup.Preview)
        val preview = result as CreditPreparationLookup.Preview
        assertTrue(preview.detail.contains("R$ 8,50"))
        assertTrue(preview.detail.contains("Estoque depois: 23"))
        assertTrue(preview.timing.prepareMs >= 0)
        assertEquals(0L, repository.customerBalance(database.customerDao().all().single().id))
        assertEquals(24, database.stockMovementDao().balance(database.productDao().all().single().id))
        assertFalse(database.domainEventDao().all().any { it.type == "credit.sale.created" })
    }

    @Test
    fun ambiguityAndNotFoundStayExplicit() = runBlocking {
        assertTrue(adapter.findCustomer("cliente inexistente") is CustomerLookup.NotFound)
        assertTrue(adapter.findProduct("produto inexistente") is ProductLookup.NotFound)

        repository.createCustomer("Maria Lino")
        repository.createProduct("Café Marabá", 850, 24)

        val customer = adapter.findCustomer("Maria")
        val product = adapter.findProduct("Café")
        val preview = adapter.prepareCreditSale("Maria", "Café", 1)

        assertEquals(
            listOf("Maria Lina", "Maria Lino").sorted(),
            (customer as CustomerLookup.Ambiguous).options.sorted(),
        )
        assertEquals(
            listOf("Café Maratá", "Café Marabá").sorted(),
            (product as ProductLookup.Ambiguous).options.sorted(),
        )
        assertTrue(preview is CreditPreparationLookup.Ambiguous)
        assertEquals("product", (preview as CreditPreparationLookup.Ambiguous).entityType)
        assertEquals(0L, repository.customerBalance(database.customerDao().all().first().id))
        assertFalse(database.domainEventDao().all().any { it.type == "credit.sale.created" })
    }
}
