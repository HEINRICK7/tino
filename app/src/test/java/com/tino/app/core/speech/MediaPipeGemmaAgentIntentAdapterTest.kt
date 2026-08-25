package com.tino.app.core.speech

import com.tino.app.domain.agent.AgentIntentResult
import com.tino.app.domain.agent.TinoCapabilityId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaPipeGemmaAgentIntentAdapterTest {
    @Test
    fun validGemmaOutputBecomesStructuredIntentWithoutFinancialValues() = runBlocking {
        val inference = RecordingInference(
            """{"schema":"tino.agent-intent","schema_version":1,"capability":"READ_FINANCIAL_SUMMARY","period":"TODAY"}""",
        )
        val result = MediaPipeGemmaAgentIntentAdapter(inference).interpret("Quanto entrou hoje?")

        val supported = result as AgentIntentResult.Supported
        assertEquals("READ_FINANCIAL_SUMMARY", supported.intent.capability.name)
        assertEquals("TODAY", supported.intent.period.name)
        assertTrue(inference.lastPrompt.contains("Não retorne valores"))
        assertTrue(inference.lastPrompt.contains("Quanto entrou hoje?"))
    }

    @Test
    fun replenishmentIntentKeepsRoomFactsBehindTheCapabilityBoundary() = runBlocking {
        val inference = RecordingInference(
            """{"schema":"tino.agent-intent","schema_version":1,"capability":"REPLENISHMENT_QUERY","period":"TODAY"}""",
        )

        val result = MediaPipeGemmaAgentIntentAdapter(inference)
            .interpret("Quais produtos tenho que comprar?")

        val supported = result as AgentIntentResult.Supported
        assertEquals("REPLENISHMENT_QUERY", supported.intent.capability.name)
        assertTrue(inference.lastPrompt.contains("REPLENISHMENT_QUERY"))
        assertTrue(inference.lastPrompt.contains("não liste produtos"))
        assertTrue(inference.lastPrompt.contains("inventory.replenishment"))
        assertTrue(inference.lastPrompt.contains("source=InventoryPolicy / Room"))
    }

    @Test
    fun gemmaToolContractIsFilteredByTheActiveBusinessProfile() = runBlocking {
        val inference = RecordingInference(
            """{"schema":"tino.agent-intent","schema_version":1,"capability":"LIST_CUSTOMERS","period":"TODAY"}""",
        )

        MediaPipeGemmaAgentIntentAdapter(inference).interpret(
            "Quais clientes tenho?",
            setOf(TinoCapabilityId.LIST_CUSTOMERS),
        )

        assertTrue(inference.lastPrompt.contains("As únicas capabilities permitidas neste contexto são: LIST_CUSTOMERS"))
        assertTrue(inference.lastPrompt.contains("customers.list[LIST_CUSTOMERS]"))
        assertFalse(inference.lastPrompt.contains("products.list[LIST_PRODUCTS]"))
        assertFalse(inference.lastPrompt.contains("inventory.replenishment[REPLENISHMENT_QUERY]"))
    }

    @Test
    fun extraFinancialFieldIsRejectedByStrictSchema() = runBlocking {
        val result = MediaPipeGemmaAgentIntentAdapter(
            RecordingInference(
                """{"schema":"tino.agent-intent","schema_version":1,"capability":"READ_FINANCIAL_SUMMARY","period":"TODAY","amount_cents":12345}""",
            ),
        ).interpret("Quanto entrou hoje?")

        assertTrue(result is AgentIntentResult.Unsupported)
    }

    @Test
    fun wrongIntentSchemaIsRejected() = runBlocking {
        val result = MediaPipeGemmaAgentIntentAdapter(
            RecordingInference(
                """{"schema":"other.protocol","schema_version":1,"capability":"READ_FINANCIAL_SUMMARY","period":"TODAY"}""",
            ),
        ).interpret("Quanto entrou hoje?")

        assertTrue(result is AgentIntentResult.Unsupported)
    }

    @Test
    fun unsupportedCapabilityAndMalformedOutputAreSafe() = runBlocking {
        val unsupportedCapability = MediaPipeGemmaAgentIntentAdapter(
            RecordingInference(
                """{"schema":"tino.agent-intent","schema_version":1,"capability":"DELETE_ALL_DATA","period":"TODAY"}""",
            ),
        ).interpret("Apague tudo")
        val malformed = MediaPipeGemmaAgentIntentAdapter(RecordingInference("não sei")).interpret("qualquer coisa")

        assertTrue(unsupportedCapability is AgentIntentResult.Unsupported)
        assertTrue(malformed is AgentIntentResult.Unsupported)
    }

    @Test
    fun naturalParaphrasesAreSentToTheSameCapabilityBoundary() = runBlocking {
        val inference = RecordingInference(
            """{"schema":"tino.agent-intent","schema_version":1,"capability":"READ_FINANCIAL_SUMMARY","period":"TODAY"}""",
        )
        val adapter = MediaPipeGemmaAgentIntentAdapter(inference)
        val phrases = listOf(
            "Quanto entrou hoje?",
            "Quanto recebemos hoje no comércio?",
            "Qual foi o total recebido hoje?",
        )

        phrases.forEach { phrase ->
            assertTrue(adapter.interpret(phrase) is AgentIntentResult.Supported)
            assertTrue(inference.lastPrompt.contains(phrase))
        }
    }

    @Test
    fun creditItemReturnsReferencesInsteadOfIdsOrFinancialValues() = runBlocking {
        val result = MediaPipeGemmaAgentIntentAdapter(
            RecordingInference(
                """{"schema":"tino.agent-intent","schema_version":1,"capability":"ADD_CREDIT_ITEM","period":"TODAY","customer_ref":"Dona Maria Lina","product_ref":"Café Maratá","quantity":1}""",
            ),
        ).interpret("adicionar um café maratá na conta da Dona Maria Lina")

        val supported = result as AgentIntentResult.Supported
        assertEquals("ADD_CREDIT_ITEM", supported.intent.capability.name)
        assertEquals("Dona Maria Lina", supported.intent.customerRef)
        assertEquals("Café Maratá", supported.intent.productRef)
        assertEquals(1, supported.intent.quantity)
    }

    @Test
    fun creditItemRejectsCrossCapabilityFieldsAndCapturesRawOutputOnlyForDebug() = runBlocking {
        val raw = """{"schema":"tino.agent-intent","schema_version":1,"capability":"ADD_CREDIT_ITEM","period":"TODAY","customer_ref":"Maria Lina","product_ref":"Café Maratá","quantity":1,"amount_cents":4500}"""
        val result = MediaPipeGemmaAgentIntentAdapter(RecordingInference(raw))
            .interpret("Maria Lina comprou 45 no fiado")

        val unsupported = result as AgentIntentResult.Unsupported
        assertEquals("Não consegui entender exatamente o que você quer anotar.", unsupported.userMessage)
        assertEquals("UNKNOWN_INTENT_FIELDS", unsupported.reason)
        assertTrue(unsupported.debug?.unexpectedKeys?.contains("amount_cents") == true)
        assertNotNull(unsupported.debug?.rawOutput)
        assertTrue(unsupported.debug?.rawOutput?.contains("amount_cents") == true)
    }

    @Test
    fun creditItemPromptCoversNaturalPortugueseVariantsWithoutAllowingAmountMutation() = runBlocking {
        val inference = RecordingInference(
            """{"schema":"tino.agent-intent","schema_version":1,"capability":"ADD_CREDIT_ITEM","period":"TODAY","customer_ref":"Maria Lina","product_ref":"Café Maratá","quantity":1}""",
        )
        val adapter = MediaPipeGemmaAgentIntentAdapter(inference)
        listOf(
            "Maria Lina comprou fiado um café maratá",
            "Adicionar um café maratá na conta da Maria Lina",
            "Maria Lina levou um café maratá fiado",
        ).forEach { phrase ->
            assertTrue(adapter.interpret(phrase) is AgentIntentResult.Supported)
            assertTrue(inference.lastPrompt.contains(phrase))
        }
        assertTrue(inference.lastPrompt.contains("EXATAMENTE estas chaves"))
        assertTrue(inference.lastPrompt.contains("amount_cents"))
    }

    @Test
    fun customerBalanceReturnsOnlyTheCustomerReference() = runBlocking {
        val result = MediaPipeGemmaAgentIntentAdapter(
            RecordingInference(
                """{"schema":"tino.agent-intent","schema_version":1,"capability":"GET_CUSTOMER_BALANCE","period":"TODAY","customer_ref":"Dona Maria Lina"}""",
            ),
        ).interpret("Quanto a Maria Lina está devendo?")

        val supported = result as AgentIntentResult.Supported
        assertEquals("GET_CUSTOMER_BALANCE", supported.intent.capability.name)
        assertEquals("Dona Maria Lina", supported.intent.customerRef)
        assertEquals(null, supported.intent.productRef)
        assertEquals(null, supported.intent.quantity)
    }

    @Test
    fun customerTimelineReturnsOnlyTheCustomerReference() = runBlocking {
        val result = MediaPipeGemmaAgentIntentAdapter(
            RecordingInference(
                """{"schema":"tino.agent-intent","schema_version":1,"capability":"GET_CUSTOMER_TIMELINE","period":"TODAY","customer_ref":"Maria Lina"}""",
            ),
        ).interpret("Mostra a conta da Maria")

        val supported = result as AgentIntentResult.Supported
        assertEquals("GET_CUSTOMER_TIMELINE", supported.intent.capability.name)
        assertEquals("Maria Lina", supported.intent.customerRef)
    }

    private class RecordingInference(
        private val response: String,
    ) : GemmaTextInference {
        var lastPrompt: String = ""

        override suspend fun generate(prompt: String): GemmaTextInferenceResult {
            lastPrompt = prompt
            return GemmaTextInferenceResult.Generated(response)
        }
    }
}
