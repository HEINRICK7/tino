package com.tino.app.core.speech

import com.tino.app.domain.voice.VoiceContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaPipeGemmaStructuredExtractorTest {
    @Test
    fun onboardingFallbackFillsFieldsWhenModelReturnsInvalidJson() = runBlocking {
        val extractor = MediaPipeGemmaStructuredExtractor(FixedInference("resposta inválida"))

        val result = extractor.extract(
            VoiceContext.ONBOARDING,
            "Mercadinho Nossa Senhora de Fátima, meu nome é Carlos Henrique e o telefone 86 94209350",
        )

        val extracted = result as GemmaExtractionResult.Extracted
        assertEquals("Mercadinho Nossa Senhora de Fátima", extracted.fields["store_name"])
        assertEquals("Carlos Henrique", extracted.fields["owner_name"])
        assertEquals("8694209350", extracted.fields["phone"])
    }

    @Test
    fun onboardingNeverUsesModelExampleInsteadOfTheCommittedTranscript() = runBlocking {
        val extractor = MediaPipeGemmaStructuredExtractor(
            FixedInference(
                """{"store_name":"Mercadinho Central","owner_name":"Carlos","phone":"86999999999"}""",
            ),
        )

        val result = extractor.extract(
            VoiceContext.ONBOARDING,
            "Mercadinho Nossa Senhora de Fátima meu nome é Carlos Henrique e o telefone é 86 99420 9350",
        )

        val extracted = result as GemmaExtractionResult.Extracted
        assertEquals("Mercadinho Nossa Senhora de Fátima", extracted.fields["store_name"])
        assertEquals("Carlos Henrique", extracted.fields["owner_name"])
        assertEquals("86994209350", extracted.fields["phone"])
    }

    @Test
    fun modelAliasesAreCanonicalizedBeforeValidation() {
        val fields = GemmaJsonOutputParser.parse(
            """{"store":"Mercadinho Central","owner":"Carlos","cellphone":"86999999999"}""",
        )

        assertTrue(fields != null)
        assertEquals("Mercadinho Central", fields?.get("store_name"))
        assertEquals("Carlos", fields?.get("owner_name"))
        assertEquals("86999999999", fields?.get("phone"))
    }

    private class FixedInference(
        private val response: String,
    ) : GemmaTextInference {
        override suspend fun generate(prompt: String): GemmaTextInferenceResult =
            GemmaTextInferenceResult.Generated(response)
    }
}
