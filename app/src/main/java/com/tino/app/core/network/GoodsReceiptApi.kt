package com.tino.app.core.network

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tino.app.domain.receiving.FiscalStatus
import com.tino.app.domain.receiving.GoodsReceiptConfirmation
import com.tino.app.domain.receiving.GoodsReceiptDecision
import com.tino.app.domain.receiving.GoodsReceiptDecisionAction
import com.tino.app.domain.receiving.GoodsReceiptErrorCode
import com.tino.app.domain.receiving.GoodsReceiptItemResult
import com.tino.app.domain.receiving.GoodsReceiptPreview
import com.tino.app.domain.receiving.GoodsReceiptPreviewIssuer
import com.tino.app.domain.receiving.GoodsReceiptPreviewItem
import com.tino.app.domain.receiving.GoodsReceiptPreviewStatus
import com.tino.app.domain.receiving.GoodsReceiptPreviewSummary
import com.tino.app.domain.receiving.GoodsReceiptResult
import com.tino.app.domain.receiving.GoodsReceiptStatus
import com.tino.app.domain.receiving.NfeDocumentState
import com.tino.app.domain.receiving.NfePreviewReference
import com.tino.app.domain.receiving.NfeRetrievalStatus
import com.tino.app.domain.receiving.ProductResolutionStatus
import com.tino.app.domain.receiving.ProductSearchItem
import java.math.BigDecimal

interface GoodsReceiptApi {
    suspend fun retrieveNfe(businessId: String, accessKey: String, idempotencyKey: String): NfeDocumentState
    suspend fun getNfeDocument(businessId: String, documentId: String): NfeDocumentState
    suspend fun getPreview(businessId: String, documentId: String): GoodsReceiptPreview
    suspend fun reprocessNfe(businessId: String, documentId: String, idempotencyKey: String): NfeDocumentState
    suspend fun searchProducts(businessId: String, query: String? = null, gtin: String? = null): List<ProductSearchItem>
    suspend fun confirmGoodsReceipt(
        businessId: String,
        previewId: String,
        confirmation: GoodsReceiptConfirmation,
        idempotencyKey: String,
    ): GoodsReceiptResult
    suspend fun getGoodsReceipt(businessId: String, receiptId: String): GoodsReceiptResult
}

class RestGoodsReceiptApi(
    baseUrl: String,
    transport: BackendHttpTransport,
) : GoodsReceiptApi {
    private val client = BackendHttpClient(baseUrl, transport)

    override suspend fun retrieveNfe(businessId: String, accessKey: String, idempotencyKey: String): NfeDocumentState =
        client.post(
            path = "/api/v1/businesses/$businessId/nfe-documents",
            idempotencyKey = idempotencyKey,
            body = JsonObject().apply { addProperty("access_key", accessKey) },
        ).asJsonObject.toNfeDocumentState()

    override suspend fun getNfeDocument(businessId: String, documentId: String): NfeDocumentState =
        client.get("/api/v1/businesses/$businessId/nfe-documents/$documentId").asJsonObject.toNfeDocumentState()

    override suspend fun getPreview(businessId: String, documentId: String): GoodsReceiptPreview =
        client.get("/api/v1/businesses/$businessId/nfe-documents/$documentId/preview").asJsonObject.toPreview()

    override suspend fun reprocessNfe(businessId: String, documentId: String, idempotencyKey: String): NfeDocumentState =
        client.post(
            path = "/api/v1/businesses/$businessId/nfe-documents/$documentId/reprocess",
            idempotencyKey = idempotencyKey,
        ).asJsonObject.toNfeDocumentState()

    override suspend fun searchProducts(businessId: String, query: String?, gtin: String?): List<ProductSearchItem> =
        client.get(
            path = "/api/v1/businesses/$businessId/products",
            query = buildMap {
                query?.takeIf { it.isNotBlank() }?.let { put("q", it) }
                gtin?.takeIf { it.isNotBlank() }?.let { put("gtin", it) }
            },
        ).asJsonArray.map { it.asJsonObject.toProductSearchItem() }

    override suspend fun confirmGoodsReceipt(
        businessId: String,
        previewId: String,
        confirmation: GoodsReceiptConfirmation,
        idempotencyKey: String,
    ): GoodsReceiptResult = client.post(
        path = "/api/v1/businesses/$businessId/goods-receipts/$previewId/confirm",
        idempotencyKey = idempotencyKey,
        body = confirmation.toJson(),
    ).asJsonObject.toGoodsReceiptResult()

    override suspend fun getGoodsReceipt(businessId: String, receiptId: String): GoodsReceiptResult =
        client.get("/api/v1/businesses/$businessId/goods-receipts/$receiptId").asJsonObject.toGoodsReceiptResult()
}

class UnavailableGoodsReceiptApi : GoodsReceiptApi {
    private fun unavailable(): Nothing = throw BackendTransportException(
        "Entrada NF-e conectada ainda não está configurada.",
        retryable = false,
    )

    override suspend fun retrieveNfe(businessId: String, accessKey: String, idempotencyKey: String): NfeDocumentState = unavailable()
    override suspend fun getNfeDocument(businessId: String, documentId: String): NfeDocumentState = unavailable()
    override suspend fun getPreview(businessId: String, documentId: String): GoodsReceiptPreview = unavailable()
    override suspend fun reprocessNfe(businessId: String, documentId: String, idempotencyKey: String): NfeDocumentState = unavailable()
    override suspend fun searchProducts(businessId: String, query: String?, gtin: String?): List<ProductSearchItem> = unavailable()
    override suspend fun confirmGoodsReceipt(businessId: String, previewId: String, confirmation: GoodsReceiptConfirmation, idempotencyKey: String): GoodsReceiptResult = unavailable()
    override suspend fun getGoodsReceipt(businessId: String, receiptId: String): GoodsReceiptResult = unavailable()
}

private class BackendHttpClient(
    private val baseUrl: String,
    private val transport: BackendHttpTransport,
) {
    init {
        require(baseUrl.startsWith("https://")) { "A API do TINO exige HTTPS." }
    }

    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JsonObjectOrArray =
        execute(BackendHttpRequest("GET", path, query = query))

    suspend fun post(path: String, idempotencyKey: String, body: JsonObject? = null): JsonObjectOrArray {
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= 200) {
            "Idempotency-Key inválida."
        }
        return execute(
            BackendHttpRequest(
                method = "POST",
                path = path,
                headers = mapOf("Idempotency-Key" to idempotencyKey),
                body = body?.let(Gson()::toJson),
            ),
        )
    }

    private suspend fun execute(request: BackendHttpRequest): JsonObjectOrArray {
        val response = transport.execute(request)
        if (response.status == 401) {
            throw BackendAuthenticationException("A sessão do TINO expirou ou não está autorizada.")
        }
        if (response.status !in 200..299) throw response.toBackendException()
        return JsonObjectOrArray.parse(response.body)
    }
}

private sealed interface JsonObjectOrArray {
    val element: JsonElement

    data class Object(override val element: JsonObject) : JsonObjectOrArray
    data class Array(override val element: JsonArray) : JsonObjectOrArray

    val asJsonObject: JsonObject get() = (this as Object).element
    val asJsonArray: JsonArray get() = (this as Array).element

    companion object {
        fun parse(body: String): JsonObjectOrArray {
            val element = JsonParser.parseString(body.ifBlank { "{}" })
            return when {
                element.isJsonObject -> Object(element.asJsonObject)
                element.isJsonArray -> Array(element.asJsonArray)
                else -> error("Resposta JSON do backend inválida.")
            }
        }
    }
}

internal fun BackendHttpResponse.toBackendException(): BackendApiException {
    val parsed = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
    val rawCode = parsed?.get("code")?.takeUnless { it.isJsonNull }?.asString
    val code = runCatching { BackendWireErrorCode.valueOf(rawCode.orEmpty()) }.getOrDefault(
        when (status) {
            403 -> BackendWireErrorCode.BUSINESS_ACCESS_DENIED
            408, 429, in 500..599 -> BackendWireErrorCode.RETRIEVAL_UNAVAILABLE
            else -> BackendWireErrorCode.UNKNOWN
        },
    )
    val message = parsed?.get("message")?.takeUnless { it.isJsonNull }?.asString
        ?: "O backend não aceitou a operação."
    val retryable = parsed?.get("retryable")?.takeUnless { it.isJsonNull }?.asBoolean
        ?: (status == 408 || status == 429 || status in 500..599)
    val correlationId = parsed?.get("correlation_id")?.takeUnless { it.isJsonNull }?.asString
    return BackendApiException(code, message, retryable, correlationId, status)
}

private fun JsonObject.toNfeDocumentState(): NfeDocumentState = NfeDocumentState(
    documentId = requiredString("document_id"),
    accessKey = requiredString("access_key"),
    retrievalStatus = enumValue<NfeRetrievalStatus>("retrieval_status"),
    fiscalStatus = enumValue<FiscalStatus>("fiscal_status"),
    itemCount = requiredInt("item_count"),
    errorCode = optionalString("error_code")?.let { runCatching { GoodsReceiptErrorCode.valueOf(it) }.getOrNull() },
    retryable = optionalBoolean("retryable") ?: false,
    preview = get("preview")?.takeUnless { it.isJsonNull }?.asJsonObject?.let {
        NfePreviewReference(
            previewId = it.requiredString("preview_id"),
            status = it.enumValue("status"),
            version = it.requiredLong("version"),
        )
    },
)

private fun JsonObject.toPreview(): GoodsReceiptPreview = GoodsReceiptPreview(
    previewId = requiredString("preview_id"),
    documentId = requiredString("document_id"),
    documentNumber = optionalString("document_number"),
    series = optionalString("series"),
    issuer = get("issuer")?.takeUnless { it.isJsonNull }?.asJsonObject?.let {
        GoodsReceiptPreviewIssuer(it.requiredString("legal_name"), it.optionalString("trade_name"))
    },
    retrievalStatus = enumValue("retrieval_status"),
    fiscalStatus = enumValue("fiscal_status"),
    status = enumValue("status"),
    version = requiredLong("version"),
    summary = getAsJsonObject("summary").let {
        GoodsReceiptPreviewSummary(
            totalItems = it.requiredInt("total_items"),
            matchedItems = it.requiredInt("matched_items"),
            newCandidateItems = it.requiredInt("new_candidate_items"),
            reviewRequiredItems = it.requiredInt("review_required_items"),
        )
    },
    items = getAsJsonArray("items").map { it.asJsonObject.toPreviewItem() },
)

private fun JsonObject.toPreviewItem(): GoodsReceiptPreviewItem = GoodsReceiptPreviewItem(
    lineNumber = requiredInt("line_number"),
    description = requiredString("description"),
    supplierProductCode = optionalString("supplier_product_code"),
    gtin = optionalString("gtin"),
    resolutionStatus = enumValue("resolution_status"),
    productId = optionalString("product_id"),
    candidateName = optionalString("candidate_name"),
    purchaseUnit = requiredString("purchase_unit"),
    purchaseQuantity = requiredDecimal("purchase_quantity"),
    purchaseUnitCost = requiredDecimal("purchase_unit_cost"),
    productTotal = requiredDecimal("product_total"),
    baseUnit = optionalString("base_unit"),
    conversionFactor = optionalDecimal("conversion_factor"),
    stockQuantity = optionalDecimal("stock_quantity"),
    requiresUserAction = optionalBoolean("requires_user_action") ?: true,
)

private fun JsonObject.toProductSearchItem(): ProductSearchItem = ProductSearchItem(
    productId = requiredString("product_id"),
    name = requiredString("name"),
    baseUnit = requiredString("base_unit"),
    gtin = optionalString("gtin"),
)

private fun GoodsReceiptConfirmation.toJson(): JsonObject = JsonObject().apply {
    addProperty("preview_version", previewVersion)
    add("items", JsonArray().also { array -> items.forEach { array.add(it.toJson()) } })
}

private fun GoodsReceiptDecision.toJson(): JsonObject = JsonObject().apply {
    addProperty("line_number", lineNumber)
    addProperty("action", action.name)
    if (productId == null) add("product_id", com.google.gson.JsonNull.INSTANCE) else addProperty("product_id", productId)
    if (baseUnit == null) add("base_unit", com.google.gson.JsonNull.INSTANCE) else addProperty("base_unit", baseUnit)
    if (conversionFactor == null) add("conversion_factor", com.google.gson.JsonNull.INSTANCE) else addProperty("conversion_factor", conversionFactor)
}

private fun JsonObject.toGoodsReceiptResult(): GoodsReceiptResult = GoodsReceiptResult(
    receiptId = requiredString("receipt_id"),
    status = enumValue("status"),
    itemCount = requiredInt("item_count"),
    items = getAsJsonArray("items").map { item ->
        item.asJsonObject.let {
            GoodsReceiptItemResult(
                lineNumber = it.requiredInt("line_number"),
                productId = it.requiredString("product_id"),
                productName = it.requiredString("product_name"),
                baseUnit = it.requiredString("base_unit"),
                quantityAdded = it.requiredDecimal("quantity_added"),
                unitCost = it.requiredDecimal("unit_cost"),
            )
        }
    },
)

private inline fun <reified T : Enum<T>> JsonObject.enumValue(key: String): T =
    enumValues<T>().firstOrNull { it.name == requiredString(key) } ?: error("Enum $key inválido.")

private fun JsonObject.requiredString(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString
    ?.takeIf { it.isNotBlank() } ?: error("Campo obrigatório ausente: $key")

private fun JsonObject.optionalString(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
    ?.takeIf { it.isNotBlank() }

private fun JsonObject.requiredInt(key: String): Int = requiredNumber(key).intValueExact()
private fun JsonObject.requiredLong(key: String): Long = requiredNumber(key).longValueExact()
private fun JsonObject.requiredDecimal(key: String): BigDecimal = requiredNumber(key)
private fun JsonObject.optionalDecimal(key: String): BigDecimal? = get(key)?.takeUnless { it.isJsonNull }?.asBigDecimal
private fun JsonObject.optionalBoolean(key: String): Boolean? = get(key)?.takeUnless { it.isJsonNull }?.asBoolean
private fun JsonObject.requiredNumber(key: String): BigDecimal = get(key)?.takeUnless { it.isJsonNull }?.asString?.let(::BigDecimal)
    ?: error("Campo numérico ausente: $key")
