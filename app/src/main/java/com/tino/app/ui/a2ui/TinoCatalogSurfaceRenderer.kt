package com.tino.app.ui.a2ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tino.app.interfaceadapter.a2ui.A2uiActionEvent
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceComponent
import com.tino.app.interfaceadapter.a2ui.TinoCustomComponentCatalog
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.theme.TinoAmber
import com.tino.app.ui.theme.TinoAmberContainer
import com.tino.app.ui.theme.TinoBorder
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoGreenTint
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoPaper
import com.tino.app.ui.theme.TinoRed
import com.tino.app.ui.theme.TinoRedContainer
import com.tino.app.ui.theme.TinoShapes
import com.tino.app.ui.theme.TinoSize
import com.tino.app.ui.theme.TinoSpacing

internal val TinoCustomCatalogTypes = setOf(
    TinoCustomComponentCatalog.METRIC_CARD,
    TinoCustomComponentCatalog.PRODUCT_CARD,
    TinoCustomComponentCatalog.CUSTOMER_CARD,
    TinoCustomComponentCatalog.DEBT_CARD,
    TinoCustomComponentCatalog.INVENTORY_ALERT_CARD,
    TinoCustomComponentCatalog.SALE_CARD,
    TinoCustomComponentCatalog.SUMMARY_CARD,
    TinoCustomComponentCatalog.QUICK_QUERY_CARD,
    TinoCustomComponentCatalog.CONFIRMATION_CARD,
    TinoCustomComponentCatalog.STATUS_CARD,
    TinoCustomComponentCatalog.MINI_CHART,
)

@Composable
internal fun TinoCatalogSurfaceComponentView(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    dataModel: Map<String, String>,
    onAction: (A2uiActionEvent) -> Unit,
) {
    fun value(name: String): String? = component.bindings[name]?.let(dataModel::get)
        ?: component.props[name]

    when (component.type) {
        TinoCustomComponentCatalog.SUMMARY_CARD -> TinoSummaryCatalogCard(component, surfaceId, ::value, onAction)
        TinoCustomComponentCatalog.QUICK_QUERY_CARD -> TinoQuickQueryCatalogCard(component, surfaceId, ::value, onAction)
        TinoCustomComponentCatalog.CONFIRMATION_CARD -> TinoConfirmationCatalogCard(component, surfaceId, ::value, onAction)
        TinoCustomComponentCatalog.STATUS_CARD -> TinoStatusCatalogCard(component, surfaceId, ::value, onAction)
        TinoCustomComponentCatalog.MINI_CHART -> TinoMiniChartCatalogCard(component, surfaceId, ::value, onAction)
        else -> TinoEntityCatalogCard(component, surfaceId, ::value, onAction)
    }
}

@Composable
private fun TinoEntityCatalogCard(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    value: (String) -> String?,
    onAction: (A2uiActionEvent) -> Unit,
) {
    val status = value("status")
    val color = catalogStatusColor(status)
    val title = value("title") ?: component.type
    val context = value("context") ?: status
    val primary = value("value")
    val supporting = value("supportingText")
    TinoCatalogCardFrame(
        color = catalogContainerColor(status),
        description = listOfNotNull(title, primary, supporting).joinToString(", "),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = TinoSize.minTouch),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            CatalogIcon(value("icon"), status, color)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TinoInk, maxLines = 1)
                context?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TinoMuted, maxLines = 1) }
                supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1) }
            }
            primary?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
            }
        }
        CatalogActions(component, surfaceId, onAction)
    }
}

@Composable
private fun TinoSummaryCatalogCard(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    value: (String) -> String?,
    onAction: (A2uiActionEvent) -> Unit,
) {
    TinoCatalogCardFrame(description = value("title") ?: "Resumo") {
        Text(value("title") ?: "Resumo", style = MaterialTheme.typography.titleSmall, color = TinoInk)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CatalogMetric("Vendas", value("salesValue"))
            CatalogMetric("Recebidos", value("receivedValue"))
            CatalogMetric("Fiado", value("creditValue"))
        }
        CatalogActions(component, surfaceId, onAction)
    }
}

@Composable
private fun CatalogMetric(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TinoMuted)
        Text(value ?: "—", style = MaterialTheme.typography.titleMedium, color = TinoInk, maxLines = 1)
    }
}

@Composable
private fun TinoQuickQueryCatalogCard(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    value: (String) -> String?,
    onAction: (A2uiActionEvent) -> Unit,
) {
    TinoCatalogCardFrame(description = listOfNotNull(value("title"), value("supportingText")).joinToString(", ")) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = TinoSize.listRowHeight),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            CatalogIcon(value("icon"), null, TinoGreen)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(value("title") ?: "Consulta", style = MaterialTheme.typography.titleSmall, color = TinoInk)
                value("supportingText")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TinoMuted) }
            }
        }
        CatalogActions(component, surfaceId, onAction)
    }
}

@Composable
private fun TinoConfirmationCatalogCard(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    value: (String) -> String?,
    onAction: (A2uiActionEvent) -> Unit,
) {
    TinoCatalogCardFrame(description = listOfNotNull(value("title"), value("entity"), value("value")).joinToString(", ")) {
        Text(value("title") ?: "Confirmar operação", style = MaterialTheme.typography.titleSmall, color = TinoInk)
        value("entity")?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = TinoInk) }
        value("value")?.let { Text(it, style = MaterialTheme.typography.headlineSmall, color = TinoGreen) }
        value("detail")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TinoMuted) }
        CatalogActions(component, surfaceId, onAction)
    }
}

@Composable
private fun TinoStatusCatalogCard(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    value: (String) -> String?,
    onAction: (A2uiActionEvent) -> Unit,
) {
    val status = value("status")
    TinoCatalogCardFrame(
        color = catalogContainerColor(status),
        description = listOfNotNull(value("title"), value("message")).joinToString(", "),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = TinoSize.minTouch),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            CatalogIcon("status", status, catalogStatusColor(status))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(value("title") ?: "Status", style = MaterialTheme.typography.titleSmall, color = TinoInk)
                Text(value("message") ?: "", style = MaterialTheme.typography.bodySmall, color = TinoMuted)
            }
        }
        CatalogActions(component, surfaceId, onAction)
    }
}

@Composable
private fun TinoMiniChartCatalogCard(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    value: (String) -> String?,
    onAction: (A2uiActionEvent) -> Unit,
) {
    TinoCatalogCardFrame(description = listOfNotNull(value("title"), value("value")).joinToString(", ")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(value("title") ?: "Tendência", style = MaterialTheme.typography.titleSmall, color = TinoInk)
                Text(value("value") ?: "—", style = MaterialTheme.typography.titleMedium, color = TinoInk)
            }
            value("series")?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = TinoGreen)
            }
        }
        value("labels")?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TinoMuted) }
        CatalogActions(component, surfaceId, onAction)
    }
}

@Composable
private fun TinoCatalogCardFrame(
    color: Color = TinoPaper,
    description: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(TinoSize.cardBorder, TinoBorder, TinoShapes.medium)
            .semantics { contentDescription = description },
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(
            modifier = Modifier.padding(TinoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            content()
        }
    }
}

@Composable
private fun CatalogIcon(iconKey: String?, status: String?, color: Color) {
    Icon(
        imageVector = when (iconKey) {
            "customer", "person" -> TinoIcons.People
            "debt", "credit" -> TinoIcons.Credit
            "sale", "cart" -> TinoIcons.Cart
            "metric", "chart" -> TinoIcons.Trends
            "search" -> TinoIcons.Search
            "status" -> if (status?.uppercase() == "ERROR") TinoIcons.Error else TinoIcons.Warning
            else -> TinoIcons.Products
        },
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .size(TinoSize.iconLarge)
            .background(color.copy(alpha = 0.12f), TinoShapes.small)
            .padding(TinoSpacing.xs),
    )
}

@Composable
private fun CatalogActions(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    onAction: (A2uiActionEvent) -> Unit,
) {
    if (component.actions.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
        component.actions.forEach { actionName ->
            TextButton(
                onClick = {
                    onAction(
                        A2uiActionEvent(
                            surfaceId = surfaceId,
                            componentId = component.componentId,
                            actionName = actionName,
                            payload = component.actionPayloads[actionName].orEmpty(),
                            sessionId = "default",
                        ),
                    )
                },
            ) {
                Text(
                    component.actionLabels[actionName]
                        ?: when (actionName) {
                            "request_details" -> "Ver detalhes"
                            "retry" -> "Tentar novamente"
                            "confirm_operation" -> "Confirmar"
                            "cancel_operation" -> "Cancelar"
                            else -> actionName
                        },
                    maxLines = 1,
                )
            }
        }
    }
}

private fun catalogStatusColor(status: String?): Color = when (status?.uppercase()) {
    "SUCCESS", "PAID", "AVAILABLE", "NORMAL" -> TinoGreen
    "WARNING", "LOW", "OPEN", "PARTIAL", "OUT_OF_STOCK" -> TinoAmber
    "ERROR", "OVERDUE", "CRITICAL" -> TinoRed
    else -> TinoMuted
}

private fun catalogContainerColor(status: String?): Color = when (status?.uppercase()) {
    "ERROR", "OVERDUE", "CRITICAL" -> TinoRedContainer
    "WARNING", "LOW", "OPEN", "PARTIAL", "OUT_OF_STOCK" -> TinoAmberContainer
    else -> TinoPaper
}
