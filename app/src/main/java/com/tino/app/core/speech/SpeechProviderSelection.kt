package com.tino.app.core.speech

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
