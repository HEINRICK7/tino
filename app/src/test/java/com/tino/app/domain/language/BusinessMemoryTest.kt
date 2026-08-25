package com.tino.app.domain.language

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessMemoryTest {
    private val store = InMemoryBusinessMemoryStore()
    private val memory = GovernedBusinessMemory(store)

    @Test
    fun repeatedUserEvidencePromotesCandidateToTrusted() = runBlocking {
        val first = memory.record(candidate(MemoryProvenanceType.USER_CORRECTION)).getOrThrow()
        val second = memory.record(candidate(MemoryProvenanceType.USER_CONFIRMATION)).getOrThrow()
        val third = memory.record(candidate(MemoryProvenanceType.USER_CONFIRMATION)).getOrThrow()

        assertEquals(MemoryLifecycle.CANDIDATE, first.lifecycle)
        assertEquals(MemoryLifecycle.LEARNED, second.lifecycle)
        assertEquals(MemoryLifecycle.TRUSTED, third.lifecycle)
        assertEquals("Café Maratá", memory.resolve("store-1", "entity_alias:PRODUCT:maraca")?.value)
    }

    @Test
    fun contradictionDemotesPreviousValueAndRequiresEvidenceForNewValue() = runBlocking {
        repeat(3) { memory.record(candidate(MemoryProvenanceType.USER_CONFIRMATION, value = "Café Maratá")) }
        val contradiction = memory.record(
            candidate(MemoryProvenanceType.USER_CONTRADICTION, value = "Café Maratá Tradicional"),
        ).getOrThrow()

        assertEquals(MemoryLifecycle.CANDIDATE, contradiction.lifecycle)
        assertTrue(store.list("store-1").any { it.value == "Café Maratá" && it.lifecycle == MemoryLifecycle.DEMOTED })
        assertEquals(null, memory.resolve("store-1", "entity_alias:PRODUCT:maraca"))
    }

    @Test
    fun transactionalFactIsRejectedAndNeverPersisted() = runBlocking {
        val result = memory.record(
            MemoryCandidate(
                scopeKey = "store-1",
                memoryKey = "customer_balance:Maria",
                value = "180",
                kind = BusinessMemoryKind.WORKFLOW_PREFERENCE,
                provenance = MemoryProvenance(MemoryProvenanceType.USER_CORRECTION, occurredAtEpochMs = 1L),
            ),
        )

        assertFalse(result.isSuccess)
        assertTrue(store.list("store-1").isEmpty())
    }

    @Test
    fun removeIsTerminalAndResolveDoesNotReturnRemovedMemory() = runBlocking {
        repeat(2) { memory.record(candidate(MemoryProvenanceType.USER_CONFIRMATION)) }
        assertNotNull(memory.resolve("store-1", "entity_alias:PRODUCT:maraca"))

        memory.remove("store-1", "entity_alias:PRODUCT:maraca")

        assertEquals(null, memory.resolve("store-1", "entity_alias:PRODUCT:maraca"))
        assertEquals(MemoryLifecycle.REMOVED, store.list("store-1").single().lifecycle)
    }

    private fun candidate(type: MemoryProvenanceType, value: String = "Café Maratá") = MemoryCandidate(
        scopeKey = "store-1",
        memoryKey = "entity_alias:PRODUCT:maraca",
        value = value,
        kind = BusinessMemoryKind.ENTITY_ALIAS,
        confidence = MemoryConfidence(0.9),
        provenance = MemoryProvenance(type, sourceInteractionId = "interaction-1", occurredAtEpochMs = 1L),
    )
}

private class InMemoryBusinessMemoryStore : BusinessMemoryStorePort {
    private val values = linkedMapOf<String, BusinessMemoryRecord>()
    override suspend fun find(scopeKey: String, memoryKey: String, value: String) =
        values.values.firstOrNull { it.scopeKey == scopeKey && it.memoryKey == memoryKey && it.value == value }
    override suspend fun findByKey(scopeKey: String, memoryKey: String) = values.values.filter { it.scopeKey == scopeKey && it.memoryKey == memoryKey }
    override suspend fun upsert(record: BusinessMemoryRecord) { values[record.id] = record }
    override suspend fun list(scopeKey: String) = values.values.filter { it.scopeKey == scopeKey }
}
