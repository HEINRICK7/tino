package com.tino.app.core.speech

sealed interface TranscriptEvent {
    val text: String

    data object MicStarted : TranscriptEvent {
        override val text: String = ""
    }
    data object SpeechStarted : TranscriptEvent {
        override val text: String = ""
    }
    data object EndOfSpeech : TranscriptEvent {
        override val text: String = ""
    }
    data class Partial(override val text: String) : TranscriptEvent
    data class Revised(override val text: String) : TranscriptEvent
    data class Committed(override val text: String) : TranscriptEvent
    data class Failed(val reason: String) : TranscriptEvent {
        override val text: String = ""
    }
}

interface LiveTranscriberPort {
    suspend fun start(): kotlinx.coroutines.flow.Flow<TranscriptEvent>
    suspend fun stop()
}
