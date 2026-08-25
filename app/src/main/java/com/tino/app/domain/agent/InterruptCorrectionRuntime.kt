package com.tino.app.domain.agent

data class InteractionPatch(
    val updates: Map<String, String> = emptyMap(),
    val invalidateSlots: Set<String> = emptySet(),
    val expectedStateVersion: Long? = null,
)

/** Domain dependencies invalidated when a slot changes. */
object InteractionDependencyResolver {
    fun forSlot(slot: String): Set<String> = when (slot) {
        "product" -> setOf("product_id", "unit_price", "stock_snapshot")
        "customer" -> setOf("customer_id", "credit_balance")
        "quantity" -> setOf("total", "stock_validation")
        "amount_cents" -> setOf("total", "projected_balance")
        "payment_method",
        "paymentMethod",
        -> setOf("payment_authorization")
        "period" -> setOf("period_start", "period_end", "aggregates")
        else -> emptySet()
    }
}

sealed interface InteractionPatchResult {
    data class Applied(
        val changedSlots: Set<String>,
        val invalidatedSlots: Set<String>,
        val stateVersion: Long,
    ) : InteractionPatchResult

    data object NoPendingAction : InteractionPatchResult

    data class Rejected(
        val reason: RejectionReason,
        val stateVersion: Long,
    ) : InteractionPatchResult
}

enum class RejectionReason {
    ACTIVE_OPERATION,
    STALE_STATE,
    UNSUPPORTED_SLOT,
    EMPTY_PATCH,
}

/** Applies field-level corrections without restarting unrelated interaction state. */
class InterruptCorrectionRuntime(
    private val sharedState: TinoAgentSession,
) {
    private val supportedSlots = setOf(
        "quantity",
        "customer",
        "product",
        "period",
        "amount",
        "amount_cents",
        "payment_method",
        "paymentMethod",
        "value",
    )

    private fun canonicalSlot(slot: String): String = when (slot) {
        "payment_method" -> "paymentMethod"
        "amount_cents" -> "amount"
        else -> slot
    }

    fun apply(patch: InteractionPatch): InteractionPatchResult {
        val current = sharedState.snapshot.value
        val action = current.pendingAction ?: return InteractionPatchResult.NoPendingAction
        if (action.stage == PendingActionStage.EXECUTING) {
            return InteractionPatchResult.Rejected(
                reason = RejectionReason.ACTIVE_OPERATION,
                stateVersion = current.stateVersion,
            )
        }
        if (patch.expectedStateVersion != null && patch.expectedStateVersion != current.stateVersion) {
            return InteractionPatchResult.Rejected(
                reason = RejectionReason.STALE_STATE,
                stateVersion = current.stateVersion,
            )
        }
        if (patch.updates.isEmpty() && patch.invalidateSlots.isEmpty()) {
            return InteractionPatchResult.Rejected(
                reason = RejectionReason.EMPTY_PATCH,
                stateVersion = current.stateVersion,
            )
        }
        val requestedSlots = patch.updates.keys + patch.invalidateSlots
        if (requestedSlots.any { it !in supportedSlots }) {
            return InteractionPatchResult.Rejected(
                reason = RejectionReason.UNSUPPORTED_SLOT,
                stateVersion = current.stateVersion,
            )
        }
        val before = action.collectedSlots
        val resolvedInvalidations = (patch.invalidateSlots + patch.updates.keys.flatMap { slot ->
            InteractionDependencyResolver.forSlot(canonicalSlot(slot))
        }).map(::canonicalSlot).toSet()
        val updated = before
            .filterKeys { canonicalSlot(it) !in resolvedInvalidations }
            .toMutableMap()
            .apply {
                putAll(patch.updates)
            }
        val required = TinoCapabilityRegistry.require(action.capability).requiredSlots
        val missing = required.filterTo(mutableSetOf()) { requiredSlot ->
            updated.keys.none { canonicalSlot(it) == canonicalSlot(requiredSlot) }
        }
        val updatedAction = action.copy(
            stage = PendingActionStage.DRAFT,
            collectedSlots = updated,
            missingSlots = missing,
        )
        if (!sharedState.updateDraftIfVersion(current.stateVersion, updatedAction)) {
            return InteractionPatchResult.Rejected(
                reason = RejectionReason.STALE_STATE,
                stateVersion = sharedState.snapshot.value.stateVersion,
            )
        }
        return InteractionPatchResult.Applied(
            changedSlots = patch.updates.keys,
            invalidatedSlots = resolvedInvalidations,
            stateVersion = sharedState.snapshot.value.stateVersion,
        )
    }

    fun interrupt() {
        sharedState.cancel()
    }
}
