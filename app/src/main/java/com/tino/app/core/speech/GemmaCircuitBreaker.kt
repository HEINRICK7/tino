package com.tino.app.core.speech

/** Runtime availability exposed by the local LLM boundary. */
enum class GemmaAvailability {
    AVAILABLE,
    INITIALIZING,
    DEGRADED,
    UNAVAILABLE,
    RECOVERING,
}

/**
 * Small process-local breaker for the isolated Gemma service.
 *
 * One failed request is enough to stop retrying the same broken initialization
 * on every phrase. After the cooldown, one request is allowed as a recovery
 * probe. This keeps the normal voice path deterministic when Gemma is absent.
 */
internal class GemmaCircuitBreaker(
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    var state: GemmaAvailability = GemmaAvailability.AVAILABLE
        private set

    private var retryAfterMs: Long = 0L

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = nowMs()
        if (state == GemmaAvailability.DEGRADED || state == GemmaAvailability.UNAVAILABLE) {
            if (now < retryAfterMs) return false
            state = GemmaAvailability.RECOVERING
        } else {
            state = GemmaAvailability.INITIALIZING
        }
        return true
    }

    @Synchronized
    fun recordSuccess() {
        state = GemmaAvailability.AVAILABLE
        retryAfterMs = 0L
    }

    @Synchronized
    fun recordFailure() {
        state = if (state == GemmaAvailability.RECOVERING) {
            GemmaAvailability.UNAVAILABLE
        } else {
            GemmaAvailability.DEGRADED
        }
        retryAfterMs = nowMs() + cooldownMs
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 30_000L
    }
}
