package com.tino.app.domain.voice

import java.math.BigDecimal
import java.math.RoundingMode

enum class VoiceContext {
    ONBOARDING,
    PRODUCT_CREATE,
    STOCK_RECEIPT,
    CUSTOMER_CREATE,
    SUPPLIER_CREATE,
    CREDIT_SALE,
    SALE,
    GLOBAL,
}

data class VoiceExtraction(
    val context: VoiceContext,
    val transcript: String,
    val fields: Map<String, String>,
)

sealed interface VoiceInputResult {
    data class Extracted(val value: VoiceExtraction) : VoiceInputResult
    data class NeedsCorrection(
        val value: VoiceExtraction,
        val missingFields: Set<String>,
        val invalidFields: Set<String>,
        val message: String,
    ) : VoiceInputResult
    data class Unavailable(val reason: String) : VoiceInputResult
    data class Failed(val reason: String) : VoiceInputResult
}

interface VoiceInputPort {
    suspend fun listen(
        context: VoiceContext,
        onCommitted: (String) -> Unit = {},
        onTranscript: (String) -> Unit = {},
    ): VoiceInputResult

    suspend fun stop() = Unit
}

sealed interface VoiceValidationResult {
    data class Valid(
        val value: VoiceExtraction,
        val ignoredFields: Set<String> = emptySet(),
    ) : VoiceValidationResult

    data class NeedsCorrection(
        val value: VoiceExtraction,
        val missingFields: Set<String>,
        val invalidFields: Set<String>,
        val ignoredFields: Set<String>,
        val message: String,
    ) : VoiceValidationResult
}

private enum class VoiceFieldType {
    TEXT,
    PHONE,
    POSITIVE_INTEGER,
    POSITIVE_MONEY,
}

private data class VoiceFieldRule(
    val type: VoiceFieldType,
    val required: Boolean,
)

/**
 * Keeps model output inside the context contract before it reaches a screen.
 * It never saves data and it deliberately ignores fields outside the allowlist.
 */
object VoiceExtractionValidator {
    private val rulesByContext = mapOf(
        VoiceContext.ONBOARDING to rules(
            required = setOf("store_name", "owner_name", "phone"),
        ),
        VoiceContext.PRODUCT_CREATE to rules(
            required = setOf("product_name", "sale_price"),
            optional = setOf("stock_initial", "size", "unit"),
        ),
        VoiceContext.STOCK_RECEIPT to rules(
            required = setOf("product", "quantity"),
            optional = setOf("boxes", "units_per_box", "unit_cost", "supplier"),
        ),
        VoiceContext.CUSTOMER_CREATE to rules(
            required = setOf("name", "phone"),
        ),
        VoiceContext.SUPPLIER_CREATE to rules(
            required = setOf("name", "phone"),
        ),
        VoiceContext.CREDIT_SALE to rules(
            required = setOf("customer"),
            optional = setOf("products", "quantity"),
        ),
        VoiceContext.SALE to rules(
            required = setOf("products"),
            optional = setOf("quantity", "payment_method"),
        ),
        VoiceContext.GLOBAL to rules(
            required = setOf("intent"),
        ),
    )

    fun validate(extraction: VoiceExtraction): VoiceValidationResult {
        val rules = rulesByContext.getValue(extraction.context)
        val normalizedFields = linkedMapOf<String, String>()
        val missingFields = linkedSetOf<String>()
        val invalidFields = linkedSetOf<String>()
        val ignoredFields = extraction.fields.keys - rules.keys

        rules.forEach { (field, rule) ->
            val rawValue = extraction.fields[field]
            if (rawValue == null || rawValue.isBlank()) {
                if (rule.required) missingFields += field
                return@forEach
            }

            when (val normalizedValue = normalize(rawValue, rule.type)) {
                null -> invalidFields += field
                else -> normalizedFields[field] = normalizedValue
            }
        }

        val normalizedExtraction = extraction.copy(fields = normalizedFields)
        if (missingFields.isEmpty() && invalidFields.isEmpty()) {
            return VoiceValidationResult.Valid(normalizedExtraction, ignoredFields)
        }

        return VoiceValidationResult.NeedsCorrection(
            value = normalizedExtraction,
            missingFields = missingFields,
            invalidFields = invalidFields,
            ignoredFields = ignoredFields,
            message = correctionMessage(missingFields, invalidFields),
        )
    }

    private fun normalize(rawValue: String, type: VoiceFieldType): String? {
        val value = rawValue.trim().replace(WHITESPACE, " ")
        if (value.isBlank()) return null

        return when (type) {
            VoiceFieldType.TEXT -> value
            VoiceFieldType.PHONE -> normalizePhone(value)
            VoiceFieldType.POSITIVE_INTEGER -> normalizePositiveInteger(value)
            VoiceFieldType.POSITIVE_MONEY -> normalizePositiveMoney(value)
        }
    }

    private fun normalizePhone(value: String): String? {
        val digits = value.filter(Char::isDigit)
        return digits.takeIf { it.length in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS }
    }

    private fun normalizePositiveInteger(value: String): String? {
        if (!value.matches(POSITIVE_INTEGER)) return null
        return value.toLongOrNull()?.takeIf { it > 0 }?.toString()
    }

    private fun normalizePositiveMoney(value: String): String? {
        val number = value
            .replace("R$", "", ignoreCase = true)
            .replace(" ", "")
            .replaceThousandsAndDecimalSeparators()
            .takeIf { it.matches(MONEY_NUMBER) }
            ?.let { runCatching { BigDecimal(it) }.getOrNull() }
            ?: return null

        if (number.signum() <= 0) return null
        return number.setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString()
    }

    private fun String.replaceThousandsAndDecimalSeparators(): String = when {
        contains(',') && contains('.') -> {
            if (lastIndexOf(',') > lastIndexOf('.')) {
                replace(".", "").replace(',', '.')
            } else {
                replace(",", "")
            }
        }
        contains(',') -> replace(',', '.')
        else -> this
    }

    private fun correctionMessage(
        missingFields: Set<String>,
        invalidFields: Set<String>,
    ): String = buildList {
        if (missingFields.isNotEmpty()) {
            add("Falta informar: ${missingFields.joinToString { fieldLabel(it) }}.")
        }
        if (invalidFields.isNotEmpty()) {
            add("Revise: ${invalidFields.joinToString { fieldLabel(it) }}.")
        }
    }.joinToString(" ")

    private fun fieldLabel(field: String): String = FIELD_LABELS[field] ?: field

    private fun rules(
        required: Set<String>,
        optional: Set<String> = emptySet(),
    ): Map<String, VoiceFieldRule> = (required + optional).associateWith { field ->
        VoiceFieldRule(fieldType(field), field in required)
    }

    private fun fieldType(field: String): VoiceFieldType = when (field) {
        "phone" -> VoiceFieldType.PHONE
        "sale_price", "unit_cost" -> VoiceFieldType.POSITIVE_MONEY
        "boxes", "units_per_box", "quantity", "stock_initial" -> VoiceFieldType.POSITIVE_INTEGER
        else -> VoiceFieldType.TEXT
    }

    private val WHITESPACE = Regex("\\s+")
    private val POSITIVE_INTEGER = Regex("[0-9]+")
    private val MONEY_NUMBER = Regex("[0-9]+(?:\\.[0-9]{1,2})?")
    private const val MIN_PHONE_DIGITS = 10
    private const val MAX_PHONE_DIGITS = 13
    private const val MONEY_SCALE = 2
    private val FIELD_LABELS = mapOf(
        "store_name" to "nome do comércio",
        "owner_name" to "seu nome",
        "phone" to "celular",
        "product_name" to "produto",
        "sale_price" to "preço de venda",
        "stock_initial" to "estoque inicial",
        "unit_cost" to "custo unitário",
        "product" to "produto",
        "quantity" to "quantidade",
        "supplier" to "fornecedor",
        "boxes" to "caixas",
        "units_per_box" to "unidades por caixa",
        "name" to "nome",
        "customer" to "cliente",
        "products" to "produtos",
        "payment_method" to "forma de pagamento",
        "intent" to "intenção",
    )
}
