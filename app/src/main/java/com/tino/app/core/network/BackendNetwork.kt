package com.tino.app.core.network

import com.tino.app.core.security.SecureTokenStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class BackendHttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class BackendHttpResponse(
    val status: Int,
    val body: String,
)

fun interface BackendHttpTransport {
    suspend fun execute(request: BackendHttpRequest): BackendHttpResponse
}

fun interface BackendTokenRefresher {
    suspend fun refreshAccessToken(): Boolean
}

class BackendAuthenticationException(message: String) : IllegalStateException(message)

class BackendTransportException(
    message: String,
    val retryable: Boolean = true,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class BackendApiException(
    val code: BackendWireErrorCode,
    override val message: String,
    val retryable: Boolean,
    val correlationId: String?,
    val httpStatus: Int,
) : IllegalStateException(message)

enum class BackendWireErrorCode {
    INVALID_OTP_REQUEST,
    OTP_RATE_LIMITED,
    OTP_DELIVERY_UNAVAILABLE,
    OTP_INVALID,
    OTP_EXPIRED,
    OTP_LOCKED,
    OTP_ALREADY_USED,
    OTP_OPERATION_FAILED,
    INVALID_ACCESS_KEY,
    NFE_NOT_FOUND,
    RETRIEVAL_UNAVAILABLE,
    OUTCOME_UNKNOWN,
    FISCAL_CANCELLED,
    FISCAL_DENIED,
    PRODUCT_REVIEW_REQUIRED,
    PACKAGING_CONVERSION_REQUIRED,
    STALE_PREVIEW,
    INVALID_PRODUCT_SELECTION,
    BUSINESS_ACCESS_DENIED,
    IDEMPOTENCY_CONFLICT,
    UNKNOWN,
}

class UrlConnectionBackendTransport(
    private val baseUrl: String,
    private val tokenStore: SecureTokenStore,
    private val testSslSocketFactory: SSLSocketFactory? = null,
    private val testHostnameVerifier: HostnameVerifier? = null,
    private val tokenRefresher: BackendTokenRefresher? = null,
) : BackendHttpTransport {
    init {
        require(baseUrl.startsWith("https://")) { "A API do TINO exige HTTPS." }
    }

    override suspend fun execute(request: BackendHttpRequest): BackendHttpResponse {
        val first = executeOnce(request)
        if (first.status != 401 || tokenRefresher?.refreshAccessToken() != true) return first
        return executeOnce(request)
    }

    private suspend fun executeOnce(request: BackendHttpRequest): BackendHttpResponse = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        var connection: HttpURLConnection? = null
        try {
            val query = request.query.entries.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
            connection = (URL(baseUrl.trimEnd('/') + request.path + query).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Request-Id", java.util.UUID.randomUUID().toString())
                if (this is HttpsURLConnection) {
                    testSslSocketFactory?.let { sslSocketFactory = it }
                    testHostnameVerifier?.let { hostnameVerifier = it }
                }
                tokenStore.read()?.takeIf { it.isNotBlank() }?.let {
                    setRequestProperty("Authorization", "Bearer $it")
                }
                request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                if (request.body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            request.body?.let { body ->
                connection!!.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection!!.responseCode
            val stream = if (status in 200..299) connection!!.inputStream else connection!!.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(body.length <= MAX_RESPONSE_CHARS) { "Resposta do backend excedeu o limite." }
            BackendHttpResponse(status, body)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: IOException) {
            throw BackendTransportException("Não foi possível acessar o TINO Backend.", cause = error)
        } finally {
            connection?.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_RESPONSE_CHARS = 2_000_000
    }
}
