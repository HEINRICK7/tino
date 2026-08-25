package com.tino.app.core.common

import java.security.SecureRandom
import java.util.UUID

/** Generates time-ordered UUIDs for offline-created domain entities. */
object UuidV7 {
    private val random = SecureRandom()

    fun new(): String {
        val timestamp = System.currentTimeMillis() and 0x0000_FFFF_FFFF_FFFFL
        val mostSignificantBits = (timestamp shl 16) or
            (0x7L shl 12) or
            (random.nextInt() and 0x0FFF).toLong()
        val leastSignificantBits = (random.nextLong() and 0x3FFF_FFFF_FFFF_FFFFL) or
            Long.MIN_VALUE
        return UUID(mostSignificantBits, leastSignificantBits).toString()
    }
}
