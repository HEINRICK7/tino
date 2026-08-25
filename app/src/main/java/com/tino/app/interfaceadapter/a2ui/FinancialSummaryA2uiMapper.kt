package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.agent.AgentResponse
import com.tino.app.domain.agent.AgentSurface
import com.tino.app.domain.agent.FinancialSummaryResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinancialSummaryA2uiMapper @Inject constructor() {
    fun map(result: FinancialSummaryResult, messageId: String = "financial-summary-today"): A2uiMessage =
        A2uiMessage(
            messageId = messageId,
            component = A2uiComponent.FinancialSummaryCard(
                title = "Entrou hoje",
                primaryLabel = "Recebido hoje",
                primaryValueText = formatCents(result.receivedTotalCents),
                metrics = listOf(
                    A2uiMetric("cash", "Dinheiro", formatCents(result.receivedCashCents)),
                    A2uiMetric("pix", "PIX", formatCents(result.receivedPixCents)),
                    A2uiMetric("card", "Maquininha", formatCents(result.receivedCardCents)),
                    A2uiMetric("unknown", "Não identificado", formatCents(result.receivedUnknownCents)),
                ),
                emptyMessage = if (result.receivedTotalCents == 0L) {
                    "Hoje ainda não entrou nada."
                } else {
                    null
                },
                dataSource = "LOCAL_ONLY",
            ),
        )

    fun map(response: AgentResponse.SurfaceReady): A2uiMessage =
        (response.surface as? AgentSurface.FinancialSummaryCard)?.let(::map) ?: map(response.result)

    fun map(surface: AgentSurface.FinancialSummaryCard): A2uiMessage =
        A2uiMessage(
            messageId = "financial-summary-today",
            component = A2uiComponent.FinancialSummaryCard(
                title = surface.title,
                primaryLabel = surface.primaryLabel,
                primaryValueText = surface.primaryValueText,
                metrics = surface.metrics.map { metric ->
                    A2uiMetric(metric.key, metric.label, metric.valueText)
                },
                emptyMessage = surface.emptyMessage,
                dataSource = surface.dataSource.name,
            ),
        )

    private fun formatCents(cents: Long): String =
        "R$ %.2f".format(java.util.Locale("pt", "BR"), cents / 100.0)
}
