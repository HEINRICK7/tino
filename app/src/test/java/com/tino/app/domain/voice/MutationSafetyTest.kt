package com.tino.app.domain.voice

import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.domain.agent.TinoCapabilityId
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MutationSafetyTest {
    private val clock = MutableClock()
    private val safety = MutationSafetyCoordinator(clock)
    private val call = ToolCall(
        name = CommerceToolName.CHANGE_PRODUCT_PRICE,
        arguments = mapOf("product" to "Café Maratá", "new_price_cents" to "1090"),
    )

    @Test
    fun mutationCreatesAProposalAndRequiresExactConfirmation() = runBlocking {
        val prepared = safety.prepare(call, preview("R$ 8,00 → R$ 10,90"))

        assertTrue(prepared.operation.requiresConfirmation)
        assertEquals(OperationRisk.LOW_RISK_MUTATION, prepared.operation.risk)
        assertTrue(
            safety.authorize(call, prepared.confirmation, preview("R$ 8,00 → R$ 10,90")) is
                MutationAuthorization.Allowed,
        )
    }

    @Test
    fun wrongTokenIsDeniedBeforeCommit() = runBlocking {
        val prepared = safety.prepare(call, preview("same"))

        val result = safety.authorize(
            call,
            prepared.confirmation.copy(confirmationToken = "attacker-token"),
            preview("same"),
        )

        assertEquals("Token de confirmação inválido.", (result as MutationAuthorization.Denied).reason)
    }

    @Test
    fun stalePreviewIsInvalidated() = runBlocking {
        val prepared = safety.prepare(call, preview("old price"))

        val result = safety.authorize(call, prepared.confirmation, preview("new price"))

        assertEquals(
            "Os dados mudaram desde a prévia. Gere uma nova confirmação.",
            (result as MutationAuthorization.Denied).reason,
        )
    }

    @Test
    fun expiredPreviewCannotBeConfirmed() = runBlocking {
        val prepared = safety.prepare(call, preview("same"))
        clock.advance(5 * 60 * 1_000L)

        val result = safety.authorize(call, prepared.confirmation, preview("same"))

        assertEquals("A confirmação expirou. Gere uma nova prévia.", (result as MutationAuthorization.Denied).reason)
    }

    @Test
    fun doubleConfirmIsBlockedByIdempotency() = runBlocking {
        val prepared = safety.prepare(call, preview("same"))
        val authorization = safety.authorize(call, prepared.confirmation, preview("same")) as MutationAuthorization.Allowed

        safety.commit(authorization.operation)
        val replay = safety.authorize(call, prepared.confirmation, preview("same"))

        assertEquals("Operação repetida bloqueada por idempotência.", (replay as MutationAuthorization.Denied).reason)
    }

    @Test
    fun cancelRemovesPendingOperationWithoutCommit() = runBlocking {
        val prepared = safety.prepare(call, preview("same"))

        safety.cancel(prepared.confirmation)
        val result = safety.authorize(call, prepared.confirmation, preview("same"))

        assertEquals("Operação inexistente, expirada ou já concluída.", (result as MutationAuthorization.Denied).reason)
    }

    @Test
    fun cancellationCannotDeleteOperationAlreadyReservedForExecution() = runBlocking {
        val store = InMemoryMutationOperationStore()
        val guarded = MutationSafetyCoordinator(clock, store)
        val prepared = guarded.prepare(call, preview("same"))
        val allowed = guarded.authorize(call, prepared.confirmation, preview("same")) as MutationAuthorization.Allowed

        guarded.cancel(prepared.confirmation)
        guarded.commit(allowed.operation)

        assertEquals(MutationOperationStatus.COMMITTED, store.find(prepared.operation.operationId)?.status)
    }

    @Test
    fun hitlLifecycleProducesSafeAuditTrail() = runBlocking {
        val audit = RecordingAuditLogger()
        val guarded = MutationSafetyCoordinator(clock, InMemoryMutationOperationStore(), audit)
        val prepared = guarded.prepare(call, preview("same"))
        val allowed = guarded.authorize(call, prepared.confirmation, preview("same")) as MutationAuthorization.Allowed
        guarded.commit(allowed.operation)

        assertEquals(listOf("PREPARED", "AUTHORIZED", "COMMITTED"), audit.statuses)
        assertTrue(audit.capabilities.all { it == TinoCapabilityId.CHANGE_PRODUCT_PRICE.name })
    }

    @Test
    fun safeExecutorDoesNotAllowBooleanConfirmationToBypassGate() = runBlocking {
        val delegate = RecordingToolExecutor()
        val executor = MutationSafeToolExecutor(delegate, MutationSafetyCoordinator(clock))

        executor.preview(call)
        val thrown = runCatching { executor.execute(call, confirmed = true) }.exceptionOrNull()

        assertEquals("Mutation só pode ser executada por confirm(call, token).", thrown?.message)
        assertEquals(0, delegate.executed)
    }

    @Test
    fun safeExecutorCommitsOnceAndRejectsReplay() = runBlocking {
        val delegate = RecordingToolExecutor()
        val executor = MutationSafeToolExecutor(delegate, MutationSafetyCoordinator(clock))
        val prepared = executor.preview(call).preparedMutation!!

        executor.confirm(call, prepared.confirmation)
        val replay = runCatching { executor.confirm(call, prepared.confirmation) }.exceptionOrNull()

        assertTrue(replay?.message?.contains("repetida") == true)
        assertEquals(1, delegate.executed)
    }

    @Test
    fun concurrentConfirmationsReserveOnlyOnce() = runBlocking {
        val delegate = RecordingToolExecutor()
        val executor = MutationSafeToolExecutor(delegate, MutationSafetyCoordinator(clock))
        val prepared = executor.preview(call).preparedMutation!!

        val outcomes = listOf(
            async(Dispatchers.Default) { runCatching { executor.confirm(call, prepared.confirmation) }.isSuccess },
            async(Dispatchers.Default) { runCatching { executor.confirm(call, prepared.confirmation) }.isSuccess },
        ).awaitAll()

        assertEquals(listOf(true, false), outcomes.sortedDescending())
        assertEquals(1, delegate.executed)
    }

    @Test
    fun confirmationServiceRehydratesPersistedOperationThroughSafeExecutor() = runBlocking {
        val store = InMemoryMutationOperationStore()
        val safety = MutationSafetyCoordinator(clock, store)
        val delegate = RecordingToolExecutor()
        val executor = MutationSafeToolExecutor(delegate, safety)
        val prepared = executor.preview(call).preparedMutation!!
        val service = MutationConfirmationService(store, executor, safety)

        service.confirm(prepared.confirmation)

        assertEquals(1, delegate.executed)
        assertEquals(
            MutationOperationStatus.COMMITTED,
            store.find(prepared.operation.operationId)?.status,
        )
    }

    private fun preview(detail: String) = ToolPreview(
        title = "Alterar preço?",
        detail = detail,
        confirmLabel = "ALTERAR PREÇO",
    )

    private class RecordingToolExecutor : ToolExecutor {
        var executed = 0

        override suspend fun preview(call: ToolCall): ToolPreview = ToolPreview(
            title = "Alterar preço?",
            detail = "same",
            confirmLabel = "ALTERAR PREÇO",
        )

        override suspend fun execute(call: ToolCall, confirmed: Boolean): ToolExecutionResult {
            executed++
            return ToolExecutionResult("ok")
        }
    }

    private class RecordingAuditLogger : AuditLogger {
        val statuses = mutableListOf<String>()
        val capabilities = mutableListOf<String>()

        override fun record(type: AuditEventType, metadata: Map<String, String>) {
            if (type == AuditEventType.CONFIRMATION) {
                metadata["status"]?.let(statuses::add)
                metadata["capability"]?.let(capabilities::add)
            }
        }
    }

    private class MutableClock(
        private var currentMillis: Long = 1_000_000L,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(currentMillis)

        fun advance(millis: Long) {
            currentMillis += millis
        }
    }
}
