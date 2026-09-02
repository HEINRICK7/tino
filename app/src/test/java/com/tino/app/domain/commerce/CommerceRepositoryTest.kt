package com.tino.app.domain.commerce

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.database.CreditEntryEntity
import com.tino.app.core.database.CreditEntryType
import com.tino.app.core.database.ProductEntity
import com.tino.app.core.sync.CommerceSnapshotRepository
import com.tino.app.core.sync.RemoteEventApplier
import com.tino.app.core.sync.SyncScheduler
import com.tino.app.domain.voice.CommerceToolDispatcher
import com.tino.app.domain.voice.CommerceToolName
import com.tino.app.domain.voice.ToolCall
import com.tino.app.domain.voice.ToolClarificationException
import com.tino.app.domain.agent.AgentActivityLedger
import com.tino.app.domain.agent.AgentActivitySource
import com.tino.app.domain.agent.AgentUndoEligibility
import com.tino.app.domain.agent.AgentUndoPlanner
import com.tino.app.domain.agent.AgentUndoPolicy
import com.tino.app.domain.agent.AgentUndoService
import com.tino.app.domain.agent.AgentUndoState
import com.tino.app.domain.agent.TinoCapabilityId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CommerceRepositoryTest {
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
    fun offlineCreditSalePaymentAndSnapshotRecoveryKeepStateConsistent() = runBlocking {
        repository.createProduct("Café Maratá", 800, 20)
        val productId = database.productDao().all().single().id
        repository.createCustomer("João")
        val customerId = database.customerDao().all().single().id

        repository.registerCreditSale(customerId, productId, 2)
        repository.registerCreditPayment(customerId, 800, PaymentMethod.CASH)

        assertEquals(18, database.stockMovementDao().balance(productId))
        assertEquals(800L, database.creditDao().balance(customerId))
        assertEquals(6, database.domainEventDao().all().size)

        val snapshot = CommerceSnapshotRepository(database).export()
        val restored = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TinoDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            CommerceSnapshotRepository(restored).restore(snapshot)
            assertEquals(18, restored.stockMovementDao().balance(productId))
            assertEquals(800L, restored.creditDao().balance(customerId))
            assertEquals(6, restored.domainEventDao().all().size)
            assertTrue(restored.productDao().all().any { it.name == "Café Maratá" })
        } finally {
            restored.close()
        }
    }

    @Test
    fun stockReceiptSupplierAndCashSaleUseMovementsAndEvents() = runBlocking {
        repository.createProduct("Leite", 500, 0)
        val productId = database.productDao().all().single().id
        repository.createSupplier("Distribuidora Nordeste")
        val supplierId = database.supplierDao().all().single().id

        repository.registerStockReceipt(productId, 24, 350, supplierId)
        repository.registerSale(productId, 3)

        assertEquals(21, database.stockMovementDao().balance(productId))
        assertEquals(1, database.purchaseDao().all().size)
        assertEquals(5, database.domainEventDao().all().size)
    }

    @Test
    fun madeToOrderSaleDoesNotRequireOrChangeStock() = runBlocking {
        database.productDao().insert(ProductEntity("made", "Bolo", 5_000, "un", 1, null, false))

        repository.registerSale("made", 3)

        assertEquals(0, database.stockMovementDao().balance("made"))
        assertEquals(1, database.saleDao().all().size)
    }

    @Test
    fun madeToOrderProductCannotReceiveStock() = runBlocking {
        database.productDao().insert(ProductEntity("made", "Bolo", 5_000, "un", 1, null, false))

        val failure = runCatching { repository.registerStockReceipt("made", 1, 3_000) }.exceptionOrNull()

        assertEquals("Produto sob demanda não possui estoque.", failure?.message)
        assertEquals(0, database.purchaseDao().all().size)
    }

    @Test
    fun customerProfileUpdatePersistsAndCreatesSyncEvent() = runBlocking {
        repository.createCustomer("Maria", "85999990000")
        val customer = database.customerDao().all().single()

        repository.updateCustomer(customer.id, "Maria Lina", "85888880000")

        val updated = database.customerDao().findById(customer.id)
        assertEquals("Maria Lina", updated?.name)
        assertEquals("85888880000", updated?.phone)
        assertEquals("customer.updated", database.domainEventDao().all().last().type)
    }

    @Test
    fun saleKeepsPixAndCardDistinctFromCash() = runBlocking {
        repository.createProduct("Café", 850, 0)
        val productId = database.productDao().all().single().id
        repository.registerStockReceipt(productId, 10, 500, null)

        repository.registerSale(productId, 1, PaymentMethod.PIX)
        repository.registerSale(productId, 1, PaymentMethod.CARD)

        assertEquals(setOf("pix", "card"), database.saleDao().all().map { it.paymentMethod }.toSet())
        val saleEvents = database.domainEventDao().all().filter { it.type == "sale.created" }
        assertTrue(saleEvents.any { it.payloadJson.contains("\"payment_method\":\"pix\"") })
        assertTrue(saleEvents.any { it.payloadJson.contains("\"payment_method\":\"card\"") })
    }

    @Test
    fun directReceiptPersistsWithoutProductSaleOrStockMovement() = runBlocking {
        repository.registerDirectReceipt(
            amountCents = 18_700,
            paymentMethod = PaymentMethod.CARD,
            operationId = "operation-receipt-187",
        )

        val receipts = database.directReceiptDao().all()
        assertEquals(1, receipts.size)
        assertEquals(18_700L, receipts.single().amountCents)
        assertEquals("card", receipts.single().paymentMethod)
        assertEquals(18_700L, repository.todayDirectReceiptTotalCents())
        assertTrue(database.productDao().all().isEmpty())
        assertTrue(database.saleDao().all().isEmpty())
        assertTrue(database.stockMovementDao().all().isEmpty())

        val event = database.domainEventDao().all().single()
        assertEquals("direct.receipt.created", event.type)
        assertTrue(event.payloadJson.contains("\"amount_cents\":18700"))
        assertTrue(event.payloadJson.contains("\"payment_method\":\"card\""))
    }

    @Test
    fun directReceiptRetryWithSameOperationIdDoesNotDuplicate() = runBlocking {
        repository.registerDirectReceipt(18_700, PaymentMethod.CARD, operationId = "operation-receipt-187")

        repository.registerDirectReceipt(18_700, PaymentMethod.CARD, operationId = "operation-receipt-187")

        assertEquals(1, database.directReceiptDao().all().size)
        assertEquals(1, database.domainEventDao().all().size)
    }

    @Test
    fun manualOrderPersistsItemsAndEventWithoutMutatingStock() = runBlocking {
        repository.createProduct("Café Maratá", 850, 4)
        val product = database.productDao().all().single()

        repository.createManualOrder(
            productId = product.id,
            quantity = 2,
            customerName = " Maria Lina ",
            fulfillment = "DELIVERY",
        )

        val order = database.orderDao().observeAll().first().single()
        assertEquals("MANUAL", order.channel)
        assertEquals("DELIVERY", order.fulfillment)
        assertEquals("Maria Lina", order.customerName)
        assertEquals("CONFIRMED", order.status)
        assertEquals(1_700L, order.totalCents)
        assertEquals(4, database.stockMovementDao().balance(product.id))

        val item = database.orderDao().items(order.id).single()
        assertEquals(product.id, item.productId)
        assertEquals(2, item.quantity)
        assertEquals(850L, item.unitPriceCents)
        assertTrue(database.domainEventDao().all().any { event ->
            event.type == "order.created" && event.aggregateId == order.id &&
                event.payloadJson.contains("\"total_cents\":1700")
        })
    }

    @Test
    fun amountOnlyCreditIncreasesReceivableWithoutReceivedOrProductSale() = runBlocking {
        repository.createCustomer("João Ferreira")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 3_100, operationId = "credit-opening-31")
        val receivedBefore = repository.observeTodayReceived().first()
        val directReceiptBefore = repository.todayDirectReceiptTotalCents()

        repository.registerCreditByAmount(customerId, 7_000, operationId = "credit-joao-70")

        assertEquals(10_100L, database.creditDao().balance(customerId))
        assertEquals(10_100L, repository.totalReceivableCents())
        assertEquals(receivedBefore, repository.observeTodayReceived().first())
        assertEquals(directReceiptBefore, repository.todayDirectReceiptTotalCents())
        assertTrue(database.productDao().all().isEmpty())
        assertTrue(database.saleDao().all().isEmpty())
        assertTrue(database.stockMovementDao().all().isEmpty())
        assertEquals(2, database.creditDao().all().size)

        val snapshot = CommerceSnapshotRepository(database).export()
        val restored = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TinoDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            CommerceSnapshotRepository(restored).restore(snapshot)
            assertEquals(10_100L, restored.creditDao().balance(customerId))
            assertEquals(2, restored.creditDao().all().size)
            assertTrue(restored.saleDao().all().isEmpty())
        } finally {
            restored.close()
        }
    }

    @Test
    fun amountOnlyCreditRetryWithSameOperationIdDoesNotDuplicate() = runBlocking {
        repository.createCustomer("João Ferreira")
        val customerId = database.customerDao().all().single().id

        repository.registerCreditByAmount(customerId, 7_000, operationId = "credit-joao-70")
        repository.registerCreditByAmount(customerId, 7_000, operationId = "credit-joao-70")

        assertEquals(7_000L, database.creditDao().balance(customerId))
        assertEquals(1, database.creditDao().all().size)
        assertEquals(2, database.domainEventDao().all().size)
    }

    @Test
    fun creditPaymentUpdatesDebtAndPixProjectionAsOneEconomicOperation() = runBlocking {
        repository.createCustomer("João Ferreira")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 10_100, operationId = "credit-joao-101")
        val receivedBefore = repository.observeTodayReceived().first()

        repository.registerCreditPayment(
            customerId = customerId,
            amountCents = 5_000,
            paymentMethod = PaymentMethod.PIX,
            operationId = "payment-joao-50-pix",
        )

        assertEquals(5_100L, database.creditDao().balance(customerId))
        assertEquals(5_000L, repository.todayCreditPaymentReceivedCents(PaymentMethod.PIX))
        assertEquals(0L, repository.todayCreditPaymentReceivedCents(PaymentMethod.CASH))
        assertEquals(receivedBefore, repository.observeTodayReceived().first())
        assertTrue(database.directReceiptDao().all().isEmpty())
        assertTrue(database.saleDao().all().isEmpty())
        assertTrue(database.productDao().all().isEmpty())
        assertTrue(database.stockMovementDao().all().isEmpty())

        val payment = database.creditDao().all().single { it.type == CreditEntryType.PAYMENT }
        assertEquals(-5_000L, payment.amountCents)
        assertEquals("pix", payment.paymentMethod)
        val event = database.domainEventDao().all().single { it.type == "credit.payment.received" }
        assertTrue(event.payloadJson.contains("\"payment_method\":\"pix\""))

        val snapshot = CommerceSnapshotRepository(database).export()
        val restored = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TinoDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            CommerceSnapshotRepository(restored).restore(snapshot)
            assertEquals(5_100L, restored.creditDao().balance(customerId))
            assertEquals("pix", restored.creditDao().all().single { it.type == CreditEntryType.PAYMENT }.paymentMethod)
            assertTrue(restored.directReceiptDao().all().isEmpty())
        } finally {
            restored.close()
        }
    }

    @Test
    fun creditPaymentRetryWithSameOperationIdDoesNotDuplicateProjection() = runBlocking {
        repository.createCustomer("João Ferreira")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 10_100, operationId = "credit-joao-101")

        repository.registerCreditPayment(customerId, 5_000, PaymentMethod.PIX, "payment-joao-50-pix")
        repository.registerCreditPayment(customerId, 5_000, PaymentMethod.PIX, "payment-joao-50-pix")

        assertEquals(5_100L, database.creditDao().balance(customerId))
        assertEquals(5_000L, repository.todayCreditPaymentReceivedCents(PaymentMethod.PIX))
        assertEquals(2, database.creditDao().all().size)
        assertEquals(3, database.domainEventDao().all().size)
    }

    @Test
    fun legacyPaymentWithoutMethodStaysUnknownAndNeverBecomesCash() = runBlocking {
        repository.createCustomer("João Ferreira")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 20_000, operationId = "credit-joao-200")
        database.creditDao().insert(
            CreditEntryEntity(
                id = "legacy-payment-100",
                customerId = customerId,
                amountCents = -10_000,
                type = CreditEntryType.PAYMENT,
                referenceId = null,
                occurredAt = System.currentTimeMillis(),
            ),
        )

        repository.registerCreditPayment(customerId, 5_000, PaymentMethod.PIX, "payment-joao-50-pix")

        assertEquals(5_000L, database.creditDao().balance(customerId))
        assertEquals(15_000L, repository.todayCreditPaymentTotalCents())
        assertEquals(5_000L, repository.todayCreditPaymentReceivedCents(PaymentMethod.PIX))
        assertEquals(10_000L, repository.todayCreditPaymentReceivedCents(PaymentMethod.UNKNOWN))
        assertEquals(0L, repository.todayCreditPaymentReceivedCents(PaymentMethod.CASH))
        assertEquals("unknown", database.creditDao().findById("legacy-payment-100")?.paymentMethod)

        val oldSnapshot = CommerceSnapshotRepository(database).export().copy(schemaVersion = 1)
        val restored = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TinoDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            CommerceSnapshotRepository(restored).restore(oldSnapshot)
            assertEquals(5_000L, restored.creditDao().balance(customerId))
            assertEquals("unknown", restored.creditDao().findById("legacy-payment-100")?.paymentMethod)
        } finally {
            restored.close()
        }
    }

    @Test
    fun newCreditPaymentWithoutValidMethodIsRejected() = runBlocking {
        repository.createCustomer("João Ferreira")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 10_000, operationId = "credit-joao-100")

        var rejected = false
        try {
            repository.registerCreditPayment(customerId, 5_000)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
        assertEquals(10_000L, database.creditDao().balance(customerId))
        assertTrue(database.creditDao().all().none { it.type == CreditEntryType.PAYMENT })
    }

    @Test
    fun creditDueDatePersistsInLedgerEventAndSnapshot() = runBlocking {
        repository.createCustomer("Maria Lina")
        val customerId = database.customerDao().all().single().id
        val dueAt = System.currentTimeMillis() + 5 * 86_400_000L

        repository.registerCreditByAmount(
            customerId = customerId,
            amountCents = 10_100,
            operationId = "credit-maria-101",
            dueAt = dueAt,
        )

        assertEquals(dueAt, database.creditDao().all().single().dueAt)
        assertTrue(
            database.domainEventDao().all()
                .single { it.type == "credit.receivable.created" }
                .payloadJson.contains("\"due_at\":$dueAt"),
        )

        val snapshot = CommerceSnapshotRepository(database).export()
        val restored = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TinoDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            CommerceSnapshotRepository(restored).restore(snapshot)
            assertEquals(dueAt, restored.creditDao().all().single().dueAt)
        } finally {
            restored.close()
        }
    }

    @Test
    fun changingProductPriceUsesCurrentValueAndCreatesEvent() = runBlocking {
        repository.createProduct("Café Maratá", 850, 0)
        val productId = database.productDao().all().single().id

        repository.changeProductPrice(productId, 875)

        assertEquals(875L, database.productDao().findById(productId)?.priceCents)
        val priceEvent = database.domainEventDao().all().single { it.type == "product.price.changed" }
        assertTrue(priceEvent.payloadJson.contains("\"previous_price_cents\":850"))
        assertTrue(priceEvent.payloadJson.contains("\"new_price_cents\":875"))
    }

    @Test
    fun priceUpdateDispatcherExecutesTheCanonicalUseCaseAfterConfirmation() = runBlocking {
        repository.createProduct("Café Maratá", 850, 0)
        val productId = database.productDao().all().single().id
        val dispatcher = CommerceToolDispatcher(repository)
        val call = ToolCall(
            name = CommerceToolName.CHANGE_PRODUCT_PRICE,
            arguments = mapOf("product" to "Café Maratá", "new_price_cents" to "875"),
        )

        val preview = dispatcher.preview(call)

        assertTrue(preview.detail.contains("R$ 8,50"))
        assertTrue(preview.detail.contains("R$ 8,75"))
        assertEquals(850L, database.productDao().findById(productId)?.priceCents)

        dispatcher.execute(call, confirmed = true)

        assertEquals(875L, database.productDao().findById(productId)?.priceCents)
        assertTrue(database.domainEventDao().all().any { it.type == "product.price.changed" })
    }

    @Test
    fun creditCommandPreviewShowsConsequenceBeforeMutation() = runBlocking {
        repository.createProduct("Café Maratá", 800, 10)
        val productId = database.productDao().all().single().id
        repository.createCustomer("João")
        val customerId = database.customerDao().all().single().id
        val dispatcher = CommerceToolDispatcher(repository)
        val call = ToolCall(
            name = CommerceToolName.REGISTER_CREDIT_SALE,
            arguments = mapOf("customer" to "João", "product" to "Café Maratá", "quantity" to "2"),
        )

        val preview = dispatcher.preview(call)

        assertTrue(preview.detail.contains("Fiado atual: R$ 0,00"))
        assertTrue(preview.detail.contains("Depois: R$ 16,00"))
        assertEquals(10, database.stockMovementDao().balance(productId))
        assertEquals(0L, database.creditDao().balance(customerId))

        var rejected = false
        try {
            dispatcher.execute(call, confirmed = false)
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue(rejected)
        assertEquals(10, database.stockMovementDao().balance(productId))

        dispatcher.execute(call, confirmed = true)
        assertEquals(8, database.stockMovementDao().balance(productId))
        assertEquals(1600L, database.creditDao().balance(customerId))
    }

    @Test
    fun customerCreationDispatcherPersistsOnlyAfterConfirmedExecution() = runBlocking {
        val dispatcher = CommerceToolDispatcher(repository)
        val call = ToolCall(
            name = CommerceToolName.CREATE_CUSTOMER,
            arguments = mapOf("name" to "Maria Lina", "phone" to "86999990000"),
        )

        val preview = dispatcher.preview(call)

        assertEquals("Cadastrar cliente?", preview.title)
        assertTrue(preview.detail.contains("Maria Lina"))
        assertTrue(preview.detail.contains("Telefone: 86999990000"))
        assertTrue(database.customerDao().all().isEmpty())

        dispatcher.execute(call, confirmed = true)

        val created = database.customerDao().all().single()
        assertEquals("Maria Lina", created.name)
        assertEquals("86999990000", created.phone)
        assertTrue(database.domainEventDao().all().any { it.type == "customer.created" })
    }

    @Test
    fun saleContinuationDefaultsToOneUnitWhenProductPickerProvidedOnlyProduct() = runBlocking {
        repository.createProduct("Café Maratá", 800, 5)
        val productId = database.productDao().all().single().id
        val dispatcher = CommerceToolDispatcher(repository)
        val call = ToolCall(
            name = CommerceToolName.REGISTER_SALE,
            arguments = mapOf("product" to "Café Maratá"),
        )

        val preview = dispatcher.preview(call)

        assertTrue(preview.detail.contains("1 × Café Maratá"))
        dispatcher.execute(call, confirmed = true)
        assertEquals(4, database.stockMovementDao().balance(productId))
        assertEquals(800L, database.saleDao().all().single().totalCents)
    }

    @Test
    fun stockReceiptPreviewShowsBeforeAfterAndKeepsConfirmationBoundary() = runBlocking {
        repository.createProduct("Café Maratá", 800, 8)
        val productId = database.productDao().all().single().id
        repository.createSupplier("Distribuidora Nordeste")
        val dispatcher = CommerceToolDispatcher(repository)
        val call = ToolCall(
            name = CommerceToolName.REGISTER_STOCK_RECEIPT,
            arguments = mapOf(
                "product" to "Café Maratá",
                "quantity" to "24",
                "unit_cost_cents" to "500",
                "supplier" to "Distribuidora Nordeste",
            ),
        )

        val preview = dispatcher.preview(call)

        assertTrue(preview.detail.contains("Estoque atual: 8"))
        assertTrue(preview.detail.contains("Depois: 32"))
        assertTrue(preview.detail.contains("Fornecedor: Distribuidora Nordeste"))
        assertEquals(8, database.stockMovementDao().balance(productId))

        dispatcher.execute(call, confirmed = true)

        assertEquals(32, database.stockMovementDao().balance(productId))
        assertEquals(1, database.purchaseDao().all().size)
    }

    @Test
    fun partialEntityReferenceBecomesClarificationInsteadOfFirstMatch() = runBlocking {
        repository.createProduct("Café Maratá", 800, 10)
        repository.createProduct("Café Pilão", 900, 10)
        val dispatcher = CommerceToolDispatcher(repository)

        val resolution = repository.resolveProductByName("Café")
        assertTrue(resolution is EntityResolution.Ambiguous)

        var clarified = false
        try {
            dispatcher.preview(
                ToolCall(
                    CommerceToolName.CHECK_STOCK,
                    mapOf("product" to "Café"),
                ),
            )
        } catch (error: ToolClarificationException) {
            clarified = error.message?.contains("Café Maratá") == true &&
                error.message?.contains("Café Pilão") == true
        }
        assertTrue(clarified)
    }

    @Test
    fun creditPaymentUndoAppendsReversalAndIsIdempotent() = runBlocking {
        repository.createCustomer("Maria")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 10_000, operationId = "opening-maria")
        repository.registerCreditPayment(customerId, 5_000, PaymentMethod.PIX, "payment-maria")

        val reversalId = repository.reverseCreditPayment("payment-maria", "reversal-maria")
        val retryId = repository.reverseCreditPayment("payment-maria", "another-reversal-id")

        assertEquals("reversal-maria", reversalId)
        assertEquals(reversalId, retryId)
        assertEquals(10_000L, database.creditDao().balance(customerId))
        assertEquals(3, database.creditDao().all().size)
        assertEquals(1, database.domainEventDao().all().count { it.type == "credit.payment.reversed" })
    }

    @Test
    fun correctingCreditPaymentPreservesOriginalAndProducesEquivalentCorrectedState() = runBlocking {
        repository.createCustomer("Maria")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 10_000, operationId = "opening-correction")
        repository.registerCreditPayment(customerId, 5_000, PaymentMethod.PIX, "payment-original")

        val corrected = repository.correctCreditPayment(
            originalPaymentOperationId = "payment-original",
            amountCents = 4_000,
            paymentMethod = PaymentMethod.CASH,
            operationId = "payment-corrected",
            reversalOperationId = "reversal-correction",
        )

        assertEquals("payment-corrected", corrected.correctedPaymentOperationId)
        assertEquals(6_000L, database.creditDao().balance(customerId))
        assertEquals(4, database.creditDao().all().size)
        assertEquals(-5_000L, database.creditDao().findById("payment-original")?.amountCents)
        assertEquals(-4_000L, database.creditDao().findById("payment-corrected")?.amountCents)
        assertEquals(1, database.domainEventDao().all().count { it.type == "credit.payment.reversed" })
    }

    @Test
    fun creditAdjustmentAppendsSignedEventWithoutChangingOriginalEntries() = runBlocking {
        repository.createCustomer("Maria")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 10_000, operationId = "opening-adjustment")

        val provenance = LedgerProvenance(
            source = LedgerSourceType.VOICE,
            actor = LedgerActorType.AGENT,
            transcript = "desconta cinco reais da conta da Maria",
            agentExecutionId = "agent-adjustment-1",
            createdAtEpochMs = 123L,
        )
        repository.registerCreditAdjustment(
            customerId = customerId,
            amountCents = -500,
            reason = "Desconto autorizado pelo comerciante",
            operationId = "adjustment-maria",
            provenance = provenance,
        )
        repository.registerCreditAdjustment(
            customerId = customerId,
            amountCents = -500,
            reason = "Desconto autorizado pelo comerciante",
            operationId = "adjustment-maria",
            provenance = provenance,
        )

        val adjustment = database.creditDao().findById("adjustment-maria")
        assertEquals(-500L, adjustment?.amountCents)
        assertEquals(SharedLedgerEventType.ADJUSTMENT.name, adjustment?.ledgerType)
        assertEquals("Desconto autorizado pelo comerciante", adjustment?.reason)
        assertEquals(provenance, adjustment?.provenance?.let(LedgerProvenanceCodec::decode))
        assertEquals(9_500L, database.creditDao().balance(customerId))
        assertEquals(10_000L, database.creditDao().findById("opening-adjustment")?.amountCents)
        assertEquals(1, database.domainEventDao().all().count { it.type == "credit.adjustment.created" })
    }

    @Test
    fun creditAdjustmentCannotMakeBalanceNegative() = runBlocking {
        repository.createCustomer("Maria")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 1_000, operationId = "opening-adjustment-limit")

        var rejected = false
        try {
            repository.registerCreditAdjustment(
                customerId = customerId,
                amountCents = -1_001,
                reason = "Ajuste inválido",
                operationId = "adjustment-invalid",
            )
        } catch (error: IllegalStateException) {
            rejected = error.message?.contains("saldo negativo") == true
        }

        assertTrue(rejected)
        assertEquals(1_000L, database.creditDao().balance(customerId))
        assertTrue(database.creditDao().findById("adjustment-invalid") == null)
    }

    @Test
    fun disputeAppendsZeroValueEventAndDoesNotChangeBalance() = runBlocking {
        repository.createCustomer("Maria")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 10_000, operationId = "opening-dispute")

        val disputeId = repository.disputeCreditEntry(
            customerId = customerId,
            referencedEntryId = "opening-dispute",
            reason = "Cliente contesta o lançamento",
            operationId = "dispute-maria",
        )
        val retryId = repository.disputeCreditEntry(
            customerId = customerId,
            referencedEntryId = "opening-dispute",
            reason = "Cliente contesta o lançamento",
            operationId = "dispute-maria",
        )

        val dispute = database.creditDao().findById(disputeId)
        assertEquals("dispute-maria", retryId)
        assertEquals(0L, dispute?.amountCents)
        assertEquals(SharedLedgerEventType.DISPUTE.name, dispute?.ledgerType)
        assertEquals("opening-dispute", dispute?.referenceId)
        assertEquals(10_000L, database.creditDao().balance(customerId))
        assertEquals(1, database.domainEventDao().all().count { it.type == "credit.entry.disputed" })
        assertTrue(
            SharedLedgerProjector.project(
                customerId,
                database.creditDao().all().map(SharedLedgerProjector::fromCreditEntry),
            ).disputedEventIds.contains("opening-dispute"),
        )
    }

    @Test
    fun remoteLedgerEventsPreserveSemanticTypeReasonAndProvenance() = runBlocking {
        repository.createCustomer("Maria")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 10_000, operationId = "opening-remote-ledger")
        val provenance = LedgerProvenance(
            source = LedgerSourceType.VOICE,
            actor = LedgerActorType.AGENT,
            transcript = "ajusta a conta da Maria",
            agentExecutionId = "agent-remote-ledger-1",
            createdAtEpochMs = 456L,
        )
        repository.registerCreditAdjustment(
            customerId = customerId,
            amountCents = -500,
            reason = "Pagamento confirmado fora do caixa",
            operationId = "adjustment-remote-ledger",
            provenance = provenance,
        )
        repository.disputeCreditEntry(
            customerId = customerId,
            referencedEntryId = "opening-remote-ledger",
            reason = "Contestação enviada pelo cliente",
            operationId = "dispute-remote-ledger",
            provenance = provenance,
        )

        val remote = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TinoDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val applier = RemoteEventApplier(remote)
            database.domainEventDao().all()
                .filter { it.type == "credit.adjustment.created" || it.type == "credit.entry.disputed" }
                .forEach { applier.applyIfNew(it) }

            val adjustment = remote.creditDao().findById("adjustment-remote-ledger")
            val dispute = remote.creditDao().findById("dispute-remote-ledger")
            assertEquals(SharedLedgerEventType.ADJUSTMENT.name, adjustment?.ledgerType)
            assertEquals("Pagamento confirmado fora do caixa", adjustment?.reason)
            assertEquals(provenance, adjustment?.provenance?.let(LedgerProvenanceCodec::decode))
            assertEquals(SharedLedgerEventType.DISPUTE.name, dispute?.ledgerType)
            assertEquals("opening-remote-ledger", dispute?.referenceId)
            assertEquals(0L, dispute?.amountCents)
        } finally {
            remote.close()
        }
    }

    @Test
    fun settlementClosesBalanceWithoutDeletingLedgerHistory() = runBlocking {
        repository.createCustomer("Maria")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 10_000, operationId = "opening-settlement")
        repository.registerCreditPayment(customerId, 2_500, PaymentMethod.PIX, "payment-settlement")

        val settlementId = repository.settleCredit(
            customerId = customerId,
            reason = "Quitação acordada com a cliente",
            operationId = "settlement-maria",
        )
        val retryId = repository.settleCredit(
            customerId = customerId,
            reason = "Quitação acordada com a cliente",
            operationId = "settlement-maria",
        )

        val settlement = database.creditDao().findById(settlementId)
        assertEquals("settlement-maria", retryId)
        assertEquals(-7_500L, settlement?.amountCents)
        assertEquals(SharedLedgerEventType.SETTLEMENT.name, settlement?.ledgerType)
        assertEquals(0L, database.creditDao().balance(customerId))
        assertEquals(3, database.creditDao().all().size)
        assertEquals(1, database.domainEventDao().all().count { it.type == "credit.settled" })

        val timeline = TemporalCreditService.buildTimeline(
            customerId = customerId,
            customerName = "Maria",
            entries = database.creditDao().all(),
            now = System.currentTimeMillis(),
            zone = java.time.ZoneId.systemDefault(),
        )
        assertEquals(0L, timeline.currentBalanceCents)
        assertEquals(0L, timeline.openCents)
        assertEquals(1, timeline.payments.size)
        assertEquals(3, timeline.ledgerEvents.size)
        assertTrue(timeline.ledgerEvents.any { it.type == SharedLedgerEventType.PAYMENT })
        assertTrue(timeline.ledgerEvents.any { it.type == SharedLedgerEventType.SETTLEMENT })
    }

    @Test
    fun remoteSettlementPreservesClosedBalanceAndSemanticMetadata() = runBlocking {
        repository.createCustomer("Maria")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 4_000, operationId = "opening-remote-settlement")
        val provenance = LedgerProvenance(
            source = LedgerSourceType.MANUAL_UI,
            actor = LedgerActorType.MERCHANT,
            createdAtEpochMs = 789L,
        )
        repository.settleCredit(
            customerId = customerId,
            reason = "Quitação registrada no caixa",
            operationId = "settlement-remote",
            provenance = provenance,
        )
        val events = database.domainEventDao().all()
        val settlementEvent = events.single { it.type == "credit.settled" }
        val openingEvent = events.single { it.type == "credit.receivable.created" }

        val remote = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TinoDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val applier = RemoteEventApplier(remote)
            applier.applyIfNew(openingEvent)
            applier.applyIfNew(settlementEvent)

            val settlement = remote.creditDao().findById("settlement-remote")
            assertEquals(-4_000L, settlement?.amountCents)
            assertEquals(SharedLedgerEventType.SETTLEMENT.name, settlement?.ledgerType)
            assertEquals("Quitação registrada no caixa", settlement?.reason)
            assertEquals(provenance, settlement?.provenance?.let(LedgerProvenanceCodec::decode))
            assertEquals(0L, remote.creditDao().balance(customerId))
        } finally {
            remote.close()
        }
    }

    @Test
    fun sharedLedgerReadApiProjectsEventsPerCustomerWithoutCrossingBoundaries() = runBlocking {
        repository.createCustomer("Maria")
        repository.createCustomer("João")
        val customers = database.customerDao().all().associateBy { it.name }
        val mariaId = customers.getValue("Maria").id
        val joaoId = customers.getValue("João").id
        repository.registerCreditByAmount(mariaId, 10_000, operationId = "ledger-maria")
        repository.registerCreditPayment(mariaId, 2_000, PaymentMethod.PIX, "ledger-maria-payment")
        repository.registerCreditByAmount(joaoId, 3_000, operationId = "ledger-joao")

        val maria = repository.sharedLedgerProjection(mariaId)
        val statement = repository.sharedLedgerStatement(mariaId)
        val all = repository.allSharedLedgerProjections().associateBy { it.customerId }

        assertEquals(8_000L, maria.balanceCents)
        assertEquals(listOf("ledger-maria", "ledger-maria-payment"), maria.events.map { it.id })
        assertEquals("Maria", statement.customerName)
        assertEquals(8_000L, statement.balanceCents)
        assertEquals(maria.events.map { it.id }, statement.entries.map { it.id })
        assertEquals(8_000L, all.getValue(mariaId).balanceCents)
        assertEquals(3_000L, all.getValue(joaoId).balanceCents)
        assertEquals(emptySet<String>(), all.getValue(joaoId).disputedEventIds)
    }

    @Test
    fun remoteCreditPaymentReversalIsAppliedIdempotently() = runBlocking {
        repository.createCustomer("Maria")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 10_000, operationId = "opening-remote")
        repository.registerCreditPayment(customerId, 5_000, PaymentMethod.PIX, "payment-remote")
        repository.reverseCreditPayment("payment-remote", "reversal-remote")
        val reversalEvent = database.domainEventDao().all().single { it.type == "credit.payment.reversed" }

        val remote = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TinoDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val applier = RemoteEventApplier(remote)
            applier.applyIfNew(reversalEvent)
            applier.applyIfNew(reversalEvent)

            assertEquals(1, remote.creditDao().all().size)
            assertEquals(5_000L, remote.creditDao().all().single().amountCents)
            assertEquals("payment-remote", remote.creditDao().all().single().referenceId)
        } finally {
            remote.close()
        }
    }

    @Test
    fun undoServiceCreatesCompensationActivityAndDoesNotDoubleCompensate() = runBlocking {
        repository.createCustomer("Maria")
        val customerId = database.customerDao().all().single().id
        repository.registerCreditByAmount(customerId, 10_000, operationId = "opening-service")
        repository.registerCreditPayment(customerId, 5_000, PaymentMethod.PIX, "payment-service")
        val ledger = AgentActivityLedger()
        val entry = ledger.record(
            capability = TinoCapabilityId.RECEIVE_CREDIT_PAYMENT,
            summary = "Pagamento recebido",
            source = AgentActivitySource.VOICE,
            operationId = "payment-service",
            undo = AgentUndoEligibility(
                policy = AgentUndoPolicy.COMPENSATING_OPERATION,
                compensatingCapability = TinoCapabilityId.REVERSE_CREDIT_PAYMENT,
            ),
        )
        val service = AgentUndoService(AgentUndoPlanner(ledger), repository, ledger)

        val first = service.undo(entry.id)
        val second = service.undo(entry.id)

        assertEquals(first.compensationOperationId, second.compensationOperationId)
        assertEquals(10_000L, database.creditDao().balance(customerId))
        assertEquals(AgentUndoState.COMPLETED, ledger.entries.value.first { it.id == entry.id }.undoState)
        assertEquals(2, ledger.entries.value.size)
        assertEquals(1, database.creditDao().all().count { it.referenceId == "payment-service" })
    }
}
