package com.tino.app.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.common.UuidV7
import com.tino.app.core.database.DomainEventEntity
import com.tino.app.core.database.ProductEntity
import com.tino.app.core.database.TinoDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.fail
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CommerceSnapshotRepositoryTest {
    private lateinit var database: TinoDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TinoDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun corruptedEventIsRejectedBeforeExistingDataIsTouched() = runBlocking {
        database.productDao().insert(ProductEntity("current", "Atual", 100, "un", 1L))
        val valid = CommerceSnapshotRepository(database).export()
        val corrupted = valid.copy(
            events = listOf(
                DomainEventEntity(
                    eventId = UuidV7.new(),
                    storeId = "store",
                    deviceId = "device",
                    aggregateId = "aggregate",
                    type = "product.created",
                    schemaVersion = 1,
                    occurredAt = 1L,
                    payloadJson = "{invalid",
                ),
            ),
        )

        try {
            CommerceSnapshotRepository(database).restore(corrupted)
            fail("Esperava snapshot corrompido ser recusado")
        } catch (_: SnapshotValidationException) {
            // expected
        }
        assertNotNull(database.productDao().findById("current"))
    }

    @Test
    fun requireEmptyPolicyDoesNotSilentlyReplaceCurrentCommerce() = runBlocking {
        database.productDao().insert(ProductEntity("current", "Atual", 100, "un", 1L))
        val emptySnapshot = CommerceSnapshotRepository(database).export().copy(
            products = emptyList(),
        )

        try {
            CommerceSnapshotRepository(database).restore(emptySnapshot, RestorePolicy.REQUIRE_EMPTY)
            fail("Esperava política de aparelho vazio ser respeitada")
        } catch (_: SnapshotValidationException) {
            // expected
        }
        assertNotNull(database.productDao().findById("current"))
    }
}
