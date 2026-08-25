package com.tino.app.domain.language

import com.tino.app.domain.commerce.PaymentMethod

enum class LanguageSource {
    VOICE,
    TEXT,
}

enum class LanguageEntityType {
    CUSTOMER,
    PRODUCT,
    SUPPLIER,
}

enum class TinoIntent {
    READ_FINANCIAL_SUMMARY,
    READ_RECEIVABLES,
    ADD_CREDIT,
    ADD_CREDIT_ITEM,
    RECEIVE_CREDIT_PAYMENT,
    SEARCH_PRODUCT,
    READ_PRODUCT,
    READ_STOCK,
    REGISTER_STOCK_ENTRY,
    SEARCH_CUSTOMER,
    READ_CUSTOMER_BALANCE,
    READ_CUSTOMER_TIMELINE,
    SEARCH_SUPPLIER,
    CHANGE_PRICE,
    CORRECTION,
    NEGATION,
    COMPOUND,
}

/** Classification of a turn before it is allowed to reach a capability. */
enum class ContextTurnClassification {
    CONTINUATION,
    CORRECTION,
    CONFIRMATION,
    CANCELLATION,
    NEW_INTENT,
}

/** Where a resolved reference came from. Explicit speech always has priority. */
enum class ContextReferenceSource {
    EXPLICIT,
    PENDING_ACTION,
    CONVERSATION,
    SCREEN,
    RECENT,
    LEARNED,
}

enum class CommerceSubintent {
    ADD_ITEM,
    REGISTER_RECEIPT,
    READ_BALANCE,
    REGISTER_STOCK,
}

enum class LanguageCorrectionField {
    PAYMENT_METHOD,
    QUANTITY,
    CUSTOMER,
    PRODUCT,
    AMOUNT,
}

data class LanguageCorrection(
    val field: LanguageCorrectionField,
    val value: String,
    val previousIntent: TinoIntent? = null,
)

data class LanguageInput(
    val transcript: String,
    val source: LanguageSource = LanguageSource.VOICE,
    val locale: String = "pt-BR",
)

data class EntityReference(
    val type: LanguageEntityType,
    val text: String,
)

data class IntentInterpretation(
    val intent: TinoIntent,
    val references: List<EntityReference> = emptyList(),
    val quantity: ParsedQuantity? = null,
    val amountCents: Long? = null,
    val paymentMethod: PaymentMethod? = null,
    val source: LanguageSource,
    val transcript: String,
    val confidence: Float? = null,
    val subintent: CommerceSubintent? = null,
    val negated: Boolean = false,
    val correction: LanguageCorrection? = null,
    val operations: List<IntentInterpretation> = emptyList(),
    val missingSlots: Set<String> = emptySet(),
    val classification: ContextTurnClassification? = null,
    val referenceSources: Map<LanguageEntityType, ContextReferenceSource> = emptyMap(),
)

data class CommerceContext(
    val activeCustomer: EntityReference? = null,
    val activeProduct: EntityReference? = null,
    val activeSupplier: EntityReference? = null,
    val recentEntities: List<EntityReference> = emptyList(),
    val recentIntent: TinoIntent? = null,
    val currentScreen: String? = null,
    val recentActions: List<TinoIntent> = emptyList(),
    val storeVocabulary: Set<String> = emptySet(),
    val localAliases: Map<String, String> = emptyMap(),
    val usageFrequency: Map<String, Int> = emptyMap(),
    val lastResolvedReference: EntityReference? = null,
    val lastAgentResult: String? = null,
    val contextUpdatedAtEpochMs: Long? = null,
)

sealed interface LanguageEntityResolution<out T> {
    data class Resolved<T>(
        val entity: T,
        val confidence: Float? = null,
    ) : LanguageEntityResolution<T>

    data class Ambiguous<T>(
        val candidates: List<T>,
        val reason: String = "Mais de uma entidade corresponde à referência.",
    ) : LanguageEntityResolution<T>

    data object NotFound : LanguageEntityResolution<Nothing>

    data class NeedsClarification(
        val reason: String,
    ) : LanguageEntityResolution<Nothing>
}

interface LanguageEntityResolver<T> {
    suspend fun resolve(reference: String): LanguageEntityResolution<T>
}

interface LanguageIntentInterpreter {
    suspend fun interpret(input: LanguageInput): IntentInterpretation?
}
