package com.tino.app.domain.intelligence.execution.handlers

import com.tino.app.domain.intelligence.execution.BasePlanHandler
import com.tino.app.domain.intelligence.execution.PlanHandlerContext
import com.tino.app.domain.intelligence.*
import com.tino.app.domain.intelligence.clarification.IntelligenceClarificationPolicy
import com.tino.app.domain.intelligence.planning.IntelligenceGoal
import com.tino.app.domain.intelligence.planning.IntelligencePlan
import java.time.Clock
import java.util.Locale

class KnowledgePlanHandler(
    context: PlanHandlerContext,
) : BasePlanHandler(context) {
    override fun supports(goal: IntelligenceGoal): Boolean = goal in setOf(
        IntelligenceGoal.KNOWLEDGE,
        IntelligenceGoal.UNSUPPORTED,
    )

    override suspend fun execute(
        request: IntelligenceRequest,
        plan: IntelligencePlan,
        normalized: String,
    ): IntelligenceResponse = when (plan.goal) {
        IntelligenceGoal.KNOWLEDGE -> answerKnowledge(request)
        IntelligenceGoal.UNSUPPORTED -> unsupported()
        else -> unsupported()
    }

    private suspend fun answerKnowledge(request: IntelligenceRequest): IntelligenceResponse {
        val result = knowledge.query(KnowledgeQuery(request.utterance.trim(), setOf("tino-help", "fiscal-glossary", "approved-docs"))) ?: return IntelligenceResponse(IntelligenceResponseStatus.KNOWLEDGE_UNAVAILABLE, "Ainda não tenho uma base de conhecimento disponível para explicar esse termo com fonte aprovada.", plan = listOf("search_knowledge"), limitations = listOf("RAG/knowledge adapter não está configurado neste build."))
        return IntelligenceResponse(IntelligenceResponseStatus.ANSWERED, result.answer, plan = listOf("search_knowledge", "grounded_answer"), toolCalls = listOf(call("search_knowledge", 1)), knowledgeUsed = result.sources, confidence = result.confidence)
    }

}
