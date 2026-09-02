package com.tino.app.core.network

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.tino.app.domain.catalog.CatalogApi
import com.tino.app.domain.catalog.CatalogStockMode
import com.tino.app.domain.catalog.RemoteCatalogProduct

class RestCatalogApi(
    baseUrl: String,
    private val transport: BackendHttpTransport,
) : CatalogApi {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    init {
        require(normalizedBaseUrl.startsWith("https://")) { "A API do TINO exige HTTPS." }
    }

    override suspend fun syncExternalCatalog(businessId: String): CatalogStockMode {
        val connectionsResponse = transport.execute(
            BackendHttpRequest(
                method = "GET",
                path = "/api/v1/businesses/$businessId/external-connections",
            ),
        )
        if (connectionsResponse.status == 401) {
            throw BackendAuthenticationException("A sessão do TINO expirou ou não está autorizada.")
        }
        if (connectionsResponse.status !in 200..299) throw connectionsResponse.toBackendException()
        val connections = JsonParser.parseString(connectionsResponse.body.ifBlank { "[]" })
        require(connections.isJsonArray) { "Resposta de conexões externas inválida." }
        val connection = connections.asJsonArray
            .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .firstOrNull { it.stringOrNull("provider") == "DOCES_SONHOS" }
            ?: return CatalogStockMode.TRACKED
        val connectionId = connection.stringOrNull("id")
            ?: throw BackendTransportException("A conexão do catálogo não está identificada.", retryable = false)
        val syncResponse = transport.execute(
            BackendHttpRequest(
                method = "POST",
                path = "/api/v1/businesses/$businessId/external-connections/$connectionId/sync",
            ),
        )
        if (syncResponse.status == 401) {
            throw BackendAuthenticationException("A sessão do TINO expirou ou não está autorizada.")
        }
        if (syncResponse.status !in 200..299) throw syncResponse.toBackendException()
        val result = JsonParser.parseString(syncResponse.body.ifBlank { "{}" })
        require(result.isJsonObject) { "Resposta de sincronização externa inválida." }
        val status = result.asJsonObject.stringOrNull("status")
        if (status !in setOf("CONNECTED", "READY")) {
            throw BackendTransportException("A sincronização externa não foi concluída.")
        }
        return CatalogStockMode.MADE_TO_ORDER
    }

    override suspend fun listProducts(
        businessId: String,
        query: String?,
        gtin: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteCatalogProduct> {
        require(limit in 1..100) { "O limite do catálogo deve estar entre 1 e 100." }
        require(offset >= 0) { "O deslocamento do catálogo não pode ser negativo." }
        val response = transport.execute(
            BackendHttpRequest(
                method = "GET",
                path = "/api/v1/businesses/$businessId/products",
                query = buildMap {
                    query?.takeIf { it.isNotBlank() }?.let { put("q", it) }
                    gtin?.takeIf { it.isNotBlank() }?.let { put("gtin", it) }
                    put("limit", limit.toString())
                    if (offset > 0) put("offset", offset.toString())
                },
            ),
        )
        if (response.status == 401) throw BackendAuthenticationException("A sessão do TINO expirou ou não está autorizada.")
        if (response.status !in 200..299) throw response.toBackendException()
        val root = JsonParser.parseString(response.body)
        if (!root.isJsonArray) throw IllegalStateException("Resposta de catálogo inválida.")
        return root.asJsonArray.map { item -> item.toRemoteCatalogProduct() }
    }
}

class UnavailableCatalogApi : CatalogApi {
    override suspend fun syncExternalCatalog(businessId: String): Nothing =
        throw BackendTransportException("Catálogo remoto ainda não está configurado.", retryable = false)

    override suspend fun listProducts(businessId: String, query: String?, gtin: String?, limit: Int, offset: Int): List<RemoteCatalogProduct> =
        throw BackendTransportException("Catálogo remoto ainda não está configurado.", retryable = false)
}

private fun JsonElement.toRemoteCatalogProduct(): RemoteCatalogProduct {
    if (!isJsonObject) return RemoteCatalogProduct(null, null, null, null, null)
    val objectValue = asJsonObject
    return RemoteCatalogProduct(
        productId = objectValue.stringOrNull("product_id"),
        name = objectValue.stringOrNull("name"),
        baseUnit = objectValue.stringOrNull("base_unit"),
        gtin = objectValue.stringOrNull("gtin"),
        price = objectValue.stringOrNull("price"),
    )
}

private fun com.google.gson.JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeUnless { it.isJsonNull }?.let { element -> runCatching { element.asString }.getOrNull() }
