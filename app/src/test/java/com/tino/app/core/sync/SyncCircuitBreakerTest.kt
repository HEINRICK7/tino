package com.tino.app.core.sync

import org.junit.Assert.fail
import org.junit.Test

class SyncCircuitBreakerTest {
    @Test
    fun opensAfterThreeFailuresAndAllowsARecoveryProbeAfterCooldown() {
        var now = 0L
        val breaker = SyncCircuitBreaker(
            failureThreshold = 3,
            openDurationMillis = 1_000,
            nowMillis = { now },
        )

        repeat(3) {
            breaker.beforeCall()
            breaker.recordFailure()
        }
        expectUnavailable { breaker.beforeCall() }

        now = 1_000
        breaker.beforeCall()
        breaker.recordSuccess()
        breaker.beforeCall()
    }

    @Test
    fun halfOpenAllowsOnlyOneProbe() {
        var now = 1_000L
        val breaker = SyncCircuitBreaker(
            failureThreshold = 1,
            openDurationMillis = 500,
            nowMillis = { now },
        )

        breaker.beforeCall()
        breaker.recordFailure()
        now = 1_500
        breaker.beforeCall()
        expectUnavailable { breaker.beforeCall() }
        breaker.recordSuccess()
    }

    private fun expectUnavailable(block: () -> Unit) {
        try {
            block()
            fail("Esperava SyncUnavailableException")
        } catch (_: SyncUnavailableException) {
            // expected
        }
    }
}
