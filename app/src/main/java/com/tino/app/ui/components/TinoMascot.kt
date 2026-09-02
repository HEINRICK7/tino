package com.tino.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.tino.app.R
import com.tino.app.domain.agent.TinoPresenceMode
import com.tino.app.ui.theme.LocalTinoReduceMotion
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoTheme
import com.tino.app.ui.icons.TinoIcons
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Semantic states. Features describe intent; they never select an asset. */
sealed interface TinoMascotState {
    val contentDescription: String

    data object Idle : TinoMascotState {
        override val contentDescription = "TINO está disponível"
    }

    data object Observing : TinoMascotState {
        override val contentDescription = "TINO está acompanhando esta tela"
    }

    data object LookingLeft : TinoMascotState {
        override val contentDescription = "TINO está olhando para a esquerda"
    }

    data object LookingRight : TinoMascotState {
        override val contentDescription = "TINO está olhando para a direita"
    }

    data object Thinking : TinoMascotState {
        override val contentDescription = "TINO tem sugestões para esta tela"
    }

    data object Attention : TinoMascotState {
        override val contentDescription = "TINO tem algo importante para mostrar"
    }

    data object Guiding : TinoMascotState {
        override val contentDescription = "TINO está orientando o próximo passo"
    }

    companion object {
        fun fromPresence(mode: TinoPresenceMode): TinoMascotState = when (mode) {
            TinoPresenceMode.IDLE -> Idle
            TinoPresenceMode.LISTENING -> Observing
            TinoPresenceMode.THINKING,
            TinoPresenceMode.RESOLVING -> Thinking
            TinoPresenceMode.WAITING_FOR_USER -> Attention
            TinoPresenceMode.COMPLETED -> Idle
            TinoPresenceMode.ERROR -> Attention
        }
    }
}

/** Standard visual sizes; every token has a touch-safe footprint. */
enum class TinoMascotSize(val dp: Dp) {
    Icon(48.dp),
    Small(64.dp),
    Medium(88.dp),
    Large(128.dp),
    Hero(160.dp),
}

/** Composition intent. The parent still owns the final layout and clipping. */
enum class TinoMascotPlacement(
    internal val rotation: Float = 0f,
    internal val translationX: Float = 0f,
    internal val translationY: Float = 0f,
    internal val scale: Float = 1f,
) {
    Default,
    Inline,
    CardSide(rotation = -2f, translationX = -1f),
    CardTop(translationY = -2f),
    PeekLeft(translationX = -8f),
    PeekRight(translationX = 8f),
    PeekTop(translationY = -8f),
    Elevated(translationY = -4f, scale = 1.04f),
}

private data class GazeTarget(
    val x: Float,
    val y: Float,
    val travelMillis: Int,
    val holdMillis: Long,
)

private data class BodyTarget(
    val rotation: Float,
    val scaleX: Float,
    val scaleY: Float,
    val shiftX: Float,
    val shiftY: Float,
    val travelMillis: Int,
    val holdMillis: Long,
)

private fun TinoMascotState.motionMode(): TinoPresenceMode = when (this) {
    TinoMascotState.Idle -> TinoPresenceMode.IDLE
    TinoMascotState.Observing,
    TinoMascotState.LookingLeft,
    TinoMascotState.LookingRight -> TinoPresenceMode.LISTENING
    TinoMascotState.Thinking,
    TinoMascotState.Guiding -> TinoPresenceMode.THINKING
    TinoMascotState.Attention -> TinoPresenceMode.WAITING_FOR_USER
}

private fun bodyTargets(state: TinoMascotState): List<BodyTarget> = when (state) {
    TinoMascotState.LookingLeft -> listOf(BodyTarget(-2.5f, 1.01f, 0.99f, -1f, 0f, 420, 900))
    TinoMascotState.LookingRight -> listOf(BodyTarget(2.5f, 1.01f, 0.99f, 1f, 0f, 420, 900))
    TinoMascotState.Guiding -> listOf(
        BodyTarget(-1.8f, 1.01f, 0.99f, -1f, 0f, 520, 720),
        BodyTarget(1.8f, 0.99f, 1.01f, 1f, -1f, 520, 900),
    )
    else -> when (state.motionMode()) {
        TinoPresenceMode.IDLE -> listOf(
            BodyTarget(-1.4f, 1.01f, 0.99f, -1f, 0f, 620, 820),
            BodyTarget(1.2f, 0.99f, 1.01f, 1f, 0f, 720, 1_100),
            BodyTarget(0f, 1f, 1f, 0f, 0f, 560, 1_300),
        )
        TinoPresenceMode.LISTENING -> listOf(
            BodyTarget(-3f, 1.02f, 0.98f, -1f, 0f, 420, 560),
            BodyTarget(2.6f, 1.02f, 0.98f, 1f, 0f, 460, 680),
            BodyTarget(0f, 1f, 1f, 0f, 0f, 360, 760),
        )
        TinoPresenceMode.THINKING,
        TinoPresenceMode.RESOLVING -> listOf(
            BodyTarget(-2.2f, 1.01f, 0.99f, -1f, 0f, 520, 620),
            BodyTarget(2f, 0.99f, 1.01f, 1f, 0f, 600, 820),
            BodyTarget(0f, 1f, 1f, 0f, 0f, 500, 760),
        )
        TinoPresenceMode.WAITING_FOR_USER,
        TinoPresenceMode.COMPLETED,
        TinoPresenceMode.ERROR -> listOf(
            BodyTarget(0f, 1.025f, 0.975f, 0f, 0.5f, 320, 900),
            BodyTarget(-2f, 1.01f, 0.99f, -0.5f, 0f, 460, 760),
            BodyTarget(2f, 1.01f, 0.99f, 0.5f, 0f, 500, 920),
        )
    }
}

private fun gazeTargets(state: TinoMascotState): List<GazeTarget> = when (state) {
    TinoMascotState.LookingLeft -> listOf(GazeTarget(-0.82f, 0f, 360, 1_100))
    TinoMascotState.LookingRight -> listOf(GazeTarget(0.82f, 0f, 360, 1_100))
    TinoMascotState.Guiding -> listOf(
        GazeTarget(-0.62f, -0.08f, 400, 800),
        GazeTarget(0.62f, -0.08f, 420, 1_000),
    )
    TinoMascotState.Thinking -> listOf(
        GazeTarget(-0.32f, -0.58f, 460, 800),
        GazeTarget(0.48f, -0.42f, 520, 900),
        GazeTarget(0f, 0f, 360, 650),
    )
    TinoMascotState.Attention -> listOf(
        GazeTarget(0f, 0f, 280, 1_200),
        GazeTarget(0.35f, -0.08f, 360, 720),
    )
    else -> when (state.motionMode()) {
        TinoPresenceMode.IDLE -> listOf(
            GazeTarget(0f, 0f, 240, 1_200),
            GazeTarget(-0.58f, -0.06f, 440, 1_000),
            GazeTarget(0.56f, 0.04f, 440, 1_150),
        )
        TinoPresenceMode.LISTENING -> listOf(
            GazeTarget(-0.62f, 0f, 360, 620),
            GazeTarget(0.62f, 0f, 420, 760),
            GazeTarget(0f, 0f, 280, 820),
        )
        else -> listOf(GazeTarget(0f, 0f, 320, 1_000))
    }
}

/**
 * The only public mascot renderer. Assets are resolved here, never by
 * features. The neutral body asset is also the safe visual fallback.
 */
@Composable
fun TinoMascot(
    state: TinoMascotState,
    modifier: Modifier = Modifier,
    size: TinoMascotSize = TinoMascotSize.Medium,
    placement: TinoMascotPlacement = TinoMascotPlacement.Default,
    onClick: (() -> Unit)? = null,
) {
    val reduceMotion = LocalTinoReduceMotion.current
    val density = LocalDensity.current
    val body = ImageBitmap.imageResource(R.drawable.tino_mascot_official_body)
    val mode = state.motionMode()

    val bob = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "tino-mascot-idle-motion")
        val value by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (state == TinoMascotState.Idle) 2_800 else 1_100,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "tino-mascot-bob",
        )
        value
    }
    val gazeX = remember { Animatable(0f) }
    val gazeY = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val bodyScaleX = remember { Animatable(1f) }
    val bodyScaleY = remember { Animatable(1f) }
    val bodyShiftX = remember { Animatable(0f) }
    val bodyShiftY = remember { Animatable(0f) }

    LaunchedEffect(state, reduceMotion) {
        val targets = bodyTargets(state)
        var index = 0
        while (isActive) {
            val target = targets[index % targets.size]
            val travel = if (reduceMotion) 1 else target.travelMillis
            coroutineScope {
                launch { rotation.animateTo(target.rotation, tween(travel, easing = FastOutSlowInEasing)) }
                launch { bodyScaleX.animateTo(target.scaleX, tween(travel, easing = FastOutSlowInEasing)) }
                launch { bodyScaleY.animateTo(target.scaleY, tween(travel, easing = FastOutSlowInEasing)) }
                launch { bodyShiftX.animateTo(target.shiftX, tween(travel, easing = FastOutSlowInEasing)) }
                launch { bodyShiftY.animateTo(target.shiftY, tween(travel, easing = FastOutSlowInEasing)) }
            }
            if (reduceMotion) break
            delay(target.holdMillis)
            index += 1
        }
    }

    LaunchedEffect(state, reduceMotion) {
        val targets = gazeTargets(state)
        var index = 0
        gazeX.snapTo(0f)
        gazeY.snapTo(0f)
        while (isActive) {
            val target = targets[index % targets.size]
            val travel = if (reduceMotion) 1 else target.travelMillis
            coroutineScope {
                launch { gazeX.animateTo(target.x, tween(travel, easing = FastOutSlowInEasing)) }
                launch { gazeY.animateTo(target.y, tween(travel, easing = FastOutSlowInEasing)) }
            }
            if (reduceMotion) break
            delay(target.holdMillis)
            index += 1
        }
    }

    var blinking by remember { mutableStateOf(false) }
    LaunchedEffect(reduceMotion) {
        blinking = false
        if (!reduceMotion) {
            while (isActive) {
                delay(2_400)
                blinking = true
                delay(110)
                blinking = false
            }
        }
    }

    val interactiveModifier = if (onClick == null) {
        modifier
    } else {
        modifier
            .semantics { contentDescription = state.contentDescription }
            .tinoClickable(role = Role.Button, onClick = onClick)
    }
    Canvas(
        interactiveModifier
            .size(size.dp)
            .graphicsLayer {
                translationX = bodyShiftX.value + placement.translationX * density.density
                translationY = (bob * if (state == TinoMascotState.Idle) 1.5f else 2.5f) +
                    bodyShiftY.value + placement.translationY * density.density
                rotationZ = rotation.value + placement.rotation
                scaleX = bodyScaleX.value * placement.scale
                scaleY = bodyScaleY.value * placement.scale
            },
    ) {
        drawTinoMascot(
            officialBody = body,
            blinking = blinking,
            gazeX = gazeX.value,
            gazeY = gazeY.value,
        )
    }
}

private fun DrawScope.drawTinoMascot(
    officialBody: ImageBitmap,
    blinking: Boolean,
    gazeX: Float,
    gazeY: Float,
) {
    val width = size.width
    val height = size.height
    drawImage(
        image = officialBody,
        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
    )

    val eyeY = height * (121f / 220f)
    val eyeRadiusX = width * (15f / 220f)
    val eyeRadiusY = height * (21f / 220f)
    val offsetX = gazeX * width * 0.035f
    val offsetY = gazeY * height * 0.035f
    listOf(width * (81f / 220f), width * (136f / 220f)).forEach { eyeX ->
        val centerX = eyeX + offsetX
        val centerY = eyeY + offsetY
        if (blinking) {
            drawLine(
                color = TinoInk,
                start = androidx.compose.ui.geometry.Offset(centerX - eyeRadiusX, centerY),
                end = androidx.compose.ui.geometry.Offset(centerX + eyeRadiusX, centerY),
                strokeWidth = eyeRadiusY * 0.20f,
                cap = StrokeCap.Round,
            )
        } else {
            drawOval(
                color = TinoInk,
                topLeft = androidx.compose.ui.geometry.Offset(centerX - eyeRadiusX, centerY - eyeRadiusY),
                size = androidx.compose.ui.geometry.Size(eyeRadiusX * 2f, eyeRadiusY * 2f),
            )
        }
    }
}

@Preview(name = "Idle", showBackground = true)
@Composable
fun TinoMascotIdlePreview() = TinoMascotPreview(TinoMascotState.Idle)

@Preview(name = "Thinking", showBackground = true)
@Composable
fun TinoMascotThinkingPreview() = TinoMascotPreview(TinoMascotState.Thinking)

@Preview(name = "Attention", showBackground = true)
@Composable
fun TinoMascotAttentionPreview() = TinoMascotPreview(TinoMascotState.Attention)

@Preview(name = "Looking left", showBackground = true)
@Composable
fun TinoMascotLookingLeftPreview() = TinoMascotPreview(TinoMascotState.LookingLeft)

@Preview(name = "Looking right", showBackground = true)
@Composable
fun TinoMascotLookingRightPreview() = TinoMascotPreview(TinoMascotState.LookingRight)

@Preview(name = "Guiding", showBackground = true)
@Composable
fun TinoMascotGuidingPreview() = TinoMascotPreview(TinoMascotState.Guiding)

@Preview(name = "Small", showBackground = true)
@Composable
fun TinoMascotSmallPreview() = TinoMascotPreview(TinoMascotState.Observing, TinoMascotSize.Small)

@Preview(name = "Large", showBackground = true)
@Composable
fun TinoMascotLargePreview() = TinoMascotPreview(TinoMascotState.Observing, TinoMascotSize.Large)

@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TinoMascotDarkPreview() = TinoMascotPreview(TinoMascotState.Observing)

@Composable
private fun TinoMascotPreview(
    state: TinoMascotState,
    size: TinoMascotSize = TinoMascotSize.Medium,
) {
    TinoTheme {
        TinoMascot(state = state, size = size)
    }
}

@Preview(name = "Empty state", showBackground = true)
@Composable
fun TinoEmptyStatePreview() {
    TinoTheme {
        Box(Modifier.padding(16.dp)) {
            TinoEmptyState(
                icon = TinoIcons.Products,
                title = "Nenhum produto ainda",
                message = "Cadastre o primeiro produto para começar.",
            )
        }
    }
}

@Preview(name = "Contextual card", showBackground = true)
@Composable
fun TinoContextualCardPreview() {
    TinoTheme {
        Box(Modifier.padding(16.dp)) {
            TinoContextualEmptyState(
                title = "Ainda não há clientes",
                message = "O TINO pode orientar seu primeiro cadastro.",
                actionLabel = "Cadastrar agora",
                icon = TinoIcons.People,
                onAction = {},
            )
        }
    }
}
