package com.tino.app.core.speech

import com.tino.app.domain.voice.VoiceContext
import com.tino.app.domain.voice.VoiceExtraction
import com.tino.app.domain.voice.VoiceExtractionValidator
import com.tino.app.domain.voice.VoiceInputPort
import com.tino.app.domain.voice.VoiceInputResult
import com.tino.app.domain.voice.VoiceValidationResult
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GemmaExtractionResult {
    data class Extracted(val fields: Map<String, String>) : GemmaExtractionResult
    data class Unavailable(val reason: String) : GemmaExtractionResult
    data class Failed(val reason: String) : GemmaExtractionResult
}

/** Structured extraction boundary; Gemma supplies fields, not domain mutations. */
interface GemmaStructuredExtractor {
    suspend fun extract(context: VoiceContext, transcript: String): GemmaExtractionResult
}

@Singleton
class UnavailableGemmaStructuredExtractor @Inject constructor() : GemmaStructuredExtractor {
    override suspend fun extract(context: VoiceContext, transcript: String): GemmaExtractionResult =
        GemmaExtractionResult.Unavailable("A voz ainda não está disponível neste aparelho. Preencha abaixo.")
}

@Singleton
class GemmaVoiceInputAdapter @Inject constructor(
    private val transcriber: LiveTranscriberPort,
    private val extractor: GemmaStructuredExtractor,
) : VoiceInputPort {
    override suspend fun stop() = transcriber.stop()

    override suspend fun listen(
        context: VoiceContext,
        onCommitted: (String) -> Unit,
        onTranscript: (String) -> Unit,
    ): VoiceInputResult {
        var result: VoiceInputResult? = null
        try {
            transcriber.start()
                .takeWhile { event ->
                    when (event) {
                        TranscriptEvent.MicStarted,
                        TranscriptEvent.SpeechStarted,
                        TranscriptEvent.EndOfSpeech,
                        -> true
                        is TranscriptEvent.Partial,
                        is TranscriptEvent.Revised,
                        -> {
                            onTranscript(event.text)
                            true
                        }
                        is TranscriptEvent.Failed -> {
                            result = VoiceInputResult.Unavailable(event.reason)
                            false
                        }
                        is TranscriptEvent.Committed -> {
                            onTranscript(event.text)
                            onCommitted(event.text)
                            result = committedResult(context, event.text)
                            false
                        }
                    }
                }
                .collect()
        } catch (error: Throwable) {
            result = VoiceInputResult.Failed(error.message ?: "Não foi possível processar a fala.")
        } finally {
            runCatching { transcriber.stop() }
        }
        return result ?: VoiceInputResult.Failed("A fala não foi confirmada.")
    }

    private suspend fun committedResult(
        context: VoiceContext,
        transcript: String,
    ): VoiceInputResult = when (val extraction = extractor.extract(context, transcript)) {
        is GemmaExtractionResult.Extracted -> validatedResult(
            VoiceExtraction(context, transcript, extraction.fields),
        )
        is GemmaExtractionResult.Unavailable -> VoiceInputResult.Unavailable(extraction.reason)
        is GemmaExtractionResult.Failed -> VoiceInputResult.Failed(extraction.reason)
    }

    private fun validatedResult(extraction: VoiceExtraction): VoiceInputResult =
        when (val validation = VoiceExtractionValidator.validate(extraction)) {
            is VoiceValidationResult.Valid -> VoiceInputResult.Extracted(validation.value)
            is VoiceValidationResult.NeedsCorrection -> VoiceInputResult.NeedsCorrection(
                value = validation.value,
                missingFields = validation.missingFields,
                invalidFields = validation.invalidFields,
                message = validation.message,
            )
        }
}
