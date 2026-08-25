package com.tino.app.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.common.UuidV7
import com.tino.app.core.database.DomainEventEntity
import com.tino.app.core.database.SyncStatus
import com.tino.app.core.database.TinoDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class SyncCoordinatorTest {
    private lateinit var database: TinoDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TinoDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pushesPendingEventsOnceAndAdvancesPullCursor() = runBlocking {
        val event = event("product.created", "product-1", "{\"name\":\"Café\",\"price_cents\":800}")
        database.domainEventDao().insert(event)
        val gateway = FakeGateway()
        val coordinator = coordinator(gateway)

        assertEquals(SyncAttemptResult.SUCCESS, coordinator.syncOnce())
        assertEquals(SyncStatus.SYNCED, database.domainEventDao().findById(event.eventId)?.syncStatus)
        assertEquals(listOf(event.eventId), gateway.pushedIds)
        assertEquals("cursor-1", database.syncCursorDao().find(SyncCoordinator.SYNC_SCOPE)?.cursor)

        // Re-running the worker does not resend the already acknowledged event.
        assertEquals(SyncAttemptResult.SUCCESS, coordinator.syncOnce())
        assertEquals(listOf(event.eventId), gateway.pushedIds)
    }

    @Test
    fun processRestartRecoversEventsLeftInSyncingState() = runBlocking {
        val event = event("product.created", "product-1", "{\"name\":\"Café\",\"price_cents\":800}")
            .copy(syncStatus = SyncStatus.SYNCING)
        database.domainEventDao().insert(event)
        val gateway = FakeGateway()

        assertEquals(SyncAttemptResult.SUCCESS, coordinator(gateway).syncOnce())
        assertEquals(SyncStatus.SYNCED, database.domainEventDao().findById(event.eventId)?.syncStatus)
        assertEquals(listOf(event.eventId), gateway.pushedIds)
    }

    @Test
    fun retryAfterTimeoutDoesNotDuplicateServerAcceptedMutation() = runBlocking {
        val event = event("product.created", "product-1", "{\"name\":\"Café\",\"price_cents\":800}")
        val gateway = AcceptThenTimeoutGateway()
        database.domainEventDao().insert(event)

        assertEquals(SyncAttemptResult.RETRY, coordinator(gateway).syncOnce())
        assertEquals(SyncAttemptResult.SUCCESS, coordinator(gateway).syncOnce())
        assertEquals(1, gateway.acceptedEventIds.size)
        assertEquals(SyncStatus.SYNCED, database.domainEventDao().findById(event.eventId)?.syncStatus)
    }

    @Test
    fun temporaryCloudFailureLeavesEventFailedForRetry() = runBlocking {
        val event = event("product.created", "product-1", "{\"name\":\"Café\",\"price_cents\":800}")
        database.domainEventDao().insert(event)
        val coordinator = coordinator(FailingGateway())

        assertEquals(SyncAttemptResult.RETRY, coordinator.syncOnce())
        val stored = database.domainEventDao().findById(event.eventId)
        assertEquals(SyncStatus.FAILED, stored?.syncStatus)
        assertEquals(1, stored?.attempts)
        assertTrue(stored?.lastError?.contains("indisponível") == true)
    }

    @Test
    fun nonRetryableRejectionDoesNotReturnToPendingQueue() = runBlocking {
        val event = event("future.event", "aggregate-1", "{}")
        database.domainEventDao().insert(event)
        val coordinator = coordinator(RejectingGateway(event.eventId))

        assertEquals(SyncAttemptResult.SUCCESS, coordinator.syncOnce())
        val stored = database.domainEventDao().findById(event.eventId)
        assertEquals(SyncStatus.REJECTED, stored?.syncStatus)
        assertEquals(1, stored?.attempts)
        assertTrue(stored?.lastError?.contains("SCHEMA_INVALID") == true)
    }

    @Test
    fun unknownRemoteEventIsQuarantinedAndCursorStillAdvances() = runBlocking {
        val remote = event("future.event", "aggregate-1", "{}")
        val coordinator = coordinator(PullGateway(remote))

        assertEquals(SyncAttemptResult.SUCCESS, coordinator.syncOnce())
        val stored = database.domainEventDao().findById(remote.eventId)
        assertEquals(SyncStatus.BLOCKED, stored?.syncStatus)
        assertTrue(stored?.lastError?.contains("UNSUPPORTED_EVENT") == true)
        assertEquals("cursor-1", database.syncCursorDao().find(SyncCoordinator.SYNC_SCOPE)?.cursor)
    }

    @Test
    fun remoteMultiItemSaleProjectsEveryLineAndStockMovement() = runBlocking {
        val payload = JSONObject()
            .put("total_cents", 2300)
            .put("payment_method", "cash")
            .put("items", JSONArray()
                .put(JSONObject().put("product_id", "product-1").put("quantity", 2).put("unit_price_cents", 850))
                .put(JSONObject().put("product_id", "product-2").put("quantity", 1).put("unit_price_cents", 600)))
        val remote = event("sale.created", "sale-1", payload.toString())
        val coordinator = coordinator(PullGateway(remote))

        assertEquals(SyncAttemptResult.SUCCESS, coordinator.syncOnce())
        assertEquals(2, database.saleDao().allItems().size)
        assertEquals(-3, database.stockMovementDao().all().sumOf { it.quantityDelta })
    }

    @Test
    fun pullAppliesRemoteEventAndDoesNotDuplicateIt() = runBlocking {
        val remote = event("product.created", "product-1", "{\"name\":\"Café\",\"price_cents\":800}")
        val gateway = PullGateway(remote)
        val coordinator = coordinator(gateway)

        assertEquals(SyncAttemptResult.SUCCESS, coordinator.syncOnce())
        assertEquals(SyncAttemptResult.SUCCESS, coordinator.syncOnce())
        assertEquals(1, database.productDao().all().size)
        assertEquals(1, database.domainEventDao().all().size)
    }

    private fun coordinator(gateway: SyncGateway) = SyncCoordinator(
        database = database,
        eventDao = database.domainEventDao(),
        cursorDao = database.syncCursorDao(),
        gateway = gateway,
        remoteEventApplier = RemoteEventApplier(database),
    )

    private fun event(type: String, aggregateId: String, payload: String) = DomainEventEntity(
        eventId = UuidV7.new(),
        storeId = "store-1",
        deviceId = "device-1",
        aggregateId = aggregateId,
        type = type,
        schemaVersion = 1,
        occurredAt = System.currentTimeMillis(),
        payloadJson = JSONObject(payload).toString(),
    )

    private class FakeGateway : SyncGateway {
        val pushedIds = mutableListOf<String>()
        private var pullCount = 0

        override suspend fun push(events: List<DomainEventEntity>): PushResult {
            events.mapTo(pushedIds) { it.eventId }
            return PushResult(events.map { it.eventId }.toSet())
        }

        override suspend fun pull(cursor: String?): PullChanges {
            pullCount += 1
            return PullChanges(emptyList(), "cursor-$pullCount")
        }
    }

    private class FailingGateway : SyncGateway {
        override suspend fun push(events: List<DomainEventEntity>): PushResult =
            throw SyncUnavailableException("cloud indisponível")

        override suspend fun pull(cursor: String?): PullChanges =
            throw SyncUnavailableException("cloud indisponível")
    }

    private class PullGateway(private val event: DomainEventEntity) : SyncGateway {
        override suspend fun push(events: List<DomainEventEntity>): PushResult = PushResult(emptySet())

        override suspend fun pull(cursor: String?): PullChanges = PullChanges(listOf(event), "cursor-1")
    }

    private class AcceptThenTimeoutGateway : SyncGateway {
        var calls = 0
        val acceptedEventIds = mutableSetOf<String>()

        override suspend fun push(events: List<DomainEventEntity>): PushResult {
            calls++
            acceptedEventIds += events.map { it.eventId }
            if (calls == 1) throw SyncUnavailableException("timeout após confirmação")
            return PushResult(events.map { it.eventId }.toSet())
        }

        override suspend fun pull(cursor: String?): PullChanges = PullChanges(emptyList(), cursor)
    }

    private class RejectingGateway(private val eventId: String) : SyncGateway {
        override suspend fun push(events: List<DomainEventEntity>): PushResult = PushResult(
            acknowledgedEventIds = emptySet(),
            rejected = listOf(PushRejection(eventId, "SCHEMA_INVALID", retryable = false, message = "payload inválido")),
        )

        override suspend fun pull(cursor: String?): PullChanges = PullChanges(emptyList(), "cursor-1")
    }
}
