package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.voice.ToolExecutionResult
import com.tino.app.domain.voice.ToolPreview
import com.tino.app.domain.voice.ToolPreviewPresentation
import com.tino.app.domain.voice.ToolResultPresentation
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommerceActionA2uiMapper @Inject constructor() {
    fun preview(preview: ToolPreview): A2uiMessage {
        val presentation = preview.presentation
        return message(
            title = when (presentation) {
                is ToolPreviewPresentation.Payment -> "Pagamento"
                is ToolPreviewPresentation.Credit -> "Fiado"
                is ToolPreviewPresentation.StockEntry -> "Entrada"
                is ToolPreviewPresentation.PriceChange -> "Alterar preço"
                null -> preview.title.removeSuffix("?")
            },
            detail = preview.detail,
            confirmLabel = "Confirmar",
            complete = false,
            entityName = when (presentation) {
                is ToolPreviewPresentation.Payment -> presentation.customerName
                is ToolPreviewPresentation.Credit -> presentation.customerName
                is ToolPreviewPresentation.StockEntry -> presentation.productName
                is ToolPreviewPresentation.PriceChange -> presentation.productName
                null -> null
            },
            primaryValueText = when (presentation) {
                is ToolPreviewPresentation.Payment -> presentation.amountText
                is ToolPreviewPresentation.Credit -> presentation.totalText
                else -> null
            },
            detailRows = when (presentation) {
                is ToolPreviewPresentation.Payment -> listOf(
                    A2uiDetailRow("Método", presentation.methodLabel),
                    A2uiDetailRow("Saldo", "${presentation.currentBalanceText} → ${presentation.projectedBalanceText}"),
                )
                is ToolPreviewPresentation.Credit -> presentation.lines.map { line ->
                    A2uiDetailRow(line.productName.orEmpty(), line.quantityText)
                } + A2uiDetailRow("Novo saldo", presentation.projectedBalanceText)
                is ToolPreviewPresentation.StockEntry -> buildList {
                    add(A2uiDetailRow("Quantidade", presentation.quantityText))
                    add(A2uiDetailRow("Custo unitário", presentation.unitCostText))
                    presentation.supplierName?.let { add(A2uiDetailRow("Fornecedor", it)) }
                }
                is ToolPreviewPresentation.PriceChange -> listOf(
                    A2uiDetailRow("Atual", presentation.oldPriceText),
                    A2uiDetailRow("Novo", presentation.newPriceText),
                )
                null -> emptyList()
            },
            iconKey = when (presentation) {
                is ToolPreviewPresentation.Payment -> "payment"
                is ToolPreviewPresentation.Credit -> "credit"
                is ToolPreviewPresentation.StockEntry -> "stock_entry"
                is ToolPreviewPresentation.PriceChange -> "price"
                null -> null
            },
        )
    }

    fun completed(
        result: ToolExecutionResult,
        activityId: String? = null,
        undoAvailableOverride: Boolean? = null,
    ): A2uiMessage {
        val presentation = result.presentation
        return message(
            title = result.title,
            detail = result.message,
            confirmLabel = "Concluído",
            complete = true,
            semanticType = TinoA2UiComponentCatalog.OPERATION_SUCCESS,
            operationId = result.operationId,
            activityId = activityId,
            undoAvailable = undoAvailableOverride ?: (result.undo != null && result.operationId != null),
            entityName = (presentation as? ToolResultPresentation.Payment)?.customerName,
            primaryValueText = (presentation as? ToolResultPresentation.Payment)?.amountText,
            detailRows = (presentation as? ToolResultPresentation.Payment)?.let {
                listOf(A2uiDetailRow("Método", it.methodLabel))
            }.orEmpty(),
            iconKey = if (presentation != null) "payment" else null,
        )
    }

    private fun message(
        title: String,
        detail: String,
        confirmLabel: String,
        complete: Boolean,
        semanticType: String = TinoA2UiComponentCatalog.ACTION_CONFIRMATION,
        operationId: String? = null,
        activityId: String? = null,
        undoAvailable: Boolean = false,
        entityName: String? = null,
        primaryValueText: String? = null,
        detailRows: List<A2uiDetailRow> = emptyList(),
        iconKey: String? = null,
    ) = A2uiMessage(
        messageId = UUID.randomUUID().toString(),
        component = A2uiComponent.ActionConfirmation(
            title = title,
            detail = detail,
            confirmLabel = confirmLabel,
            complete = complete,
            semanticType = semanticType,
            operationId = operationId,
            activityId = activityId,
            undoAvailable = undoAvailable,
            entityName = entityName,
            primaryValueText = primaryValueText,
            detailRows = detailRows,
            iconKey = iconKey,
        ),
    )
}
