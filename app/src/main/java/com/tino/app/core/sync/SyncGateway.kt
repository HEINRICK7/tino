package com.tino.app.core.sync

import com.tino.app.core.database.DomainEventEntity
import javax.inject.Inject

data class PullChanges(
    val events: List<DomainEventEntity>,
    val nextCursor: String?,
)

data class PushRejection(
    val eventId: String,
    val code: String,
    val retryable: Boolean,
    val message: String,
)

data class PushResult(
    val acknowledgedEventIds: Set<String>,
    val rejected: List<PushRejection> = emptyList(),
)

interface SyncGateway {
    suspend fun push(events: List<DomainEventEntity>): PushResult
    suspend fun pull(cursor: String?): PullChanges
}

class SyncUnavailableException(message: String) : IllegalStateException(message)

/** The server understood the request but the event cannot be retried unchanged. */
class SyncPermanentException(message: String) : IllegalStateException(message)

/** Credentials need attention; local data remains valid and is never discarded. */
class SyncAuthRequiredException(message: String) : IllegalStateException(message)

/** Placeholder until the cloud REST contract is supplied; it never lies about sync state. */
class UnavailableSyncGateway @Inject constructor() : SyncGateway {
    override suspend fun push(events: List<DomainEventEntity>): PushResult =
        throw SyncUnavailableException("Sincronização cloud ainda não configurada.")

    override suspend fun pull(cursor: String?): PullChanges =
        throw SyncUnavailableException("Sincronização cloud ainda não configurada.")
}
