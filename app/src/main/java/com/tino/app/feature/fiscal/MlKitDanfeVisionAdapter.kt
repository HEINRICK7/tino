package com.tino.app.feature.fiscal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.tino.app.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tino.fiscal.core.DanfeColumn
import com.tino.fiscal.core.DanfeProductMapper
import com.tino.fiscal.core.DanfeTableRow
import com.tino.fiscal.core.DocumentImage
import com.tino.fiscal.core.DocumentVisionPort
import com.tino.fiscal.core.ProductImportResult
import com.tino.fiscal.core.ProductImportSource
import com.tino.fiscal.core.RecognizedCell
import java.math.BigDecimal
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Local first DANFE reader. It extracts text on-device and maps only rows that
 * have a product code, description, invoice unit and quantity. It never writes
 * Room, resolves catalog entities or changes stock.
 */
class MlKitDanfeVisionAdapter(
    private val context: Context,
    private val source: ProductImportSource = ProductImportSource.DANFE_CAMERA,
) : DocumentVisionPort {
    override suspend fun extractProducts(image: DocumentImage): ProductImportResult {
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
            ?: return ProductImportResult.Unavailable(
                reason = "Não foi possível abrir a foto da nota.",
                source = source,
            )
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val ocrBitmap = DanfeOcrImagePreprocessor.enhance(bitmap)
        return try {
            val result = recognizer.process(InputImage.fromBitmap(ocrBitmap, 0)).awaitTask()
            if (BuildConfig.DEBUG) {
                Log.d("TinoDanfeOCR", "recognizedText=${result.text}")
            }
            val rows = DanfeTextRowParser.parse(result.text, result.textBlocks.flatMap { it.lines })
            if (BuildConfig.DEBUG) {
                Log.d("TinoDanfeOCR", "parsedRows=${rows.size}")
            }
            when (val mapped = DanfeProductMapper.map(rows, source)) {
                is ProductImportResult.NeedsReview -> mapped.copy(
                    reason = if (mapped.products.isEmpty()) {
                        "Não encontrei linhas de produto legíveis. Fotografe somente a tabela, mais de perto e com boa luz."
                    } else {
                        mapped.reason
                    },
                )
                else -> mapped
            }
        } catch (error: Exception) {
            ProductImportResult.Unavailable(
                reason = "Não consegui ler os produtos desta foto. Tente uma foto mais nítida e iluminada.",
                source = source,
            )
        } finally {
            recognizer.close()
            ocrBitmap.recycle()
            bitmap.recycle()
        }
    }
}

private object DanfeOcrImagePreprocessor {
    fun enhance(source: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val contrast = 1.45f
        val translate = 128f * (1f - contrast)
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        Canvas(enhanced).drawBitmap(
            source,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            },
        )
        return enhanced
    }
}

private object DanfeTextRowParser {
    private val productCode = Regex("^\\d{3,14}$")
    private val numeric = Regex("^\\d+(?:[.,]\\d+)?$")
    private val ncm = Regex("^\\d{8}$")
    private val cfop = Regex("^\\d{4}$")
    private val units = setOf("UN", "UND", "PC", "PÇ", "CX", "FD", "FC", "KG", "G", "LT", "L", "MT", "M", "DZ")
    private val packedUnit = Regex("^(UN|UND|PC|PÇ|CX|FD|FC|KG|G|LT|L|MT|M|DZ)(\\d{2,6})$")

    fun parse(rawText: String, lines: List<com.google.mlkit.vision.text.Text.Line>): List<DanfeTableRow> {
        val lineInputs = if (lines.isNotEmpty()) {
            lines.map { line ->
                OcrLine(
                    text = line.text,
                    confidence = line.confidence ?: 0.82f,
                    bounds = line.boundingBox,
                )
            }
        } else {
            rawText.lines().map { OcrLine(it, 0.70f, null) }
        }
        val groupedRows = groupIntoRows(lineInputs).mapNotNull { row ->
            parseLine(row.text, row.confidence)
        }
        val sequentialRows = parseSequential(rawText)
        return (groupedRows + sequentialRows)
            .distinctBy { row ->
                row.cells[DanfeColumn.PRODUCT_CODE]?.text.orEmpty() +
                    row.cells[DanfeColumn.DESCRIPTION]?.text.orEmpty()
            }
    }

    private fun groupIntoRows(lines: List<OcrLine>): List<OcrRow> {
        val positioned = lines.filter { it.bounds != null }
        if (positioned.size < 2) {
            return lines.map { OcrRow(it.text, it.confidence) }
        }

        val tolerance = positioned
            .mapNotNull { it.bounds?.height()?.toFloat() }
            .average()
            .toFloat()
            .times(0.90f)
            .coerceAtLeast(14f)
        val rows = mutableListOf<MutableList<OcrLine>>()
        positioned.sortedBy { it.bounds!!.centerY() }.forEach { line ->
            val centerY = line.bounds!!.centerY()
            val row = rows.lastOrNull { existing ->
                kotlin.math.abs(existing.first().bounds!!.centerY() - centerY) <= tolerance
            }
            if (row == null) rows += mutableListOf(line) else row += line
        }
        return rows.map { row ->
            OcrRow(
                text = row.sortedBy { it.bounds!!.left }.joinToString(" ") { it.text },
                confidence = row.map { it.confidence }.minOrNull() ?: 0.7f,
            )
        }
    }

    private fun parseLine(raw: String, confidence: Float): DanfeTableRow? {
        val tokens = raw
            .replace('|', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(' ')
            .filter { it.isNotBlank() }
        if (tokens.size < 5) return null
        val codeIndex = tokens.indexOfFirst { productCode.matches(normalizeCode(it)) }
        if (codeIndex !in 0..1) return null

        val unitMatch = tokens.withIndex()
            .drop(codeIndex + 2)
            .mapNotNull { indexed -> findUnit(indexed.index, indexed.value) }
            .firstOrNull() ?: return null
        val unitIndex = unitMatch.index
        val nextQuantity = tokens.getOrNull(unitIndex + 1)?.let(::parseDecimal)
        val packedQuantity = unitMatch.packedQuantity
        val quantity = nextQuantity ?: packedQuantity ?: return null
        val descriptionTokens = tokens.subList(codeIndex + 1, unitIndex)
            .filterNot { numeric.matches(it.replace(".", "")) }
        val description = descriptionTokens.joinToString(" ").trim()
        if (description.isBlank()) return null

        val cells = linkedMapOf(
            DanfeColumn.PRODUCT_CODE to RecognizedCell(tokens[codeIndex], confidence),
            DanfeColumn.DESCRIPTION to RecognizedCell(description, confidence),
            DanfeColumn.UNIT to RecognizedCell(unitMatch.unit, confidence),
            DanfeColumn.QUANTITY to RecognizedCell(
                tokens.getOrNull(unitIndex + 1) ?: packedQuantity!!.toPlainString(),
                confidence,
            ),
        )
        tokens.drop(1).firstOrNull { ncm.matches(it) }?.let {
            cells[DanfeColumn.NCM] = RecognizedCell(it, confidence)
        }
        tokens.drop(1).firstOrNull { cfop.matches(it) }?.let {
            cells[DanfeColumn.CFOP] = RecognizedCell(it, confidence)
        }
        tokens.drop(unitIndex + 2).mapNotNull(::parseDecimal).take(2).forEachIndexed { index, value ->
            val column = if (index == 0) DanfeColumn.UNIT_COST else DanfeColumn.TOTAL
            cells[column] = RecognizedCell(value.toPlainString(), confidence)
        }
        return DanfeTableRow(cells)
    }

    /**
     * Some DANFEs are returned by ML Kit as one OCR line per cell instead of
     * one line per visual row. In that case the bounding-box grouping loses
     * the row relationship. This conservative fallback follows the OCR order:
     * product code -> description -> fiscal cells -> unit -> quantity.
     */
    private fun parseSequential(rawText: String): List<DanfeTableRow> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val rows = mutableListOf<DanfeTableRow>()
        lines.forEachIndexed { index, line ->
            if (!productCode.matches(normalizeCode(line))) return@forEachIndexed
            val descriptionIndex = (index + 1 until (index + 12).coerceAtMost(lines.size))
                .firstOrNull { candidate ->
                    val value = lines[candidate]
                    value.count { it.isLetter() } >= 5 &&
                        !value.contains("DADOS DO PRODUTO", ignoreCase = true) &&
                        !value.contains("NCM", ignoreCase = true)
                } ?: return@forEachIndexed
            val unitIndex = (descriptionIndex + 1 until (descriptionIndex + 10).coerceAtMost(lines.size))
                .firstOrNull { candidate ->
                    findUnit(0, lines[candidate]) != null
                } ?: return@forEachIndexed
            val endIndex = (unitIndex + 2).coerceAtMost(lines.size)
            val synthetic = buildList {
                add(line)
                add(lines[descriptionIndex])
                addAll(lines.subList(descriptionIndex + 1, endIndex))
            }.joinToString(" ")
            parseLine(synthetic, 0.70f)?.let(rows::add)
        }
        return rows
    }

    private fun parseDecimal(value: String): BigDecimal? {
        val normalized = value.replace("R$", "").trim()
        val decimal = when {
            normalized.contains(",") -> normalized.replace(".", "").replace(",", ".")
            normalized.count { it == '.' } > 1 -> normalized.replace(".", "")
            else -> normalized
        }
        return decimal.toBigDecimalOrNull()
    }

    private fun normalizeCode(value: String): String = value
        .trim('.', ',', ':', ';', '|')
        .uppercase()
        .replace('O', '0')

    private fun normalizeUnit(value: String): String = value
        .trim('.', ',', ':', ';', '|')
        .uppercase()
        .replace(".", "")
        .let { unit ->
            when (unit) {
                "FO", "FC" -> "FD"
                else -> unit
            }
        }

    private fun findUnit(index: Int, value: String): UnitMatch? {
        val normalized = normalizeUnit(value)
        if (normalized in units) return UnitMatch(index, normalized, null)
        val packed = packedUnit.matchEntire(normalized) ?: return null
        val quantity = packed.groupValues[2].toBigDecimalOrNull()
        return UnitMatch(index, packed.groupValues[1], quantity)
    }

    private data class OcrLine(
        val text: String,
        val confidence: Float,
        val bounds: Rect?,
    )

    private data class OcrRow(
        val text: String,
        val confidence: Float,
    )

    private data class UnitMatch(
        val index: Int,
        val unit: String,
        val packedQuantity: BigDecimal?,
    )
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
}
