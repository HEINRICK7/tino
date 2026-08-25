package com.tino.app.domain.language

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Short structured memory for the active commerce interaction, not chat history. */
@Singleton
class CommerceContextMemory @Inject constructor(
    private val correctionLearningEngine: CorrectionLearningEngine,
    private val businessMemory: BusinessMemoryPort,
) {
    constructor() : this(CorrectionLearningEngine(), GovernedBusinessMemory(InMemoryBusinessMemoryStore()))

    companion object {
        const val DEFAULT_CONVERSATION_TTL_MS = 10 * 60 * 1_000L
        const val DEFAULT_BUSINESS_SCOPE_KEY = "default-store"
    }

    var context: CommerceContext = CommerceContext()
        private set

    private var lastInterpretation: IntentInterpretation? = null
    private var lastActionableInterpretation: IntentInterpretation? = null
    private var pendingVoiceCorrection: PendingVoiceCorrection? = null
    private var preparedVoiceCorrection: CorrectionEvent? = null
    var lastVoiceCorrectionEvent: CorrectionEvent? = null
        private set
    private val correctionScopeKey = "session-${UUID.randomUUID()}"

    fun previousInterpretation(): IntentInterpretation? {
        expireConversationIfNeeded()
        return lastActionableInterpretation ?: lastInterpretation
    }

    fun remember(interpretation: IntentInterpretation) {
        val customer = interpretation.references.firstOrNull { it.type == LanguageEntityType.CUSTOMER }
        val product = interpretation.references.firstOrNull { it.type == LanguageEntityType.PRODUCT }
        val entities = (interpretation.references + context.recentEntities)
            .distinctBy { it.type to LanguageNormalizer.normalize(it.text) }
            .take(8)
        val frequencies = interpretation.references.fold(context.usageFrequency) { result, reference ->
            val key = "${reference.type}:${LanguageNormalizer.normalize(reference.text)}"
            result + (key to ((result[key] ?: 0) + 1))
        }
        context = context.copy(
            activeCustomer = customer ?: context.activeCustomer,
            activeProduct = product ?: context.activeProduct,
            activeSupplier = interpretation.references.firstOrNull { it.type == LanguageEntityType.SUPPLIER }
                ?: context.activeSupplier,
            recentEntities = entities,
            recentIntent = interpretation.correction?.previousIntent ?: interpretation.intent,
            recentActions = (listOf(interpretation.intent) + context.recentActions).take(8),
            usageFrequency = frequencies,
            lastResolvedReference = interpretation.references.firstOrNull(),
            contextUpdatedAtEpochMs = System.currentTimeMillis(),
        )
        lastInterpretation = interpretation
        if (interpretation.intent != TinoIntent.CORRECTION && interpretation.intent != TinoIntent.NEGATION) {
            lastActionableInterpretation = interpretation
        }
    }

    fun rememberScreen(
        screen: String,
        primaryEntity: EntityReference? = null,
        secondaryEntities: List<EntityReference> = emptyList(),
    ) {
        context = context.copy(
            currentScreen = screen,
            activeCustomer = primaryEntity?.takeIf { it.type == LanguageEntityType.CUSTOMER } ?: context.activeCustomer,
            activeProduct = primaryEntity?.takeIf { it.type == LanguageEntityType.PRODUCT } ?: context.activeProduct,
            activeSupplier = primaryEntity?.takeIf { it.type == LanguageEntityType.SUPPLIER } ?: context.activeSupplier,
            recentEntities = (listOfNotNull(primaryEntity) + secondaryEntities + context.recentEntities)
                .distinctBy { it.type to LanguageNormalizer.normalize(it.text) }
                .take(8),
            lastResolvedReference = primaryEntity ?: context.lastResolvedReference,
            contextUpdatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    /** Keeps the screen entity but drops conversation-only memory after inactivity. */
    fun expireConversationIfNeeded(
        nowEpochMs: Long = System.currentTimeMillis(),
        ttlMs: Long = DEFAULT_CONVERSATION_TTL_MS,
    ) {
        val updatedAt = context.contextUpdatedAtEpochMs ?: return
        if (nowEpochMs - updatedAt <= ttlMs) return
        lastInterpretation = null
        lastActionableInterpretation = null
        context = context.copy(
            recentIntent = null,
            recentActions = emptyList(),
            lastResolvedReference = null,
        )
    }

    /** Clears a draft without erasing the screen/entity context. */
    fun clearConversationDraft() {
        lastInterpretation = null
        lastActionableInterpretation = null
        discardVoiceCorrection()
        context = context.copy(
            recentIntent = null,
            recentActions = emptyList(),
            lastResolvedReference = null,
        )
    }

    fun rememberAgentResult(result: String) {
        context = context.copy(lastAgentResult = result, contextUpdatedAtEpochMs = System.currentTimeMillis())
    }

    fun addLocalAlias(spoken: String, canonical: String) {
        val key = LanguageNormalizer.normalize(spoken)
        if (key.isBlank() || canonical.isBlank()) return
        context = context.copy(localAliases = context.localAliases + (key to canonical))
    }

    suspend fun learnFromCorrection(interpretation: IntentInterpretation) {
        val correction = interpretation.correction ?: return
        val previous = previousInterpretation() ?: return
        val entityType = when (correction.field) {
            LanguageCorrectionField.CUSTOMER -> LanguageEntityType.CUSTOMER
            LanguageCorrectionField.PRODUCT -> LanguageEntityType.PRODUCT
            else -> return
        }
        val spoken = previous.references.firstOrNull { it.type == entityType }?.text ?: return
        val learned = correctionLearningEngine.record(
            CorrectionEvent(
                spoken = spoken,
                canonical = correction.value,
                entityType = entityType,
                scope = CorrectionLearningScope.SESSION,
                scopeKey = correctionScopeKey,
                provenance = CorrectionProvenance.USER_CORRECTION,
            ),
        )
        val durable = businessMemory.record(
            MemoryCandidate(
                scopeKey = DEFAULT_BUSINESS_SCOPE_KEY,
                memoryKey = "entity_alias:${entityType.name}:${LanguageNormalizer.normalize(spoken)}",
                value = correction.value,
                kind = BusinessMemoryKind.ENTITY_ALIAS,
                confidence = MemoryConfidence(0.8),
                provenance = MemoryProvenance(
                    type = MemoryProvenanceType.USER_CORRECTION,
                    sourceInteractionId = interpretation.transcript,
                    occurredAtEpochMs = System.currentTimeMillis(),
                ),
            ),
        ).getOrNull()
        if (learned.status in setOf(CorrectionLearningStatus.LEARNED, CorrectionLearningStatus.TRUSTED) ||
            durable?.lifecycle in setOf(MemoryLifecycle.LEARNED, MemoryLifecycle.TRUSTED)
        ) {
            addLocalAlias(spoken, correction.value)
        }
    }

    /** Queues an edited voice turn so the next grounded interpretation can classify it. */
    fun queueVoiceCorrection(originalTranscript: String, correctedTranscript: String) {
        if (originalTranscript.isBlank() || correctedTranscript.isBlank() ||
            LanguageNormalizer.normalize(originalTranscript) == LanguageNormalizer.normalize(correctedTranscript)
        ) return
        pendingVoiceCorrection = PendingVoiceCorrection(originalTranscript, correctedTranscript)
        preparedVoiceCorrection = null
        lastVoiceCorrectionEvent = null
    }

    fun resetVoiceCorrectionTelemetry() {
        lastVoiceCorrectionEvent = null
    }

    /** Drops an uncommitted voice correction after cancellation or failed execution. */
    fun discardVoiceCorrection() {
        pendingVoiceCorrection = null
        preparedVoiceCorrection = null
        lastVoiceCorrectionEvent = null
    }

    /**
     * Prepares a conservative one-token transcript correction after the
     * interpreter has grounded references. The event is not persisted until
     * commitVoiceCorrection is called after successful execution.
     */
    suspend fun prepareVoiceCorrection(interpretation: IntentInterpretation) {
        val reference = interpretation.references.firstOrNull { candidate ->
            candidate.type == LanguageEntityType.PRODUCT || candidate.type == LanguageEntityType.CUSTOMER
        }
        prepareVoiceCorrection(reference)
    }

    /**
     * Completes correction grounding from a successful capability response.
     * Fast routes may resolve the entity outside the language interpreter, so
     * the response is also an authoritative grounding boundary.
     */
    suspend fun prepareVoiceCorrectionForResolvedReference(reference: EntityReference) {
        prepareVoiceCorrection(reference)
    }

    private suspend fun prepareVoiceCorrection(reference: EntityReference?) {
        val pending = pendingVoiceCorrection ?: return
        val originalTokens = LanguageNormalizer.normalize(pending.originalTranscript).split(' ').filter(String::isNotBlank)
        val correctedTokens = LanguageNormalizer.normalize(pending.correctedTranscript).split(' ').filter(String::isNotBlank)
        val removed = originalTokens.filter { it !in correctedTokens }
        val added = correctedTokens.filter { it !in originalTokens }
        if (removed.size != 1 || added.isEmpty()) {
            discardVoiceCorrection()
            return
        }
        val groundedReference = reference?.takeIf { candidate ->
            val referenceTokens = LanguageNormalizer.normalize(candidate.text).split(' ')
            candidate.type in setOf(LanguageEntityType.PRODUCT, LanguageEntityType.CUSTOMER) &&
                added.any { it in referenceTokens }
        } ?: run {
            discardVoiceCorrection()
            return
        }
        preparedVoiceCorrection = CorrectionEvent(
            spoken = removed.single(),
            canonical = LanguageNormalizer.normalize(groundedReference.text),
            entityType = groundedReference.type,
            scope = CorrectionLearningScope.STORE,
            scopeKey = DEFAULT_BUSINESS_SCOPE_KEY,
            provenance = CorrectionProvenance.USER_CORRECTION,
        )

        // QUEUED/PREPARED is not learning evidence. Persistence happens only
        // after the caller proves that the grounded operation succeeded.
    }

    /** Materializes a prepared correction after a successful grounded operation. */
    suspend fun commitVoiceCorrection(): CorrectionEvent? {
        val event = preparedVoiceCorrection ?: return null
        val pending = pendingVoiceCorrection ?: return null
        lastVoiceCorrectionEvent = event
        correctionLearningEngine.record(event)
        businessMemory.record(
            MemoryCandidate(
                scopeKey = DEFAULT_BUSINESS_SCOPE_KEY,
                memoryKey = "entity_alias:${event.entityType.name}:${LanguageNormalizer.normalize(event.spoken)}",
                value = event.canonical,
                kind = BusinessMemoryKind.ENTITY_ALIAS,
                confidence = MemoryConfidence(0.8),
                provenance = MemoryProvenance(
                    type = MemoryProvenanceType.USER_CORRECTION,
                    sourceInteractionId = pending.correctedTranscript,
                    occurredAtEpochMs = System.currentTimeMillis(),
                ),
            ),
        )
        pendingVoiceCorrection = null
        preparedVoiceCorrection = null
        return event
    }

    /** Restores durable interpretations/aliases; current facts remain Room-backed. */
    suspend fun restoreBusinessMemory(scopeKey: String = DEFAULT_BUSINESS_SCOPE_KEY) {
        businessMemory.list(scopeKey)
            .filter { it.kind == BusinessMemoryKind.ENTITY_ALIAS && it.lifecycle in setOf(MemoryLifecycle.LEARNED, MemoryLifecycle.TRUSTED) }
            .forEach { record ->
                val spoken = record.memoryKey.substringAfterLast(':')
                addLocalAlias(spoken, record.value)
            }
    }

    fun learnedAlias(spoken: String, entityType: LanguageEntityType): String? =
        correctionLearningEngine.resolve(
            spoken = spoken,
            entityType = entityType,
            scope = CorrectionLearningScope.SESSION,
            scopeKey = correctionScopeKey,
        )

    fun correctionEntries(): List<CorrectionLearningEntry> = correctionLearningEngine.entries(
        CorrectionLearningScope.SESSION,
        correctionScopeKey,
    )

    suspend fun demoteLearnedAlias(spoken: String, entityType: LanguageEntityType, reason: String) {
        correctionLearningEngine.demote(
            spoken = spoken,
            entityType = entityType,
            scope = CorrectionLearningScope.SESSION,
            scopeKey = correctionScopeKey,
            reason = reason,
        )
        businessMemory.demote(
            DEFAULT_BUSINESS_SCOPE_KEY,
            "entity_alias:${entityType.name}:${LanguageNormalizer.normalize(spoken)}",
            reason,
        )
        context = context.copy(localAliases = context.localAliases - LanguageNormalizer.normalize(spoken))
    }

    suspend fun removeLearnedAlias(spoken: String, entityType: LanguageEntityType) {
        correctionLearningEngine.remove(
            spoken = spoken,
            canonical = null,
            entityType = entityType,
            scope = CorrectionLearningScope.SESSION,
            scopeKey = correctionScopeKey,
        )
        businessMemory.remove(
            DEFAULT_BUSINESS_SCOPE_KEY,
            "entity_alias:${entityType.name}:${LanguageNormalizer.normalize(spoken)}",
        )
        context = context.copy(localAliases = context.localAliases - LanguageNormalizer.normalize(spoken))
    }

    fun clear() {
        context = CommerceContext()
        lastInterpretation = null
        lastActionableInterpretation = null
        discardVoiceCorrection()
        correctionLearningEngine.clearScope(CorrectionLearningScope.SESSION, correctionScopeKey)
    }
}

class ContextualLanguageInterpreter(
    private val base: LanguageIntentInterpreter,
    private val memory: CommerceContextMemory,
) : LanguageIntentInterpreter {
    override suspend fun interpret(input: LanguageInput): IntentInterpretation? {
        memory.restoreBusinessMemory()
        val interpretation = correctionOrNegation(input)
            ?: screenContextContinuation(input)
            ?: base.interpret(input)
            ?: continuation(input)
        val enriched = interpretation?.let {
            if (it.references.isNotEmpty() && it.referenceSources.isEmpty()) {
                it.copy(referenceSources = it.references.associate { reference ->
                    reference.type to ContextReferenceSource.EXPLICIT
                })
            } else {
                it
            }
        }
        enriched?.let {
            memory.learnFromCorrection(it)
            memory.prepareVoiceCorrection(it)
            memory.remember(it)
        }
        return enriched
    }

    private suspend fun screenContextContinuation(input: LanguageInput): IntentInterpretation? {
        val text = LanguageNormalizer.normalize(input.transcript)
        val product = memory.context.activeProduct
        val customer = memory.context.activeCustomer

        if (product != null && text in setOf("quanto eu vendo dele", "quanto custa ele", "qual o preco dele")) {
            return IntentInterpretation(
                intent = TinoIntent.READ_PRODUCT,
                references = listOf(product),
                source = input.source,
                transcript = input.transcript,
                classification = ContextTurnClassification.CONTINUATION,
                referenceSources = mapOf(LanguageEntityType.PRODUCT to ContextReferenceSource.SCREEN),
            )
        }

        if (product != null && text in setOf("e o preco", "e quanto custa", "quanto custa", "qual o preco")) {
            return IntentInterpretation(
                intent = TinoIntent.READ_PRODUCT,
                references = listOf(product),
                source = input.source,
                transcript = input.transcript,
                classification = ContextTurnClassification.CONTINUATION,
                referenceSources = mapOf(LanguageEntityType.PRODUCT to ContextReferenceSource.SCREEN),
            )
        }

        val hasProductPronoun = product != null && text.split(' ').any { it in setOf("dele", "dela", "esse", "aquele") }
        val hasCustomerPronoun = customer != null && text.split(' ').any { it in setOf("ele", "ela") }
        if (!hasProductPronoun && !hasCustomerPronoun) return null

        var rewritten = text
        product?.let {
            rewritten = rewritten.replace(Regex("\\b(dele|dela|esse|aquele)\\b"), it.text)
        }
        customer?.let {
            rewritten = rewritten.replace(Regex("\\b(ele|ela)\\b"), it.text)
        }
        return base.interpret(LanguageInput(rewritten, input.source, input.locale))
            ?.copy(
                transcript = input.transcript,
                classification = ContextTurnClassification.CONTINUATION,
                referenceSources = buildMap {
                    if (product != null && rewritten.contains(product.text)) {
                        put(LanguageEntityType.PRODUCT, ContextReferenceSource.SCREEN)
                    }
                    if (customer != null && rewritten.contains(customer.text)) {
                        put(LanguageEntityType.CUSTOMER, ContextReferenceSource.SCREEN)
                    }
                },
            )
    }

    private fun correctionOrNegation(input: LanguageInput): IntentInterpretation? {
        val text = LanguageNormalizer.normalize(input.transcript)
        if (!text.startsWith("nao")) return null
        val previous = memory.previousInterpretation()
        val body = text.removePrefix("nao").trimStart(' ', ',')
            .removePrefix("foi ")
            .removePrefix("era ")

        if (previous?.intent == TinoIntent.RECEIVE_CREDIT_PAYMENT) MoneyParser.parse(body)?.let { amount ->
            return IntentInterpretation(
                intent = TinoIntent.CORRECTION,
                references = previous?.references.orEmpty(),
                amountCents = amount,
                paymentMethod = previous?.paymentMethod,
                source = input.source,
                transcript = input.transcript,
                correction = LanguageCorrection(
                    field = LanguageCorrectionField.AMOUNT,
                    value = amount.toString(),
                    previousIntent = previous?.intent,
                ),
                classification = ContextTurnClassification.CORRECTION,
                referenceSources = previous?.references.orEmpty().associate { it.type to ContextReferenceSource.PENDING_ACTION },
            )
        }

        PaymentMethodLexicon.resolve(body)?.let { method ->
            return IntentInterpretation(
                intent = TinoIntent.CORRECTION,
                references = previous?.references.orEmpty(),
                amountCents = previous?.amountCents,
                paymentMethod = method,
                source = input.source,
                transcript = input.transcript,
                correction = LanguageCorrection(
                    field = LanguageCorrectionField.PAYMENT_METHOD,
                    value = method.name,
                    previousIntent = previous?.intent,
                ),
                classification = ContextTurnClassification.CORRECTION,
                referenceSources = previous?.references.orEmpty().associate { it.type to ContextReferenceSource.PENDING_ACTION },
            )
        }

        val correctedQuantity = QuantityParser.parseCountPrefix(body)
        if (correctedQuantity != null && previous?.quantity != null) {
            return IntentInterpretation(
                intent = TinoIntent.CORRECTION,
                references = previous.references,
                quantity = ParsedQuantity(correctedQuantity.toBigDecimal()),
                source = input.source,
                transcript = input.transcript,
                correction = LanguageCorrection(
                    field = LanguageCorrectionField.QUANTITY,
                    value = correctedQuantity.toString(),
                    previousIntent = previous.intent,
                ),
                classification = ContextTurnClassification.CORRECTION,
                referenceSources = previous.references.associate { it.type to ContextReferenceSource.PENDING_ACTION },
            )
        }

        val correctedProduct = body.removePrefix("e ").removePrefix("é ").trim()
        val likelyCustomerName = correctedProduct.split(' ').firstOrNull() in setOf(
            "maria", "chico", "joao", "joana", "jose", "ana", "pedro",
        )
        if (previous?.references?.any { it.type == LanguageEntityType.CUSTOMER } == true &&
            correctedProduct.isNotBlank() &&
            (previous.references.none { it.type == LanguageEntityType.PRODUCT } || likelyCustomerName)
        ) {
            return IntentInterpretation(
                intent = TinoIntent.CORRECTION,
                references = previous.references.filterNot { it.type == LanguageEntityType.CUSTOMER } +
                    EntityReference(LanguageEntityType.CUSTOMER, correctedProduct),
                quantity = previous.quantity,
                amountCents = previous.amountCents,
                paymentMethod = previous.paymentMethod,
                source = input.source,
                transcript = input.transcript,
                correction = LanguageCorrection(
                    field = LanguageCorrectionField.CUSTOMER,
                    value = correctedProduct,
                    previousIntent = previous.intent,
                ),
                classification = ContextTurnClassification.CORRECTION,
                referenceSources = mapOf(LanguageEntityType.CUSTOMER to ContextReferenceSource.EXPLICIT),
            )
        }
        if (previous?.references?.any { it.type == LanguageEntityType.PRODUCT } == true && correctedProduct.isNotBlank()) {
            return IntentInterpretation(
                intent = TinoIntent.CORRECTION,
                references = previous.references.filterNot { it.type == LanguageEntityType.PRODUCT } +
                    EntityReference(LanguageEntityType.PRODUCT, correctedProduct),
                quantity = previous.quantity,
                source = input.source,
                transcript = input.transcript,
                correction = LanguageCorrection(
                    field = LanguageCorrectionField.PRODUCT,
                    value = correctedProduct,
                    previousIntent = previous.intent,
                ),
                classification = ContextTurnClassification.CORRECTION,
                referenceSources = mapOf(LanguageEntityType.PRODUCT to ContextReferenceSource.EXPLICIT),
            )
        }

        return IntentInterpretation(
            intent = TinoIntent.NEGATION,
            references = previous?.references.orEmpty(),
            source = input.source,
            transcript = input.transcript,
            negated = true,
            classification = ContextTurnClassification.CANCELLATION,
        )
    }

    private fun continuation(input: LanguageInput): IntentInterpretation? {
        val text = LanguageNormalizer.normalize(input.transcript)
        val activeCustomer = memory.context.activeCustomer ?: return null
        if ((text.contains("pagou") || text.contains("recebeu")) &&
            (text.contains("mes") || text.contains("mês")) &&
            text.split(' ').any { it in setOf("ela", "ele", "elela") }
        ) {
            return IntentInterpretation(
                intent = TinoIntent.READ_CUSTOMER_TIMELINE,
                references = listOf(activeCustomer),
                source = input.source,
                transcript = input.transcript,
                classification = ContextTurnClassification.CONTINUATION,
                referenceSources = mapOf(LanguageEntityType.CUSTOMER to ContextReferenceSource.CONVERSATION),
            )
        }
        if (text == "quanto ficou" || text == "quanto ficou a conta" || text == "qual ficou o saldo") {
            return IntentInterpretation(
                intent = TinoIntent.READ_CUSTOMER_BALANCE,
                references = listOf(activeCustomer),
                source = input.source,
                transcript = input.transcript,
                classification = ContextTurnClassification.CONTINUATION,
                referenceSources = mapOf(LanguageEntityType.CUSTOMER to ContextReferenceSource.CONVERSATION),
            )
        }
        if (memory.context.recentIntent in setOf(TinoIntent.READ_CUSTOMER_BALANCE, TinoIntent.READ_CUSTOMER_TIMELINE) &&
            text.startsWith("e ") &&
            !text.startsWith("e mais ") &&
            text.removePrefix("e ").isNotBlank()
        ) {
            return IntentInterpretation(
                intent = if (memory.context.recentIntent == TinoIntent.READ_CUSTOMER_TIMELINE) {
                    TinoIntent.READ_CUSTOMER_TIMELINE
                } else {
                    TinoIntent.READ_CUSTOMER_BALANCE
                },
                references = listOf(
                    EntityReference(
                        LanguageEntityType.CUSTOMER,
                        text.removePrefix("e ").trim().removePrefix("o ").removePrefix("a ").trim(),
                    ),
                ),
                source = input.source,
                transcript = input.transcript,
                classification = ContextTurnClassification.CONTINUATION,
                referenceSources = mapOf(LanguageEntityType.CUSTOMER to ContextReferenceSource.EXPLICIT),
            )
        }
        val prefix = when {
            text.startsWith("e mais ") -> "e mais "
            text.startsWith("mais ") -> "mais "
            else -> return null
        }
        val body = text.removePrefix(prefix).trim()
        if (body.isBlank()) return null
        val quantity = QuantityParser.parseCountPrefix(body) ?: 1
        val product = body
            .replace(Regex("^(?:\\d+|um|uma|dois|duas|tres|quatro|cinco|seis|sete|oito|nove|dez)\\s+"), "")
            .trim()
            .takeIf { it.isNotBlank() } ?: return null
        return IntentInterpretation(
            intent = TinoIntent.ADD_CREDIT_ITEM,
            subintent = CommerceSubintent.ADD_ITEM,
            references = listOf(
                activeCustomer,
                EntityReference(LanguageEntityType.PRODUCT, product),
            ),
            quantity = ParsedQuantity(quantity.toBigDecimal()),
            source = input.source,
            transcript = input.transcript,
            classification = ContextTurnClassification.CONTINUATION,
            referenceSources = mapOf(LanguageEntityType.CUSTOMER to ContextReferenceSource.CONVERSATION),
        )
    }
}

private data class PendingVoiceCorrection(
    val originalTranscript: String,
    val correctedTranscript: String,
)
