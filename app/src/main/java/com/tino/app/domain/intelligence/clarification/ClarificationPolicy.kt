package com.tino.app.domain.intelligence.clarification

import com.tino.app.domain.intelligence.IntelligenceResponse
import com.tino.app.domain.intelligence.IntelligenceResponseStatus

interface IntelligenceClarificationPolicy {
    fun missingProductReference(): IntelligenceResponse
}

class DeterministicClarificationPolicy : IntelligenceClarificationPolicy {
    override fun missingProductReference() = IntelligenceResponse(
        status = IntelligenceResponseStatus.NEEDS_CLARIFICATION,
        answer = "Qual produto você quer analisar?",
        plan = listOf("search_product"),
    )
}
