package com.tino.app.domain.agent

import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class AgentRuntimeModulesTest {
    @Test
    fun progressRuntimeRejectsConcurrentRunsAndAuditsEveryTransition() {
        val audit = RecordingAuditLogger()
        val runtime = AgentProgressRuntime(clock = { 100L }, auditLogger = audit)

        runtime.start("run-active", "exec-active")
        try {
            runtime.start("run-overlap", "exec-overlap")
            fail("uma segunda execução não pode substituir uma execução ativa")
        } catch (_: IllegalStateException) {
            // expected
        }

        runtime.toolStarted("list_products")
        runtime.toolCompleted("list_products", succeeded = true)
        runtime.complete()

        assertEquals(listOf("RunStarted", "ToolStarted", "ToolCompleted", "RunCompleted"), audit.progressEvents)
        assertEquals(AgentProgressTerminalState.COMPLETED, runtime.snapshot.value.terminalState)
        assertFalse(audit.progressEvents.any { it == "RunFailed" })
    }

    @Test
    fun progressRuntimePublishesOrderedTerminalLifecycle() {
        val runtime = AgentProgressRuntime(clock = { 100L })

        runtime.start("run-1", "exec-1")
        runtime.capabilityStarted(TinoCapabilityId.LIST_PRODUCTS)
        runtime.toolStarted("list_products")
        runtime.toolProgress("list_products", "Consultando", 0.5f)
        runtime.toolCompleted("list_products", true)
        runtime.complete()

        assertEquals(AgentProgressTerminalState.COMPLETED, runtime.snapshot.value.terminalState)
        assertEquals(6L, runtime.snapshot.value.sequence)
        assertTrue(runtime.snapshot.value.lastEvent is AgentProgressEvent.RunCompleted)
    }

    @Test
    fun streamingRuntimeKeepsSequenceAndRejectsEventsAfterTerminal() = runBlocking {
        val runtime = AgentStreamingRuntime(clock = { 200L })
        val first = runtime.emit("run-2", AgentStreamEventType.TRANSCRIPT_COMMITTED)
        val second = runtime.emit("run-2", AgentStreamEventType.AGENT_STARTED)
        val terminal = runtime.close("run-2", AgentStreamEventType.COMPLETED)

        assertEquals(1L, first.sequence)
        assertEquals(2L, second.sequence)
        assertEquals(3L, terminal.sequence)
        var rejected = false
        try {
            runtime.emit("run-2", AgentStreamEventType.A2UI_UPDATED)
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun streamingRuntimeProvidesRecoverySnapshotAndRejectsConcurrentRuns() = runBlocking {
        val runtime = AgentStreamingRuntime(clock = { 300L })
        runtime.emit("run-stream", AgentStreamEventType.SPEECH, payloadVersion = 2)

        assertEquals("run-stream", runtime.activeRunIdOrNull())
        try {
            runtime.emit("other-stream", AgentStreamEventType.AGENT_STARTED)
            fail("streams concorrentes não podem disputar o mesmo runtime")
        } catch (_: IllegalStateException) {
            // expected
        }

        runtime.emit("run-stream", AgentStreamEventType.TRANSCRIPT_PARTIAL, mapOf("text" to "clientes"))
        runtime.close("run-stream", AgentStreamEventType.COMPLETED)

        assertEquals(AgentStreamTerminalState.COMPLETED, runtime.snapshot.value.terminalState)
        assertEquals(3L, runtime.snapshot.value.sequence)
        assertEquals(null, runtime.activeRunIdOrNull())
        assertEquals(
            listOf(AgentStreamEventType.SPEECH, AgentStreamEventType.TRANSCRIPT_PARTIAL),
            runtime.events.take(2).toList().map { it.type },
        )
    }

    @Test
    fun hitlRequiresConfirmationForMutationsAndBlocksReplay() {
        val runtime = HumanGateRuntime()
        val pending = runtime.evaluate(TinoCapabilityId.ADD_CREDIT_ITEM, "Adicionar 2 cafés")
        assertTrue(pending is HumanGateResult.ConfirmationRequired)
        val gateId = (pending as HumanGateResult.ConfirmationRequired).request.gateId

        assertEquals(HumanGateResult.Allowed, runtime.confirm(gateId))
        assertTrue(runtime.confirm(gateId) is HumanGateResult.Denied)
        assertEquals(HumanGateResult.Allowed, runtime.evaluate(TinoCapabilityId.LIST_PRODUCTS, "Listar produtos"))
        assertEquals(HumanGateDecision.CONFIRM, HumanGatePolicy.evaluate(TinoCapabilityId.ADD_CREDIT_ITEM))
        assertEquals(HumanGateDecision.ALLOW, HumanGatePolicy.evaluate(TinoCapabilityId.LIST_PRODUCTS))
    }

    @Test
    fun ephemeralCapabilityIsAvailableForOneAttemptAndClearedOnFailure() {
        val session = TinoAgentSession()
        session.enterScreen(
            ScreenAgentContext(
                screen = "home",
                availableCapabilities = setOf(TinoCapabilityId.LIST_CUSTOMERS),
            ),
        )

        session.grantEphemeralCapability(TinoCapabilityId.LIST_PRODUCTS)

        assertTrue(TinoCapabilityId.LIST_PRODUCTS in session.availableCapabilities())
        session.markFailed()
        assertFalse(TinoCapabilityId.LIST_PRODUCTS in session.availableCapabilities())
        assertTrue(TinoCapabilityId.LIST_CUSTOMERS in session.availableCapabilities())
    }

    @Test
    fun correctionPatchPreservesIndependentSlotsAndInterruptClearsPendingAction() {
        val session = TinoAgentSession()
        session.updateDraft(
            PendingAgentAction(
                capability = TinoCapabilityId.ADD_CREDIT_ITEM,
                summary = "Venda",
                requiresConfirmation = true,
                collectedSlots = mapOf("customer" to "Maria", "product" to "Café", "quantity" to "2"),
            ),
        )
        val runtime = InterruptCorrectionRuntime(session)

        val result = runtime.apply(InteractionPatch(updates = mapOf("quantity" to "3")))
        assertTrue(result is InteractionPatchResult.Applied)
        assertEquals("Maria", session.snapshot.value.collectedSlots["customer"])
        assertEquals("3", session.snapshot.value.collectedSlots["quantity"])

        runtime.interrupt()
        assertEquals(null, session.snapshot.value.pendingAction)
    }

    @Test
    fun correctionPatchInvalidatesOnlyDerivedFields() {
        val session = TinoAgentSession()
        session.updateDraft(
            PendingAgentAction(
                capability = TinoCapabilityId.ADD_CREDIT_ITEM,
                summary = "Venda",
                requiresConfirmation = true,
                collectedSlots = mapOf(
                    "customer" to "Maria",
                    "product" to "Café",
                    "quantity" to "2",
                    "unrelated_note" to "balcão",
                ),
            ),
        )

        val result = InterruptCorrectionRuntime(session).apply(
            InteractionPatch(updates = mapOf("product" to "Leite")),
        ) as InteractionPatchResult.Applied

        assertTrue("unit_price" in result.invalidatedSlots)
        assertTrue("stock_snapshot" in result.invalidatedSlots)
        assertEquals("Leite", session.snapshot.value.collectedSlots["product"])
        assertEquals("Maria", session.snapshot.value.collectedSlots["customer"])
        assertEquals("2", session.snapshot.value.collectedSlots["quantity"])
        assertEquals("balcão", session.snapshot.value.collectedSlots["unrelated_note"])
    }

    @Test
    fun correctionPatchCannotOverwriteAnActiveOperation() {
        val session = TinoAgentSession()
        session.updateDraft(
            PendingAgentAction(
                capability = TinoCapabilityId.ADD_CREDIT_ITEM,
                summary = "Venda",
                requiresConfirmation = true,
                collectedSlots = mapOf("customer" to "Maria", "product" to "Café", "quantity" to "2"),
                stage = PendingActionStage.EXECUTING,
            ),
        )
        val before = session.snapshot.value

        val result = InterruptCorrectionRuntime(session).apply(
            InteractionPatch(updates = mapOf("quantity" to "3"), expectedStateVersion = before.stateVersion),
        )

        assertEquals(
            InteractionPatchResult.Rejected(RejectionReason.ACTIVE_OPERATION, before.stateVersion),
            result,
        )
        assertEquals(before, session.snapshot.value)
    }

    @Test
    fun correctionPatchUsesCanonicalAliasesAndRejectsStaleState() {
        val session = TinoAgentSession()
        session.updateDraft(
            PendingAgentAction(
                capability = TinoCapabilityId.RECEIVE_CREDIT_PAYMENT,
                summary = "Recebimento",
                requiresConfirmation = true,
                collectedSlots = mapOf("customer" to "Maria", "amount_cents" to "2500", "paymentMethod" to "pix"),
            ),
        )
        val runtime = InterruptCorrectionRuntime(session)
        val version = session.snapshot.value.stateVersion
        session.rememberResult("turn mais novo")

        val stale = runtime.apply(
            InteractionPatch(
                updates = mapOf("payment_method" to "cash"),
                expectedStateVersion = version,
            ),
        )
        assertEquals(RejectionReason.STALE_STATE, (stale as InteractionPatchResult.Rejected).reason)

        val applied = runtime.apply(InteractionPatch(updates = mapOf("payment_method" to "cash")))
        assertTrue(applied is InteractionPatchResult.Applied)
        assertEquals("cash", session.snapshot.value.collectedSlots["payment_method"])
        assertTrue(session.snapshot.value.pendingAction?.missingSlots?.isEmpty() == true)
    }

    @Test
    fun a2uiHostRejectsOlderIncrementalSurfacePatch() {
        val host = com.tino.app.interfaceadapter.a2ui.A2uiSurfaceHost()
        val component = com.tino.app.interfaceadapter.a2ui.A2uiSurfaceComponent(
            "primary",
            com.tino.app.interfaceadapter.a2ui.TinoA2UiComponentCatalog.INSIGHT_CARD,
        )
        val create = com.tino.app.interfaceadapter.a2ui.A2uiSurfaceMessage(
            "m1", "surface", com.tino.app.interfaceadapter.a2ui.A2uiSurfaceOperation.CREATE_SURFACE,
            components = listOf(component), sequence = 2L,
        )
        val update = create.copy(messageId = "m2", sequence = 3L, dataModel = mapOf("answer" to "novo"))
            .copy(operation = com.tino.app.interfaceadapter.a2ui.A2uiSurfaceOperation.UPDATE_DATA_MODEL, components = emptyList())
        val old = update.copy(messageId = "m3", sequence = 1L, dataModel = mapOf("answer" to "antigo"))

        assertTrue(host.apply(create) is com.tino.app.interfaceadapter.a2ui.A2uiSurfaceApplyResult.Applied)
        assertTrue(host.apply(update) is com.tino.app.interfaceadapter.a2ui.A2uiSurfaceApplyResult.Applied)
        assertTrue(host.apply(old) is com.tino.app.interfaceadapter.a2ui.A2uiSurfaceApplyResult.Rejected)
        assertEquals("novo", host.snapshot("surface")?.dataModel?.get("answer"))
    }

    @Test
    fun presenceProjectsWaitingAndProgressFromRuntimeSignals() {
        val progress = AgentProgressRuntime()
        progress.start("run-3", "exec-3")
        progress.waitingForUser("Confirme a operação")
        val waiting = TinoPresenceResolver.resolve(TinoAgentSessionSnapshot(), progress.snapshot.value)
        assertEquals(TinoPresenceMode.WAITING_FOR_USER, waiting.mode)

        val listening = TinoPresenceResolver.resolve(
            TinoAgentSessionSnapshot(voiceState = AgentVoiceState.LISTENING),
        )
        assertEquals(TinoPresenceMode.LISTENING, listening.mode)
    }

    @Test
    fun presencePrefersCurrentVoiceOverStaleTerminalProgress() {
        val progress = AgentProgressRuntime()
        progress.start("old-run", "old-exec")
        progress.complete()

        val currentListening = TinoPresenceResolver.resolve(
            TinoAgentSessionSnapshot(voiceState = AgentVoiceState.LISTENING),
            progress.snapshot.value,
        )

        assertEquals(TinoPresenceMode.LISTENING, currentListening.mode)
    }

    @Test
    fun presenceShowsThinkingWhenProgressIsActiveEvenBeforeVoiceStateCatchesUp() {
        val progress = AgentProgressRuntime()
        progress.start("run-thinking", "exec-thinking")

        val thinking = TinoPresenceResolver.resolve(
            TinoAgentSessionSnapshot(),
            progress.snapshot.value,
        )

        assertEquals(TinoPresenceMode.THINKING, thinking.mode)
    }

    @Test
    fun presenceProjectsSharedConfirmationStateAsWaiting() {
        val pending = PendingAgentAction(
            capability = TinoCapabilityId.ADD_CREDIT_ITEM,
            summary = "Confirme o lançamento",
            requiresConfirmation = true,
        )
        val waiting = TinoPresenceResolver.resolve(
            TinoAgentSessionSnapshot(
                pendingAction = pending,
                confirmationState = ConfirmationState.REQUIRED,
            ),
        )

        assertEquals(TinoPresenceMode.WAITING_FOR_USER, waiting.mode)
        assertEquals("Confirme o lançamento", waiting.message)
    }

    @Test
    fun fullIntegrationCompletesReadOnlyFlowAndRecoversTimeout() = runBlocking {
        val progress = AgentProgressRuntime()
        val streaming = AgentStreamingRuntime()
        val session = TinoAgentSession()
        val integration = FullAgentRuntimeIntegration(session, progress, streaming)

        val completed = integration.execute(
            FullRuntimeRequest("run-4", "exec-4", TinoCapabilityId.LIST_PRODUCTS),
        ) { "produtos" }
        assertEquals(FullRuntimeResult.Completed("produtos"), completed)
        assertEquals(AgentProgressTerminalState.COMPLETED, progress.snapshot.value.terminalState)
        assertEquals(
            listOf(
                AgentStreamEventType.AGENT_STARTED,
                AgentStreamEventType.STATE_CHANGED,
                AgentStreamEventType.TOOL_STARTED,
                AgentStreamEventType.TOOL_COMPLETED,
                AgentStreamEventType.A2UI_UPDATED,
                AgentStreamEventType.COMPLETED,
            ),
            streaming.events.replayCache.map { it.type },
        )

        val timedOut = integration.execute(
            FullRuntimeRequest("run-5", "exec-5", TinoCapabilityId.LIST_PRODUCTS, timeoutMs = 10L),
        ) {
            delay(100L)
            "nunca"
        }
        assertTrue(timedOut is FullRuntimeResult.Failed && timedOut.timedOut)
        assertEquals(AgentStreamTerminalState.FAILED, streaming.snapshot.value.terminalState)
        assertEquals(AgentVoiceState.FAILED, session.snapshot.value.voiceState)
    }

    @Test
    fun fullIntegrationFailureProducesRecoverableTerminalLifecycle() = runBlocking {
        val progress = AgentProgressRuntime()
        val streaming = AgentStreamingRuntime()
        val session = TinoAgentSession()
        val integration = FullAgentRuntimeIntegration(session, progress, streaming)

        val failed = integration.execute(
            FullRuntimeRequest("run-failure", "exec-failure", TinoCapabilityId.LIST_PRODUCTS),
        ) { error("Room indisponível") }

        assertEquals(FullRuntimeResult.Failed("Room indisponível"), failed)
        assertEquals(AgentVoiceState.FAILED, session.snapshot.value.voiceState)
        assertEquals(AgentProgressTerminalState.FAILED, progress.snapshot.value.terminalState)
        assertEquals(AgentStreamTerminalState.FAILED, streaming.snapshot.value.terminalState)
        assertEquals(AgentStreamEventType.A2UI_UPDATED, streaming.events.replayCache.last { it.type == AgentStreamEventType.A2UI_UPDATED }.type)
    }

    @Test
    fun fullIntegrationCarriesCorrectionIntoRecomputedOperation() = runBlocking {
        val session = TinoAgentSession()
        session.updateDraft(
            PendingAgentAction(
                capability = TinoCapabilityId.ADD_CREDIT_ITEM,
                summary = "2 unidades de Café para Maria",
                requiresConfirmation = true,
                collectedSlots = mapOf("customer" to "Maria", "product" to "Café", "quantity" to "2"),
                stage = PendingActionStage.PREVIEW_READY,
            ),
        )
        val patch = InterruptCorrectionRuntime(session).apply(
            InteractionPatch(updates = mapOf("quantity" to "3")),
        )
        assertTrue(patch is InteractionPatchResult.Applied)
        assertEquals("3", session.snapshot.value.pendingAction?.collectedSlots?.get("quantity"))
        assertEquals(PendingActionStage.DRAFT, session.snapshot.value.pendingAction?.stage)

        val progress = AgentProgressRuntime()
        val streaming = AgentStreamingRuntime()
        val integration = FullAgentRuntimeIntegration(session, progress, streaming)
        val recomputed = integration.execute(
            FullRuntimeRequest("run-correction", "exec-correction", TinoCapabilityId.ADD_CREDIT_ITEM),
        ) { "preview atualizado para 3 unidades" }

        assertEquals(FullRuntimeResult.Completed("preview atualizado para 3 unidades"), recomputed)
        assertEquals(AgentVoiceState.SUCCESS, session.snapshot.value.voiceState)
    }

    @Test
    fun fullIntegrationStopsMutationUntilHumanGateIsApproved() = runBlocking {
        val progress = AgentProgressRuntime()
        val streaming = AgentStreamingRuntime()
        val session = TinoAgentSession()
        val integration = FullAgentRuntimeIntegration(session, progress, streaming)
        val gateRuntime = HumanGateRuntime()
        val requested = gateRuntime.evaluate(TinoCapabilityId.ADD_CREDIT_ITEM, "Adicionar 2 cafés")
        val request = FullRuntimeRequest("run-hitl", "exec-hitl", TinoCapabilityId.ADD_CREDIT_ITEM, humanGate = requested)

        val waiting = integration.execute(request) { error("não pode executar antes da confirmação") }

        assertTrue(waiting is FullRuntimeResult.WaitingForUser)
        assertEquals(AgentVoiceState.NEEDS_CLARIFICATION, session.snapshot.value.voiceState)
        assertEquals(AgentProgressTerminalState.WAITING_FOR_USER, progress.snapshot.value.terminalState)
        assertEquals(AgentStreamTerminalState.ACTIVE, streaming.snapshot.value.terminalState)

        val gateId = (requested as HumanGateResult.ConfirmationRequired).request.gateId
        assertEquals(HumanGateResult.Allowed, gateRuntime.confirm(gateId))
        val completed = integration.execute(
            request.copy(humanGate = HumanGateResult.Allowed),
        ) { "mutação concluída" }

        assertEquals(FullRuntimeResult.Completed("mutação concluída"), completed)
        assertEquals(AgentProgressTerminalState.COMPLETED, progress.snapshot.value.terminalState)
        assertEquals(AgentStreamTerminalState.COMPLETED, streaming.snapshot.value.terminalState)
    }

    @Test
    fun fullIntegrationCancellationClearsSharedStateAndClosesRuntime() = runBlocking {
        val progress = AgentProgressRuntime()
        val streaming = AgentStreamingRuntime()
        val session = TinoAgentSession()
        val integration = FullAgentRuntimeIntegration(session, progress, streaming)

        val running = async {
            integration.execute(
                FullRuntimeRequest("run-cancel", "exec-cancel", TinoCapabilityId.LIST_PRODUCTS),
            ) {
                delay(500L)
                "late"
            }
        }
        delay(40L)
        running.cancelAndJoin()

        assertEquals(AgentVoiceState.IDLE, session.snapshot.value.voiceState)
        assertEquals(AgentProgressTerminalState.CANCELLED, progress.snapshot.value.terminalState)
        assertEquals(AgentStreamTerminalState.CANCELLED, streaming.snapshot.value.terminalState)
        assertTrue(streaming.events.replayCache.any { it.type == AgentStreamEventType.A2UI_UPDATED })
    }

    private class RecordingAuditLogger : AuditLogger {
        val progressEvents = mutableListOf<String>()

        override fun record(type: AuditEventType, metadata: Map<String, String>) {
            if (type == AuditEventType.AGENT_PROGRESS) {
                metadata["progress_event"]?.let(progressEvents::add)
            }
        }
    }
}
