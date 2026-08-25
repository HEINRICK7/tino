package com.tino.app.feature.fiscal

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.tino.fiscal.core.DocumentFrameMetrics
import kotlin.math.abs

/**
 * Lightweight on-device quality analyzer for the scanner preview.
 *
 * This intentionally does not run OCR or touch the fiscal domain. It samples
 * the Y plane to decide whether the frame is bright, detailed and stable
 * enough for a high-resolution capture. It also exposes a conservative,
 * axis-aligned bright-region candidate so auto-capture cannot fire from frame
 * stability alone. The full angled-contour detector remains a later slice.
 */
class TinoDocumentFrameAnalyzer(
    private val onMetrics: (DocumentFrameMetrics, NormalizedDocumentQuad?) -> Unit,
) : ImageAnalysis.Analyzer {
    private var previousBrightness: Float? = null
    private var previousEdgeDensity: Float? = null
    private var previousQuad: QuadSignature? = null
    private var stableFrameCount = 0
    private var geometryStableFrameCount = 0

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val sampleWidth = 64
            val sampleHeight = 48
            var total = 0f
            var totalGradient = 0f
            var minValue = 1f
            var maxValue = 0f
            var edgeCount = 0
            var sampleCount = 0
            var previousRowValue: Float? = null
            val luminance = FloatArray(sampleWidth * sampleHeight)

            for (sampleY in 0 until sampleHeight) {
                val y = (sampleY * image.height / sampleHeight).coerceAtMost(image.height - 1)
                var previousValue: Float? = null
                for (sampleX in 0 until sampleWidth) {
                    val x = (sampleX * image.width / sampleWidth).coerceAtMost(image.width - 1)
                    val offset = y * plane.rowStride + x * plane.pixelStride
                    if (offset < 0 || offset >= buffer.limit()) continue
                    val value = (buffer.get(offset).toInt() and 0xFF) / 255f
                    luminance[sampleY * sampleWidth + sampleX] = value
                    total += value
                    minValue = minOf(minValue, value)
                    maxValue = maxOf(maxValue, value)
                    sampleCount++

                    previousValue?.let { delta ->
                        val gradient = abs(value - delta)
                        totalGradient += gradient
                        if (gradient > EDGE_THRESHOLD) edgeCount++
                    }
                    previousRowValue?.let { delta ->
                        val gradient = abs(value - delta)
                        totalGradient += gradient
                        if (gradient > EDGE_THRESHOLD) edgeCount++
                    }
                    previousValue = value
                }
                previousRowValue = previousValue
            }

            if (sampleCount == 0) return
            val brightness = total / sampleCount
            val edgeDensity = (edgeCount / (sampleCount * 2f)).coerceIn(0f, 1f)
            val sharpness = (totalGradient / (sampleCount * 2f) * SHARPNESS_GAIN)
                .coerceIn(0f, 1f)
            val contrast = maxValue - minValue
            val candidateQuad = findBrightQuad(luminance, sampleWidth, sampleHeight)
            val geometryStable = previousQuad?.isNear(candidateQuad) == true
            geometryStableFrameCount = if (geometryStable) {
                geometryStableFrameCount + 1
            } else {
                0
            }
            previousQuad = candidateQuad

            val stable = previousBrightness != null &&
                abs(brightness - previousBrightness!!) < STABLE_BRIGHTNESS_DELTA &&
                abs(edgeDensity - (previousEdgeDensity ?: edgeDensity)) < STABLE_EDGE_DELTA
            stableFrameCount = if (stable) stableFrameCount + 1 else 0
            previousBrightness = brightness
            previousEdgeDensity = edgeDensity

            // Conservative heuristic for the first slice. It avoids claiming
            // document geometry while still blocking dark/empty frames.
            val sheetDetected = contrast >= MIN_DOCUMENT_CONTRAST && edgeDensity >= MIN_EDGE_DENSITY
            val coverageRatio = (0.55f + edgeDensity * 3.2f).coerceIn(0f, 0.94f)
            val metrics = DocumentFrameMetrics(
                sheetDetected = sheetDetected,
                coverageRatio = coverageRatio,
                brightness = brightness,
                sharpness = sharpness,
                stableFrameCount = stableFrameCount,
                quadrilateralDetected = candidateQuad != null,
                geometryStableFrameCount = geometryStableFrameCount,
            )
            onMetrics(metrics, candidateQuad?.toNormalizedQuad())
        } finally {
            image.close()
        }
    }

    private fun findBrightQuad(values: FloatArray, width: Int, height: Int): QuadSignature? {
        val mean = values.average().toFloat()
        val threshold = maxOf(0.55f, (mean + 0.10f).coerceAtMost(0.78f))
        val mask = BooleanArray(values.size) { values[it] >= threshold }
        val visited = BooleanArray(values.size)
        val queue = IntArray(values.size)
        var best: QuadSignature? = null
        var bestScore = 0f

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var count = 0
            var minX = width
            var minY = height
            var maxX = 0
            var maxY = 0
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                count++
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val neighbor = ny * width + nx
                    if (mask[neighbor] && !visited[neighbor]) {
                        visited[neighbor] = true
                        queue[tail++] = neighbor
                    }
                }
            }
            val boxWidth = maxX - minX + 1
            val boxHeight = maxY - minY + 1
            val boxArea = boxWidth * boxHeight
            val areaRatio = boxArea.toFloat() / values.size
            val density = count.toFloat() / boxArea.coerceAtLeast(1)
            if (areaRatio !in 0.18f..0.995f || density < 0.28f) continue
            val centerX = (minX + maxX) / (2f * width)
            val centerY = (minY + maxY) / (2f * height)
            if (centerX !in 0.08f..0.92f || centerY !in 0.08f..0.92f) continue
            val score = areaRatio * density
            if (score > bestScore) {
                bestScore = score
                best = QuadSignature(
                    left = minX / width.toFloat(),
                    top = minY / height.toFloat(),
                    right = maxX / width.toFloat(),
                    bottom = maxY / height.toFloat(),
                )
            }
        }
        return best
    }

    private data class QuadSignature(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        fun toNormalizedQuad(): NormalizedDocumentQuad = NormalizedDocumentQuad(
            topLeft = android.graphics.PointF(left, top),
            topRight = android.graphics.PointF(right, top),
            bottomRight = android.graphics.PointF(right, bottom),
            bottomLeft = android.graphics.PointF(left, bottom),
        )

        fun isNear(other: QuadSignature?): Boolean = other != null &&
            kotlin.math.abs(left - other.left) < 0.04f &&
            kotlin.math.abs(top - other.top) < 0.04f &&
            kotlin.math.abs(right - other.right) < 0.04f &&
            kotlin.math.abs(bottom - other.bottom) < 0.04f
    }
    companion object {
        private const val EDGE_THRESHOLD = 0.12f
        private const val SHARPNESS_GAIN = 5.5f
        private const val MIN_DOCUMENT_CONTRAST = 0.18f
        private const val MIN_EDGE_DENSITY = 0.035f
        private const val STABLE_BRIGHTNESS_DELTA = 0.025f
        private const val STABLE_EDGE_DELTA = 0.025f
    }
}
