package com.tino.app.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CatalogMigrationTest {
    @Test
    fun migration27To28AddsOnlyCatalogFieldsAndState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("catalog-migration-test.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(27) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE products (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, priceCents INTEGER NOT NULL, unit TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val database = helper.writableDatabase
            database.execSQL("INSERT INTO products(id, name, priceCents, unit, createdAt) VALUES ('p', 'Local', 100, 'UN', 1)")
            MIGRATION_27_28.migrate(database)

            database.query("PRAGMA table_info(products)").use { cursor ->
                val columns = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(1))
                }
                assertTrue("gtin" in columns)
            }
            database.query("SELECT name FROM products WHERE id = 'p'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Local", cursor.getString(0))
            }
            database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'catalog_sync_state'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
        } finally {
            helper.close()
            context.deleteDatabase("catalog-migration-test.db")
        }
    }

    @Test
    fun migration28To29DefaultsExistingProductsToTrackedStock() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("catalog-migration-29-test.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(28) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE products (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, priceCents INTEGER NOT NULL, unit TEXT NOT NULL, createdAt INTEGER NOT NULL, gtin TEXT)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val database = helper.writableDatabase
            database.execSQL("INSERT INTO products(id, name, priceCents, unit, createdAt, gtin) VALUES ('p', 'Local', 100, 'UN', 1, NULL)")
            MIGRATION_28_29.migrate(database)

            database.query("SELECT stockTracked FROM products WHERE id = 'p'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase("catalog-migration-29-test.db")
        }
    }
}
