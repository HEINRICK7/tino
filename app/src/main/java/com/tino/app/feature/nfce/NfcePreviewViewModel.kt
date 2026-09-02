package com.tino.app.feature.nfce

import androidx.lifecycle.ViewModel
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.network.NfcePurchaseDocumentApi
import com.tino.app.domain.nfce.PurchaseDocument
import com.tino.app.domain.nfce.PurchaseDocumentConfirmation
import com.tino.app.domain.nfce.PurchaseDocumentDecision
import com.tino.app.domain.nfce.PurchaseDocumentMatch
import com.tino.app.domain.nfce.PurchaseDocumentPreview
import com.tino.app.domain.nfce.PurchaseReceipt
import com.tino.app.domain.nfce.PurchaseHistory
import com.tino.app.domain.nfce.PurchaseHistoryDetail
import com.tino.app.domain.nfce.PurchaseInsight
import com.tino.app.domain.onboarding.BootstrapApi
import java.math.BigDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NfcePreviewViewModel @Inject constructor(
    private val api: NfcePurchaseDocumentApi,
    private val bootstrapApi: BootstrapApi,
    private val identityProvider: IdentityProvider,
) : ViewModel() {
    private var readyBusinessId: String? = null

    suspend fun ensureReadyForNfce() {
        authorizedBusinessId()
    }

    suspend fun createPreview(document: PurchaseDocument): PurchaseDocumentPreview {
        val businessId = authorizedBusinessId()
        return api.createPreview(
            businessId = businessId,
            document = document,
            idempotencyKey = "nfce-preview:${document.accessKey}",
        )
    }

    suspend fun confirmPreview(preview: PurchaseDocumentPreview): PurchaseReceipt {
        val businessId = authorizedBusinessId()
        val decisions = preview.items.map { item ->
            val match = preview.matches.firstOrNull { it.lineNumber == item.lineNumber }
                ?: error("A prévia não trouxe a correspondência da linha ${item.lineNumber}.")
            when (match.status) {
                PurchaseDocumentMatch.Status.EXACT_MATCH,
                PurchaseDocumentMatch.Status.HIGH_CONFIDENCE_MATCH ->
                    PurchaseDocumentDecision(
                        lineNumber = item.lineNumber,
                        action = PurchaseDocumentDecision.Action.USE_EXISTING,
                        productId = match.productId ?: error("Produto ausente na linha ${item.lineNumber}."),
                        conversionFactor = BigDecimal.ONE,
                        baseUnit = match.baseUnit ?: item.unit ?: error("Unidade ausente na linha ${item.lineNumber}."),
                    )
                PurchaseDocumentMatch.Status.NEW_PRODUCT ->
                    PurchaseDocumentDecision(
                        lineNumber = item.lineNumber,
                        action = PurchaseDocumentDecision.Action.CREATE_PRODUCT,
                        conversionFactor = BigDecimal.ONE,
                        baseUnit = match.baseUnit ?: item.unit ?: error("Unidade ausente na linha ${item.lineNumber}."),
                    )
                PurchaseDocumentMatch.Status.REVIEW_REQUIRED ->
                    error("É necessário revisar o produto da linha ${item.lineNumber} antes de confirmar.")
            }
        }
        return api.confirmPreview(
            businessId = businessId,
            previewId = preview.previewId,
            confirmation = PurchaseDocumentConfirmation(preview.version, decisions),
            idempotencyKey = "nfce-confirm:${preview.previewId}:${preview.version}",
        )
    }

    suspend fun getPurchaseHistory(period: String): PurchaseHistory {
        val businessId = authorizedBusinessId()
        return api.getHistory(businessId, period)
    }

    suspend fun getPurchaseHistoryDetail(receiptId: String): PurchaseHistoryDetail {
        val businessId = authorizedBusinessId()
        return api.getHistoryDetail(businessId, receiptId)
    }

    suspend fun getPurchaseInsights(period: String): List<PurchaseInsight> {
        val businessId = authorizedBusinessId()
        return api.getInsights(businessId, period)
    }

    /**
     * The local identity can contain a stale business id after reinstall or
     * account changes. It is only a bootstrap hint; the authenticated READY
     * context is the sole tenant authority for NFC-e operations.
     */
    private suspend fun authorizedBusinessId(): String {
        readyBusinessId?.let { return it }
        val localIdentity = identityProvider.current()
        val context = bootstrapApi.bootstrap(
            requestedBusinessId = localIdentity.businessId,
            installationExternalId = localIdentity.installationId,
        )
        require(context.state == "READY") {
            "O comércio não está pronto para entrada de NFC-e (${context.state})."
        }
        val business = context.selectedBusiness
            ?: error("O bootstrap READY não retornou um comércio selecionado.")
        val installation = context.installation
            ?: error("O bootstrap READY não retornou uma instalação ativa.")
        require(installation.businessId == business.id) {
            "A instalação autenticada não pertence ao comércio selecionado."
        }
        identityProvider.setBusinessId(business.id)
        readyBusinessId = business.id
        return business.id
    }
}
