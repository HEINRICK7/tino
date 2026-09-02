package com.tino.app.core.network

import com.tino.app.domain.catalog.RemoteCatalogProduct
import com.tino.app.domain.catalog.CatalogStockMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogApiContractTest {
    @Test
    fun synchronizesConfiguredDocesSonhosConnectionBeforeCatalogRead() = runBlocking {
        val requests = mutableListOf<BackendHttpRequest>()
        val api = RestCatalogApi("https://api.tino.test", BackendHttpTransport { request ->
            requests += request
            when (request.method to request.path) {
                "GET" to "/api/v1/businesses/business-1/external-connections" ->
                    BackendHttpResponse(200, "[{\"id\":\"connection-1\",\"provider\":\"DOCES_SONHOS\"}]")
                "POST" to "/api/v1/businesses/business-1/external-connections/connection-1/sync" ->
                    BackendHttpResponse(200, "{\"status\":\"CONNECTED\"}")
                else -> error("unexpected request: $request")
            }
        })

        assertEquals(CatalogStockMode.MADE_TO_ORDER, api.syncExternalCatalog("business-1"))

        assertEquals(
            listOf(
                "GET /api/v1/businesses/business-1/external-connections",
                "POST /api/v1/businesses/business-1/external-connections/connection-1/sync",
            ),
            requests.map { "${it.method} ${it.path}" },
        )
    }

    @Test
    fun mapsSnakeCaseCatalogResponseAndSendsContractQuery() = runBlocking {
        var captured: BackendHttpRequest? = null
        val api = RestCatalogApi("https://api.tino.test", BackendHttpTransport { request ->
            captured = request
            BackendHttpResponse(
                200,
                "[{\"product_id\":\"p-1\",\"name\":\"Café\",\"base_unit\":\"UN\",\"gtin\":null,\"price\":69.90}]",
            )
        })

        val products = api.listProducts("business-1", query = "café", limit = 100, offset = 100)

        assertEquals("GET", captured?.method)
        assertEquals("/api/v1/businesses/business-1/products", captured?.path)
        assertEquals("café", captured?.query?.get("q"))
        assertEquals("100", captured?.query?.get("limit"))
        assertEquals("100", captured?.query?.get("offset"))
        assertEquals(RemoteCatalogProduct("p-1", "Café", "UN", null, "69.90"), products.single())
        assertNull(products.single().gtin)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfContractLimit(): Unit = runBlocking {
        RestCatalogApi("https://api.tino.test", BackendHttpTransport { BackendHttpResponse(200, "[]") })
            .listProducts("business-1", limit = 101)
    }
}
