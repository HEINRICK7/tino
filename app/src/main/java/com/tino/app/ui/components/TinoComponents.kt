package com.tino.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.tino.app.ui.theme.LocalTinoReduceMotion
import com.tino.app.ui.theme.TinoMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import com.tino.app.R
import com.tino.app.core.database.CustomerBalance
import com.tino.app.core.database.ProductSummary
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.illustration.TinoIllustration
import com.tino.app.ui.illustration.TinoIllustrationState
import com.tino.app.ui.theme.TinoAmber
import com.tino.app.ui.theme.TinoAmberContainer
import com.tino.app.ui.theme.TinoBlue
import com.tino.app.ui.theme.TinoBorder
import com.tino.app.ui.theme.TinoElevation
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoGreenBorder
import com.tino.app.ui.theme.TinoGreenDark
import com.tino.app.ui.theme.TinoGreenLight
import com.tino.app.ui.theme.TinoGreenTint
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoOrange
import com.tino.app.ui.theme.TinoPaper
import com.tino.app.ui.theme.TinoPurple
import com.tino.app.ui.theme.TinoPurpleContainer
import com.tino.app.ui.theme.TinoRed
import com.tino.app.ui.theme.TinoRedContainer
import com.tino.app.ui.theme.TinoShapes
import com.tino.app.ui.theme.TinoSize
import com.tino.app.ui.theme.TinoSpacing
import com.tino.app.ui.theme.TinoSurface
import com.tino.app.ui.theme.TinoIllustrationTokens
import com.tino.app.domain.agent.TinoPresenceMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TinoNavDestination { Hoje, Produtos, Fiado, Mais }

class TinoScrollTelemetry {
    var offsetPx by mutableStateOf(0)
}

val LocalTinoScrollTelemetry = staticCompositionLocalOf { TinoScrollTelemetry() }

enum class TinoStatus { Normal, Attention, Success, Error, Offline }

private fun TinoPresenceMode.label(): String = when (this) {
    TinoPresenceMode.IDLE -> "Estou aqui para ajudar"
    TinoPresenceMode.LISTENING -> "Pode falar, estou ouvindo"
    TinoPresenceMode.THINKING,
    TinoPresenceMode.RESOLVING -> "Estou organizando isso"
    TinoPresenceMode.WAITING_FOR_USER -> "Só preciso confirmar uma coisa"
    TinoPresenceMode.COMPLETED -> "Pronto, resolvido"
    TinoPresenceMode.ERROR -> "Vamos tentar de novo"
}


@Composable
fun TinoMascotPresence(
    mode: TinoPresenceMode = TinoPresenceMode.IDLE,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null && mode == TinoPresenceMode.IDLE) {
        modifier.tinoClickable(onClick = onClick)
    } else {
        modifier
    }
    Card(
        modifier = clickableModifier.fillMaxWidth(),
        shape = TinoShapes.large,
        colors = CardDefaults.cardColors(containerColor = TinoGreenTint),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoElevation.subtle),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TinoSize.mascotPresenceMinHeight).padding(horizontal = TinoSpacing.sm, vertical = TinoSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            TinoMascot(
                state = TinoMascotState.fromPresence(mode),
                size = TinoMascotSize.Medium,
                placement = TinoMascotPlacement.CardSide,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xxs)) {
                Text("TINO", color = TinoGreenDark, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(mode.label(), color = TinoInk, style = MaterialTheme.typography.bodyMedium)
            }
            if (mode == TinoPresenceMode.IDLE) {
                Icon(TinoIcons.Voice, contentDescription = "Falar com o TINO", tint = TinoGreen, modifier = Modifier.size(TinoSize.iconNormal))
            }
        }
    }
}

@Composable
fun TinoMascotFab(
    mode: TinoPresenceMode = TinoPresenceMode.IDLE,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    mascotSize: Dp = TinoSize.mascotFab,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(mascotSize)
            .wrapContentSize(unbounded = true, align = Alignment.Center),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(mascotSize)
                .semantics {
                    contentDescription = when (mode) {
                        TinoPresenceMode.LISTENING -> "Parar de ouvir"
                        TinoPresenceMode.THINKING,
                        TinoPresenceMode.RESOLVING,
                        -> "TINO está consultando"
                        TinoPresenceMode.WAITING_FOR_USER -> "TINO espera uma confirmação"
                        else -> "O que o TINO pode fazer aqui"
                    }
                }
                .tinoClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            TinoMascot(
                state = TinoMascotState.fromPresence(mode),
                size = if (mascotSize <= TinoSize.mascotInline) {
                    TinoMascotSize.Small
                } else {
                    TinoMascotSize.Medium
                },
                placement = TinoMascotPlacement.Default,
            )
        }
    }
}


@Composable
fun TinoMascotInlineLabel(
    label: String = "TINO",
    mode: TinoPresenceMode = TinoPresenceMode.IDLE,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TinoSize.inputHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
    ) {
        Text(
            label,
            color = TinoMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        TinoMascotFab(
            mode = mode,
            mascotSize = TinoSize.mascotInline,
            onClick = onClick,
        )
    }
}

@Composable
fun TinoSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f).tinoOccupiedBounds("section-header:" + title),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onAction != null) {
            TinoTextAction(
                label = actionLabel,
                onClick = onAction,
                modifier = Modifier.tinoInteractiveBounds("section-action:" + actionLabel),
                color = TinoGreen,
            )
        }
    }
}

@Composable
fun TinoSectionLabel(title: String) {
    Text(
        title,
        modifier = Modifier.tinoOccupiedBounds("section-label:" + title),
        color = TinoMuted,
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun TinoWelcomeHeader(
    greeting: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(end = TinoSize.iconButton + TinoSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
    ) {
        Text(greeting, style = MaterialTheme.typography.headlineSmall, color = TinoInk)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TinoMuted)
    }
}

data class TinoGettingStartedPage(
    val title: String,
    val message: String,
    val icon: ImageVector,
    val actionLabel: String? = null,
    val showMascot: Boolean = false,
)

@Composable
fun TinoGettingStartedCarousel(
    pages: List<TinoGettingStartedPage>,
    onAction: (TinoGettingStartedPage) -> Unit,
    eyebrow: String = "COMEÇAR",
    compact: Boolean = false,
) {
    if (pages.isEmpty()) return
    var activeIndex by remember { mutableStateOf(0) }
    val pageKeys = pages.map { it.title }
    LaunchedEffect(pageKeys) {
        activeIndex = 0
        while (isActive) {
            delay(4_200)
            activeIndex = (activeIndex + 1) % pages.size
        }
    }
    val page = pages[activeIndex.coerceIn(pages.indices)]
    TinoCardSurface(
        status = TinoCardStatus.SUCCESS,
        description = "${page.title}. ${page.message}",
        contentPadding = if (compact) {
            PaddingValues(horizontal = TinoSpacing.md, vertical = TinoSpacing.sm)
        } else {
            PaddingValues(TinoSpacing.lg)
        },
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                (slideInHorizontally { width -> width / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { width -> -width / 4 } + fadeOut())
            },
            label = "getting-started-carousel",
        ) { visiblePage ->
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) TinoSpacing.xs else TinoSpacing.sm)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) TinoSpacing.xxs else TinoSpacing.xs)) {
                        Text(eyebrow, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, color = TinoGreenDark, fontWeight = FontWeight.Bold)
                        Text(visiblePage.title, style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge, color = TinoInk)
                        Text(visiblePage.message, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium, color = TinoMuted)
                    }
                    if (visiblePage.showMascot) {
                        TinoMascot(
                            state = TinoMascotState.Idle,
                            size = TinoMascotSize.Small,
                            placement = TinoMascotPlacement.Inline,
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(TinoSize.iconLarge),
                            shape = CircleShape,
                            color = TinoGreenLight,
                            contentColor = TinoGreen,
                        ) {
                            Icon(visiblePage.icon, contentDescription = null, modifier = Modifier.padding(TinoSpacing.sm))
                        }
                    }
                }
                visiblePage.actionLabel?.let { label ->
                    if (compact) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TinoProgressDots(
                                activeIndex = activeIndex,
                                total = pages.size,
                                modifier = Modifier.weight(1f),
                            )
                            TinoTextAction(
                                label = label,
                                onClick = { onAction(visiblePage) },
                                modifier = Modifier.tinoInteractiveBounds("getting-started-action:" + visiblePage.title),
                                color = TinoGreenDark,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        TinoPrimaryButton(
                            label = label,
                            onClick = { onAction(visiblePage) },
                            modifier = Modifier,
                            compact = false,
                        )
                    }
                } ?: if (!compact) {
                    Spacer(Modifier.height(TinoSize.buttonHeight))
                } else {
                    TinoProgressDots(activeIndex = activeIndex, total = pages.size)
                }
            }
        }
        if (!compact) {
            TinoProgressDots(activeIndex = activeIndex, total = pages.size)
        }
    }
}

@Composable
fun TinoGettingStartedCard(
    title: String,
    message: String,
    actionLabel: String,
    icon: ImageVector,
    progressIndex: Int,
    showProgress: Boolean = true,
    compact: Boolean = false,
    onAction: () -> Unit,
) {
    TinoCardSurface(
        status = TinoCardStatus.SUCCESS,
        description = "$title. $message",
        contentPadding = if (compact) {
            PaddingValues(horizontal = TinoSpacing.md, vertical = TinoSpacing.sm)
        } else {
            PaddingValues(TinoSpacing.lg)
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) TinoSpacing.xxs else TinoSpacing.xs)) {
                Text("COMEÇAR", style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, color = TinoGreenDark, fontWeight = FontWeight.Bold)
                Text(title, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, color = TinoInk)
                Text(message, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium, color = TinoMuted)
            }
            Surface(
                modifier = Modifier.size(TinoSize.iconLarge),
                shape = CircleShape,
                color = TinoGreenLight,
                contentColor = TinoGreen,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(TinoSpacing.sm))
            }
        }
        TinoPrimaryButton(
            label = actionLabel,
            onClick = onAction,
            modifier = Modifier,
            compact = compact,
        )
        if (showProgress) {
            TinoProgressDots(activeIndex = progressIndex)
        }
    }
}

@Composable
fun TinoContextualEmptyState(
    title: String,
    message: String,
    actionLabel: String,
    icon: ImageVector,
    onAction: () -> Unit,
) {
    TinoCardSurface(
        status = TinoCardStatus.SUCCESS,
        description = "$title. $message",
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TinoMascot(
                state = TinoMascotState.Observing,
                size = TinoMascotSize.Small,
                placement = TinoMascotPlacement.CardSide,
            )
            Spacer(Modifier.width(TinoSpacing.sm))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TinoInk)
                Text(message, style = MaterialTheme.typography.bodySmall, color = TinoMuted)
                TinoTextAction(
                    label = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.tinoInteractiveBounds("contextual-empty-action:$title"),
                    color = TinoGreenDark,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun TinoProgressDots(
    activeIndex: Int,
    total: Int = 3,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Progresso do começo: ${activeIndex + 1} de $total" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            Box(
                Modifier
                    .padding(horizontal = TinoSpacing.xs)
                    .size(if (index == activeIndex) TinoSize.progressActiveDot else TinoSize.progressInactiveDot)
                    .background(if (index == activeIndex) TinoGreen else TinoGreenBorder, CircleShape),
            )
        }
    }
}

@Composable
fun TinoHorizontalCarousel(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().tinoOccupiedBounds("horizontal-carousel"),
        horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        contentPadding = PaddingValues(end = TinoSpacing.md),
        content = content,
    )
}

@Composable
fun TinoQuickActionCard(
    icon: ImageVector,
    label: String,
    accent: Color,
    container: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.tinoInteractiveBounds("quick-action:" + label).tinoClickable(onClick = onClick),
        shape = TinoShapes.large,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoElevation.subtle),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = TinoSpacing.sm, vertical = TinoSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            Box(Modifier.size(TinoSize.quickActionIcon).background(container, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(TinoSize.iconNormal))
            }
            Text(label, color = TinoInk, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
fun TinoActionTile(
    icon: ImageVector,
    label: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.tinoOccupiedBounds("action-tile-visual:" + label).tinoInteractiveBounds("action-tile:" + label).tinoClickable(onClick = onClick),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoSize.cardElevation),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = TinoSize.actionTileMinHeight).padding(horizontal = TinoSpacing.xs, vertical = TinoSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
        ) {
            Box(Modifier.size(TinoSize.homeActionIcon).background(TinoGreenTint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconNormal))
            }
            Text(
                label,
                color = TinoInk,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                color = TinoMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TinoAgentInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    onVoice: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = TinoSize.minTouch),
        singleLine = true,
        maxLines = 1,
        placeholder = { Text("Pergunte ao TINO", color = TinoMuted, maxLines = 1) },
        leadingIcon = { Icon(TinoIcons.Search, contentDescription = null, tint = TinoGreen) },
        trailingIcon = onVoice?.let { voiceAction ->
            {
                IconButton(onClick = voiceAction) {
                    Icon(TinoIcons.Voice, contentDescription = "Falar com o TINO", tint = TinoGreen)
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        shape = TinoShapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = TinoSurface,
            unfocusedContainerColor = TinoSurface,
            focusedBorderColor = TinoGreenBorder,
            unfocusedBorderColor = TinoBorder,
            cursorColor = TinoGreen,
        ),
    )
}

@Composable
fun TinoLogo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.tino_mark),
            contentDescription = null,
            modifier = Modifier.size(TinoSize.iconCompact),
        )
        Text("TINO", color = TinoGreen, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun TinoTopBar(
    title: String,
    onBack: (() -> Unit)?,
    trailingContent: @Composable (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TinoSize.topBarHeight)
            .padding(end = TinoSize.topBarMascotClearance)
            .tinoMascotRow("top-bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TinoIconButton(TinoIcons.Back, "Voltar", onBack)
        } else {
            Spacer(Modifier.width(TinoSize.iconButton))
        }
        Text(
            title,
            modifier = Modifier.weight(1f).tinoOccupiedBounds("top-bar-title:" + title),
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailingContent?.invoke()
    }
}

@Composable
fun TinoTopBar(title: String, onBack: () -> Unit) {
    TinoTopBar(title = title, onBack = onBack, trailingContent = null)
}

@Composable
fun TinoTopBar(title: String) {
    TinoTopBar(title = title, onBack = null, trailingContent = null)
}

enum class TinoHeaderStyle { Inventory, Ledger, Directory, Menu, Form }

/** Contextual header used to give each work area its own visual rhythm. */
@Composable
fun TinoContextHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    style: TinoHeaderStyle,
    onBack: (() -> Unit)? = null,
) {
    val iconColor = when (style) {
        TinoHeaderStyle.Inventory -> TinoGreen
        TinoHeaderStyle.Ledger -> TinoPurple
        TinoHeaderStyle.Directory -> TinoBlue
        TinoHeaderStyle.Menu -> TinoMuted
        TinoHeaderStyle.Form -> TinoOrange
    }
    val iconContainer = when (style) {
        TinoHeaderStyle.Inventory -> TinoGreenTint
        TinoHeaderStyle.Ledger -> TinoPurpleContainer
        TinoHeaderStyle.Directory -> TinoBlue.copy(alpha = 0.12f)
        TinoHeaderStyle.Menu -> TinoPaper
        TinoHeaderStyle.Form -> TinoAmberContainer
    }
    Column(
        Modifier.fillMaxWidth().tinoMascotRow("context-header-$style"),
        verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = TinoSize.topBarHeight)
                .padding(end = TinoSize.iconButton + TinoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let {
                TinoIconButton(TinoIcons.Back, "Voltar", it)
                Spacer(Modifier.width(TinoSpacing.xs))
            }
            Surface(
                modifier = Modifier.size(TinoSize.cardIcon),
                shape = CircleShape,
                color = iconContainer,
                contentColor = iconColor,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(TinoSpacing.sm))
            }
            Spacer(Modifier.width(TinoSpacing.sm))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = TinoInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TinoMuted, modifier = Modifier.padding(start = if (onBack != null) TinoSize.iconButton + TinoSpacing.xs else TinoSize.cardIcon + TinoSpacing.sm))
    }
}

/** The add action is a navigation slot, not a floating action button. */
@Composable
fun TinoContextualCreateButton(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .heightIn(min = TinoSize.bottomNavigationHeight)
            .tinoInteractiveBounds("bottom-navigation-create")
            .semantics { contentDescription = "Adicionar dados neste contexto" },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(TinoSize.iconButton)
                .tinoClickable(enabled = onClick != null, role = Role.Button, onClick = onClick),
            shape = CircleShape,
            color = TinoGreen,
            contentColor = TinoSurface,
            shadowElevation = TinoElevation.floating,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(TinoIcons.Add, contentDescription = null)
            }
        }
    }
}

@Composable
fun TinoBottomNavigation(
    current: TinoNavDestination,
    visibleDestinations: Set<TinoNavDestination> = TinoNavDestination.values().toSet(),
    stockAttentionCount: Int = 0,
    creditAttentionCount: Int = 0,
    onQuickCreate: (() -> Unit)? = null,
    onNavigate: (TinoNavDestination) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(TinoPaper)
            .padding(horizontal = TinoSpacing.sm, vertical = TinoSpacing.sm)
            .navigationBarsPadding(),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = TinoShapes.large,
            colors = CardDefaults.cardColors(containerColor = TinoSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = TinoSize.navigationElevation),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TinoSpacing.xs, vertical = TinoSpacing.sm)
                    .heightIn(min = TinoSize.bottomNavigationHeight),
            ) {
                if (TinoNavDestination.Hoje in visibleDestinations) {
                    TinoNavigationItem(TinoNavDestination.Hoje, "Hoje", TinoIcons.Home, current, onNavigate)
                } else {
                    Spacer(Modifier.weight(1f).heightIn(min = TinoSize.bottomNavigationHeight))
                }
                if (TinoNavDestination.Produtos in visibleDestinations) {
                    TinoNavigationItem(TinoNavDestination.Produtos, "Estoque", TinoIcons.Products, current, onNavigate, stockAttentionCount, TinoAmber)
                } else {
                    Spacer(Modifier.weight(1f).heightIn(min = TinoSize.bottomNavigationHeight))
                }
                TinoContextualCreateButton(
                    onClick = onQuickCreate,
                    modifier = Modifier.weight(1f),
                )
                if (TinoNavDestination.Fiado in visibleDestinations) {
                    TinoNavigationItem(TinoNavDestination.Fiado, "Caderneta", TinoIcons.Credit, current, onNavigate, creditAttentionCount, TinoRed)
                } else {
                    Spacer(Modifier.weight(1f).heightIn(min = TinoSize.bottomNavigationHeight))
                }
                if (TinoNavDestination.Mais in visibleDestinations) {
                    TinoNavigationItem(TinoNavDestination.Mais, "Mais", TinoIcons.More, current, onNavigate)
                } else {
                    Spacer(Modifier.weight(1f).heightIn(min = TinoSize.bottomNavigationHeight))
                }
            }
        }
    }
}

@Composable
private fun RowScope.TinoNavigationItem(
    destination: TinoNavDestination,
    label: String,
    icon: ImageVector,
    current: TinoNavDestination,
    onNavigate: (TinoNavDestination) -> Unit,
    badgeCount: Int = 0,
    badgeColor: Color = TinoRed,
) {
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = TinoSize.bottomNavigationHeight)
            .tinoClickable { onNavigate(destination) }
            .padding(horizontal = TinoSpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val selected = current == destination
        val reduceMotion = LocalTinoReduceMotion.current
        val iconScale by animateFloatAsState(
            targetValue = if (selected) 1.08f else 1f,
            animationSpec = TinoMotion.emphasis(reduceMotion),
            label = "nav-icon-scale",
        )
        Box(
            Modifier.size(TinoSize.navigationItemIconBox),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(TinoSize.navigationSelectedIcon)
                    .background(if (selected) TinoGreenLight else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) TinoGreen else TinoMuted,
                    modifier = Modifier.size(TinoSize.iconNormal).graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                )
            }
            if (badgeCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(TinoSize.navigationBadge)
                        .background(badgeColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            label,
            color = if (selected) TinoGreen else TinoMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun TinoPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val buttonEnabled = enabled && !loading
    Button(
        onClick = onClick,
        enabled = buttonEnabled,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) TinoSize.minTouch else TinoSize.buttonHeight)
            .tinoPressScale(interactionSource, buttonEnabled)
            .tinoInteractiveBounds("primary-button:" + label),
        shape = TinoShapes.small,
        contentPadding = PaddingValues(horizontal = TinoSpacing.sm, vertical = TinoSpacing.xs),
        colors = ButtonDefaults.buttonColors(containerColor = TinoGreen),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = TinoSize.buttonElevation),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(TinoSize.iconSmall),
                color = TinoSurface,
                strokeWidth = TinoSize.progressStrokeWidth,
            )
            Spacer(Modifier.width(TinoSpacing.sm))
        }
        Text(
            if (loading) "Processando…" else label,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TinoPrimaryButton(label: String, onClick: () -> Unit) {
    TinoPrimaryButton(label, onClick, Modifier)
}

@Composable
fun TinoSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val buttonEnabled = enabled && !loading
    OutlinedButton(
        onClick = onClick,
        enabled = buttonEnabled,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TinoSize.buttonHeight)
            .tinoPressScale(interactionSource, buttonEnabled)
            .tinoInteractiveBounds("secondary-button:" + label),
        shape = TinoShapes.small,
        contentPadding = PaddingValues(horizontal = TinoSpacing.sm, vertical = TinoSpacing.xs),
        border = ButtonDefaults.outlinedButtonBorder(enabled = buttonEnabled).copy(width = TinoSize.cardBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TinoGreen),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(TinoSize.iconSmall),
                color = TinoGreen,
                strokeWidth = TinoSize.progressStrokeWidth,
            )
            Spacer(Modifier.width(TinoSpacing.sm))
        }
        Text(
            if (loading) "Processando…" else label,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Compact secondary action shared by dense cards, headers and overlays. */
@Composable
fun TinoTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = TinoGreen,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = TinoSize.minTouch),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = TinoSpacing.sm, vertical = TinoSpacing.xs),
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Text(
            label,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TinoSecondaryButton(label: String, onClick: () -> Unit) {
    TinoSecondaryButton(label, onClick, Modifier)
}

@Composable
fun TinoIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    TinoIconButton(icon, contentDescription, true, onClick)
}

@Composable
fun TinoIconButton(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(TinoSize.iconButton)
            .tinoPressScale(interactionSource, enabled)
            .tinoInteractiveBounds("icon-button:" + contentDescription),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) TinoInk else TinoMuted)
    }
}

@Composable
fun TinoCard(
    modifier: Modifier = Modifier,
    containerColor: Color = TinoSurface,
    elevation: Dp = TinoSize.cardElevation,
    contentPadding: PaddingValues = PaddingValues(TinoSpacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().tinoAnimateContentSize().tinoOccupiedBounds("tino-card"),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm), content = content)
    }
}

@Composable
fun TinoVoiceCard(
    title: String = "Falar com o TINO",
    message: String = "Diga o que você precisa",
    showForward: Boolean = true,
    emphasized: Boolean = false,
    showVoiceIcon: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().tinoInteractiveBounds("voice-card:" + title).tinoClickable(onClick = onClick),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (emphasized) TinoGreenLight else TinoGreenTint),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoSize.cardElevation),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(TinoSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md),
        ) {
            // The Home hero is idle here; keep the lighter halo visible against its green card.
            if (showVoiceIcon) {
                TinoVoiceIcon(active = false)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(title, color = if (emphasized) TinoGreenDark else TinoInk, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(message, color = if (emphasized) TinoGreen else TinoMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }
            if (showForward) {
                Icon(TinoIcons.Forward, contentDescription = "Abrir voz", tint = TinoGreen, modifier = Modifier.size(TinoSize.iconNormal))
            }
        }
    }
}

enum class TinoVoiceFabState {
    Idle,
    Listening,
    Processing,
    Waiting,
    Success,
    Error,
}

/** Shared microphone treatment used by the Home hero and every voice FAB. */
@Composable
fun TinoVoiceIcon(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    contentDescription: String = "Falar com o TINO",
) {
    Box(
        modifier.size(TinoSize.voiceIcon).background(if (active) TinoGreenLight else TinoGreenTint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(TinoSize.voiceButton).background(TinoGreen, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                TinoIcons.Voice,
                contentDescription = contentDescription,
                tint = TinoSurface,
                modifier = Modifier.size(TinoSize.iconProminent),
            )
        }
    }
}

@Composable
fun TinoVoiceFab(
    state: TinoVoiceFabState = TinoVoiceFabState.Idle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var showSuccess by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state == TinoVoiceFabState.Success) {
            showSuccess = true
            delay(1_200)
            showSuccess = false
        } else {
            showSuccess = false
        }
    }
    val visualState = if (state == TinoVoiceFabState.Success && showSuccess) {
        TinoVoiceFabState.Success
    } else if (state == TinoVoiceFabState.Success) {
        TinoVoiceFabState.Idle
    } else {
        state
    }
    val isListening = visualState == TinoVoiceFabState.Listening
    val isVoiceOpen = visualState == TinoVoiceFabState.Listening || visualState == TinoVoiceFabState.Processing
    val reduceMotion = LocalTinoReduceMotion.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val oppositeCornerTravel = (screenWidth - (TinoSpacing.screen * 2) - TinoSize.voiceIcon).coerceAtLeast(0.dp)
    val horizontalOffset by animateDpAsState(
        targetValue = if (isVoiceOpen) -oppositeCornerTravel else 0.dp,
        animationSpec = TinoMotion.spatial(reduceMotion),
        label = "voice-fab-parallax",
    )
    val scale by animateFloatAsState(
        targetValue = if (isVoiceOpen) 1.04f else 1f,
        animationSpec = TinoMotion.emphasis(reduceMotion),
        label = "voice-fab-scale",
    )
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(isListening, reduceMotion) {
        if (reduceMotion || !isListening) {
            pulse.snapTo(1f)
            return@LaunchedEffect
        }
        var expand = true
        while (isActive) {
            pulse.animateTo(if (expand) 1.1f else 1f, TinoMotion.emphasis())
            expand = !expand
        }
    }
    Box(
        modifier = modifier
            .size(TinoSize.voiceIcon)
            .offset {
                androidx.compose.ui.unit.IntOffset(horizontalOffset.roundToPx(), 0)
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(TinoSize.voiceIcon)
                .background(if (isListening) TinoGreenLight else TinoGreenTint, CircleShape),
        )
        if (isListening) {
            Box(
                Modifier
                    .size(TinoSize.voiceIcon)
                    .graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                        alpha = 0.42f
                    }
                    .background(TinoGreenLight, CircleShape),
            )
        }
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(TinoSize.voiceButton),
            shape = CircleShape,
            containerColor = TinoGreen,
            contentColor = TinoSurface,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = TinoElevation.subtle,
                pressedElevation = TinoElevation.buttonPressed,
            ),
        ) {
            when (visualState) {
                TinoVoiceFabState.Idle,
                TinoVoiceFabState.Listening -> Icon(TinoIcons.Voice, contentDescription = "Falar com o TINO", modifier = Modifier.size(TinoSize.iconProminent))
                TinoVoiceFabState.Processing -> CircularProgressIndicator(
                    modifier = Modifier.size(TinoSize.iconNormal),
                    color = TinoSurface,
                strokeWidth = TinoSize.voiceProgressStrokeWidth,
                )
                TinoVoiceFabState.Waiting -> Icon(TinoIcons.Warning, contentDescription = "Aguardando sua confirmação", modifier = Modifier.size(TinoSize.iconProminent))
                TinoVoiceFabState.Success -> Icon(TinoIcons.Success, contentDescription = "Dados preenchidos", modifier = Modifier.size(TinoSize.iconProminent))
                TinoVoiceFabState.Error -> Icon(TinoIcons.Error, contentDescription = "Tentar novamente", modifier = Modifier.size(TinoSize.iconProminent))
            }
        }
    }
}

@Composable
fun TinoMetricCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = TinoGreen,
    accentContainer: Color = TinoGreenLight,
) {
    Card(
        modifier = modifier.heightIn(min = TinoSize.metricCardHeight).tinoOccupiedBounds("metric-card:" + value + ":" + label),
        shape = TinoShapes.small,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoSize.cardElevation),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = TinoSpacing.sm, vertical = TinoSpacing.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Box(Modifier.size(TinoSize.iconProminent).background(accentContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(TinoSize.iconNormal))
            }
            Text(value, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
            Text(label, color = TinoMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
fun TinoInsightCard(title: String, message: String, onView: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().tinoOccupiedBounds("insight-card:" + title),
        shape = TinoShapes.small,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoElevation.subtle),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = TinoSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(TinoSize.statusAccentBar).height(TinoSize.successIcon).background(TinoAmber, TinoShapes.small))
            Icon(TinoIcons.Warning, contentDescription = "Atenção", tint = TinoAmber, modifier = Modifier.size(TinoSize.iconNormal))
            Column(Modifier.weight(1f).padding(horizontal = TinoSpacing.sm), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(title, color = TinoInk, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Text(message, color = TinoInk, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            TinoTextAction(
                label = "VER",
                onClick = onView,
                color = TinoGreen,
            )
        }
    }
}

@Composable
fun TinoProductRow(
    product: ProductSummary,
    onClick: () -> Unit,
    motionBoundsEnabled: Boolean = true,
) {
    TinoListCard(
        Modifier
            .then(
                if (motionBoundsEnabled) {
                    Modifier
                        .tinoSharedBounds(TinoSharedKeys.product(product.id))
                        .tinoOccupiedBounds("product-row-visual:" + product.name)
                        .tinoInteractiveBounds("product-row:" + product.name)
                } else {
                    Modifier
                },
            )
            .tinoClickable(onClick = onClick),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Text(product.name, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (product.stockTracked) "${product.stockQuantityExact} ${product.unit}" else "Feito sob demanda",
                color = TinoMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Text(
                when {
                    !product.stockTracked -> "Sob demanda"
                    product.stockQuantity == 0 -> "Sem estoque"
                    product.stockQuantity <= 6 -> "Estoque baixo"
                    else -> "Disponível"
                },
                color = when {
                    !product.stockTracked -> TinoGreen
                    product.stockQuantity == 0 -> TinoRed
                    product.stockQuantity <= 6 -> TinoAmber
                    else -> TinoGreen
                },
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Icon(TinoIcons.Forward, contentDescription = "Abrir ${product.name}", tint = TinoMuted, modifier = Modifier.size(TinoSize.iconNormal))
        }
    }
}

@Composable
fun TinoSaleProductRow(product: ProductSummary, onAdd: () -> Unit, enabled: Boolean = !product.stockTracked || product.stockQuantity > 0) {
    TinoListRow(
        icon = TinoIcons.Products,
        title = product.name,
        supportingText = if (!product.stockTracked) {
            "${formatCents(product.priceCents.toLong())} · Feito sob demanda"
        } else if (product.stockQuantity > 0) {
            "${formatCents(product.priceCents.toLong())} · ${product.stockQuantityExact} disponíveis"
        } else {
            "Sem estoque"
        },
        onClick = onAdd,
        enabled = enabled,
    )
}

@Composable
fun TinoCustomerRow(customer: CustomerBalance, onClick: () -> Unit) {
    TinoListCard(
        Modifier
            .tinoSharedBounds(TinoSharedKeys.customer(customer.id))
            .tinoOccupiedBounds("customer-row-visual:" + customer.name)
            .tinoInteractiveBounds("customer-row:" + customer.name)
            .tinoClickable(onClick = onClick),
    ) {
        Box(Modifier.size(TinoSize.avatar).background(TinoGreenTint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(TinoIcons.Person, contentDescription = null, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconNormal))
        }
        Spacer(Modifier.width(TinoSpacing.sm))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Text(customer.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                if (customer.balanceCents > 0) "Em aberto" else "Sem saldo",
                color = if (customer.balanceCents > 0) TinoGreen else TinoMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
        Text(formatCents(customer.balanceCents), color = if (customer.balanceCents > 0) TinoRed else TinoGreen, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Icon(TinoIcons.Forward, contentDescription = "Abrir ${customer.name}", tint = TinoMuted, modifier = Modifier.size(TinoSize.iconNormal))
    }
}

@Composable
fun TinoSupplierRow(name: String, detail: String, onClick: (() -> Unit)? = null, sharedKey: String? = null) {
    TinoListCard(
        onClick?.let {
            Modifier
                .then(if (sharedKey != null) Modifier.tinoSharedBounds(TinoSharedKeys.supplier(sharedKey)) else Modifier)
                .tinoOccupiedBounds("supplier-row-visual:" + name)
                .tinoInteractiveBounds("supplier-row:" + name)
                .tinoClickable(onClick = it)
        } ?: Modifier,
    ) {
        Icon(TinoIcons.Supplier, contentDescription = null, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconLarge))
        Spacer(Modifier.width(TinoSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(detail, color = TinoMuted)
        }
    }
}

@Composable
fun TinoOrderRow(status: String, customer: String, total: String, onClick: () -> Unit, sharedKey: String? = null) {
    TinoListCard(
        Modifier
            .then(if (sharedKey != null) Modifier.tinoSharedBounds(TinoSharedKeys.order(sharedKey)) else Modifier)
            .tinoOccupiedBounds("order-row-visual:" + customer)
            .tinoInteractiveBounds("order-row:" + customer)
            .tinoClickable(onClick = onClick),
    ) {
        TinoStatusIcon(TinoStatus.Normal, "Status do pedido")
        Spacer(Modifier.width(TinoSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(status, color = TinoGreen, fontWeight = FontWeight.Bold)
            Text(customer, fontWeight = FontWeight.SemiBold)
        }
        Text(total, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TinoListCard(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tinoAnimateContentSize()
            .tinoOccupiedBounds("tino-list-row"),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TinoSize.listRowHeight).padding(horizontal = TinoSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        HorizontalDivider(color = TinoBorder.copy(alpha = 0.55f))
    }
}

@Composable
fun TinoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
    labelAbove: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val field = @Composable {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth().heightIn(min = TinoSize.inputHeight).tinoOccupiedBounds("tino-field:" + label).tinoInteractiveBounds("tino-field:" + label),
            label = if (labelAbove) null else { { Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium) } },
            placeholder = placeholder?.let { text -> { Text(text, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge) } },
            leadingIcon = leadingIcon,
            prefix = prefix,
            keyboardOptions = keyboardOptions,
            singleLine = true,
            shape = TinoShapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TinoGreenTint,
                unfocusedContainerColor = TinoSurface,
                focusedBorderColor = TinoGreen,
                unfocusedBorderColor = TinoBorder,
                focusedLabelColor = TinoGreen,
                cursorColor = TinoGreen,
            ),
        )
    }
    if (labelAbove) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Text(label, color = TinoMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            field()
        }
    } else {
        field()
    }
}

@Composable
fun TinoSearchField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    TinoTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        leadingIcon = { Icon(TinoIcons.Search, contentDescription = "Pesquisar", modifier = Modifier.size(TinoSize.iconNormal)) },
    )
}

@Composable
fun TinoMoneyField(value: String, onValueChange: (String) -> Unit, label: String = "Valor") {
    TinoTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        prefix = { Text("R$ ") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
fun TinoQuantitySelector(quantity: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
        TinoIconButton(TinoIcons.Remove, "Diminuir quantidade", onDecrease)
        Text(quantity.toString(), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TinoIconButton(TinoIcons.Add, "Aumentar quantidade", onIncrease)
    }
}

@Composable
fun TinoConfirmationSheet(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    TinoCard {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text(message, color = TinoMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
            TinoSecondaryButton("CANCELAR", onDismiss, Modifier.weight(1f))
            TinoPrimaryButton("CONFIRMAR", onConfirm, Modifier.weight(1f))
        }
    }
}

@Composable
fun TinoStatusIcon(status: TinoStatus, contentDescription: String) {
    val (icon, tint) = when (status) {
        TinoStatus.Normal -> TinoIcons.Success to TinoGreen
        TinoStatus.Attention -> TinoIcons.Warning to TinoAmber
        TinoStatus.Success -> TinoIcons.Success to TinoGreen
        TinoStatus.Error -> TinoIcons.Error to TinoRed
        TinoStatus.Offline -> TinoIcons.Offline to TinoMuted
    }
    Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(TinoSize.iconNormal))
}

@Composable
fun TinoSyncIndicator(offline: Boolean = false, compact: Boolean = false, onClick: (() -> Unit)? = null) {
    val label = if (offline) "Sem internet" else "Dados salvos"
    val status = if (offline) TinoStatus.Offline else TinoStatus.Success
    Row(
        modifier = Modifier
            .heightIn(min = TinoSize.minTouch)
            .widthIn(min = TinoSize.minTouch)
            .then(
                if (onClick != null) {
                    Modifier.tinoInteractiveBounds("sync-indicator").tinoClickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
    ) {
        if (compact) {
            Icon(TinoIcons.Synced, contentDescription = label, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconCompact))
        } else {
            TinoStatusIcon(status, label)
            Text(label, color = if (offline) TinoMuted else TinoGreen, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun TinoMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    detail: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TinoSize.menuRowHeight).tinoInteractiveBounds("menu-row:" + label).tinoClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md),
    ) {
        Icon(icon, contentDescription = label, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconNormal))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, color = TinoMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 1) }
        }
        Icon(TinoIcons.Forward, contentDescription = "Abrir $label", tint = TinoMuted, modifier = Modifier.size(TinoSize.iconNormal))
    }
}

@Composable
fun TinoListRow(
    icon: ImageVector,
    title: String,
    supportingText: String? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = TinoSize.listRowHeight)
        .tinoInteractiveBounds("list-row:" + title)
        .tinoClickable(enabled = enabled && onClick != null, onClick = onClick ?: {})
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = title,
            tint = if (enabled) TinoGreen else TinoMuted,
            modifier = Modifier.size(TinoSize.iconNormal),
        )
        Spacer(Modifier.width(TinoSpacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Text(
                title,
                color = if (enabled) TinoInk else TinoMuted,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            supportingText?.let {
                Text(it, color = TinoMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailingText?.let {
            Text(it, color = TinoMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (onClick != null) {
            Icon(TinoIcons.Forward, contentDescription = "Abrir $title", tint = TinoMuted)
        }
    }
    HorizontalDivider(color = TinoBorder.copy(alpha = 0.55f))
}

@Composable
fun TinoMenuCard(
    icon: ImageVector,
    label: String,
    detail: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth().tinoOccupiedBounds("menu-card-visual:" + label).tinoInteractiveBounds("menu-card:" + label).tinoClickable(onClick = onClick),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (highlighted) TinoGreenTint else TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoSize.cardElevation),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TinoSize.menuRowHeight).padding(horizontal = TinoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md),
        ) {
            Box(Modifier.size(TinoSize.cardIcon).background(TinoGreenLight, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconNormal))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, color = TinoMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Icon(TinoIcons.Forward, contentDescription = "Abrir $label", tint = TinoMuted, modifier = Modifier.size(TinoSize.iconNormal))
        }
    }
}

@Composable
fun TinoOfflineBanner(message: String = "Você pode continuar trabalhando sem internet.") {
    Row(
        Modifier.fillMaxWidth().padding(TinoSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md),
    ) {
        TinoIllustration(
            state = TinoIllustrationState.OFFLINE,
            modifier = Modifier.size(TinoIllustrationTokens.Small),
            contentDescription = null,
        )
        Column {
            Text("Sem internet", fontWeight = FontWeight.Bold)
            Text(message, color = TinoMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun TinoEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    illustrationState: TinoIllustrationState? = null,
) {
    Column(
        Modifier.fillMaxWidth().tinoAnimateContentSize().tinoOccupiedBounds("empty-state:" + title).padding(vertical = TinoSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TinoSpacing.md),
    ) {
        if (illustrationState != null) {
            TinoIllustration(
                state = illustrationState,
                modifier = Modifier.size(TinoIllustrationTokens.Medium),
                contentDescription = "$title. $message",
            )
        } else {
            Icon(icon, contentDescription = null, tint = TinoMuted, modifier = Modifier.size(TinoSize.emptyStateIcon))
        }
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text(message, color = TinoMuted)
        if (actionLabel != null && onAction != null) {
            TinoPrimaryButton(
                label = actionLabel,
                onClick = onAction,
                modifier = Modifier.widthIn(max = TinoSize.emptyContentMaxWidth),
            )
        }
    }
}

@Composable
fun TinoLoadingState(
    icon: ImageVector,
    title: String,
    message: String,
    illustrationState: TinoIllustrationState? = null,
) {
    TinoCardSurface(description = "Carregando") {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md),
        ) {
            Box(
                Modifier.size(TinoSize.iconLarge).background(TinoGreenTint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (illustrationState != null) {
                    TinoIllustration(
                        state = illustrationState,
                        modifier = Modifier.size(TinoIllustrationTokens.Medium),
                        contentDescription = null,
                    )
                }
                CircularProgressIndicator(
                    modifier = Modifier.size(TinoSize.iconNormal),
                    color = TinoGreen,
                    strokeWidth = TinoSize.progressStrokeWidth,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(message, color = TinoMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun TinoStatusBadge(label: String, status: TinoStatus) {
    val background = when (status) {
        TinoStatus.Attention -> TinoAmberContainer
        TinoStatus.Error -> TinoRedContainer
        else -> TinoGreenLight
    }
    val tint = when (status) {
        TinoStatus.Attention -> TinoAmber
        TinoStatus.Error -> TinoRed
        else -> TinoGreen
    }
    Row(
        Modifier
            .background(background, TinoShapes.small)
            .padding(horizontal = TinoSpacing.md, vertical = TinoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
    ) {
        TinoStatusIcon(status, label)
        Text(label, color = tint, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TinoFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .heightIn(min = TinoSize.minTouch)
            .tinoOccupiedBounds("filter-chip-visual:" + label)
            .tinoInteractiveBounds("filter-chip:" + label)
            .tinoClickable(onClick = onClick),
        shape = TinoShapes.large,
        color = if (selected) TinoGreenLight else TinoSurface,
        tonalElevation = if (selected) TinoElevation.subtle else TinoElevation.none,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = TinoSpacing.md, vertical = TinoSpacing.sm),
            color = if (selected) TinoGreenDark else TinoMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun formatCents(cents: Long): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0)
