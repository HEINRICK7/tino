package com.tino.app.domain.intelligence.planning

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerEvaluationTest {
    @Test
    fun comparesDeterministicAndAdkContractWithoutExecutingTools() = runBlocking {
        val deterministic = DeterministicIntelligenceQueryPlanner()
        val adk = object : PlannerPort {
            override val id: String = "adk"

            override suspend fun plan(request: com.tino.app.domain.intelligence.IntelligenceRequest): IntelligencePlan =
                deterministic.plan(request).copy(plannerId = id)
        }

        val report = PlannerAbEvaluator(DeterministicIntelligencePlanValidator()).evaluate(
            planners = mapOf("deterministic" to deterministic, "adk" to adk),
        )

        assertEquals("gate-3.2", report.corpusId)
        assertEquals(4, report.byPlanner["deterministic"]?.totalCases)
        assertEquals(4, report.byPlanner["deterministic"]?.planCorrectCount)
        assertEquals(4, report.byPlanner["adk"]?.toolOrderingCorrectCount)
        assertEquals(0, report.byPlanner["adk"]?.validationRejectionCount)
        assertTrue((report.byPlanner["adk"]?.groundingReadyRate ?: 0.0) == 1.0)
    }
}
