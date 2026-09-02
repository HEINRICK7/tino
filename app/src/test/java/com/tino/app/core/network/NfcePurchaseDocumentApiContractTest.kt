package com.tino.app.core.network

import com.tino.app.domain.nfce.PurchaseDocument
import com.tino.app.domain.nfce.PurchaseDocumentConfirmation
import com.tino.app.domain.nfce.PurchaseDocumentDecision
import com.tino.app.domain.nfce.PurchaseItem
import com.tino.app.domain.nfce.PurchaseIssuer
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcePurchaseDocumentApiContractTest {
    @Test
    fun previewUsesCanonicalSnakeCaseAndPreservesExactDecimals() = runBlockingTest {
        val transport = RecordingTransport(
            """
            {
              "preview_id":"preview-1","document_id":"document-1","status":"REVIEW_READY","version":0,
              "source":"NFCE","document_type":"NFCE","access_key":"22260831838128000748650120002104021782591975",
              "issued_at":"2026-08-29T08:04:14-03:00",
              "issuer":{"name":"GRUPO VANGUARDA","tax_id":"31838128000748"},
              "items":[{"line_number":1,"external_code":"249886","gtin":null,"description":"Bolo",
                "quantity":1.000,"unit":"UN","unit_price":10.790,"total_price":10.790,
                "match_status":"EXACT_MATCH","matched_product_id":"product-1","candidate_name":"Bolo",
                "base_unit":"UN","match_confidence":1.0000,"requires_user_action":false}],
              "total":10.790,"summary":{"items":1,"matched":1,"new_products":0,"needs_review":0,"purchase_total":10.790},"actions":[]
            }
            """.trimIndent(),
        )
        val api = RestNfcePurchaseDocumentApi(transport)

        val preview = api.createPreview(
            businessId = "business-1",
            document = document(),
            idempotencyKey = "nfce-preview:test",
        )

        assertEquals("/api/v1/businesses/business-1/receiving/purchase-documents/preview", transport.request.path)
        assertEquals("nfce-preview:test", transport.request.headers["Idempotency-Key"])
        assertTrue(transport.request.body!!.contains("\"document_type\":\"NFCE\""))
        assertTrue(transport.request.body!!.contains("\"issued_at\":\"2026-08-29T08:04:14-03:00\""))
        assertTrue(transport.request.body!!.contains("10.790"))
        assertFalse(transport.request.body!!.contains("html"))
        assertFalse(transport.request.body!!.contains("cookie"))
        assertEquals(BigDecimal("10.790"), preview.items.single().unitPrice)
        assertEquals(BigDecimal("10.790"), preview.total)
        assertEquals("GRUPO VANGUARDA", preview.issuer.name)
    }

    @Test
    fun unauthorizedResponseIsNotConvertedIntoAFalsePreview() = runBlockingTest {
        val api = RestNfcePurchaseDocumentApi(RecordingTransport("", status = 401))
        var thrown = false
        try {
            api.createPreview("business-1", document(), "nfce-preview:test")
        } catch (error: BackendAuthenticationException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun confirmationUsesTheVersionedCanonicalDecisionContract() = runBlockingTest {
        val transport = RecordingTransport(
            """{"receipt_id":"receipt-1","status":"CONFIRMED","item_count":1,"items":[]}""",
        )
        val api = RestNfcePurchaseDocumentApi(transport)

        val receipt = api.confirmPreview(
            businessId = "business-1",
            previewId = "preview-1",
            confirmation = PurchaseDocumentConfirmation(
                previewVersion = 0,
                items = listOf(PurchaseDocumentDecision(
                    lineNumber = 1,
                    action = PurchaseDocumentDecision.Action.USE_EXISTING,
                    productId = "product-1",
                    conversionFactor = BigDecimal("1.000"),
                    baseUnit = "UN",
                )),
            ),
            idempotencyKey = "nfce-confirm:preview-1:0",
        )

        assertEquals("/api/v1/businesses/business-1/receiving/purchase-documents/preview-1/confirm", transport.request.path)
        assertEquals("nfce-confirm:preview-1:0", transport.request.headers["Idempotency-Key"])
        assertTrue(transport.request.body!!.contains("\"preview_version\":0"))
        assertTrue(transport.request.body!!.contains("\"conversion_factor\":1.000"))
        assertTrue(transport.request.body!!.contains("\"product_id\":\"product-1\""))
        assertFalse(transport.request.body!!.contains("html"))
        assertEquals("receipt-1", receipt.receiptId)
        assertEquals(1, receipt.itemCount)
    }

    @Test
    fun historyDetailAndInsightsUseTheVersionedReadContracts() = runBlockingTest {
        val historyTransport = RecordingTransport(
            """
            {
              "period":"WEEK","from":"2026-08-24T00:00:00Z","to":"2026-08-31T00:00:00Z",
              "purchases":[{"receipt_id":"receipt-1","confirmed_at":"2026-08-29T11:00:00Z",
                "issuer_name":"GRUPO VANGUARDA","total":10.790,"item_count":1,
                "new_product_count":0,"stock_quantity":1.000}],
              "purchase_count":1,"item_count":1,"new_product_count":0,"total":10.790
            }
            """.trimIndent(),
        )
        val history = RestNfcePurchaseDocumentApi(historyTransport).getHistory("business-1", "WEEK")
        assertEquals("/api/v1/businesses/business-1/receiving/purchase-documents/purchase-history?period=WEEK", historyTransport.request.path)
        assertEquals(BigDecimal("10.790"), history.total)
        assertEquals("receipt-1", history.purchases.single().receiptId)

        val detailTransport = RecordingTransport(
            """
            {
              "receipt_id":"receipt-1","confirmed_at":"2026-08-29T11:00:00Z",
              "issuer_name":"GRUPO VANGUARDA","issuer_tax_id":"31838128000748",
              "access_key":"22260831838128000748650120002104021782591975","total":10.790,
              "items":[{"line_number":1,"product_id":"product-1","description":"Bolo",
                "quantity":1.000,"unit":"UN","unit_price":10.790,"stock_quantity":1.000,
                "match_status":"EXACT_MATCH"}]
            }
            """.trimIndent(),
        )
        val detail = RestNfcePurchaseDocumentApi(detailTransport).getHistoryDetail("business-1", "receipt-1")
        assertEquals("/api/v1/businesses/business-1/receiving/purchase-documents/purchase-history/receipt-1", detailTransport.request.path)
        assertEquals("Bolo", detail.items.single().description)
        assertEquals(BigDecimal("10.790"), detail.items.single().unitPrice)

        val insightTransport = RecordingTransport(
            """
            {"period":"WEEK","insights":[{"type":"COST_CHANGE",
              "message":"O custo médio observado mudou.","evidence_ids":["receipt-1","observation-1"]}]}
            """.trimIndent(),
        )
        val insights = RestNfcePurchaseDocumentApi(insightTransport).getInsights("business-1", "WEEK")
        assertEquals("/api/v1/businesses/business-1/receiving/purchase-documents/purchase-history-insights?period=WEEK", insightTransport.request.path)
        assertEquals(listOf("receipt-1", "observation-1"), insights.single().evidenceIds)
    }

    @Test
    fun historyRejectsAnUnsupportedPeriodBeforeTransport() = runBlockingTest {
        var rejected = false
        try {
            RestNfcePurchaseDocumentApi(RecordingTransport("{}"))
                .getHistory("business-1", "DAY")
        } catch (error: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    private fun document() = PurchaseDocument(
        source = PurchaseDocument.Source.NFCE,
        documentType = PurchaseDocument.DocumentType.NFCE,
        accessKey = "22260831838128000748650120002104021782591975",
        issuedAt = LocalDateTime.of(2026, 8, 29, 8, 4, 14),
        issuer = PurchaseIssuer("GRUPO VANGUARDA", "31838128000748"),
        items = listOf(PurchaseItem(1, "249886", null, "Bolo", BigDecimal("1.000"), "UN", BigDecimal("10.790"), BigDecimal("10.790"))),
        total = BigDecimal("10.790"),
    )

    private class RecordingTransport(
        private val responseBody: String,
        private val status: Int = 200,
    ) : BackendHttpTransport {
        lateinit var request: BackendHttpRequest

        override suspend fun execute(request: BackendHttpRequest): BackendHttpResponse {
            this.request = request
            return BackendHttpResponse(status, responseBody)
        }
    }
}

private fun <T> runBlockingTest(block: suspend () -> T): T = kotlinx.coroutines.runBlocking { block() }
