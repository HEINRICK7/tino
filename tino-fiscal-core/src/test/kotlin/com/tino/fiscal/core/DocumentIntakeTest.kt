package com.tino.fiscal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import java.math.BigDecimal

class DocumentIntakeTest {
    @Test
    fun qualityGateRequestsGuidanceBeforeCapture() {
        assertEquals(
            DocumentCaptureGuidance.DetectingSheet,
            DocumentCaptureQualityGate.evaluate(DocumentFrameMetrics(false, 0.4f, 0.8f, 0.8f, 4)),
        )
        assertEquals(
            DocumentCaptureGuidance.MoveCloser,
            DocumentCaptureQualityGate.evaluate(DocumentFrameMetrics(true, 0.4f, 0.8f, 0.8f, 4)),
        )
        assertEquals(
            DocumentCaptureGuidance.MoreLight,
            DocumentCaptureQualityGate.evaluate(DocumentFrameMetrics(true, 0.7f, 0.1f, 0.8f, 4)),
        )
        assertEquals(
            DocumentCaptureGuidance.HoldSteady,
            DocumentCaptureQualityGate.evaluate(DocumentFrameMetrics(true, 0.7f, 0.8f, 0.2f, 4)),
        )
    }

    @Test
    fun qualityGateOnlyReadyAfterStableSharpFrame() {
        val metrics = DocumentFrameMetrics(
            sheetDetected = true,
            coverageRatio = 0.76f,
            brightness = 0.72f,
            sharpness = 0.81f,
            stableFrameCount = 3,
        )
        assertEquals(DocumentCaptureGuidance.ReadyToCapture, DocumentCaptureQualityGate.evaluate(metrics))
    }

    @Test
    fun importedProductContractDoesNotDependOnRoomOrOcr() {
        val product = ImportedProduct(
            supplierCode = "CAF001",
            description = "Café Maratá 250g",
            ncm = "09012100",
            cfop = "2102",
            invoiceUnit = "FD",
            invoiceQuantity = BigDecimal("6"),
            packageQuantity = 20,
            unitCost = BigDecimal("6.20"),
            confidence = 0.94f,
        )
        val result: ProductImportResult = ProductImportResult.Success(
            products = listOf(product),
            source = ProductImportSource.DANFE_CAMERA,
        )
        val success = assertIs<ProductImportResult.Success>(result)
        assertEquals("Café Maratá 250g", success.products.single().description)
        assertEquals(ProductImportSource.DANFE_CAMERA, success.source)
    }

    @Test
    fun danfeMapperMapsOnlyProductColumns() {
        val result = DanfeProductMapper.map(
            rows = listOf(
                DanfeTableRow(
                    mapOf(
                        DanfeColumn.PRODUCT_CODE to RecognizedCell("1050", 0.99f),
                        DanfeColumn.DESCRIPTION to RecognizedCell("KIFLOCAO DE MILHO 20X500G", 0.96f),
                        DanfeColumn.NCM to RecognizedCell("11041900", 0.98f),
                        DanfeColumn.CST to RecognizedCell("040", 0.97f),
                        DanfeColumn.CFOP to RecognizedCell("5101", 0.96f),
                        DanfeColumn.UNIT to RecognizedCell("FD", 0.98f),
                        DanfeColumn.QUANTITY to RecognizedCell("6", 0.95f),
                        DanfeColumn.UNIT_COST to RecognizedCell("8,70", 0.94f),
                        DanfeColumn.TOTAL to RecognizedCell("52,20", 0.94f),
                    ),
                ),
            ),
            source = ProductImportSource.DANFE_CAMERA,
        )
        val success = assertIs<ProductImportResult.Success>(result)
        val product = success.products.single()
        assertEquals("1050", product.supplierCode)
        assertEquals(BigDecimal("6"), product.invoiceQuantity)
        assertEquals(BigDecimal("8.70"), product.unitCost)
        assertEquals(null, product.packageQuantity)
    }

    @Test
    fun lowQuantityConfidenceBecomesNeedsReviewWithoutDroppingExtractedProduct() {
        val result = DanfeProductMapper.map(
            rows = listOf(
                DanfeTableRow(
                    mapOf(
                        DanfeColumn.DESCRIPTION to RecognizedCell("BISC KIKOS ROSQUINHA LEITE 12X300G", 0.92f),
                        DanfeColumn.UNIT to RecognizedCell("CX", 0.91f),
                        DanfeColumn.QUANTITY to RecognizedCell("1", 0.42f),
                    ),
                ),
            ),
            source = ProductImportSource.DANFE_CAMERA,
        )
        val review = assertIs<ProductImportResult.NeedsReview>(result)
        assertEquals(1, review.products.size)
        assertEquals("CX", review.products.single().invoiceUnit)
        assertEquals(BigDecimal.ONE, review.products.single().invoiceQuantity)
    }

    @Test
    fun captureUiStateAndProgressFollowQualityGate() {
        val metrics = DocumentFrameMetrics(true, 0.76f, 0.72f, 0.81f, 2)
        assertEquals(CaptureUiState.HOLD_STILL, DocumentCaptureQualityGate.uiState(metrics))
        assertEquals(2f / 3f, DocumentCaptureQualityGate.stabilityProgress(metrics))

        val ready = metrics.copy(stableFrameCount = 3)
        assertEquals(CaptureUiState.READY, DocumentCaptureQualityGate.uiState(ready))
        assertEquals(1f, DocumentCaptureQualityGate.stabilityProgress(ready))
    }
}
