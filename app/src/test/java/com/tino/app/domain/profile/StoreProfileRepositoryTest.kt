package com.tino.app.domain.profile

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.database.TinoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class StoreProfileRepositoryTest {
    private val databaseName = "store-profile-persistence-test.db"
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun profileSurvivesDatabaseCloseAndReopen() = runBlocking {
        val firstDatabase = Room.databaseBuilder(context, TinoDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        StoreProfileRepository(firstDatabase.storeProfileDao()).save(
            "Mercadinho Nossa Senhora de Fátima",
            "Carlos Henrique",
            "86994209350",
        )
        firstDatabase.close()

        val reopened = Room.databaseBuilder(context, TinoDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        val profile = reopened.storeProfileDao().observe().first()

        assertEquals("Mercadinho Nossa Senhora de Fátima", profile?.storeName)
        assertEquals("Carlos Henrique", profile?.ownerName)
        assertEquals("86994209350", profile?.phone)
        reopened.close()
    }

    @Test
    fun verticalAndModulesSurvivePersistenceAndCanBeUpdated() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = StoreProfileRepository(database.storeProfileDao())
        repository.save(
            storeName = "Padaria Central",
            ownerName = "Carlos",
            phone = "86994209350",
            vertical = BusinessVertical.BAKERY,
            activeModules = setOf(BusinessModule.CORE, BusinessModule.SALES, BusinessModule.CUSTOMERS),
        )

        val saved = repository.observeBusinessProfile().first()
        assertEquals(BusinessVertical.BAKERY, saved?.primaryVertical)
        assertEquals(setOf(OperationalPattern.PRODUCTION_AND_SALES), saved?.effectiveOperationalPatterns())
        assertTrue(saved?.has(BusinessModule.CUSTOMERS) == true)
        assertTrue(saved?.has(BusinessModule.INVENTORY) == false)

        repository.updateProfile(saved!!.copy(enabledModules = setOf(BusinessModule.CORE, BusinessModule.SALES)))
        val updated = repository.observeBusinessProfile().first()
        assertEquals(setOf(BusinessModule.CORE, BusinessModule.SALES), updated?.enabledModules)
        assertEquals("Padaria Central", updated?.storeName)
        database.close()
    }

    @Test
    fun permanentCapabilityAndPatternSurviveCloseAndReopen() = runBlocking {
        val firstDatabase = Room.databaseBuilder(context, TinoDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        val firstRepository = StoreProfileRepository(firstDatabase.storeProfileDao())
        firstRepository.save(
            storeName = "Comércio Composto",
            ownerName = "Carlos",
            phone = "86994209350",
            vertical = BusinessVertical.OTHER,
            activeModules = setOf(BusinessModule.CORE, BusinessModule.CUSTOMERS),
        )
        val saved = firstRepository.observeBusinessProfile().first()!!
        firstRepository.updateProfile(
            saved.copy(permanentCapabilities = setOf(com.tino.app.domain.agent.TinoCapabilityId.LIST_PRODUCTS)),
        )
        firstDatabase.close()

        val reopened = Room.databaseBuilder(context, TinoDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        val restored = StoreProfileRepository(reopened.storeProfileDao()).observeBusinessProfile().first()!!

        assertEquals(setOf(OperationalPattern.GENERAL), restored.effectiveOperationalPatterns())
        assertEquals(
            setOf(com.tino.app.domain.agent.TinoCapabilityId.LIST_PRODUCTS),
            restored.permanentCapabilities,
        )
        assertTrue(restored.has(BusinessModule.CUSTOMERS))
        assertTrue(restored.has(BusinessModule.INVENTORY).not())
        reopened.close()
    }
}
