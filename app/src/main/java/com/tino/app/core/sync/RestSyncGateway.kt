package com.tino.app.core.sync

import com.tino.app.core.common.UuidV7
import com.tino.app.core.database.DomainEventEntity
import com.tino.app.core.database.SyncStatus
import com.tino.app.core.security.SecureTokenStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RestSyncGateway(
    private val baseUrl: String,
    private val tokenStore: SecureTokenStore,
) : SyncGateway {
    private val circuitBreaker = SyncCircuitBreaker()

    init {
        require(baseUrl.startsWith("https://")) { "A sincronização cloud exige HTTPS." }
    }

    override suspend fun push(events: List<DomainEventEntity>): PushResult {
        if (events.isEmpty()) return PushResult(emptySet())
        val response = request(
            method = "POST",
            path = "/v1/sync/events",
            body = JSONObject().put("events", JSONArray(events.map(::eventJson))).toString(),
        )
        val acknowledged = response.optJSONArray("acknowledged_event_ids") ?: JSONArray()
        val alreadyProcessed = response.optJSONArray("already_processed_event_ids") ?: JSONArray()
        val acknowledgedIds = (0 until acknowledged.length()).map { acknowledged.getString(it) }.toMutableSet()
        (0 until alreadyProcessed.length()).forEach { acknowledgedIds += alreadyProcessed.getString(it) }
        return PushResult(acknowledgedEventIds = acknowledgedIds, rejected = parseRejections(response))
    }

    override suspend fun pull(cursor: String?): PullChanges {
        val suffix = cursor?.let { "?cursor=${java.net.URLEncoder.encode(it, Charsets.UTF_8.name())}&limit=100" } ?: "?limit=100"
        val response = request("GET", "/v1/sync/changes$suffix", null)
        val changes = response.optJSONArray("changes") ?: JSONArray()
        val events = (0 until changes.length()).map { eventFromJson(changes.getJSONObject(it)) }
        return PullChanges(events, response.optString("next_cursor").ifBlank { null })
    }

    private fun request(method: String, path: String, body: String?): JSONObject {
        circuitBreaker.beforeCall()
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Request-Id", UuidV7.new())
                tokenStore.read()?.takeIf { it.isNotBlank() }?.let {
                    setRequestProperty("Authorization", "Bearer $it")
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            val http = requireNotNull(connection)
            body?.let { http.outputStream.use { stream -> stream.write(it.toByteArray()) } }
            val status = http.responseCode
            val response = (if (status in 200..299) http.inputStream else http.errorStream)
                ?.bufferedReader()?.use { reader ->
                    val text = reader.readText()
                    require(text.length <= MAX_RESPONSE_CHARS) { "Resposta de sync excedeu o limite." }
                    text
                }.orEmpty()
            if (status !in 200..299) {
                val message = when (status) {
                    401, 403 -> "Entre novamente para sincronizar o TINO."
                    408, 429 -> "A sincronização está ocupada; vamos tentar novamente."
                    in 500..599 -> "A sincronização está indisponível temporariamente."
                    else -> "O registro não pôde ser aceito pela sincronização."
                }
                throw when (status) {
                    401, 403 -> SyncAuthRequiredException(message)
                    408, 429, in 500..599 -> SyncUnavailableException(message)
                    else -> SyncPermanentException(message)
                }
            }
            JSONObject(response.ifBlank { "{}" }).also { circuitBreaker.recordSuccess() }
        } catch (error: Throwable) {
            // Client errors are protocol/auth signals, not dependency outages.
            // They must not open the breaker and hide a useful server response.
            if (error !is SyncPermanentException && error !is SyncAuthRequiredException) {
                circuitBreaker.recordFailure()
            }
            throw error
        } finally {
            connection?.disconnect()
        }
    }

    private fun eventJson(event: DomainEventEntity): JSONObject = JSONObject()
        .put("event_id", event.eventId)
        .put("store_id", event.storeId)
        .put("device_id", event.deviceId)
        .put("aggregate_id", event.aggregateId)
        .put("event_type", event.type)
        .put("schema_version", event.schemaVersion)
        .put("occurred_at", event.occurredAt)
        .put("payload", JSONObject(event.payloadJson))

    private fun eventFromJson(json: JSONObject): DomainEventEntity = DomainEventEntity(
        eventId = json.getString("event_id"),
        storeId = json.getString("store_id"),
        deviceId = json.getString("device_id"),
        aggregateId = json.getString("aggregate_id"),
        type = json.optString("event_type").ifBlank { json.getString("type") },
        schemaVersion = json.getInt("schema_version"),
        occurredAt = json.getLong("occurred_at"),
        payloadJson = json.getJSONObject("payload").toString(),
        syncStatus = SyncStatus.SYNCED,
    )

    private fun parseRejections(response: JSONObject): List<PushRejection> {
        val rejected = response.optJSONArray("rejected") ?: return emptyList()
        return (0 until rejected.length()).map { index ->
            val item = rejected.getJSONObject(index)
            PushRejection(
                eventId = item.getString("event_id"),
                code = item.optString("code", "SYNC_REJECTED"),
                retryable = item.optBoolean("retryable", false),
                message = item.optString("message", "Evento rejeitado pela cloud."),
            )
        }
    }

    private companion object {
        const val MAX_RESPONSE_CHARS = 1_000_000
    }
}
