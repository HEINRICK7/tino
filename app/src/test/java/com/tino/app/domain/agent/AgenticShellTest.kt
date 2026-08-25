package com.tino.app.domain.agent

import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.tino.app.domain.language.EntityReference
import com.tino.app.domain.language.LanguageEntityType
import com.tino.app.domain.language.TinoIntent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class AgenticShellTest {
    @Test
    fun sharedStatePublishesRedactedVersionedTransitionsForUiAndRuntime() {
        val audit = RecordingAuditLogger()
        val session = TinoAgentSession(InMemoryInteractionStateStore(), audit)

        session.beginListening()
        session.beginUnderstanding()
        session.cancel()

        val versions = audit.events
            .filter { it.first == AuditEventType.AGENT_STATE_CHANGED }
            .map { it.second.getValue("state_version").toLong() }

        assertEquals(listOf(1L, 2L, 3L), versions)
        assertEquals(AgentVoiceState.IDLE, session.snapshot.value.voiceState)
        assertEquals(3L, session.snapshot.value.stateVersion)
        assertTrue(audit.events.all { (_, metadata) -> metadata.keys.none { it == "transcript" || it == "result" } })
    }

    @Test
    fun sessionKeepsScreenContextAndPendingActionAcrossGlobalVoiceFlow() {
        val session = TinoAgentSession()

        session.enterScreen(
            ScreenAgentContext(
                screen = "CUSTOMER_DETAIL",
                activeCustomerId = "maria-1",
                tags = setOf("customer", "credit"),
                primaryEntity = EntityReference(LanguageEntityType.CUSTOMER, "Maria Lina"),
            ),
        )
        session.beginListening()
        session.beginUnderstanding()
        session.readyToConfirm(
            PendingAgentAction(
                capability = TinoCapabilityId.RECEIVE_CREDIT_PAYMENT,
                summary = "Maria Lina · R$ 20,00 · Pix",
                requiresConfirmation = true,
                intent = TinoIntent.RECEIVE_CREDIT_PAYMENT,
                collectedSlots = mapOf("customer" to "Maria Lina", "amount" to "2000"),
                missingSlots = setOf("paymentMethod"),
            ),
        )

        val snapshot = session.snapshot.value
        assertEquals("CUSTOMER_DETAIL", snapshot.screenContext.screen)
        assertEquals("maria-1", snapshot.activeCustomerId)
        assertEquals(AgentVoiceState.READY_TO_CONFIRM, snapshot.voiceState)
        assertEquals(TinoCapabilityId.RECEIVE_CREDIT_PAYMENT, snapshot.recentCapability)
        assertTrue(snapshot.pendingAction?.requiresConfirmation == true)
        assertEquals("2000", snapshot.collectedSlots["amount"])
        assertEquals(setOf("paymentMethod"), snapshot.missingSlots)
        assertEquals("Maria Lina", snapshot.screenContext.primaryEntity?.text)

        session.cancel()

        assertEquals(AgentVoiceState.IDLE, session.snapshot.value.voiceState)
        assertEquals(null, session.snapshot.value.pendingAction)
        assertEquals("maria-1", session.snapshot.value.activeCustomerId)
    }

    @Test
    fun capabilityRegistrySeparatesRiskPresentationAndOfflineSupport() {
        val readStock = TinoCapabilityRegistry.require(TinoCapabilityId.READ_STOCK)
        val payment = TinoCapabilityRegistry.require(TinoCapabilityId.RECEIVE_CREDIT_PAYMENT)
        val navigate = TinoCapabilityRegistry.require(TinoCapabilityId.NAVIGATE)

        assertEquals(TinoCapabilityRisk.LOW, readStock.risk)
        assertEquals(TinoPresentationMode.OVERLAY, readStock.presentation)
        assertTrue(readStock.offline)
        assertEquals(TinoCapabilityRisk.HIGH, payment.risk)
        assertEquals(TinoPresentationMode.BOTTOM_SHEET, payment.presentation)
        assertEquals(TinoPresentationMode.NAVIGATE, navigate.presentation)
        assertTrue(TinoCapabilityRegistry.isAvailableOffline(TinoCapabilityId.ADD_CREDIT_ITEM))
    }

    @Test
    fun screenContextRegistryKeepsContextBySurface() {
        val registry = ScreenContextRegistry()
        val context = ScreenAgentContext(
            screen = "PRODUCT_DETAIL",
            primaryEntity = EntityReference(LanguageEntityType.PRODUCT, "Café Maratá"),
        )

        registry.register(context)

        assertEquals(context, registry.contextFor("PRODUCT_DETAIL"))
        assertEquals(null, registry.contextFor("CUSTOMER_DETAIL"))
    }

    @Test
    fun sharedStateRevisionIsMonotonicAndSurvivesConcurrentMutations() {
        val session = TinoAgentSession()
        val workers = 8
        val mutationsPerWorker = 25
        val latch = CountDownLatch(workers)
        val executor = Executors.newFixedThreadPool(workers)

        repeat(workers) { worker ->
            executor.execute {
                repeat(mutationsPerWorker) { mutation ->
                    session.rememberSurface("worker-$worker-$mutation")
                }
                latch.countDown()
            }
        }

        latch.await()
        executor.shutdown()

        val sharedState: SharedAgentState = session
        assertEquals((workers * mutationsPerWorker).toLong(), sharedState.snapshot.value.stateVersion)
    }

    @Test
    fun previewTransitionIsPublishedThroughTheSharedStateFlow() {
        val session = TinoAgentSession()
        val action = PendingAgentAction(
            capability = TinoCapabilityId.ADD_CREDIT_ITEM,
            summary = "2 cafés",
            requiresConfirmation = true,
            stage = PendingActionStage.DRAFT,
        )

        session.markPreviewReady(action)

        assertEquals(AgentVoiceState.PREVIEW_READY, session.snapshot.value.voiceState)
        assertEquals(PendingActionStage.PREVIEW_READY, session.snapshot.value.pendingAction?.stage)
        assertTrue(session.snapshot.value.stateVersion >= 2L)
    }

    private class RecordingAuditLogger : AuditLogger {
        val events = mutableListOf<Pair<AuditEventType, Map<String, String>>>()

        override fun record(type: AuditEventType, metadata: Map<String, String>) {
            events += type to metadata
        }
    }
}
