package com.tino.app.domain.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionLearningEngineTest {
    private val engine = CorrectionLearningEngine()

    @Test
    fun consistentCorrectionsMoveCandidateToLearnedAndTrusted() {
        val first = engine.record(event("maraca", "marata", CorrectionProvenance.USER_CORRECTION))
        val learned = engine.record(event("maraca", "marata", CorrectionProvenance.USER_CORRECTION))
        val trusted = engine.record(event("maraca", "marata", CorrectionProvenance.USER_CONFIRMATION))

        assertEquals(CorrectionLearningStatus.CANDIDATE, first.status)
        assertEquals(CorrectionLearningStatus.LEARNED, learned.status)
        assertEquals(CorrectionLearningStatus.TRUSTED, trusted.status)
        assertEquals("marata", engine.resolve("maraca", LanguageEntityType.PRODUCT,
            CorrectionLearningScope.SESSION, "session-a"))
        assertTrue(trusted.provenance.contains(CorrectionProvenance.USER_CONFIRMATION))
    }

    @Test
    fun contradictionDemotesExistingMappingAndDoesNotResolveImmediately() {
        repeat(2) { engine.record(event("maraca", "marata", CorrectionProvenance.USER_CORRECTION)) }
        val alternative = engine.record(event("maraca", "maraca", CorrectionProvenance.USER_CONTRADICTION))

        assertEquals(CorrectionLearningStatus.CANDIDATE, alternative.status)
        assertNull(engine.resolve("maraca", LanguageEntityType.PRODUCT,
            CorrectionLearningScope.SESSION, "session-a"))
        assertTrue(engine.entries(CorrectionLearningScope.SESSION, "session-a")
            .any { it.canonical == "marata" && it.status == CorrectionLearningStatus.DEMOTED })
    }

    @Test
    fun removalKeepsHistoryButStopsResolution() {
        repeat(2) { engine.record(event("chico", "chico filo", CorrectionProvenance.USER_CORRECTION)) }
        engine.remove("chico", "chico filo", LanguageEntityType.CUSTOMER,
            CorrectionLearningScope.SESSION, "session-a")

        assertNull(engine.resolve("chico", LanguageEntityType.CUSTOMER,
            CorrectionLearningScope.SESSION, "session-a"))
        assertEquals(CorrectionLearningStatus.REMOVED,
            engine.entries(CorrectionLearningScope.SESSION, "session-a").single().status)
    }

    @Test
    fun scopeIsolationPreventsOneSessionFromChangingAnother() {
        repeat(2) { engine.record(event("maraca", "marata", CorrectionProvenance.USER_CORRECTION, "session-a")) }

        assertEquals("marata", engine.resolve("maraca", LanguageEntityType.PRODUCT,
            CorrectionLearningScope.SESSION, "session-a"))
        assertNull(engine.resolve("maraca", LanguageEntityType.PRODUCT,
            CorrectionLearningScope.SESSION, "session-b"))
    }

    private fun event(
        spoken: String,
        canonical: String,
        provenance: CorrectionProvenance,
        scopeKey: String = "session-a",
    ) = CorrectionEvent(
        spoken = spoken,
        canonical = canonical,
        entityType = if (spoken == "chico") LanguageEntityType.CUSTOMER else LanguageEntityType.PRODUCT,
        scope = CorrectionLearningScope.SESSION,
        scopeKey = scopeKey,
        provenance = provenance,
    )
}
