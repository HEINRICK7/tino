@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.tino.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import com.tino.app.ui.theme.LocalTinoReduceMotion
import com.tino.app.ui.theme.TinoMotion
import com.tino.app.ui.theme.TinoMotionKind

/**
 * Shared-element / lookahead host for the TINO shell.
 * One host per experience surface; screens do not create their own.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TinoMotionHost(content: @Composable () -> Unit) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalTinoSharedTransitionScope provides this) {
            LookaheadScope {
                content()
            }
        }
    }
}

val LocalTinoSharedTransitionScope = staticCompositionLocalOf<androidx.compose.animation.SharedTransitionScope?> {
    null
}

val LocalTinoAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> {
    null
}

object TinoSharedKeys {
    fun product(id: String) = "product:$id"
    fun customer(id: String) = "customer:$id"
    fun order(id: String) = "order:$id"
    fun supplier(id: String) = "supplier:$id"
}

fun tinoEnter(reduceMotion: Boolean): EnterTransition {
    if (reduceMotion) return EnterTransition.None
    return fadeIn(animationSpec = TinoMotion.settle()) +
        expandVertically(animationSpec = TinoMotion.settle()) +
        scaleIn(initialScale = TinoMotion.ScreenEnterScale, animationSpec = TinoMotion.settle())
}

fun tinoExit(reduceMotion: Boolean): ExitTransition {
    if (reduceMotion) return ExitTransition.None
    return fadeOut(animationSpec = TinoMotion.settle()) +
        shrinkVertically(animationSpec = TinoMotion.settle()) +
        scaleOut(targetScale = TinoMotion.ScreenEnterScale, animationSpec = TinoMotion.settle())
}

fun tinoOverlayEnter(reduceMotion: Boolean): EnterTransition {
    if (reduceMotion) return EnterTransition.None
    return fadeIn(animationSpec = TinoMotion.settle()) +
        slideInVertically(animationSpec = TinoMotion.spatial()) { distance -> distance / 6 } +
        scaleIn(initialScale = TinoMotion.ScreenEnterScale, animationSpec = TinoMotion.spatial())
}

fun tinoSplashExit(reduceMotion: Boolean): ExitTransition {
    if (reduceMotion) return ExitTransition.None
    val spec = TinoMotion.reveal<Float>()
    return fadeOut(animationSpec = spec) +
        scaleOut(targetScale = 1.04f, animationSpec = spec)
}

fun tinoOverlayExit(reduceMotion: Boolean): ExitTransition {
    if (reduceMotion) return ExitTransition.None
    return fadeOut(animationSpec = TinoMotion.settle()) +
        slideOutVertically(animationSpec = TinoMotion.spatial()) { distance -> distance / 6 }
}

private const val TinoScreenTransitionMillis = 380

fun tinoScreenContentTransform(
    reduceMotion: Boolean,
    fromLayer: Int = 0,
    toLayer: Int = 0,
): ContentTransform {
    if (reduceMotion) {
        return ContentTransform(
            targetContentEnter = EnterTransition.None,
            initialContentExit = ExitTransition.None,
            sizeTransform = null,
        )
    }
    val fade = tween<Float>(TinoScreenTransitionMillis, easing = FastOutSlowInEasing)
    val slide = tween<IntOffset>(TinoScreenTransitionMillis, easing = FastOutSlowInEasing)
    return when {
        toLayer > fromLayer -> ContentTransform(
            targetContentEnter = fadeIn(animationSpec = fade) +
                slideInHorizontally(animationSpec = slide) { distance -> distance / 3 },
            initialContentExit = fadeOut(animationSpec = fade) +
                slideOutHorizontally(animationSpec = slide) { distance -> -distance / 6 },
            sizeTransform = null,
        )
        toLayer < fromLayer -> ContentTransform(
            targetContentEnter = fadeIn(animationSpec = fade) +
                slideInHorizontally(animationSpec = slide) { distance -> -distance / 6 },
            initialContentExit = fadeOut(animationSpec = fade) +
                slideOutHorizontally(animationSpec = slide) { distance -> distance / 3 },
            sizeTransform = null,
        )
        else -> ContentTransform(
            targetContentEnter = fadeIn(animationSpec = fade) +
                slideInHorizontally(animationSpec = slide) { distance -> distance / 8 },
            initialContentExit = fadeOut(animationSpec = fade) +
                slideOutHorizontally(animationSpec = slide) { distance -> -distance / 8 },
            sizeTransform = null,
        )
    }
}

fun Modifier.tinoSharedBounds(key: String): Modifier = composed {
    val shared = LocalTinoSharedTransitionScope.current
    val visibility = LocalTinoAnimatedVisibilityScope.current
    val reduceMotion = LocalTinoReduceMotion.current
    if (shared == null || visibility == null || reduceMotion || key.isBlank()) {
        this
    } else {
        with(shared) {
            sharedBounds(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = visibility,
                boundsTransform = { _, _ -> TinoMotion.spatial() },
                enter = fadeIn(animationSpec = TinoMotion.settle()),
                exit = fadeOut(animationSpec = TinoMotion.settle()),
            )
        }
    }
}

fun Modifier.tinoClickable(
    enabled: Boolean = true,
    role: Role? = null,
    onClick: (() -> Unit)?,
): Modifier = composed {
    if (onClick == null) return@composed this
    val interactionSource = remember { MutableInteractionSource() }
    tinoPressScale(interactionSource, enabled)
        .clickable(
            enabled = enabled,
            role = role,
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick,
        )
}

fun Modifier.tinoAnimateContentSize(): Modifier = composed {
    val reduceMotion = LocalTinoReduceMotion.current
    animateContentSize(animationSpec = TinoMotion.content(reduceMotion))
}

fun Modifier.tinoPressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalTinoReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) TinoMotion.PressScale else 1f,
        animationSpec = TinoMotion.of(TinoMotionKind.PRESS, reduceMotion),
        label = "tino-press-scale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
