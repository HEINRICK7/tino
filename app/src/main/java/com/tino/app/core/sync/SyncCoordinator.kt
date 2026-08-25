package com.tino.app.core.sync

import androidx.room.withTransaction
import com.tino.app.core.database.DomainEventDao
import com.tino.app.core.database.SyncCursorEntity
import com.tino.app.core.database.SyncCursorDao
import com.tino.app.core.database.SyncStatus
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.core.observability.NoOpAuditLogger
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncAttemptResult { SUCCESS, RETRY }

@Singleton
class SyncCoordinator @Inject constructor(
    private val database: TinoDatabase,
    private val eventDao: DomainEventDao,
    private val cursorDao: SyncCursorDao,
    private val gateway: SyncGateway,
    private val remoteEventApplier: RemoteEventApplier,
    private val auditLogger: AuditLogger = NoOpAuditLogger,
) {
    suspend fun syncOnce(): SyncAttemptResult {
        eventDao.recoverInFlight()
        val pending = eventDao.pending(limit = 100)
        auditLogger.record(
            AuditEventType.SYNC_STATUS,
            mapOf("sync_state" to "attempt", "events_pending" to pending.size.toString()),
        )
        return try {
            if (pending.isNotEmpty()) {
                val ids = pending.map { it.eventId }
                eventDao.markSyncing(ids)
                val pushResult = gateway.push(pending)
                val acknowledged = pushResult.acknowledgedEventIds intersect pending.map { it.eventId }.toSet()
                val rejectedById = pushResult.rejected.associateBy { it.eventId }
                database.withTransaction {
                    if (acknowledged.isNotEmpty()) eventDao.markSynced(acknowledged.toList())
                    pending.map { it.eventId }.filterNot { it in acknowledged }.forEach { eventId ->
                        val rejection = rejectedById[eventId]
                        when {
                            rejection == null -> eventDao.updateStatus(eventId, SyncStatus.FAILED, "Cloud não confirmou o evento.")
                            rejection.retryable -> eventDao.updateStatus(eventId, SyncStatus.FAILED, "${rejection.code}: ${rejection.message}")
                            else -> eventDao.updateStatus(eventId, SyncStatus.REJECTED, "${rejection.code}: ${rejection.message}")
                        }
                    }
                }
            }

            val currentCursor = cursorDao.find(SYNC_SCOPE)?.cursor
            val changes = gateway.pull(currentCursor)
            database.withTransaction {
                changes.events.forEach { remoteEventApplier.applyIfNew(it) }
                changes.nextCursor?.let {
                    cursorDao.save(SyncCursorEntity(SYNC_SCOPE, it, System.currentTimeMillis()))
                }
            }
            auditLogger.record(
                AuditEventType.SYNC_STATUS,
                mapOf(
                    "sync_state" to "success",
                    "events_pushed" to pending.size.toString(),
                    "events_pulled" to changes.events.size.toString(),
                ),
            )
            SyncAttemptResult.SUCCESS
        } catch (error: SyncAuthRequiredException) {
            pending.forEach { eventDao.updateStatus(it.eventId, SyncStatus.BLOCKED, error.message) }
            auditLogger.record(AuditEventType.SYNC_STATUS, mapOf("sync_state" to "auth_required"))
            SyncAttemptResult.SUCCESS
        } catch (error: SyncPermanentException) {
            pending.forEach { eventDao.updateStatus(it.eventId, SyncStatus.REJECTED, error.message) }
            auditLogger.record(AuditEventType.SYNC_STATUS, mapOf("sync_state" to "permanent_failure"))
            SyncAttemptResult.SUCCESS
        } catch (error: SyncUnavailableException) {
            pending.forEach { eventDao.updateStatus(it.eventId, SyncStatus.FAILED, error.message) }
            auditLogger.record(AuditEventType.SYNC_STATUS, mapOf("sync_state" to "retry", "reason" to "unavailable"))
            SyncAttemptResult.RETRY
        } catch (error: Throwable) {
            pending.forEach { eventDao.updateStatus(it.eventId, SyncStatus.FAILED, error.message) }
            auditLogger.record(AuditEventType.SYNC_STATUS, mapOf("sync_state" to "retry", "reason" to "unexpected"))
            SyncAttemptResult.RETRY
        }
    }

    companion object {
        const val SYNC_SCOPE = "store"
    }
}
