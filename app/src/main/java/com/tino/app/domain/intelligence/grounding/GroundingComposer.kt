package com.tino.app.domain.intelligence.grounding

import com.tino.app.domain.intelligence.IntelligenceResponse
import com.tino.app.domain.intelligence.planning.IntelligencePlan

interface IntelligenceGroundingComposer {
    fun compose(plan: IntelligencePlan, response: IntelligenceResponse): IntelligenceResponse
}

class DeterministicGroundingComposer : IntelligenceGroundingComposer {
    override fun compose(plan: IntelligencePlan, response: IntelligenceResponse): IntelligenceResponse {
        val grounded = if (plan.steps.isEmpty() || response.plan.isNotEmpty()) {
            response
        } else {
            response.copy(plan = plan.steps.map { it.toolName })
        }
        return grounded.copy(plannerUsed = grounded.plannerUsed ?: plan.plannerId)
    }
}
