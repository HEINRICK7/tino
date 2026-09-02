package com.tino.app.domain.intelligence

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TinoAttentionEngineTest {
    private val now = 1_700_000_000_000L

    @Test
    fun reconcilesInsightsDeduplicatesAndKeepsDismissalAcrossRefreshes() = runBlocking {
        val repository = RecordingRepository()
        val engine = TinoAttentionEngine(
            repository = repository,
            clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.of("UTC")),
        )
        val analysis = analysis()

        assertEquals(1, engine.reconcile(analysis, now).size)
        engine.dismiss("insight-1")
        assertTrue(engine.reconcile(analysis, now).isEmpty())
        assertEquals(AttentionState.DISMISSED, repository.items.single().state)
    }

    @Test
    fun snoozedAttentionReturnsOnlyAfterItsDeadline() = runBlocking {
        val repository = RecordingRepository()
        val engine = TinoAttentionEngine(
            repository = repository,
            clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.of("UTC")),
        )
        engine.reconcile(analysis(), now)
        engine.snooze("insight-1", now + 10_000L)

        assertTrue(engine.reconcile(analysis(), now).isEmpty())
        assertEquals(1, engine.reconcile(analysis(), now + 10_000L).size)
    }

    @Test
    fun changedExplanationMakesDismissedInsightEligibleAgain() = runBlocking {
        val repository = RecordingRepository()
        val engine = TinoAttentionEngine(
            repository = repository,
            clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.of("UTC")),
        )
        engine.reconcile(analysis(), now)
        engine.dismiss("insight-1")

        val changed = analysis().copy(
            insights = listOf(analysis().insights.single().copy(explanation = "Restam 1 unidade.")),
        )
        assertEquals(1, engine.reconcile(changed, now + 1_000L).size)
        assertEquals(AttentionState.ACTIVE, repository.items.single().state)
    }

    @Test
    fun removedCandidateIsResolvedAndNoLongerReturnsInTheDigest() = runBlocking {
        val repository = RecordingRepository()
        val engine = TinoAttentionEngine(
            repository = repository,
            clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.of("UTC")),
        )
        engine.reconcile(analysis(), now)
        val empty = TinoIntelligenceAnalysis(
            evidence = emptyList(),
            insights = emptyList(),
            candidateInsights = emptyList(),
        )

        assertTrue(engine.reconcile(empty, now + 1_000L).isEmpty())
        assertEquals(AttentionState.RESOLVED, repository.items.single().state)
        assertTrue(engine.digest(now + 1_000L).isEmpty)
    }

    @Test
    fun recurringCandidateReturnsAfterItWasResolved() = runBlocking {
        val repository = RecordingRepository()
        val engine = TinoAttentionEngine(
            repository = repository,
            clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.of("UTC")),
        )
        engine.reconcile(analysis(), now)
        engine.reconcile(
            TinoIntelligenceAnalysis(emptyList(), emptyList()),
            now + 1_000L,
        )

        assertEquals(1, engine.reconcile(analysis(), now + 2_000L).size)
        assertEquals(AttentionState.ACTIVE, repository.items.single().state)
    }

    @Test
    fun actionAndDismissAreRecordedAsAcceptanceMetrics() = runBlocking {
        val repository = RecordingRepository()
        val engine = TinoAttentionEngine(
            repository = repository,
            clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.of("UTC")),
        )
        engine.reconcile(analysis(), now)
        engine.actioned("insight-1")
        engine.dismiss("insight-1")

        assertEquals(1, repository.outcomes[AttentionOutcome.ACTIONED])
        assertEquals(1, repository.outcomes[AttentionOutcome.DISMISSED])
    }

    private fun analysis() = TinoIntelligenceAnalysis(
        evidence = emptyList(),
        insights = listOf(
            TinoInsight(
                id = "insight-1",
                type = ThoughtType.ATTENTION,
                subjectId = "product-1",
                title = "Café",
                explanation = "Restam 2 unidades.",
                evidenceIds = listOf("evidence-1"),
                confidence = 0.9,
                relevance = 90,
                urgency = 80,
                novelty = 70,
                actions = emptyList(),
            ),
        ),
    )

    private class RecordingRepository : AttentionRepository {
        val items = mutableListOf<AttentionRecord>()
        val outcomes = mutableMapOf<AttentionOutcome, Int>()
        private val flow = MutableStateFlow<List<AttentionRecord>>(emptyList())

        override suspend fun upsertAll(records: List<AttentionRecord>) {
            records.forEach { record ->
                items.removeAll { it.id == record.id }
                items += record
            }
            flow.value = items.toList()
        }

        override suspend fun list(): List<AttentionRecord> = items.toList()
        override suspend fun find(id: String): AttentionRecord? = items.firstOrNull { it.id == id }
        override fun observeActive(): Flow<List<AttentionRecord>> = flow

        override suspend fun recordOutcome(attentionId: String, outcome: AttentionOutcome) {
            outcomes[outcome] = (outcomes[outcome] ?: 0) + 1
        }

        override suspend fun outcomeMetrics(): AttentionMetrics = AttentionMetrics(outcomes.toMap())

        override suspend fun updateState(id: String, state: AttentionState, snoozedUntilEpochMs: Long?) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) {
                items[index] = items[index].copy(state = state, snoozedUntilEpochMs = snoozedUntilEpochMs)
                flow.value = items.toList()
            }
        }
    }
}
