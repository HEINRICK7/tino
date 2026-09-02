package com.tino.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.domain.intelligence.AttentionRecord
import com.tino.app.domain.intelligence.AttentionState
import com.tino.app.domain.intelligence.AttentionOutcome
import com.tino.app.domain.intelligence.TinoBusinessEvidence
import com.tino.app.domain.intelligence.TinoEvidenceSource
import com.tino.app.domain.intelligence.TinoEvidenceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class AttentionPersistenceTest {
    private lateinit var database: TinoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun attentionRoundTripsEvidenceAndStateThroughRoom() = runBlocking {
        val repository = RoomAttentionRepository(database.attentionDao())
        repository.upsertAll(
            listOf(
                AttentionRecord(
                    id = "insight-1",
                    insightId = "insight-1",
                    subjectId = "product-1",
                    title = "Café",
                    explanation = "Restam 2 unidades.",
                    evidenceIds = listOf("evidence-1", "evidence-2"),
                    relevance = 90,
                    urgency = 80,
                    confidence = 0.9,
                    state = AttentionState.ACTIVE,
                    createdAtEpochMs = 100L,
                    lastSeenAtEpochMs = 200L,
                ),
            ),
        )

        val stored = repository.find("insight-1")!!
        assertEquals(listOf("evidence-1", "evidence-2"), stored.evidenceIds)
        repository.updateState("insight-1", AttentionState.DISMISSED)
        assertEquals(AttentionState.DISMISSED, repository.find("insight-1")!!.state)
        assertTrue(repository.observeActive().first().isEmpty())
        repository.recordOutcome("insight-1", AttentionOutcome.DISMISSED)
        assertEquals(1, repository.outcomeMetrics().count(AttentionOutcome.DISMISSED))
    }

    @Test
    fun evidenceCatalogRoundTripsProvenanceAndStructuredFactsThroughRoom() = runBlocking {
        val repository = RoomTinoEvidenceRepository(database.intelligenceEvidenceDao())
        repository.upsertAll(
            listOf(
                TinoBusinessEvidence(
                    id = "evidence:stockout:p1",
                    type = TinoEvidenceType.ANOMALY,
                    subjectId = "p1",
                    facts = mapOf("title" to "Ruptura", "body" to "Café, 2 unidades"),
                    source = TinoEvidenceSource.DERIVED,
                    confidence = 0.78,
                    occurredAtEpochMs = 100L,
                    detectedAtEpochMs = 200L,
                ),
            ),
        )

        val stored = repository.find("evidence:stockout:p1")!!
        assertEquals(TinoEvidenceType.ANOMALY, stored.type)
        assertEquals(TinoEvidenceSource.DERIVED, stored.source)
        assertEquals("Café, 2 unidades", stored.facts["body"])
    }
}
