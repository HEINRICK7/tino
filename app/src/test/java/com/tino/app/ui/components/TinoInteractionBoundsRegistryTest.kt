package com.tino.app.ui.components

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TinoInteractionBoundsRegistryTest {
    @Test
    fun sitsInlineWithTheTitleRowOnTheRight() {
        val registry = TinoInteractionBoundsRegistry()
        registry.updateRoot(Rect(0f, 0f, 360f, 700f))
        registry.update("mascot-row:top-bar", Rect(0f, 16f, 360f, 72f), TinoBoundsKind.MASCOT_ROW)
        registry.update("chips", Rect(0f, 240f, 360f, 288f), TinoBoundsKind.OCCUPIED)

        val placement = registry.chooseMascotPlacement(
            mascotSizePx = 60f,
            marginPx = 8f,
            collisionPaddingPx = 8f,
            belowHeaderPx = 72f,
        )

        assertTrue(placement.visible)
        assertEquals(292f, placement.xPx)
        assertEquals(TinoMascotAnchor.TOP_END, placement.anchor)
        assertEquals(14f, placement.yPx, 0.1f)
        assertTrue(placement.yPx + 60f <= 240f)
    }

    @Test
    fun homeWithoutATitleRowSitsBesideTheGreeting() {
        val registry = TinoInteractionBoundsRegistry()
        registry.updateRoot(Rect(0f, 0f, 360f, 700f))
        registry.update("home-header", Rect(0f, 0f, 360f, 56f), TinoBoundsKind.OCCUPIED)

        val placement = registry.chooseMascotPlacement(
            mascotSizePx = 60f,
            marginPx = 8f,
            collisionPaddingPx = 8f,
            belowHeaderPx = 72f,
        )

        assertTrue(placement.visible)
        assertEquals(292f, placement.xPx)
        assertEquals(TinoMascotAnchor.TOP_END_BELOW_HEADER, placement.anchor)
        assertEquals(80f, placement.yPx, 0.1f)
    }

    @Test
    fun neverMovesTheMascotToTheLeft() {
        val registry = TinoInteractionBoundsRegistry()
        registry.updateRoot(Rect(0f, 0f, 360f, 700f))
        registry.update("right-column", Rect(250f, 0f, 360f, 700f), TinoBoundsKind.INTERACTIVE)

        val placement = registry.chooseMascotPlacement(
            mascotSizePx = 60f,
            marginPx = 8f,
            collisionPaddingPx = 8f,
        )

        assertTrue(placement.visible)
        assertEquals(292f, placement.xPx)
    }

    @Test
    fun disablesMascotWhenTheSlotOverlapsInteractiveContent() {
        val registry = TinoInteractionBoundsRegistry()
        registry.updateRoot(Rect(0f, 0f, 360f, 700f))
        registry.update("full-screen-interaction", Rect(0f, 0f, 360f, 700f))

        val placement = registry.chooseMascotPlacement(
            mascotSizePx = 60f,
            marginPx = 8f,
            collisionPaddingPx = 8f,
        )

        assertTrue(placement.visible)
        assertFalse(placement.enabled)
        assertEquals(292f, placement.xPx)
    }
}
