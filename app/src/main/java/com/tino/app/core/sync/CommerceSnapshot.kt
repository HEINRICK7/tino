package com.tino.app.core.sync

import androidx.room.withTransaction
import com.tino.app.core.database.CreditEntryEntity
import com.tino.app.core.database.CustomerEntity
import com.tino.app.core.database.DomainEventEntity
import com.tino.app.core.database.DirectReceiptEntity
import com.tino.app.core.database.FiscalImportEntity
import com.tino.app.core.database.ProductEntity
import com.tino.app.core.database.ProductPurchaseHistoryEntity
import com.tino.app.core.database.AgentActivityEntity
import com.tino.app.core.database.PurchaseEntity
import com.tino.app.core.database.PurchaseItemEntity
import com.tino.app.core.database.SaleEntity
import com.tino.app.core.database.SaleItemEntity
import com.tino.app.core.database.StockMovementEntity
import com.tino.app.core.database.SupplierEntity
import com.tino.app.core.database.SupplierProductMappingEntity
import com.tino.app.core.database.SyncCursorEntity
import com.tino.app.core.database.TinoDatabase
import javax.inject.Inject
import javax.inject.Singleton

data class CommerceSnapshot(
    val schemaVersion: Int,
    val products: List<ProductEntity>,
    val customers: List<CustomerEntity>,
    val suppliers: List<SupplierEntity>,
    val sales: List<SaleEntity>,
    val directReceipts: List<DirectReceiptEntity> = emptyList(),
    val saleItems: List<SaleItemEntity>,
    val stockMovements: List<StockMovementEntity>,
    val creditEntries: List<CreditEntryEntity>,
    val purchases: List<PurchaseEntity>,
    val purchaseItems: List<PurchaseItemEntity>,
    val events: List<DomainEventEntity>,
    val cursors: List<SyncCursorEntity>,
    val fiscalImports: List<FiscalImportEntity> = emptyList(),
    val supplierProductMappings: List<SupplierProductMappingEntity> = emptyList(),
    val productPurchaseHistory: List<ProductPurchaseHistoryEntity> = emptyList(),
    val agentActivities: List<AgentActivityEntity> = emptyList(),
)

enum class RestorePolicy {
    REPLACE_EXISTING,
    REQUIRE_EMPTY,
}

class SnapshotValidationException(message: String) : IllegalArgumentException(message)

@Singleton
class CommerceSnapshotRepository @Inject constructor(
    private val database: TinoDatabase,
) {
    suspend fun export(): CommerceSnapshot = CommerceSnapshot(
        schemaVersion = 4,
        products = database.productDao().all(),
        customers = database.customerDao().all(),
        suppliers = database.supplierDao().all(),
        sales = database.saleDao().all(),
        directReceipts = database.directReceiptDao().all(),
        saleItems = database.saleDao().allItems(),
        stockMovements = database.stockMovementDao().all(),
        creditEntries = database.creditDao().all(),
        purchases = database.purchaseDao().all(),
        purchaseItems = database.purchaseDao().allItems(),
        events = database.domainEventDao().all(),
        cursors = database.syncCursorDao().all(),
        fiscalImports = database.fiscalImportDao().all(),
        supplierProductMappings = database.supplierProductMappingDao().all(),
        productPurchaseHistory = database.productPurchaseHistoryDao().all(),
        agentActivities = database.agentActivityDao().all(),
    )

    suspend fun restore(
        snapshot: CommerceSnapshot,
        policy: RestorePolicy = RestorePolicy.REPLACE_EXISTING,
    ) {
        validate(snapshot)
        if (policy == RestorePolicy.REQUIRE_EMPTY && !export().isEmpty()) {
            throw SnapshotValidationException("O aparelho já possui dados. Escolha uma restauração que substitua o estado atual.")
        }
        database.withTransaction {
            database.saleDao().clearItems()
            database.purchaseDao().clearItems()
            database.saleDao().clear()
            database.directReceiptDao().clear()
            database.purchaseDao().clear()
            database.stockMovementDao().clear()
            database.creditDao().clear()
            database.domainEventDao().clear()
            database.productDao().clear()
            database.customerDao().clear()
            database.supplierDao().clear()
            database.fiscalImportDao().clear()
            database.supplierProductMappingDao().clear()
            database.productPurchaseHistoryDao().clear()
            database.agentActivityDao().clear()
            database.syncCursorDao().clear()

            snapshot.products.forEach { database.productDao().insert(it) }
            snapshot.customers.forEach { database.customerDao().insert(it) }
            snapshot.suppliers.forEach { database.supplierDao().insert(it) }
            snapshot.sales.forEach { database.saleDao().insert(it) }
            snapshot.directReceipts.forEach { database.directReceiptDao().insert(it) }
            database.saleDao().insertItems(snapshot.saleItems)
            snapshot.stockMovements.forEach { database.stockMovementDao().insert(it) }
            snapshot.creditEntries.forEach { database.creditDao().insert(it) }
            snapshot.purchases.forEach { database.purchaseDao().insert(it) }
            database.purchaseDao().insertItems(snapshot.purchaseItems)
            snapshot.events.forEach { database.domainEventDao().insertIgnore(it) }
            snapshot.cursors.forEach { database.syncCursorDao().save(it) }
            snapshot.fiscalImports.forEach { database.fiscalImportDao().insert(it) }
            snapshot.supplierProductMappings.forEach { database.supplierProductMappingDao().insert(it) }
            snapshot.productPurchaseHistory.forEach { database.productPurchaseHistoryDao().insert(it) }
            snapshot.agentActivities.forEach { database.agentActivityDao().upsert(it) }
        }
    }

    private fun validate(snapshot: CommerceSnapshot) {
        if (snapshot.schemaVersion !in 1..CURRENT_SCHEMA_VERSION) {
            throw SnapshotValidationException("Snapshot incompatível com esta versão do TINO.")
        }
        validateUnique("produto", snapshot.products.map { it.id })
        validateUnique("cliente", snapshot.customers.map { it.id })
        validateUnique("fornecedor", snapshot.suppliers.map { it.id })
        validateUnique("venda", snapshot.sales.map { it.id })
        validateUnique("evento", snapshot.events.map { it.eventId })
        validateUnique("atividade", snapshot.agentActivities.map { it.id })
        snapshot.products.forEach { product ->
            if (product.id.isBlank() || product.name.isBlank() || product.priceCents <= 0) {
                throw SnapshotValidationException("Snapshot contém um produto inválido.")
            }
        }
        snapshot.customers.forEach { customer ->
            if (customer.id.isBlank() || customer.name.isBlank()) {
                throw SnapshotValidationException("Snapshot contém um cliente inválido.")
            }
        }
        // Historical/imported facts may legitimately arrive before a matching
        // projection is restored. Referential reconstruction belongs to the
        // domain applier; restore must reject corruption, not valid history.
        snapshot.events.forEach { event ->
            if (event.eventId.isBlank() || event.storeId.isBlank() || event.deviceId.isBlank() || event.type.isBlank()) {
                throw SnapshotValidationException("Snapshot contém evento sem identidade.")
            }
            try {
                org.json.JSONObject(event.payloadJson)
            } catch (_: Throwable) {
                throw SnapshotValidationException("Snapshot contém evento corrompido.")
            }
        }
        if (snapshot.cursors.any { it.scope.isBlank() || it.cursor.isBlank() }) {
            throw SnapshotValidationException("Snapshot contém cursor inválido.")
        }
    }

    private fun validateUnique(kind: String, ids: List<String>) {
        if (ids.any(String::isBlank) || ids.toSet().size != ids.size) {
            throw SnapshotValidationException("Snapshot contém IDs duplicados ou vazios em $kind.")
        }
    }

    private fun CommerceSnapshot.isEmpty(): Boolean =
        products.isEmpty() && customers.isEmpty() && suppliers.isEmpty() && sales.isEmpty() &&
            directReceipts.isEmpty() && saleItems.isEmpty() && stockMovements.isEmpty() &&
            creditEntries.isEmpty() && purchases.isEmpty() && purchaseItems.isEmpty() &&
            events.isEmpty() && cursors.isEmpty() && fiscalImports.isEmpty() &&
            supplierProductMappings.isEmpty() && productPurchaseHistory.isEmpty() && agentActivities.isEmpty()

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 4
    }
}
