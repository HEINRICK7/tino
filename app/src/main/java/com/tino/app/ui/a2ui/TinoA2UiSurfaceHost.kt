package com.tino.app.ui.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.tino.app.ui.theme.TinoBorder
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoPaper
import com.tino.app.ui.theme.TinoShapes
import com.tino.app.ui.theme.TinoSize
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
        Column(modifier, verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
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
        Column(modifier, verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
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
                TextButton(
                    onClick = {
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
                    },
                ) {
                    Text(component.props["label"] ?: "Ação", maxLines = 1)
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = TinoShapes.medium,
                border = androidx.compose.foundation.BorderStroke(TinoSize.cardBorder, TinoBorder),
                colors = CardDefaults.cardColors(containerColor = TinoPaper),
            ) {
                Column(
                    modifier = Modifier.padding(TinoSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = TinoInk)
                    value?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = TinoInk)
                    }
                    component.props["status"]?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = TinoMuted)
                    }
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
                            Text(component.actionLabels[actionName] ?: actionName)
                        }
                    }
                }
            }
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TinoShapes.medium,
        border = androidx.compose.foundation.BorderStroke(TinoSize.cardBorder, TinoBorder),
        colors = CardDefaults.cardColors(containerColor = TinoPaper),
    ) {
        Column(
            modifier = Modifier.padding(TinoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TinoInk)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = TinoMuted)
        }
    }
}
