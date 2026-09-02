package com.tino.app.core.network

import com.tino.app.domain.onboarding.BootstrapBusiness
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapApiTest {
    @Test
    fun bootstrapUsesTheAuthoritativeSnakeCaseContract() = runBlocking {
        val requests = mutableListOf<BackendHttpRequest>()
        val transport = BackendHttpTransport { request ->
            requests += request
            BackendHttpResponse(
                status = 200,
                body = """
                    {
                      "state":"READY",
                      "user":{"user_id":"user-1","status":"ACTIVE"},
                      "businesses":[{"id":"business-1","trade_name":"Doces & Sonhos","vertical":"RETAIL","status":"ACTIVE","role":"OWNER","data_source_type":"EXTERNAL_API"}],
                      "selected_business":{"id":"business-1","trade_name":"Doces & Sonhos","vertical":"RETAIL","status":"ACTIVE","role":"OWNER","data_source_type":"EXTERNAL_API"},
                      "installation":{"id":"installation-1","installation_id":"install-1","business_id":"business-1","status":"ACTIVE"}
                    }
                """.trimIndent(),
            )
        }

        val context = RestBootstrapApi("https://api.tino.otimizanegocio.com/", transport)
            .bootstrap("business-1", "install-1")

        assertEquals("READY", context.state)
        assertEquals("Doces & Sonhos", context.selectedBusiness?.tradeName)
        assertEquals("EXTERNAL_API", context.selectedBusiness?.dataSourceType)
        assertEquals("business-1", context.installation?.businessId)
        assertEquals("/api/v1/bootstrap", requests.single().path)
    }

    @Test
    fun createBusinessSendsTradeNameAndVerticalWithoutLocalOnlyFields() = runBlocking {
        val requests = mutableListOf<BackendHttpRequest>()
        val transport = BackendHttpTransport { request ->
            requests += request
            BackendHttpResponse(
                status = 201,
                body = """
                    {"id":"business-2","trade_name":"Doces & Sonhos","vertical":"BAKERY","status":"ACTIVE","role":"OWNER"}
                """.trimIndent(),
            )
        }

        val created: BootstrapBusiness = RestBootstrapApi("https://api.tino.otimizanegocio.com/", transport)
            .createBusiness("Doces & Sonhos", "BAKERY")

        assertEquals("business-2", created.id)
        assertEquals("Doces & Sonhos", created.tradeName)
        assertTrue(requests.single().body!!.contains("\"trade_name\":\"Doces & Sonhos\""))
        assertTrue(requests.single().body!!.contains("\"vertical\":\"BAKERY\""))
        assertTrue(!requests.single().body!!.contains("owner_name"))
        assertEquals("/api/v1/businesses", requests.single().path)
    }
}
