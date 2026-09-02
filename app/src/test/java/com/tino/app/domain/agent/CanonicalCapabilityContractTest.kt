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
            TinoCapabilityId.GET_CUSTOMER_CONTACT,
            TinoCapabilityId.GET_PRODUCT_STOCK,
            TinoCapabilityId.GET_PRODUCT_PRICE,
            TinoCapabilityId.LIST_SUPPLIERS,
            TinoCapabilityId.LIST_RECEIVABLES,
            TinoCapabilityId.LIST_OVERDUE,
        )
        queries.forEach { id ->
            val definition = TinoCapabilityRegistry.require(id)
            assertEquals(TinoCapabilityType.QUERY, definition.type)
            assertEquals(TinoConfirmationPolicy.NONE, definition.confirmation)
            assertTrue(definition.offline)
            assertFalse(definition.sourceOfTruth.contains("modelo local", ignoreCase = true))
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
    fun customerCreationIsAConfirmedOfflineMutation() {
        val definition = TinoCapabilityRegistry.require(TinoCapabilityId.CREATE_CUSTOMER)

        assertEquals(TinoCapabilityType.MUTATION, definition.type)
        assertEquals(TinoCapabilityRisk.MEDIUM, definition.risk)
        assertEquals(TinoConfirmationPolicy.REQUIRED, definition.confirmation)
        assertTrue(definition.operationIdRequired)
        assertTrue(definition.offline)
        assertEquals("CustomerRepository", definition.sourceOfTruth)
    }

    @Test
    fun stockEntryIsAConfirmedOfflineMutationWithCanonicalPreview() {
        val definition = TinoCapabilityRegistry.require(TinoCapabilityId.REGISTER_STOCK_ENTRY)

        assertEquals(TinoCapabilityType.MUTATION, definition.type)
        assertEquals(TinoCapabilityRisk.MEDIUM, definition.risk)
        assertEquals(TinoConfirmationPolicy.REQUIRED, definition.confirmation)
        assertTrue(definition.operationIdRequired)
        assertTrue(definition.offline)
        assertEquals("CommerceRepository / Room", definition.sourceOfTruth)
        assertEquals("stock_entry_preview", definition.a2uiComponent)
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
        assertEquals(
            TinoToolId.LIST_SUPPLIERS,
            TinoToolCatalog.descriptorFor(TinoCapabilityId.LIST_SUPPLIERS).id,
        )
        assertEquals(
            TinoToolId.CUSTOMER_CONTACT,
            TinoToolCatalog.descriptorFor(TinoCapabilityId.GET_CUSTOMER_CONTACT).id,
        )
        assertEquals(
            TinoToolId.CUSTOMER_CREATE,
            TinoToolCatalog.descriptorFor(TinoCapabilityId.CREATE_CUSTOMER).id,
        )
        assertEquals(
            TinoToolId.PRODUCT_PRICE_UPDATE,
            TinoToolCatalog.descriptorFor(TinoCapabilityId.CHANGE_PRODUCT_PRICE).id,
        )
        assertEquals(
            TinoToolId.STOCK_ENTRY,
            TinoToolCatalog.descriptorFor(TinoCapabilityId.REGISTER_STOCK_ENTRY).id,
        )
    }
}
