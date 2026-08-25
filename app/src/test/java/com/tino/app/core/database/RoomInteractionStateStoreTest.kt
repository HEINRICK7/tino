package com.tino.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.domain.agent.InteractionState
import com.tino.app.domain.agent.InteractionStatePersistencePolicy
import com.tino.app.domain.agent.PendingAgentAction
import com.tino.app.domain.agent.PendingClarification
import com.tino.app.domain.agent.SessionMemory
import com.tino.app.domain.agent.ScreenAgentContext
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.agent.WorkingMemory
import com.tino.app.domain.language.EntityReference
import com.tino.app.domain.language.LanguageEntityType
import com.tino.app.domain.language.TinoIntent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RoomInteractionStateStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val store = RoomInteractionStateStore(database.interactionStateDao())

    @After
    fun close() {
        database.close()
    }

    @Test
    fun roundTripRestoresPendingOperationAndPolicy() = runBlocking {
        val now = System.currentTimeMillis()
        val state = InteractionState(
            sessionId = "session-room",
            stateVersion = 7L,
            currentScreen = ScreenAgentContext("CUSTOMER_DETAIL", activeCustomerId = "maria-1"),
            pendingAction = PendingAgentAction(
                capability = TinoCapabilityId.ADD_CREDIT_ITEM,
                summary = "Maria · 2 cafés",
                requiresConfirmation = true,
                collectedSlots = mapOf("customer" to "Maria", "quantity" to "2"),
            ),
            updatedAtEpochMs = now,
            expiresAtEpochMs = now + 1_000L,
            persistencePolicy = InteractionStatePersistencePolicy.UNTIL_RESOLVED,
        )

        store.save(state)
        val restored = store.load(state.sessionId)

        assertEquals("CUSTOMER_DETAIL", restored?.currentScreen?.screen)
        assertEquals("maria-1", restored?.currentScreen?.activeCustomerId)
        assertEquals("2", restored?.pendingAction?.collectedSlots?.get("quantity"))
        assertEquals(InteractionStatePersistencePolicy.UNTIL_RESOLVED, restored?.persistencePolicy)
        assertEquals(7L, restored?.stateVersion)
    }

    @Test
    fun clearRemovesResolvedInteraction() = runBlocking {
        store.save(
            InteractionState(
                sessionId = "session-clear",
                currentScreen = ScreenAgentContext("HOME"),
                updatedAtEpochMs = 100L,
            ),
        )

        store.clear("session-clear")

        assertNull(store.load("session-clear"))
    }

    @Test
    fun roundTripRestoresWorkingAndSessionMemorySeparately() = runBlocking {
        val now = System.currentTimeMillis()
        val state = InteractionState(
            sessionId = "session-memory",
            currentScreen = ScreenAgentContext("CUSTOMER_DETAIL"),
            updatedAtEpochMs = now,
            expiresAtEpochMs = now + 10_000L,
            workingMemory = WorkingMemory(
                operationIntent = TinoIntent.ADD_CREDIT_ITEM,
                pendingClarification = PendingClarification(
                    entityType = "product",
                    slot = "product",
                    prompt = "Qual produto?",
                    options = listOf("Tradicional", "Extraforte"),
                ),
                updatedAtEpochMs = now,
                expiresAtEpochMs = now + 1_000L,
            ),
            sessionMemory = SessionMemory(
                currentScreen = ScreenAgentContext("CUSTOMER_DETAIL"),
                recentEntities = listOf(EntityReference(LanguageEntityType.CUSTOMER, "Maria")),
                lastObjective = TinoIntent.READ_CUSTOMER_BALANCE,
                activeSurfaceId = "customer-detail",
                turnCount = 2,
                updatedAtEpochMs = now,
                expiresAtEpochMs = now + 10_000L,
            ),
        )

        store.save(state)
        val restored = store.load(state.sessionId)

        assertEquals(TinoIntent.ADD_CREDIT_ITEM, restored?.workingMemory?.operationIntent)
        assertEquals(listOf("Tradicional", "Extraforte"), restored?.workingMemory?.pendingClarification?.options)
        assertEquals("Maria", restored?.sessionMemory?.recentEntities?.single()?.text)
        assertEquals("customer-detail", restored?.sessionMemory?.activeSurfaceId)
        assertEquals(2, restored?.sessionMemory?.turnCount)
    }

    @Test
    fun olderRevisionCannotOverwriteNewerPersistedInteraction() = runBlocking {
        val now = System.currentTimeMillis()
        val newer = InteractionState(
            sessionId = "session-versioned",
            stateVersion = 8L,
            currentScreen = ScreenAgentContext("HOME"),
            updatedAtEpochMs = now,
        )
        val older = newer.copy(stateVersion = 7L, currentScreen = ScreenAgentContext("CUSTOMERS"))

        store.save(newer)
        store.save(older)

        val restored = store.load(newer.sessionId)
        assertEquals(8L, restored?.stateVersion)
        assertEquals("HOME", restored?.currentScreen?.screen)
    }
}
