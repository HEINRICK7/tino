package com.tino.app.core.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speech-to-transcript boundary kept separate from Gemma text inference.
 * MediaPipe LlmInference consumes text; an ASR implementation must provide this port.
 */
interface GemmaTranscriberRuntime {
    suspend fun start(): Flow<TranscriptEvent>
    suspend fun stop()
}

/** Default until an approved on-device speech recognizer is supplied by the app build. */
@Singleton
class UnavailableGemmaTranscriberRuntime @Inject constructor() : GemmaTranscriberRuntime {
    override suspend fun start(): Flow<TranscriptEvent> = flowOf(
        TranscriptEvent.Failed("A voz ainda não está disponível neste aparelho. Preencha abaixo."),
    )

    override suspend fun stop() = Unit
}

/** The only live transcription adapter exposed to the rest of the app. */
@Singleton
class GemmaLiveTranscriber @Inject constructor(
    private val runtime: GemmaTranscriberRuntime,
) : LiveTranscriberPort {
    override suspend fun start(): Flow<TranscriptEvent> = runtime.start()

    override suspend fun stop() = runtime.stop()
}
