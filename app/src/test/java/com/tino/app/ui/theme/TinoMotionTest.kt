package com.tino.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import com.tino.app.ui.components.TinoSharedKeys
import com.tino.app.ui.components.tinoScreenContentTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TinoMotionTest {
    @Test
    fun pressScaleStaysSubtle() {
        assertEquals(0.97f, TinoMotion.PressScale, 0.0f)
    }

    @Test
    fun pressIsStifferThanSpatialTravel() {
        val press = TinoMotion.press<Float>() as SpringSpec
        val spatial = TinoMotion.spatial<Float>() as SpringSpec
        assertTrue(press.stiffness > spatial.stiffness)
        assertEquals(TinoMotion.pressDamping, press.dampingRatio, 0.0f)
    }

    @Test
    fun reducedMotionCollapsesEveryKindToASnap() {
        TinoMotionKind.entries.forEach { kind ->
            val spec = TinoMotion.of<Float>(kind, reduceMotion = true)
            assertTrue(kind.name, spec is SnapSpec)
        }
    }

    @Test
    fun spatialKeepsASoftLanding() {
        val spatial = TinoMotion.spatial<Float>() as SpringSpec
        assertTrue(spatial.dampingRatio < 1f)
        assertTrue(spatial.dampingRatio > 0.7f)
    }

    @Test
    fun sharedKeysStayStableAndDistinct() {
        assertEquals("product:cafe-marata", TinoSharedKeys.product("cafe-marata"))
        assertEquals("customer:maria", TinoSharedKeys.customer("maria"))
        assertEquals("order:42", TinoSharedKeys.order("42"))
        assertEquals("supplier:acme", TinoSharedKeys.supplier("acme"))
        assertNotEquals(TinoSharedKeys.product("maria"), TinoSharedKeys.customer("maria"))
    }

    @Test
    fun mascotIsSofterThanPress() {
        val mascot = TinoMotion.mascot<Float>() as SpringSpec
        val press = TinoMotion.press<Float>() as SpringSpec
        assertTrue(mascot.stiffness < press.stiffness)
        assertTrue(mascot.dampingRatio < 1f)
    }

    @Test
    fun errorMascotIsTheBounciest() {
        val error = TinoMotion.bouncy<Float>() as SpringSpec
        val idle = TinoMotion.mascot<Float>() as SpringSpec
        assertTrue(error.dampingRatio < idle.dampingRatio)
    }

    @Test
    fun reducedMotionScreenTransformHasNoEnterOrExit() {
        val transform = tinoScreenContentTransform(reduceMotion = true, fromLayer = 1, toLayer = 2)
        assertEquals(EnterTransition.None, transform.targetContentEnter)
        assertEquals(ExitTransition.None, transform.initialContentExit)
    }

    @Test
    fun everyScreenChangeHasAVisibleEnterAndExit() {
        listOf(0 to 1, 1 to 2, 2 to 1, 1 to 1).forEach { (from, to) ->
            val transform = tinoScreenContentTransform(reduceMotion = false, fromLayer = from, toLayer = to)
            assertNotEquals(EnterTransition.None, transform.targetContentEnter)
            assertNotEquals(ExitTransition.None, transform.initialContentExit)
        }
    }

    @Test
    fun splashRevealIsSlowerThanSettle() {
        val reveal = TinoMotion.reveal<Float>() as SpringSpec
        val settle = TinoMotion.settle<Float>() as SpringSpec
        assertTrue(reveal.stiffness < settle.stiffness)
        assertEquals(TinoMotion.revealDamping, reveal.dampingRatio, 0.0f)
    }
}
