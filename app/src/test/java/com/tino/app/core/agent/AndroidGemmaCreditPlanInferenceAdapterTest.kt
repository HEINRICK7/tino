package com.tino.app.core.agent

import com.tino.agent.contracts.CreditPlanInferenceResult
import com.tino.app.core.speech.GemmaTextInference
import com.tino.app.core.speech.GemmaTextInferenceResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidGemmaCreditPlanInferenceAdapterTest {
    @Test
    fun forwardsGeneratedModelOutputWithoutAddingFacts() = runBlocking {
        val adapter = AndroidGemmaCreditPlanInferenceAdapter(
            FakeInference(GemmaTextInferenceResult.Generated("{\"capability\":\"ADD_CREDIT_ITEM\"}")),
        )

        assertEquals(
            CreditPlanInferenceResult.Generated("{\"capability\":\"ADD_CREDIT_ITEM\"}"),
            adapter.generate("prompt"),
        )
    }

    @Test
    fun preservesUnavailableAndFailedStates() = runBlocking {
        assertEquals(
            CreditPlanInferenceResult.Unavailable("model missing"),
            AndroidGemmaCreditPlanInferenceAdapter(
                FakeInference(GemmaTextInferenceResult.Unavailable("model missing")),
            ).generate("prompt"),
        )
        assertEquals(
            CreditPlanInferenceResult.Failed("inference failed"),
            AndroidGemmaCreditPlanInferenceAdapter(
                FakeInference(GemmaTextInferenceResult.Failed("inference failed")),
            ).generate("prompt"),
        )
    }

    private class FakeInference(
        private val result: GemmaTextInferenceResult,
    ) : GemmaTextInference {
        override suspend fun generate(prompt: String): GemmaTextInferenceResult = result
    }
}
