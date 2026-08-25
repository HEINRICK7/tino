package com.tino.app.domain.language

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** A catalog candidate; the lexicon never creates or changes the entity. */
data class AdaptiveLexiconCandidate<T>(
    val entity: T,
    val entityType: LanguageEntityType,
    val canonical: String,
    val aliases: Set<String> = emptySet(),
    val usageFrequency: Int = 0,
    val recentUses: Int = 0,
    val screenTags: Set<String> = emptySet(),
)

data class AdaptiveLexiconScore(
    val total: Float,
    val lexical: Float,
    val phonetic: Float,
    val contextual: Float,
)

data class AdaptiveLexiconCandidateScore<T>(
    val candidate: AdaptiveLexiconCandidate<T>,
    val score: AdaptiveLexiconScore,
)

sealed interface AdaptiveLexiconResolution<out T> {
    data class Resolved<T>(val entity: T, val confidence: Float) : AdaptiveLexiconResolution<T>
    data class Ambiguous<T>(val candidates: List<T>) : AdaptiveLexiconResolution<T>
    data object NotFound : AdaptiveLexiconResolution<Nothing>
    data class NeedsClarification(val confidence: Float) : AdaptiveLexiconResolution<Nothing>
}

/**
 * Port for adaptive vocabulary lookup. Implementations may be replaced by a
 * persisted/indexed catalog, while callers keep receiving catalog entities.
 */
interface AdaptiveLexiconPort {
    fun <T> rank(
        reference: String,
        candidates: List<AdaptiveLexiconCandidate<T>>,
        context: CommerceContext = CommerceContext(),
        learnedAliases: Map<String, String> = emptyMap(),
    ): List<AdaptiveLexiconCandidateScore<T>>

    fun <T> resolve(
        reference: String,
        candidates: List<AdaptiveLexiconCandidate<T>>,
        context: CommerceContext = CommerceContext(),
        learnedAliases: Map<String, String> = emptyMap(),
    ): AdaptiveLexiconResolution<T>
}

/**
 * Deterministic lexical, phonetic and contextual resolver.
 *
 * Exact catalog names and learned aliases win. Approximate matches are only
 * accepted when confidence and separation from the second candidate are high;
 * otherwise the caller must clarify instead of guessing a commerce entity.
 */
@Singleton
class AdaptiveLexicon @Inject constructor() : AdaptiveLexiconPort {
    companion object {
        const val DEFAULT_AUTO_RESOLVE_THRESHOLD = 0.72f
        const val DEFAULT_FALLBACK_THRESHOLD = 0.56f
        const val DEFAULT_AMBIGUITY_MARGIN = 0.08f
    }

    override fun <T> rank(
        reference: String,
        candidates: List<AdaptiveLexiconCandidate<T>>,
        context: CommerceContext,
        learnedAliases: Map<String, String>,
    ): List<AdaptiveLexiconCandidateScore<T>> {
        val normalizedReference = LanguageNormalizer.normalize(reference)
        if (normalizedReference.isBlank()) return emptyList()

        return candidates.mapNotNull { candidate ->
            if (candidate.entityType == LanguageEntityType.SUPPLIER ||
                candidate.entityType == LanguageEntityType.PRODUCT ||
                candidate.entityType == LanguageEntityType.CUSTOMER
            ) {
                scoreCandidate(normalizedReference, candidate, context, learnedAliases)
            } else {
                null
            }
        }.filter { it.score.total > 0f }
            .sortedByDescending { it.score.total }
    }

    override fun <T> resolve(
        reference: String,
        candidates: List<AdaptiveLexiconCandidate<T>>,
        context: CommerceContext,
        learnedAliases: Map<String, String>,
    ): AdaptiveLexiconResolution<T> {
        val ranked = rank(reference, candidates, context, learnedAliases)
        val best = ranked.firstOrNull() ?: return AdaptiveLexiconResolution.NotFound
        val second = ranked.getOrNull(1)
        val normalizedReference = LanguageNormalizer.normalize(reference)
        val exact = ranked.filter { score ->
            val candidateAliases = listOf(score.candidate.canonical) + score.candidate.aliases + learnedAliases.keys
                .filter { learnedAliases[it]?.let(LanguageNormalizer::normalize) ==
                    LanguageNormalizer.normalize(score.candidate.canonical) }
            candidateAliases.any { LanguageNormalizer.normalize(it) == normalizedReference }
        }
        if (exact.size > 1) return AdaptiveLexiconResolution.Ambiguous(exact.map { it.candidate.entity })
        if (exact.size == 1) {
            return AdaptiveLexiconResolution.Resolved(exact.single().candidate.entity, 1f)
        }

        if (second != null && best.score.total - second.score.total < DEFAULT_AMBIGUITY_MARGIN) {
            return AdaptiveLexiconResolution.Ambiguous(
                ranked.takeWhile { best.score.total - it.score.total < DEFAULT_AMBIGUITY_MARGIN }
                    .map { it.candidate.entity },
            )
        }
        if (best.score.total >= DEFAULT_AUTO_RESOLVE_THRESHOLD) {
            return AdaptiveLexiconResolution.Resolved(best.candidate.entity, best.score.total)
        }
        if (best.score.total >= DEFAULT_FALLBACK_THRESHOLD) {
            return AdaptiveLexiconResolution.NeedsClarification(best.score.total)
        }
        return AdaptiveLexiconResolution.NotFound
    }

    private fun <T> scoreCandidate(
        reference: String,
        candidate: AdaptiveLexiconCandidate<T>,
        context: CommerceContext,
        learnedAliases: Map<String, String>,
    ): AdaptiveLexiconCandidateScore<T> {
        val learned = learnedAliases
            .filterValues { value ->
                LanguageNormalizer.normalize(value) == LanguageNormalizer.normalize(candidate.canonical)
            }
            .keys
        val texts = (listOf(candidate.canonical) + candidate.aliases + learned)
            .map(LanguageNormalizer::normalize)
            .filter(String::isNotBlank)
        val lexical = texts.maxOfOrNull { surfaceSimilarity(reference, it) } ?: 0f
        val phonetic = texts.maxOfOrNull { phoneticSimilarity(reference, it) } ?: 0f
        val contextual = (
            (candidate.recentUses.coerceAtMost(5) / 5f) * 0.12f +
                (candidate.usageFrequency.coerceAtMost(20) / 20f) * 0.08f +
                if (context.currentScreen != null && context.currentScreen in candidate.screenTags) 0.08f else 0f
            ).coerceAtMost(0.28f)
        val total = (lexical * 0.60f + phonetic * 0.30f + contextual * 0.10f).coerceAtMost(1f)
        return AdaptiveLexiconCandidateScore(
            candidate = candidate,
            score = AdaptiveLexiconScore(total, lexical, phonetic, contextual),
        )
    }

    private fun surfaceSimilarity(left: String, right: String): Float {
        if (left == right) return 1f
        val leftTokens = left.split(' ').filter(String::isNotBlank).toSet()
        val rightTokens = right.split(' ').filter(String::isNotBlank).toSet()
        val tokenScore = if (leftTokens.isEmpty() || rightTokens.isEmpty()) 0f else {
            leftTokens.intersect(rightTokens).size.toFloat() / max(leftTokens.size, rightTokens.size)
        }
        val editScore = (listOf(left) + leftTokens).maxOf { leftPart ->
            (listOf(right) + rightTokens).maxOf { rightPart -> editSimilarity(leftPart, rightPart) }
        }
        return max(tokenScore, editScore)
    }

    private fun phoneticSimilarity(left: String, right: String): Float {
        val leftParts = listOf(left) + left.split(' ').filter(String::isNotBlank)
        val rightParts = listOf(right) + right.split(' ').filter(String::isNotBlank)
        return leftParts.maxOf { leftPart ->
            rightParts.maxOf { rightPart -> editSimilarity(phoneticKey(leftPart), phoneticKey(rightPart)) }
        }
    }

    private fun phoneticKey(value: String): String = LanguageNormalizer.normalize(value)
        .replace("ph", "f")
        .replace("ch", "x")
        .replace("lh", "l")
        .replace("nh", "n")
        .map { char ->
            when (char) {
                'a', 'e', 'i', 'o', 'u' -> 'a'
                'c', 'k', 'q' -> 'k'
                'g', 'j' -> 'j'
                's', 'z', 'x', 'ç' -> 's'
                else -> char
            }
        }.joinToString("")

    private fun editSimilarity(left: String, right: String): Float {
        if (left == right) return 1f
        if (left.isBlank() || right.isBlank()) return 0f
        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        left.forEachIndexed { row, leftChar ->
            current[0] = row + 1
            right.forEachIndexed { column, rightChar ->
                current[column + 1] = minOf(
                    current[column] + 1,
                    previous[column + 1] + 1,
                    previous[column] + if (leftChar == rightChar) 0 else 1,
                )
            }
            current.copyInto(previous)
        }
        return (1f - previous[right.length].toFloat() / max(left.length, right.length)).coerceAtLeast(0f)
    }
}
