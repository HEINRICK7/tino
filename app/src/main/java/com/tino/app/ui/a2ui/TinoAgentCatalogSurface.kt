package com.tino.app.ui.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tino.app.BuildConfig
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.feature.voice.AgenticVoiceState
import com.tino.app.interfaceadapter.a2ui.A2uiMessage
import com.tino.app.interfaceadapter.a2ui.A2uiSemanticMapper
import com.tino.app.interfaceadapter.a2ui.A2uiSurfacePolicy
import com.tino.app.ui.components.TinoCard
import com.tino.app.ui.components.TinoPrimaryButton
import com.tino.app.ui.components.TinoSecondaryButton
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoSpacing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.tino.app.domain.profile.CapabilityRecoveryPolicy

fun AgenticVoiceState.presentsBottomRiseCatalog(): Boolean = catalogMessage() != null

fun AgenticVoiceState.catalogMessage(): A2uiMessage? = when (this) {
    is AgenticVoiceState.Result -> response.message
    is AgenticVoiceState.CustomerBalanceResult -> response.message
    is AgenticVoiceState.CustomerTimelineResult -> response.message
    is AgenticVoiceState.ReadListResult -> response.message
    is AgenticVoiceState.IntelligenceResult -> response.message
    is AgenticVoiceState.EntityChoice -> response.message
    is AgenticVoiceState.ActionPreview -> response.message
    is AgenticVoiceState.ActionCompleted -> response.message
    is AgenticVoiceState.Unsupported -> A2uiSemanticMapper.error(
        message = message,
        title = "AINDA NÃO CONSIGO RESPONDER",
    )
    is AgenticVoiceState.Error -> A2uiSemanticMapper.error(
        message = message,
        title = "NÃO ENTENDI DIREITO",
    )
    else -> null
}

@Composable
fun TinoAgentCatalogSurface(
    state: AgenticVoiceState,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onActionConfirm: (AgenticVoiceState.ActionPreview) -> Unit,
    onUndo: (String) -> Unit,
    onEntityChoiceSelected: (AgenticVoiceState.EntityChoice, String) -> Unit,
    onCapabilityUseOnce: () -> Unit,
    onCapabilityActivate: (TinoCapabilityId) -> Unit,
    onCardAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = state.catalogMessage() ?: return
    val spec = A2uiSurfacePolicy.forComponent(message.component.type)
    TinoA2UiBottomSurface(
        size = spec.size,
        title = A2uiSurfacePolicy.titleFor(message.component),
        subtitle = A2uiSurfacePolicy.subtitleFor(message.component),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        when (state) {
            is AgenticVoiceState.IntelligenceResult -> TinoA2UiSurfaceHost(
                message = state.response.message,
                surfaceId = "tino-intelligence-surface",
            )
            is AgenticVoiceState.EntityChoice -> TinoA2UiRenderer(
                message = message,
                onEntityChoiceSelected = { label -> onEntityChoiceSelected(state, label) },
            )
            is AgenticVoiceState.ActionPreview -> TinoA2UiRenderer(
                message = message,
                onActionConfirmed = { onActionConfirm(state) },
                onActionCancelled = onDismiss,
            )
            is AgenticVoiceState.ActionCompleted -> TinoA2UiRenderer(
                message = message,
                onUndo = onUndo,
            )
            is AgenticVoiceState.Unsupported,
            is AgenticVoiceState.Error,
            -> TinoA2UiRenderer(message = message, onRetry = onStart)
            else -> TinoA2UiRenderer(message = message, onCardAction = onCardAction)
        }
        when (state) {
            is AgenticVoiceState.Result,
            is AgenticVoiceState.CustomerBalanceResult,
            is AgenticVoiceState.CustomerTimelineResult,
            is AgenticVoiceState.IntelligenceResult,
            is AgenticVoiceState.ActionCompleted,
            -> TinoPrimaryButton("NOVA PERGUNTA", onStart)
            is AgenticVoiceState.EntityChoice -> TinoSecondaryButton("CANCELAR", onDismiss)
            is AgenticVoiceState.Unsupported -> {
                val capability = (state as AgenticVoiceState.Unsupported).debug?.capability
                    ?.let { runCatching { TinoCapabilityId.valueOf(it) }.getOrNull() }
                if ((state as AgenticVoiceState.Unsupported).debug?.capability != null) {
                    TinoPrimaryButton("USAR UMA VEZ", onCapabilityUseOnce)
                    if (capability != null && CapabilityRecoveryPolicy.canActivatePermanently(capability)) {
                        TinoSecondaryButton("ATIVAR SEMPRE", { onCapabilityActivate(capability) })
                    }
                }
                TinoSecondaryButton("CANCELAR", onDismiss)
            }
            is AgenticVoiceState.Error -> TinoSecondaryButton("CANCELAR", onDismiss)
            else -> Unit
        }
        if (BuildConfig.DEBUG) {
            DebugMetrics(state)
        }
    }
}

@Composable
private fun DebugMetrics(state: AgenticVoiceState) {
    val text = when (state) {
        is AgenticVoiceState.Result ->
            "Fast Router ${if (state.metrics.fastRouterHit) "HIT" else "MISS"} ${state.metrics.fastRouterMs}ms"
        is AgenticVoiceState.CustomerBalanceResult ->
            "cliente ${state.metrics.customerResolutionMs ?: 0}ms · tool ${state.metrics.capabilityMs}ms"
        is AgenticVoiceState.CustomerTimelineResult ->
            "tool ${state.metrics.capabilityMs}ms · A2UI ${state.metrics.a2uiMs}ms"
        is AgenticVoiceState.IntelligenceResult ->
            "status ${state.response.response.status.name} · plano ${state.response.response.plan.size}"
        else -> null
    } ?: return
    TinoCard {
        Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Text("DEBUG", style = MaterialTheme.typography.labelLarge)
            Text(text, color = TinoMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
