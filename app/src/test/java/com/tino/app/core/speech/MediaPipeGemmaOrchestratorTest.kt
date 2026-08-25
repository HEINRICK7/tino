package com.tino.app.core.speech

import com.tino.app.domain.voice.CommerceToolName
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaPipeGemmaOrchestratorTest {
    @Test
    fun naturalCreditSaleNormalizesToolNameAndSpokenQuantity() = runBlocking {
        val orchestrator = MediaPipeGemmaOrchestrator(
            FixedInference(
                """{"name":"register_credit_sale","arguments":{"customer":"João","product":"Café Maratá","quantity":"dois"}}""",
            ),
        )

        val call = orchestrator.interpret("João levou dois cafés fiado")

        assertNotNull(call)
        assertEquals(CommerceToolName.REGISTER_CREDIT_SALE, call?.name)
        assertEquals("João", call?.arguments?.get("customer"))
        assertEquals("2", call?.arguments?.get("quantity"))
    }

    @Test
    fun malformedModelOutputDoesNotBecomeACommand() = runBlocking {
        val orchestrator = MediaPipeGemmaOrchestrator(FixedInference("não é json"))

        assertNull(orchestrator.interpret("qualquer coisa"))
    }

    @Test
    fun entityReferencesAreNormalizedAndQuantityDefaultsToOne() = runBlocking {
        val orchestrator = MediaPipeGemmaOrchestrator(
            FixedInference(
                """{"name":"ADD_CREDIT_ITEM","arguments":{"customer_ref":"Dona Maria Lina","product_ref":"Café Maratá"}}""",
            ),
        )

        val call = orchestrator.interpret("adicionar um café maratá na conta da Dona Maria Lina")

        assertNotNull(call)
        assertEquals(CommerceToolName.ADD_CREDIT_ITEM, call?.name)
        assertEquals("Dona Maria Lina", call?.arguments?.get("customer"))
        assertEquals("Café Maratá", call?.arguments?.get("product"))
        assertEquals("1", call?.arguments?.get("quantity"))
        assertNull(call?.arguments?.get("customer_ref"))
        assertNull(call?.arguments?.get("product_ref"))
    }

    @Test
    fun modelCannotProvideEntityIdsOrFinancialTruthForCreditItem() = runBlocking {
        val orchestrator = MediaPipeGemmaOrchestrator(
            FixedInference(
                """{"name":"ADD_CREDIT_ITEM","arguments":{"customer":"Maria Lina","product":"Café Maratá","customer_id":"fake","price_cents":"1","stock":"999"}}""",
            ),
        )

        assertNull(orchestrator.interpret("adicionar café na conta da Maria Lina"))
    }

    @Test
    fun unavailableGemmaFallsBackToGlobalDeterministicCommandRouter() = runBlocking {
        val orchestrator = MediaPipeGemmaOrchestrator(UnavailableInference())

        val call = orchestrator.interpret("vendi três cafés no PIX")

        assertNotNull(call)
        assertEquals(CommerceToolName.REGISTER_SALE, call?.name)
        assertEquals("3", call?.arguments?.get("quantity"))
        assertEquals("pix", call?.arguments?.get("payment_method"))
    }

    private class FixedInference(
        private val response: String,
    ) : GemmaTextInference {
        override suspend fun generate(prompt: String): GemmaTextInferenceResult =
            GemmaTextInferenceResult.Generated(response)
    }

    private class UnavailableInference : GemmaTextInference {
        override suspend fun generate(prompt: String): GemmaTextInferenceResult =
            GemmaTextInferenceResult.Unavailable("modelo indisponível")
    }
}
