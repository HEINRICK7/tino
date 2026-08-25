package com.tino.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ProductSummary(
    val id: String,
    val name: String,
    val priceCents: Long,
    val unit: String,
    val stockQuantity: Int,
)

data class OrderSummary(
    val id: String,
    val channel: String,
    val fulfillment: String,
    val customerName: String?,
    val status: String,
    val totalCents: Long,
    val createdAt: Long,
)

data class OrderDetail(
    val order: OrderEntity,
    val items: List<OrderItemEntity>,
)

data class RecommendationOutcomeCountRow(
    val outcome: String,
    val count: Int,
)

@Dao
interface ProductDao {
    @Query(
        """
        SELECT p.id, p.name, p.priceCents, p.unit,
               COALESCE(SUM(sm.quantityDelta), 0) AS stockQuantity
        FROM products p
        LEFT JOIN stock_movements sm ON sm.productId = p.id
        GROUP BY p.id, p.name, p.priceCents, p.unit
        ORDER BY p.name
        """,
    )
    fun observeAll(): Flow<List<ProductSummary>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): ProductEntity?

    @Query("SELECT * FROM products WHERE LOWER(name) LIKE LOWER(:query) ORDER BY name LIMIT 5")
    suspend fun searchByName(query: String): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity)

    @Query("UPDATE products SET priceCents = :priceCents WHERE id = :id")
    suspend fun updatePrice(id: String, priceCents: Long)

    @Query("SELECT * FROM products ORDER BY name")
    suspend fun all(): List<ProductEntity>

    @Query("DELETE FROM products")
    suspend fun clear()
}

@Dao
interface InteractionStateDao {
    @Query("SELECT * FROM interaction_states WHERE sessionId = :sessionId LIMIT 1")
    suspend fun findBySessionId(sessionId: String): InteractionStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: InteractionStateEntity)

    @Query("DELETE FROM interaction_states WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM interaction_states WHERE expiresAtEpochMs IS NOT NULL AND expiresAtEpochMs < :nowEpochMs")
    suspend fun deleteExpired(nowEpochMs: Long): Int
}

@Dao
interface BusinessMemoryDao {
    @Query("SELECT * FROM business_memory WHERE scopeKey = :scopeKey AND memoryKey = :memoryKey AND value = :value LIMIT 1")
    suspend fun find(scopeKey: String, memoryKey: String, value: String): BusinessMemoryEntity?

    @Query("SELECT * FROM business_memory WHERE scopeKey = :scopeKey AND memoryKey = :memoryKey ORDER BY supportCount DESC")
    suspend fun findByKey(scopeKey: String, memoryKey: String): List<BusinessMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BusinessMemoryEntity)

    @Query("SELECT * FROM business_memory WHERE scopeKey = :scopeKey ORDER BY memoryKey, value")
    suspend fun list(scopeKey: String): List<BusinessMemoryEntity>
}

@Dao
interface StoreProfileDao {
    @Query("SELECT * FROM store_profile WHERE id = 'default' LIMIT 1")
    fun observe(): Flow<StoreProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: StoreProfileEntity)
}

@Dao
interface SaleDao {
    @Insert
    suspend fun insert(sale: SaleEntity)

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SaleEntity?

    @Insert
    suspend fun insertItems(items: List<SaleItemEntity>)

    @Query("SELECT COUNT(*) FROM sales WHERE createdAt >= :startOfDay")
    fun observeTodayCount(startOfDay: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(totalCents), 0) FROM sales WHERE createdAt >= :startOfDay")
    fun observeTodayTotal(startOfDay: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(totalCents), 0) FROM sales WHERE createdAt >= :startOfDay AND paymentMethod = :paymentMethod")
    fun observeTodayTotalByPaymentMethod(startOfDay: Long, paymentMethod: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(totalCents), 0) FROM sales WHERE createdAt >= :startOfDay AND paymentMethod IN ('cash', 'pix', 'card')")
    fun observeTodayReceived(startOfDay: Long): Flow<Long>

    @Query("SELECT * FROM sales ORDER BY createdAt")
    suspend fun all(): List<SaleEntity>

    @Query("SELECT * FROM sale_items ORDER BY saleId, lineNumber")
    suspend fun allItems(): List<SaleItemEntity>

    @Query("DELETE FROM sale_items")
    suspend fun clearItems()

    @Query("DELETE FROM sales")
    suspend fun clear()
}

@Dao
interface DirectReceiptDao {
    @Insert
    suspend fun insert(receipt: DirectReceiptEntity)

    @Query("SELECT * FROM direct_receipts WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): DirectReceiptEntity?

    @Query("SELECT * FROM direct_receipts WHERE operationId = :operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): DirectReceiptEntity?

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM direct_receipts WHERE occurredAt >= :startOfDay")
    fun observeTodayTotal(startOfDay: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM direct_receipts WHERE occurredAt >= :startOfDay AND paymentMethod = :paymentMethod")
    fun observeTodayTotalByPaymentMethod(startOfDay: Long, paymentMethod: String): Flow<Long>

    @Query("SELECT * FROM direct_receipts ORDER BY occurredAt")
    suspend fun all(): List<DirectReceiptEntity>

    @Query("DELETE FROM direct_receipts")
    suspend fun clear()
}

data class FinancialSummaryRow(
    val receivedTotalCents: Long,
    val receivedCashCents: Long,
    val receivedPixCents: Long,
    val receivedCardCents: Long,
    val receivedUnknownCents: Long,
    val totalReceivableCents: Long,
    val creditCreatedCents: Long,
    val creditPaymentsReceivedCents: Long,
)

@Dao
interface FinancialProjectionDao {
    @Query(
        """
        SELECT
            COALESCE((SELECT SUM(totalCents) FROM sales
                WHERE createdAt >= :startAt AND createdAt < :endAt
                AND paymentMethod IN ('cash', 'pix', 'card', 'unknown')), 0)
            + COALESCE((SELECT SUM(amountCents) FROM direct_receipts
                WHERE occurredAt >= :startAt AND occurredAt < :endAt
                AND paymentMethod IN ('cash', 'pix', 'card', 'unknown')), 0)
            + COALESCE((SELECT SUM(-amountCents) FROM credit_entries
                WHERE type = 'PAYMENT' AND occurredAt >= :startAt AND occurredAt < :endAt
                AND paymentMethod IN ('cash', 'pix', 'card', 'unknown')), 0)
                AS receivedTotalCents,

            COALESCE((SELECT SUM(totalCents) FROM sales
                WHERE createdAt >= :startAt AND createdAt < :endAt AND paymentMethod = 'cash'), 0)
            + COALESCE((SELECT SUM(amountCents) FROM direct_receipts
                WHERE occurredAt >= :startAt AND occurredAt < :endAt AND paymentMethod = 'cash'), 0)
            + COALESCE((SELECT SUM(-amountCents) FROM credit_entries
                WHERE type = 'PAYMENT' AND occurredAt >= :startAt AND occurredAt < :endAt AND paymentMethod = 'cash'), 0)
                AS receivedCashCents,

            COALESCE((SELECT SUM(totalCents) FROM sales
                WHERE createdAt >= :startAt AND createdAt < :endAt AND paymentMethod = 'pix'), 0)
            + COALESCE((SELECT SUM(amountCents) FROM direct_receipts
                WHERE occurredAt >= :startAt AND occurredAt < :endAt AND paymentMethod = 'pix'), 0)
            + COALESCE((SELECT SUM(-amountCents) FROM credit_entries
                WHERE type = 'PAYMENT' AND occurredAt >= :startAt AND occurredAt < :endAt AND paymentMethod = 'pix'), 0)
                AS receivedPixCents,

            COALESCE((SELECT SUM(totalCents) FROM sales
                WHERE createdAt >= :startAt AND createdAt < :endAt AND paymentMethod = 'card'), 0)
            + COALESCE((SELECT SUM(amountCents) FROM direct_receipts
                WHERE occurredAt >= :startAt AND occurredAt < :endAt AND paymentMethod = 'card'), 0)
            + COALESCE((SELECT SUM(-amountCents) FROM credit_entries
                WHERE type = 'PAYMENT' AND occurredAt >= :startAt AND occurredAt < :endAt AND paymentMethod = 'card'), 0)
                AS receivedCardCents,

            COALESCE((SELECT SUM(totalCents) FROM sales
                WHERE createdAt >= :startAt AND createdAt < :endAt AND paymentMethod = 'unknown'), 0)
            + COALESCE((SELECT SUM(amountCents) FROM direct_receipts
                WHERE occurredAt >= :startAt AND occurredAt < :endAt AND paymentMethod = 'unknown'), 0)
            + COALESCE((SELECT SUM(-amountCents) FROM credit_entries
                WHERE type = 'PAYMENT' AND occurredAt >= :startAt AND occurredAt < :endAt AND paymentMethod = 'unknown'), 0)
                AS receivedUnknownCents,

            COALESCE((SELECT SUM(amountCents) FROM credit_entries), 0)
                AS totalReceivableCents,
            COALESCE((SELECT SUM(amountCents) FROM credit_entries
                WHERE type = 'SALE' AND occurredAt >= :startAt AND occurredAt < :endAt), 0)
                AS creditCreatedCents,
            COALESCE((SELECT SUM(-amountCents) FROM credit_entries
                WHERE type = 'PAYMENT' AND occurredAt >= :startAt AND occurredAt < :endAt), 0)
                AS creditPaymentsReceivedCents
        """,
    )
    fun observeSummary(startAt: Long, endAt: Long): Flow<FinancialSummaryRow>
}

@Dao
interface StockMovementDao {
    @Insert
    suspend fun insert(movement: StockMovementEntity)

    @Query("SELECT * FROM stock_movements WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): StockMovementEntity?

    @Query("SELECT COALESCE(SUM(quantityDelta), 0) FROM stock_movements WHERE productId = :productId")
    suspend fun balance(productId: String): Int

    @Query("SELECT COALESCE(SUM(quantityDelta), 0) FROM stock_movements WHERE productId = :productId")
    fun observeBalance(productId: String): Flow<Int>

    @Query("SELECT * FROM stock_movements ORDER BY occurredAt")
    suspend fun all(): List<StockMovementEntity>

    @Query("DELETE FROM stock_movements")
    suspend fun clear()
}

@Dao
interface RecommendationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(recommendations: List<RecommendationEntity>)

    @Query("SELECT * FROM recommendations WHERE decision = 'PENDING' ORDER BY createdAtEpochMs DESC")
    suspend fun pending(): List<RecommendationEntity>

    @Query("SELECT * FROM recommendations WHERE decision = 'PENDING' ORDER BY createdAtEpochMs DESC")
    fun observePending(): Flow<List<RecommendationEntity>>

    @Query("SELECT id FROM recommendations WHERE decision = 'PENDING' AND createdAtEpochMs < :beforeEpochMs")
    suspend fun stalePendingIds(beforeEpochMs: Long): List<String>

    @Query("UPDATE recommendations SET decision = 'EXPIRED' WHERE decision = 'PENDING' AND createdAtEpochMs < :beforeEpochMs")
    suspend fun expirePending(beforeEpochMs: Long): Int

    @Query("UPDATE recommendations SET decision = :decision WHERE id = :id")
    suspend fun updateDecision(id: String, decision: String): Int

    @Query("SELECT * FROM recommendations WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RecommendationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOutcome(outcome: RecommendationOutcomeEntity): Long

    @Query("SELECT outcome, COUNT(*) AS count FROM recommendation_outcomes GROUP BY outcome")
    fun observeOutcomeCounts(): Flow<List<RecommendationOutcomeCountRow>>
}

@Dao
interface DomainEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(event: DomainEventEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM domain_events WHERE eventId = :eventId)")
    suspend fun exists(eventId: String): Boolean

    @Insert
    suspend fun insert(event: DomainEventEntity)

    @Query("SELECT * FROM domain_events WHERE eventId = :eventId LIMIT 1")
    suspend fun findById(eventId: String): DomainEventEntity?

    @Query("SELECT * FROM domain_events WHERE syncStatus IN ('PENDING', 'FAILED') ORDER BY occurredAt LIMIT :limit")
    suspend fun pending(limit: Int): List<DomainEventEntity>

    @Query("SELECT COUNT(*) FROM domain_events WHERE syncStatus IN ('PENDING', 'SYNCING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    /** A process killed during push must never strand events outside the retry queue. */
    @Query("UPDATE domain_events SET syncStatus = 'PENDING', lastError = 'Tentativa de sincronização interrompida.' WHERE syncStatus = 'SYNCING'")
    suspend fun recoverInFlight()

    @Query("UPDATE domain_events SET syncStatus = :status, attempts = attempts + 1, lastError = :error WHERE eventId = :eventId")
    suspend fun updateStatus(eventId: String, status: SyncStatus, error: String? = null)

    @Query("UPDATE domain_events SET syncStatus = 'SYNCING' WHERE eventId IN (:eventIds)")
    suspend fun markSyncing(eventIds: List<String>)

    @Query("UPDATE domain_events SET syncStatus = 'SYNCED', lastError = NULL WHERE eventId IN (:eventIds)")
    suspend fun markSynced(eventIds: List<String>)

    @Query("SELECT * FROM domain_events ORDER BY occurredAt")
    suspend fun all(): List<DomainEventEntity>

    @Query("DELETE FROM domain_events")
    suspend fun clear()
}

@Dao
interface AgentActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activity: AgentActivityEntity)

    @Query("SELECT * FROM agent_activities ORDER BY occurredAt ASC, id ASC")
    suspend fun all(): List<AgentActivityEntity>

    @Query("SELECT * FROM agent_activities WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AgentActivityEntity?

    @Query("SELECT * FROM agent_activities WHERE operationId = :operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): AgentActivityEntity?

    @Query("DELETE FROM agent_activities")
    suspend fun clear()
}

@Dao
interface IntelligenceTelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: IntelligenceTelemetryEntity)

    @Query("SELECT * FROM intelligence_telemetry ORDER BY occurredAtEpochMs DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<IntelligenceTelemetryEntity>
}

@Dao
interface MutationOperationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: MutationOperationEntity)

    @Query("SELECT * FROM mutation_operations WHERE operationId = :operationId LIMIT 1")
    suspend fun findById(operationId: String): MutationOperationEntity?

    @Query("UPDATE mutation_operations SET status = 'EXECUTING' WHERE operationId = :operationId AND idempotencyKey = :idempotencyKey AND status = 'PENDING'")
    suspend fun reserve(operationId: String, idempotencyKey: String): Int

    @Query("UPDATE mutation_operations SET status = :status WHERE operationId = :operationId AND idempotencyKey = :idempotencyKey AND status = 'EXECUTING'")
    suspend fun markCommitted(operationId: String, idempotencyKey: String, status: String = "COMMITTED"): Int

    @Query("UPDATE mutation_operations SET status = 'PENDING' WHERE operationId = :operationId AND idempotencyKey = :idempotencyKey AND status = 'EXECUTING'")
    suspend fun release(operationId: String, idempotencyKey: String): Int

    @Query("DELETE FROM mutation_operations WHERE operationId = :operationId AND status = 'PENDING'")
    suspend fun deletePendingById(operationId: String)
}

@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(customer: CustomerEntity)

    @Query("UPDATE customers SET name = :name, phone = :phone WHERE id = :id")
    suspend fun updateProfile(id: String, name: String, phone: String?)

    @Query("SELECT * FROM customers ORDER BY name")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE LOWER(name) LIKE LOWER(:query) ORDER BY name LIMIT 5")
    suspend fun searchByName(query: String): List<CustomerEntity>

    @Query("SELECT * FROM customers ORDER BY name")
    suspend fun all(): List<CustomerEntity>

    @Query("DELETE FROM customers")
    suspend fun clear()
}

data class CustomerBalance(
    val id: String,
    val name: String,
    val phone: String?,
    val balanceCents: Long,
)

@Dao
interface CreditDao {
    @Insert
    suspend fun insert(entry: CreditEntryEntity)

    @Query("SELECT * FROM credit_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CreditEntryEntity?

    @Query("SELECT * FROM credit_entries WHERE referenceId = :referenceId AND type = 'SALE' LIMIT 1")
    suspend fun findReversalByReference(referenceId: String): CreditEntryEntity?

    @Query(
        """
        SELECT c.id, c.name, c.phone, COALESCE(SUM(ce.amountCents), 0) AS balanceCents
        FROM customers c LEFT JOIN credit_entries ce ON ce.customerId = c.id
        GROUP BY c.id, c.name, c.phone ORDER BY c.name
        """,
    )
    fun observeBalances(): Flow<List<CustomerBalance>>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM credit_entries WHERE customerId = :customerId")
    suspend fun balance(customerId: String): Long

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM credit_entries")
    fun observeTotalBalance(): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(-amountCents), 0) FROM credit_entries " +
            "WHERE type = 'PAYMENT' AND paymentMethod = :paymentMethod AND occurredAt >= :startOfDay",
    )
    fun observeTodayPaymentReceived(startOfDay: Long, paymentMethod: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(-amountCents), 0) FROM credit_entries WHERE type = 'PAYMENT' AND occurredAt >= :startOfDay")
    fun observeTodayPaymentReceivedTotal(startOfDay: Long): Flow<Long>

    @Query("SELECT * FROM credit_entries ORDER BY occurredAt")
    suspend fun all(): List<CreditEntryEntity>

    @Query("DELETE FROM credit_entries")
    suspend fun clear()
}

@Dao
interface SupplierDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(supplier: SupplierEntity)

    @Query("SELECT * FROM suppliers ORDER BY name")
    fun observeAll(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE LOWER(name) LIKE LOWER(:query) ORDER BY name LIMIT 5")
    suspend fun searchByName(query: String): List<SupplierEntity>

    @Query("SELECT * FROM suppliers ORDER BY name")
    suspend fun all(): List<SupplierEntity>

    @Query("DELETE FROM suppliers")
    suspend fun clear()
}

@Dao
interface PurchaseDao {
    @Insert
    suspend fun insert(purchase: PurchaseEntity)

    @Query("SELECT * FROM purchases WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PurchaseEntity?

    @Insert
    suspend fun insertItems(items: List<PurchaseItemEntity>)

    @Query("SELECT * FROM purchases ORDER BY createdAt DESC")
    suspend fun all(): List<PurchaseEntity>

    @Query("SELECT * FROM purchase_items ORDER BY purchaseId, lineNumber")
    suspend fun allItems(): List<PurchaseItemEntity>

    @Query("DELETE FROM purchase_items")
    suspend fun clearItems()

    @Query("DELETE FROM purchases")
    suspend fun clear()
}

@Dao
interface OrderDao {
    @Query("SELECT id, channel, fulfillment, customerName, status, totalCents, createdAt FROM orders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OrderSummary>>

    @Insert
    suspend fun insert(order: OrderEntity)

    @Insert
    suspend fun insertItems(items: List<OrderItemEntity>)

    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): OrderEntity?

    @Query("SELECT * FROM order_items WHERE orderId = :orderId ORDER BY lineNumber")
    suspend fun items(orderId: String): List<OrderItemEntity>

    @Query("UPDATE orders SET status = :status WHERE id = :orderId")
    suspend fun updateStatus(orderId: String, status: String)

    @Query("DELETE FROM order_items")
    suspend fun clearItems()

    @Query("DELETE FROM orders")
    suspend fun clear()
}

@Dao
interface FiscalImportDao {
    @Insert
    suspend fun insert(fiscalImport: FiscalImportEntity)

    @Query("SELECT * FROM fiscal_imports WHERE documentId = :documentId LIMIT 1")
    suspend fun findByDocumentId(documentId: String): FiscalImportEntity?

    @Query("SELECT * FROM fiscal_imports WHERE operationId = :operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): FiscalImportEntity?

    @Query("SELECT * FROM fiscal_imports ORDER BY committedAt")
    suspend fun all(): List<FiscalImportEntity>

    @Query("DELETE FROM fiscal_imports")
    suspend fun clear()
}

@Dao
interface SupplierProductMappingDao {
    @Insert
    suspend fun insert(mapping: SupplierProductMappingEntity)

    @Query("SELECT * FROM supplier_product_mappings WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SupplierProductMappingEntity?

    @Query("SELECT * FROM supplier_product_mappings ORDER BY confirmedAt")
    suspend fun all(): List<SupplierProductMappingEntity>

    @Query("DELETE FROM supplier_product_mappings")
    suspend fun clear()
}

@Dao
interface ProductPurchaseHistoryDao {
    @Insert
    suspend fun insert(history: ProductPurchaseHistoryEntity)

    @Query("SELECT * FROM product_purchase_history WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ProductPurchaseHistoryEntity?

    @Insert
    suspend fun insertAll(history: List<ProductPurchaseHistoryEntity>)

    @Query("SELECT * FROM product_purchase_history ORDER BY purchasedAt")
    suspend fun all(): List<ProductPurchaseHistoryEntity>

    @Query("DELETE FROM product_purchase_history")
    suspend fun clear()
}

@Dao
interface SyncCursorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(cursor: SyncCursorEntity)

    @Query("SELECT * FROM sync_cursors WHERE scope = :scope LIMIT 1")
    suspend fun find(scope: String): SyncCursorEntity?

    @Query("SELECT * FROM sync_cursors")
    suspend fun all(): List<SyncCursorEntity>

    @Query("DELETE FROM sync_cursors")
    suspend fun clear()
}
