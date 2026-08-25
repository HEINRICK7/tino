package com.tino.app.core.common

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UuidV7Test {
    @Test
    fun generatesVersion7UuidWithRfcVariant() {
        val uuid = UUID.fromString(UuidV7.new())

        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
        assertTrue(uuid.toString().matches(Regex("[0-9a-f-]{36}")))
    }
}
