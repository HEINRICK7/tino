package com.tino.app.core.network

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tino.app.domain.onboarding.BootstrapBusiness
import com.tino.app.domain.onboarding.BootstrapContext
import com.tino.app.domain.onboarding.BootstrapInstallation
import com.tino.app.domain.onboarding.BootstrapApi

class RestBootstrapApi(
    baseUrl: String,
    private val transport: BackendHttpTransport,
) : BootstrapApi {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    init {
        require(normalizedBaseUrl.startsWith("https://")) { "A API do TINO exige HTTPS." }
    }

    override suspend fun bootstrap(
        requestedBusinessId: String?,
        installationExternalId: String?,
    ): BootstrapContext {
        val body = JsonObject().apply {
            requestedBusinessId?.takeIf { it.isNotBlank() }?.let { addProperty("requested_business_id", it) }
            installationExternalId?.takeIf { it.isNotBlank() }?.let { addProperty("installation_external_id", it) }
        }
        return post("/api/v1/bootstrap", body).toBootstrapContext()
    }

    override suspend fun createBusiness(tradeName: String, vertical: String): BootstrapBusiness =
        post(
            "/api/v1/businesses",
            JsonObject().apply {
                addProperty("trade_name", tradeName.trim())
                addProperty("vertical", vertical)
            },
        ).toBootstrapBusiness()

    override suspend fun registerInstallation(businessId: String, installationId: String): BootstrapInstallation =
        post(
            "/api/v1/businesses/$businessId/installations",
            JsonObject().apply { addProperty("installation_id", installationId) },
        ).toBootstrapInstallation()

    private suspend fun post(path: String, body: JsonObject): JsonObject {
        val response = transport.execute(
            BackendHttpRequest(
                method = "POST",
                path = path,
                body = body.toString(),
            ),
        )
        if (response.status == 401) throw BackendAuthenticationException("A sessão do TINO expirou ou não está autorizada.")
        if (response.status !in 200..299) throw response.toBackendException()
        val parsed = JsonParser.parseString(response.body.ifBlank { "{}" })
        require(parsed.isJsonObject) { "Resposta de onboarding inválida." }
        return parsed.asJsonObject
    }
}

class UnavailableBootstrapApi : BootstrapApi {
    private fun unavailable(): Nothing = throw BackendTransportException(
        "O onboarding remoto ainda não está configurado.",
        retryable = false,
    )

    override suspend fun bootstrap(requestedBusinessId: String?, installationExternalId: String?): BootstrapContext = unavailable()
    override suspend fun createBusiness(tradeName: String, vertical: String): BootstrapBusiness = unavailable()
    override suspend fun registerInstallation(businessId: String, installationId: String): BootstrapInstallation = unavailable()
}

private fun JsonObject.toBootstrapContext(): BootstrapContext = BootstrapContext(
    state = stringOrNull("state") ?: error("Bootstrap sem state."),
    businesses = getAsJsonArrayOrNull("businesses")?.map { it.toBootstrapBusiness() }.orEmpty(),
    selectedBusiness = get("selected_business")?.takeUnless(JsonElement::isJsonNull)?.asJsonObject?.toBootstrapBusiness(),
    installation = get("installation")?.takeUnless(JsonElement::isJsonNull)?.asJsonObject?.toBootstrapInstallation(),
)

private fun JsonElement.toBootstrapBusiness(): BootstrapBusiness {
    val value = asJsonObject
    return BootstrapBusiness(
        id = value.stringOrNull("id") ?: error("Empresa sem id."),
        tradeName = value.stringOrNull("trade_name") ?: error("Empresa sem trade_name."),
        vertical = value.stringOrNull("vertical") ?: error("Empresa sem vertical."),
        status = value.stringOrNull("status"),
        role = value.stringOrNull("role"),
        dataSourceType = value.stringOrNull("data_source_type"),
    )
}

private fun JsonObject.toBootstrapBusiness(): BootstrapBusiness = (this as JsonElement).toBootstrapBusiness()

private fun JsonObject.toBootstrapInstallation(): BootstrapInstallation = BootstrapInstallation(
    id = stringOrNull("id") ?: error("Instalação sem id."),
    installationId = stringOrNull("installation_id") ?: error("Instalação sem installation_id."),
    businessId = stringOrNull("business_id") ?: error("Instalação sem business_id."),
    status = stringOrNull("status"),
)

private fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }

private fun JsonObject.getAsJsonArrayOrNull(key: String) =
    get(key)?.takeUnless { it.isJsonNull || !it.isJsonArray }?.asJsonArray
