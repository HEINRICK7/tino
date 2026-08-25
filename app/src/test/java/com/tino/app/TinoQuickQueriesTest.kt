package com.tino.app

import com.tino.app.domain.agent.TinoCapabilityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TinoQuickQueriesTest {
    @Test
    fun quickQueriesOnlyExposeCapabilitiesAllowedByTheResolvedProfile() {
        val queries = availableQuickQueries(setOf(TinoCapabilityId.LIST_CUSTOMERS))

        assertEquals(setOf("customers"), queries.map { it.id }.toSet())
        assertTrue(queries.all { it.requiredCapabilities.all(setOf(TinoCapabilityId.LIST_CUSTOMERS)::contains) })
    }

    @Test
    fun permanentInventoryCapabilityRestoresOnlyItsQueries() {
        val queries = availableQuickQueries(setOf(TinoCapabilityId.LIST_PRODUCTS))

        assertEquals(setOf("products"), queries.map { it.id }.toSet())
    }
}
