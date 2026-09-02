package com.tino.app.ui.a2ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.tino.app.interfaceadapter.a2ui.A2uiMessage
import com.tino.app.interfaceadapter.a2ui.A2uiSurfacePolicy
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceSize
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceStage
import com.tino.app.ui.components.TinoIconButton
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.theme.LocalTinoReduceMotion
import com.tino.app.ui.theme.TinoElevation
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMotion
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoShapes
import com.tino.app.ui.theme.TinoSize
import com.tino.app.ui.theme.TinoSpacing
import com.tino.app.ui.theme.TinoSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Native TINO catalog chrome. The agent never supplies position, height,
 * curve or color; this host owns the rise-from-footer motion.
 */
@Composable
fun TinoA2UiBottomSurface(
    size: A2uiSurfaceSize,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrollContent: Boolean = true,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = LocalTinoReduceMotion.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(A2uiSurfacePolicy.initialStage(size)) }
    val rise = remember { Animatable(0f) }
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(size) {
        stage = A2uiSurfacePolicy.initialStage(size)
        val target = A2uiSurfacePolicy.fraction(stage, size)
        rise.snapTo(if (reduceMotion) target else 0f)
        contentVisible = false
        if (!reduceMotion) {
            rise.animateTo(target, TinoMotion.spatial())
            delay(80)
        }
        contentVisible = true
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Painel do TINO" },
    ) {
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val scrimAlpha = (0.32f * rise.value / 0.94f).coerceIn(0f, 0.32f)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        val sheetHeight = maxHeight * rise.value.coerceIn(0.18f, 0.96f)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .shadow(TinoElevation.surface, TinoShapes.sheet, clip = false)
                .clip(TinoShapes.sheet)
                .background(TinoSurface)
                .navigationBarsPadding()
                .pointerInput(size, maxHeightPx) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val current = rise.value
                                if (current < 0.22f) {
                                    onDismiss()
                                    return@launch
                                }
                                val next = nearestStage(current, size)
                                stage = next
                                rise.animateTo(
                                    A2uiSurfacePolicy.fraction(next, size),
                                    TinoMotion.spatial(),
                                )
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val delta = dragAmount / maxHeightPx
                        val next = (rise.value - delta).coerceIn(0.16f, 0.96f)
                        scope.launch { rise.snapTo(next) }
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = TinoSpacing.sm, bottom = TinoSpacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(TinoSize.surfaceHandleWidth)
                        .height(TinoSize.surfaceHandleHeight)
                        .clip(TinoShapes.full)
                        .background(TinoMuted.copy(alpha = 0.35f)),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TinoSpacing.screen),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xxs)) {
                    Text(title, color = TinoInk, fontWeight = FontWeight.SemiBold)
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = TinoMuted)
                    }
                }
                TinoIconButton(TinoIcons.Close, "Fechar painel do TINO", onDismiss)
            }
            Spacer(Modifier.height(TinoSpacing.sm))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TinoSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
                content = header,
            )
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(animationSpec = TinoMotion.settle(reduceMotion)) +
                    slideInVertically(animationSpec = TinoMotion.settle(reduceMotion)) { distance -> distance / 12 },
                exit = fadeOut(animationSpec = TinoMotion.settle(reduceMotion)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = sheetHeight - TinoSize.surfaceContentReservedHeight)
                        .then(
                            if (scrollContent) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            },
                        )
                        .padding(start = TinoSpacing.screen, end = TinoSpacing.screen, bottom = TinoSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
                    content = content,
                )
            }
        }
    }
}

private fun nearestStage(fraction: Float, size: A2uiSurfaceSize): A2uiSurfaceStage {
    val peek = A2uiSurfacePolicy.fraction(A2uiSurfaceStage.PEEK, size)
    val expanded = A2uiSurfacePolicy.fraction(A2uiSurfaceStage.EXPANDED, size)
    val full = A2uiSurfacePolicy.fraction(A2uiSurfaceStage.FULL, size)
    return listOf(
        A2uiSurfaceStage.PEEK to peek,
        A2uiSurfaceStage.EXPANDED to expanded,
        A2uiSurfaceStage.FULL to full,
    ).minBy { (_, value) -> kotlin.math.abs(value - fraction) }.first
}
