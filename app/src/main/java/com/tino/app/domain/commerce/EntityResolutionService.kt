package com.tino.app.domain.commerce

import com.tino.app.core.database.CustomerEntity
import com.tino.app.core.database.ProductEntity
import com.tino.app.core.database.SupplierEntity
import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.domain.language.AdaptiveLexicon
import com.tino.app.domain.language.AdaptiveLexiconPort
import com.tino.app.domain.language.AdaptiveLexiconResolution
import com.tino.app.domain.language.CommerceContext
import com.tino.app.domain.language.LanguageEntityType
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

enum class EntityResolutionStrategy { EXACT, ALIAS, FUZZY }

sealed interface EntityResolutionMatch<out T> {
    data class Resolved<T>(val value: T, val strategy: EntityResolutionStrategy) : EntityResolutionMatch<T>
    data class Ambiguous<T>(val values: List<T>, val strategy: EntityResolutionStrategy) : EntityResolutionMatch<T>
    data object NotFound : EntityResolutionMatch<Nothing>
}

/**
 * Local anti-corruption layer between model references and commerce entities.
 * It never accepts IDs, price, balance or stock from the language model.
 */
@Singleton
class EntityResolutionService @Inject constructor(
    private val commerceRepository: CommerceRepository,
    private val auditLogger: AuditLogger,
    private val adaptiveLexicon: AdaptiveLexiconPort,
) {
    constructor(
        commerceRepository: CommerceRepository,
        auditLogger: AuditLogger,
    ) : this(commerceRepository, auditLogger, AdaptiveLexicon())

    suspend fun resolveCustomer(reference: String): EntityResolutionMatch<CustomerEntity> =
        resolve(
            entityType = "customer",
            reference = reference,
            candidates = commerceRepository.allCustomersForResolution(),
            name = CustomerEntity::name,
        )

    suspend fun resolveProduct(reference: String): EntityResolutionMatch<ProductEntity> =
        resolve(
            entityType = "product",
            reference = reference,
            candidates = commerceRepository.allProductsForResolution(),
            name = ProductEntity::name,
        )

    suspend fun resolveSupplier(reference: String): EntityResolutionMatch<SupplierEntity> =
        resolve(
            entityType = "supplier",
            reference = reference,
            candidates = commerceRepository.allSuppliersForResolution(),
            name = SupplierEntity::name,
        )

    private fun <T> resolve(
        entityType: String,
        reference: String,
        candidates: List<T>,
        name: (T) -> String,
    ): EntityResolutionMatch<T> {
        audit(AuditEventType.ENTITY_RESOLUTION_STARTED, entityType, null, candidates.size)
        val normalizedReference = normalize(reference)
        if (normalizedReference.isBlank()) {
            audit(AuditEventType.ENTITY_RESOLUTION_NOT_FOUND, entityType, null, 0)
            return EntityResolutionMatch.NotFound
        }

        val exact = candidates.filter { normalize(name(it)) == normalizedReference }
        if (exact.size == 1) {
            audit(AuditEventType.ENTITY_RESOLUTION_EXACT, entityType, EntityResolutionStrategy.EXACT, 1)
            return EntityResolutionMatch.Resolved(exact.single(), EntityResolutionStrategy.EXACT)
        }
        if (exact.size > 1) {
            audit(AuditEventType.ENTITY_RESOLUTION_AMBIGUOUS, entityType, EntityResolutionStrategy.EXACT, exact.size)
            return EntityResolutionMatch.Ambiguous(exact, EntityResolutionStrategy.EXACT)
        }

        val referenceAlias = withoutHonorifics(normalizedReference)
        val aliases = candidates.filter { withoutHonorifics(normalize(name(it))) == referenceAlias }
        if (aliases.size == 1) {
            audit(AuditEventType.ENTITY_RESOLUTION_ALIAS, entityType, EntityResolutionStrategy.ALIAS, 1)
            return EntityResolutionMatch.Resolved(aliases.single(), EntityResolutionStrategy.ALIAS)
        }
        if (aliases.size > 1) {
            audit(AuditEventType.ENTITY_RESOLUTION_AMBIGUOUS, entityType, EntityResolutionStrategy.ALIAS, aliases.size)
            return EntityResolutionMatch.Ambiguous(aliases, EntityResolutionStrategy.ALIAS)
        }

        val scored = candidates.mapNotNull { candidate ->
            fuzzyScore(referenceAlias, withoutHonorifics(normalize(name(candidate))))
                ?.let { score -> candidate to score }
        }
        val bestScore = scored.maxOfOrNull { it.second }
        if (bestScore == null) {
            val adaptive = adaptiveLexicon.resolve(
                reference = reference,
                candidates = candidates.map { candidate ->
                    com.tino.app.domain.language.AdaptiveLexiconCandidate(
                        entity = candidate,
                        entityType = entityType.toLanguageEntityType(),
                        canonical = name(candidate),
                    )
                },
            )
            return when (adaptive) {
                is AdaptiveLexiconResolution.Resolved -> {
                    audit(AuditEventType.ENTITY_RESOLUTION_FUZZY, entityType, EntityResolutionStrategy.FUZZY, 1)
                    EntityResolutionMatch.Resolved(adaptive.entity, EntityResolutionStrategy.FUZZY)
                }
                is AdaptiveLexiconResolution.Ambiguous -> {
                    audit(
                        AuditEventType.ENTITY_RESOLUTION_AMBIGUOUS,
                        entityType,
                        EntityResolutionStrategy.FUZZY,
                        adaptive.candidates.size,
                    )
                    EntityResolutionMatch.Ambiguous(adaptive.candidates, EntityResolutionStrategy.FUZZY)
                }
                is AdaptiveLexiconResolution.NeedsClarification,
                AdaptiveLexiconResolution.NotFound,
                -> {
                    audit(AuditEventType.ENTITY_RESOLUTION_NOT_FOUND, entityType, null, 0)
                    EntityResolutionMatch.NotFound
                }
            }
        }
        val best = scored.filter { it.second == bestScore }.map { it.first }
        if (best.size > 1) {
            audit(AuditEventType.ENTITY_RESOLUTION_AMBIGUOUS, entityType, EntityResolutionStrategy.FUZZY, best.size)
            return EntityResolutionMatch.Ambiguous(best, EntityResolutionStrategy.FUZZY)
        }
        audit(AuditEventType.ENTITY_RESOLUTION_FUZZY, entityType, EntityResolutionStrategy.FUZZY, 1)
        return EntityResolutionMatch.Resolved(best.single(), EntityResolutionStrategy.FUZZY)
    }

    private fun String.toLanguageEntityType(): LanguageEntityType = when (this) {
        "customer" -> LanguageEntityType.CUSTOMER
        "supplier" -> LanguageEntityType.SUPPLIER
        else -> LanguageEntityType.PRODUCT
    }

    private fun fuzzyScore(reference: String, candidate: String): Double? {
        val referenceTokens = reference.split(' ').filter(String::isNotBlank).toSet()
        val candidateTokens = candidate.split(' ').filter(String::isNotBlank).toSet()
        if (referenceTokens.isEmpty() || candidateTokens.isEmpty()) return null
        val overlap = referenceTokens.intersect(candidateTokens).size
        val tokenScore = overlap.toDouble() / maxOf(referenceTokens.size, candidateTokens.size)
        val prefixMatch = candidateTokens.any { candidateToken ->
            referenceTokens.any { referenceToken ->
                candidateToken.length >= 4 && referenceToken.length >= 4 &&
                    (candidateToken.startsWith(referenceToken) || referenceToken.startsWith(candidateToken))
            }
        }
        return when {
            tokenScore >= 0.5 -> tokenScore
            prefixMatch -> 0.5
            else -> null
        }
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.trim().lowercase(), Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .replace("[^a-z0-9]+".toRegex(), " ")
        .trim()

    private fun withoutHonorifics(value: String): String = value
        .split(' ')
        .filter { it !in HONORIFICS }
        .joinToString(" ")

    private fun audit(
        type: AuditEventType,
        entityType: String,
        strategy: EntityResolutionStrategy?,
        candidateCount: Int,
    ) {
        auditLogger.record(
            type,
            buildMap {
                put("entity_type", entityType)
                put("candidate_count", candidateCount.toString())
                strategy?.let { put("match_strategy", it.name.lowercase()) }
            },
        )
    }

    companion object {
        private val HONORIFICS = setOf("dona", "don", "senhora", "senhor", "sra", "sr")
    }
}

object NoopAuditLogger : AuditLogger {
    override fun record(type: AuditEventType, metadata: Map<String, String>) = Unit
}
