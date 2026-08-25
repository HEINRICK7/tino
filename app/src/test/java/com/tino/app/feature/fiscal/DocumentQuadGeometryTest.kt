package com.tino.app.feature.fiscal

import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DocumentQuadGeometryTest {
    private fun point(x: Float, y: Float): PointF = PointF().apply {
        this.x = x
        this.y = y
    }

    @Test
    fun ordersCornersFromUnorderedPoints() {
        val quad = DocumentQuadGeometry.orderCorners(
            listOf(
                point(0.9f, 0.8f),
                point(0.1f, 0.1f),
                point(0.9f, 0.1f),
                point(0.1f, 0.8f),
            ),
        )
        assertEquals(0.1f, quad?.topLeft?.x)
        assertEquals(0.1f, quad?.topLeft?.y)
        assertEquals(0.9f, quad?.topRight?.x)
        assertEquals(0.1f, quad?.topRight?.y)
        assertEquals(0.9f, quad?.bottomRight?.x)
        assertEquals(0.8f, quad?.bottomRight?.y)
        assertEquals(0.1f, quad?.bottomLeft?.x)
        assertEquals(0.8f, quad?.bottomLeft?.y)
    }

    @Test
    fun rejectsSmallOrOffCenterQuadrilateral() {
        val small = NormalizedDocumentQuad(
            point(0.4f, 0.4f), point(0.6f, 0.4f),
            point(0.6f, 0.6f), point(0.4f, 0.6f),
        )
        val outside = NormalizedDocumentQuad(
            point(-0.1f, 0.1f), point(0.9f, 0.1f),
            point(0.9f, 0.9f), point(-0.1f, 0.9f),
        )
        assertFalse(DocumentQuadGeometry.isValid(small))
        assertFalse(DocumentQuadGeometry.isValid(outside))
    }

    @Test
    fun acceptsLargeCenteredQuadrilateral() {
        val quad = NormalizedDocumentQuad(
            point(0.08f, 0.12f), point(0.92f, 0.10f),
            point(0.94f, 0.88f), point(0.06f, 0.90f),
        )
        assertTrue(DocumentQuadGeometry.isValid(quad))
        assertEquals(0.66f, DocumentQuadGeometry.areaRatio(quad), 0.03f)
    }

    @Test
    fun calculatesStableRectifiedOutputDimensions() {
        val quad = NormalizedDocumentQuad(
            point(0.10f, 0.12f), point(0.90f, 0.08f),
            point(0.94f, 0.88f), point(0.06f, 0.92f),
        )

        val size = DocumentPerspectiveRectifier.outputSize(1000, 600, quad)

        assertNotNull(size)
        assertTrue(size!!.width >= 320)
        assertTrue(size.height >= 240)
        assertTrue(size.width > size.height)
    }
}
