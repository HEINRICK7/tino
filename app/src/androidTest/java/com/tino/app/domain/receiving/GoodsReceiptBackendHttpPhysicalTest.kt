package com.tino.app.domain.receiving

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.network.RestGoodsReceiptApi
import com.tino.app.core.network.UrlConnectionBackendTransport
import com.tino.app.core.security.SecureTokenStore
import java.math.BigDecimal
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Optional device E2E against the running local backend; credentials arrive only as test args. */
@RunWith(AndroidJUnit4::class)
class GoodsReceiptBackendHttpPhysicalTest {
    private lateinit var database: TinoDatabase
    private lateinit var tokenStore: SecureTokenStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tokenStore = SecureTokenStore(context)
    }

    @After
    fun tearDown() {
        tokenStore.clear()
        database.close()
    }

    @Test
    fun authenticatedBackendFlowRetrievesPreviewsConfirmsAndReconciles() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val baseUrl = args.getString("goods_receipt_backend_url")
        val accessToken = args.getString("goods_receipt_access_token")
        val businessId = args.getString("goods_receipt_business_id")
        assumeTrue("Backend E2E arguments are not configured", !baseUrl.isNullOrBlank() && !accessToken.isNullOrBlank() && !businessId.isNullOrBlank())
        val backendUrl = requireNotNull(baseUrl)
        val token = requireNotNull(accessToken)
        val backendBusinessId = requireNotNull(businessId)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("tino_identity", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("store_id", backendBusinessId)
            .apply()
        tokenStore.save(token)
        val tls = insecureTestTls()
        val api = RestGoodsReceiptApi(
            baseUrl = backendUrl,
            transport = UrlConnectionBackendTransport(
                baseUrl = backendUrl,
                tokenStore = tokenStore,
                testSslSocketFactory = tls.socketFactory,
                testHostnameVerifier = HostnameVerifier { _, _ -> true },
            ),
        )
        val repository = GoodsReceiptRepository(
            api = api,
            database = database,
            productDao = database.productDao(),
            operations = database.goodsReceiptOperationDao(),
            remoteReceipts = database.remoteGoodsReceiptDao(),
            productMappings = database.remoteProductMappingDao(),
            identityProvider = IdentityProvider(context),
        )

        val document = repository.retrieve(ACCESS_KEY)
        assertEquals(NfeRetrievalStatus.SUCCESS, document.retrievalStatus)
        val preview = repository.getPreview(document.documentId)
        assertEquals(FiscalStatus.AUTHORIZED, preview.fiscalStatus)
        val item = preview.items.single()
        val baseUnit = item.baseUnit ?: item.purchaseUnit
        val result = repository.confirm(
            preview,
            GoodsReceiptConfirmation(
                previewVersion = preview.version,
                items = listOf(
                    GoodsReceiptDecision(
                        lineNumber = item.lineNumber,
                        action = if (item.productId == null) GoodsReceiptDecisionAction.CREATE_PRODUCT else GoodsReceiptDecisionAction.USE_EXISTING,
                        productId = item.productId,
                        baseUnit = baseUnit,
                        conversionFactor = if (item.purchaseUnit == baseUnit) null else BigDecimal.ONE,
                    ),
                ),
            ),
        )
        val reconciled = repository.reconcile(result.receiptId)

        assertEquals(GoodsReceiptStatus.CONFIRMED, result.status)
        assertEquals(result.receiptId, reconciled.receiptId)
        assertEquals(result.items.single().quantityAdded.toPlainString(), database.remoteGoodsReceiptDao().items(result.receiptId).single().quantityAdded)
        assertTrue(database.stockMovementDao().all().isEmpty())
        assertTrue(database.domainEventDao().all().isEmpty())
    }

    private fun insecureTestTls(): SSLContext = SSLContext.getInstance("TLS").apply {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        })
        init(null, trustAll, SecureRandom())
    }

    private companion object {
        const val ACCESS_KEY = "53160911510448000171550010000106771000187760"
    }
}
