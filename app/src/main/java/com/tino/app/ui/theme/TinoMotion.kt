package com.tino.app.ui.theme

import android.animation.ValueAnimator
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Closed motion vocabulary for the TINO UI.
 *
 * Compose default for organic movement is physics (`spring()`), not duration
 * tweens. Shared-element / lookahead hosts consume the spatial spec.
 * Reduced motion always collapses to an instant snap.
 */
enum class TinoMotionKind {
    /** Tap and press feedback. High stiffness, no bounce. */
    PRESS,
    /** Content appearing or settling in place. */
    SETTLE,
    /** Mascot travel, shared bounds, placement changes. */
    SPATIAL,
    /** Completion and confirmation. Slight overshoot. */
    EMPHASIS,
    /** Idle body, breathing and roam. Low bounce, floating. */
    MASCOT,
    /** Eye tracking. Smooth, no overshoot. */
    GAZE,
    /** Cards and lists growing or shrinking. */
    CONTENT,
    /** Error / alert mascot. Elastic and noticeable. */
    BOUNCY,
    /** Splash handover and first reveal. Slow, no bounce. */
    REVEAL,
}

object TinoMotion {
    const val PressScale = 0.97f
    const val ScreenEnterScale = 0.98f

    val pressStiffness = Spring.StiffnessHigh
    val settleStiffness = Spring.StiffnessMediumLow
    val spatialStiffness = 220f
    val emphasisStiffness = Spring.StiffnessMedium
    val mascotStiffness = Spring.StiffnessMediumLow
    val gazeStiffness = Spring.StiffnessMediumLow
    val contentStiffness = Spring.StiffnessMedium
    val bouncyStiffness = Spring.StiffnessMedium
    val revealStiffness = Spring.StiffnessLow

    val pressDamping = Spring.DampingRatioNoBouncy
    val settleDamping = Spring.DampingRatioNoBouncy
    val spatialDamping = 0.84f
    val emphasisDamping = Spring.DampingRatioMediumBouncy
    val mascotDamping = Spring.DampingRatioLowBouncy
    val gazeDamping = Spring.DampingRatioNoBouncy
    val contentDamping = Spring.DampingRatioNoBouncy
    val bouncyDamping = Spring.DampingRatioHighBouncy
    val revealDamping = Spring.DampingRatioNoBouncy

    fun <T> of(
        kind: TinoMotionKind,
        reduceMotion: Boolean = false,
    ): FiniteAnimationSpec<T> {
        if (reduceMotion) return snap()
        return when (kind) {
            TinoMotionKind.PRESS -> spring(dampingRatio = pressDamping, stiffness = pressStiffness)
            TinoMotionKind.SETTLE -> spring(dampingRatio = settleDamping, stiffness = settleStiffness)
            TinoMotionKind.SPATIAL -> spring(dampingRatio = spatialDamping, stiffness = spatialStiffness)
            TinoMotionKind.EMPHASIS -> spring(dampingRatio = emphasisDamping, stiffness = emphasisStiffness)
            TinoMotionKind.MASCOT -> spring(dampingRatio = mascotDamping, stiffness = mascotStiffness)
            TinoMotionKind.GAZE -> spring(dampingRatio = gazeDamping, stiffness = gazeStiffness)
            TinoMotionKind.CONTENT -> spring(dampingRatio = contentDamping, stiffness = contentStiffness)
            TinoMotionKind.BOUNCY -> spring(dampingRatio = bouncyDamping, stiffness = bouncyStiffness)
            TinoMotionKind.REVEAL -> spring(dampingRatio = revealDamping, stiffness = revealStiffness)
        }
    }

    fun <T> press(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        of(TinoMotionKind.PRESS, reduceMotion)

    fun <T> settle(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        of(TinoMotionKind.SETTLE, reduceMotion)

    fun <T> spatial(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        of(TinoMotionKind.SPATIAL, reduceMotion)

    fun <T> emphasis(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        of(TinoMotionKind.EMPHASIS, reduceMotion)

    fun <T> mascot(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        of(TinoMotionKind.MASCOT, reduceMotion)

    fun <T> gaze(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        of(TinoMotionKind.GAZE, reduceMotion)

    fun <T> content(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        of(TinoMotionKind.CONTENT, reduceMotion)

    fun <T> bouncy(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        of(TinoMotionKind.BOUNCY, reduceMotion)

    fun <T> reveal(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        of(TinoMotionKind.REVEAL, reduceMotion)
}

val LocalTinoReduceMotion = staticCompositionLocalOf { false }

@Composable
fun rememberTinoReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val durationScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        durationScale == 0f || !ValueAnimator.areAnimatorsEnabled()
    }
}


