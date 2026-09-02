package com.tino.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.domain.intelligence.ApprovedKnowledgeCatalog
import com.tino.app.domain.intelligence.ApprovedKnowledgeEntry
import com.tino.app.domain.intelligence.BuiltInApprovedKnowledgeCatalog
import com.tino.app.domain.intelligence.KnowledgeCatalogUpdateStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RoomApprovedKnowledgeCatalogTest {
    @Test
    fun bootstrapsApprovedCatalogAndRestoresItFromRoom() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TinoDatabase::class.java,
        ).build()
        try {
            val first = RoomApprovedKnowledgeCatalog(database.approvedKnowledgeCatalogDao(), database)
            assertEquals("v1", first.current().version)
            assertEquals(1, database.approvedKnowledgeCatalogDao().active()?.let { 1 } ?: 0)

            val reopened = RoomApprovedKnowledgeCatalog(database.approvedKnowledgeCatalogDao(), database)
            assertEquals(
                BuiltInApprovedKnowledgeCatalog.current.entries.map { it.id },
                reopened.current().entries.map { it.id },
            )
            assertEquals(
                BuiltInApprovedKnowledgeCatalog.current.entries.map { it.answer },
                reopened.current().entries.map { it.answer },
            )
            assertTrue(reopened.current().entries.all { it.sourceRef == null })
        } finally {
            database.close()
        }
    }

    @Test
    fun activationAndRollbackSurviveANewAdapterInstance() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TinoDatabase::class.java,
        ).build()
        try {
            val first = RoomApprovedKnowledgeCatalog(database.approvedKnowledgeCatalogDao(), database)
            val candidate = ApprovedKnowledgeCatalog(
                version = "v2",
                entries = listOf(
                    ApprovedKnowledgeEntry(
                        id = "delivery",
                        collection = "tino-help",
                        phrases = listOf("entrega"),
                        keywords = listOf("entrega"),
                        answer = "Resposta aprovada sobre entrega.",
                        sourceRef = "approved://manual/v2/delivery",
                    ),
                ),
            )

            assertEquals(KnowledgeCatalogUpdateStatus.ACTIVATED, first.activate(candidate).status)
            val reopened = RoomApprovedKnowledgeCatalog(database.approvedKnowledgeCatalogDao(), database)
            assertEquals("v2", reopened.current().version)
            assertEquals("approved://manual/v2/delivery", reopened.current().entries.single().sourceRef)
            assertEquals(KnowledgeCatalogUpdateStatus.ROLLED_BACK, reopened.rollback().status)
            assertEquals("v1", reopened.current().version)
            assertTrue(database.approvedKnowledgeCatalogDao().previous() != null)
        } finally {
            database.close()
        }
    }
}
