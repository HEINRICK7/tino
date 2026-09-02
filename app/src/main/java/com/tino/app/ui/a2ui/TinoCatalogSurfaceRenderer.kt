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
import com.tino.app.ui.components.TinoCardStatus
import com.tino.app.ui.components.TinoCardRenderer
import com.tino.app.ui.components.TinoCardSpec
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
    TinoCustomComponentCatalog.ACTION_LIST_CARD,
    TinoCustomComponentCatalog.TIMELINE_CARD,
    TinoCustomComponentCatalog.EMPTY_STATE_CARD,
    TinoCustomComponentCatalog.CATALOG_CARD,
    TinoCustomComponentCatalog.CATALOG_LIST_CARD,
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
        TinoCustomComponentCatalog.ACTION_LIST_CARD -> TinoActionListCatalogCard(component, surfaceId, ::value, onAction)
        TinoCustomComponentCatalog.TIMELINE_CARD -> TinoTimelineCatalogCard(component, surfaceId, ::value, onAction)
        TinoCustomComponentCatalog.EMPTY_STATE_CARD -> TinoCardRenderer(
            TinoCardSpec.Empty(
                title = value("title") ?: "Nada por aqui ainda",
                message = value("message") ?: "Assim que tiver informações, elas vão aparecer.",
            ),
        )
        TinoCustomComponentCatalog.CATALOG_LIST_CARD -> TinoCatalogListSurfaceCard(component, surfaceId, ::value, onAction)
        else -> TinoEntityCatalogCard(component, surfaceId, ::value, onAction)
    }
}

@Composable
private fun TinoCatalogListSurfaceCard(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    value: (String) -> String?,
    onAction: (A2uiActionEvent) -> Unit,
) {
    val title = value("title") ?: "Resultados"
    val variant = value("variant") ?: "catalog"
    fun itemAction(item: com.tino.app.interfaceadapter.a2ui.A2uiCatalogItem): (() -> Unit)? =
        item.actionName?.let { actionName ->
            {
                onAction(
                    A2uiActionEvent(
                        surfaceId = surfaceId,
                        componentId = component.componentId,
                        actionName = actionName,
                        payload = item.actionPayload,
                        sessionId = "default",
                    ),
                )
            }
        }

    if (variant == "replenishment") {
        if (component.items.isEmpty()) {
            TinoCardRenderer(
                TinoCardSpec.Status(
                    icon = TinoIcons.Success,
                    title = "Tudo certo por aqui!",
                    message = value("emptyMessage") ?: "Nenhum produto precisa de reposição.",
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
                    status = TinoCardStatus.WARNING,
                    actionLabel = "Ver produtos",
                    onClick = firstAction(component, surfaceId, onAction),
                ),
            )
        }
    } else if (variant == "customers" || variant == "receivables") {
        TinoCardRenderer(
            TinoCardSpec.EntityList(
                title = title,
                items = component.items.map { item ->
                    val receivable = variant == "receivables"
                    com.tino.app.ui.components.TinoEntityCardSpec(
                        icon = if (receivable) TinoIcons.Credit else TinoIcons.People,
                        title = if (receivable) "Conta de ${item.title}" else item.title,
                        context = item.context ?: if (receivable) "Fiado" else "Cadastro",
                        primaryText = if (receivable) item.primaryText else "Cliente",
                        secondaryText = if (receivable) item.supportingText else null,
                        metadata = if (receivable) {
                            emptyList()
                        } else {
                            listOf(
                                "Telefone" to (item.secondaryText ?: "Não informado"),
                                "Situação" to "Cadastrado",
                            )
                        },
                        status = if (receivable) TinoCardStatus.CREDIT else TinoCardStatus.INFO,
                        footerLabel = if (receivable) "Ver conta" else "Ver detalhes do cliente",
                        onClick = itemAction(item),
                    )
                },
                emptyMessage = value("emptyMessage"),
            ),
        )
    } else {
        TinoCardRenderer(
            TinoCardSpec.CatalogList(
                title = title,
                items = component.items.map { item ->
                    com.tino.app.ui.components.TinoCatalogItemSpec(
                        icon = catalogIcon(item.iconKey),
                        title = item.title,
                        context = item.context,
                        primaryText = item.primaryText,
                        secondaryText = item.secondaryText,
                        statusText = item.supportingText ?: item.status.toReadableStatus(),
                        status = catalogCardStatus(item.status),
                        onClick = itemAction(item),
                    )
                },
                emptyMessage = value("emptyMessage"),
                footerLabel = component.actions.firstOrNull()?.let { component.actionLabels[it] ?: "Ver todos" },
                onFooter = component.actions.firstOrNull()?.let { actionName ->
                    {
                        onAction(
                            A2uiActionEvent(
                                surfaceId = surfaceId,
                                componentId = component.componentId,
                                actionName = actionName,
                                payload = component.actionPayloads[actionName].orEmpty(),
                                sessionId = "default",
                            ),
                        )
                    }
                },
                variant = if (variant == "products") "products" else "catalog",
            ),
        )
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
    val title = value("title") ?: component.type
    val context = value("context") ?: status
    val primary = value("value")
    val supporting = value("supportingText")
    if (title == "Nenhum produto cadastrado." || title == "Nada por aqui ainda") {
        TinoCardRenderer(TinoCardSpec.Empty(title = title, message = supporting ?: "Assim que tiver informações, elas vão aparecer."))
        return
    }
    TinoCardRenderer(TinoCardSpec.Catalog(
        icon = catalogIcon(value("icon")),
        title = title,
        context = context.orEmpty(),
        primaryText = primary.orEmpty(),
        secondaryText = value("secondaryText"),
        statusText = supporting,
        status = catalogCardStatus(status),
        actionLabel = firstActionLabel(component, "Ver detalhes"),
        onAction = firstAction(component, surfaceId, onAction),
    ))
}

@Composable
private fun TinoSummaryCatalogCard(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    value: (String) -> String?,
    onAction: (A2uiActionEvent) -> Unit,
) {
    TinoCardRenderer(TinoCardSpec.Metric(
        icon = TinoIcons.Trends,
        title = value("title") ?: "Resumo do dia",
        value = value("salesValue") ?: "—",
        supportingText = value("receivedValue") ?: "Sem recebimentos",
        details = listOfNotNull(
            value("creditValue")?.let { "Fiado" to it },
        ),
        actionLabel = firstActionLabel(component, "Ver detalhes"),
        onClick = firstAction(component, surfaceId, onAction),
    ))
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
    TinoCardRenderer(TinoCardSpec.Action(
        actions = listOf(
            com.tino.app.ui.components.TinoSystemAction(
                icon = catalogIcon(value("icon")),
                title = value("title") ?: "Consulta",
                subtitle = value("supportingText").orEmpty(),
                onClick = { emitFirstAction(component, surfaceId, onAction) },
            ),
        ),
    ))
}

@Composable
private fun TinoConfirmationCatalogCard(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    value: (String) -> String?,
    onAction: (A2uiActionEvent) -> Unit,
) {
    TinoCardRenderer(TinoCardSpec.Preview(
        title = value("title") ?: "Confirmar operação",
        rows = listOfNotNull(
            value("entity")?.let { "Item" to it },
            value("detail")?.let { "Detalhes" to it },
        ),
        total = value("value") ?: "—",
        onCancel = namedAction(component, surfaceId, onAction, "cancel"),
        onConfirm = firstAction(component, surfaceId, onAction),
    ))
}

@Composable
private fun TinoStatusCatalogCard(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    value: (String) -> String?,
    onAction: (A2uiActionEvent) -> Unit,
) {
    val status = value("status")
    TinoCardRenderer(TinoCardSpec.Status(
        icon = catalogIcon("status"),
        title = value("title") ?: "Status",
        message = value("message").orEmpty(),
        status = catalogCardStatus(status),
        actionLabel = firstActionLabel(component, "Ver detalhes"),
        onAction = firstAction(component, surfaceId, onAction),
    ))
}

@Composable
private fun TinoMiniChartCatalogCard(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    value: (String) -> String?,
    onAction: (A2uiActionEvent) -> Unit,
) {
    TinoCardRenderer(TinoCardSpec.Metric(
        icon = TinoIcons.Trends,
        title = value("title") ?: "Tendência",
        value = value("value") ?: "—",
        supportingText = value("series") ?: "",
        details = listOfNotNull(value("labels")?.let { "Período" to it }),
        actionLabel = firstActionLabel(component, "Ver detalhes"),
        onClick = firstAction(component, surfaceId, onAction),
    ))
}

@Composable
private fun TinoActionListCatalogCard(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    value: (String) -> String?,
    onAction: (A2uiActionEvent) -> Unit,
) {
    val items = value("items").orEmpty().split("||").mapNotNull { item ->
        val parts = item.split("|", limit = 3)
        if (parts.size == 3) parts else null
    }
    TinoCardRenderer(TinoCardSpec.Action(
        actions = items.map { parts ->
            com.tino.app.ui.components.TinoSystemAction(
                icon = catalogIcon(parts[0]),
                title = parts[1],
                subtitle = parts[2],
                onClick = { emitFirstAction(component, surfaceId, onAction) },
            )
        },
    ))
}

@Composable
private fun TinoTimelineCatalogCard(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    value: (String) -> String?,
    onAction: (A2uiActionEvent) -> Unit,
) {
    val items = value("items").orEmpty().split("||").mapNotNull { item ->
        val parts = item.split("|", limit = 3)
        if (parts.size == 3) parts else null
    }
    TinoCardRenderer(TinoCardSpec.Timeline(
        title = value("title") ?: "Atividades",
        items = items.map { parts -> Triple(parts[0], parts[1], parts[2]) },
        onClick = { emitFirstAction(component, surfaceId, onAction) },
    ))
}

private fun firstActionLabel(
    component: A2uiSurfaceComponent,
    fallback: String,
): String? = component.actions.firstOrNull()?.let { actionName ->
    component.actionLabels[actionName] ?: fallback
}

private fun firstAction(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    onAction: (A2uiActionEvent) -> Unit,
): (() -> Unit)? = component.actions.firstOrNull()?.let {
    { emitFirstAction(component, surfaceId, onAction) }
}

private fun namedAction(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    onAction: (A2uiActionEvent) -> Unit,
    namePart: String,
): (() -> Unit)? = component.actions.firstOrNull { it.contains(namePart, ignoreCase = true) }?.let { actionName ->
    {
        onAction(
            A2uiActionEvent(
                surfaceId = surfaceId,
                componentId = component.componentId,
                actionName = actionName,
                payload = component.actionPayloads[actionName].orEmpty(),
                sessionId = "default",
            ),
        )
    }
}

private fun emitFirstAction(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    onAction: (A2uiActionEvent) -> Unit,
) {
    component.actions.firstOrNull()?.let { actionName ->
        onAction(
            A2uiActionEvent(
                surfaceId = surfaceId,
                componentId = component.componentId,
                actionName = actionName,
                payload = component.actionPayloads[actionName].orEmpty(),
                sessionId = "default",
            ),
        )
    }
}

private fun catalogIcon(iconKey: String?, status: String? = null) = when (iconKey) {
    "customer", "person" -> TinoIcons.People
    "debt", "credit" -> TinoIcons.Credit
    "sale", "cart" -> TinoIcons.Cart
    "metric", "chart" -> TinoIcons.Trends
    "supplier", "truck" -> TinoIcons.Supplier
    "document", "note" -> TinoIcons.Document
    "search" -> TinoIcons.Search
    "status" -> if (status?.uppercase() == "ERROR") TinoIcons.Error else TinoIcons.Warning
    else -> TinoIcons.Products
}

private fun catalogCardStatus(status: String?): TinoCardStatus = when (status?.uppercase()) {
    "SUCCESS", "PAID", "AVAILABLE", "NORMAL" -> TinoCardStatus.SUCCESS
    "OPEN" -> TinoCardStatus.CREDIT
    "WARNING", "LOW", "PARTIAL", "OUT_OF_STOCK" -> TinoCardStatus.WARNING
    "ERROR", "OVERDUE", "CRITICAL" -> TinoCardStatus.ERROR
    "INFO" -> TinoCardStatus.INFO
    else -> TinoCardStatus.NEUTRAL
}

private fun String.toReadableStatus(): String? = when (uppercase()) {
    "NORMAL" -> null
    "AVAILABLE" -> "Disponível"
    "OUT_OF_STOCK" -> "Estoque zerado"
    "LOW", "WARNING" -> "Atenção"
    "OPEN" -> "Em aberto"
    "OVERDUE", "ERROR" -> "Atrasado"
    else -> null
}
