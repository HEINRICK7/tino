package com.tino.app.core.catalog

import androidx.room.withTransaction
import com.tino.app.core.database.CatalogSyncStateDao
import com.tino.app.core.database.CatalogSyncStateEntity
import com.tino.app.core.database.ProductDao
import com.tino.app.core.database.ProductEntity
import com.tino.app.core.database.TinoDatabase
import com.tino.app.domain.catalog.CatalogProduct
import com.tino.app.domain.catalog.CatalogProductStore
import com.tino.app.domain.catalog.CatalogSyncState
import com.tino.app.domain.catalog.CatalogSyncStateStore
import com.tino.app.domain.catalog.CatalogSyncStatus
import com.tino.app.domain.catalog.CatalogUpsertFailure
import com.tino.app.domain.catalog.CatalogUpsertResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomCatalogProductStore @Inject constructor(
    private val database: TinoDatabase,
    private val productDao: ProductDao,
) : CatalogProductStore {
    override suspend fun upsert(products: List<CatalogProduct>): CatalogUpsertResult = database.withTransaction {
        var created = 0
        var updated = 0
        val failures = mutableListOf<CatalogUpsertFailure>()
        products.forEach { product ->
            runCatching {
                val existing = productDao.findById(product.productId)
                if (existing == null) {
                    val inserted = productDao.insertCatalogProduct(
                        ProductEntity(
                            id = product.productId,
                            name = product.name,
                            priceCents = product.priceCents,
                            unit = product.baseUnit,
                            createdAt = System.currentTimeMillis(),
                            gtin = product.gtin,
                            stockTracked = product.stockTracked,
                        ),
                    )
                    check(inserted != -1L) { "nome de produto já cadastrado" }
                    created++
                } else {
                    productDao.updateCatalogFields(
                        id = existing.id,
                        name = product.name,
                        priceCents = product.priceCents,
                        unit = product.baseUnit,
                        gtin = product.gtin,
                        stockTracked = product.stockTracked,
                    )
                    updated++
                }
            }.onFailure {
                failures += CatalogUpsertFailure(
                    product.productId,
                    it.message?.takeIf(String::isNotBlank) ?: "item não aplicado",
                )
            }
        }
        CatalogUpsertResult(created, updated, failures)
    }
}

@Singleton
class RoomCatalogSyncStateStore @Inject constructor(
    private val dao: CatalogSyncStateDao,
) : CatalogSyncStateStore {
    override fun observe(businessId: String): Flow<CatalogSyncState?> = dao.observe(businessId).map { it?.toDomain() }

    override suspend fun current(businessId: String): CatalogSyncState? = dao.find(businessId)?.toDomain()

    override suspend fun save(businessId: String, state: CatalogSyncState) {
        dao.upsert(
            CatalogSyncStateEntity(
                businessId = businessId,
                status = state.status.name,
                lastSuccessfulAt = state.lastSuccessfulAt,
                completedAt = state.completedAt,
                total = state.total,
                accepted = state.accepted,
                rejected = state.rejected,
                possiblyPartial = state.possiblyPartial,
                errorMessage = state.errorMessage,
            ),
        )
    }
}

private fun CatalogSyncStateEntity.toDomain(): CatalogSyncState = CatalogSyncState(
    status = runCatching { CatalogSyncStatus.valueOf(status) }.getOrDefault(CatalogSyncStatus.FAILED),
    lastSuccessfulAt = lastSuccessfulAt,
    completedAt = completedAt,
    total = total,
    accepted = accepted,
    rejected = rejected,
    possiblyPartial = possiblyPartial,
    errorMessage = errorMessage,
)
