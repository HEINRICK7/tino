package com.tino.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class TinoConverters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromCreditEntryType(type: CreditEntryType): String = type.name

    @TypeConverter
    fun toCreditEntryType(value: String): CreditEntryType = CreditEntryType.valueOf(value)

    @TypeConverter
    fun fromPurchaseStatus(status: PurchaseStatus): String = status.name

    @TypeConverter
    fun toPurchaseStatus(value: String): PurchaseStatus = PurchaseStatus.valueOf(value)

    @TypeConverter
    fun fromFiscalImportStatus(status: FiscalImportStatus): String = status.name

    @TypeConverter
    fun toFiscalImportStatus(value: String): FiscalImportStatus = FiscalImportStatus.valueOf(value)
}

@Database(
    entities = [
        ProductEntity::class,
        SaleEntity::class,
        DirectReceiptEntity::class,
        SaleItemEntity::class,
        StockMovementEntity::class,
        CustomerEntity::class,
        SupplierEntity::class,
        CreditEntryEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        DomainEventEntity::class,
        SyncCursorEntity::class,
        StoreProfileEntity::class,
        FiscalImportEntity::class,
        SupplierProductMappingEntity::class,
        ProductPurchaseHistoryEntity::class,
        AgentActivityEntity::class,
        IntelligenceTelemetryEntity::class,
        InteractionStateEntity::class,
        MutationOperationEntity::class,
        BusinessMemoryEntity::class,
        RecommendationEntity::class,
    ],
    version = 18,
    exportSchema = true,
)
@TypeConverters(TinoConverters::class)
abstract class TinoDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun directReceiptDao(): DirectReceiptDao
    abstract fun financialProjectionDao(): FinancialProjectionDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun domainEventDao(): DomainEventDao
    abstract fun customerDao(): CustomerDao
    abstract fun supplierDao(): SupplierDao
    abstract fun creditDao(): CreditDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun orderDao(): OrderDao
    abstract fun fiscalImportDao(): FiscalImportDao
    abstract fun supplierProductMappingDao(): SupplierProductMappingDao
    abstract fun productPurchaseHistoryDao(): ProductPurchaseHistoryDao
    abstract fun syncCursorDao(): SyncCursorDao
    abstract fun storeProfileDao(): StoreProfileDao
    abstract fun agentActivityDao(): AgentActivityDao
    abstract fun intelligenceTelemetryDao(): IntelligenceTelemetryDao
    abstract fun interactionStateDao(): InteractionStateDao
    abstract fun mutationOperationDao(): MutationOperationDao
    abstract fun businessMemoryDao(): BusinessMemoryDao
    abstract fun recommendationDao(): RecommendationDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS customers (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, phone TEXT, createdAt INTEGER NOT NULL)",
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_customers_name ON customers(name)")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS suppliers (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, phone TEXT, createdAt INTEGER NOT NULL)",
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_suppliers_name ON suppliers(name)")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS credit_entries (id TEXT NOT NULL PRIMARY KEY, customerId TEXT NOT NULL, amountCents INTEGER NOT NULL, type TEXT NOT NULL, referenceId TEXT, occurredAt INTEGER NOT NULL)",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_credit_entries_customerId ON credit_entries(customerId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_credit_entries_occurredAt ON credit_entries(occurredAt)")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS purchases (id TEXT NOT NULL PRIMARY KEY, supplierId TEXT, status TEXT NOT NULL, totalCostCents INTEGER NOT NULL, createdAt INTEGER NOT NULL)",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_purchases_supplierId ON purchases(supplierId)")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS purchase_items (purchaseId TEXT NOT NULL, lineNumber INTEGER NOT NULL, productId TEXT NOT NULL, quantity INTEGER NOT NULL, unitCostCents INTEGER NOT NULL, PRIMARY KEY(purchaseId, lineNumber))",
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS sync_cursors (scope TEXT NOT NULL PRIMARY KEY, cursor TEXT NOT NULL, updatedAt INTEGER NOT NULL)",
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS store_profile (id TEXT NOT NULL PRIMARY KEY, storeName TEXT NOT NULL, ownerName TEXT NOT NULL, phone TEXT NOT NULL, createdAt INTEGER NOT NULL)",
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS direct_receipts (id TEXT NOT NULL PRIMARY KEY, amountCents INTEGER NOT NULL, paymentMethod TEXT NOT NULL, occurredAt INTEGER NOT NULL, source TEXT NOT NULL, note TEXT, operationId TEXT NOT NULL)",
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_direct_receipts_operationId ON direct_receipts(operationId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_direct_receipts_occurredAt ON direct_receipts(occurredAt)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE credit_entries ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'unknown'")
        database.execSQL("UPDATE credit_entries SET paymentMethod = 'credit' WHERE type = 'SALE'")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE credit_entries ADD COLUMN dueAt INTEGER")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE suppliers ADD COLUMN taxId TEXT")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_suppliers_taxId ON suppliers(taxId)")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS fiscal_imports (id TEXT NOT NULL PRIMARY KEY, documentId TEXT NOT NULL, accessKey TEXT, documentHashSha256 TEXT NOT NULL, operationId TEXT NOT NULL, status TEXT NOT NULL, committedAt INTEGER NOT NULL, originalXml BLOB NOT NULL)",
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fiscal_imports_documentId ON fiscal_imports(documentId)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fiscal_imports_operationId ON fiscal_imports(operationId)")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS supplier_product_mappings (id TEXT NOT NULL PRIMARY KEY, supplierId TEXT NOT NULL, supplierProductCode TEXT, gtin TEXT, supplierDescription TEXT NOT NULL, productId TEXT NOT NULL, confirmedAt INTEGER NOT NULL, matchMethod TEXT NOT NULL)",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_product_mappings_supplierId ON supplier_product_mappings(supplierId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_product_mappings_productId ON supplier_product_mappings(productId)")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS product_purchase_history (id TEXT NOT NULL PRIMARY KEY, fiscalDocumentId TEXT NOT NULL, supplierId TEXT NOT NULL, productId TEXT NOT NULL, purchasedAt INTEGER NOT NULL, fiscalQuantity TEXT NOT NULL, stockQuantity INTEGER NOT NULL, unitPurchaseCostCents INTEGER NOT NULL, totalCostCents INTEGER NOT NULL)",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_product_purchase_history_productId ON product_purchase_history(productId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_product_purchase_history_fiscalDocumentId ON product_purchase_history(fiscalDocumentId)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS agent_activities (id TEXT NOT NULL PRIMARY KEY, capability TEXT NOT NULL, operationId TEXT, occurredAt INTEGER NOT NULL, source TEXT NOT NULL, summary TEXT NOT NULL, summaryKind TEXT, summaryPayloadJson TEXT, undoPolicy TEXT, compensatingCapability TEXT, undoDeadline INTEGER, undoState TEXT NOT NULL, status TEXT NOT NULL, compensatesActivityId TEXT)",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_agent_activities_occurredAt ON agent_activities(occurredAt)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_agent_activities_operationId ON agent_activities(operationId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_agent_activities_undoState ON agent_activities(undoState)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS intelligence_telemetry (id TEXT NOT NULL PRIMARY KEY, requestId TEXT NOT NULL, plannerUsed TEXT NOT NULL, planJson TEXT NOT NULL, validationResult TEXT NOT NULL, validationErrorsJson TEXT NOT NULL, fallbackUsed INTEGER NOT NULL, executionResult TEXT NOT NULL, latencyMs INTEGER NOT NULL, planningLatencyMs INTEGER NOT NULL, errorStage TEXT NOT NULL, occurredAtEpochMs INTEGER NOT NULL)",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_intelligence_telemetry_requestId ON intelligence_telemetry(requestId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_intelligence_telemetry_plannerUsed ON intelligence_telemetry(plannerUsed)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_intelligence_telemetry_occurredAtEpochMs ON intelligence_telemetry(occurredAtEpochMs)")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE intelligence_telemetry ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE intelligence_telemetry ADD COLUMN plannerSelected TEXT NOT NULL DEFAULT 'DETERMINISTIC'")
        database.execSQL("ALTER TABLE intelligence_telemetry ADD COLUMN fallbackReason TEXT")
        database.execSQL("ALTER TABLE intelligence_telemetry ADD COLUMN validationRejectionKindsJson TEXT NOT NULL DEFAULT '[]'")
        database.execSQL("ALTER TABLE intelligence_telemetry ADD COLUMN groundingCompleteness TEXT NOT NULL DEFAULT 'NOT_RUN'")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE intelligence_telemetry ADD COLUMN loopId TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE intelligence_telemetry ADD COLUMN turnIndex INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE intelligence_telemetry ADD COLUMN loopState TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE intelligence_telemetry ADD COLUMN decision TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS interaction_states (sessionId TEXT NOT NULL PRIMARY KEY, stateJson TEXT NOT NULL, persistencePolicy TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL, expiresAtEpochMs INTEGER)",
        )
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS mutation_operations (operationId TEXT NOT NULL PRIMARY KEY, capabilityId TEXT NOT NULL, argumentsJson TEXT NOT NULL, risk TEXT NOT NULL, requiresConfirmation INTEGER NOT NULL, idempotencyKey TEXT NOT NULL, previewFingerprint TEXT NOT NULL, confirmationTokenHash TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, expiresAtEpochMs INTEGER NOT NULL, status TEXT NOT NULL)",
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_mutation_operations_idempotencyKey ON mutation_operations(idempotencyKey)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_mutation_operations_status ON mutation_operations(status)")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS business_memory (id TEXT NOT NULL PRIMARY KEY, scopeKey TEXT NOT NULL, memoryKey TEXT NOT NULL, value TEXT NOT NULL, kind TEXT NOT NULL, lifecycle TEXT NOT NULL, confidence REAL NOT NULL, supportCount INTEGER NOT NULL, contradictionCount INTEGER NOT NULL, provenanceJson TEXT NOT NULL, sourceEventIdsJson TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL, demotionReason TEXT)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_business_memory_scopeKey_memoryKey_value ON business_memory(scopeKey, memoryKey, value)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_business_memory_scopeKey ON business_memory(scopeKey)")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE store_profile ADD COLUMN businessVertical TEXT NOT NULL DEFAULT 'RETAIL'")
        database.execSQL("ALTER TABLE store_profile ADD COLUMN activeModules TEXT NOT NULL DEFAULT 'CORE,SALES,INVENTORY,CUSTOMERS,CREDIT,STOCK_ENTRY,FISCAL'")
        database.execSQL("ALTER TABLE store_profile ADD COLUMN profileVersion INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE store_profile ADD COLUMN operationalPatterns TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE store_profile ADD COLUMN permanentCapabilities TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS orders (id TEXT NOT NULL PRIMARY KEY, channel TEXT NOT NULL, fulfillment TEXT NOT NULL, customerName TEXT, addressReference TEXT, status TEXT NOT NULL, totalCents INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_orders_createdAt ON orders(createdAt)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_orders_status ON orders(status)")
        database.execSQL("CREATE TABLE IF NOT EXISTS order_items (orderId TEXT NOT NULL, lineNumber INTEGER NOT NULL, productId TEXT NOT NULL, productName TEXT NOT NULL, quantity INTEGER NOT NULL, unitPriceCents INTEGER NOT NULL, PRIMARY KEY(orderId, lineNumber))")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS recommendations (id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, productId TEXT NOT NULL, message TEXT NOT NULL, confidence REAL NOT NULL, decision TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, stockQuantity INTEGER, unitsSoldLast30Days INTEGER, rule TEXT, windowDays INTEGER)",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_recommendations_productId ON recommendations(productId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_recommendations_createdAtEpochMs ON recommendations(createdAtEpochMs)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_recommendations_decision ON recommendations(decision)")
    }
}
