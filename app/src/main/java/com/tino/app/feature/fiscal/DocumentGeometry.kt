package com.tino.app.feature.fiscal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class NormalizedDocumentQuad(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF,
) {
    fun asPoints(): List<PointF> = listOf(topLeft, topRight, bottomRight, bottomLeft)
}

/** Geometry-only functions are deterministic and independent of OCR/fiscal state. */
object DocumentQuadGeometry {
    fun orderCorners(points: List<PointF>): NormalizedDocumentQuad? {
        if (points.size != 4) return null
        val topLeft = points.minByOrNull { it.x + it.y } ?: return null
        val bottomRight = points.maxByOrNull { it.x + it.y } ?: return null
        val topRight = points.minByOrNull { it.y - it.x } ?: return null
        val bottomLeft = points.maxByOrNull { it.y - it.x } ?: return null
        val ordered = listOf(topLeft, topRight, bottomRight, bottomLeft)
        // Do not use PointF.hashCode here: this path is also exercised by the
        // local JVM tests, where Android value-object methods are not reliable.
        val unique = ordered.indices.all { index ->
            ((index + 1) until ordered.size).all { otherIndex ->
                ordered[index].x != ordered[otherIndex].x ||
                    ordered[index].y != ordered[otherIndex].y
            }
        }
        if (!unique) return null
        return NormalizedDocumentQuad(topLeft, topRight, bottomRight, bottomLeft)
    }

    fun areaRatio(quad: NormalizedDocumentQuad): Float = abs(
        quad.topLeft.x * quad.topRight.y +
            quad.topRight.x * quad.bottomRight.y +
            quad.bottomRight.x * quad.bottomLeft.y +
            quad.bottomLeft.x * quad.topLeft.y -
            quad.topRight.x * quad.topLeft.y -
            quad.bottomRight.x * quad.topRight.y -
            quad.bottomLeft.x * quad.bottomRight.y -
            quad.topLeft.x * quad.bottomLeft.y,
    ) / 2f

    fun isValid(quad: NormalizedDocumentQuad): Boolean {
        val area = areaRatio(quad)
        val centerX = quad.asPoints().map { it.x }.average().toFloat()
        val centerY = quad.asPoints().map { it.y }.average().toFloat()
        val topWidth = distance(quad.topLeft, quad.topRight)
        val bottomWidth = distance(quad.bottomLeft, quad.bottomRight)
        val leftHeight = distance(quad.topLeft, quad.bottomLeft)
        val rightHeight = distance(quad.topRight, quad.bottomRight)
        if (area < MIN_AREA || area > MAX_AREA) return false
        if (centerX !in MIN_CENTER..MAX_CENTER || centerY !in MIN_CENTER..MAX_CENTER) return false
        if (min(topWidth, bottomWidth) / max(topWidth, bottomWidth) < MIN_SIDE_RATIO) return false
        if (min(leftHeight, rightHeight) / max(leftHeight, rightHeight) < MIN_SIDE_RATIO) return false
        return quad.asPoints().all { it.x in 0f..1f && it.y in 0f..1f }
    }

    private fun distance(a: PointF, b: PointF): Float = hypot(a.x - b.x, a.y - b.y)

    private const val MIN_AREA = 0.20f
    private const val MAX_AREA = 0.98f
    private const val MIN_CENTER = 0.08f
    private const val MAX_CENTER = 0.92f
    private const val MIN_SIDE_RATIO = 0.30f
}

/**
 * Low-memory DANFE candidate detector. It operates on a bounded grayscale
 * bitmap, while the original capture remains available for final rectification.
 */
class BitmapDocumentQuadDetector(
    private val maxAnalysisDimension: Int = 480,
) {
    fun detect(source: Bitmap): NormalizedDocumentQuad? {
        val scale = min(
            1f,
            maxAnalysisDimension.toFloat() / max(source.width, source.height).toFloat(),
        )
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())
        val bitmap = if (width == source.width && height == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, width, height, true)
        }
        return try {
            detectOnBitmap(bitmap)
        } finally {
            if (bitmap !== source) bitmap.recycle()
        }
    }

    private fun detectOnBitmap(bitmap: Bitmap): NormalizedDocumentQuad? {
        val gridWidth = 120
        val gridHeight = 90
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val luminance = FloatArray(gridWidth * gridHeight)
        var sum = 0f
        var minValue = 1f
        var maxValue = 0f
        for (y in 0 until gridHeight) {
            for (x in 0 until gridWidth) {
                val sourceX = (x * bitmap.width / gridWidth).coerceAtMost(bitmap.width - 1)
                val sourceY = (y * bitmap.height / gridHeight).coerceAtMost(bitmap.height - 1)
                val color = pixels[sourceY * bitmap.width + sourceX]
                val value = (
                    Color.red(color) * 0.299f +
                        Color.green(color) * 0.587f +
                        Color.blue(color) * 0.114f
                    ) / 255f
                luminance[y * gridWidth + x] = value
                sum += value
                minValue = min(minValue, value)
                maxValue = max(maxValue, value)
            }
        }
        val mean = sum / luminance.size
        val thresholds = listOf(0.50f, 0.58f, (mean + 0.10f).coerceIn(0.42f, 0.78f))
        return thresholds.mapNotNull { threshold -> findCandidate(luminance, gridWidth, gridHeight, threshold) }
            .maxByOrNull { it.score }
            ?.takeIf { it.contrast >= 0.18f && DocumentQuadGeometry.isValid(it.quad) }
            ?.quad
    }

    private fun findCandidate(
        values: FloatArray,
        width: Int,
        height: Int,
        threshold: Float,
    ): Candidate? {
        val mask = BooleanArray(values.size) { values[it] >= threshold }
        val visited = BooleanArray(values.size)
        val queue = IntArray(values.size)
        var best: Candidate? = null
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
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
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
            val areaRatio = boxArea.toFloat() / (width * height)
            val density = count.toFloat() / boxArea.coerceAtLeast(1)
            if (areaRatio < 0.18f || areaRatio > 0.995f || density < 0.28f) continue

            val quad = NormalizedDocumentQuad(
                topLeft = PointF(minX / width.toFloat(), minY / height.toFloat()),
                topRight = PointF(maxX / width.toFloat(), minY / height.toFloat()),
                bottomRight = PointF(maxX / width.toFloat(), maxY / height.toFloat()),
                bottomLeft = PointF(minX / width.toFloat(), maxY / height.toFloat()),
            )
            val contrast = values.filterIndexed { index, _ -> mask[index] }.average().toFloat()
            val score = areaRatio * density * contrast
            val candidate = Candidate(quad, score, contrast)
            if (best == null || candidate.score > best.score) best = candidate
        }
        return best
    }

    private data class Candidate(
        val quad: NormalizedDocumentQuad,
        val score: Float,
        val contrast: Float,
    )
}

data class RectifiedDocumentSize(val width: Int, val height: Int)

object DocumentPerspectiveRectifier {
    fun outputSize(sourceWidth: Int, sourceHeight: Int, quad: NormalizedDocumentQuad): RectifiedDocumentSize? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || !DocumentQuadGeometry.isValid(quad)) return null
        val sourcePoints = sourcePoints(sourceWidth, sourceHeight, quad)
        val outputWidth = max(
            distance(sourcePoints, 0, 2),
            distance(sourcePoints, 6, 4),
        ).roundToInt().coerceIn(320, 2400)
        val outputHeight = max(
            distance(sourcePoints, 0, 6),
            distance(sourcePoints, 2, 4),
        ).roundToInt().coerceIn(240, 3200)
        return RectifiedDocumentSize(outputWidth, outputHeight)
    }

    fun rectify(source: Bitmap, quad: NormalizedDocumentQuad): Bitmap? {
        val size = outputSize(source.width, source.height, quad) ?: return null
        val sourcePoints = sourcePoints(source.width, source.height, quad)
        val destinationPoints = floatArrayOf(
            0f, 0f,
            size.width.toFloat(), 0f,
            size.width.toFloat(), size.height.toFloat(),
            0f, size.height.toFloat(),
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(sourcePoints, 0, destinationPoints, 0, 4)) return null
        return Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun sourcePoints(
        sourceWidth: Int,
        sourceHeight: Int,
        quad: NormalizedDocumentQuad,
    ): FloatArray = floatArrayOf(
        quad.topLeft.x * sourceWidth,
        quad.topLeft.y * sourceHeight,
        quad.topRight.x * sourceWidth,
        quad.topRight.y * sourceHeight,
        quad.bottomRight.x * sourceWidth,
        quad.bottomRight.y * sourceHeight,
        quad.bottomLeft.x * sourceWidth,
        quad.bottomLeft.y * sourceHeight,
    )

    private fun distance(points: FloatArray, first: Int, second: Int): Float = hypot(
        points[first] - points[second],
        points[first + 1] - points[second + 1],
    )
}

/**
 * Removes the small strips of desk/keyboard that can remain after perspective
 * correction. It is deliberately conservative: when the table boundary is
 * uncertain, the full rectified image is kept for human review.
 */
object DocumentContentCropper {
    fun trimTableMargins(source: Bitmap): Bitmap {
        if (source.width < 320 || source.height < 240) return source

        val analysisWidth = min(900, source.width)
        val scale = analysisWidth.toFloat() / source.width.toFloat()
        val analysisHeight = max(1, (source.height * scale).roundToInt())
        val analysis = Bitmap.createScaledBitmap(source, analysisWidth, analysisHeight, true)
        val pixels = IntArray(analysisWidth * analysisHeight)
        analysis.getPixels(pixels, 0, analysisWidth, 0, 0, analysisWidth, analysisHeight)
        analysis.recycle()

        val darkRatios = FloatArray(analysisHeight)
        val brightness = FloatArray(analysisHeight)
        for (y in 0 until analysisHeight) {
            var dark = 0
            var totalLuminance = 0f
            for (x in 0 until analysisWidth) {
                val color = pixels[y * analysisWidth + x]
                val luminance = Color.red(color) * 0.299f +
                    Color.green(color) * 0.587f +
                    Color.blue(color) * 0.114f
                totalLuminance += luminance / 255f
                if (luminance < 165f) dark++
            }
            darkRatios[y] = dark.toFloat() / analysisWidth
            brightness[y] = totalLuminance / analysisWidth
        }

        val top = max(
            firstStableTableRow(darkRatios, brightness),
            firstPaperRun(brightness),
        )
        val bottom = min(
            lastStableTableRow(darkRatios, brightness),
            lastPaperRun(brightness),
        )
        val padding = max(2, (analysisHeight * 0.012f).roundToInt())
        val topWithPadding = (top - padding).coerceAtLeast(0)
        val bottomWithPadding = (bottom + padding).coerceAtMost(analysisHeight - 1)
        val cropHeight = bottomWithPadding - topWithPadding + 1

        // Keep the original if the detector found no meaningful margin or if
        // the proposed crop could remove real rows from a sparse document.
        if (cropHeight < analysisHeight * 0.72f) return source

        val sourceTop = (topWithPadding / scale).roundToInt().coerceIn(0, source.height - 1)
        val sourceBottom = (bottomWithPadding / scale).roundToInt().coerceIn(sourceTop + 1, source.height - 1)
        val horizontalInset = (source.width * 0.015f).roundToInt()
        val sourceLeft = horizontalInset.coerceAtMost(source.width / 20)
        val sourceRight = (source.width - sourceLeft).coerceAtLeast(sourceLeft + 1)
        return Bitmap.createBitmap(
            source,
            sourceLeft,
            sourceTop,
            sourceRight - sourceLeft,
            sourceBottom - sourceTop + 1,
        )
    }

    private fun firstStableTableRow(ratios: FloatArray, brightness: FloatArray): Int {
        val threshold = 0.34f
        for (index in 0 until ratios.size - 2) {
            val brightRun = (index until (index + 8).coerceAtMost(brightness.size))
                .count { brightness[it] >= 0.48f }
            if ((ratios[index] >= threshold && ratios[index + 1] >= threshold * 0.82f) || brightRun >= 7) {
                return index
            }
        }
        return 0
    }

    private fun lastStableTableRow(ratios: FloatArray, brightness: FloatArray): Int {
        val threshold = 0.34f
        for (index in ratios.size - 1 downTo 2) {
            val brightRun = ((index - 7).coerceAtLeast(0)..index)
                .count { brightness[it] >= 0.48f }
            if ((ratios[index] >= threshold && ratios[index - 1] >= threshold * 0.82f) || brightRun >= 7) {
                return index
            }
        }
        return ratios.lastIndex
    }

    private fun firstPaperRun(brightness: FloatArray): Int {
        for (index in 0 until brightness.size - 10) {
            if ((index until index + 10).count { brightness[it] >= 0.48f } >= 8) return index
        }
        return 0
    }

    private fun lastPaperRun(brightness: FloatArray): Int {
        for (index in brightness.size - 1 downTo 10) {
            if (((index - 9)..index).count { brightness[it] >= 0.42f } >= 8) return index
        }
        return brightness.lastIndex
    }
}
