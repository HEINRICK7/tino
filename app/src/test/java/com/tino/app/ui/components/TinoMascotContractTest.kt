package com.tino.app.ui.components

import com.tino.app.domain.agent.TinoPresenceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TinoMascotContractTest {
    @Test
    fun everyRuntimePresenceModeMapsToAnOfficialMascotState() {
        TinoPresenceMode.entries.forEach { mode ->
            assertTrue(TinoMascotState.fromPresence(mode).contentDescription.startsWith("TINO"))
        }
    }

    @Test
    fun runtimeModesKeepTheUserFacingIntent() {
        assertEquals(TinoMascotState.Idle, TinoMascotState.fromPresence(TinoPresenceMode.IDLE))
        assertEquals(TinoMascotState.Observing, TinoMascotState.fromPresence(TinoPresenceMode.LISTENING))
        assertEquals(TinoMascotState.Thinking, TinoMascotState.fromPresence(TinoPresenceMode.THINKING))
        assertEquals(TinoMascotState.Attention, TinoMascotState.fromPresence(TinoPresenceMode.WAITING_FOR_USER))
        assertEquals(TinoMascotState.Idle, TinoMascotState.fromPresence(TinoPresenceMode.COMPLETED))
        assertEquals(TinoMascotState.Attention, TinoMascotState.fromPresence(TinoPresenceMode.ERROR))
    }

    @Test
    fun sizeTokensAreOrderedAndDoNotUseArbitraryPerScreenValues() {
        val sizes = TinoMascotSize.entries.map { it.dp.value }
        assertEquals(sizes.sorted(), sizes)
        assertEquals(listOf(48f, 64f, 88f, 128f, 160f), sizes)
    }
}
