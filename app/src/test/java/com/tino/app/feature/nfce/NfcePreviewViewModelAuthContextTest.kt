package com.tino.app.feature.nfce

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.network.NfcePurchaseDocumentApi
import com.tino.app.domain.nfce.PurchaseDocument
import com.tino.app.domain.nfce.PurchaseDocumentConfirmation
import com.tino.app.domain.nfce.PurchaseDocumentPreview
import com.tino.app.domain.nfce.PurchaseDocumentPreviewSummary
import com.tino.app.domain.nfce.PurchaseDocumentMatch
import com.tino.app.domain.nfce.PurchaseIssuer
import com.tino.app.domain.nfce.PurchaseItem
import com.tino.app.domain.nfce.PurchaseHistory
import com.tino.app.domain.nfce.PurchaseHistoryDetail
import com.tino.app.domain.nfce.PurchaseInsight
import com.tino.app.domain.nfce.PurchaseReceipt
import com.tino.app.domain.onboarding.BootstrapApi
import com.tino.app.domain.onboarding.BootstrapBusiness
import com.tino.app.domain.onboarding.BootstrapContext
import com.tino.app.domain.onboarding.BootstrapInstallation
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NfcePreviewViewModelAuthContextTest {
    private lateinit var identity: IdentityProvider

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("tino_identity", Context.MODE_PRIVATE).edit().clear().apply()
        identity = IdentityProvider(context)
        identity.setBusinessId("stale-local-business")
    }

    @Test
    fun usesOnlyAuthenticatedReadyBootstrapBusinessAsTenant() = runBlocking {
        val observedBusinessId = AtomicReference<String?>()
        val api = RecordingNfceApi(observedBusinessId)
        val viewModel = NfcePreviewViewModel(
            api = api,
            bootstrapApi = FakeBootstrapApi(readyContext("authenticated-business")),
            identityProvider = identity,
        )

        viewModel.createPreview(document())

        assertEquals("authenticated-business", observedBusinessId.get())
        assertEquals("authenticated-business", identity.current().businessId)
    }

    @Test
    fun refusesNfceOperationWhenBootstrapIsNotReady() = runBlocking {
        val api = RecordingNfceApi(AtomicReference())
        val viewModel = NfcePreviewViewModel(
            api = api,
            bootstrapApi = FakeBootstrapApi(readyContext("authenticated-business", state = "BUSINESS_REQUIRED")),
            identityProvider = identity,
        )

        val error = runCatching { viewModel.createPreview(document()) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("BUSINESS_REQUIRED"))
        assertEquals(null, api.lastBusinessId)
    }

    @Test
    fun refusesReadyContextWhoseInstallationBelongsToAnotherBusiness() = runBlocking {
        val api = RecordingNfceApi(AtomicReference())
        val context = readyContext("authenticated-business").copy(
            installation = BootstrapInstallation("installation", "device", "another-business", "ACTIVE"),
        )
        val viewModel = NfcePreviewViewModel(
            api = api,
            bootstrapApi = FakeBootstrapApi(context),
            identityProvider = identity,
        )

        val error = runCatching { viewModel.createPreview(document()) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("não pertence"))
        assertEquals(null, api.lastBusinessId)
    }

    private fun readyContext(businessId: String, state: String = "READY") = BootstrapContext(
        state = state,
        businesses = listOf(BootstrapBusiness(businessId, "Teste", "RETAIL", "ACTIVE", "OWNER")),
        selectedBusiness = BootstrapBusiness(businessId, "Teste", "RETAIL", "ACTIVE", "OWNER"),
        installation = BootstrapInstallation("installation", "device", businessId, "ACTIVE"),
    )

    private fun document() = PurchaseDocument(
        source = PurchaseDocument.Source.NFCE,
        documentType = PurchaseDocument.DocumentType.NFCE,
        accessKey = "22260831838128000748650120002104021782591975",
        issuedAt = LocalDateTime.of(2026, 8, 1, 12, 0),
        issuer = PurchaseIssuer("Fornecedor", "12345678000199"),
        items = emptyList(),
        total = null,
    )

    private class FakeBootstrapApi(private val context: BootstrapContext) : BootstrapApi {
        override suspend fun bootstrap(requestedBusinessId: String?, installationExternalId: String?) = context
        override suspend fun createBusiness(tradeName: String, vertical: String): BootstrapBusiness = error("unused")
        override suspend fun registerInstallation(businessId: String, installationId: String): BootstrapInstallation = error("unused")
    }

    private class RecordingNfceApi(private val observedBusinessId: AtomicReference<String?>) : NfcePurchaseDocumentApi {
        var lastBusinessId: String? = null

        override suspend fun createPreview(
            businessId: String,
            document: PurchaseDocument,
            idempotencyKey: String,
        ): PurchaseDocumentPreview {
            lastBusinessId = businessId
            observedBusinessId.set(businessId)
            return PurchaseDocumentPreview(
                previewId = "preview",
                documentId = "document",
                status = "READY",
                version = 1,
                source = document.source,
                documentType = document.documentType,
                accessKey = document.accessKey,
                issuedAt = null,
                issuer = document.issuer,
                items = listOf(PurchaseItem(1, null, null, "Produto", null, "UN", null, null)),
                matches = listOf(PurchaseDocumentMatch(1, PurchaseDocumentMatch.Status.NEW_PRODUCT, null, null, "UN", null, false)),
                total = null,
                summary = PurchaseDocumentPreviewSummary(1, 0, 1, 0, null),
            )
        }

        override suspend fun confirmPreview(
            businessId: String,
            previewId: String,
            confirmation: PurchaseDocumentConfirmation,
            idempotencyKey: String,
        ): PurchaseReceipt = error("unused")

        override suspend fun getHistory(businessId: String, period: String): PurchaseHistory = error("unused")
        override suspend fun getHistoryDetail(businessId: String, receiptId: String): PurchaseHistoryDetail = error("unused")
        override suspend fun getInsights(businessId: String, period: String): List<PurchaseInsight> = error("unused")
    }
}
