package com.tino.app.core.speech

import com.tino.app.domain.voice.VoiceContext
import com.tino.app.domain.voice.VoiceInputPort
import com.tino.app.domain.voice.VoiceInputResult
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps speech capture available without silently pretending to extract fields.
 * Contextual forms fall back to manual entry until a deterministic extractor is
 * deliberately designed and validated.
 */
@Singleton
class ManualVoiceInputAdapter @Inject constructor(
    private val transcriber: LiveTranscriberPort,
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
                            result = VoiceInputResult.Unavailable(
                                "A fala foi transcrita, mas este formulário precisa ser preenchido manualmente.",
                            )
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
}
