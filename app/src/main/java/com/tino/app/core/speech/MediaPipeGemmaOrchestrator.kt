package com.tino.app.core.speech

import com.tino.app.domain.voice.CommerceToolName
import com.tino.app.domain.voice.GemmaOrchestrator
import com.tino.app.domain.voice.GlobalCommandRouter
import com.tino.app.domain.voice.ToolCall
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

/** Global voice adapter. Gemma proposes a tool call; domain code still previews and confirms it. */
@Singleton
class MediaPipeGemmaOrchestrator @Inject constructor(
    private val inference: GemmaTextInference,
    private val globalCommandRouter: GlobalCommandRouter,
) : GemmaOrchestrator {
    constructor(inference: GemmaTextInference) : this(inference, GlobalCommandRouter())

    override suspend fun interpret(committedTranscript: String): ToolCall? {
        val prompt = """
            <start_of_turn>user
            Você entende comandos de um comerciante brasileiro no app TINO.
            Retorne SOMENTE um objeto JSON com "name" e "arguments".
            "name" deve ser uma destas opções: ${CommerceToolName.entries.joinToString()}.
            "arguments" deve ser um objeto com texto simples.
            Entenda frases naturais, abreviações e variações como "anota", "vende", "vendi",
            "quanto o João deve", "tem café", "chegou mercadoria" e "recebi pagamento".
            Use estes argumentos quando fizer sentido. Para dinheiro, sempre retorne centavos
            inteiros em "unit_cost_cents" e "amount_cents":
            SEARCH_PRODUCT(product), SEARCH_CUSTOMER(customer), REGISTER_SALE(product, quantity),
            REGISTER_CREDIT_SALE(customer, product, quantity), ADD_CREDIT_ITEM(customer, product, quantity),
            REGISTER_STOCK_RECEIPT(product, quantity, unit_cost_cents, supplier),
            CHECK_STOCK(product), GET_CUSTOMER_BALANCE(customer), REGISTER_CREDIT_PAYMENT(customer, amount_cents, payment_method),
            GET_TODAY_SALES(), PREPARE_PURCHASE(), FIND_SUPPLIER(supplier).
            Para alterar preço, use CHANGE_PRODUCT_PRICE(product, new_price_cents).
            Nunca use preço em ponto flutuante nesse argumento; converta R$8,75 para 875.
            Se não houver um comando claro, retorne {}.
            Nunca execute a ação; apenas organize a intenção para revisão humana.
            Para REGISTER_CREDIT_PAYMENT, payment_method só deve aparecer quando a fala informar dinheiro, PIX ou maquininha.
            Se estiver ausente, não invente uma forma de recebimento; deixe o campo ausente para o TINO perguntar.
            Fala: ${JSONObject.quote(committedTranscript)}
            <end_of_turn>
            <start_of_turn>model
        """.trimIndent()

        val generated = inference.generate(prompt)
        if (generated !is GemmaTextInferenceResult.Generated) {
            return globalCommandRouter.route(committedTranscript)
        }

        return runCatching {
            val start = generated.text.indexOf('{')
            val end = generated.text.lastIndexOf('}')
            if (start < 0 || end <= start) {
                inference.reportMalformedOutput()
                return null
            }
            val json = JSONObject(generated.text.substring(start, end + 1))
            val name = CommerceToolName.entries.firstOrNull {
                it.name.equals(json.optString("name").trim(), ignoreCase = true)
            } ?: return null
            val argumentsJson = json.optJSONObject("arguments") ?: JSONObject()
            val arguments = linkedMapOf<String, String>()
            argumentsJson.keys().forEach { key ->
                val value = argumentsJson.opt(key)
                if (value != null && value != JSONObject.NULL) {
                    arguments[key] = value.toString()
                }
            }
            if (name == CommerceToolName.ADD_CREDIT_ITEM && arguments.keys.any { it in MODEL_FORBIDDEN_ENTITY_FIELDS }) {
                return null
            }
            ToolCall(name, normalizeArguments(name, arguments))
        }.getOrNull()
    }

    private fun normalizeArguments(
        name: CommerceToolName,
        raw: Map<String, String>,
    ): Map<String, String> {
        val normalized = raw.toMutableMap()
        if (normalized["customer"].isNullOrBlank()) {
            normalized["customer_ref"]?.let { normalized["customer"] = it }
        }
        if (normalized["product"].isNullOrBlank()) {
            normalized["product_ref"]?.let { normalized["product"] = it }
        }
        normalized.remove("customer_ref")
        normalized.remove("product_ref")
        if (name == CommerceToolName.ADD_CREDIT_ITEM && normalized["quantity"].isNullOrBlank()) {
            normalized["quantity"] = "1"
        }
        if (name == CommerceToolName.REGISTER_STOCK_RECEIPT &&
            normalized["unit_cost_cents"].isNullOrBlank()
        ) {
            normalized["unit_cost"]?.let { normalized["unit_cost_cents"] = moneyToCents(it) }
        }
        if (name == CommerceToolName.REGISTER_CREDIT_PAYMENT &&
            normalized["amount_cents"].isNullOrBlank()
        ) {
            normalized["amount"]?.let { normalized["amount_cents"] = moneyToCents(it) }
        }
        if (name == CommerceToolName.REGISTER_CREDIT_PAYMENT && normalized["payment_method"].isNullOrBlank()) {
            normalized["payment_method_ref"]?.let { normalized["payment_method"] = it }
        }
        if (name == CommerceToolName.CHANGE_PRODUCT_PRICE &&
            normalized["new_price_cents"].isNullOrBlank()
        ) {
            normalized["new_price"]?.let { normalized["new_price_cents"] = moneyToCents(it) }
        }
        listOf("quantity", "boxes", "units_per_box").forEach { key ->
            normalized[key]?.let { normalized[key] = integerToDigits(it) }
        }
        return normalized
    }

    companion object {
        private val MODEL_FORBIDDEN_ENTITY_FIELDS = setOf(
            "customer_id",
            "product_id",
            "price_cents",
            "unit_price_cents",
            "balance_cents",
            "stock",
            "stock_quantity",
        )
    }

    private fun integerToDigits(raw: String): String {
        Regex("\\d+").find(raw)?.value?.let { return it }
        return when (raw.trim().lowercase()) {
            "um", "uma" -> "1"
            "dois", "duas" -> "2"
            "tres", "três" -> "3"
            "quatro" -> "4"
            "cinco" -> "5"
            "seis" -> "6"
            "sete" -> "7"
            "oito" -> "8"
            "nove" -> "9"
            "dez" -> "10"
            else -> raw.trim()
        }
    }

    private fun moneyToCents(raw: String): String {
        val normalized = raw
            .replace("R$", "", ignoreCase = true)
            .replace(" ", "")
            .let {
                when {
                    it.contains(',') && it.contains('.') -> it.replace(".", "").replace(',', '.')
                    it.contains(',') -> it.replace(',', '.')
                    else -> it
                }
            }
        return BigDecimal(normalized)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toPlainString()
    }
}
