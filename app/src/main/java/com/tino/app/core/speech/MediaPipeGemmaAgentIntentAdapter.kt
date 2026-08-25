package com.tino.app.core.speech

import com.tino.app.domain.agent.AgentIntentInterpreter
import com.tino.app.domain.agent.AgentIntentResult
import com.tino.app.domain.agent.AgentIntentSchema
import com.tino.app.domain.agent.RawAgentIntent
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.agent.TinoToolCatalog
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_DEBUG_OUTPUT_CHARS = 4000

/**
 * Gemma is an intent classifier only. It cannot query Room, calculate money,
 * render UI, or execute a commerce operation.
 */
@Singleton
class MediaPipeGemmaAgentIntentAdapter @Inject constructor(
    private val inference: GemmaTextInference,
) : AgentIntentInterpreter {
    override suspend fun interpret(input: String): AgentIntentResult {
        return interpret(input, emptySet())
    }

    override suspend fun interpret(
        input: String,
        availableCapabilities: Set<TinoCapabilityId>,
    ): AgentIntentResult {
        if (input.isBlank()) {
            return AgentIntentResult.Unsupported("Diga o que você quer saber.")
        }
        return when (val generated = inference.generate(GemmaAgentIntentPrompt.build(input, availableCapabilities))) {
            is GemmaTextInferenceResult.Generated -> {
                val parsed = GemmaAgentIntentJsonParser.parse(generated.text)
                if (parsed == null) {
                    inference.reportMalformedOutput()
                    AgentIntentResult.Unsupported(
                        reason = "MALFORMED_INTENT",
                        userMessage = "Não consegui entender exatamente o que você quer fazer.",
                    )
                } else {
                    when (val validation = AgentIntentSchema.validate(parsed)) {
                        is AgentIntentResult.Unsupported -> validation.copy(
                            debug = validation.debug?.copy(
                                rawOutput = generated.text.take(MAX_DEBUG_OUTPUT_CHARS),
                            ),
                        )
                        is AgentIntentResult.Supported -> validation
                    }
                }
            }

            is GemmaTextInferenceResult.Unavailable -> AgentIntentResult.Unsupported(generated.reason)
            is GemmaTextInferenceResult.Failed -> AgentIntentResult.Unsupported(generated.reason)
        }
    }
}

internal object GemmaAgentIntentPrompt {
    private val supportedCapabilities = setOf(
        TinoCapabilityId.READ_FINANCIAL_SUMMARY,
        TinoCapabilityId.LIST_PRODUCTS,
        TinoCapabilityId.REPLENISHMENT_QUERY,
        TinoCapabilityId.GET_PRODUCT_STOCK,
        TinoCapabilityId.GET_PRODUCT_PRICE,
        TinoCapabilityId.LIST_CUSTOMERS,
        TinoCapabilityId.LIST_RECEIVABLES,
        TinoCapabilityId.LIST_OVERDUE,
        TinoCapabilityId.ADD_CREDIT_ITEM,
        TinoCapabilityId.READ_CUSTOMER_BALANCE,
        TinoCapabilityId.RECEIVE_CREDIT_PAYMENT,
    )

    fun build(
        input: String,
        availableCapabilities: Set<TinoCapabilityId> = emptySet(),
    ): String {
        val allowedCapabilities = if (availableCapabilities.isEmpty()) {
            supportedCapabilities
        } else {
            supportedCapabilities.filterTo(mutableSetOf()) { it in availableCapabilities }
        }
        val allowedNames = allowedCapabilities.sortedBy { it.name }.joinToString(", ") { it.name }
        return """
        <start_of_turn>user
        Você classifica a intenção de um comerciante brasileiro no app TINO.
        Retorne SOMENTE um objeto JSON válido, sem markdown e sem explicação.
        Schema obrigatório: ${AgentIntentSchema.SCHEMA}, versão ${AgentIntentSchema.VERSION}.
        Para READ_FINANCIAL_SUMMARY, use schema, schema_version, capability, period e, quando houver,
        metric com RECEIVED ou RECEIVABLE e payment_method com ALL, CASH, PIX ou CARD.
        Para LIST_PRODUCTS, REPLENISHMENT_QUERY, LIST_CUSTOMERS, LIST_RECEIVABLES e LIST_OVERDUE, use somente schema,
        schema_version, capability e period.
        Para GET_PRODUCT_STOCK e GET_PRODUCT_PRICE, use somente schema, schema_version, capability,
        period e product_ref como referência textual.
        Para ADD_CREDIT_ITEM, use schema, schema_version, capability, period, customer_ref, product_ref e quantity.
        Para REGISTER_CREDIT_PAYMENT, use schema, schema_version, capability, period, customer_ref, amount_cents
        e, somente se a frase informar, payment_method com CASH, PIX ou CARD.
        Para GET_CUSTOMER_BALANCE, use schema, schema_version, capability, period e customer_ref.
        Para GET_CUSTOMER_TIMELINE, use schema, schema_version, capability, period e customer_ref.
        As únicas capabilities permitidas neste contexto são: $allowedNames.
        Nunca retorne uma capability fora dessa lista.
        Ela responde perguntas sobre quanto entrou hoje, incluindo variações naturais.
        O período permitido é TODAY.
        Não invente produtos, preços, estoque, clientes, saldos ou atrasos. Para perguntas sobre o banco,
        retorne apenas a capability e as referências; os dados virão de uma tool local.
        Para "Quais produtos temos cadastrados?" use LIST_PRODUCTS.
        Para "Quais produtos tenho que comprar?", "o que preciso repor?", "o que acabou?" ou "o que está acabando?"
        use REPLENISHMENT_QUERY. Retorne somente a intenção; não liste produtos nem invente o que comprar.
        Para "Quanto de Café Maratá tenho?" use GET_PRODUCT_STOCK com product_ref textual.
        Para "Quanto custa Café Maratá?" use GET_PRODUCT_PRICE com product_ref textual.
        Para "Quais clientes tenho?" use LIST_CUSTOMERS.
        Para "Quem está me devendo?" use LIST_RECEIVABLES.
        Para "Quem está atrasado?" ou "Quais fiados estão vencidos?" use LIST_OVERDUE.
        Também reconheça ADD_CREDIT_ITEM para frases como "adicionar um café maratá na conta da Dona Maria Lina".
        Também reconheça as variações "Maria Lina comprou fiado um café maratá" e "Maria Lina levou um café maratá fiado".
        Para ADD_CREDIT_ITEM, retorne EXATAMENTE estas chaves: schema, schema_version, capability, period,
        customer_ref, product_ref e quantity. Não inclua nenhuma outra chave. Use referências textuais,
        nunca IDs, preços, saldos, estoque, payment_method, amount ou amount_cents. Não execute nada.
        Esta capability cobre somente fiado por item. Se a frase falar apenas um valor em dinheiro,
        sem produto, não invente produto e retorne {}.
        Também reconheça GET_CUSTOMER_BALANCE para perguntas como "Quanto a Maria Lina está devendo?".
        Para GET_CUSTOMER_BALANCE, retorne somente customer_ref como referência textual.
        Nunca retorne customer_id, saldo, preço, estoque ou valores calculados. Não execute nada.
        Também reconheça GET_CUSTOMER_TIMELINE para frases como "Mostra a conta da Maria".
        Para GET_CUSTOMER_TIMELINE, retorne somente customer_ref como referência textual.
        Também reconheça REGISTER_CREDIT_PAYMENT para frases como "Maria pagou 20", "recebi 20 da conta da Maria"
        e "Dona Lina deu 20 do fiado". Retorne customer_ref textual, amount_cents inteiro em centavos e
        payment_method somente quando a frase disser dinheiro, PIX ou maquininha. Nunca invente a forma recebida.
        Se a forma não estiver na frase, omita payment_method; o TINO perguntará depois.
        Para READ_FINANCIAL_SUMMARY: Não retorne valores, totais, nomes de produtos, clientes,
        operações ou explicações. Não invente dados. Se não houver uma intenção permitida, retorne {}.
        Catálogo de tools locais disponíveis para a etapa seguinte (o TINO executará a tool e consultará o Room;
        você não deve inventar o resultado): ${toolContract(allowedCapabilities)}
        Pergunta do comerciante: ${JSONObject.quote(input.trim())}
        <end_of_turn>
        <start_of_turn>model
    """.trimIndent()
    }

    private fun toolContract(allowedCapabilities: Set<TinoCapabilityId>): String = TinoToolCatalog.all
        .filter { it.capabilityId != null }
        .filter { it.capabilityId in allowedCapabilities }
        .sortedBy { it.name }
        .joinToString("; ") { descriptor ->
            val capability = descriptor.capabilityId?.name.orEmpty()
            "${descriptor.name}[$capability] args=${descriptor.arguments.ifEmpty { setOf("none") }.joinToString(",")}" +
                " source=${descriptor.sourceOfTruth} a2ui=${descriptor.a2uiComponent ?: "semantic"}"
        }

}

internal object GemmaAgentIntentJsonParser {
    fun parse(response: String): RawAgentIntent? {
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val json = JSONObject(response.substring(start, end + 1))
            val keys = buildSet {
                json.keys().forEach(::add)
            }
            RawAgentIntent(
                schema = json.optString("schema").takeIf { it.isNotBlank() },
                schemaVersion = (json.opt("schema_version") as? Number)?.toInt(),
                capability = json.optString("capability").takeIf { it.isNotBlank() },
                period = json.optString("period").takeIf { it.isNotBlank() },
                customerRef = json.optString("customer_ref").takeIf { it.isNotBlank() },
                productRef = json.optString("product_ref").takeIf { it.isNotBlank() },
                paymentMethod = json.optString("payment_method").takeIf { it.isNotBlank() },
                metric = json.optString("metric").takeIf { it.isNotBlank() },
                quantity = json.opt("quantity").let { value ->
                    when (value) {
                        is Number -> value.toInt()
                        is String -> value.toIntOrNull()
                        else -> null
                    }
                },
                amountCents = json.opt("amount_cents").let { value ->
                    when (value) {
                        is Number -> value.toLong()
                        is String -> value.toLongOrNull()
                        else -> null
                    }
                },
                keys = keys,
            )
        }.getOrNull()
    }
}
