package com.tino.app.feature.nfce

import com.tino.app.domain.nfce.PurchaseDocument
import com.tino.app.domain.nfce.PurchaseDocumentMatch
import com.tino.app.domain.nfce.PurchaseDocumentPreview
import com.tino.app.domain.nfce.PurchaseDocumentPreviewSummary
import com.tino.app.domain.nfce.PurchaseIssuer
import com.tino.app.domain.nfce.PurchaseItem
import java.math.BigDecimal
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NfceMascotExplanationTest {
    @Test
    fun explanationContainsOnlyTheBackendPreviewPlan() {
        val explanation = nfcePreviewExplanation(preview())

        assertEquals(
            listOf(
                "Encontrei 6 produtos nessa compra.",
                "4 já estavam cadastrados e serão atualizados no estoque.",
                "2 são novos e serão cadastrados.",
                "Preciso confirmar 1 produto(s) antes de terminar.",
            ),
            explanation,
        )
    }

    @Test
    fun explanationDoesNotAskForReviewWhenBackendSaysThereIsNone() {
        val explanation = nfcePreviewExplanation(preview(needsReview = 0))

        assertEquals(
            listOf(
                "Encontrei 6 produtos nessa compra.",
                "4 já estavam cadastrados e serão atualizados no estoque.",
                "2 são novos e serão cadastrados.",
            ),
            explanation,
        )
    }

    private fun preview(needsReview: Int = 1) = PurchaseDocumentPreview(
        previewId = "preview-1",
        documentId = "document-1",
        status = "REVIEW_READY",
        version = 0,
        source = PurchaseDocument.Source.NFCE,
        documentType = PurchaseDocument.DocumentType.NFCE,
        accessKey = "22260831838128000748650120002104021782591975",
        issuedAt = OffsetDateTime.parse("2026-08-29T08:04:14-03:00"),
        issuer = PurchaseIssuer("GRUPO VANGUARDA", "31838128000748"),
        items = listOf(PurchaseItem(1, "249886", null, "Produto", BigDecimal.ONE, "UN", BigDecimal.ONE, BigDecimal.ONE)),
        matches = listOf(PurchaseDocumentMatch(1, PurchaseDocumentMatch.Status.REVIEW_REQUIRED, null, null, "UN", null, true)),
        total = BigDecimal("65.11"),
        summary = PurchaseDocumentPreviewSummary(6, 4, 2, needsReview, BigDecimal("65.11")),
    )
}
