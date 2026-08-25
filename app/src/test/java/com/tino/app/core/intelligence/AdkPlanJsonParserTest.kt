package com.tino.app.core.intelligence

import com.tino.app.domain.intelligence.IntelligenceGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdkPlanJsonParserTest {
    @Test
    fun parsesNewCompoundQuestionIntoExecutionPlan() {
        val plan = AdkPlanParser.parse(
            """
            Aqui está o plano:
            {
              "goal": "RECENT_PAYMENTS",
              "steps": [
                {"tool": "get_receivables", "purpose": "encontrar quem ainda deve"},
                {"tool": "get_customer_payment_history", "purpose": "comparar pagamentos recentes"},
                {"tool": "filter_recent_payments", "purpose": "selecionar o pagamento mais recente"},
                {"tool": "get_customer_balance", "purpose": "calcular quanto ainda falta"}
              ],
              "requires_clarification": false,
              "confidence": 0.91
            }
            """.trimIndent(),
        )

        requireNotNull(plan)
        assertEquals(IntelligenceGoal.RECENT_PAYMENTS, plan.goal)
        assertEquals(4, plan.steps.size)
        assertEquals("get_customer_balance", plan.steps.last().toolName)
        assertEquals(0.91f, plan.confidence)
    }

    @Test
    fun rejectsUnknownGoalInsteadOfCreatingExecutablePlan() {
        val plan = AdkPlanParser.parse(
            "{\"goal\":\"DELETE_EVERYTHING\",\"steps\":[]}",
        )

        assertNull(plan)
    }
}
