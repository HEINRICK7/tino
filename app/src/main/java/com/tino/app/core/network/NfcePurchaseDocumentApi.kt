package com.tino.app.core.network

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonParser
import com.tino.app.domain.nfce.PurchaseDocument
import com.tino.app.domain.nfce.PurchaseDocumentPreview
import com.tino.app.domain.nfce.PurchaseDocumentPreviewSummary
import com.tino.app.domain.nfce.PurchaseDocumentMatch
import com.tino.app.domain.nfce.PurchaseDocumentConfirmation
import com.tino.app.domain.nfce.PurchaseReceipt
import com.tino.app.domain.nfce.PurchaseHistory
import com.tino.app.domain.nfce.PurchaseHistoryDetail
import com.tino.app.domain.nfce.PurchaseHistoryEntry
import com.tino.app.domain.nfce.PurchaseHistoryItem
import com.tino.app.domain.nfce.PurchaseInsight
import com.tino.app.domain.nfce.PurchaseItem
import com.tino.app.domain.nfce.PurchaseIssuer
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

interface NfcePurchaseDocumentApi {
    suspend fun createPreview(
        businessId: String,
        document: PurchaseDocument,
        idempotencyKey: String,
    ): PurchaseDocumentPreview

    suspend fun confirmPreview(
        businessId: String,
        previewId: String,
        confirmation: PurchaseDocumentConfirmation,
        idempotencyKey: String,
    ): PurchaseReceipt

    suspend fun getHistory(businessId: String, period: String): PurchaseHistory
    suspend fun getHistoryDetail(businessId: String, receiptId: String): PurchaseHistoryDetail
    suspend fun getInsights(businessId: String, period: String): List<PurchaseInsight>
}

class RestNfcePurchaseDocumentApi(
    private val transport: BackendHttpTransport,
) : NfcePurchaseDocumentApi {
    override suspend fun createPreview(
        businessId: String,
        document: PurchaseDocument,
        idempotencyKey: String,
    ): PurchaseDocumentPreview {
        require(businessId.isNotBlank()) { "businessId é obrigatório." }
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= 200) {
            "Idempotency-Key inválida."
        }
        val response = transport.execute(
            BackendHttpRequest(
                method = "POST",
                path = "/api/v1/businesses/$businessId/receiving/purchase-documents/preview",
                headers = mapOf("Idempotency-Key" to idempotencyKey),
                body = document.toJson().toString(),
            ),
        )
        if (response.status == 401) throw BackendAuthenticationException("A sessão do TINO expirou ou não está autorizada.")
        if (response.status !in 200..299) throw response.toBackendException()
        val root = JsonParser.parseString(response.body.ifBlank { "{}" })
        require(root.isJsonObject) { "Resposta de preview NFC-e inválida." }
        return root.asJsonObject.toPurchaseDocumentPreview()
    }

    override suspend fun confirmPreview(
        businessId: String,
        previewId: String,
        confirmation: PurchaseDocumentConfirmation,
        idempotencyKey: String,
    ): PurchaseReceipt {
        require(businessId.isNotBlank()) { "businessId é obrigatório." }
        require(previewId.isNotBlank()) { "previewId é obrigatório." }
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= 200) { "Idempotency-Key inválida." }
        val response = transport.execute(
            BackendHttpRequest(
                method = "POST",
                path = "/api/v1/businesses/$businessId/receiving/purchase-documents/$previewId/confirm",
                headers = mapOf("Idempotency-Key" to idempotencyKey),
                body = confirmation.toJson().toString(),
            ),
        )
        if (response.status == 401) throw BackendAuthenticationException("A sessão do TINO expirou ou não está autorizada.")
        if (response.status !in 200..299) throw response.toBackendException()
        val root = JsonParser.parseString(response.body.ifBlank { "{}" })
        require(root.isJsonObject) { "Resposta de confirmação NFC-e inválida." }
        return root.asJsonObject.toPurchaseReceipt()
    }

    override suspend fun getHistory(businessId: String, period: String): PurchaseHistory {
        validateHistoryRequest(businessId, period)
        return executeJson("/api/v1/businesses/$businessId/receiving/purchase-documents/purchase-history?period=$period")
            .toPurchaseHistory()
    }

    override suspend fun getHistoryDetail(businessId: String, receiptId: String): PurchaseHistoryDetail {
        require(businessId.isNotBlank()) { "businessId é obrigatório." }
        require(receiptId.isNotBlank()) { "receiptId é obrigatório." }
        return executeJson("/api/v1/businesses/$businessId/receiving/purchase-documents/purchase-history/$receiptId")
            .toPurchaseHistoryDetail()
    }

    override suspend fun getInsights(businessId: String, period: String): List<PurchaseInsight> {
        validateHistoryRequest(businessId, period)
        return executeJson("/api/v1/businesses/$businessId/receiving/purchase-documents/purchase-history-insights?period=$period")
            .getAsJsonArray("insights").map { it.asJsonObject.toPurchaseInsight() }
    }

    private suspend fun executeJson(path: String): JsonObject {
        val response = transport.execute(BackendHttpRequest(method = "GET", path = path))
        if (response.status == 401) throw BackendAuthenticationException("A sessão do TINO expirou ou não está autorizada.")
        if (response.status !in 200..299) throw response.toBackendException()
        val root = JsonParser.parseString(response.body.ifBlank { "{}" })
        require(root.isJsonObject) { "Resposta do histórico NFC-e inválida." }
        return root.asJsonObject
    }

    private fun validateHistoryRequest(businessId: String, period: String) {
        require(businessId.isNotBlank()) { "businessId é obrigatório." }
        require(period in setOf("WEEK", "MONTH", "YEAR")) { "Período de histórico inválido." }
    }
}

class UnavailableNfcePurchaseDocumentApi : NfcePurchaseDocumentApi {
    override suspend fun createPreview(
        businessId: String,
        document: PurchaseDocument,
        idempotencyKey: String,
    ): PurchaseDocumentPreview = throw BackendTransportException(
        "A entrada inteligente de NFC-e ainda não está configurada.",
        retryable = false,
    )

    override suspend fun confirmPreview(
        businessId: String,
        previewId: String,
        confirmation: PurchaseDocumentConfirmation,
        idempotencyKey: String,
    ): PurchaseReceipt = throw BackendTransportException(
        "A entrada inteligente de NFC-e ainda não está configurada.",
        retryable = false,
    )

    override suspend fun getHistory(businessId: String, period: String): PurchaseHistory = unavailableHistory()
    override suspend fun getHistoryDetail(businessId: String, receiptId: String): PurchaseHistoryDetail = unavailableHistory()
    override suspend fun getInsights(businessId: String, period: String): List<PurchaseInsight> = unavailableHistory()

    private fun <T> unavailableHistory(): T = throw BackendTransportException(
        "A entrada inteligente de NFC-e ainda não está configurada.", retryable = false,
    )
}

private fun PurchaseDocumentConfirmation.toJson(): JsonObject = JsonObject().apply {
    addProperty("preview_version", previewVersion)
    add("items", JsonArray().also { array -> items.forEach { decision ->
        array.add(JsonObject().apply {
            addProperty("line_number", decision.lineNumber)
            addProperty("action", decision.action.name)
            decision.productId?.let { addProperty("product_id", it) } ?: add("product_id", JsonNull.INSTANCE)
            decision.conversionFactor?.let { add("conversion_factor", JsonPrimitive(it)) } ?: add("conversion_factor", JsonNull.INSTANCE)
            decision.baseUnit?.let { addProperty("base_unit", it) } ?: add("base_unit", JsonNull.INSTANCE)
        })
    } })
}

private fun JsonObject.toPurchaseReceipt(): PurchaseReceipt = PurchaseReceipt(
    receiptId = requiredString("receipt_id"),
    status = requiredString("status"),
    itemCount = requiredInt("item_count"),
)

private fun JsonObject.toPurchaseHistory(): PurchaseHistory = PurchaseHistory(
    period = requiredString("period"),
    from = OffsetDateTime.parse(requiredString("from")),
    to = OffsetDateTime.parse(requiredString("to")),
    purchases = getAsJsonArray("purchases").map { it.asJsonObject.toPurchaseHistoryEntry() },
    purchaseCount = requiredInt("purchase_count"),
    itemCount = requiredInt("item_count"),
    newProductCount = requiredInt("new_product_count"),
    total = optionalDecimal("total") ?: BigDecimal.ZERO,
)

private fun JsonObject.toPurchaseHistoryEntry(): PurchaseHistoryEntry = PurchaseHistoryEntry(
    receiptId = requiredString("receipt_id"),
    confirmedAt = OffsetDateTime.parse(requiredString("confirmed_at")),
    issuerName = optionalString("issuer_name"),
    total = optionalDecimal("total"),
    itemCount = requiredInt("item_count"),
    newProductCount = requiredInt("new_product_count"),
    stockQuantity = optionalDecimal("stock_quantity"),
)

private fun JsonObject.toPurchaseHistoryDetail(): PurchaseHistoryDetail = PurchaseHistoryDetail(
    receiptId = requiredString("receipt_id"),
    confirmedAt = OffsetDateTime.parse(requiredString("confirmed_at")),
    issuerName = optionalString("issuer_name"),
    issuerTaxId = optionalString("issuer_tax_id"),
    accessKey = requiredString("access_key"),
    total = optionalDecimal("total"),
    items = getAsJsonArray("items").map { it.asJsonObject.toPurchaseHistoryItem() },
)

private fun JsonObject.toPurchaseHistoryItem(): PurchaseHistoryItem = PurchaseHistoryItem(
    lineNumber = requiredInt("line_number"),
    productId = optionalString("product_id"),
    description = requiredString("description"),
    quantity = optionalDecimal("quantity"),
    unit = optionalString("unit"),
    unitPrice = optionalDecimal("unit_price"),
    stockQuantity = optionalDecimal("stock_quantity"),
    matchStatus = requiredString("match_status"),
)

private fun JsonObject.toPurchaseInsight(): PurchaseInsight = PurchaseInsight(
    type = requiredString("type"),
    message = requiredString("message"),
    evidenceIds = getAsJsonArray("evidence_ids").map { it.asString },
)

private fun PurchaseDocument.toJson(): JsonObject = JsonObject().apply {
    addProperty("source", source.name)
    addProperty("document_type", documentType.name)
    addProperty("access_key", accessKey)
    // The PI fiscal page provides local Brazilian time without an offset.
    issuedAt?.atOffset(ZoneOffset.of("-03:00"))?.let { addProperty("issued_at", it.toString()) }
        ?: add("issued_at", JsonNull.INSTANCE)
    add("issuer", JsonObject().apply {
        issuer.name?.let { addProperty("name", it) } ?: add("name", JsonNull.INSTANCE)
        issuer.taxId?.let { addProperty("tax_id", it) } ?: add("tax_id", JsonNull.INSTANCE)
    })
    add("items", JsonArray().also { array -> items.forEach { array.add(it.toJson()) } })
    total?.let { add("total", JsonPrimitive(it)) } ?: add("total", JsonNull.INSTANCE)
}

private fun PurchaseItem.toJson(): JsonObject = JsonObject().apply {
    addProperty("line_number", lineNumber)
    externalCode?.let { addProperty("external_code", it) } ?: add("external_code", JsonNull.INSTANCE)
    gtin?.let { addProperty("gtin", it) } ?: add("gtin", JsonNull.INSTANCE)
    addProperty("description", description)
    quantity?.let { add("quantity", JsonPrimitive(it)) } ?: add("quantity", JsonNull.INSTANCE)
    unit?.let { addProperty("unit", it) } ?: add("unit", JsonNull.INSTANCE)
    unitPrice?.let { add("unit_price", JsonPrimitive(it)) } ?: add("unit_price", JsonNull.INSTANCE)
    totalPrice?.let { add("total_price", JsonPrimitive(it)) } ?: add("total_price", JsonNull.INSTANCE)
}

private fun JsonObject.toPurchaseDocumentPreview(): PurchaseDocumentPreview {
    val issuerValue = get("issuer")?.takeUnless { it.isJsonNull }?.asJsonObject
    val source = PurchaseDocument.Source.valueOf(requiredString("source"))
    val documentType = PurchaseDocument.DocumentType.valueOf(requiredString("document_type"))
    return PurchaseDocumentPreview(
        previewId = requiredString("preview_id"),
        documentId = requiredString("document_id"),
        status = requiredString("status"),
        version = requiredLong("version"),
        source = source,
        documentType = documentType,
        accessKey = requiredString("access_key"),
        issuedAt = optionalString("issued_at")?.let(OffsetDateTime::parse),
        issuer = PurchaseIssuer(
            name = issuerValue?.optionalString("name"),
            taxId = issuerValue?.optionalString("tax_id"),
        ),
        items = getAsJsonArray("items").map { it.asJsonObject.toPurchaseItem() },
        matches = getAsJsonArray("items").map { it.asJsonObject.toPurchaseMatch() },
        total = optionalDecimal("total"),
        summary = getAsJsonObject("summary").let {
            PurchaseDocumentPreviewSummary(
                items = it.requiredInt("items"),
                matched = it.requiredInt("matched"),
                newProducts = it.requiredInt("new_products"),
                needsReview = it.requiredInt("needs_review"),
                purchaseTotal = it.optionalDecimal("purchase_total"),
            )
        },
    )
}

private fun JsonObject.toPurchaseItem(): PurchaseItem = PurchaseItem(
    lineNumber = requiredInt("line_number"),
    externalCode = optionalString("external_code"),
    gtin = optionalString("gtin"),
    description = requiredString("description"),
    quantity = optionalDecimal("quantity"),
    unit = optionalString("unit"),
    unitPrice = optionalDecimal("unit_price"),
    totalPrice = optionalDecimal("total_price"),
)

private fun JsonObject.toPurchaseMatch(): PurchaseDocumentMatch = PurchaseDocumentMatch(
    lineNumber = requiredInt("line_number"),
    status = PurchaseDocumentMatch.Status.valueOf(requiredString("match_status")),
    productId = optionalString("matched_product_id"),
    candidateName = optionalString("candidate_name"),
    baseUnit = optionalString("base_unit"),
    confidence = optionalDecimal("match_confidence"),
    requiresUserAction = get("requires_user_action")?.takeUnless { it.isJsonNull }?.asBoolean ?: true,
)

private fun JsonObject.requiredString(key: String): String =
    get(key)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
        ?: error("Campo obrigatório ausente: $key")

private fun JsonObject.optionalString(key: String): String? =
    get(key)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

private fun JsonObject.requiredInt(key: String): Int = requiredNumber(key).intValueExact()
private fun JsonObject.requiredLong(key: String): Long = requiredNumber(key).longValueExact()
private fun JsonObject.optionalDecimal(key: String): BigDecimal? =
    get(key)?.takeUnless { it.isJsonNull }?.asBigDecimal

private fun JsonObject.requiredNumber(key: String): BigDecimal =
    get(key)?.takeUnless { it.isJsonNull }?.asString?.let(::BigDecimal)
        ?: error("Campo numérico ausente: $key")
