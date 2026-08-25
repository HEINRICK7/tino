package com.tino.fiscal.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentCaptureQualityGateTest {
    @Test
    fun missingDocumentTakesPriorityOverOtherQualitySignals() {
        val guidance = DocumentCaptureQualityGate.evaluate(
            DocumentFrameMetrics(
                sheetDetected = false,
                coverageRatio = 0.20f,
                brightness = 0.10f,
                sharpness = 0.10f,
                stableFrameCount = 0,
            ),
        )

        assertEquals(DocumentCaptureGuidance.DetectingSheet, guidance)
    }

    @Test
    fun readyRequiresBrightnessSharpnessAndStability() {
        val guidance = DocumentCaptureQualityGate.evaluate(
            DocumentFrameMetrics(
                sheetDetected = true,
                coverageRatio = 0.72f,
                brightness = 0.70f,
                sharpness = 0.80f,
                stableFrameCount = 3,
            ),
        )

        assertEquals(DocumentCaptureGuidance.ReadyToCapture, guidance)
        assertEquals(CaptureUiState.READY, DocumentCaptureQualityGate.uiState(
            DocumentFrameMetrics(true, 0.72f, 0.70f, 0.80f, 3),
        ))
    }

    @Test
    fun tooMuchCoverageAsksUserToMoveAway() {
        val guidance = DocumentCaptureQualityGate.evaluate(
            DocumentFrameMetrics(true, 0.99f, 0.70f, 0.80f, 3),
        )

        assertEquals(DocumentCaptureGuidance.MoveFarther, guidance)
    }
}
