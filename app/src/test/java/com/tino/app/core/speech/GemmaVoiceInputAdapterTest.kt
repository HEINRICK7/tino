package com.tino.app.core.speech

import com.tino.app.domain.voice.VoiceContext
import com.tino.app.domain.voice.VoiceInputResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GemmaVoiceInputAdapterTest {
    @Test
    fun committedExtractionIsValidatedBeforeItReachesTheVoicePort() = runBlocking {
        val transcriber = RecordingTranscriber(
            flowOf(
                TranscriptEvent.Partial("Mercadinho São José"),
                TranscriptEvent.Committed("Mercadinho São José"),
            ),
        )
        val extractor = FixedExtractor(
            GemmaExtractionResult.Extracted(
                mapOf(
                    "store_name" to "Mercadinho São José",
                    "phone" to "(86) 9 1234-5678",
                ),
            ),
        )

        val transcripts = mutableListOf<String>()
        val committed = mutableListOf<String>()
        val result = GemmaVoiceInputAdapter(transcriber, extractor).listen(
            context = VoiceContext.ONBOARDING,
            onCommitted = { committed += it },
            onTranscript = { transcripts += it },
        )

        val correction = result as VoiceInputResult.NeedsCorrection
        assertEquals(listOf("Mercadinho São José", "Mercadinho São José"), transcripts)
        assertEquals(listOf("Mercadinho São José"), committed)
        assertEquals(setOf("owner_name"), correction.missingFields)
        assertEquals("86912345678", correction.value.fields["phone"])
        assertTrue(transcriber.stopCalled)
    }

    @Test
    fun fullTranscriptToFieldsPipelineUsesSpokenValuesWhenModelJsonIsInvalid() = runBlocking {
        val transcriber = RecordingTranscriber(
            flowOf(
                TranscriptEvent.Partial("Mercadinho Nossa Senhora de Fátima"),
                TranscriptEvent.Committed(
                    "Mercadinho Nossa Senhora de Fátima meu nome é Carlos Henrique e o telefone é 86 99420 9350",
                ),
            ),
        )
        val extractor = MediaPipeGemmaStructuredExtractor(
            FixedInference("resposta inválida do modelo"),
        )

        val result = GemmaVoiceInputAdapter(transcriber, extractor).listen(VoiceContext.ONBOARDING)
        val extracted = result as VoiceInputResult.Extracted

        assertEquals("Mercadinho Nossa Senhora de Fátima", extracted.value.fields["store_name"])
        assertEquals("Carlos Henrique", extracted.value.fields["owner_name"])
        assertEquals("86994209350", extracted.value.fields["phone"])
    }

    private class FixedExtractor(
        private val result: GemmaExtractionResult,
    ) : GemmaStructuredExtractor {
        override suspend fun extract(context: VoiceContext, transcript: String): GemmaExtractionResult = result
    }

    private class FixedInference(
        private val response: String,
    ) : GemmaTextInference {
        override suspend fun generate(prompt: String): GemmaTextInferenceResult =
            GemmaTextInferenceResult.Generated(response)
    }

    private class RecordingTranscriber(
        private val events: Flow<TranscriptEvent>,
    ) : LiveTranscriberPort {
        var stopCalled = false

        override suspend fun start(): Flow<TranscriptEvent> = events

        override suspend fun stop() {
            stopCalled = true
        }
    }
}
