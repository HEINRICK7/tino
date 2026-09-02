package com.tino.app.domain.catalog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCatalogTest {
    @Test
    fun convertsBackendPricesToExactCentsWithoutFloatingPoint() {
        assertEquals(6500L, "65.00".toPriceCents())
        assertEquals(6990L, "69.90".toPriceCents())
        assertEquals(6500L, "65.000000000".toPriceCents())
    }

    @Test
    fun rejectsNullNegativeNonNumericAndFractionalCentPrices() {
        listOf(null, "", "-1.00", "NaN", "Infinity", "0.001", "1.999").forEach { raw ->
            runCatching { raw.toPriceCents() }.onSuccess {
                throw AssertionError("Preço deveria ser rejeitado: $raw")
            }
        }
    }

    @Test
    fun syncAcceptsValidItemsRejectsInvalidItemsAndIsIdempotent() = runBlocking {
        val api = FakeCatalogApi(
            listOf(
                RemoteCatalogProduct("p-1", "Cerveja", "UN", null, "65.00"),
                RemoteCatalogProduct("p-2", "Caixa", "CX", "789", "69.90"),
                RemoteCatalogProduct("p-bad", "Produto inválido", "UN", null, "0.001"),
            ),
        )
        val store = FakeProductStore()
        val state = FakeStateStore()
        val sync = SyncCatalog(api, store, state)

        val first = sync("business-1")
        val second = sync("business-1")

        assertEquals(CatalogSyncStatus.PARTIAL, first.status)
        assertEquals(2, first.accepted)
        assertEquals(1, first.rejected)
        assertEquals(2, first.created)
        assertEquals(2, second.updated)
        assertEquals(2, store.products.size)
        assertEquals(6990L, store.products.getValue("p-2").priceCents)
        assertEquals(CatalogSyncStatus.PARTIAL, state.value.value?.status)
    }

    @Test
    fun syncReadsEveryPageAndDoesNotMarkACompleteCatalogAsLimited() = runBlocking {
        val products = (1..205).map { index ->
            RemoteCatalogProduct("p-$index", "Produto $index", "UN", null, "10.00")
        }
        val store = FakeProductStore()
        val result = SyncCatalog(FakeCatalogApi(products), store, FakeStateStore())("business-1")

        assertEquals(CatalogSyncStatus.SUCCESS, result.status)
        assertEquals(205, result.total)
        assertEquals(205, result.accepted)
        assertEquals(0, result.rejected)
        assertEquals(false, result.possiblyPartial)
        assertEquals(205, store.products.size)
    }

    @Test
    fun madeToOrderExternalCatalogDoesNotTrackStock() = runBlocking {
        val store = FakeProductStore()
        val result = SyncCatalog(
            FakeCatalogApi(listOf(RemoteCatalogProduct("p-1", "Bolo", "UN", null, "50.00")), CatalogStockMode.MADE_TO_ORDER),
            store,
            FakeStateStore(),
        )("business-1")

        assertEquals(CatalogSyncStatus.SUCCESS, result.status)
        assertEquals(false, store.products.getValue("p-1").stockTracked)
    }

    @Test
    fun backendFailureDoesNotTouchProductStoreAndReturnsSanitizedError() = runBlocking {
        val store = FakeProductStore()
        store.products["local"] = CatalogProduct("local", "Local", "UN", null, 100)
        val sync = SyncCatalog(FailingCatalogApi(), store, FakeStateStore())

        val failure = runCatching { sync("business-1") }.exceptionOrNull()

        assertTrue(failure is CatalogSyncException)
        assertEquals("Não foi possível atualizar o catálogo. Verifique sua conexão e tente novamente.", failure?.message)
        assertEquals(1, store.products.size)
    }
}

private class FakeCatalogApi(
    private val products: List<RemoteCatalogProduct>,
    private val stockMode: CatalogStockMode = CatalogStockMode.TRACKED,
) : CatalogApi {
    override suspend fun syncExternalCatalog(businessId: String) = stockMode

    override suspend fun listProducts(businessId: String, query: String?, gtin: String?, limit: Int, offset: Int) =
        products.drop(offset).take(limit)
}

private class FailingCatalogApi : CatalogApi {
    override suspend fun syncExternalCatalog(businessId: String) = CatalogStockMode.TRACKED

    override suspend fun listProducts(businessId: String, query: String?, gtin: String?, limit: Int, offset: Int): List<RemoteCatalogProduct> =
        error("raw backend response must not leak")
}

private class FakeProductStore : CatalogProductStore {
    val products = linkedMapOf<String, CatalogProduct>()

    override suspend fun upsert(products: List<CatalogProduct>): CatalogUpsertResult {
        var created = 0
        var updated = 0
        products.forEach { product ->
            if (this.products.put(product.productId, product) == null) created++ else updated++
        }
        return CatalogUpsertResult(created, updated, emptyList())
    }
}

private class FakeStateStore : CatalogSyncStateStore {
    val value = MutableStateFlow<CatalogSyncState?>(null)

    override fun observe(businessId: String): Flow<CatalogSyncState?> = value

    override suspend fun current(businessId: String): CatalogSyncState? = value.value

    override suspend fun save(businessId: String, state: CatalogSyncState) {
        value.value = state
    }
}
