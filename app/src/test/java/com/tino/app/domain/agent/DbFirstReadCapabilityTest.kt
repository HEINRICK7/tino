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
import com.tino.app.domain.commerce.TemporalCreditService
import com.tino.app.domain.finance.FinancialProjectionRepository
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
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class DbFirstReadCapabilityTest {
    private lateinit var database: TinoDatabase
    private lateinit var repository: CommerceRepository
    private lateinit var service: DbFirstReadCapabilityService
    private val zone = ZoneId.of("America/Fortaleza")
    private val clock = Clock.fixed(Instant.parse("2026-08-17T15:00:00Z"), zone)

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
        service = DbFirstReadCapabilityService(
            commerceRepository = repository,
            entityResolver = EntityResolutionService(repository, NoopAuditLogger),
            temporalCredit = TemporalCreditService(repository),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun listProductsReturnsRealPriceAndStockFromRoom() = runBlocking {
        repository.createProduct("Café Maratá", 850, 24)

        val result = service.listProducts().value

        assertEquals(1, result.items.size)
        assertEquals("Café Maratá", result.items.single().name)
        assertEquals(850L, result.items.single().priceCents)
        assertEquals(24, result.items.single().stockQuantity)
        assertEquals(AgentDataSource.LOCAL_ONLY, result.dataSource)
    }

    @Test
    fun emptyProductsIsExplicitAndDoesNotInventItems() = runBlocking {
        val result = service.listProducts().value

        assertTrue(result.items.isEmpty())
        assertEquals("Nenhum produto cadastrado.", result.emptyMessage)
    }

    @Test
    fun listSuppliersReturnsRealContactDataFromRoom() = runBlocking {
        repository.createSupplier("Distribuidora Central", "85999990000")

        val result = service.listSuppliers().value

        assertEquals(1, result.items.size)
        assertEquals("Distribuidora Central", result.items.single().name)
        assertEquals("85999990000", result.items.single().phone)
        assertEquals(AgentDataSource.LOCAL_ONLY, result.dataSource)
    }

    @Test
    fun emptySuppliersIsExplicitAndDoesNotInventContacts() = runBlocking {
        val result = service.listSuppliers().value

        assertTrue(result.items.isEmpty())
        assertEquals("Nenhum fornecedor cadastrado.", result.emptyMessage)
    }

    @Test
    fun customerContactResolvesNameAndReturnsPhoneFromRoom() = runBlocking {
        repository.createCustomer("Maria Lina", "86994209350")

        val result = service.customerContact("maria lina")

        assertTrue(result is DbFirstReadResult.CustomerContact)
        val contact = (result as DbFirstReadResult.CustomerContact).value
        assertEquals("Maria Lina", contact.customerName)
        assertEquals("86994209350", contact.phone)
        assertEquals(AgentDataSource.LOCAL_ONLY, contact.dataSource)
    }

    @Test
    fun customerContactKeepsMissingPhoneExplicit() = runBlocking {
        repository.createCustomer("Maria Lina")

        val result = service.customerContact("Maria")

        assertTrue(result is DbFirstReadResult.CustomerContact)
        assertEquals(null, (result as DbFirstReadResult.CustomerContact).value.phone)
    }

    @Test
    fun customerContactDoesNotGuessWhenNameIsAmbiguous() = runBlocking {
        repository.createCustomer("Maria Lina")
        repository.createCustomer("Maria Luiza")

        val result = service.customerContact("Maria")

        assertTrue(result is DbFirstReadResult.Ambiguous)
        assertEquals(listOf("Maria Lina", "Maria Luiza"), (result as DbFirstReadResult.Ambiguous).options)
    }

    @Test
    fun productFactResolvesReferenceAgainstRealProduct() = runBlocking {
        repository.createProduct("Café Maratá", 850, 24)

        val result = service.productFact(AgentCapability.GET_PRODUCT_STOCK, "cafe marata")

        assertTrue(result is DbFirstReadResult.ProductFact)
        val product = (result as DbFirstReadResult.ProductFact).value.product
        assertEquals("Café Maratá", product.name)
        assertEquals(24, product.stockQuantity)
        assertEquals(850L, product.priceCents)
    }

    @Test
    fun receivablesAndOverdueUseRealCustomerLedger() = runBlocking {
        repository.createCustomer("Maria Lina")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(
            customerId = customerId,
            amountCents = 10_100,
            operationId = "credit-maria-overdue",
            dueAt = clock.millis() - 3 * DAY_MS,
        )

        val receivables = service.listReceivables().value.items
        val overdue = service.listOverdue().value.items

        assertEquals(1, receivables.size)
        assertEquals("Maria Lina", receivables.single().customerName)
        assertEquals(10_100L, receivables.single().balanceCents)
        assertEquals(1, overdue.size)
        assertEquals("Maria Lina", overdue.single().customerName)
        assertEquals(10_100L, overdue.single().balanceCents)
        assertEquals(3L, overdue.single().daysOverdue)
    }

    @Test
    fun boundaryReturnsTypedReadListWithoutModelOrMutation() = runBlocking {
        repository.createProduct("Café Maratá", 850, 24)
        val boundary = TinoAgentBoundary(
            financialSummaryTool = FinancialSummaryQueryTool(
                FinancialProjectionRepository(database.financialProjectionDao()),
                clock,
            ),
            renderer = AgentSurfaceRenderer(),
            dbFirstRead = service,
        )
        val beforeEvents = database.domainEventDao().all()

        val response = boundary.ask(
            AgentIntent(
                schemaVersion = AgentIntentSchema.VERSION,
                capability = AgentCapability.LIST_PRODUCTS,
                period = AgentIntentPeriod.TODAY,
            ),
        )

        assertTrue(response is AgentResponse.ReadListReady)
        val ready = response as AgentResponse.ReadListReady
        assertTrue(ready.result is DbFirstReadResult.Products)
        assertEquals("Café Maratá", (ready.result as DbFirstReadResult.Products).value.items.single().name)
        assertEquals(beforeEvents, database.domainEventDao().all())
    }

    companion object {
        private const val DAY_MS = 86_400_000L
    }
}
