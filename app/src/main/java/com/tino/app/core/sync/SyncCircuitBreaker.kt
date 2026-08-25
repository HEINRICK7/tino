package com.tino.app.core.sync

/**
 * Small process-local breaker for the sync transport.
 *
 * It protects the device from repeatedly opening sockets while the cloud is
 * unavailable. WorkManager remains responsible for the durable retry.
 */
internal class SyncCircuitBreaker(
    private val failureThreshold: Int = 3,
    private val openDurationMillis: Long = 30_000,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private enum class State { CLOSED, OPEN, HALF_OPEN }

    private var state = State.CLOSED
    private var consecutiveFailures = 0
    private var openedAtMillis = 0L
    private var halfOpenProbeInFlight = false

    @Synchronized
    fun beforeCall() {
        when (state) {
            State.CLOSED -> Unit
            State.OPEN -> {
                if (nowMillis() - openedAtMillis < openDurationMillis) {
                    throw SyncUnavailableException("Sincronização temporariamente pausada; tentando novamente em breve.")
                }
                state = State.HALF_OPEN
                halfOpenProbeInFlight = true
            }
            State.HALF_OPEN -> {
                if (halfOpenProbeInFlight) {
                    throw SyncUnavailableException("Já existe uma tentativa de recuperação da sincronização.")
                }
                halfOpenProbeInFlight = true
            }
        }
    }

    @Synchronized
    fun recordSuccess() {
        state = State.CLOSED
        consecutiveFailures = 0
        halfOpenProbeInFlight = false
    }

    @Synchronized
    fun recordFailure() {
        halfOpenProbeInFlight = false
        consecutiveFailures += 1
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN
            openedAtMillis = nowMillis()
        }
    }
}
