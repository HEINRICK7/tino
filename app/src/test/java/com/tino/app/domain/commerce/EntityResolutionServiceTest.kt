package com.tino.app.domain.commerce

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.database.CreditEntryEntity
import com.tino.app.core.database.CreditEntryType
import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.core.sync.SyncScheduler
import com.tino.app.domain.voice.CommerceToolDispatcher
import com.tino.app.domain.voice.CommerceToolName
import com.tino.app.domain.voice.ToolCall
import com.tino.app.domain.voice.ToolClarificationException
import com.tino.app.domain.agent.AgentCapability
import com.tino.app.domain.agent.AgentIntent
import com.tino.app.domain.agent.AgentIntentPeriod
import com.tino.app.domain.agent.AgentResponse
import com.tino.app.domain.agent.AgentSurfaceRenderer
import com.tino.app.domain.agent.FinancialSummaryQueryTool
import com.tino.app.domain.agent.CustomerBalanceQueryTool
import com.tino.app.domain.agent.CustomerTimelineQueryResult
import com.tino.app.domain.agent.CustomerTimelineQueryTool
import com.tino.app.domain.agent.TinoAgentBoundary
import com.tino.app.domain.finance.FinancialProjectionRepository
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
import java.time.Clock

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class EntityResolutionServiceTest {
    private lateinit var database: TinoDatabase
    private lateinit var repository: CommerceRepository
    private lateinit var audit: RecordingAuditLogger
    private lateinit var resolver: EntityResolutionService

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
        audit = RecordingAuditLogger()
        resolver = EntityResolutionService(repository, audit)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun honorificAliasResolvesToThePersistedCustomer() = runBlocking {
        repository.createCustomer("Maria Lina")
        val stored = database.customerDao().all().single()

        val result = resolver.resolveCustomer("Dona Maria Lina")

        assertEquals(
            EntityResolutionMatch.Resolved(stored, EntityResolutionStrategy.ALIAS),
            result,
        )
        assertTrue(audit.types.contains(AuditEventType.ENTITY_RESOLUTION_STARTED))
        assertTrue(audit.types.contains(AuditEventType.ENTITY_RESOLUTION_ALIAS))
    }

    @Test
    fun accentInsensitiveProductResolutionReturnsTheRealPriceAndStock() = runBlocking {
        repository.createProduct("Café Maratá", priceCents = 875, initialStock = 24)
        val stored = database.productDao().all().single()

        val result = resolver.resolveProduct("cafe marata")

        val resolved = result as EntityResolutionMatch.Resolved
        assertEquals(stored.id, resolved.value.id)
        assertEquals(875L, resolved.value.priceCents)
        assertEquals(EntityResolutionStrategy.EXACT, resolved.strategy)
        assertEquals(24, repository.stockBalance(resolved.value.id))
        assertTrue(audit.types.contains(AuditEventType.ENTITY_RESOLUTION_EXACT))
    }

    @Test
    fun fuzzyReferenceResolvesLocallyWithoutChangingTheEntity() = runBlocking {
        repository.createCustomer("Maria Lina")
        val stored = database.customerDao().all().single()

        val result = resolver.resolveCustomer("Maria Lin")

        val resolved = result as EntityResolutionMatch.Resolved
        assertEquals(stored.id, resolved.value.id)
        assertEquals(EntityResolutionStrategy.FUZZY, resolved.strategy)
        assertTrue(audit.types.contains(AuditEventType.ENTITY_RESOLUTION_FUZZY))
    }

    @Test
    fun adaptivePhoneticReferenceResolvesThePersistedProduct() = runBlocking {
        repository.createProduct("Café Maratá", priceCents = 875, initialStock = 24)
        val stored = database.productDao().all().single()

        val result = resolver.resolveProduct("Maracá")

        val resolved = result as EntityResolutionMatch.Resolved
        assertEquals(stored.id, resolved.value.id)
        assertEquals(EntityResolutionStrategy.FUZZY, resolved.strategy)
        assertEquals(875L, resolved.value.priceCents)
    }

    @Test
    fun tiedFuzzyCandidatesBecomeAnExplicitAmbiguity() = runBlocking {
        repository.createCustomer("Maria Lina")
        repository.createCustomer("Maria Luiza")
        val beforeEvents = database.domainEventDao().all()

        val result = resolver.resolveCustomer("Maria")

        val ambiguous = result as EntityResolutionMatch.Ambiguous
        assertEquals(setOf("Maria Lina", "Maria Luiza"), ambiguous.values.map { it.name }.toSet())
        assertEquals(EntityResolutionStrategy.FUZZY, ambiguous.strategy)
        assertTrue(audit.types.contains(AuditEventType.ENTITY_RESOLUTION_AMBIGUOUS))
    }

    @Test
    fun missingEntityIsNotFabricated() = runBlocking {
        val result = resolver.resolveProduct("Produto que não existe")

        assertEquals(EntityResolutionMatch.NotFound, result)
        assertTrue(audit.types.contains(AuditEventType.ENTITY_RESOLUTION_NOT_FOUND))
    }

    @Test
    fun unresolvedAndAmbiguousReferencesDoNotMutateCommerceState() = runBlocking {
        repository.createCustomer("Maria Lina")
        repository.createCustomer("Maria Luiza")
        repository.createProduct("Café Maratá", priceCents = 875, initialStock = 24)
        val beforeCustomers = database.customerDao().all()
        val beforeProducts = database.productDao().all()
        val beforeEvents = database.domainEventDao().all()

        assertEquals(EntityResolutionMatch.NotFound, resolver.resolveProduct("Biscoito inexistente"))
        assertTrue(resolver.resolveCustomer("Maria") is EntityResolutionMatch.Ambiguous)

        assertEquals(beforeCustomers, database.customerDao().all())
        assertEquals(beforeProducts, database.productDao().all())
        assertEquals(beforeEvents, database.domainEventDao().all())
        assertFalse(audit.types.isEmpty())
    }

    @Test
    fun dispatcherUsesResolvedNamesAndOffersChoicesWithoutMutatingOnFailure() = runBlocking {
        repository.createCustomer("Maria Lina")
        repository.createCustomer("Maria Luiza")
        repository.createProduct("Café Maratá", priceCents = 875, initialStock = 24)
        val dispatcher = CommerceToolDispatcher(repository)
        val beforeEvents = database.domainEventDao().all()

        val preview = dispatcher.preview(
            ToolCall(
                CommerceToolName.ADD_CREDIT_ITEM,
                mapOf("customer" to "Dona Maria Lina", "product" to "cafe marata", "quantity" to "1"),
            ),
        )
        assertTrue(preview.detail.contains("Maria Lina"))
        assertTrue(preview.detail.contains("Café Maratá"))
        assertTrue(preview.detail.contains("R$ 8,75"))

        try {
            dispatcher.preview(
                ToolCall(
                    CommerceToolName.ADD_CREDIT_ITEM,
                    mapOf("customer" to "Maria", "product" to "cafe marata", "quantity" to "1"),
                ),
            )
            throw AssertionError("A escolha ambígua deveria exigir esclarecimento")
        } catch (error: ToolClarificationException) {
            assertEquals("customer", error.argumentKey)
            assertEquals(setOf("Maria Lina", "Maria Luiza"), error.options.toSet())
        }
        assertEquals(beforeEvents, database.domainEventDao().all())
    }

    @Test
    fun canonicalAgentBoundaryReachesRuntimeEntityResolutionBeforePreview() = runBlocking {
        repository.createCustomer("Maria Lina")
        repository.createProduct("Café Maratá", priceCents = 875, initialStock = 24)
        val beforeEvents = database.domainEventDao().all()
        val boundary = TinoAgentBoundary(
            financialSummaryTool = FinancialSummaryQueryTool(
                FinancialProjectionRepository(database.financialProjectionDao()),
                Clock.systemDefaultZone(),
            ),
            renderer = AgentSurfaceRenderer(),
            toolExecutor = CommerceToolDispatcher(repository),
        )

        val response = boundary.ask(
            AgentIntent(
                schemaVersion = 1,
                capability = AgentCapability.ADD_CREDIT_ITEM,
                period = AgentIntentPeriod.TODAY,
                customerRef = "Dona Maria Lina",
                productRef = "cafe marata",
                quantity = 1,
            ),
        )

        assertTrue(response is AgentResponse.ActionPreviewReady)
        val preview = response as AgentResponse.ActionPreviewReady
        assertTrue(preview.preview.detail.contains("Maria Lina"))
        assertTrue(preview.preview.detail.contains("Café Maratá"))
        assertTrue(preview.preview.detail.contains("R$ 8,75"))
        assertTrue((preview.preview.diagnostics?.customerResolutionMs ?: -1L) >= 0L)
        assertTrue((preview.preview.diagnostics?.productResolutionMs ?: -1L) >= 0L)
        assertEquals(beforeEvents, database.domainEventDao().all())
    }

    @Test
    fun canonicalCustomerBalanceUsesTemporalProjectionWithoutMutation() = runBlocking {
        repository.createCustomer("Maria Lina")
        val customer = database.customerDao().all().single()
        repository.registerCreditByAmount(customer.id, 7_000L, operationId = "credit-maria")
        val beforeCredits = database.creditDao().all()
        val beforeEvents = database.domainEventDao().all()
        val boundary = TinoAgentBoundary(
            financialSummaryTool = FinancialSummaryQueryTool(
                FinancialProjectionRepository(database.financialProjectionDao()),
                Clock.systemDefaultZone(),
            ),
            renderer = AgentSurfaceRenderer(),
            toolExecutor = CommerceToolDispatcher(repository),
            customerBalanceTool = CustomerBalanceQueryTool(
                entityResolver = resolver,
                temporalCredit = TemporalCreditService(repository),
            ),
        )

        val response = boundary.ask(
            AgentIntent(
                schemaVersion = 1,
                capability = AgentCapability.GET_CUSTOMER_BALANCE,
                period = AgentIntentPeriod.TODAY,
                customerRef = "Dona Maria Lina",
            ),
        )

        assertTrue(response is AgentResponse.CustomerBalanceReady)
        val result = (response as AgentResponse.CustomerBalanceReady).result
        assertEquals("Maria Lina", result.customerName)
        assertEquals(7_000L, result.currentBalanceCents)
        assertEquals(7_000L, result.openCents)
        assertEquals(0L, result.overdueCents)
        assertTrue((response as AgentResponse.CustomerBalanceReady).customerResolutionMs >= 0L)
        assertEquals(beforeCredits, database.creditDao().all())
        assertEquals(beforeEvents, database.domainEventDao().all())
    }

    @Test
    fun ambiguousCustomerBalanceReturnsA2uiChoiceWithoutMutation() = runBlocking {
        repository.createCustomer("Maria Lina")
        repository.createCustomer("Maria Luiza")
        val beforeEvents = database.domainEventDao().all()
        val boundary = TinoAgentBoundary(
            financialSummaryTool = FinancialSummaryQueryTool(
                FinancialProjectionRepository(database.financialProjectionDao()),
                Clock.systemDefaultZone(),
            ),
            renderer = AgentSurfaceRenderer(),
            toolExecutor = CommerceToolDispatcher(repository),
            customerBalanceTool = CustomerBalanceQueryTool(
                entityResolver = resolver,
                temporalCredit = TemporalCreditService(repository),
            ),
        )

        val response = boundary.ask(
            AgentIntent(
                schemaVersion = 1,
                capability = AgentCapability.GET_CUSTOMER_BALANCE,
                period = AgentIntentPeriod.TODAY,
                customerRef = "Maria",
            ),
        )

        assertTrue(response is AgentResponse.EntityChoiceReady)
        val choice = response as AgentResponse.EntityChoiceReady
        assertEquals(setOf("Maria Lina", "Maria Luiza"), choice.options.toSet())
        assertEquals(beforeEvents, database.domainEventDao().all())
    }

    @Test
    fun customerTimelineUsesTemporalLedgerAndDoesNotMutate() = runBlocking {
        repository.createCustomer("Maria Lina")
        val customer = database.customerDao().all().single()
        val july = 1_751_328_000_000L
        val august = 1_755_408_000_000L
        database.creditDao().insert(
            CreditEntryEntity(
                id = "credit-july",
                customerId = customer.id,
                amountCents = 12_000,
                type = CreditEntryType.SALE,
                referenceId = null,
                occurredAt = july,
            ),
        )
        database.creditDao().insert(
            CreditEntryEntity(
                id = "payment-august",
                customerId = customer.id,
                amountCents = -5_000,
                type = CreditEntryType.PAYMENT,
                referenceId = null,
                occurredAt = august,
                paymentMethod = "pix",
            ),
        )
        val beforeCredits = database.creditDao().all()
        val tool = CustomerTimelineQueryTool(
            entityResolver = resolver,
            temporalCredit = TemporalCreditService(repository),
            clock = Clock.systemDefaultZone(),
        )

        val result = tool.execute("Dona Maria Lina") as CustomerTimelineQueryResult.Ready

        assertEquals("Maria Lina", result.result.customerName)
        assertEquals(7_000L, result.result.currentBalanceCents)
        assertEquals(2, result.result.items.size)
        assertEquals("Pagou no PIX", result.result.items.first().label)
        assertEquals("-R$ 50,00", result.result.items.first().amountText)
        assertEquals("+R$ 120,00", result.result.items.last().amountText)
        assertEquals(beforeCredits, database.creditDao().all())
    }

    private class RecordingAuditLogger : AuditLogger {
        val types = mutableListOf<AuditEventType>()
        val records = mutableListOf<Map<String, String>>()

        override fun record(type: AuditEventType, metadata: Map<String, String>) {
            types += type
            records += metadata
            assertTrue(metadata.keys.all { it in SAFE_KEYS })
            assertFalse(metadata.keys.any { it in FORBIDDEN_KEYS })
        }

        companion object {
            val SAFE_KEYS = setOf("entity_type", "match_strategy", "candidate_count")
            val FORBIDDEN_KEYS = setOf("name", "reference", "customer_id", "product_id", "supplier_id")
        }
    }
}
