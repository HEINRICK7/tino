package com.tino.app.domain.agent

import java.time.Clock
import java.util.UUID

enum class HumanGateDecision { ALLOW, CONFIRM, DENY }

/**
 * Pure policy shared by the agent runtime and the commerce mutation boundary.
 * It deliberately owns no pending state: token issuance, expiry and replay
 * protection remain in the authoritative mutation store.
 */
object HumanGatePolicy {
    fun evaluate(capability: TinoCapabilityId): HumanGateDecision = runCatching {
        val descriptor = TinoCapabilityRegistry.require(capability)
        when {
            descriptor.type != TinoCapabilityType.MUTATION -> HumanGateDecision.ALLOW
            descriptor.confirmation == TinoConfirmationPolicy.REQUIRED -> HumanGateDecision.CONFIRM
            else -> HumanGateDecision.DENY
        }
    }.getOrElse { HumanGateDecision.DENY }
}

data class HumanGateRequest(
    val gateId: String = UUID.randomUUID().toString(),
    val capability: TinoCapabilityId,
    val summary: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

sealed interface HumanGateResult {
    data object Allowed : HumanGateResult
    data class ConfirmationRequired(val request: HumanGateRequest) : HumanGateResult
    data class Denied(val reason: String) : HumanGateResult
}

/** Central risk gate for capabilities before a mutation can reach a tool. */
class HumanGateRuntime(
    private val clock: Clock = Clock.systemUTC(),
    private val confirmationTtlMs: Long = 5 * 60 * 1_000L,
) {
    private val pending = linkedMapOf<String, HumanGateRequest>()

    init {
        require(confirmationTtlMs > 0L) { "TTL de confirmação deve ser positivo." }
    }

    @Synchronized
    fun evaluate(capability: TinoCapabilityId, summary: String): HumanGateResult {
        return when (HumanGatePolicy.evaluate(capability)) {
            HumanGateDecision.ALLOW -> HumanGateResult.Allowed
            HumanGateDecision.DENY -> HumanGateResult.Denied("Capability não pode atravessar o gate HITL.")
            HumanGateDecision.CONFIRM -> {
                val now = clock.millis()
                val request = HumanGateRequest(
                    capability = capability,
                    summary = summary,
                    createdAtEpochMs = now,
                    expiresAtEpochMs = now + confirmationTtlMs,
                )
                pending[request.gateId] = request
                HumanGateResult.ConfirmationRequired(request)
            }
        }
    }

    @Synchronized
    fun confirm(gateId: String): HumanGateResult {
        val request = pending.remove(gateId) ?: return HumanGateResult.Denied("Confirmação inexistente ou já utilizada.")
        if (clock.millis() >= request.expiresAtEpochMs) {
            return HumanGateResult.Denied("A confirmação expirou.")
        }
        return HumanGateResult.Allowed
    }

    @Synchronized
    fun cancel(gateId: String) {
        pending.remove(gateId)
    }

    @Synchronized
    fun expire() {
        val now = clock.millis()
        pending.entries.removeIf { (_, request) -> now >= request.expiresAtEpochMs }
    }
}
