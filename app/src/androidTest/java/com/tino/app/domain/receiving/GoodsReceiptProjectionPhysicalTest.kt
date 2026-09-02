package com.tino.app.domain.receiving

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.network.GoodsReceiptApi
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Device-level proof that a remote receipt is projected without a second local stock mutation. */
@RunWith(AndroidJUnit4::class)
class GoodsReceiptProjectionPhysicalTest {
    private lateinit var database: TinoDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TinoDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun remoteConfirmationPreservesDecimalAndLeavesManualMovementTablesUntouched() = runBlocking {
        val repository = GoodsReceiptRepository(
            api = PhysicalGoodsReceiptApi,
            database = database,
            productDao = database.productDao(),
            operations = database.goodsReceiptOperationDao(),
            remoteReceipts = database.remoteGoodsReceiptDao(),
            productMappings = database.remoteProductMappingDao(),
            identityProvider = IdentityProvider(ApplicationProvider.getApplicationContext()),
        )

        val result = repository.confirm(
            preview = preview,
            confirmation = GoodsReceiptConfirmation(
                previewVersion = 0,
                items = listOf(
                    GoodsReceiptDecision(
                        lineNumber = 1,
                        action = GoodsReceiptDecisionAction.CREATE_PRODUCT,
                        baseUnit = "KG",
                    ),
                ),
            ),
        )

        assertEquals("physical-receipt", result.receiptId)
        assertEquals("2.5", database.remoteGoodsReceiptDao().items("physical-receipt").single().quantityAdded)
        assertTrue(database.stockMovementDao().all().isEmpty())
        assertTrue(database.domainEventDao().all().isEmpty())
    }

    private val preview = GoodsReceiptPreview(
        previewId = "physical-preview",
        documentId = "physical-document",
        documentNumber = "1",
        series = "1",
        issuer = null,
        retrievalStatus = NfeRetrievalStatus.SUCCESS,
        fiscalStatus = FiscalStatus.AUTHORIZED,
        status = GoodsReceiptPreviewStatus.REVIEW_REQUIRED,
        version = 0,
        summary = GoodsReceiptPreviewSummary(1, 0, 1, 0),
        items = listOf(
            GoodsReceiptPreviewItem(
                lineNumber = 1,
                description = "Produto físico",
                supplierProductCode = null,
                gtin = null,
                resolutionStatus = ProductResolutionStatus.NEW_CANDIDATE,
                productId = null,
                candidateName = "Produto físico",
                purchaseUnit = "KG",
                purchaseQuantity = BigDecimal("2.5"),
                purchaseUnitCost = BigDecimal("149.125"),
                productTotal = BigDecimal("372.8125"),
                baseUnit = null,
                conversionFactor = null,
                stockQuantity = null,
                requiresUserAction = true,
            ),
        ),
    )
}

private object PhysicalGoodsReceiptApi : GoodsReceiptApi {
    override suspend fun retrieveNfe(businessId: String, accessKey: String, idempotencyKey: String): NfeDocumentState = error("unused")
    override suspend fun getNfeDocument(businessId: String, documentId: String): NfeDocumentState = error("unused")
    override suspend fun getPreview(businessId: String, documentId: String): GoodsReceiptPreview = error("unused")
    override suspend fun reprocessNfe(businessId: String, documentId: String, idempotencyKey: String): NfeDocumentState = error("unused")
    override suspend fun searchProducts(businessId: String, query: String?, gtin: String?): List<ProductSearchItem> = error("unused")
    override suspend fun confirmGoodsReceipt(
        businessId: String,
        previewId: String,
        confirmation: GoodsReceiptConfirmation,
        idempotencyKey: String,
    ): GoodsReceiptResult = GoodsReceiptResult(
        receiptId = "physical-receipt",
        status = GoodsReceiptStatus.CONFIRMED,
        itemCount = 1,
        items = listOf(
            GoodsReceiptItemResult(
                lineNumber = 1,
                productId = "physical-product",
                productName = "Produto físico",
                baseUnit = "KG",
                quantityAdded = BigDecimal("2.5"),
                unitCost = BigDecimal("149.125"),
            ),
        ),
    )

    override suspend fun getGoodsReceipt(businessId: String, receiptId: String): GoodsReceiptResult = error("unused")
}
