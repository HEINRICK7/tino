package com.tino.app.domain.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.database.CreditEntryEntity
import com.tino.app.core.database.CreditEntryType
import com.tino.app.core.database.DirectReceiptEntity
import com.tino.app.core.database.SaleEntity
import com.tino.app.core.database.TinoDatabase
import com.tino.app.domain.finance.FinancialProjectionRepository
import com.tino.app.domain.voice.CommerceToolName
import com.tino.app.domain.voice.ToolCall
import com.tino.app.domain.voice.ToolExecutionResult
import com.tino.app.domain.voice.ToolExecutor
import com.tino.app.domain.voice.ToolPreview
import com.tino.app.interfaceadapter.a2ui.EntityChoiceA2uiMapper
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
class AgenticQueryTest {
    private lateinit var database: TinoDatabase
    private lateinit var boundary: TinoAgentBoundary
    private val zone = ZoneId.of("America/Fortaleza")
    private val clock = Clock.fixed(Instant.parse("2026-08-17T15:00:00Z"), zone)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val projection = FinancialProjectionRepository(database.financialProjectionDao())
        boundary = TinoAgentBoundary(
            financialSummaryTool = FinancialSummaryQueryTool(projection, clock),
            renderer = AgentSurfaceRenderer(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun todayQuestionUsesFinancialProjectionAndReturnsStructuredSurface() = runBlocking {
        val today = Instant.now(clock).toEpochMilli()
        val yesterday = today - 24 * 60 * 60 * 1_000L
        database.saleDao().insert(SaleEntity("sale-today", 10_000, "cash", today))
        database.directReceiptDao().insert(DirectReceiptEntity("receipt-today", 5_000, "pix", today, "manual", null, "receipt-today"))
        database.creditDao().insert(CreditEntryEntity("payment-today", "joao", -1_000, CreditEntryType.PAYMENT, null, today, "unknown"))
        database.directReceiptDao().insert(DirectReceiptEntity("receipt-yesterday", 99_999, "pix", yesterday, "manual", null, "receipt-yesterday"))

        val beforeSales = database.saleDao().all()
        val beforeReceipts = database.directReceiptDao().all()
        val beforeCredits = database.creditDao().all()
        val beforeEvents = database.domainEventDao().all()

        val response = boundary.ask(AgentRequest("Quanto entrou hoje?"))

        assertTrue(response is AgentResponse.SurfaceReady)
        val ready = response as AgentResponse.SurfaceReady
        assertEquals(AgentCapability.READ_FINANCIAL_SUMMARY, ready.capability)
        assertEquals(AgentDataSource.LOCAL_ONLY, ready.dataSource)
        assertEquals(16_000L, ready.result.receivedTotalCents)
        assertEquals(10_000L, ready.result.receivedCashCents)
        assertEquals(5_000L, ready.result.receivedPixCents)
        assertEquals(1_000L, ready.result.receivedUnknownCents)
        val surface = ready.surface as AgentSurface.FinancialSummaryCard
        assertEquals(16_000L, surface.primaryValueCents)
        assertEquals("R$ 160,00", surface.primaryValueText)
        assertEquals(null, surface.emptyMessage)
        assertEquals(4, surface.metrics.size)
        assertEquals(surface, AgentSurfaceRenderer().render(ready.result))

        assertEquals(beforeSales, database.saleDao().all())
        assertEquals(beforeReceipts, database.directReceiptDao().all())
        assertEquals(beforeCredits, database.creditDao().all())
        assertEquals(beforeEvents, database.domainEventDao().all())
    }

    @Test
    fun emptyTodayReturnsZeroCardInsteadOfErrorOrConfusingEmptyState() = runBlocking {
        val response = boundary.ask(AgentRequest("Quanto entrou hoje?"))

        assertTrue(response is AgentResponse.SurfaceReady)
        val surface = (response as AgentResponse.SurfaceReady).surface as AgentSurface.FinancialSummaryCard
        assertEquals(0L, surface.primaryValueCents)
        assertEquals("R$ 0,00", surface.primaryValueText)
        assertEquals("Hoje ainda não entrou nada.", surface.emptyMessage)
        assertEquals(4, surface.metrics.size)
        assertTrue(surface.metrics.all { it.valueCents == 0L })
    }

    @Test
    fun unsupportedQuestionDoesNotMutateOrPretendToKnow() = runBlocking {
        val response = boundary.ask(AgentRequest("Quanto foi vendido ontem?"))

        assertTrue(response is AgentResponse.Unsupported)
        assertEquals(0, database.domainEventDao().all().size)
    }

    @Test
    fun fastFinancialQuestionDoesNotInvokeModelFallback() = runBlocking {
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = object : AgentIntentInterpreter {
                override suspend fun interpret(input: String): AgentIntentResult =
                    error("O modelo não deveria ser chamado para pergunta simples")
            },
            boundary = boundary,
            a2uiMapper = com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper(),
        )

        val response = coordinator.ask("Quanto entrou no PIX hoje?")

        assertTrue(response is AgentA2uiResponse.Ready)
        val ready = response as AgentA2uiResponse.Ready
        assertTrue(ready.fastRouterHit)
        assertTrue(ready.fastRouterMs >= 0L)
        assertEquals(FinancialPaymentMethod.PIX, ready.intent.paymentMethod)
    }

    @Test
    fun semanticCapabilityPreservesOriginatingProductReference() = runBlocking {
        var receivedIntent: AgentIntent? = null
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = object : AgentIntentInterpreter {
                override suspend fun interpret(input: String): AgentIntentResult =
                    error("não deveria interpretar uma ação semântica")
            },
            boundary = object : AgentQueryBoundary {
                override suspend fun ask(intent: AgentIntent): AgentResponse {
                    receivedIntent = intent
                    return AgentResponse.Unsupported("teste")
                }
            },
            a2uiMapper = com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper(),
        )

        coordinator.askCapability(AgentCapability.GET_PRODUCT_STOCK, "product-42")

        assertEquals("product-42", receivedIntent?.productRef)
        assertEquals(null, receivedIntent?.customerRef)

        coordinator.askCapability(AgentCapability.REPLENISHMENT_QUERY, "product-42")

        assertEquals("product-42", receivedIntent?.productRef)

        coordinator.askCapability(AgentCapability.LIST_PRODUCTS, "product-42")

        assertEquals("product-42", receivedIntent?.productRef)

        coordinator.askCapability(AgentCapability.LIST_SUPPLIERS, "supplier-42")

        assertEquals("supplier-42", receivedIntent?.supplierRef)
    }

    @Test
    fun canonicalStockEntryProducesPreviewWithoutExecutingMutation() = runBlocking {
        var previewedCall: ToolCall? = null
        var executed = false
        val executor = object : ToolExecutor {
            override suspend fun preview(call: ToolCall): ToolPreview {
                previewedCall = call
                return ToolPreview("Registrar entrada?", "Café Maratá · 12 · R$ 5,00")
            }

            override suspend fun execute(call: ToolCall, confirmed: Boolean): ToolExecutionResult {
                executed = true
                return ToolExecutionResult("registrado")
            }
        }
        val boundaryWithStockEntry = TinoAgentBoundary(
            FinancialSummaryQueryTool(
                FinancialProjectionRepository(database.financialProjectionDao()),
                clock,
            ),
            AgentSurfaceRenderer(),
            executor,
        )

        val response = boundaryWithStockEntry.ask(
            AgentIntent(
                schemaVersion = AgentIntentSchema.VERSION,
                capability = AgentCapability.REGISTER_STOCK_ENTRY,
                period = AgentIntentPeriod.TODAY,
                productRef = "Café Maratá",
                quantity = 12,
                unitCostCents = 500,
                supplierRef = "Distribuidora Central",
            ),
        )

        assertTrue(response is AgentResponse.ActionPreviewReady)
        assertEquals(CommerceToolName.REGISTER_STOCK_RECEIPT, previewedCall?.name)
        assertEquals("Café Maratá", previewedCall?.arguments?.get("product"))
        assertEquals("12", previewedCall?.arguments?.get("quantity"))
        assertEquals("500", previewedCall?.arguments?.get("unit_cost_cents"))
        assertEquals("Distribuidora Central", previewedCall?.arguments?.get("supplier"))
        assertTrue(!executed)
    }

    @Test
    fun fastReceivableQuestionRendersReceivableInsteadOfReceived() = runBlocking {
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = object : AgentIntentInterpreter {
                override suspend fun interpret(input: String): AgentIntentResult =
                    error("O modelo não deveria ser chamado para pergunta simples")
            },
            boundary = boundary,
            a2uiMapper = com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper(),
        )

        val response = coordinator.ask("Quanto tenho para receber?") as AgentA2uiResponse.Ready

        assertEquals(FinancialMetric.RECEIVABLE, response.intent.metric)
        val card = response.message.component as com.tino.app.interfaceadapter.a2ui.A2uiComponent.FinancialSummaryCard
        assertEquals("A receber", card.primaryLabel)
    }

    @Test
    fun combinedFinancialQuestionRendersReceivedAndReceivableSeparately() = runBlocking {
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = object : AgentIntentInterpreter {
                override suspend fun interpret(input: String): AgentIntentResult =
                    error("O modelo não deveria ser chamado para resumo financeiro composto")
            },
            boundary = boundary,
            a2uiMapper = com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper(),
        )

        val response = coordinator.ask(
            "Quanto eu recebi hoje no Pix e no dinheiro e quanto ainda tenho para receber?",
        ) as AgentA2uiResponse.Ready
        val card = response.message.component as com.tino.app.interfaceadapter.a2ui.A2uiComponent.FinancialSummaryCard

        assertEquals(FinancialMetric.SUMMARY, response.intent.metric)
        assertEquals("Resumo financeiro de hoje", card.title)
        assertEquals("Recebido hoje", card.primaryLabel)
        assertTrue(card.metrics.any { it.label == "A receber" })
    }

    @Test
    fun physicalFinancialQuestionNeverReachesProductPicker() = runBlocking {
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = object : AgentIntentInterpreter {
                override suspend fun interpret(input: String): AgentIntentResult =
                    error("O modelo não deveria ser chamado para consulta financeira determinística")
            },
            boundary = boundary,
            a2uiMapper = com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper(),
        )

        val response = coordinator.ask(
            "Quanto eu recebi hoje no Pix e no dinheiro e quanto ainda tenho para receber?",
        )

        assertTrue(response is AgentA2uiResponse.Ready)
        val ready = response as AgentA2uiResponse.Ready
        assertEquals(AgentCapability.READ_FINANCIAL_SUMMARY, ready.intent.capability)
        assertTrue(ready.fastRouterHit)
    }

    @Test
    fun globalInventoryQuestionReturnsAProductListWithoutProductPicker() = runBlocking {
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = object : AgentIntentInterpreter {
                override suspend fun interpret(input: String): AgentIntentResult =
                    error("O modelo não deveria ser chamado para listagem global de produtos")
            },
            boundary = boundary,
            a2uiMapper = com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper(),
        )

        val response = coordinator.ask("Quais produtos eu tenho cadastrado no meu estoque?")

        assertTrue(response is AgentA2uiResponse.ReadListReady)
        assertEquals(AgentCapability.LIST_PRODUCTS, (response as AgentA2uiResponse.ReadListReady).intent.capability)
        assertTrue(response.fastRouterHit)
    }

    @Test
    fun homeAgenticInputUsesGlobalRouterForSaleAndKeepsPreviewBeforeMutation() = runBlocking {
        val executor = object : ToolExecutor {
            override suspend fun preview(call: ToolCall) = ToolPreview("Registrar venda?", call.name.name)

            override suspend fun execute(call: ToolCall, confirmed: Boolean) =
                ToolExecutionResult("Venda registrada.")
        }
        val globalBoundary = TinoAgentBoundary(
            financialSummaryTool = FinancialSummaryQueryTool(FinancialProjectionRepository(database.financialProjectionDao()), clock),
            renderer = AgentSurfaceRenderer(),
            toolExecutor = executor,
        )
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = object : AgentIntentInterpreter {
                override suspend fun interpret(input: String): AgentIntentResult =
                    error("O modelo não deveria ser chamado para comando global determinístico")
            },
            boundary = globalBoundary,
            a2uiMapper = com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper(),
        )

        val response = coordinator.ask("vendi três cafés no PIX")

        assertTrue(response is AgentA2uiResponse.ActionPreview)
        val preview = response as AgentA2uiResponse.ActionPreview
        assertEquals(AgentCapability.GLOBAL_TOOL, preview.intent.capability)
        assertEquals(com.tino.app.domain.voice.CommerceToolName.REGISTER_SALE, preview.call.name)
        assertEquals("pix", preview.call.arguments["payment_method"])
    }

    @Test
    fun globalProductClarificationResumesTheOriginalCallWithTheSelectedProduct() = runBlocking {
        var executedCall: ToolCall? = null
        val executor = object : ToolExecutor {
            override suspend fun preview(call: ToolCall) = ToolPreview("Consulta", call.name.name)

            override suspend fun execute(call: ToolCall, confirmed: Boolean): ToolExecutionResult {
                executedCall = call
                return ToolExecutionResult("Estoque consultado.")
            }
        }
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = object : AgentIntentInterpreter {
                override suspend fun interpret(input: String): AgentIntentResult =
                    error("O modelo não deveria ser chamado para a retomada da escolha")
            },
            boundary = TinoAgentBoundary(
                financialSummaryTool = FinancialSummaryQueryTool(
                    FinancialProjectionRepository(database.financialProjectionDao()),
                    clock,
                ),
                renderer = AgentSurfaceRenderer(),
                toolExecutor = executor,
            ),
            a2uiMapper = com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper(),
        )
        val choice = AgentA2uiResponse.EntityChoice(
            intent = AgentIntent(
                schemaVersion = AgentIntentSchema.VERSION,
                capability = AgentCapability.GLOBAL_TOOL,
                period = AgentIntentPeriod.TODAY,
                globalToolCall = ToolCall(
                    name = com.tino.app.domain.voice.CommerceToolName.CHECK_STOCK,
                    arguments = emptyMap(),
                ),
            ),
            entityType = "product",
            options = listOf("Café Maratá"),
            message = EntityChoiceA2uiMapper().map("product", listOf("Café Maratá")),
            latencyMs = 0L,
            intentLatencyMs = 0L,
        )

        val response = coordinator.selectEntityChoice(choice, "Café Maratá")

        assertTrue(response is AgentA2uiResponse.ActionCompleted)
        assertEquals("Café Maratá", executedCall?.arguments?.get("product"))
    }

    @Test
    fun contextualFollowUpReachesTheCanonicalPreviewWithTheActiveCustomer() = runBlocking {
        val executor = object : ToolExecutor {
            override suspend fun preview(call: ToolCall) = ToolPreview("Registrar no fiado?", call.name.name)

            override suspend fun execute(call: ToolCall, confirmed: Boolean) =
                ToolExecutionResult("Operação registrada.")
        }
        val contextualBoundary = TinoAgentBoundary(
            financialSummaryTool = FinancialSummaryQueryTool(
                FinancialProjectionRepository(database.financialProjectionDao()),
                clock,
            ),
            renderer = AgentSurfaceRenderer(),
            toolExecutor = executor,
        )
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = object : AgentIntentInterpreter {
                override suspend fun interpret(input: String): AgentIntentResult =
                    error("O modelo não deveria ser chamado para o fluxo contextual determinístico")
            },
            boundary = contextualBoundary,
            a2uiMapper = com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper(),
        )

        coordinator.ask("Bota dois cafés pra Maria")
        val response = coordinator.ask("E mais um açúcar")

        assertTrue(response is AgentA2uiResponse.ActionPreview)
        val preview = response as AgentA2uiResponse.ActionPreview
        assertEquals(AgentCapability.ADD_CREDIT_ITEM, preview.intent.capability)
        assertEquals("maria", preview.call.arguments["customer"])
        assertEquals("acucar", preview.call.arguments["product"])
        assertEquals("1", preview.call.arguments["quantity"])
    }

    @Test
    fun multiturnDraftAccumulatesAndConfirmationExecutesEachCommittedItem() = runBlocking {
        var executeCount = 0
        val executor = object : ToolExecutor {
            override suspend fun preview(call: ToolCall) = ToolPreview(
                title = "Registrar no fiado?",
                detail = "${call.arguments["quantity"]} × ${call.arguments["product"]}",
            )

            override suspend fun execute(call: ToolCall, confirmed: Boolean): ToolExecutionResult {
                executeCount++
                return ToolExecutionResult("${call.arguments["product"]} registrado")
            }
        }
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = object : AgentIntentInterpreter {
                override suspend fun interpret(input: String): AgentIntentResult =
                    error("O modelo não deve receber o fluxo multiturno determinístico")
            },
            boundary = TinoAgentBoundary(
                financialSummaryTool = FinancialSummaryQueryTool(
                    FinancialProjectionRepository(database.financialProjectionDao()),
                    clock,
                ),
                renderer = AgentSurfaceRenderer(),
                toolExecutor = executor,
            ),
            a2uiMapper = com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper(),
        )

        coordinator.ask("Bota dois Maratá pra Maria")
        val updated = coordinator.ask("Mais um açúcar") as AgentA2uiResponse.ActionPreview
        assertTrue(updated.preview.detail.contains("marata"))
        assertTrue(updated.preview.detail.contains("acucar"))

        val completed = coordinator.ask("Pode lançar")
        assertTrue(completed is AgentA2uiResponse.ActionCompleted)
        assertEquals(2, executeCount)

        val duplicate = coordinator.ask("Pode lançar")
        assertTrue(duplicate is AgentA2uiResponse.Unsupported)
        assertEquals(2, executeCount)
    }

    @Test
    fun cancellationClearsOnlyThePendingDraftAndNeverExecutes() = runBlocking {
        var executeCount = 0
        val executor = object : ToolExecutor {
            override suspend fun preview(call: ToolCall) = ToolPreview("Registrar?", call.name.name)
            override suspend fun execute(call: ToolCall, confirmed: Boolean): ToolExecutionResult {
                executeCount++
                return ToolExecutionResult("registrado")
            }
        }
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = object : AgentIntentInterpreter {
                override suspend fun interpret(input: String): AgentIntentResult =
                    error("O modelo não deve receber cancelamento")
            },
            boundary = TinoAgentBoundary(
                financialSummaryTool = FinancialSummaryQueryTool(
                    FinancialProjectionRepository(database.financialProjectionDao()),
                    clock,
                ),
                renderer = AgentSurfaceRenderer(),
                toolExecutor = executor,
            ),
            a2uiMapper = com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper(),
        )

        coordinator.ask("Bota dois Maratá pra Maria")
        val cancelled = coordinator.ask("Cancela")

        assertTrue(cancelled is AgentA2uiResponse.Unsupported)
        assertEquals(0, executeCount)
        assertTrue(coordinator.ask("Sim") is AgentA2uiResponse.Unsupported)
    }

    @Test
    fun correctionIsStoppedAtTheCoordinatorAndNeverReachesAWriteTool() = runBlocking {
        var previewCalls = 0
        val executor = object : ToolExecutor {
            override suspend fun preview(call: ToolCall): ToolPreview {
                previewCalls++
                return ToolPreview("não deveria executar", call.name.name)
            }

            override suspend fun execute(call: ToolCall, confirmed: Boolean) =
                ToolExecutionResult("não deveria executar")
        }
        val contextualBoundary = TinoAgentBoundary(
            financialSummaryTool = FinancialSummaryQueryTool(
                FinancialProjectionRepository(database.financialProjectionDao()),
                clock,
            ),
            renderer = AgentSurfaceRenderer(),
            toolExecutor = executor,
        )
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = object : AgentIntentInterpreter {
                override suspend fun interpret(input: String): AgentIntentResult =
                    error("O modelo não deve receber correção contextual")
            },
            boundary = contextualBoundary,
            a2uiMapper = com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper(),
        )

        coordinator.ask("Bota dois cafés pra Maria")
        previewCalls = 0
        val response = coordinator.ask("Não, três")

        assertTrue(response is AgentA2uiResponse.ActionPreview)
        assertEquals(1, previewCalls)
    }
}
