package com.tino.app.domain.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLexiconTest {
    private val lexicon = AdaptiveLexicon()

    @Test
    fun phoneticTypoRanksAndResolvesTheRealCatalogEntity() {
        val result = lexicon.resolve(
            reference = "Maracá",
            candidates = listOf(
                candidate("product-1", LanguageEntityType.PRODUCT, "Café Maratá"),
                candidate("product-2", LanguageEntityType.PRODUCT, "Açúcar"),
            ),
        )

        assertEquals("product-1", (result as AdaptiveLexiconResolution.Resolved).entity)
        assertTrue(result.confidence >= AdaptiveLexicon.DEFAULT_AUTO_RESOLVE_THRESHOLD)
    }

    @Test
    fun learnedAliasWinsWithoutChangingTheCanonicalEntity() {
        val result = lexicon.resolve(
            reference = "maraca",
            candidates = listOf(candidate("product-1", LanguageEntityType.PRODUCT, "Café Maratá")),
            learnedAliases = mapOf("maraca" to "cafe marata"),
        )

        assertEquals("product-1", (result as AdaptiveLexiconResolution.Resolved).entity)
        assertEquals(1f, result.confidence, 0f)
    }

    @Test
    fun closeCustomerNamesRemainAmbiguousInsteadOfGuessing() {
        val result = lexicon.resolve(
            reference = "Maria",
            candidates = listOf(
                candidate("customer-1", LanguageEntityType.CUSTOMER, "Maria Lina"),
                candidate("customer-2", LanguageEntityType.CUSTOMER, "Maria Luiza"),
            ),
        )

        assertTrue(result is AdaptiveLexiconResolution.Ambiguous)
    }

    @Test
    fun unrelatedTextFallsBackWithoutFabricatingAResult() {
        val result = lexicon.resolve(
            reference = "Biscoito inexistente",
            candidates = listOf(candidate("product-1", LanguageEntityType.PRODUCT, "Café Maratá")),
        )

        assertEquals(AdaptiveLexiconResolution.NotFound, result)
    }

    private fun candidate(id: String, type: LanguageEntityType, name: String) =
        AdaptiveLexiconCandidate(
            entity = id,
            entityType = type,
            canonical = name,
        )
}
