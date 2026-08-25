package com.tino.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.domain.language.BusinessMemoryKind
import com.tino.app.domain.language.GovernedBusinessMemory
import com.tino.app.domain.language.MemoryCandidate
import com.tino.app.domain.language.MemoryProvenance
import com.tino.app.domain.language.MemoryProvenanceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class BusinessMemoryPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun close() = database.close()

    @Test
    fun recordSurvivesRepositoryRecreationAndDoesNotBecomeCommerceFact() = runBlocking {
        val first = GovernedBusinessMemory(RoomBusinessMemoryRepository(database.businessMemoryDao()))
        val candidate = MemoryCandidate(
            scopeKey = "store-device",
            memoryKey = "entity_alias:PRODUCT:maraca",
            value = "Café Maratá",
            kind = BusinessMemoryKind.ENTITY_ALIAS,
            provenance = MemoryProvenance(MemoryProvenanceType.USER_CORRECTION, occurredAtEpochMs = 10L),
        )
        first.record(candidate)
        first.record(candidate.copy(provenance = MemoryProvenance(MemoryProvenanceType.USER_CONFIRMATION, occurredAtEpochMs = 20L)))

        val recreated = GovernedBusinessMemory(RoomBusinessMemoryRepository(database.businessMemoryDao()))

        assertEquals("Café Maratá", recreated.resolve("store-device", "entity_alias:PRODUCT:maraca")?.value)
        assertEquals(1, recreated.list("store-device").size)
    }
}
