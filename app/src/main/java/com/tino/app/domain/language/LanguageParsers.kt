package com.tino.app.domain.language

import java.math.BigDecimal
import java.math.RoundingMode

data class ParsedQuantity(
    val amount: BigDecimal,
    val unit: CommercialUnit = CommercialUnit.UNIT,
    val sourceUnit: CommercialUnit? = null,
    val requiresCatalogPackaging: Boolean = unit == CommercialUnit.BOX || unit == CommercialUnit.PACKAGE || unit == CommercialUnit.BUNDLE,
) {
    val wholeUnits: Int? get() = runCatching {
        amount.setScale(0, RoundingMode.UNNECESSARY).intValueExact()
    }.getOrNull()
}

object QuantityParser {
    private val numberWords = mapOf(
        "zero" to 0, "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2,
        "tres" to 3, "quatro" to 4, "cinco" to 5, "seis" to 6, "sete" to 7,
        "oito" to 8, "nove" to 9, "dez" to 10, "onze" to 11, "doze" to 12,
        "treze" to 13, "quatorze" to 14, "quinze" to 15, "dezesseis" to 16,
        "dezessete" to 17, "dezoito" to 18, "dezenove" to 19, "vinte" to 20,
        "trinta" to 30, "quarenta" to 40, "cinquenta" to 50,
    )

    fun parse(value: String): ParsedQuantity? {
        val text = LanguageNormalizer.normalize(value)
        if (text == "meia duzia") return ParsedQuantity(BigDecimal(6), CommercialUnit.UNIT, CommercialUnit.DOZEN, false)
        if (text == "uma duzia" || text == "um duzia") return ParsedQuantity(BigDecimal(12), CommercialUnit.UNIT, CommercialUnit.DOZEN, false)

        val fraction = Regex("^(meio|meia)\\s+(.+)$").matchEntire(text)
        if (fraction != null) {
            val unit = UnitLexicon.resolve(fraction.groupValues[2]) ?: return null
            return ParsedQuantity(BigDecimal("0.5"), unit, unit)
        }

        val kiloAndHalf = Regex("^(.+?)\\s+quilo(?:grama)?s?\\s+e\\s+meio$").matchEntire(text)
        if (kiloAndHalf != null) {
            val whole = parseNumber(kiloAndHalf.groupValues[1]) ?: return null
            return ParsedQuantity(BigDecimal(whole).add(BigDecimal("0.5")), CommercialUnit.KILOGRAM, CommercialUnit.KILOGRAM)
        }

        val numeric = Regex("^(\\d+(?:[.,]\\d+)?)\\s*(.*)$").matchEntire(text)
        if (numeric != null) {
            val amount = numeric.groupValues[1].replace(',', '.').toBigDecimalOrNull() ?: return null
            val unit = numeric.groupValues[2].trim().takeIf { it.isNotBlank() }?.let(UnitLexicon::resolve)
                ?: CommercialUnit.UNIT
            return ParsedQuantity(amount, unit, unit.takeUnless { unit == CommercialUnit.UNIT && numeric.groupValues[2].isBlank() })
        }

        val words = text.split(' ')
        val unitIndex = words.indexOfFirst { UnitLexicon.resolve(it) != null }
        if (unitIndex <= 0) return null
        val unit = UnitLexicon.resolve(words[unitIndex]) ?: return null
        val quantityText = words.take(unitIndex).joinToString(" ")
        val amount = parseNumber(quantityText)?.let(::BigDecimal) ?: return null
        return ParsedQuantity(amount, unit, unit)
    }

    fun parseCountPrefix(value: String): Int? {
        val text = LanguageNormalizer.normalize(value)
        val numeric = Regex("^(\\d+)\\b").find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (numeric != null) return numeric
        val words = text.split(' ')
        val numberWordsText = when {
            words.size >= 3 && words[1] == "e" -> words.take(3).joinToString(" ")
            else -> words.firstOrNull().orEmpty()
        }
        return parseNumber(numberWordsText)
    }

    private fun parseNumber(value: String): Int? {
        numberWords[value]?.let { return it }
        val parts = value.split(" e ")
        if (parts.size == 2) {
            val tens = numberWords[parts[0]] ?: return null
            val units = numberWords[parts[1]] ?: return null
            return tens + units
        }
        return null
    }

    private fun BigDecimal.toIntExactOrNull(): Int? = runCatching {
        setScale(0, RoundingMode.UNNECESSARY).intValueExact()
    }.getOrNull()
}

object MoneyParser {
    private val wordValues = mapOf(
        "zero" to 0L, "um" to 1L, "uma" to 1L, "dois" to 2L, "duas" to 2L,
        "tres" to 3L, "quatro" to 4L, "cinco" to 5L, "seis" to 6L, "sete" to 7L,
        "oito" to 8L, "nove" to 9L, "dez" to 10L, "onze" to 11L, "doze" to 12L,
        "treze" to 13L, "quatorze" to 14L, "quinze" to 15L, "vinte" to 20L,
        "trinta" to 30L, "quarenta" to 40L, "cinquenta" to 50L,
    )

    fun parse(value: String): Long? {
        val text = LanguageNormalizer.normalize(value)
        val numeric = Regex("(?:r\\s*)?(\\d+(?:[.,]\\d{1,2})?)\\s*(?:reais|real|conto)?").matchEntire(text)
        if (numeric != null) {
            return numeric.groupValues[1].replace(',', '.').toBigDecimalOrNull()
                ?.movePointRight(2)
                ?.setScale(0, RoundingMode.UNNECESSARY)
                ?.longValueExact()
                ?.takeIf { it > 0L }
        }
        val words = text
            .removeSuffix(" reais")
            .removeSuffix(" real")
            .removeSuffix(" conto")
            .removeSuffix(" centavos")
            .trim()
            .replace(" reais e ", " e ")
            .replace(" real e ", " e ")
        val reaisAndCents = Regex("^(.+?)\\s+e\\s+(\\w+)$").matchEntire(words)
        if (reaisAndCents != null) {
            val reais = parseWholeWords(reaisAndCents.groupValues[1]) ?: return null
            val cents = wordValues[reaisAndCents.groupValues[2]] ?: return null
            return reais * 100L + cents
        }
        return parseWholeWords(words)?.times(100L)?.takeIf { it > 0L }
    }

    private fun parseWholeWords(value: String): Long? {
        wordValues[value]?.let { return it }
        val parts = value.split(" e ")
        if (parts.size == 2) {
            val first = wordValues[parts[0]] ?: return null
            val second = wordValues[parts[1]] ?: return null
            return first + second
        }
        return null
    }
}
