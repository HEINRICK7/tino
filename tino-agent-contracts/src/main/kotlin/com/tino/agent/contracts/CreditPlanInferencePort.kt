package com.tino.agent.contracts

fun interface CreditPlanInferencePort {
    suspend fun generate(prompt: String): CreditPlanInferenceResult
}

sealed interface CreditPlanInferenceResult {
    data class Generated(val text: String) : CreditPlanInferenceResult
    data class Unavailable(val reason: String) : CreditPlanInferenceResult
    data class Failed(val reason: String) : CreditPlanInferenceResult
}
