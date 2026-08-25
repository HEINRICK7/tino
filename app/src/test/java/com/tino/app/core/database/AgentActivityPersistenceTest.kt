package com.tino.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.domain.agent.AgentActivityLedger
import com.tino.app.domain.agent.AgentActivitySource
import com.tino.app.domain.agent.AgentActivitySummary
import com.tino.app.domain.agent.AgentUndoEligibility
import com.tino.app.domain.agent.AgentUndoPolicy
import com.tino.app.domain.agent.AgentUndoState
import com.tino.app.domain.agent.TinoCapabilityId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class AgentActivityPersistenceTest {
    private val databaseName = "agent-activity-persistence-test.db"
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: TinoDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun activitySurvivesLedgerAndDatabaseRecreation() = runBlocking {
        database = openDatabase()
        val firstLedger = AgentActivityLedger(RoomAgentActivityRepository(database!!.agentActivityDao()))
        val entry = firstLedger.record(
            capability = TinoCapabilityId.RECEIVE_CREDIT_PAYMENT,
            summary = "Pagamento recebido",
            source = AgentActivitySource.VOICE,
            operationId = "payment-persisted",
            undo = AgentUndoEligibility(
                policy = AgentUndoPolicy.COMPENSATING_OPERATION,
                compensatingCapability = TinoCapabilityId.REVERSE_CREDIT_PAYMENT,
            ),
            summaryData = AgentActivitySummary.CreditPayment("Maria", 5_000, "pix"),
            occurredAtEpochMs = 100L,
        )
        firstLedger.awaitPersistence()
        database!!.close()

        database = openDatabase()
        val restoredLedger = AgentActivityLedger(RoomAgentActivityRepository(database!!.agentActivityDao()))
        restoredLedger.awaitPersistence()

        assertEquals(entry.id, restoredLedger.entries.value.single().id)
        assertEquals("payment-persisted", restoredLedger.entries.value.single().operationId)
        assertEquals(AgentActivitySummary.CreditPayment("Maria", 5_000, "pix"), restoredLedger.entries.value.single().summaryData)
        assertNotNull(restoredLedger.latestUndoable())
    }

    @Test
    fun undoStateAndExpirationArePersistedWithoutDeletingHistory() = runBlocking {
        database = openDatabase()
        val ledger = AgentActivityLedger(RoomAgentActivityRepository(database!!.agentActivityDao()))
        val entry = ledger.record(
            capability = TinoCapabilityId.RECEIVE_CREDIT_PAYMENT,
            summary = "Pagamento recebido",
            source = AgentActivitySource.TEXT,
            operationId = "payment-expiring",
            undo = AgentUndoEligibility(
                policy = AgentUndoPolicy.COMPENSATING_OPERATION,
                compensatingCapability = TinoCapabilityId.REVERSE_CREDIT_PAYMENT,
                deadlineEpochMs = 10L,
            ),
            occurredAtEpochMs = 1L,
        )

        runCatching { ledger.requestUndo(entry.id, nowEpochMs = 11L) }
        ledger.awaitPersistence()
        assertEquals(AgentUndoState.EXPIRED, ledger.entries.value.single().undoState)
        assertEquals(1, ledger.entries.value.size)
    }

    private fun openDatabase() = Room.databaseBuilder(
        context,
        TinoDatabase::class.java,
        databaseName,
    ).addMigrations(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
    ).allowMainThreadQueries().build()
}
