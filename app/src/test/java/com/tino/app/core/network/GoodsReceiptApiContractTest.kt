package com.tino.app.core.network

import com.tino.app.domain.receiving.GoodsReceiptConfirmation
import com.tino.app.domain.receiving.GoodsReceiptDecision
import com.tino.app.domain.receiving.GoodsReceiptDecisionAction
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GoodsReceiptApiContractTest {
    @Test
    fun retrieveUsesFrozenSnakeCaseAndParsesDecimalWithoutDouble() = runBlockingTest {
        val transport = RecordingTransport(
            """
            {
              "document_id":"doc-1",
              "access_key":"53160911510448000171550010000106771000187760",
              "retrieval_status":"SUCCESS",
              "fiscal_status":"AUTHORIZED",
              "item_count":1,
              "error_code":null,
              "retryable":false,
              "preview":{"preview_id":"preview-1","status":"REVIEW_REQUIRED","version":0}
            }
            """.trimIndent(),
        )
        val api = RestGoodsReceiptApi("https://backend.test", transport)

        val document = api.retrieveNfe(
            businessId = "business-1",
            accessKey = "53160911510448000171550010000106771000187760",
            idempotencyKey = "idem-1",
        )

        assertEquals("/api/v1/businesses/business-1/nfe-documents", transport.request.path)
        assertEquals("idem-1", transport.request.headers["Idempotency-Key"])
        assertTrue("X-Tenant-Id" !in transport.request.headers)
        assertTrue("tenant_id" !in transport.request.headers)
        assertTrue(transport.request.body!!.contains("\"access_key\""))
        assertTrue(!transport.request.body!!.contains("accessKey"))
        assertEquals("preview-1", document.preview?.previewId)
    }

    @Test
    fun previewAndConfirmationRetainDecimalWireValues() = runBlockingTest {
        val transport = RecordingTransport(
            """
            {
              "preview_id":"preview-1","document_id":"doc-1","document_number":"15430","series":"0",
              "issuer":{"legal_name":"Fornecedor","trade_name":"Fornecedor"},
              "retrieval_status":"SUCCESS","fiscal_status":"AUTHORIZED","status":"REVIEW_REQUIRED","version":0,
              "summary":{"total_items":1,"matched_items":0,"new_candidate_items":1,"review_required_items":0},
              "items":[{"line_number":1,"description":"Produto","supplier_product_code":"346","gtin":null,
                "resolution_status":"NEW_CANDIDATE","product_id":null,"candidate_name":"Produto",
                "purchase_unit":"KG","purchase_quantity":2.500,"purchase_unit_cost":149.125,
                "product_total":372.8125,"base_unit":null,"conversion_factor":null,"stock_quantity":null,
                "requires_user_action":true}]
            }
            """.trimIndent(),
        )
        val api = RestGoodsReceiptApi("https://backend.test", transport)
        val preview = api.getPreview("business-1", "doc-1")

        assertEquals(BigDecimal("2.500"), preview.items.single().purchaseQuantity)
        assertEquals(BigDecimal("149.125"), preview.items.single().purchaseUnitCost)

        transport.responseBody = """
            {"receipt_id":"receipt-1","status":"CONFIRMED","item_count":1,"items":[
              {"line_number":1,"product_id":"product-1","product_name":"Produto","base_unit":"UN",
               "quantity_added":2.5,"unit_cost":149.125}
            ]}
        """.trimIndent()
        val result = api.confirmGoodsReceipt(
            "business-1",
            "preview-1",
            GoodsReceiptConfirmation(
                previewVersion = 0,
                items = listOf(GoodsReceiptDecision(1, GoodsReceiptDecisionAction.CREATE_PRODUCT, baseUnit = "UN", conversionFactor = BigDecimal("1.25"))),
            ),
            "confirm-1",
        )

        assertEquals(BigDecimal("2.5"), result.items.single().quantityAdded)
        assertTrue(transport.request.body!!.contains("1.25"))
        assertTrue(transport.request.body!!.contains("\"preview_version\""))
    }

    @Test
    fun invalidAccessKeyIsRejectedBeforeTransport() {
        var called = false
        val transport = BackendHttpTransport {
            called = true
            error("transport must not be called")
        }
        assertThrows(IllegalArgumentException::class.java) {
            com.tino.app.domain.receiving.NfeAccessKeyValidator.normalizeAndValidate("123")
        }
        assertTrue(!called)
    }

    @Test
    fun stableErrorCodeAndRetryabilityAreParsedFromWireContract() = runBlockingTest {
        val transport = RecordingTransport(
            responseBody = """
                {"code":"STALE_PREVIEW","message":"preview changed","retryable":false,"correlation_id":"corr-1"}
            """.trimIndent(),
            status = 409,
        )
        val api = RestGoodsReceiptApi("https://backend.test", transport)

        val error = assertThrows(BackendApiException::class.java) {
            runBlockingTest { api.getPreview("business-1", "doc-1") }
        }

        assertEquals(BackendWireErrorCode.STALE_PREVIEW, error.code)
        assertFalse(error.retryable)
        assertEquals("corr-1", error.correlationId)
        Unit
    }

    @Test
    fun unauthorizedResponseIsAuthenticationFailureWithoutInventingErrorCode() = runBlockingTest {
        val transport = RecordingTransport("", status = 401)
        val api = RestGoodsReceiptApi("https://backend.test", transport)

        assertThrows(BackendAuthenticationException::class.java) {
            runBlockingTest { api.getPreview("business-1", "doc-1") }
        }
        Unit
    }

    private class RecordingTransport(
        var responseBody: String,
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
