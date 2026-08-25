package com.tino.app.feature.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgenticVoiceMetricsTest {
    @Test
    fun stageDurationsSeparateSilenceFromFinalization() {
        val metrics = AgenticVoiceMetrics(
            ttfpMs = 420L,
            voiceFinalMs = 4_330L,
            gemmaMs = 0L,
            intentMs = 3L,
            capabilityMs = 97L,
            a2uiMs = 0L,
            totalToCardMs = 4_330L,
            firstPartialMs = 420L,
            lastPartialMs = 1_200L,
            endOfSpeechMs = 4_200L,
            finalResultMs = 4_330L,
        )

        assertEquals(780L, metrics.firstToLastPartialMs)
        assertEquals(3_000L, metrics.lastPartialToEndOfSpeechMs)
        assertEquals(130L, metrics.endOfSpeechToFinalMs)
        assertEquals(4_330L, metrics.finalResultMs)
    }

    @Test
    fun missingRecognizerStagesRemainUnknown() {
        val metrics = AgenticVoiceMetrics(
            ttfpMs = null,
            voiceFinalMs = 100L,
            gemmaMs = 0L,
            intentMs = 1L,
            capabilityMs = 1L,
            a2uiMs = 1L,
            totalToCardMs = 101L,
        )

        assertNull(metrics.firstToLastPartialMs)
        assertNull(metrics.lastPartialToEndOfSpeechMs)
        assertNull(metrics.endOfSpeechToFinalMs)
    }
}
