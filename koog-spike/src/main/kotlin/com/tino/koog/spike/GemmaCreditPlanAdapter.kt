package com.tino.koog.spike

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.tino.agent.contracts.CreditPlanInferencePort
import com.tino.agent.contracts.CreditPlanInferenceResult

@Serializable
private data class CreditPreparationPlanPayload(
    val schema: String,
    @SerialName("schema_version") val schemaVersion: Int,
    val capability: String,
    @SerialName("customer_ref") val customerReference: String,
    @SerialName("product_ref") val productReference: String,
    val quantity: Int,
)

sealed interface CreditPlanResult {
    data class Ready(val request: CreditPreparationRequest) : CreditPlanResult
    data class Rejected(val reason: String) : CreditPlanResult
}

/**
 * Boundary for a future local Gemma runtime. The model emits only references;
 * all operational facts remain behind CreditPreparationFactsPort.
 */
class GemmaCreditPlanAdapter(
    private val inference: CreditPlanInferencePort,
) {
    suspend fun interpret(input: String): CreditPlanResult {
        return when (val generated = inference.generate(promptFor(input))) {
            is CreditPlanInferenceResult.Generated -> parse(generated.text)
            is CreditPlanInferenceResult.Unavailable -> CreditPlanResult.Rejected(generated.reason)
            is CreditPlanInferenceResult.Failed -> CreditPlanResult.Rejected(generated.reason)
        }
    }

    private fun parse(raw: String): CreditPlanResult = try {
        val payload = strictJson.decodeFromString<CreditPreparationPlanPayload>(raw)
        validate(payload)
    } catch (_: Exception) {
        CreditPlanResult.Rejected("O modelo não produziu um plano de fiado válido.")
    }

    private fun validate(payload: CreditPreparationPlanPayload): CreditPlanResult {
        if (payload.schema != SCHEMA || payload.schemaVersion != VERSION) {
            return CreditPlanResult.Rejected("schema de plano não suportado")
        }
        if (payload.capability != "ADD_CREDIT_ITEM") {
            return CreditPlanResult.Rejected("capability não permitida neste slice")
        }
        if (payload.customerReference.isBlank() || payload.productReference.isBlank()) {
            return CreditPlanResult.Rejected("referências de cliente e produto são obrigatórias")
        }
        if (payload.quantity !in 1..1000) {
            return CreditPlanResult.Rejected("quantidade fora do limite permitido")
        }
        return CreditPlanResult.Ready(
            CreditPreparationRequest(
                customerReference = payload.customerReference.trim(),
                productReference = payload.productReference.trim(),
                quantity = payload.quantity,
            ),
        )
    }

    private fun promptFor(input: String): String = """
        Você é o interpretador de comandos do TINO.
        Extraia somente um plano JSON para ADD_CREDIT_ITEM.
        Retorne exatamente os campos schema, schema_version, capability,
        customer_ref, product_ref e quantity.
        customer_ref e product_ref devem ser referências textuais da frase.
        Nunca invente customer_id, product_id, price_cents, balance_cents ou stock.
        Frase do comerciante: $input
    """.trimIndent()

    private companion object {
        const val SCHEMA = "tino.credit-preparation-plan"
        const val VERSION = 1
        val strictJson = Json {
            ignoreUnknownKeys = false
            isLenient = false
        }
    }
}

sealed interface CreditPreparationFlowResult {
    data class Preview(val value: CreditPreparationAgentResult.Preview) : CreditPreparationFlowResult
    data class Blocked(val value: CreditPreparationAgentResult.Blocked) : CreditPreparationFlowResult
    data class Rejected(val reason: String) : CreditPreparationFlowResult
}

/** Text/model → validated plan → allowlisted tools → preview. */
class GemmaCreditPreparationFlow(
    private val planner: GemmaCreditPlanAdapter,
    private val agent: CreditPreparationAgent,
) {
    suspend fun prepare(input: String): CreditPreparationFlowResult = when (val plan = planner.interpret(input)) {
        is CreditPlanResult.Rejected -> CreditPreparationFlowResult.Rejected(plan.reason)
        is CreditPlanResult.Ready -> when (val result = agent.prepare(plan.request)) {
            is CreditPreparationAgentResult.Preview -> CreditPreparationFlowResult.Preview(result)
            is CreditPreparationAgentResult.Blocked -> CreditPreparationFlowResult.Blocked(result)
        }
    }
}
