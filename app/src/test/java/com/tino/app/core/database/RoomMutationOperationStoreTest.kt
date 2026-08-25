package com.tino.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.voice.MutationConfirmation
import com.tino.app.domain.voice.MutationOperationStatus
import com.tino.app.domain.voice.MutationSafetyCoordinator
import com.tino.app.domain.voice.OperationRisk
import com.tino.app.domain.voice.PreparedMutation
import com.tino.app.domain.voice.ProposedOperation
import com.tino.app.domain.voice.MutationAuthorization
import com.tino.app.domain.voice.ToolCall
import com.tino.app.domain.voice.ToolPreview
import com.tino.app.domain.voice.CommerceToolName
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RoomMutationOperationStoreTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        TinoDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val store = RoomMutationOperationStore(database.mutationOperationDao())

    @After
    fun close() {
        database.close()
    }

    @Test
    fun pendingAndCommittedOperationSurviveThroughRoomAdapter() = runBlocking {
        val prepared = PreparedMutation(
            operation = ProposedOperation(
                operationId = "op-room",
                capabilityId = TinoCapabilityId.CHANGE_PRODUCT_PRICE,
                arguments = mapOf("product" to "Café", "new_price_cents" to "1090"),
                risk = OperationRisk.LOW_RISK_MUTATION,
                requiresConfirmation = true,
                idempotencyKey = "idem-room",
                previewFingerprint = "preview-room",
                createdAtEpochMs = 1L,
                expiresAtEpochMs = 300_001L,
            ),
            confirmation = MutationConfirmation("op-room", "secret-token"),
        )

        store.save(prepared)
        assertEquals(MutationOperationStatus.PENDING, store.find("op-room")?.status)

        assertTrue(store.reserve("op-room", "idem-room"))
        store.markCommitted("op-room", "idem-room")

        val restored = store.find("op-room")
        assertNotNull(restored)
        assertEquals(MutationOperationStatus.COMMITTED, restored?.status)
        assertEquals("preview-room", restored?.prepared?.operation?.previewFingerprint)
    }

    @Test
    fun coordinatorCanAuthorizePendingOperationAfterRecreation() = runBlocking {
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneId.of("UTC"))
        val safety = MutationSafetyCoordinator(clock, store)
        val call = ToolCall(
            CommerceToolName.CHANGE_PRODUCT_PRICE,
            mapOf("product" to "Café", "new_price_cents" to "1090"),
        )
        val preview = ToolPreview("Alterar preço?", "old → new", "ALTERAR PREÇO")
        val prepared = safety.prepare(call, preview)

        val recreated = MutationSafetyCoordinator(clock, store)
        val result = recreated.authorize(call, prepared.confirmation, preview)

        assertTrue(result is MutationAuthorization.Allowed)
    }

    @Test
    fun committedOperationRemainsTerminalAfterCoordinatorRecreation() = runBlocking {
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneId.of("UTC"))
        val safety = MutationSafetyCoordinator(clock, store)
        val call = ToolCall(
            CommerceToolName.CHANGE_PRODUCT_PRICE,
            mapOf("product" to "Café", "new_price_cents" to "1090"),
        )
        val preview = ToolPreview("Alterar preço?", "old → new", "ALTERAR PREÇO")
        val prepared = safety.prepare(call, preview)
        val allowed = safety.authorize(call, prepared.confirmation, preview) as MutationAuthorization.Allowed
        safety.commit(allowed.operation)

        val recreated = MutationSafetyCoordinator(clock, store)
        val replay = recreated.authorize(call, prepared.confirmation, preview)

        assertEquals("Operação repetida bloqueada por idempotência.", (replay as MutationAuthorization.Denied).reason)
        assertEquals(MutationOperationStatus.COMMITTED, store.find(prepared.operation.operationId)?.status)
    }
}
