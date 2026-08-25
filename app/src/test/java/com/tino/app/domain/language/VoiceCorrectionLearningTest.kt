package com.tino.app.domain.language

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCorrectionLearningTest {
    @Test
    fun usedVoiceCorrectionIsCommittedOnlyAfterSuccessfulExecution() = runBlocking {
        val engine = CorrectionLearningEngine()
        val store = TestBusinessMemoryStore()
        val memory = CommerceContextMemory(engine, GovernedBusinessMemory(store))
        val interpreter = ContextualLanguageInterpreter(DeterministicLanguageInterpreter(), memory)

        memory.queueVoiceCorrection(
            originalTranscript = "Quanto de Café Maracá tenho",
            correctedTranscript = "Quanto de Café Maratá tenho",
        )
        val interpretation = interpreter.interpret(
            LanguageInput("Quanto de Café Maratá tenho", LanguageSource.VOICE),
        )

        assertEquals(TinoIntent.READ_STOCK, interpretation?.intent)
        assertEquals("cafe marata", interpretation?.references?.single()?.text)
        assertNull(memory.lastVoiceCorrectionEvent)
        assertTrue(engine.entries(CorrectionLearningScope.STORE, CommerceContextMemory.DEFAULT_BUSINESS_SCOPE_KEY).isEmpty())
        assertTrue(store.list(CommerceContextMemory.DEFAULT_BUSINESS_SCOPE_KEY).isEmpty())

        val event = memory.commitVoiceCorrection()

        assertEquals("maraca", event?.spoken)
        assertEquals("cafe marata", event?.canonical)
        assertEquals(LanguageEntityType.PRODUCT, event?.entityType)
        assertEquals(event, memory.lastVoiceCorrectionEvent)
        assertEquals(CorrectionLearningStatus.CANDIDATE,
            engine.entries(CorrectionLearningScope.STORE, CommerceContextMemory.DEFAULT_BUSINESS_SCOPE_KEY).single().status)
        assertEquals(MemoryLifecycle.CANDIDATE,
            store.list(CommerceContextMemory.DEFAULT_BUSINESS_SCOPE_KEY).single().lifecycle)
    }

    @Test
    fun fastPathGroundingCanPrepareCorrectionBeforeCommit() = runBlocking {
        val engine = CorrectionLearningEngine()
        val store = TestBusinessMemoryStore()
        val memory = CommerceContextMemory(engine, GovernedBusinessMemory(store))

        memory.queueVoiceCorrection(
            originalTranscript = "Quanto de Café Maracá tenho",
            correctedTranscript = "Quanto de Café Maratá tenho",
        )
        memory.prepareVoiceCorrectionForResolvedReference(
            EntityReference(LanguageEntityType.PRODUCT, "Café Maratá"),
        )

        assertNull(memory.lastVoiceCorrectionEvent)
        val event = memory.commitVoiceCorrection()

        assertEquals("maraca", event?.spoken)
        assertEquals("cafe marata", event?.canonical)
        assertEquals(CorrectionLearningStatus.CANDIDATE,
            engine.entries(CorrectionLearningScope.STORE, CommerceContextMemory.DEFAULT_BUSINESS_SCOPE_KEY).single().status)
        assertEquals(MemoryLifecycle.CANDIDATE,
            store.list(CommerceContextMemory.DEFAULT_BUSINESS_SCOPE_KEY).single().lifecycle)
    }

    @Test
    fun correctionWithoutSemanticChangeDoesNotCreateLearning() = runBlocking {
        val engine = CorrectionLearningEngine()
        val store = TestBusinessMemoryStore()
        val memory = CommerceContextMemory(engine, GovernedBusinessMemory(store))

        memory.queueVoiceCorrection("Quanto de Café Maratá tenho", "Quanto de Café Maratá tenho")

        assertNull(memory.commitVoiceCorrection())
        assertTrue(engine.entries(CorrectionLearningScope.STORE, CommerceContextMemory.DEFAULT_BUSINESS_SCOPE_KEY).isEmpty())
        assertTrue(store.list(CommerceContextMemory.DEFAULT_BUSINESS_SCOPE_KEY).isEmpty())
    }

    @Test
    fun cancelledCorrectionIsDiscardedWithoutLearning() = runBlocking {
        val engine = CorrectionLearningEngine()
        val store = TestBusinessMemoryStore()
        val memory = CommerceContextMemory(engine, GovernedBusinessMemory(store))
        val interpreter = ContextualLanguageInterpreter(DeterministicLanguageInterpreter(), memory)

        memory.queueVoiceCorrection("Quanto de Café Maracá tenho", "Quanto de Café Maratá tenho")
        interpreter.interpret(LanguageInput("Quanto de Café Maratá tenho"))
        memory.discardVoiceCorrection()

        assertNull(memory.commitVoiceCorrection())
        assertTrue(engine.entries(CorrectionLearningScope.STORE, CommerceContextMemory.DEFAULT_BUSINESS_SCOPE_KEY).isEmpty())
        assertTrue(store.list(CommerceContextMemory.DEFAULT_BUSINESS_SCOPE_KEY).isEmpty())
    }

    @Test
    fun failedExecutionDoesNotCommitPreparedCorrection() = runBlocking {
        val engine = CorrectionLearningEngine()
        val store = TestBusinessMemoryStore()
        val memory = CommerceContextMemory(engine, GovernedBusinessMemory(store))
        val interpreter = ContextualLanguageInterpreter(DeterministicLanguageInterpreter(), memory)

        memory.queueVoiceCorrection("Quanto de Café Maracá tenho", "Quanto de Café Maratá tenho")
        interpreter.interpret(LanguageInput("Quanto de Café Maratá tenho"))

        // The query may be semantically grounded, but no successful result was
        // produced, so the runtime must discard the prepared event.
        memory.discardVoiceCorrection()

        assertNull(memory.lastVoiceCorrectionEvent)
        assertNull(memory.commitVoiceCorrection())
        assertTrue(engine.entries(CorrectionLearningScope.STORE, CommerceContextMemory.DEFAULT_BUSINESS_SCOPE_KEY).isEmpty())
        assertTrue(store.list(CommerceContextMemory.DEFAULT_BUSINESS_SCOPE_KEY).isEmpty())
    }

    private class TestBusinessMemoryStore : BusinessMemoryStorePort {
        private val records = linkedMapOf<String, BusinessMemoryRecord>()

        override suspend fun find(scopeKey: String, memoryKey: String, value: String): BusinessMemoryRecord? =
            records.values.firstOrNull {
                it.scopeKey == scopeKey && it.memoryKey == memoryKey && it.value == value
            }

        override suspend fun findByKey(scopeKey: String, memoryKey: String): List<BusinessMemoryRecord> =
            records.values.filter { it.scopeKey == scopeKey && it.memoryKey == memoryKey }

        override suspend fun upsert(record: BusinessMemoryRecord) {
            records[record.id] = record
        }

        override suspend fun list(scopeKey: String): List<BusinessMemoryRecord> =
            records.values.filter { it.scopeKey == scopeKey }
    }
}
