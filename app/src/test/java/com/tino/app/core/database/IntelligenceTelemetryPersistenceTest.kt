package com.tino.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.domain.intelligence.IntelligenceErrorStage
import com.tino.app.domain.intelligence.IntelligenceExecutionResult
import com.tino.app.domain.intelligence.IntelligenceTelemetryEvent
import com.tino.app.domain.intelligence.IntelligenceValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class IntelligenceTelemetryPersistenceTest {
    @Test
    fun storesAndReadsPlannerRouteTelemetry() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TinoDatabase::class.java,
        ).build()
        try {
            val repository = RoomIntelligenceTelemetryRepository(database.intelligenceTelemetryDao())
            repository.record(
                IntelligenceTelemetryEvent(
                    id = "telemetry-1",
                    requestId = "request-1",
                    sessionId = "session-1",
                    plannerSelected = "ADK",
                    plannerUsed = "adk-fallback",
                    fallbackReason = "adk_no_plan",
                    plan = listOf("get_receivables", "sort_receivables"),
                    validationResult = IntelligenceValidationResult.ACCEPTED,
                    fallbackUsed = true,
                    executionResult = IntelligenceExecutionResult.SUCCEEDED,
                    groundingCompleteness = com.tino.app.domain.intelligence.IntelligenceGroundingCompleteness.COMPLETE,
                    latencyMs = 42L,
                    planningLatencyMs = 12L,
                    errorStage = IntelligenceErrorStage.NONE,
                    occurredAtEpochMs = 100L,
                ),
            )

            val restored = repository.recent().single()
            assertEquals("request-1", restored.requestId)
            assertEquals("session-1", restored.sessionId)
            assertEquals("ADK", restored.plannerSelected)
            assertEquals("adk_no_plan", restored.fallbackReason)
            assertEquals(listOf("get_receivables", "sort_receivables"), restored.plan)
            assertTrue(restored.fallbackUsed)
            assertEquals(12L, restored.planningLatencyMs)
        } finally {
            database.close()
        }
    }
}
