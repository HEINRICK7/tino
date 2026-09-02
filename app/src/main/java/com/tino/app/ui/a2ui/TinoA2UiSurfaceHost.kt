package com.tino.app.ui.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceHost
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceMessage
import com.tino.app.interfaceadapter.a2ui.A2uiMessage
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceOperation
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceState
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceComponent
import com.tino.app.interfaceadapter.a2ui.A2uiActionEvent
import com.tino.app.interfaceadapter.a2ui.TinoComponentCatalogValidator
import com.tino.app.interfaceadapter.a2ui.TinoComponentValidation
import com.tino.app.interfaceadapter.a2ui.CoreTinoComponentCatalog
import com.tino.app.interfaceadapter.a2ui.toSurfaceMessage
import com.tino.app.ui.components.tinoAnimateContentSize
import com.tino.app.ui.components.TinoCardRenderer
import com.tino.app.ui.components.TinoCardSpec
import com.tino.app.ui.components.TinoCardStatus
import com.tino.app.ui.components.TinoSecondaryButton
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoSpacing

/**
 * Surface host is the only Compose-aware part of the new protocol. It applies
 * lifecycle messages incrementally, then delegates the resulting typed data to
 * the existing renderer. No action is inferred or executed here.
 */
@Composable
fun TinoA2UiSurfaceHost(
    message: A2uiSurfaceMessage,
    modifier: Modifier = Modifier,
    onAction: (A2uiActionEvent) -> Unit = {},
) {
    val host = remember { A2uiSurfaceHost() }
    var state by remember { mutableStateOf<A2uiSurfaceState?>(null) }
    LaunchedEffect(message.messageId, message.operation, message.surfaceId) {
        state = when (val result = host.apply(message)) {
            is com.tino.app.interfaceadapter.a2ui.A2uiSurfaceApplyResult.Applied -> result.state
            is com.tino.app.interfaceadapter.a2ui.A2uiSurfaceApplyResult.Rejected -> host.snapshot(message.surfaceId)
        }
    }

    state?.let { current ->
        Column(modifier.tinoAnimateContentSize(), verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
            current.components.forEach { component ->
                TinoA2UiSurfaceComponentView(component, current.surfaceId, current.dataModel, onAction)
            }
        }
    }
}

/** Compatibility entry point: repeated typed results update the same surfaceId. */
@Composable
fun TinoA2UiSurfaceHost(
    message: A2uiMessage,
    surfaceId: String,
    modifier: Modifier = Modifier,
    onAction: (A2uiActionEvent) -> Unit = {},
) {
    val host = remember { A2uiSurfaceHost() }
    var state by remember { mutableStateOf<A2uiSurfaceState?>(null) }
    LaunchedEffect(message.messageId) {
        val operation = if (host.snapshot(surfaceId) == null) {
            A2uiSurfaceOperation.CREATE_SURFACE
        } else {
            A2uiSurfaceOperation.UPDATE_COMPONENTS
        }
        val surfaceMessage = message.toSurfaceMessage(surfaceId, operation)
        state = when (val result = host.apply(surfaceMessage)) {
            is com.tino.app.interfaceadapter.a2ui.A2uiSurfaceApplyResult.Applied -> result.state
            is com.tino.app.interfaceadapter.a2ui.A2uiSurfaceApplyResult.Rejected -> host.snapshot(surfaceId)
        }
    }
    state?.let { current ->
        Column(modifier.tinoAnimateContentSize(), verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
            current.components.forEach { component ->
                TinoA2UiSurfaceComponentView(component, current.surfaceId, current.dataModel, onAction)
            }
        }
    }
}

@Composable
private fun TinoA2UiSurfaceComponentView(
    component: A2uiSurfaceComponent,
    surfaceId: String,
    dataModel: Map<String, String>,
    onAction: (A2uiActionEvent) -> Unit,
) {
    when (val validation = TinoComponentCatalogValidator.validate(component)) {
        is TinoComponentValidation.Unknown -> TinoA2UiSurfaceFallbackCard(
            title = "Componente não disponível",
            message = validation.reason,
        )
        is TinoComponentValidation.InvalidProps -> TinoA2UiSurfaceFallbackCard(
            title = "Surface incompleta",
            message = validation.reason,
        )
        TinoComponentValidation.Allowed -> {
            if (component.type in TinoCustomCatalogTypes) {
                TinoCatalogSurfaceComponentView(component, surfaceId, dataModel, onAction)
                return
            }
            if (component.type == CoreTinoComponentCatalog.TEXT) {
                Text(
                    component.props["text"] ?: component.bindings.entries.firstNotNullOfOrNull { (_, path) -> dataModel[path] }.orEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TinoInk,
                )
                return
            }
            if (component.type == CoreTinoComponentCatalog.BUTTON) {
                TinoSecondaryButton(component.props["label"] ?: "Ação") {
                    emitSurfaceAction(component, surfaceId, onAction)
                }
                return
            }
            val title = component.props["title"]
                ?: component.props["label"]
                ?: component.props["name"]
                ?: component.props["product"]
                ?: component.type
            val value = component.bindings.entries.firstNotNullOfOrNull { (property, path) ->
                dataModel[path]?.let { property to it }
            }?.second
                ?: component.props["answer"]
                ?: component.props["value"]
                ?: component.props["message"]
                ?: component.props["text"]
            val actionLabel = component.actions.firstOrNull()?.let { actionName ->
                component.actionLabels[actionName] ?: actionName.surfaceLabel()
            }
            val message = listOfNotNull(
                value?.takeIf { it.isNotBlank() },
                component.props["status"]?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifBlank { "Informação do TINO" }
            val status = when (component.props["status"]?.uppercase()) {
                "WARNING", "LOW", "PENDING" -> TinoCardStatus.WARNING
                "ERROR", "FAILED", "OVERDUE" -> TinoCardStatus.ERROR
                "SUCCESS", "OK", "DONE" -> TinoCardStatus.SUCCESS
                else -> TinoCardStatus.INFO
            }
            TinoCardRenderer(
                TinoCardSpec.Status(
                    icon = surfaceIcon(component.type),
                    title = title,
                    message = message,
                    status = status,
                    actionLabel = actionLabel,
                    onAction = if (component.actions.isEmpty()) null else {
                        { emitSurfaceAction(component, surfaceId, onAction) }
                    },
                ),
            )
        }
    }
}

/** Small adapter used by the physical smoke surface and by unknown-component fallback tests. */
@Composable
fun TinoA2UiSurfaceFallbackCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    TinoCardRenderer(
        TinoCardSpec.Status(
            icon = TinoIcons.Warning,
            title = title,
            message = message,
            modifier = modifier,
            status = TinoCardStatus.WARNING,
        ),
    )
}

private fun emitSurfaceAction(
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

private fun surfaceIcon(type: String) = when {
    type.contains("product", ignoreCase = true) || type.contains("inventory", ignoreCase = true) -> TinoIcons.Products
    type.contains("customer", ignoreCase = true) || type.contains("credit", ignoreCase = true) -> TinoIcons.People
    type.contains("payment", ignoreCase = true) || type.contains("money", ignoreCase = true) -> TinoIcons.Payment
    type.contains("insight", ignoreCase = true) || type.contains("trend", ignoreCase = true) -> TinoIcons.Trends
    type.contains("operation", ignoreCase = true) || type.contains("confirmation", ignoreCase = true) -> TinoIcons.Success
    else -> TinoIcons.Document
}

private fun String.surfaceLabel(): String = when (lowercase()) {
    "open", "open_details", "view_details", "details" -> "Ver detalhes"
    "retry", "try_again" -> "Tentar novamente"
    "confirm", "confirm_action" -> "Confirmar"
    "cancel", "cancel_action" -> "Cancelar"
    "edit" -> "Editar"
    "register", "save" -> "Salvar"
    else -> replace('_', ' ').replaceFirstChar { it.uppercase() }
}
