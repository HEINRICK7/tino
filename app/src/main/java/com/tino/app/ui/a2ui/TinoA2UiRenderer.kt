package com.tino.app.ui.a2ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tino.app.interfaceadapter.a2ui.A2uiComponent
import com.tino.app.interfaceadapter.a2ui.A2uiListItem
import com.tino.app.interfaceadapter.a2ui.A2uiMessage
import com.tino.app.interfaceadapter.a2ui.A2uiVisualStatus
import com.tino.app.interfaceadapter.a2ui.TinoA2UiComponentCatalog
import com.tino.app.interfaceadapter.a2ui.TinoA2UiProtocol
import com.tino.app.ui.theme.TinoBorder
import com.tino.app.ui.theme.TinoAmber
import com.tino.app.ui.theme.TinoAmberContainer
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoGreenTint
import com.tino.app.ui.theme.TinoRedContainer
import com.tino.app.ui.theme.TinoRed
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoShapes
import com.tino.app.ui.theme.TinoSpacing
import com.tino.app.ui.theme.TinoSurface
import com.tino.app.ui.theme.TinoSize
import com.tino.app.ui.theme.TinoElevation
import com.tino.app.ui.icons.TinoIcons

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
) {
    TinoA2UiMetrics.recordRendered()
    if (!message.hasSupportedEnvelope || !TinoA2UiComponentCatalog.isAllowed(message.component.type)) {
        TinoA2UiMetrics.recordFallback(unknownComponent = !TinoA2UiComponentCatalog.isAllowed(message.component.type))
        UnsupportedA2uiCard(modifier)
        return
    }

    when (val component = message.component) {
        is A2uiComponent.FinancialSummaryCard -> FinancialSummaryCardView(component, modifier)
        is A2uiComponent.EntityChoice -> EntityChoiceView(component, modifier, onEntityChoiceSelected)
        is A2uiComponent.ActionConfirmation -> ActionConfirmationView(
            component,
            modifier,
            onActionConfirmed,
            onActionCancelled,
            onUndo,
        )
        is A2uiComponent.CustomerBalanceCard -> CustomerBalanceCardView(component, modifier)
        is A2uiComponent.CustomerTimelineCard -> CustomerTimelineCardView(component, modifier)
        is A2uiComponent.ReadListCard -> ReadListCardView(component, modifier)
        is A2uiComponent.InsightCard -> InsightCardView(component, modifier)
        is A2uiComponent.ErrorStatusCard -> ErrorStatusCardView(component, modifier, onRetry)
        is A2uiComponent.Unsupported -> UnsupportedA2uiCard(modifier)
    }
}

@Composable
private fun ErrorStatusCardView(
    component: A2uiComponent.ErrorStatusCard,
    modifier: Modifier,
    onRetry: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = TinoRedContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoElevation.none),
    ) {
        Column(
            modifier = Modifier.padding(TinoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
            ) {
                Icon(TinoIcons.Error, contentDescription = "Erro", tint = TinoRed)
                Text(component.title, style = MaterialTheme.typography.titleMedium, color = TinoInk)
            }
            Text(component.message, style = MaterialTheme.typography.bodyMedium, color = TinoInk)
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(component.retryLabel, maxLines = 1)
            }
        }
    }
}

@Composable
private fun InsightCardView(
    component: A2uiComponent.InsightCard,
    modifier: Modifier,
) {
    val isWarning = component.status != "ANSWERED"
    Card(
        modifier = modifier.fillMaxWidth().border(TinoSize.cardBorder, TinoBorder, TinoShapes.medium),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (isWarning) TinoGreenTint else TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoElevation.subtle),
    ) {
        Column(
            modifier = Modifier.padding(TinoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            Text(component.title, style = MaterialTheme.typography.titleMedium, color = TinoInk)
            Text(component.answer, style = MaterialTheme.typography.bodyLarge, color = TinoInk)
            component.evidence.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(row.label, style = MaterialTheme.typography.bodySmall, color = TinoMuted)
                    Text(row.value, style = MaterialTheme.typography.bodySmall, color = TinoInk)
                }
            }
            component.limitations.forEach { limitation ->
                Text(limitation, style = MaterialTheme.typography.bodySmall, color = TinoMuted)
            }
        }
    }
}

@Composable
private fun ActionConfirmationView(
    component: A2uiComponent.ActionConfirmation,
    modifier: Modifier,
    onConfirmed: () -> Unit,
    onCancelled: () -> Unit,
    onUndo: (String) -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoElevation.subtle),
    ) {
        Column(
            modifier = Modifier.padding(TinoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
            ) {
                component.iconKey?.let { key ->
                    Icon(
                        actionIcon(key),
                        contentDescription = null,
                        tint = TinoGreen,
                        modifier = Modifier.size(TinoSize.iconNormal),
                    )
                }
                Text(component.title, style = MaterialTheme.typography.titleMedium, color = TinoInk)
            }
            component.entityName?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = TinoInk)
            }
            component.primaryValueText?.let {
                Text(it, style = MaterialTheme.typography.headlineSmall, color = TinoGreen, maxLines = 1)
            }
            if (component.detailRows.isNotEmpty()) {
                component.detailRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(row.label, style = MaterialTheme.typography.bodyMedium, color = TinoMuted)
                        Text(row.value, style = MaterialTheme.typography.bodyMedium, color = TinoInk, maxLines = 1)
                    }
                }
            } else {
                Text(component.detail, style = MaterialTheme.typography.bodyLarge, color = TinoInk)
            }
            if (!component.complete) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
                ) {
                    androidx.compose.material3.OutlinedButton(
                        modifier = Modifier.weight(1f).heightIn(min = TinoSize.minTouch),
                        shape = TinoShapes.full,
                        onClick = onCancelled,
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = TinoSize.minTouch)
                            .semantics {
                                contentDescription = buildString {
                                    append("Confirmar")
                                    component.entityName?.let { append(" para $it") }
                                    component.primaryValueText?.let { append(" no valor de $it") }
                                }
                            },
                        shape = TinoShapes.full,
                        onClick = onConfirmed,
                    ) {
                        Text("Confirmar")
                    }
                }
            } else if (component.undoAvailable && component.activityId != null) {
                androidx.compose.material3.OutlinedButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = TinoSize.minTouch),
                    shape = TinoShapes.full,
                    onClick = { onUndo(component.activityId) },
                ) {
                    Text("Desfazer")
                }
            }
        }
    }
}

private fun actionIcon(key: String) = when (key) {
    "payment" -> TinoIcons.Payment
    "credit" -> TinoIcons.Credit
    "stock_entry" -> TinoIcons.Input
    "price" -> TinoIcons.Edit
    else -> TinoIcons.Success
}

@Composable
private fun EntityChoiceView(
    component: A2uiComponent.EntityChoice,
    modifier: Modifier,
    onSelected: (String) -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
    ) {
        Column(
            modifier = Modifier.padding(TinoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            Text(component.title, style = MaterialTheme.typography.titleMedium, color = TinoInk)
            if (component.prompt.isNotBlank()) {
                Text(component.prompt, style = MaterialTheme.typography.bodySmall, color = TinoMuted)
            }
            component.options.forEach { option ->
                androidx.compose.material3.OutlinedButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = TinoSize.minTouch),
                    shape = TinoShapes.full,
                    onClick = { onSelected(option.label) },
                ) {
                    if (component.entityType == "payment_method") {
                        Icon(paymentIcon(option.label), contentDescription = null, tint = TinoGreen)
                    }
                    Text(option.label, modifier = Modifier.padding(start = TinoSpacing.xs))
                }
            }
        }
    }
}

private fun paymentIcon(label: String) = when {
    label.contains("pix", ignoreCase = true) -> TinoIcons.Pix
    label.contains("dinheiro", ignoreCase = true) -> TinoIcons.Cash
    else -> TinoIcons.Card
}

@Composable
private fun FinancialSummaryCardView(
    component: A2uiComponent.FinancialSummaryCard,
    modifier: Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(TinoSize.cardBorder, TinoBorder, TinoShapes.medium),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoElevation.subtle),
    ) {
        Column(
            modifier = Modifier.padding(TinoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.md),
        ) {
            Text(component.title, style = MaterialTheme.typography.titleMedium, color = TinoInk)
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(component.primaryLabel, style = MaterialTheme.typography.bodyMedium, color = TinoMuted)
                Text(
                    component.primaryValueText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TinoGreen,
                )
            }
            HorizontalDivider(color = TinoBorder)
            component.metrics.forEachIndexed { index, metric ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(metric.label, style = MaterialTheme.typography.bodyMedium, color = TinoMuted)
                    Text(metric.valueText, style = MaterialTheme.typography.labelLarge, color = TinoInk)
                }
                if (index < component.metrics.lastIndex) {
                    HorizontalDivider(color = TinoBorder.copy(alpha = 0.5f))
                }
            }
            component.emptyMessage?.let { message ->
                Text(
                    message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TinoGreenTint, TinoShapes.small)
                        .padding(TinoSpacing.sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = TinoMuted,
                )
            }
        }
    }
}

@Composable
private fun CustomerBalanceCardView(
    component: A2uiComponent.CustomerBalanceCard,
    modifier: Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().border(TinoSize.cardBorder, TinoBorder, TinoShapes.medium),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoElevation.subtle),
    ) {
        Column(
            modifier = Modifier.padding(TinoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            Text(component.customerName, style = MaterialTheme.typography.titleMedium, color = TinoInk)
            Text(component.currentBalanceText, style = MaterialTheme.typography.headlineSmall, color = TinoGreen)
            Text(
                component.openText.removePrefix("Em aberto: "),
                style = MaterialTheme.typography.bodyMedium,
                color = TinoMuted,
            )
            if (!component.overdueText.endsWith("R$ 0,00")) {
                Text(component.overdueText, style = MaterialTheme.typography.bodySmall, color = TinoAmber)
            }
            component.oldestOpenText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TinoMuted) }
            component.emptyMessage?.let {
                Text(
                    it,
                    modifier = Modifier.background(TinoGreenTint, TinoShapes.small).padding(TinoSpacing.sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = TinoMuted,
                )
            }
        }
    }
}

@Composable
private fun CustomerTimelineCardView(
    component: A2uiComponent.CustomerTimelineCard,
    modifier: Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().border(TinoSize.cardBorder, TinoBorder, TinoShapes.medium),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoElevation.subtle),
    ) {
        Column(
            modifier = Modifier.padding(TinoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            Text(component.customerName, style = MaterialTheme.typography.titleMedium, color = TinoInk)
            Text(component.currentBalanceText, style = MaterialTheme.typography.headlineSmall, color = TinoGreen)
            HorizontalDivider(color = TinoBorder)
            component.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
                ) {
                    Text(item.dateText, modifier = Modifier.weight(0.25f), color = TinoMuted)
                    Text(item.label, modifier = Modifier.weight(0.5f), color = TinoInk)
                    Text(item.amountText, modifier = Modifier.weight(0.25f), color = TinoInk)
                }
            }
            component.emptyMessage?.let {
                Text(
                    it,
                    modifier = Modifier.background(TinoGreenTint, TinoShapes.small).padding(TinoSpacing.sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = TinoMuted,
                )
            }
        }
    }
}

@Composable
private fun ReadListCardView(
    component: A2uiComponent.ReadListCard,
    modifier: Modifier,
) {
    val isReplenishment = component.type == TinoA2UiComponentCatalog.PRODUCT_REPLENISHMENT
    Card(
        modifier = modifier.fillMaxWidth().border(TinoSize.cardBorder, TinoBorder, TinoShapes.medium),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (isReplenishment) TinoAmberContainer else TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoElevation.subtle),
    ) {
        Column(
            modifier = Modifier.padding(TinoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
            ) {
                if (isReplenishment) Icon(TinoIcons.Warning, contentDescription = "Atenção", tint = TinoAmber)
                Text(component.title, style = MaterialTheme.typography.titleMedium, color = TinoInk)
            }
            component.items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TinoSize.minTouch),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
                ) {
                    Icon(
                        imageVector = listItemIcon(item),
                        contentDescription = item.context ?: "Item",
                        tint = listItemStatusColor(item),
                        modifier = Modifier
                            .size(TinoSize.iconLarge)
                            .background(listItemStatusColor(item).copy(alpha = 0.12f), TinoShapes.small)
                            .padding(TinoSpacing.xs),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
                    ) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = TinoInk,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        item.context?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = TinoMuted)
                        }
                        item.supportingText?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = listItemStatusColor(item),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
                    ) {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text(
                                item.primaryText,
                                style = MaterialTheme.typography.titleMedium,
                                color = listItemStatusColor(item),
                                maxLines = 1,
                            )
                            item.secondaryText?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TinoMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (item.actionId != null) {
                            Icon(TinoIcons.Forward, contentDescription = "Abrir", tint = TinoMuted)
                        }
                    }
                }
                if (index < component.items.lastIndex) HorizontalDivider(color = TinoBorder.copy(alpha = 0.5f))
            }
            component.emptyMessage?.let {
                Text(
                    it,
                    modifier = Modifier.background(TinoGreenTint, TinoShapes.small).padding(TinoSpacing.sm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TinoMuted,
                )
            }
        }
    }
}

private fun listItemIcon(item: A2uiListItem) = when (item.iconKey) {
    "inventory" -> TinoIcons.Products
    "customer" -> TinoIcons.People
    else -> TinoIcons.Document
}

private fun listItemStatusColor(item: A2uiListItem) = when (item.status) {
    A2uiVisualStatus.SUCCESS -> TinoGreen
    A2uiVisualStatus.WARNING -> TinoAmber
    A2uiVisualStatus.ERROR -> TinoRed
    A2uiVisualStatus.INFO -> TinoMuted
    A2uiVisualStatus.NORMAL -> TinoInk
}

@Composable
private fun UnsupportedA2uiCard(modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = TinoRedContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoElevation.none),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(TinoSpacing.lg),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            Icon(TinoIcons.Error, contentDescription = "Erro", tint = TinoRed)
            Text(
                "Não foi possível mostrar esta resposta.",
                style = MaterialTheme.typography.bodyMedium,
                color = TinoInk,
            )
        }
    }
}
