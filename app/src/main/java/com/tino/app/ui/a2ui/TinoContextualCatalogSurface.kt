package com.tino.app.ui.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tino.app.domain.agent.AgentCapability
import com.tino.app.domain.agent.ScreenAgentContext
import com.tino.app.domain.agent.TinoContextCatalog
import com.tino.app.domain.agent.TinoContextSection
import com.tino.app.domain.agent.TinoContextualAction
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceSize
import com.tino.app.ui.components.TinoAgentInput
import com.tino.app.ui.components.TinoCardRenderer
import com.tino.app.ui.components.TinoCardSpec
import com.tino.app.ui.components.TinoCardStatus
import com.tino.app.ui.components.TinoCardSurface
import com.tino.app.ui.components.TinoSecondaryButton
import com.tino.app.ui.components.TinoSectionHeader
import com.tino.app.ui.components.TinoSystemAction
import com.tino.app.ui.components.tinoClickable
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.theme.TinoAmber
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoGreenTint
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoSize
import com.tino.app.ui.theme.TinoSpacing

@Composable
fun TinoContextualCatalogSurface(
    context: ScreenAgentContext,
    onCapability: (AgentCapability, String) -> Unit,
    onSpeak: () -> Unit,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSubmitText: (String) -> Unit = {},
    attentionCount: Int = 0,
    onOpenAttention: () -> Unit = {},
) {
    val catalog = remember(context) { TinoContextCatalog.forContext(context) }
    var showMore by remember(context.screen, context.primaryEntity, context.availableCapabilities) {
        mutableStateOf(false)
    }
    var typed by remember(context.screen) { mutableStateOf("") }
    val moreActions = catalog.primary.drop(2) + catalog.more
    TinoA2UiBottomSurface(
        size = A2uiSurfaceSize.MEDIUM,
        title = catalog.title,
        subtitle = catalog.subtitle,
        onDismiss = onDismiss,
        modifier = modifier,
        scrollContent = showMore,
        header = {
            TinoSectionHeader(
                title = if (showMore) "Ações do TINO" else "Sugestões",
                actionLabel = if (moreActions.isEmpty()) {
                    null
                } else if (showMore) {
                    "← Voltar"
                } else {
                    "Mais"
                },
                onAction = if (moreActions.isEmpty()) {
                    null
                } else {
                    { showMore = !showMore }
                },
            )
        },
    ) {
        if (!showMore) {
            if (attentionCount > 0) {
                TinoCardSurface(
                    status = TinoCardStatus.WARNING,
                    onClick = {
                        onDismiss()
                        onOpenAttention()
                    },
                    description = "Abrir $attentionCount item(ns) que o TINO encontrou para sua atenção.",
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = TinoSize.minTouch),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(TinoIcons.Warning, contentDescription = null, tint = TinoAmber)
                        Spacer(Modifier.width(TinoSpacing.sm))
                        Text(
                            "$attentionCount coisas merecem atenção",
                            modifier = Modifier.weight(1f),
                            color = TinoInk,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(TinoIcons.Forward, contentDescription = null, tint = TinoAmber)
                    }
                }
            }
            TinoCompactActionCard(
                actions = catalog.primary.take(2).map { action ->
                    action.toSystemAction(onCapability, onSpeak, onNavigate, onDismiss)
                },
            )
        } else {
            moreActions.groupBy { it.section }.forEach { (section, actions) ->
                Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                    Text(section.label(), color = TinoMuted)
                    TinoCardRenderer(
                        TinoCardSpec.Action(
                            actions = actions.map { action ->
                                action.toSystemAction(onCapability, onSpeak, onNavigate, onDismiss)
                            },
                        ),
                    )
                }
            }
        }
        TinoAgentInput(
            value = typed,
            onValueChange = { typed = it },
            onSubmit = {
                val text = typed.trim()
                if (text.isNotBlank()) {
                    onDismiss()
                    onSubmitText(text)
                }
            },
            onVoice = {
                onDismiss()
                onSpeak()
            },
        )
    }
}

@Composable
private fun TinoCompactActionCard(actions: List<TinoSystemAction>) {
    if (actions.isEmpty()) return
    TinoCardSurface(
        status = TinoCardStatus.NEUTRAL,
        description = "Sugestões do TINO",
    ) {
        actions.forEachIndexed { index, action ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TinoSize.minTouch)
                    .tinoClickable(onClick = action.onClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    action.icon,
                    contentDescription = null,
                    tint = TinoGreen,
                    modifier = Modifier
                        .padding(TinoSpacing.xs)
                        .background(TinoGreenTint, androidx.compose.foundation.shape.CircleShape)
                        .padding(TinoSpacing.sm),
                )
                Spacer(Modifier.width(TinoSpacing.sm))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xxs)) {
                    Text(
                        action.title,
                        color = TinoInk,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        action.subtitle,
                        color = TinoMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(TinoIcons.Forward, contentDescription = null, tint = TinoMuted)
            }
            if (index < actions.lastIndex) {
                HorizontalDivider(color = TinoGreen.copy(alpha = 0.10f))
            }
        }
    }
}

private fun TinoContextSection.label(): String = when (this) {
    TinoContextSection.SUGGESTED -> "Sugestões"
    TinoContextSection.QUERY -> "Consultas"
    TinoContextSection.ACTION -> "Operações"
    TinoContextSection.HISTORY -> "Histórico"
}

private fun TinoContextualAction.toSystemAction(
    onCapability: (AgentCapability, String) -> Unit,
    onSpeak: () -> Unit,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit,
) = TinoSystemAction(
    icon = when {
        speak -> TinoIcons.Voice
        mutation -> TinoIcons.Credit
        id.contains("stock") || id.contains("product") -> TinoIcons.Products
        id.contains("customer") || id.contains("receivable") || id.contains("overdue") || id.contains("debt") -> TinoIcons.People
        id.contains("today") || id.contains("financ") -> TinoIcons.Trends
        id.contains("supplier") -> TinoIcons.Supplier
        else -> TinoIcons.Forward
    },
    title = title,
    subtitle = when {
        speak -> "Usar a voz neste contexto"
        mutation -> "Vai pedir confirmação"
        navigateScreen != null -> "Abrir tela"
        else -> "Consulta"
    },
    onClick = {
        onDismiss()
        when {
            speak -> onSpeak()
            navigateScreen != null -> onNavigate(navigateScreen)
            capability != null -> onCapability(capability, title)
        }
    },
)
