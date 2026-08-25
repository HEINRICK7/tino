package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.intelligence.IntelligenceResponse
import java.util.UUID
import javax.inject.Inject

/** Converts a grounded runtime response into a typed, inert A2UI component. */
class IntelligenceA2uiMapper @Inject constructor() {
    fun map(response: IntelligenceResponse): A2uiMessage = A2uiMessage(
        messageId = "intelligence-${UUID.randomUUID()}",
        component = A2uiComponent.InsightCard(
            title = titleFor(response.status.name),
            answer = response.answer,
            status = response.status.name,
            evidence = buildList {
                if (response.factsUsed.isNotEmpty()) {
                    add(A2uiDetailRow("Fatos", response.factsUsed.joinToString()))
                }
                if (response.analyticsUsed.isNotEmpty()) {
                    add(A2uiDetailRow("Cálculos", response.analyticsUsed.joinToString()))
                }
                if (response.knowledgeUsed.isNotEmpty()) {
                    add(A2uiDetailRow("Fontes", response.knowledgeUsed.joinToString()))
                }
            },
            limitations = response.limitations,
            dataSource = if (response.knowledgeUsed.isNotEmpty()) "APPROVED_KNOWLEDGE" else "LOCAL_FACTS",
        ),
    )

    private fun titleFor(status: String): String = when (status) {
        "ANSWERED" -> "Análise do TINO"
        "NEEDS_CLARIFICATION" -> "Preciso de mais um detalhe"
        "AMBIGUOUS_ENTITY" -> "Encontrei mais de uma opção"
        "INSUFFICIENT_DATA" -> "Ainda faltam dados"
        "KNOWLEDGE_UNAVAILABLE" -> "Conhecimento indisponível"
        else -> "Resposta do TINO"
    }
}
