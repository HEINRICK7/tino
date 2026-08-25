package com.tino.fiscal.core

import java.math.BigDecimal

enum class ProductImportSource {
    DANFE_CAMERA,
    DANFE_IMAGE,
    NFE_XML,
}

/** Common output for camera/OCR and A1/XML adapters. It is not a Room entity. */
data class ImportedProduct(
    val supplierCode: String?,
    val description: String,
    val ncm: String?,
    val cfop: String?,
    val invoiceUnit: String?,
    val invoiceQuantity: BigDecimal?,
    val packageQuantity: Int?,
    val unitCost: BigDecimal?,
    val confidence: Float,
)

data class ProductImportRequest(
    val source: ProductImportSource,
    val evidenceHashSha256: String,
    val payload: ByteArray,
)

sealed interface ProductImportResult {
    data class Success(
        val products: List<ImportedProduct>,
        val source: ProductImportSource,
    ) : ProductImportResult

    data class NeedsReview(
        val reason: String,
        val source: ProductImportSource,
        val products: List<ImportedProduct> = emptyList(),
    ) : ProductImportResult

    data class Unavailable(
        val reason: String,
        val source: ProductImportSource,
    ) : ProductImportResult
}

/** OCR/vision/remote implementations live outside this core contract. */
fun interface ProductImportPort {
    fun extract(request: ProductImportRequest): ProductImportResult
}

data class DocumentImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val evidenceHashSha256: String,
)

/** Vision boundary: local Paddle, remote vision or a test double may implement it. */
fun interface DocumentVisionPort {
    suspend fun extractProducts(image: DocumentImage): ProductImportResult
}

enum class DanfeColumn {
    PRODUCT_CODE,
    DESCRIPTION,
    NCM,
    CST,
    CFOP,
    UNIT,
    QUANTITY,
    UNIT_COST,
    TOTAL,
}

data class RecognizedCell(
    val text: String,
    val confidence: Float,
)

data class DanfeTableRow(
    val cells: Map<DanfeColumn, RecognizedCell>,
)

/**
 * Maps only the DANFE product columns. It deliberately does not resolve
 * products, infer stock, create suppliers or commit a fiscal import.
 */
object DanfeProductMapper {
    private const val REVIEW_CONFIDENCE = 0.75f

    fun map(rows: List<DanfeTableRow>, source: ProductImportSource): ProductImportResult {
        val products = rows.mapNotNull { row ->
            val description = row.cells[DanfeColumn.DESCRIPTION]?.text?.trim().orEmpty()
            if (description.isBlank()) return@mapNotNull null
            ImportedProduct(
                supplierCode = row.text(DanfeColumn.PRODUCT_CODE),
                description = description,
                ncm = row.text(DanfeColumn.NCM),
                cfop = row.text(DanfeColumn.CFOP),
                invoiceUnit = row.text(DanfeColumn.UNIT),
                invoiceQuantity = row.decimal(DanfeColumn.QUANTITY),
                packageQuantity = null,
                unitCost = row.decimal(DanfeColumn.UNIT_COST),
                confidence = row.cells.values.minOfOrNull { it.confidence } ?: 0f,
            )
        }
        if (products.isEmpty()) {
            return ProductImportResult.NeedsReview(
                reason = "Nenhuma linha de produto legível na tabela da DANFE.",
                source = source,
            )
        }
        val lowConfidence = products.any { product ->
            product.invoiceQuantity == null || product.invoiceUnit.isNullOrBlank() ||
                product.confidence < REVIEW_CONFIDENCE
        }
        return if (lowConfidence) {
            ProductImportResult.NeedsReview(
                reason = "Quantidade, unidade ou linha fiscal precisa de conferência.",
                source = source,
                products = products,
            )
        } else {
            ProductImportResult.Success(products = products, source = source)
        }
    }

    private fun DanfeTableRow.text(column: DanfeColumn): String? =
        cells[column]?.text?.trim()?.ifBlank { null }

    private fun DanfeTableRow.decimal(column: DanfeColumn): BigDecimal? =
        text(column)?.let { value ->
            when {
                value.contains(",") -> value.replace(".", "").replace(",", ".")
                value.count { it == '.' } > 1 -> value.replace(".", "")
                else -> value
            }.toBigDecimalOrNull()
        }
}

data class DocumentFrameMetrics(
    val sheetDetected: Boolean,
    val coverageRatio: Float,
    val brightness: Float,
    val sharpness: Float,
    val stableFrameCount: Int,
    val quadrilateralDetected: Boolean = false,
    val geometryStableFrameCount: Int = 0,
)

enum class CaptureUiState {
    SEARCHING_DOCUMENT,
    ADJUST_FRAMING,
    IMPROVE_LIGHT,
    HOLD_STILL,
    READY,
    CAPTURING,
    CAPTURED,
}

sealed interface DocumentCaptureGuidance {
    data object DetectingSheet : DocumentCaptureGuidance
    data object MoveCloser : DocumentCaptureGuidance
    data object MoveFarther : DocumentCaptureGuidance
    data object MoreLight : DocumentCaptureGuidance
    data object HoldSteady : DocumentCaptureGuidance
    data object ReadyToCapture : DocumentCaptureGuidance
}

/**
 * Deterministic, dependency-free gate for deciding whether a high-resolution
 * capture may be triggered. It does not run OCR and never mutates inventory.
 */
object DocumentCaptureQualityGate {
    fun evaluate(metrics: DocumentFrameMetrics): DocumentCaptureGuidance = when {
        !metrics.sheetDetected -> DocumentCaptureGuidance.DetectingSheet
        metrics.coverageRatio < 0.55f -> DocumentCaptureGuidance.MoveCloser
        metrics.coverageRatio > 0.97f -> DocumentCaptureGuidance.MoveFarther
        metrics.brightness < 0.25f -> DocumentCaptureGuidance.MoreLight
        metrics.sharpness < 0.35f -> DocumentCaptureGuidance.HoldSteady
        metrics.stableFrameCount < 3 -> DocumentCaptureGuidance.HoldSteady
        else -> DocumentCaptureGuidance.ReadyToCapture
    }

    fun uiState(metrics: DocumentFrameMetrics): CaptureUiState = when (evaluate(metrics)) {
        DocumentCaptureGuidance.DetectingSheet -> CaptureUiState.SEARCHING_DOCUMENT
        DocumentCaptureGuidance.MoveCloser,
        DocumentCaptureGuidance.MoveFarther -> CaptureUiState.ADJUST_FRAMING
        DocumentCaptureGuidance.MoreLight -> CaptureUiState.IMPROVE_LIGHT
        DocumentCaptureGuidance.HoldSteady -> CaptureUiState.HOLD_STILL
        DocumentCaptureGuidance.ReadyToCapture -> CaptureUiState.READY
    }

    fun stabilityProgress(metrics: DocumentFrameMetrics): Float =
        (metrics.stableFrameCount / 3f).coerceIn(0f, 1f)
}
