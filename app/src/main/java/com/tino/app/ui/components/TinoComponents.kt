package com.tino.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.tino.app.R
import com.tino.app.core.database.CustomerBalance
import com.tino.app.core.database.ProductSummary
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.theme.TinoAmber
import com.tino.app.ui.theme.TinoAmberContainer
import com.tino.app.ui.theme.TinoBorder
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoGreenBorder
import com.tino.app.ui.theme.TinoGreenDark
import com.tino.app.ui.theme.TinoGreenLight
import com.tino.app.ui.theme.TinoGreenTint
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoPaper
import com.tino.app.ui.theme.TinoRed
import com.tino.app.ui.theme.TinoRedContainer
import com.tino.app.ui.theme.TinoShapes
import com.tino.app.ui.theme.TinoSize
import com.tino.app.ui.theme.TinoSpacing
import com.tino.app.ui.theme.TinoSurface
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay

enum class TinoNavDestination { Hoje, Produtos, Fiado, Mais }

enum class TinoStatus { Normal, Attention, Success, Error, Offline }

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
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = TinoGreen) }
        }
    }
}

@Composable
fun TinoSectionLabel(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth(),
        color = TinoMuted,
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun TinoHorizontalCarousel(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
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
        modifier = modifier.clickable(onClick = onClick),
        shape = TinoShapes.large,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = TinoSpacing.sm, vertical = TinoSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            Box(Modifier.size(42.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
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
        modifier = modifier.clickable(onClick = onClick),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoSize.cardElevation),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 78.dp).padding(horizontal = TinoSpacing.xs, vertical = TinoSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
        ) {
            Box(Modifier.size(40.dp).background(TinoGreenTint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconNormal))
            }
            Text(
                label,
                color = TinoInk,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        singleLine = true,
        maxLines = 1,
        placeholder = { Text("Pergunte ao TINO", color = TinoMuted, maxLines = 1) },
        leadingIcon = { Icon(TinoIcons.Search, contentDescription = null, tint = TinoGreen) },
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
        modifier = Modifier.fillMaxWidth().heightIn(min = TinoSize.topBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TinoIconButton(TinoIcons.Back, "Voltar", onBack)
        } else {
            Spacer(Modifier.width(TinoSize.iconButton))
        }
        Text(title, modifier = Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
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

@Composable
fun TinoBottomNavigation(
    current: TinoNavDestination,
    visibleDestinations: Set<TinoNavDestination> = TinoNavDestination.values().toSet(),
    stockAttentionCount: Int = 0,
    creditAttentionCount: Int = 0,
    onNavigate: (TinoNavDestination) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TinoPaper)
            .padding(horizontal = TinoSpacing.screen, vertical = TinoSpacing.sm)
            .navigationBarsPadding(),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = TinoShapes.large,
            colors = CardDefaults.cardColors(containerColor = TinoSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = TinoSize.navigationElevation),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = TinoSize.bottomNavigationHeight)) {
            if (TinoNavDestination.Hoje in visibleDestinations) TinoNavigationItem(TinoNavDestination.Hoje, "Hoje", TinoIcons.Home, current, onNavigate)
            if (TinoNavDestination.Produtos in visibleDestinations) TinoNavigationItem(TinoNavDestination.Produtos, "Estoque", TinoIcons.Products, current, onNavigate, stockAttentionCount, TinoAmber)
            if (TinoNavDestination.Fiado in visibleDestinations) TinoNavigationItem(TinoNavDestination.Fiado, "Caderneta", TinoIcons.Credit, current, onNavigate, creditAttentionCount, TinoRed)
            if (TinoNavDestination.Mais in visibleDestinations) TinoNavigationItem(TinoNavDestination.Mais, "Mais", TinoIcons.More, current, onNavigate)
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
        Modifier.weight(1f).heightIn(min = TinoSize.bottomNavigationHeight).clickable { onNavigate(destination) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val selected = current == destination
        Box(
            Modifier
                .background(if (selected) TinoGreenLight else Color.Transparent, TinoShapes.medium)
                .padding(horizontal = TinoSpacing.md, vertical = TinoSpacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(TinoSize.iconNormal), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = label, tint = if (selected) TinoGreen else TinoMuted, modifier = Modifier.size(TinoSize.iconNormal))
                    if (badgeCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 7.dp, y = (-5).dp)
                                .size(16.dp)
                                .background(badgeColor, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                                color = Color.White,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
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
                )
            }
        }
    }
}

@Composable
fun TinoPrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = TinoSize.buttonHeight),
        shape = TinoShapes.small,
        contentPadding = PaddingValues(horizontal = TinoSpacing.sm, vertical = TinoSpacing.xs),
        colors = ButtonDefaults.buttonColors(containerColor = TinoGreen),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = TinoSize.buttonElevation),
    ) {
        Text(
            label,
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
fun TinoSecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = TinoSize.buttonHeight),
        shape = TinoShapes.small,
        contentPadding = PaddingValues(horizontal = TinoSpacing.sm, vertical = TinoSpacing.xs),
        border = ButtonDefaults.outlinedButtonBorder(enabled = enabled).copy(width = 1.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TinoGreen),
    ) {
        Text(
            label,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
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
    IconButton(onClick = onClick, modifier = Modifier.size(TinoSize.iconButton), enabled = enabled) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) TinoInk else TinoMuted)
    }
}

@Composable
fun TinoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoSize.cardElevation),
    ) {
        Column(Modifier.padding(TinoSpacing.lg), verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm), content = content)
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
        Box(Modifier.size(52.dp).background(TinoGreen, CircleShape), contentAlignment = Alignment.Center) {
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
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val oppositeCornerTravel = (screenWidth - (TinoSpacing.screen * 2) - TinoSize.voiceIcon).coerceAtLeast(0.dp)
    val horizontalOffset by animateDpAsState(
        targetValue = if (isVoiceOpen) -oppositeCornerTravel else 0.dp,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "voice-fab-parallax",
    )
    val scale by animateFloatAsState(
        targetValue = if (isVoiceOpen) 1.04f else 1f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "voice-fab-scale",
    )
    val pulse = rememberInfiniteTransition(label = "voice-fab-pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "voice-fab-scale",
    )
    Box(
        modifier = modifier
            .size(TinoSize.voiceIcon)
            .offset(x = horizontalOffset)
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
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            containerColor = TinoGreen,
            contentColor = TinoSurface,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 1.dp,
                pressedElevation = 2.dp,
            ),
        ) {
            when (visualState) {
                TinoVoiceFabState.Idle,
                TinoVoiceFabState.Listening -> Icon(TinoIcons.Voice, contentDescription = "Falar com o TINO", modifier = Modifier.size(TinoSize.iconProminent))
                TinoVoiceFabState.Processing -> CircularProgressIndicator(
                    modifier = Modifier.size(TinoSize.iconNormal),
                    color = TinoSurface,
                    strokeWidth = 2.5.dp,
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
        modifier = modifier.heightIn(min = TinoSize.metricCardHeight),
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
        modifier = Modifier.fillMaxWidth(),
        shape = TinoShapes.small,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = TinoSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(TinoSize.successIcon).background(TinoAmber, TinoShapes.small))
            Icon(TinoIcons.Warning, contentDescription = "Atenção", tint = TinoAmber, modifier = Modifier.size(TinoSize.iconNormal))
            Column(Modifier.weight(1f).padding(horizontal = TinoSpacing.sm), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(title, color = TinoInk, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Text(message, color = TinoInk, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            TextButton(onClick = onView, modifier = Modifier.heightIn(min = TinoSize.minTouch), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = TinoSpacing.sm)) {
                Text("VER", color = TinoGreen, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun TinoProductRow(product: ProductSummary, onClick: () -> Unit) {
    TinoListCard(Modifier.clickable(onClick = onClick)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Text(product.name, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text("${product.stockQuantity} ${product.unit}", color = TinoMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Text(
                when {
                    product.stockQuantity == 0 -> "Sem estoque"
                    product.stockQuantity <= 6 -> "Estoque baixo"
                    else -> "Disponível"
                },
                color = when {
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
fun TinoSaleProductRow(product: ProductSummary, onAdd: () -> Unit, enabled: Boolean = product.stockQuantity > 0) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TinoShapes.small,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = TinoSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(product.name, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (product.stockQuantity > 0) "${formatCents(product.priceCents.toLong())} · ${product.stockQuantity} disponíveis" else "Sem estoque",
                    color = if (product.stockQuantity > 0) TinoMuted else TinoRed,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }
            TinoIconButton(TinoIcons.Add, "Adicionar ${product.name}", enabled, onAdd)
        }
    }
}

@Composable
fun TinoCustomerRow(customer: CustomerBalance, onClick: () -> Unit) {
    TinoListCard(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(40.dp).background(TinoGreenTint, CircleShape), contentAlignment = Alignment.Center) {
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
fun TinoSupplierRow(name: String, detail: String, onClick: (() -> Unit)? = null) {
    TinoListCard(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier) {
        Icon(TinoIcons.Supplier, contentDescription = null, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconLarge))
        Spacer(Modifier.width(TinoSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(detail, color = TinoMuted)
        }
    }
}

@Composable
fun TinoOrderRow(status: String, customer: String, total: String, onClick: () -> Unit) {
    TinoListCard(Modifier.clickable(onClick = onClick)) {
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TinoShapes.small,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TinoSize.listRowHeight).padding(horizontal = TinoSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
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
            modifier = modifier.fillMaxWidth().heightIn(min = TinoSize.inputHeight),
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
fun TinoMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TinoSize.menuRowHeight).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md),
    ) {
        Icon(icon, contentDescription = label, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconNormal))
        Text(label, modifier = Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        Icon(TinoIcons.Forward, contentDescription = "Abrir $label", tint = TinoMuted, modifier = Modifier.size(TinoSize.iconNormal))
    }
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = TinoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (highlighted) TinoGreenTint else TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoSize.cardElevation),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = TinoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md),
        ) {
            Box(Modifier.size(40.dp).background(TinoGreenLight, CircleShape), contentAlignment = Alignment.Center) {
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
        TinoStatusIcon(TinoStatus.Offline, "Sem internet")
        Column {
            Text("Sem internet", fontWeight = FontWeight.Bold)
            Text(message, color = TinoMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun TinoEmptyState(icon: ImageVector, title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = TinoSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TinoSpacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = TinoMuted, modifier = Modifier.size(TinoSize.emptyStateIcon))
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text(message, color = TinoMuted)
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
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
        modifier = Modifier.heightIn(min = TinoSize.minTouch).clickable(onClick = onClick),
        shape = TinoShapes.large,
        color = if (selected) TinoGreenLight else TinoSurface,
        tonalElevation = if (selected) 1.dp else 0.dp,
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
