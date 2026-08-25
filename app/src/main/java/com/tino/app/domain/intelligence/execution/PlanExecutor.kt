package com.tino.app.domain.intelligence.execution

import com.tino.app.domain.intelligence.BusinessAnalyticsPort
import com.tino.app.domain.intelligence.IntelligenceFactsPort
import com.tino.app.domain.intelligence.IntelligenceRequest
import com.tino.app.domain.intelligence.IntelligenceResponse
import com.tino.app.domain.intelligence.IntelligenceTextNormalizer
import com.tino.app.domain.intelligence.KnowledgeQueryPort
import com.tino.app.domain.intelligence.clarification.DeterministicClarificationPolicy
import com.tino.app.domain.intelligence.clarification.IntelligenceClarificationPolicy
import com.tino.app.domain.intelligence.grounding.DeterministicGroundingComposer
import com.tino.app.domain.intelligence.grounding.IntelligenceGroundingComposer
import com.tino.app.domain.intelligence.planning.IntelligencePlan
import com.tino.app.domain.intelligence.execution.handlers.CustomerPlanHandler
import com.tino.app.domain.intelligence.execution.handlers.FinancialPlanHandler
import com.tino.app.domain.intelligence.execution.handlers.InventoryPlanHandler
import com.tino.app.domain.intelligence.execution.handlers.KnowledgePlanHandler
import java.time.Clock

interface IntelligencePlanExecutor {
    suspend fun execute(request: IntelligenceRequest, plan: IntelligencePlan): IntelligenceResponse
}

class DeterministicIntelligencePlanExecutor(
    facts: IntelligenceFactsPort,
    analytics: BusinessAnalyticsPort,
    knowledge: KnowledgeQueryPort,
    clock: Clock,
    private val clarificationPolicy: IntelligenceClarificationPolicy = DeterministicClarificationPolicy(),
    private val grounding: IntelligenceGroundingComposer = DeterministicGroundingComposer(),
) : IntelligencePlanExecutor {
    private val context = PlanHandlerContext(
        facts = facts,
        analytics = analytics,
        knowledge = knowledge,
        clock = clock,
        clarificationPolicy = clarificationPolicy,
    )
    private val handlerRegistry = IntelligenceHandlerRegistry(
        listOf(
            FinancialPlanHandler(context),
            CustomerPlanHandler(context),
            InventoryPlanHandler(context),
            KnowledgePlanHandler(context),
        ),
    )

    override suspend fun execute(request: IntelligenceRequest, plan: IntelligencePlan): IntelligenceResponse {
        val normalized = IntelligenceTextNormalizer.normalize(request.utterance)
        val response = if (plan.requiresClarification) {
            clarificationPolicy.missingProductReference()
        } else {
            handlerRegistry.execute(request, plan, normalized)
        }
        return grounding.compose(plan, response)
    }
}
