package com.tino.app.domain.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptLifecycleTest {
    @Test
    fun partialAndRevisedTextCannotReachAgent() {
        val gate = TranscriptCommitGate()
        gate.reset()

        gate.partial("Quanto eu recebi")
        assertFalse(gate.snapshot.canSubmit)
        gate.revised("Quanto eu recebi hoje no Pix")
        assertFalse(gate.snapshot.canSubmit)
    }

    @Test
    fun onlyCommittedTextCanBeSubmitted() {
        val gate = TranscriptCommitGate()
        gate.reset()
        gate.commit("Quanto eu recebi hoje no Pix?")

        assertTrue(gate.snapshot.canSubmit)
        gate.processing()
        assertFalse(gate.snapshot.canSubmit)
    }

    @Test
    fun editingReturnsToReviewBeforeProcessing() {
        val gate = TranscriptCommitGate()
        gate.commit("Bota dois cafés Maracá pra Maria")
        gate.edit("Bota dois cafés Maratá pra Maria")

        assertFalse(gate.snapshot.canSubmit)
        gate.commit(gate.snapshot.text)
        assertTrue(gate.snapshot.canSubmit)
    }
}
