package com.tino.app.domain.catalog

import kotlinx.coroutines.flow.Flow

/** Raw contract value. Price stays textual until BigDecimal validation is complete. */
data class RemoteCatalogProduct(
    val productId: String?,
    val name: String?,
    val baseUnit: String?,
    val gtin: String?,
    val price: String?,
)

enum class CatalogStockMode { TRACKED, MADE_TO_ORDER }

data class CatalogProduct(
    val productId: String,
    val name: String,
    val baseUnit: String,
    val gtin: String?,
    val priceCents: Long,
    val stockTracked: Boolean = true,
)

interface CatalogApi {
    /** Refreshes the configured external source; native catalogs are a no-op. */
    suspend fun syncExternalCatalog(businessId: String): CatalogStockMode

    suspend fun listProducts(
        businessId: String,
        query: String? = null,
        gtin: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): List<RemoteCatalogProduct>
}

data class CatalogSyncLogEntry(
    val timestamp: Long,
    val step: String,
    val status: String,
    val detail: String,
)

data class CatalogSyncDiagnostics(
    val businessId: String,
    val status: CatalogSyncStatus,
    val total: Int = 0,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val possiblyPartial: Boolean = false,
    val errorMessage: String? = null,
    val logs: List<CatalogSyncLogEntry> = emptyList(),
)

enum class CatalogUpsertOperation { CREATED, UPDATED }

data class CatalogUpsertFailure(val productId: String?, val reason: String)

data class CatalogUpsertResult(
    val created: Int,
    val updated: Int,
    val failures: List<CatalogUpsertFailure>,
)

interface CatalogProductStore {
    suspend fun upsert(products: List<CatalogProduct>): CatalogUpsertResult
}

enum class CatalogSyncStatus { IDLE, SYNCING, SUCCESS, PARTIAL, FAILED }

data class CatalogSyncState(
    val status: CatalogSyncStatus = CatalogSyncStatus.IDLE,
    val lastSuccessfulAt: Long? = null,
    val completedAt: Long? = null,
    val total: Int = 0,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val possiblyPartial: Boolean = false,
    val errorMessage: String? = null,
)

interface CatalogSyncStateStore {
    fun observe(businessId: String): Flow<CatalogSyncState?>
    suspend fun current(businessId: String): CatalogSyncState?
    suspend fun save(businessId: String, state: CatalogSyncState)
}
