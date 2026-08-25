package com.tino.app.domain.language

data class ContextualCandidate<T>(
    val entity: T,
    val searchableText: String,
    val aliases: Set<String> = emptySet(),
    val usageFrequency: Int = 0,
    val recentUses: Int = 0,
    val screenTags: Set<String> = emptySet(),
)

data class ContextualScore(
    val total: Float,
    val textMatch: Float,
    val recentUsage: Float,
    val screenContext: Float,
    val conversationContext: Float,
)

data class ContextualCandidateScore<T>(
    val candidate: ContextualCandidate<T>,
    val score: ContextualScore,
)

/** Ranks real catalog candidates; it never creates or mutates an entity. */
class ContextualEntityResolver<T>(
    private val autoResolveThreshold: Float = 0.82f,
    private val ambiguityMargin: Float = 0.08f,
) {
    fun rank(
        reference: String,
        candidates: List<ContextualCandidate<T>>,
        context: CommerceContext,
    ): List<ContextualCandidateScore<T>> = candidates.mapNotNull { candidate ->
        val textMatch = textScore(reference, candidate)
        if (textMatch == 0f) return@mapNotNull null
        val recentUsage = (candidate.recentUses.coerceAtMost(5) / 5f) * 0.12f +
            (candidate.usageFrequency.coerceAtMost(20) / 20f) * 0.08f
        val screenContext = if (context.currentScreen != null && context.currentScreen in candidate.screenTags) 0.08f else 0f
        val normalizedReference = LanguageNormalizer.normalize(reference)
        val conversationContext = if (context.recentEntities.any {
                LanguageNormalizer.normalize(it.text) == normalizedReference &&
                    LanguageNormalizer.normalize(it.text) == LanguageNormalizer.normalize(candidate.searchableText)
            }
        ) 0.12f else 0f
        ContextualCandidateScore(
            candidate = candidate,
            score = ContextualScore(
                total = (textMatch * 0.68f + recentUsage + screenContext + conversationContext).coerceAtMost(1f),
                textMatch = textMatch,
                recentUsage = recentUsage,
                screenContext = screenContext,
                conversationContext = conversationContext,
            ),
        )
    }.sortedByDescending { it.score.total }

    fun resolve(
        reference: String,
        candidates: List<ContextualCandidate<T>>,
        context: CommerceContext,
    ): LanguageEntityResolution<T> {
        val ranked = rank(reference, candidates, context)
        val best = ranked.firstOrNull() ?: return LanguageEntityResolution.NotFound
        val second = ranked.getOrNull(1)
        if (second != null && best.score.total - second.score.total < ambiguityMargin) {
            return LanguageEntityResolution.Ambiguous(
                candidates = ranked.takeWhile { best.score.total - it.score.total < ambiguityMargin }.map { it.candidate.entity },
                reason = "A referência ainda pode indicar mais de uma entidade.",
            )
        }
        if (best.score.total < autoResolveThreshold) {
            return LanguageEntityResolution.NeedsClarification(
                "Encontrei uma possibilidade, mas preciso de mais contexto para confirmar.",
            )
        }
        return LanguageEntityResolution.Resolved(best.candidate.entity, best.score.total)
    }

    private fun textScore(reference: String, candidate: ContextualCandidate<T>): Float {
        val normalizedReference = LanguageNormalizer.normalize(reference)
        val texts = (listOf(candidate.searchableText) + candidate.aliases).map(LanguageNormalizer::normalize)
        if (texts.any { it == normalizedReference }) return 1f
        val referenceTokens = normalizedReference.split(' ').filter(String::isNotBlank).toSet()
        if (referenceTokens.isEmpty()) return 0f
        return texts.maxOf { text ->
            val candidateTokens = text.split(' ').filter(String::isNotBlank).toSet()
            val overlap = referenceTokens.intersect(candidateTokens).size
            when {
                overlap == referenceTokens.size -> 0.86f
                overlap > 0 -> overlap.toFloat() / maxOf(referenceTokens.size, candidateTokens.size)
                else -> 0f
            }
        }
    }
}
