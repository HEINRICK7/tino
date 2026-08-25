package com.tino.app.core.speech

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun providerFailureCanFallbackToStandardRecognizer() {
        assertTrue(shouldFallbackToStandard(SpeechRecognizer.ERROR_CLIENT))
        assertTrue(shouldFallbackToStandard(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
        assertTrue(shouldFallbackToStandard(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
    }

    @Test
    fun contentFailuresDoNotRetryAndMakeTheUserRepeat() {
        assertFalse(shouldFallbackToStandard(SpeechRecognizer.ERROR_NO_MATCH))
        assertFalse(shouldFallbackToStandard(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
    }
}
