package com.tino.app.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.database.ProductEntity
import com.tino.app.core.database.StockMovementEntity
import com.tino.app.core.database.TinoDatabase
import com.tino.app.domain.catalog.CatalogProduct
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RoomCatalogProductStoreTest {
    private lateinit var database: TinoDatabase
    private lateinit var store: RoomCatalogProductStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TinoDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = RoomCatalogProductStore(database, database.productDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun firstSyncCreatesAndSecondSyncUpdatesWithoutChangingStock() = runBlocking {
        val product = CatalogProduct("remote-1", "Cerveja", "CX", "789", 6990)
        database.stockMovementDao().insert(StockMovementEntity("movement-1", "remote-1", 7, "manual", null, 1L))

        val first = store.upsert(listOf(product))
        val second = store.upsert(listOf(product.copy(name = "Cerveja Atualizada", priceCents = 7100)))

        assertEquals(1, first.created)
        assertEquals(1, second.updated)
        assertEquals("Cerveja Atualizada", database.productDao().findById("remote-1")?.name)
        assertEquals(7100L, database.productDao().findById("remote-1")?.priceCents)
        assertEquals("789", database.productDao().findById("remote-1")?.gtin)
        assertEquals(7, database.stockMovementDao().balance("remote-1"))
    }

    @Test
    fun conflictingLocalNameIsRejectedWithoutDeletingExistingProduct() = runBlocking {
        database.productDao().insert(ProductEntity("local", "Cerveja", 100, "UN", 1L))

        val result = store.upsert(listOf(CatalogProduct("remote", "Cerveja", "UN", null, 200)))

        assertTrue(result.failures.isNotEmpty())
        assertEquals("local", database.productDao().findByName("Cerveja")?.id)
        assertEquals(null, database.productDao().findById("remote"))
    }

    @Test
    fun validProductsBeforeAndAfterANameConflictRemainPersisted() = runBlocking {
        database.productDao().insert(ProductEntity("local", "Cerveja", 100, "UN", 1L))

        val result = store.upsert(
            listOf(
                CatalogProduct("remote-1", "Leite", "UN", null, 200),
                CatalogProduct("remote-2", "Cerveja", "UN", null, 300),
                CatalogProduct("remote-3", "Açúcar", "UN", null, 400),
            ),
        )

        assertEquals(2, result.created)
        assertEquals(1, result.failures.size)
        assertEquals("remote-1", database.productDao().findById("remote-1")?.id)
        assertEquals("remote-3", database.productDao().findById("remote-3")?.id)
    }

    @Test
    fun madeToOrderCatalogProductIsPersistedWithoutStockTracking() = runBlocking {
        store.upsert(listOf(CatalogProduct("made", "Bolo", "UN", null, 5_000, stockTracked = false)))

        assertFalse(database.productDao().findById("made")!!.stockTracked)
    }
}
