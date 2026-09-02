package com.tino.app.domain.receiving

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.network.GoodsReceiptApi
import com.tino.app.core.network.BackendTransportException
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class GoodsReceiptRepositoryTest {
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
    fun confirmedRemoteReceiptIsProjectedExactlyOnceWithoutLocalMovement() = runBlocking {
        val observedKey = AtomicReference<String>()
        val api = FakeGoodsReceiptApi(observedKey)
        val repository = GoodsReceiptRepository(
            api = api,
            database = database,
            productDao = database.productDao(),
            operations = database.goodsReceiptOperationDao(),
            remoteReceipts = database.remoteGoodsReceiptDao(),
            productMappings = database.remoteProductMappingDao(),
            identityProvider = testIdentity(),
        )
        val preview = preview()
        val confirmation = GoodsReceiptConfirmation(
            previewVersion = 0,
            items = listOf(GoodsReceiptDecision(1, GoodsReceiptDecisionAction.CREATE_PRODUCT, baseUnit = "KG")),
        )

        val first = repository.confirm(preview, confirmation)
        val firstKey = observedKey.get()
        val second = repository.confirm(preview, confirmation)

        assertEquals(first.receiptId, second.receiptId)
        assertEquals(firstKey, observedKey.get())
        assertEquals("2.5", database.remoteGoodsReceiptDao().items("receipt-1").single().quantityAdded)
        assertEquals(0, database.stockMovementDao().all().size)
        assertEquals(0, database.domainEventDao().all().size)
        assertTrue(database.productDao().findById("remote-product-1") != null)
    }

    @Test
    fun pendingConfirmationReusesPersistedKeyAndPayloadAfterRepositoryRecreation() = runBlocking {
        val observedKeys = mutableListOf<String>()
        val api = FakeGoodsReceiptApi(
            observedKey = AtomicReference(),
            failFirstConfirmation = true,
            observedKeys = observedKeys,
        )
        val firstRepository = repository(api)
        val preview = preview()
        val confirmation = confirmation()

        val failure = runCatching { firstRepository.confirm(preview, confirmation) }.exceptionOrNull()
        assertTrue(failure is BackendTransportException)

        val secondRepository = repository(api)
        val result = secondRepository.retryPendingConfirmation(preview.previewId)

        assertEquals("receipt-1", result.receiptId)
        assertEquals(2, api.confirmationCalls)
        assertEquals(2, observedKeys.size)
        assertEquals(observedKeys[0], observedKeys[1])
        assertEquals("2.5", database.remoteGoodsReceiptDao().items("receipt-1").single().quantityAdded)
    }

    private fun repository(api: GoodsReceiptApi) = GoodsReceiptRepository(
        api = api,
        database = database,
        productDao = database.productDao(),
        operations = database.goodsReceiptOperationDao(),
        remoteReceipts = database.remoteGoodsReceiptDao(),
        productMappings = database.remoteProductMappingDao(),
        identityProvider = testIdentity(),
    )

    private fun testIdentity() = IdentityProvider(ApplicationProvider.getApplicationContext<Context>()).also {
        it.setBusinessId("business-test")
    }

    private fun confirmation() = GoodsReceiptConfirmation(
        previewVersion = 0,
        items = listOf(GoodsReceiptDecision(1, GoodsReceiptDecisionAction.CREATE_PRODUCT, baseUnit = "KG")),
    )

    private fun preview() = GoodsReceiptPreview(
        previewId = "preview-1",
        documentId = "document-1",
        documentNumber = "15430",
        series = "0",
        issuer = GoodsReceiptPreviewIssuer("Fornecedor", null),
        retrievalStatus = NfeRetrievalStatus.SUCCESS,
        fiscalStatus = FiscalStatus.AUTHORIZED,
        status = GoodsReceiptPreviewStatus.REVIEW_REQUIRED,
        version = 0,
        summary = GoodsReceiptPreviewSummary(1, 0, 1, 0),
        items = listOf(
            GoodsReceiptPreviewItem(
                lineNumber = 1,
                description = "Produto",
                supplierProductCode = "346",
                gtin = null,
                resolutionStatus = ProductResolutionStatus.NEW_CANDIDATE,
                productId = null,
                candidateName = "Produto",
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

private class FakeGoodsReceiptApi(private val observedKey: AtomicReference<String>) : GoodsReceiptApi {
    var confirmationCalls: Int = 0
    var failFirstConfirmation: Boolean = false
    var observedKeys: MutableList<String>? = null

    constructor(
        observedKey: AtomicReference<String>,
        failFirstConfirmation: Boolean,
        observedKeys: MutableList<String>,
    ) : this(observedKey) {
        this.failFirstConfirmation = failFirstConfirmation
        this.observedKeys = observedKeys
    }

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
    ): GoodsReceiptResult {
        confirmationCalls += 1
        observedKey.set(idempotencyKey)
        observedKeys?.add(idempotencyKey)
        if (failFirstConfirmation && confirmationCalls == 1) {
            throw BackendTransportException("timeout", retryable = true)
        }
        return GoodsReceiptResult(
            receiptId = "receipt-1",
            status = GoodsReceiptStatus.CONFIRMED,
            itemCount = 1,
            items = listOf(GoodsReceiptItemResult(1, "remote-product-1", "Produto", "KG", BigDecimal("2.5"), BigDecimal("149.125"))),
        )
    }

    override suspend fun getGoodsReceipt(businessId: String, receiptId: String): GoodsReceiptResult = error("unused")
}
