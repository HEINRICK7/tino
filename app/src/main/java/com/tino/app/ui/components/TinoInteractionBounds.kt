package com.tino.app.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

enum class TinoBoundsKind {
    INTERACTIVE,
    OCCUPIED,
    MASCOT_ROW,
}

enum class TinoMascotAnchor {
    TOP_END,
    TOP_START,
    TOP_END_BELOW_HEADER,
    TOP_START_BELOW_HEADER,
    CENTER_END,
    CENTER_START,
    CENTER,
    BOTTOM_END,
    BOTTOM_START,
}

/** Runtime slot selected by collision avoidance for the floating mascot. */
data class TinoMascotSlotPlacement(
    val xPx: Float,
    val yPx: Float,
    val visible: Boolean,
    val enabled: Boolean,
    val anchor: TinoMascotAnchor,
)

class TinoInteractionBoundsRegistry {
    val bounds = mutableStateMapOf<String, Rect>()
    private val kinds = mutableStateMapOf<String, TinoBoundsKind>()
    var rootBounds by mutableStateOf(Rect.Zero)

    fun update(key: String, bounds: Rect, kind: TinoBoundsKind = TinoBoundsKind.INTERACTIVE) {
        this.bounds[key] = bounds
        kinds[key] = kind
    }

    fun remove(key: String) {
        bounds.remove(key)
        kinds.remove(key)
    }

    fun updateRoot(bounds: Rect) {
        rootBounds = bounds
    }

    fun chooseMascotPlacement(
        mascotSizePx: Float,
        marginPx: Float,
        collisionPaddingPx: Float = 8f,
        belowHeaderPx: Float = 72f,
    ): TinoMascotSlotPlacement {
        val root = rootBounds
        if (root.width <= 0f || root.height <= 0f) {
            return TinoMascotSlotPlacement(0f, 0f, false, false, TinoMascotAnchor.TOP_END)
        }

        val width = root.width
        val height = root.height
        val maxX = (width - mascotSizePx - marginPx).coerceAtLeast(marginPx)
        val maxY = (height - mascotSizePx - marginPx).coerceAtLeast(marginPx)
        val endX = maxX
        val localBounds = bounds.mapValues { (_, bounds) ->
            Rect(
                left = bounds.left - root.left,
                top = bounds.top - root.top,
                right = bounds.right - root.left,
                bottom = bounds.bottom - root.top,
            )
        }
        val mascotRow = localBounds.entries.firstOrNull { (key, _) ->
            kinds[key] == TinoBoundsKind.MASCOT_ROW
        }?.value
        val (y, anchor) = if (mascotRow != null) {
            val centered = mascotRow.top + (mascotRow.height - mascotSizePx) / 2f
            centered.coerceIn(marginPx, maxY) to TinoMascotAnchor.TOP_END
        } else {
            (marginPx + belowHeaderPx).coerceIn(marginPx, maxY) to TinoMascotAnchor.TOP_END_BELOW_HEADER
        }
        val slot = Rect(endX, y, endX + mascotSizePx, y + mascotSizePx)
        val blocking = localBounds.filter { (key, _) ->
            kinds[key] == TinoBoundsKind.INTERACTIVE || kinds[key] == TinoBoundsKind.OCCUPIED
        }.values
        val enabled = blocking.none { bounds -> overlaps(slot, bounds, collisionPaddingPx) }

        return TinoMascotSlotPlacement(
            xPx = endX,
            yPx = y,
            visible = true,
            enabled = enabled,
            anchor = anchor,
        )
    }
}

private fun overlaps(first: Rect, second: Rect, paddingPx: Float): Boolean =
    first.left - paddingPx < second.right &&
        first.right + paddingPx > second.left &&
        first.top - paddingPx < second.bottom &&
        first.bottom + paddingPx > second.top

val LocalTinoInteractionBoundsRegistry = androidx.compose.runtime.staticCompositionLocalOf {
    TinoInteractionBoundsRegistry()
}

private fun Modifier.tinoRegisteredBounds(key: String, kind: TinoBoundsKind): Modifier = composed {
    val registry = LocalTinoInteractionBoundsRegistry.current
    val slotId = remember(key, kind) { "$kind:$key:${kotlin.random.Random.nextLong()}" }
    DisposableEffect(registry, slotId) {
        onDispose { registry.remove(slotId) }
    }
    this.onGloballyPositioned { coordinates ->
        registry.update(slotId, coordinates.boundsInRoot(), kind)
    }
}

fun Modifier.tinoInteractiveBounds(key: String): Modifier =
    tinoRegisteredBounds(key, TinoBoundsKind.INTERACTIVE)

fun Modifier.tinoOccupiedBounds(key: String): Modifier =
    tinoRegisteredBounds("occupied:" + key, TinoBoundsKind.OCCUPIED)

fun Modifier.tinoMascotRow(key: String): Modifier =
    tinoRegisteredBounds("mascot-row:" + key, TinoBoundsKind.MASCOT_ROW)

fun Modifier.tinoInteractionRoot(): Modifier = composed {
    val registry = LocalTinoInteractionBoundsRegistry.current
    this.onGloballyPositioned { coordinates ->
        registry.updateRoot(coordinates.boundsInRoot())
    }
}
