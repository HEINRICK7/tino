package com.tino.app.core.network

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tino.app.domain.onboarding.BusinessDataSource
import com.tino.app.domain.onboarding.BusinessDataSourceApi
import com.tino.app.domain.onboarding.BusinessDataSourceType

class RestBusinessDataSourceApi(
    baseUrl: String,
    private val transport: BackendHttpTransport,
) : BusinessDataSourceApi {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    init {
        require(normalizedBaseUrl.startsWith("https://")) { "A API do TINO exige HTTPS." }
    }

    override suspend fun get(businessId: String): BusinessDataSource =
        request("GET", businessId, null)

    override suspend fun select(
        businessId: String,
        sourceType: BusinessDataSourceType,
        provider: String?,
    ): BusinessDataSource {
        require(
            (sourceType == BusinessDataSourceType.TINO_NATIVE && provider == null) ||
                (sourceType == BusinessDataSourceType.EXTERNAL_API && provider == DOCES_SONHOS_PROVIDER),
        ) {
            "A origem externa selecionada não é suportada pelo contrato do TINO."
        }
        return request(
            method = "PUT",
            businessId = businessId,
            body = JsonObject().apply {
                addProperty("source_type", sourceType.name)
                if (provider == null) add("provider", JsonNull.INSTANCE) else addProperty("provider", provider)
            },
        )
    }

    private suspend fun request(method: String, businessId: String, body: JsonObject?): BusinessDataSource {
        require(businessId.isNotBlank()) { "businessId é obrigatório." }
        val response = transport.execute(
            BackendHttpRequest(
                method = method,
                path = "/api/v1/businesses/$businessId/data-source",
                body = body?.toString(),
            ),
        )
        if (response.status == 401) throw BackendAuthenticationException("A sessão do TINO expirou ou não está autorizada.")
        if (response.status !in 200..299) throw response.toBackendException()
        val parsed = JsonParser.parseString(response.body.ifBlank { "{}" })
        require(parsed.isJsonObject) { "Resposta de origem de dados inválida." }
        return parsed.asJsonObject.toBusinessDataSource(businessId)
    }

    private companion object {
        const val DOCES_SONHOS_PROVIDER = "DOCES_SONHOS"
    }
}

class UnavailableBusinessDataSourceApi : BusinessDataSourceApi {
    private fun unavailable(): Nothing = throw BackendTransportException(
        "A configuração da origem de dados ainda não está disponível.",
        retryable = false,
    )

    override suspend fun get(businessId: String): BusinessDataSource = unavailable()

    override suspend fun select(
        businessId: String,
        sourceType: BusinessDataSourceType,
        provider: String?,
    ): BusinessDataSource = unavailable()
}

private fun JsonObject.toBusinessDataSource(requestedBusinessId: String): BusinessDataSource {
    val responseBusinessId = stringOrNull("business_id") ?: requestedBusinessId
    require(responseBusinessId == requestedBusinessId) {
        "O backend retornou uma origem de dados de outro comércio."
    }
    val sourceType = stringOrNull("source_type")?.let {
        runCatching { BusinessDataSourceType.valueOf(it) }.getOrNull()
    } ?: error("Resposta de origem de dados sem source_type.")
    val provider = stringOrNull("provider")
    require(sourceType == BusinessDataSourceType.TINO_NATIVE || provider == "DOCES_SONHOS") {
        "Resposta de origem de dados fora do contrato."
    }
    return BusinessDataSource(
        businessId = responseBusinessId,
        sourceType = sourceType,
        provider = provider,
        connectionId = stringOrNull("connection_id"),
        status = stringOrNull("status"),
    )
}

private fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }
