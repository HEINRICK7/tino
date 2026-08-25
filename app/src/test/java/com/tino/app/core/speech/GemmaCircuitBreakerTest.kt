package com.tino.app.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaCircuitBreakerTest {
    @Test
    fun failedInitializationStopsImmediateRetryAndAllowsARecoveryProbe() {
        var now = 1_000L
        val breaker = GemmaCircuitBreaker(cooldownMs = 30_000L, nowMs = { now })

        assertTrue(breaker.tryAcquire())
        assertEquals(GemmaAvailability.INITIALIZING, breaker.state)
        breaker.recordFailure()
        assertEquals(GemmaAvailability.DEGRADED, breaker.state)
        assertFalse(breaker.tryAcquire())

        now += 30_000L
        assertTrue(breaker.tryAcquire())
        assertEquals(GemmaAvailability.RECOVERING, breaker.state)
        breaker.recordSuccess()
        assertEquals(GemmaAvailability.AVAILABLE, breaker.state)
    }

    @Test
    fun failedRecoveryBecomesUnavailableUntilTheNextProbe() {
        var now = 1_000L
        val breaker = GemmaCircuitBreaker(cooldownMs = 10_000L, nowMs = { now })

        assertTrue(breaker.tryAcquire())
        breaker.recordFailure()
        now += 10_000L
        assertTrue(breaker.tryAcquire())
        breaker.recordFailure()

        assertEquals(GemmaAvailability.UNAVAILABLE, breaker.state)
        assertFalse(breaker.tryAcquire())
    }
}
