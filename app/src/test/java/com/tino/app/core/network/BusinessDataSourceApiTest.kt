package com.tino.app.core.network

import com.tino.app.domain.onboarding.BusinessDataSourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessDataSourceApiTest {
    @Test
    fun nativeSelectionUsesTheExactBackendPayload() = runBlocking {
        val requests = mutableListOf<BackendHttpRequest>()
        val api = RestBusinessDataSourceApi("https://api.tino.otimizanegocio.com/", BackendHttpTransport { request ->
            requests += request
            BackendHttpResponse(
                200,
                """{"business_id":"business-1","source_type":"TINO_NATIVE","provider":null,"connection_id":null,"status":null}""",
            )
        })

        val result = api.select("business-1", BusinessDataSourceType.TINO_NATIVE, null)

        assertEquals("PUT", requests.single().method)
        assertEquals("/api/v1/businesses/business-1/data-source", requests.single().path)
        assertEquals("{\"source_type\":\"TINO_NATIVE\",\"provider\":null}", requests.single().body)
        assertEquals(BusinessDataSourceType.TINO_NATIVE, result.sourceType)
        assertNull(result.provider)
    }

    @Test
    fun externalSelectionUsesProviderIdentifierAndParsesAuthoritativeResponse() = runBlocking {
        val requests = mutableListOf<BackendHttpRequest>()
        val api = RestBusinessDataSourceApi("https://api.tino.otimizanegocio.com/", BackendHttpTransport { request ->
            requests += request
            BackendHttpResponse(
                200,
                """
                    {
                      "business_id":"business-2",
                      "source_type":"EXTERNAL_API",
                      "provider":"DOCES_SONHOS",
                      "connection_id":"connection-1",
                      "status":"CONNECTED"
                    }
                """.trimIndent(),
            )
        })

        val result = api.select("business-2", BusinessDataSourceType.EXTERNAL_API, "DOCES_SONHOS")

        assertEquals("{\"source_type\":\"EXTERNAL_API\",\"provider\":\"DOCES_SONHOS\"}", requests.single().body)
        assertEquals(BusinessDataSourceType.EXTERNAL_API, result.sourceType)
        assertEquals("DOCES_SONHOS", result.provider)
        assertEquals("connection-1", result.connectionId)
        assertEquals("CONNECTED", result.status)
    }

    @Test
    fun anotherDeviceReadsTheBusinessSourceWithoutSendingASelection() = runBlocking {
        val requests = mutableListOf<BackendHttpRequest>()
        val api = RestBusinessDataSourceApi("https://api.tino.otimizanegocio.com/", BackendHttpTransport { request ->
            requests += request
            BackendHttpResponse(
                200,
                """{"business_id":"business-3","source_type":"EXTERNAL_API","provider":"DOCES_SONHOS","connection_id":"connection-3","status":"READY"}""",
            )
        })

        val result = api.get("business-3")

        assertEquals("GET", requests.single().method)
        assertEquals("/api/v1/businesses/business-3/data-source", requests.single().path)
        assertTrue(requests.single().body == null)
        assertEquals(BusinessDataSourceType.EXTERNAL_API, result.sourceType)
    }
}
