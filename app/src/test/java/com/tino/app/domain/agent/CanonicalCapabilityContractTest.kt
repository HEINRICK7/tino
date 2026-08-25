package com.tino.app.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalCapabilityContractTest {
    @Test
    fun priorityCapabilitiesDeclareOneCanonicalContract() {
        val queries = listOf(
            TinoCapabilityId.LIST_PRODUCTS,
            TinoCapabilityId.GET_PRODUCT_STOCK,
            TinoCapabilityId.GET_PRODUCT_PRICE,
            TinoCapabilityId.LIST_RECEIVABLES,
            TinoCapabilityId.LIST_OVERDUE,
        )
        queries.forEach { id ->
            val definition = TinoCapabilityRegistry.require(id)
            assertEquals(TinoCapabilityType.QUERY, definition.type)
            assertEquals(TinoConfirmationPolicy.NONE, definition.confirmation)
            assertTrue(definition.offline)
            assertFalse(definition.sourceOfTruth.contains("Gemma", ignoreCase = true))
        }
    }

    @Test
    fun creditPaymentIsAConfirmedOfflineMutationWithOperationIdentity() {
        val definition = TinoCapabilityRegistry.require(TinoCapabilityId.RECEIVE_CREDIT_PAYMENT)

        assertEquals(TinoCapabilityType.MUTATION, definition.type)
        assertEquals(TinoCapabilityRisk.HIGH, definition.risk)
        assertEquals(TinoConfirmationPolicy.REQUIRED, definition.confirmation)
        assertTrue(definition.operationIdRequired)
        assertTrue(definition.offline)
        assertEquals("CreditLedger", definition.sourceOfTruth)
    }

    @Test
    fun navigationIsNotModeledAsCommerceMutation() {
        val definition = TinoCapabilityRegistry.require(TinoCapabilityId.OPEN_ENTITY)

        assertEquals(TinoCapabilityType.NAVIGATION, definition.type)
        assertEquals(TinoConfirmationPolicy.NONE, definition.confirmation)
        assertFalse(definition.operationIdRequired)
    }

    @Test
    fun canonicalToolsPointBackToCapabilityDefinitions() {
        assertEquals(
            TinoToolId.LIST_PRODUCTS,
            TinoToolCatalog.descriptorFor(TinoCapabilityId.LIST_PRODUCTS).id,
        )
        assertEquals(
            TinoToolId.CREDIT_PAYMENT,
            TinoToolCatalog.descriptorFor(TinoCapabilityId.RECEIVE_CREDIT_PAYMENT).id,
        )
        assertEquals(
            TinoToolId.LIST_CUSTOMERS,
            TinoToolCatalog.descriptorFor(TinoCapabilityId.LIST_CUSTOMERS).id,
        )
    }
}
