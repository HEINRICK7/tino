package com.tino.app.core.speech

import android.speech.SpeechRecognizer

/** Provider choice is kept independent from Android APIs so the fallback policy is testable. */
internal enum class SpeechProvider {
    ON_DEVICE,
    ANDROID_STANDARD,
    NONE,
}

internal fun selectSpeechProvider(
    onDeviceAvailable: Boolean,
    recognizerAvailable: Boolean,
): SpeechProvider = when {
    onDeviceAvailable -> SpeechProvider.ON_DEVICE
    recognizerAvailable -> SpeechProvider.ANDROID_STANDARD
    else -> SpeechProvider.NONE
}

/**
 * Errors that indicate the selected on-device provider could not serve the session.
 * Recognition/content errors intentionally do not retry: the user should not have
 * to repeat a phrase just because it was not understood.
 */
internal fun shouldFallbackToStandard(error: Int): Boolean = when (error) {
    SpeechRecognizer.ERROR_CLIENT,
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
    -> true
    else -> false
}
