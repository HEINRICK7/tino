package com.tino.app.domain.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.database.DirectReceiptEntity
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.speech.GemmaTextInference
import com.tino.app.core.speech.GemmaTextInferenceResult
import com.tino.app.core.speech.MediaPipeGemmaAgentIntentAdapter
import com.tino.app.domain.finance.FinancialProjectionRepository
import com.tino.app.interfaceadapter.a2ui.A2uiComponent
import com.tino.app.interfaceadapter.a2ui.CommerceActionA2uiMapper
import com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper
import com.tino.app.domain.voice.ToolCall
import com.tino.app.domain.voice.ToolExecutionResult
import com.tino.app.domain.voice.ToolExecutor
import com.tino.app.domain.voice.ToolPreview
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
class AgenticGemmaA2uiTest {
    private lateinit var database: TinoDatabase
    private lateinit var coordinator: AgenticTextQueryCoordinator
    private val zone = ZoneId.of("America/Fortaleza")
    private val clock = Clock.fixed(Instant.parse("2026-08-17T15:00:00Z"), zone)

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val now = Instant.now(clock).toEpochMilli()
        database.directReceiptDao().insert(
            DirectReceiptEntity("receipt-agentic", 16_000, "pix", now, "manual", null, "receipt-agentic"),
        )
        val projection = FinancialProjectionRepository(database.financialProjectionDao())
        val boundary = TinoAgentBoundary(
            financialSummaryTool = FinancialSummaryQueryTool(projection, clock),
            renderer = AgentSurfaceRenderer(),
        )
        val inference = FixedInference(
            """{"schema":"tino.agent-intent","schema_version":1,"capability":"READ_FINANCIAL_SUMMARY","period":"TODAY"}""",
        )
        coordinator = AgenticTextQueryCoordinator(
            interpreter = MediaPipeGemmaAgentIntentAdapter(inference),
            boundary = boundary,
            a2uiMapper = FinancialSummaryA2uiMapper(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun naturalTextTravelsFromGemmaToFinancialProjectionAndA2ui() = runBlocking {
        val response = coordinator.ask("Quanto recebemos hoje no comércio?")

        assertTrue(response is AgentA2uiResponse.Ready)
        val ready = response as AgentA2uiResponse.Ready
        assertEquals(AgentCapability.READ_FINANCIAL_SUMMARY, ready.intent.capability)
        assertEquals(AgentIntentPeriod.TODAY, ready.intent.period)
        assertEquals(16_000L, ready.result.receivedTotalCents)
        val card = ready.message.component as A2uiComponent.FinancialSummaryCard
        assertEquals("R$ 160,00", card.primaryValueText)
        assertEquals("LOCAL_ONLY", card.dataSource)
        assertTrue(ready.latencyMs >= 0)
    }

    @Test
    fun unsupportedIntentDoesNotReachFinancialBoundary() = runBlocking {
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = object : AgentIntentInterpreter {
                override suspend fun interpret(input: String): AgentIntentResult =
                    AgentIntentResult.Unsupported("Não entendi essa pergunta.")
            },
            boundary = object : AgentQueryBoundary {
                override suspend fun ask(intent: AgentIntent): AgentResponse =
                    error("não deve ser chamado")
            },
            a2uiMapper = FinancialSummaryA2uiMapper(),
        )

        val response = coordinator.ask("Apague todos os dados")

        assertTrue(response is AgentA2uiResponse.Unsupported)
        assertEquals("Não entendi essa pergunta.", (response as AgentA2uiResponse.Unsupported).message)
    }

    @Test
    fun creditCommandReachesCapabilityAndReturnsPreviewA2uiWithoutMutation() = runBlocking {
        val executor = PreviewExecutor()
        val boundary = TinoAgentBoundary(
            financialSummaryTool = FinancialSummaryQueryTool(
                FinancialProjectionRepository(database.financialProjectionDao()),
                clock,
            ),
            renderer = AgentSurfaceRenderer(),
            toolExecutor = executor,
        )
        val coordinator = AgenticTextQueryCoordinator(
            interpreter = MediaPipeGemmaAgentIntentAdapter(
                FixedInference(
                    """{"schema":"tino.agent-intent","schema_version":1,"capability":"ADD_CREDIT_ITEM","period":"TODAY","customer_ref":"Dona Maria Lina","product_ref":"Café Maratá","quantity":1}""",
                ),
            ),
            boundary = boundary,
            a2uiMapper = FinancialSummaryA2uiMapper(),
            actionMapper = CommerceActionA2uiMapper(),
        )

        val response = coordinator.ask("adicionar um café maratá na conta da Dona Maria Lina")

        assertTrue(response is AgentA2uiResponse.ActionPreview)
        val preview = response as AgentA2uiResponse.ActionPreview
        assertEquals(AgentCapability.ADD_CREDIT_ITEM, preview.intent.capability)
        assertEquals("maria lina", preview.call.arguments["customer"])
        assertEquals("cafe marata", preview.call.arguments["product"])
        assertTrue(preview.message.component is A2uiComponent.ActionConfirmation)
        assertTrue(!executor.executed)
        assertEquals(0, database.domainEventDao().all().size)
    }

    @Test
    fun commonCreditTextUsesCommandRouterBeforeGemma() = runBlocking {
        val executor = PreviewExecutor()
        val boundary = TinoAgentBoundary(
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
                    error("Gemma não deve ser chamado para comando comum")
            },
            boundary = boundary,
            a2uiMapper = FinancialSummaryA2uiMapper(),
            actionMapper = CommerceActionA2uiMapper(),
        )

        val response = coordinator.ask("adicionar um café maratá na conta da dona Maria Lina")

        assertTrue(response is AgentA2uiResponse.ActionPreview)
        val preview = response as AgentA2uiResponse.ActionPreview
        assertTrue(preview.commandRouterHit)
        assertTrue(preview.commandRouterMs >= 0L)
        assertEquals(AgentCapability.ADD_CREDIT_ITEM, preview.intent.capability)
        assertEquals("maria lina", preview.call.arguments["customer"])
        assertEquals("cafe marata", preview.call.arguments["product"])
        assertEquals("1", preview.call.arguments["quantity"])
        assertTrue(!executor.executed)
    }

    private class FixedInference(
        private val response: String,
    ) : GemmaTextInference {
        override suspend fun generate(prompt: String): GemmaTextInferenceResult =
            GemmaTextInferenceResult.Generated(response)
    }

    private class PreviewExecutor : ToolExecutor {
        var executed = false

        override suspend fun preview(call: ToolCall) = ToolPreview(
            title = "Registrar venda fiada?",
            detail = "Maria Lina\n1 × Café Maratá · R$ 8,75\nSaldo atual: R$ 0,00\nDepois: R$ 8,75",
            confirmLabel = "ANOTAR FIADO",
        )

        override suspend fun execute(call: ToolCall, confirmed: Boolean): ToolExecutionResult {
            executed = true
            return ToolExecutionResult("Fiado registrado para Maria Lina.")
        }
    }
}
