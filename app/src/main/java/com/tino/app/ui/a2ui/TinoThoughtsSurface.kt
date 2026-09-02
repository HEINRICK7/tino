package com.tino.app.ui.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tino.app.domain.agent.AgentCapability
import com.tino.app.domain.intelligence.ThoughtClaimKind
import com.tino.app.domain.intelligence.ThoughtUncertainty
import com.tino.app.domain.intelligence.TinoThought
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceSize
import com.tino.app.ui.components.TinoCard
import com.tino.app.ui.components.TinoTextAction
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoSpacing
import androidx.compose.material3.MaterialTheme

@Composable
fun TinoThoughtsSurface(
    thoughts: List<TinoThought>,
    onSelect: (TinoThought) -> Unit,
    onDismissThought: (TinoThought) -> Unit = {},
    onSnoozeThought: (TinoThought) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TinoA2UiBottomSurface(
        size = A2uiSurfaceSize.MEDIUM,
        title = "O TINO percebeu",
        subtitle = if (thoughts.size == 1) {
            "Tem 1 coisa que pode ser importante."
        } else {
            "${thoughts.size} coisas podem merecer sua atenção."
        },
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        thoughts.forEach { thought ->
            TinoCard {
                Text(thought.title, color = TinoInk, fontWeight = FontWeight.SemiBold)
                Text(thought.body, color = TinoInk, style = MaterialTheme.typography.bodyMedium)
                thought.why?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = TinoMuted, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    thought.claimKind.label() + " · " + thought.uncertainty.label(),
                    color = TinoMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TinoTextAction("Dispensar", { onDismissThought(thought) }, color = TinoMuted)
                    TinoTextAction("Amanhã", { onSnoozeThought(thought) }, color = TinoMuted)
                    if (thought.capability != null) {
                        TinoTextAction(thought.actionLabel ?: "Ver", { onSelect(thought) }, color = TinoGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun ThoughtClaimKind.label(): String = when (this) {
    ThoughtClaimKind.FACT -> "Fato"
    ThoughtClaimKind.INFERENCE -> "Estimativa"
    ThoughtClaimKind.FORECAST -> "Previsão"
}

private fun ThoughtUncertainty.label(): String = when (this) {
    ThoughtUncertainty.KNOW -> "Sei"
    ThoughtUncertainty.SUSPECT -> "Suspeito"
    ThoughtUncertainty.AMBIGUOUS -> "Não sei"
}

fun TinoThought.capabilityOrNull(): AgentCapability? = capability
