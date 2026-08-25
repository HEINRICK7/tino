package com.tino.app.core.agent

import com.tino.agent.contracts.CreditPlanInferencePort
import com.tino.agent.contracts.CreditPlanInferenceResult
import com.tino.app.core.speech.GemmaTextInference
import com.tino.app.core.speech.GemmaTextInferenceResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android boundary for the model-plan contract. MediaPipe is an outer detail;
 * the plan parser and Koog sandbox only see CreditPlanInferencePort.
 */
@Singleton
class AndroidGemmaCreditPlanInferenceAdapter @Inject constructor(
    private val inference: GemmaTextInference,
) : CreditPlanInferencePort {
    override suspend fun generate(prompt: String): CreditPlanInferenceResult =
        when (val result = inference.generate(prompt)) {
            is GemmaTextInferenceResult.Generated -> CreditPlanInferenceResult.Generated(result.text)
            is GemmaTextInferenceResult.Unavailable -> CreditPlanInferenceResult.Unavailable(result.reason)
            is GemmaTextInferenceResult.Failed -> CreditPlanInferenceResult.Failed(result.reason)
        }
}
