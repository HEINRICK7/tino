package com.tino.app.core.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** Stable boundary for Android speech-to-transcript providers. */
interface SpeechTranscriberRuntime {
    suspend fun start(): Flow<TranscriptEvent>
    suspend fun stop()
}

/** Safe fallback when the device has no speech recognizer. */
@Singleton
class UnavailableSpeechTranscriberRuntime @Inject constructor() : SpeechTranscriberRuntime {
    override suspend fun start(): Flow<TranscriptEvent> = flowOf(
        TranscriptEvent.Failed("A voz ainda não está disponível neste aparelho. Use o teclado para continuar."),
    )

    override suspend fun stop() = Unit
}

/** Application-facing transcription adapter. */
@Singleton
class LiveTranscriber @Inject constructor(
    private val runtime: SpeechTranscriberRuntime,
) : LiveTranscriberPort {
    override suspend fun start(): Flow<TranscriptEvent> = runtime.start()

    override suspend fun stop() = runtime.stop()
}
