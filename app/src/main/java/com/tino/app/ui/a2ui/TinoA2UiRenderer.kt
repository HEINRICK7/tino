package com.tino.app.ui.a2ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tino.app.interfaceadapter.a2ui.A2uiComponent
import com.tino.app.interfaceadapter.a2ui.A2uiMessage
import com.tino.app.interfaceadapter.a2ui.A2uiVisualStatus
import com.tino.app.interfaceadapter.a2ui.TinoA2UiComponentCatalog
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.components.TinoCardAction
import com.tino.app.ui.components.tinoAnimateContentSize
import com.tino.app.ui.components.TinoCardRenderer
import com.tino.app.ui.components.TinoCardSpec
import com.tino.app.ui.components.TinoCardStatus

/**
 * Compose is deliberately the outer adapter: it can render only catalogued,
 * typed components and fails closed for every other message.
 */
@Composable
fun TinoA2UiRenderer(
    message: A2uiMessage,
    modifier: Modifier = Modifier,
    onEntityChoiceSelected: (String) -> Unit = {},
    onActionConfirmed: () -> Unit = {},
    onActionCancelled: () -> Unit = {},
    onUndo: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onCardAction: (String) -> Unit = {},
) {
    TinoA2UiMetrics.recordRendered()
    val motionModifier = modifier.tinoAnimateContentSize()
    if (!message.hasSupportedEnvelope || !TinoA2UiComponentCatalog.isAllowed(message.component.type)) {
        TinoA2UiMetrics.recordFallback(unknownComponent = !TinoA2UiComponentCatalog.isAllowed(message.component.type))
        UnsupportedA2uiCard(motionModifier)
        return
    }

    when (val component = message.component) {
        is A2uiComponent.FinancialSummaryCard -> SystemFinancialSummaryCardView(component, motionModifier, onCardAction)
        is A2uiComponent.EntityChoice -> SystemEntityChoiceView(component, motionModifier, onEntityChoiceSelected)
        is A2uiComponent.ActionConfirmation -> SystemActionConfirmationView(
            component,
            motionModifier,
            onActionConfirmed,
            onActionCancelled,
            onUndo,
        )
        is A2uiComponent.CustomerBalanceCard -> SystemCustomerBalanceCardView(component, motionModifier, onCardAction)
        is A2uiComponent.CustomerTimelineCard -> SystemCustomerTimelineCardView(component, motionModifier, onCardAction)
        is A2uiComponent.ReadListCard -> SystemReadListCardView(component, motionModifier, onCardAction)
        is A2uiComponent.InsightCard -> SystemInsightCardView(component, motionModifier)
        is A2uiComponent.ErrorStatusCard -> SystemErrorStatusCardView(component, motionModifier, onRetry)
        is A2uiComponent.Unsupported -> UnsupportedA2uiCard(motionModifier)
    }
}

@Composable
private fun SystemFinancialSummaryCardView(
    component: A2uiComponent.FinancialSummaryCard,
    modifier: Modifier,
    onCardAction: (String) -> Unit,
) {
    TinoCardRenderer(
        TinoCardSpec.Metric(
            icon = TinoIcons.Trends,
            title = "Resumo do dia",
            value = component.primaryValueText,
            supportingText = "Total recebido",
            modifier = modifier,
            status = TinoCardStatus.SUCCESS,
            actionLabel = "Ver detalhes",
            onClick = { onCardAction("financial") },
        ),
    )
}

@Composable
private fun SystemActionConfirmationView(
    component: A2uiComponent.ActionConfirmation,
    modifier: Modifier,
    onConfirmed: () -> Unit,
    onCancelled: () -> Unit,
    onUndo: (String) -> Unit,
) {
    if (!component.complete) {
        TinoCardRenderer(
            TinoCardSpec.Preview(
                title = component.title,
                rows = component.detailRows.map { it.label to it.value }.ifEmpty { listOf("Detalhes" to component.detail) },
                total = component.primaryValueText ?: "—",
                modifier = modifier,
                onCancel = onCancelled,
                onConfirm = onConfirmed,
            ),
        )
    } else {
        TinoCardRenderer(
            TinoCardSpec.Status(
                icon = TinoIcons.Success,
                title = component.title,
                message = component.entityName ?: component.detail,
                modifier = modifier,
                status = TinoCardStatus.SUCCESS,
                footerActions = if (component.undoAvailable && component.activityId != null) {
                    listOf(TinoCardAction(component.undoLabel, { onUndo(component.activityId) }, TinoGreen))
                } else {
                    emptyList()
                },
            ),
        )
    }
}

@Composable
private fun SystemCustomerBalanceCardView(
    component: A2uiComponent.CustomerBalanceCard,
    modifier: Modifier,
    onCardAction: (String) -> Unit,
) {
    val settled = component.currentBalanceText.endsWith("R$ 0,00")
    TinoCardRenderer(
        TinoCardSpec.EntityList(
            title = component.title,
            items = listOf(
                com.tino.app.ui.components.TinoEntityCardSpec(
                    icon = TinoIcons.Credit,
                    title = "Conta de ${component.customerName}",
                    context = "Fiado",
                    primaryText = component.currentBalanceText,
                    statusText = component.overdueText.takeUnless { it.endsWith("R$ 0,00") },
                    metadata = listOfNotNull(
                        component.openText.takeIf { it.isNotBlank() }?.let { "Conta" to it },
                        component.oldestOpenText?.takeIf { it.isNotBlank() }?.let { "Mais antigo" to it },
                    ),
                    status = if (settled) TinoCardStatus.SUCCESS else TinoCardStatus.CREDIT,
                    footerLabel = "Ver conta",
                    onClick = { onCardAction("receivables") },
                ),
            ),
            modifier = modifier,
        ),
    )
}

@Composable
private fun SystemEntityChoiceView(
    component: A2uiComponent.EntityChoice,
    modifier: Modifier,
    onSelected: (String) -> Unit,
) {
    TinoCardRenderer(
        TinoCardSpec.Action(
            title = component.title,
            supportingText = component.prompt,
            actions = component.options.map { option ->
                com.tino.app.ui.components.TinoSystemAction(
                    icon = when (component.entityType) {
                        "customer" -> TinoIcons.People
                        "payment_method" -> paymentIcon(option.label)
                        else -> TinoIcons.Products
                    },
                    title = option.label,
                    subtitle = "Selecionar",
                    onClick = { onSelected(option.label) },
                )
            },
            modifier = modifier,
        ),
    )
}

@Composable
private fun SystemCustomerTimelineCardView(
    component: A2uiComponent.CustomerTimelineCard,
    modifier: Modifier,
    onCardAction: (String) -> Unit,
) {
    TinoCardRenderer(
        TinoCardSpec.Timeline(
            title = component.customerName,
            items = component.items.map { Triple(it.label, it.amountText, it.dateText) },
            modifier = modifier,
            onClick = { onCardAction("customers") },
        ),
    )
}

@Composable
private fun SystemReadListCardView(
    component: A2uiComponent.ReadListCard,
    modifier: Modifier,
    onCardAction: (String) -> Unit,
) {
    val emptyMessage = component.emptyMessage ?: "Nenhum item encontrado."
    when (component.type) {
        TinoA2UiComponentCatalog.PRODUCT_REPLENISHMENT -> {
            if (component.items.isEmpty()) {
                TinoCardRenderer(
                    TinoCardSpec.Status(
                        icon = TinoIcons.Success,
                        title = "Tudo certo por aqui!",
                        message = emptyMessage,
                        modifier = modifier,
                        status = TinoCardStatus.SUCCESS,
                    ),
                )
            } else {
                val count = component.items.size
                TinoCardRenderer(
                    TinoCardSpec.Metric(
                        icon = TinoIcons.Warning,
                        title = "Estoque baixo",
                        value = count.toString(),
                        supportingText = if (count == 1) "produto precisa de atenção" else "produtos precisam de atenção",
                        modifier = modifier,
                        status = TinoCardStatus.WARNING,
                        actionLabel = "Ver produtos",
                        onClick = { onCardAction("products") },
                    ),
                )
            }
        }
        TinoA2UiComponentCatalog.CUSTOMER_LIST,
        TinoA2UiComponentCatalog.CUSTOMER_CONTACT,
        -> TinoCardRenderer(
            TinoCardSpec.EntityList(
                title = component.title,
                items = component.items.map { item ->
                    com.tino.app.ui.components.TinoEntityCardSpec(
                        icon = TinoIcons.People,
                        title = item.title,
                        context = "Cadastro",
                        primaryText = "Cliente",
                        metadata = listOf(
                            "Telefone" to (item.secondaryText ?: "Não informado"),
                            "Situação" to "Cadastrado",
                        ),
                        status = TinoCardStatus.INFO,
                        footerLabel = "Ver detalhes do cliente",
                        onClick = { onCardAction("customer:${item.actionId.orEmpty()}") },
                    )
                },
                emptyMessage = emptyMessage,
                modifier = modifier,
            ),
        )
        TinoA2UiComponentCatalog.RECEIVABLES_LIST,
        TinoA2UiComponentCatalog.OVERDUE_LIST,
        -> TinoCardRenderer(
            TinoCardSpec.EntityList(
                title = component.title,
                items = component.items.map { item ->
                    com.tino.app.ui.components.TinoEntityCardSpec(
                        icon = TinoIcons.Credit,
                        title = "Conta de ${item.title}",
                        context = item.context ?: "Fiado",
                        primaryText = item.primaryText,
                        secondaryText = item.supportingText,
                        status = item.status.toCardStatus(),
                        footerLabel = "Ver conta",
                        onClick = { onCardAction("receivables:${item.actionId.orEmpty()}") },
                    )
                },
                emptyMessage = emptyMessage,
                modifier = modifier,
            ),
        )
        else -> TinoCardRenderer(
            TinoCardSpec.CatalogList(
                title = component.title,
                items = component.items.map { item ->
                    com.tino.app.ui.components.TinoCatalogItemSpec(
                        icon = systemCatalogIcon(item.iconKey),
                        title = item.title,
                        context = item.context,
                        primaryText = item.primaryText,
                        secondaryText = item.secondaryText,
                        statusText = item.supportingText,
                        status = item.status.toCardStatus(),
                        onClick = { onCardAction("products") },
                    )
                },
                emptyMessage = emptyMessage,
                footerLabel = when (component.type) {
                    TinoA2UiComponentCatalog.PRODUCT_LIST -> "Ver todos os produtos"
                    else -> null
                },
                onFooter = if (component.type == TinoA2UiComponentCatalog.PRODUCT_LIST) {
                    { onCardAction("products") }
                } else {
                    null
                },
                variant = if (component.type == TinoA2UiComponentCatalog.PRODUCT_LIST) "products" else "catalog",
                modifier = modifier,
            ),
        )
    }
}

@Composable
private fun SystemInsightCardView(
    component: A2uiComponent.InsightCard,
    modifier: Modifier,
) {
    val detail = buildString {
        append(component.answer)
        component.evidence.forEach { append(" ${it.label}: ${it.value}.") }
        component.limitations.forEach { append(" $it") }
    }
    TinoCardRenderer(
        TinoCardSpec.Status(
            icon = if (component.status == "ANSWERED") TinoIcons.Success else TinoIcons.Warning,
            title = component.title,
            message = detail,
            modifier = modifier,
            status = if (component.status == "ANSWERED") TinoCardStatus.SUCCESS else TinoCardStatus.WARNING,
        ),
    )
}

@Composable
private fun SystemErrorStatusCardView(
    component: A2uiComponent.ErrorStatusCard,
    modifier: Modifier,
    onRetry: () -> Unit,
) {
    TinoCardRenderer(
        TinoCardSpec.Status(
            icon = TinoIcons.Error,
            title = component.title,
            message = component.message,
            modifier = modifier,
            status = TinoCardStatus.ERROR,
            actionLabel = component.retryLabel,
            onAction = onRetry,
        ),
    )
}

private fun A2uiVisualStatus.toCardStatus() = when (this) {
    A2uiVisualStatus.SUCCESS -> TinoCardStatus.SUCCESS
    A2uiVisualStatus.WARNING -> TinoCardStatus.WARNING
    A2uiVisualStatus.ERROR -> TinoCardStatus.ERROR
    A2uiVisualStatus.INFO -> TinoCardStatus.INFO
    A2uiVisualStatus.CREDIT -> TinoCardStatus.CREDIT
    A2uiVisualStatus.NORMAL -> TinoCardStatus.NEUTRAL
}

private fun systemCatalogIcon(key: String?) = when (key) {
    "customer", "person" -> TinoIcons.People
    "debt", "credit" -> TinoIcons.Credit
    "sale", "cart" -> TinoIcons.Cart
    "supplier", "truck" -> TinoIcons.Supplier
    "document", "note" -> TinoIcons.Document
    else -> TinoIcons.Products
}

private fun paymentIcon(label: String) = when {
    label.contains("pix", ignoreCase = true) -> TinoIcons.Pix
    label.contains("dinheiro", ignoreCase = true) -> TinoIcons.Cash
    else -> TinoIcons.Card
}

@Composable
private fun UnsupportedA2uiCard(modifier: Modifier) {
    TinoCardRenderer(
        TinoCardSpec.Status(
            icon = TinoIcons.Error,
            title = "Ainda não consigo responder",
            message = "Não foi possível mostrar esta resposta.",
            modifier = modifier,
            status = TinoCardStatus.ERROR,
        ),
    )
}
