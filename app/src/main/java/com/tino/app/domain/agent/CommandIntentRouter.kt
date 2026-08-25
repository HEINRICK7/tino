package com.tino.app.domain.agent

import java.text.Normalizer
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CommandIntentResult {
    data class Match(
        val tool: TinoToolId,
        val intent: AgentIntent,
    ) : CommandIntentResult

    data object NoMatch : CommandIntentResult
}

/**
 * Deterministic router for common write commands. It extracts only the
 * references spoken by the merchant; entity IDs and all factual values are
 * still resolved by the canonical capability/domain pipeline.
 */
@Singleton
class CommandIntentRouter @Inject constructor() {
    fun route(input: String): CommandIntentResult {
        val text = normalize(input)
        if (text.isBlank()) return CommandIntentResult.NoMatch

        parseCreditPayment(text)?.let { payment ->
            val customer = payment.customer.removeCustomerTitle()
            if (customer.isNotBlank()) {
                return CommandIntentResult.Match(
                    tool = TinoToolId.CREDIT_PAYMENT,
                    intent = AgentIntent(
                        schemaVersion = AgentIntentSchema.VERSION,
                        capability = AgentCapability.REGISTER_CREDIT_PAYMENT,
                        period = AgentIntentPeriod.TODAY,
                        customerRef = customer,
                        amountCents = payment.amountCents,
                        creditPaymentMethod = payment.paymentMethod,
                    ),
                )
            }
        }
        if (!looksLikeCreditCommand(text)) return CommandIntentResult.NoMatch

        val parts = when {
            text.startsWith("adicionar ") -> parseAddToAccount(text)
            text.contains(" comprou fiado ") -> parseBoughtOnCredit(text)
            text.contains(" levou ") && text.endsWith(" fiado") -> parseTookOnCredit(text)
            text.startsWith("anota ") -> parseNoteForCustomer(text)
            else -> null
        } ?: return CommandIntentResult.NoMatch

        val product = parts.product.removeLeadingQuantityAndArticle()
        val customer = parts.customer.removeCustomerTitle()
        if (product.isBlank() || customer.isBlank()) return CommandIntentResult.NoMatch

        return CommandIntentResult.Match(
            tool = TinoToolId.CREDIT_ADD,
            intent = AgentIntent(
                schemaVersion = AgentIntentSchema.VERSION,
                capability = AgentCapability.ADD_CREDIT_ITEM,
                period = AgentIntentPeriod.TODAY,
                customerRef = customer,
                productRef = product,
                quantity = parts.product.leadingQuantity() ?: 1,
            ),
        )
    }

    private fun looksLikeCreditCommand(text: String): Boolean =
        text.contains("fiado") ||
            text.contains("na conta") ||
            text.contains("no caderno") ||
            text.startsWith("anota ")

    private fun parseAddToAccount(text: String): CommandParts? {
        val marker = listOf(" na conta da ", " na conta do ", " na conta de ")
            .firstOrNull { text.contains(it) } ?: return null
        val split = text.split(marker, limit = 2)
        if (split.size != 2) return null
        return CommandParts(split[0].removePrefix("adicionar").trim(), split[1])
    }

    private fun parseBoughtOnCredit(text: String): CommandParts? {
        val split = text.split(" comprou fiado ", limit = 2)
        if (split.size != 2) return null
        return CommandParts(split[1], split[0])
    }

    private fun parseTookOnCredit(text: String): CommandParts? {
        val withoutSuffix = text.removeSuffix(" fiado").trim()
        val split = withoutSuffix.split(" levou ", limit = 2)
        if (split.size != 2) return null
        return CommandParts(split[1], split[0])
    }

    private fun parseNoteForCustomer(text: String): CommandParts? {
        val split = text.removePrefix("anota ").split(" pra ", limit = 2)
        if (split.size != 2) return null
        return CommandParts(split[0], split[1])
    }

    private fun parseCreditPayment(text: String): PaymentParts? {
        val amountMatch = Regex("(?:r\\$\\s*)?(\\d+(?:[.,]\\d{1,2})?)\\s*(?:reais|real)?\\b")
            .find(text) ?: return null
        val amountCents = runCatching {
            BigDecimal(amountMatch.groupValues[1].replace(',', '.'))
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        }.getOrNull()?.takeIf { it > 0 } ?: return null
        val before = text.substring(0, amountMatch.range.first).trim()
        val after = text.substring(amountMatch.range.last + 1)
            .trim()
            .removePaymentMethodSuffix()
        val payerMatch = Regex("^(.+?) (?:me )?(pagou|deu|acertou)$").find(before)
        val customer = when {
            Regex("^recebi$").matches(before) -> after
                .removePrefix("da conta da ")
                .removePrefix("do fiado da ")
                .removePrefix("da ")
                .removePrefix("de ")
            Regex("^baixa$").matches(before) -> after
                .removePrefix("do fiado da ")
                .removePrefix("da conta da ")
                .removePrefix("da ")
            payerMatch != null -> payerMatch.groupValues[1]
            else -> ""
        }.trim().removeSuffix(".")
        val hasCreditContext = text.contains("fiado") || text.contains("conta") ||
            text.startsWith("recebi ") || text.startsWith("baixa ") ||
            Regex("(?:pagou|deu|acertou)").containsMatchIn(text)
        if (!hasCreditContext || customer.isBlank()) return null
        val paymentMethod = when {
            text.contains("pix") -> com.tino.app.domain.commerce.PaymentMethod.PIX
            text.contains("maquininha") || text.contains("cartao") -> com.tino.app.domain.commerce.PaymentMethod.CARD
            text.contains("dinheiro") -> com.tino.app.domain.commerce.PaymentMethod.CASH
            else -> null
        }
        return PaymentParts(customer, amountCents, paymentMethod)
    }

    private fun String.leadingQuantity(): Int? = Regex("^(\\d+)\\b").find(trim())?.groupValues?.get(1)?.toIntOrNull()

    private fun String.removeLeadingQuantityAndArticle(): String = replace(Regex("^\\d+\\s+"), "")
        .replace(Regex("^(um|uma)\\s+"), "")
        .trim()

    private fun String.removeCustomerTitle(): String = removePrefix("dona ").removePrefix("don ").trim()

    private fun String.removePaymentMethodSuffix(): String {
        val value = trim()
        val suffixes = listOf(
            "no pix",
            "via pix",
            "em pix",
            "no dinheiro",
            "em dinheiro",
            "na maquininha",
            "no cartao",
        )
        return suffixes.firstNotNullOfOrNull { suffix ->
            when {
                value == suffix -> ""
                value.endsWith(" $suffix") -> value.removeSuffix(" $suffix").trim()
                else -> null
            }
        } ?: value
    }

    private fun normalize(input: String): String = Normalizer
        .normalize(input.lowercase(), Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .replace("[^a-z0-9]+".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

    private data class CommandParts(val product: String, val customer: String)

    private data class PaymentParts(
        val customer: String,
        val amountCents: Long,
        val paymentMethod: com.tino.app.domain.commerce.PaymentMethod?,
    )
}
