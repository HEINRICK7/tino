package com.tino.app.domain.commerce

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryPolicyTest {
    @Test
    fun conservativeDefaultOnlyFlagsZeroStock() {
        val policy = InventoryPolicy.conservativeDefault

        assertTrue(policy.needsReplenishment(0))
        assertFalse(policy.needsReplenishment(1))
        assertFalse(policy.needsReplenishment(24))
    }

    @Test
    fun configuredReorderPointFlagsItemsBelowPolicy() {
        val policy = InventoryPolicy(minimumStock = 5, reorderPoint = 10)

        assertTrue(policy.needsReplenishment(10))
        assertTrue(policy.needsReplenishment(4))
        assertFalse(policy.needsReplenishment(24))
    }
}
