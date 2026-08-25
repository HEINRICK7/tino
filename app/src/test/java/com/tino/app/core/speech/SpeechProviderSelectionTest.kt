package com.tino.app.core.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechProviderSelectionTest {
    @Test
    fun onDeviceProviderHasPriority() {
        assertEquals(SpeechProvider.ON_DEVICE, selectSpeechProvider(onDeviceAvailable = true, recognizerAvailable = true))
    }

    @Test
    fun standardProviderIsUsedWhenOnDeviceIsUnavailable() {
        assertEquals(SpeechProvider.ANDROID_STANDARD, selectSpeechProvider(onDeviceAvailable = false, recognizerAvailable = true))
    }

    @Test
    fun noProviderIsRecoverableUnavailable() {
        assertEquals(SpeechProvider.NONE, selectSpeechProvider(onDeviceAvailable = false, recognizerAvailable = false))
    }
}
