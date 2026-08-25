package com.tino.app.core.sync

import com.tino.app.core.database.DomainEventEntity
/** Deterministic cloud double used by acceptance tests and local development. */
class InMemoryCloudSyncGateway : SyncGateway {
    private val events = LinkedHashMap<String, DomainEventEntity>()

    override suspend fun push(events: List<DomainEventEntity>): PushResult = synchronized(this) {
        events.forEach { this.events.putIfAbsent(it.eventId, it) }
        PushResult(events.map { it.eventId }.toSet())
    }

    override suspend fun pull(cursor: String?): PullChanges = synchronized(this) {
        val offset = cursor?.toIntOrNull() ?: 0
        val all = events.values.toList()
        val changes = all.drop(offset)
        PullChanges(changes, (offset + changes.size).toString())
    }

    @Synchronized
    fun storedEventCount(): Int = events.size
}
