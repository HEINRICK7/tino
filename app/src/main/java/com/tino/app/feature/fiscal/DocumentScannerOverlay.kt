package com.tino.app.feature.fiscal

import androidx.camera.view.LifecycleCameraController
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlinx.coroutines.isActive
import com.tino.app.ui.theme.LocalTinoReduceMotion
import com.tino.app.ui.theme.TinoMotion
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.camera.view.PreviewView
import com.tino.fiscal.core.CaptureUiState
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoGreenLight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.ui.platform.LocalDensity

data class DocumentQuad(
    val topLeft: Offset,
    val topRight: Offset,
    val bottomRight: Offset,
    val bottomLeft: Offset,
) {
    fun lerpTo(target: DocumentQuad, fraction: Float): DocumentQuad = DocumentQuad(
        topLeft = topLeft.lerp(target.topLeft, fraction),
        topRight = topRight.lerp(target.topRight, fraction),
        bottomRight = bottomRight.lerp(target.bottomRight, fraction),
        bottomLeft = bottomLeft.lerp(target.bottomLeft, fraction),
    )

    fun asPath(): Path = Path().apply {
        moveTo(topLeft.x, topLeft.y)
        lineTo(topRight.x, topRight.y)
        lineTo(bottomRight.x, bottomRight.y)
        lineTo(bottomLeft.x, bottomLeft.y)
        close()
    }
}

private fun Offset.lerp(target: Offset, fraction: Float): Offset = Offset(
    x = x + (target.x - x) * fraction,
    y = y + (target.y - y) * fraction,
)

@Composable
fun TinoDocumentCameraPreview(
    controller: LifecycleCameraController,
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    LaunchedEffect(controller, lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
    }
    AndroidView(
        factory = { context -> PreviewView(context) },
        modifier = modifier,
        update = { preview -> preview.controller = controller },
    )
}

@Composable
fun TinoDocumentScannerOverlay(
    state: CaptureUiState,
    progress: Float,
    detectedQuad: DocumentQuad?,
    detectedNormalizedQuad: NormalizedDocumentQuad? = null,
    tableMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val reduceMotion = LocalTinoReduceMotion.current
    var previousState by remember { mutableStateOf(state) }
    val visibleProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = TinoMotion.settle(reduceMotion),
        label = "capture-quality-progress",
    )
    val pulse = remember { Animatable(0.72f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            pulse.snapTo(1f)
            return@LaunchedEffect
        }
        var expand = true
        while (isActive) {
            pulse.animateTo(if (expand) 1f else 0.72f, TinoMotion.mascot())
            expand = !expand
        }
    }
    val scanLine by rememberInfiniteTransition(label = "ready-scan").animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "ready-scan-line",
    )

    LaunchedEffect(state) {
        if (state == CaptureUiState.CAPTURING && previousState != CaptureUiState.CAPTURING) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        previousState = state
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val fallbackQuad = remember(widthPx, heightPx, tableMode) {
            DocumentQuad(
                topLeft = Offset(widthPx * 0.05f, heightPx * if (tableMode) 0.14f else 0.18f),
                topRight = Offset(widthPx * 0.95f, heightPx * if (tableMode) 0.14f else 0.18f),
                bottomRight = Offset(widthPx * 0.95f, heightPx * if (tableMode) 0.76f else 0.82f),
                bottomLeft = Offset(widthPx * 0.05f, heightPx * if (tableMode) 0.76f else 0.82f),
            )
        }
        val detectedPixelQuad = detectedNormalizedQuad?.let { normalized ->
            DocumentQuad(
                topLeft = Offset(normalized.topLeft.x * widthPx, normalized.topLeft.y * heightPx),
                topRight = Offset(normalized.topRight.x * widthPx, normalized.topRight.y * heightPx),
                bottomRight = Offset(normalized.bottomRight.x * widthPx, normalized.bottomRight.y * heightPx),
                bottomLeft = Offset(normalized.bottomLeft.x * widthPx, normalized.bottomLeft.y * heightPx),
            )
        }
        val targetQuad = detectedQuad ?: detectedPixelQuad ?: fallbackQuad
        val topLeft by animateOffsetAsState(targetQuad.topLeft, TinoMotion.spatial(reduceMotion), label = "quad-top-left")
        val topRight by animateOffsetAsState(targetQuad.topRight, TinoMotion.spatial(reduceMotion), label = "quad-top-right")
        val bottomRight by animateOffsetAsState(targetQuad.bottomRight, TinoMotion.spatial(reduceMotion), label = "quad-bottom-right")
        val bottomLeft by animateOffsetAsState(targetQuad.bottomLeft, TinoMotion.spatial(reduceMotion), label = "quad-bottom-left")
        val animatedQuad = DocumentQuad(topLeft, topRight, bottomRight, bottomLeft)

        Canvas(Modifier.fillMaxSize()) {
            val path = animatedQuad.asPath()
            val dimColor = Color.Black.copy(alpha = if (state == CaptureUiState.CAPTURED) 0.08f else 0.12f)
            drawRect(dimColor)
            drawPath(path, Color.Transparent, style = Stroke(width = 1f))

            val accent = if (state == CaptureUiState.READY || state == CaptureUiState.CAPTURING) {
                TinoGreenLight
            } else {
                TinoGreen.copy(alpha = pulse.value)
            }
            drawPath(path, accent.copy(alpha = 0.24f), style = Stroke(width = 12f))

            val measure = PathMeasure()
            measure.setPath(path, false)
            val progressPath = Path()
            measure.getSegment(0f, measure.length * visibleProgress, progressPath, true)
            drawPath(progressPath, accent, style = Stroke(width = 5f, cap = StrokeCap.Round))

            drawCorner(this, animatedQuad.topLeft, accent, horizontal = 1f, vertical = 1f)
            drawCorner(this, animatedQuad.topRight, accent, horizontal = -1f, vertical = 1f)
            drawCorner(this, animatedQuad.bottomRight, accent, horizontal = -1f, vertical = -1f)
            drawCorner(this, animatedQuad.bottomLeft, accent, horizontal = 1f, vertical = -1f)

            if (state == CaptureUiState.READY) {
                val y = animatedQuad.topLeft.y + (animatedQuad.bottomLeft.y - animatedQuad.topLeft.y) * scanLine
                drawLine(
                    color = TinoGreenLight.copy(alpha = 0.78f),
                    start = Offset(animatedQuad.topLeft.x, y),
                    end = Offset(animatedQuad.topRight.x, y),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
            if (state == CaptureUiState.CAPTURED) drawRect(Color.White.copy(alpha = 0.72f))
        }

    }
}

private fun captureMessage(state: CaptureUiState, tableMode: Boolean): String = when (state) {
    CaptureUiState.SEARCHING_DOCUMENT -> if (tableMode) "Posicione a tabela" else "Posicione a nota"
    CaptureUiState.ADJUST_FRAMING -> if (tableMode) "Enquadre a tabela de produtos" else "Enquadre toda a nota"
    CaptureUiState.IMPROVE_LIGHT -> "Melhore a iluminação"
    CaptureUiState.HOLD_STILL -> "Mantenha firme"
    CaptureUiState.READY -> "Pronto"
    CaptureUiState.CAPTURING -> "Capturando..."
    CaptureUiState.CAPTURED -> "Nota capturada"
}

private fun drawCorner(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    point: Offset,
    color: Color,
    horizontal: Float,
    vertical: Float,
) {
    val length = 28f
    scope.drawLine(color, point, point + Offset(length * horizontal, 0f), 5f, StrokeCap.Round)
    scope.drawLine(color, point, point + Offset(0f, length * vertical), 5f, StrokeCap.Round)
}
