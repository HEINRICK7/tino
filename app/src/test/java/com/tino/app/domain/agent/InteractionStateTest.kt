package com.tino.app.domain.agent

import com.tino.app.domain.language.EntityReference
import com.tino.app.domain.language.LanguageEntityType
import com.tino.app.domain.language.TinoIntent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionStateTest {
    @Test
    fun goldenFlowPreservesCustomerProductAndQuantityUntilConfirmation() {
        val session = TinoAgentSession()
        session.enterScreen(
            ScreenAgentContext(
                screen = "CUSTOMER_DETAIL",
                activeCustomerId = "maria-1",
                primaryEntity = EntityReference(LanguageEntityType.CUSTOMER, "Maria José"),
            ),
        )
        session.readyToConfirm(
            PendingAgentAction(
                capability = TinoCapabilityId.ADD_CREDIT_ITEM,
                summary = "Maria José · 2 Café Maratá",
                requiresConfirmation = true,
                intent = TinoIntent.ADD_CREDIT_ITEM,
                collectedSlots = mapOf(
                    "customer" to "Maria José",
                    "product" to "Café Maratá",
                    "quantity" to "2",
                ),
                missingSlots = emptySet(),
            ),
        )

        val snapshot = session.snapshot.value
        assertEquals("Maria José", snapshot.pendingAction?.collectedSlots?.get("customer"))
        assertEquals("Café Maratá", snapshot.pendingAction?.collectedSlots?.get("product"))
        assertEquals("2", snapshot.pendingAction?.collectedSlots?.get("quantity"))
        assertEquals(AgentVoiceState.READY_TO_CONFIRM, snapshot.voiceState)
        assertEquals(InteractionStatePersistencePolicy.UNTIL_RESOLVED,
            InteractionState.fromSnapshot("default", snapshot, 100L).persistencePolicy)

        session.cancel()
        assertNull(session.snapshot.value.pendingAction)
        assertEquals("CUSTOMER_DETAIL", session.snapshot.value.screenContext.screen)
    }

    @Test
    fun expiredPendingStateIsRemovedWithoutRemovingTheScreenAnchor() = runBlocking {
        val store = InMemoryInteractionStateStore()
        val state = InteractionState(
            sessionId = "session-1",
            currentScreen = ScreenAgentContext("CUSTOMER_DETAIL"),
            pendingAction = PendingAgentAction(
                capability = TinoCapabilityId.ADD_CREDIT_ITEM,
                summary = "rascunho",
                requiresConfirmation = true,
            ),
            updatedAtEpochMs = 100L,
            expiresAtEpochMs = 200L,
            persistencePolicy = InteractionStatePersistencePolicy.UNTIL_RESOLVED,
        )
        store.save(state)

        assertEquals(1, store.expire(201L))
        assertNull(store.load("session-1"))
        assertTrue(state.currentScreen.screen == "CUSTOMER_DETAIL")
    }

    @Test
    fun workingAndSessionMemoryHaveIndependentResponsibilitiesAndTtls() {
        val working = WorkingMemory(
            operationIntent = TinoIntent.ADD_CREDIT_ITEM,
            pendingClarification = PendingClarification(
                entityType = "product",
                slot = "product",
                prompt = "Qual Café Maratá?",
                options = listOf("Tradicional", "Extraforte"),
            ),
            updatedAtEpochMs = 100L,
            expiresAtEpochMs = 200L,
        )
        val sessionMemory = SessionMemory(
            currentScreen = ScreenAgentContext("CUSTOMER_DETAIL"),
            recentEntities = listOf(EntityReference(LanguageEntityType.CUSTOMER, "Maria")),
            lastObjective = TinoIntent.READ_CUSTOMER_BALANCE,
            activeSurfaceId = "receivables",
            turnCount = 2,
            updatedAtEpochMs = 100L,
            expiresAtEpochMs = 1_000L,
        )

        assertTrue(working.isExpired(201L))
        assertTrue(!sessionMemory.isExpired(201L))
        assertEquals("product", working.pendingClarification?.entityType)
        assertEquals("Maria", sessionMemory.recentEntities.single().text)

        val snapshot = InteractionState(
            sessionId = "memory-session",
            currentScreen = ScreenAgentContext("CUSTOMER_DETAIL"),
            updatedAtEpochMs = 100L,
            workingMemory = working,
            sessionMemory = sessionMemory,
        ).toSnapshot(nowEpochMs = 201L)

        assertEquals(null, snapshot.workingMemory.pendingClarification)
        assertEquals("receivables", snapshot.sessionMemory.activeSurfaceId)
        assertEquals("CUSTOMER_DETAIL", snapshot.screenContext.screen)
    }

    @Test
    fun clarificationCanBeClearedWithoutDroppingSessionContext() {
        val session = TinoAgentSession()
        session.enterScreen(
            ScreenAgentContext(
                screen = "CUSTOMER_DETAIL",
                primaryEntity = EntityReference(LanguageEntityType.CUSTOMER, "Maria"),
            ),
        )
        session.rememberSurface("customer-detail")
        session.rememberClarification(
            PendingClarification("customer", "customer", "Qual Maria?", listOf("Maria Lina")),
        )

        session.clearClarification()

        assertEquals(null, session.snapshot.value.workingMemory.pendingClarification)
        assertEquals("customer-detail", session.snapshot.value.sessionMemory.activeSurfaceId)
        assertEquals("Maria", session.snapshot.value.sessionMemory.recentEntities.first().text)
    }

    @Test
    fun restoringSnapshotProjectsSessionMemoryBackToLegacyContextFields() {
        val state = InteractionState(
            sessionId = "restore-session",
            stateVersion = 11L,
            currentScreen = ScreenAgentContext("OLD_SCREEN"),
            updatedAtEpochMs = 100L,
            sessionMemory = SessionMemory(
                currentScreen = ScreenAgentContext("CUSTOMER_DETAIL"),
                recentEntities = listOf(EntityReference(LanguageEntityType.CUSTOMER, "Maria")),
                lastObjective = TinoIntent.READ_CUSTOMER_BALANCE,
                lastResultSummary = "grounded-result-reference-only",
                expiresAtEpochMs = 10_000L,
            ),
        )

        val restored = state.toSnapshot(nowEpochMs = 200L)

        assertEquals("CUSTOMER_DETAIL", restored.screenContext.screen)
        assertEquals("Maria", restored.recentEntities.single().text)
        assertEquals(TinoIntent.READ_CUSTOMER_BALANCE, restored.recentIntent)
        assertEquals("grounded-result-reference-only", restored.lastAgentResult)
        assertEquals(11L, restored.stateVersion)
    }
}
