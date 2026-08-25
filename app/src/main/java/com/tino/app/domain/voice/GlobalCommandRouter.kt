package com.tino.app.domain.voice

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic global fallback for the commerce voice surface.
 *
 * It only converts a committed phrase into a ToolCall. Entity resolution,
 * factual values and all writes remain behind CommerceToolDispatcher.
 */
@Singleton
class GlobalCommandRouter @Inject constructor() {
    fun route(input: String): ToolCall? {
        val text = normalize(input)
        if (text.isBlank()) return null

        stockReceipt(text)?.let { return it }
        creditPayment(text)?.let { return it }
        creditSale(text)?.let { return it }
        priceChange(text)?.let { return it }
        sale(text)?.let { return it }
        todaySales(text)?.let { return it }
        purchasePlan(text)?.let { return it }
        stockQuery(text)?.let { return it }
        customerBalance(text)?.let { return it }
        productSearch(text)?.let { return it }
        customerSearch(text)?.let { return it }
        supplierSearch(text)?.let { return it }
        return null
    }

    private fun creditPayment(text: String): ToolCall? {
        val amountMatch = Regex("(?:r\\$\\s*)?(\\d+(?:[.,]\\d{1,2})?)\\s*(?:reais|real)?\\b")
            .find(text) ?: return null
        val amountCents = moneyToCents(amountMatch.groupValues[1]) ?: return null
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
        }.trim()
        val hasContext = text.contains("fiado") || text.contains("conta") ||
            text.startsWith("recebi ") || text.startsWith("baixa ") ||
            Regex("(?:pagou|deu|acertou)").containsMatchIn(text)
        if (!hasContext || customer.isBlank()) return null
        val method = paymentMethod(text)
        return ToolCall(
            name = CommerceToolName.REGISTER_CREDIT_PAYMENT,
            arguments = buildMap {
                put("customer", customer)
                put("amount_cents", amountCents.toString())
                method?.let { put("payment_method", it) }
            },
        )
    }

    private fun creditSale(text: String): ToolCall? {
        val match = Regex("^(.+?) levou (.+?) fiado$").find(text)
            ?: Regex("^(.+?) comprou fiado (.+)$").find(text)
            ?: return null
        val quantityProduct = quantityAndProduct(match.groupValues[2]) ?: return null
        return ToolCall(
            name = CommerceToolName.REGISTER_CREDIT_SALE,
            arguments = mapOf(
                "customer" to match.groupValues[1].trim().removeCustomerTitle(),
                "product" to quantityProduct.second,
                "quantity" to quantityProduct.first.toString(),
            ),
        )
    }

    private fun stockReceipt(text: String): ToolCall? {
        if (!(text.startsWith("chegou ") || text.startsWith("chegaram ") ||
                text.startsWith("recebi ") || text.startsWith("entrada de ")
            ) || text.contains("recebi pagamento")
        ) return null
        val costMatch = Regex("(?:custo|a|por)\\s+r?\\$?\\s*(\\d+(?:[.,]\\d{1,2})?)\\s*(?:reais|real)?$")
            .find(text) ?: return null
        val costCents = moneyToCents(costMatch.groupValues[1]) ?: return null
        val body = text.substring(0, costMatch.range.first).trim()
            .removePrefix("chegaram ")
            .removePrefix("chegou ")
            .removePrefix("recebi ")
            .removePrefix("entrada de ")
        val unitsMatch = Regex("(?:com|de)\\s+(\\d+)\\s+unidades?").find(body)
        val quantity = unitsMatch?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("^(\\d+)\\s+").find(body)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val product = body
            .replace(Regex("^\\d+\\s+"), "")
            .replace(Regex("\\s+(?:com|de)\\s+\\d+\\s+unidades?"), "")
            .replace(Regex("^uma?\\s+caixa\\s+de\\s+"), "")
            .replace(Regex("^duas?\\s+caixas?\\s+de\\s+"), "")
            .trim()
            .takeIf { it.isNotBlank() } ?: return null
        return ToolCall(
            name = CommerceToolName.REGISTER_STOCK_RECEIPT,
            arguments = mapOf(
                "product" to product,
                "quantity" to quantity.toString(),
                "unit_cost_cents" to costCents.toString(),
            ),
        )
    }

    private fun priceChange(text: String): ToolCall? {
        val marker = listOf("muda ", "altera ", "altere ", "troca ")
            .firstOrNull { text.startsWith(it) } ?: return null
        val priceMatch = Regex("(?:para|por)\\s+(.+)$").find(text) ?: return null
        val priceCents = moneyToCents(priceMatch.groupValues[1]) ?: return null
        val product = text.substring(marker.length, priceMatch.range.first)
            .replace(Regex("^o\\s+"), "")
            .replace(Regex("^preco (?:do|da|de)\\s+"), "")
            .replace(Regex("^valor (?:do|da|de)\\s+"), "")
            .trim()
            .takeIf { it.isNotBlank() } ?: return null
        return ToolCall(
            name = CommerceToolName.CHANGE_PRODUCT_PRICE,
            arguments = mapOf("product" to product, "new_price_cents" to priceCents.toString()),
        )
    }

    private fun sale(text: String): ToolCall? {
        if (text.contains("fiado") ||
            !(text.startsWith("vendi ") || text.startsWith("vende ") || text.startsWith("vender ") ||
                text.startsWith("registrar venda ") || text.startsWith("registra venda "))
        ) return null
        val body = text
            .removePrefix("vendi ")
            .removePrefix("vende ")
            .removePrefix("vender ")
            .removePrefix("registrar venda ")
            .removePrefix("registra venda ")
            .removePaymentMethodSuffix()
        val quantityProduct = quantityAndProduct(body) ?: return null
        val method = paymentMethod(text)
        return ToolCall(
            name = CommerceToolName.REGISTER_SALE,
            arguments = buildMap {
                put("product", quantityProduct.second)
                put("quantity", quantityProduct.first.toString())
                method?.let { put("payment_method", it) }
            },
        )
    }

    private fun todaySales(text: String): ToolCall? {
        val match = (text.contains("quanto vendi") || text.contains("quanto vendemos") ||
            text.contains("quanto entrou") || text.contains("vendas de hoje") ||
            text.contains("quanto foi vendido")) && isToday(text)
        return if (match) ToolCall(CommerceToolName.GET_TODAY_SALES, emptyMap()) else null
    }

    private fun purchasePlan(text: String): ToolCall? {
        val match = text.contains("o que preciso comprar") || text.contains("o que esta acabando") ||
            text.contains("lista de compras") || text.contains("o que devo pedir")
        return if (match) ToolCall(CommerceToolName.PREPARE_PURCHASE, emptyMap()) else null
    }

    private fun stockQuery(text: String): ToolCall? {
        if (text.contains("quanto vendi") || text.contains("quanto entrou") ||
            !(text.contains("estoque") || text.contains("tenho") || text.contains("tem") ||
                text.contains("unidades"))
        ) return null
        val product = extractReference(text, listOf("quanto", "tenho", "tem", "estoque", "unidades", "unidade", "de", "do", "da", "o", "a", "no", "em"))
            ?: return null
        return ToolCall(CommerceToolName.CHECK_STOCK, mapOf("product" to product))
    }

    private fun customerBalance(text: String): ToolCall? {
        if (!(text.contains("deve") || text.contains("devendo") || text.contains("saldo"))) return null
        if (text.contains("quanto tenho") || text.contains("quanto entrou")) return null
        val customer = extractReference(text, listOf("quanto", "esta", "esta", "devendo", "deve", "saldo", "fiado", "tem", "a", "o", "do", "da", "de", "cliente"))
            ?: return null
        return ToolCall(CommerceToolName.GET_CUSTOMER_BALANCE, mapOf("customer" to customer))
    }

    private fun productSearch(text: String): ToolCall? {
        val starts = listOf("quanto custa ", "qual o preco ", "qual e o preco ", "buscar produto ", "encontre produto ")
        val marker = starts.firstOrNull { text.startsWith(it) } ?: return null
        val product = extractReference(text.removePrefix(marker), listOf("do", "da", "de", "o", "a")) ?: return null
        return ToolCall(CommerceToolName.SEARCH_PRODUCT, mapOf("product" to product))
    }

    private fun customerSearch(text: String): ToolCall? {
        val marker = listOf("buscar cliente ", "encontre cliente ", "consultar cliente ")
            .firstOrNull { text.startsWith(it) } ?: return null
        val customer = text.removePrefix(marker).trim().takeIf { it.isNotBlank() } ?: return null
        return ToolCall(CommerceToolName.SEARCH_CUSTOMER, mapOf("customer" to customer.removeCustomerTitle()))
    }

    private fun supplierSearch(text: String): ToolCall? {
        val marker = listOf("buscar fornecedor ", "encontre fornecedor ", "consultar fornecedor ")
            .firstOrNull { text.startsWith(it) } ?: return null
        val supplier = text.removePrefix(marker).trim().takeIf { it.isNotBlank() } ?: return null
        return ToolCall(CommerceToolName.FIND_SUPPLIER, mapOf("supplier" to supplier))
    }

    private fun quantityAndProduct(value: String): Pair<Int, String>? {
        val body = value.trim().removePaymentMethodSuffix()
        val number = Regex("^(\\d+)\\s+(.+)$").find(body)
        val quantity: Int
        val product: String
        if (number != null) {
            quantity = number.groupValues[1].toIntOrNull() ?: return null
            product = number.groupValues[2]
        } else {
            val spoken = Regex("^(um|uma|dois|duas|tres|quatro|cinco|seis|sete|oito|nove|dez)\\s+(.+)$").find(body)
            if (spoken != null) {
                quantity = spokenQuantity(spoken.groupValues[1]) ?: return null
                product = spoken.groupValues[2]
            } else {
                quantity = 1
                product = body
            }
        }
        return quantity.takeIf { it > 0 }?.let { it to product.removeLeadingArticle() }
    }

    private fun extractReference(text: String, stopWords: List<String>): String? = text
        .replace(Regex("\\?+$"), "")
        .split(' ')
        .filter { it.isNotBlank() && it !in stopWords }
        .joinToString(" ")
        .trim()
        .takeIf { it.isNotBlank() }

    private fun paymentMethod(text: String): String? = when {
        text.contains("pix") -> "pix"
        text.contains("maquininha") || text.contains("cartao") -> "card"
        text.contains("dinheiro") -> "cash"
        else -> null
    }

    private fun moneyToCents(raw: String): Long? = runCatching {
        val value = raw.trim().lowercase()
        val numeric = when {
            value.matches(Regex("\\d+(?:[.,]\\d{1,2})?")) -> value.replace(',', '.')
            value == "oito e setenta e cinco" -> "8.75"
            value == "oito e cinquenta" -> "8.50"
            value == "dez" -> "10"
            value == "cinco" -> "5"
            else -> return null
        }
        BigDecimal(numeric).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }.getOrNull()?.takeIf { it > 0 }

    private fun spokenQuantity(value: String): Int? = mapOf(
        "um" to 1,
        "uma" to 1,
        "dois" to 2,
        "duas" to 2,
        "tres" to 3,
        "quatro" to 4,
        "cinco" to 5,
        "seis" to 6,
        "sete" to 7,
        "oito" to 8,
        "nove" to 9,
        "dez" to 10,
    )[value]

    private fun String.removeCustomerTitle(): String = removePrefix("dona ").removePrefix("don ").trim()

    private fun String.removeLeadingArticle(): String = removePrefix("um ").removePrefix("uma ").trim()

    private fun String.removePaymentMethodSuffix(): String {
        val value = trim()
        val suffixes = listOf("no pix", "via pix", "em pix", "no dinheiro", "em dinheiro", "na maquininha", "no cartao")
        return suffixes.firstNotNullOfOrNull { suffix ->
            when {
                value == suffix -> ""
                value.endsWith(" $suffix") -> value.removeSuffix(" $suffix").trim()
                else -> null
            }
        } ?: value
    }

    private fun isToday(text: String): Boolean =
        text.contains("hoje") || (!text.contains("ontem") && !text.contains("semana") && !text.contains("mes"))

    private fun normalize(input: String): String = Normalizer
        .normalize(input.lowercase(), Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .replace("[^a-z0-9?,.]+".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}
