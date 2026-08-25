package com.tino.app.domain.voice

import com.tino.app.core.speech.TranscriptEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandCoordinatorTest {
    @Test
    fun unavailableTranscriberFailureBecomesRecoverableIgnoredState() = runBlocking {
        val coordinator = VoiceCommandCoordinator(PilotGemmaOrchestrator(), RecordingToolExecutor())

        val state = coordinator.accept(TranscriptEvent.Failed("Gemma indisponível"))

        assertTrue(state is VoiceCommandState.Ignored)
        assertEquals("Gemma indisponível", (state as VoiceCommandState.Ignored).reason)
    }
    @Test
    fun onlyCommittedTranscriptCreatesPreviewAndConfirmationExecutesOnce() = runBlocking {
        val executor = RecordingToolExecutor()
        val coordinator = VoiceCommandCoordinator(
            gemma = PilotGemmaOrchestrator(),
            dispatcher = executor,
        )

        val partial = coordinator.accept(TranscriptEvent.Partial("João levou dois cafés fiado"))
        assertTrue(partial is VoiceCommandState.Ignored)
        assertEquals(0, executor.executed)

        val preview = coordinator.accept(TranscriptEvent.Committed("João levou 2 Café fiado"))
        assertTrue(preview is VoiceCommandState.PreviewReady)
        assertEquals(0, executor.executed)

        val completed = coordinator.confirm()
        assertTrue(completed is VoiceCommandState.Completed)
        assertEquals(1, executor.executed)
    }

    @Test
    fun cancelPreventsExecution() = runBlocking {
        val executor = RecordingToolExecutor()
        val coordinator = VoiceCommandCoordinator(PilotGemmaOrchestrator(), executor)

        coordinator.accept(TranscriptEvent.Committed("João levou 2 Café fiado"))
        coordinator.cancel()

        assertTrue(coordinator.confirm() is VoiceCommandState.Ignored)
        assertEquals(0, executor.executed)
    }

    @Test
    fun readOnlyQueryReturnsAnswerWithoutPendingConfirmation() = runBlocking {
        val executor = RecordingToolExecutor()
        val coordinator = VoiceCommandCoordinator(
            gemma = FixedGemmaOrchestrator(
                ToolCall(CommerceToolName.GET_TODAY_SALES, emptyMap()),
            ),
            dispatcher = executor,
        )

        val answer = coordinator.accept(TranscriptEvent.Committed("Quanto vendi hoje?"))

        assertTrue(answer is VoiceCommandState.AnswerReady)
        assertEquals("Vendas de hoje: R$ 120,00", (answer as VoiceCommandState.AnswerReady).result.message)
        assertEquals(1, executor.executed)
        assertTrue(coordinator.confirm() is VoiceCommandState.Ignored)
    }

    @Test
    fun customerBalanceQueryIsReadOnlyAndReturnsNamedSurface() = runBlocking {
        val executor = RecordingToolExecutor()
        val coordinator = VoiceCommandCoordinator(
            gemma = FixedGemmaOrchestrator(
                ToolCall(
                    CommerceToolName.GET_CUSTOMER_BALANCE,
                    mapOf("customer" to "João"),
                ),
            ),
            dispatcher = executor,
        )

        val answer = coordinator.accept(TranscriptEvent.Committed("Quanto João deve?"))

        assertTrue(answer is VoiceCommandState.AnswerReady)
        assertEquals(1, executor.executed)
        assertEquals("Fiado de João", (answer as VoiceCommandState.AnswerReady).result.title)
    }

    @Test
    fun stockQueryIsReadOnlyAndReturnsStockSurface() = runBlocking {
        val executor = RecordingToolExecutor()
        val coordinator = VoiceCommandCoordinator(
            gemma = FixedGemmaOrchestrator(
                ToolCall(
                    CommerceToolName.CHECK_STOCK,
                    mapOf("product" to "Café Maratá"),
                ),
            ),
            dispatcher = executor,
        )

        val answer = coordinator.accept(TranscriptEvent.Committed("Quantos cafés ainda tem?"))

        assertTrue(answer is VoiceCommandState.AnswerReady)
        assertEquals(1, executor.executed)
        assertEquals("Estoque", (answer as VoiceCommandState.AnswerReady).result.title)
    }

    @Test
    fun priceChangeCommandCreatesPreviewAndWaitsForConfirmation() = runBlocking {
        val executor = RecordingToolExecutor()
        val coordinator = VoiceCommandCoordinator(
            gemma = FixedGemmaOrchestrator(
                ToolCall(
                    CommerceToolName.CHANGE_PRODUCT_PRICE,
                    mapOf("product" to "Café Maratá", "new_price_cents" to "875"),
                ),
            ),
            dispatcher = executor,
        )

        val preview = coordinator.accept(TranscriptEvent.Committed("Muda o café para oito e setenta e cinco"))

        assertTrue(preview is VoiceCommandState.PreviewReady)
        assertEquals(0, executor.executed)
        assertTrue(coordinator.confirm() is VoiceCommandState.Completed)
        assertEquals(1, executor.executed)
    }

    @Test
    fun clarificationKeepsIntentAndResumesWithTheMissingEntity() = runBlocking {
        val executor = ClarifyingToolExecutor()
        val coordinator = VoiceCommandCoordinator(
            gemma = FixedGemmaOrchestrator(
                ToolCall(
                    CommerceToolName.CHANGE_PRODUCT_PRICE,
                    mapOf("product" to "Café", "new_price_cents" to "875"),
                ),
            ),
            dispatcher = executor,
        )

        val clarification = coordinator.accept(TranscriptEvent.Committed("Muda o café para oito e setenta e cinco"))
        assertTrue(clarification is VoiceCommandState.Clarification)
        assertEquals(
            listOf("Café Maratá", "Café Pilão"),
            (clarification as VoiceCommandState.Clarification).options,
        )
        assertEquals(0, executor.executed)

        val preview = coordinator.accept(TranscriptEvent.Committed("Café Maratá"))
        assertTrue(preview is VoiceCommandState.PreviewReady)
        assertEquals("Café Maratá", executor.lastPreviewedCall?.arguments?.get("product"))
        assertTrue(coordinator.confirm() is VoiceCommandState.Completed)
        assertEquals(1, executor.executed)
    }

    @Test
    fun clarificationAcceptsAnIndexedChoice() = runBlocking {
        val executor = ClarifyingToolExecutor()
        val coordinator = VoiceCommandCoordinator(
            gemma = FixedGemmaOrchestrator(
                ToolCall(
                    CommerceToolName.CHANGE_PRODUCT_PRICE,
                    mapOf("product" to "Café", "new_price_cents" to "875"),
                ),
            ),
            dispatcher = executor,
        )

        coordinator.accept(TranscriptEvent.Committed("Muda o café"))
        val preview = coordinator.accept(TranscriptEvent.Committed("o segundo"))

        assertTrue(preview is VoiceCommandState.PreviewReady)
        assertEquals("Café Pilão", executor.lastPreviewedCall?.arguments?.get("product"))
    }

    @Test
    fun clarificationCanBeCancelledWithoutExecution() = runBlocking {
        val executor = ClarifyingToolExecutor()
        val coordinator = VoiceCommandCoordinator(
            gemma = FixedGemmaOrchestrator(
                ToolCall(
                    CommerceToolName.CHANGE_PRODUCT_PRICE,
                    mapOf("product" to "Café", "new_price_cents" to "875"),
                ),
            ),
            dispatcher = executor,
        )

        coordinator.accept(TranscriptEvent.Committed("Muda o café"))
        assertTrue(coordinator.accept(TranscriptEvent.Committed("cancela")) is VoiceCommandState.Cancelled)
        assertTrue(coordinator.confirm() is VoiceCommandState.Ignored)
        assertEquals(0, executor.executed)
    }

    @Test
    fun pendingMutationCanBeConfirmedByVoice() = runBlocking {
        val executor = RecordingToolExecutor()
        val coordinator = VoiceCommandCoordinator(
            gemma = FixedGemmaOrchestrator(
                ToolCall(
                    CommerceToolName.CHANGE_PRODUCT_PRICE,
                    mapOf("product" to "Café Maratá", "new_price_cents" to "875"),
                ),
            ),
            dispatcher = executor,
        )

        assertTrue(coordinator.accept(TranscriptEvent.Committed("Muda o café para oito e setenta e cinco")) is VoiceCommandState.PreviewReady)
        assertTrue(coordinator.accept(TranscriptEvent.Committed("sim")) is VoiceCommandState.Completed)
        assertEquals(1, executor.executed)
    }

    @Test
    fun unknownConfirmationKeepsMutationPending() = runBlocking {
        val executor = RecordingToolExecutor()
        val coordinator = VoiceCommandCoordinator(
            gemma = FixedGemmaOrchestrator(
                ToolCall(
                    CommerceToolName.CHANGE_PRODUCT_PRICE,
                    mapOf("product" to "Café Maratá", "new_price_cents" to "875"),
                ),
            ),
            dispatcher = executor,
        )

        coordinator.accept(TranscriptEvent.Committed("Muda o café para oito e setenta e cinco"))
        val clarification = coordinator.accept(TranscriptEvent.Committed("não sei"))

        assertTrue(clarification is VoiceCommandState.ConfirmationNeeded)
        assertEquals(0, executor.executed)
        assertTrue(coordinator.accept(TranscriptEvent.Committed("sim")) is VoiceCommandState.Completed)
        assertEquals(1, executor.executed)
    }

    private class FixedGemmaOrchestrator(
        private val call: ToolCall,
    ) : GemmaOrchestrator {
        override suspend fun interpret(committedTranscript: String): ToolCall = call
    }

    private class RecordingToolExecutor : ToolExecutor {
        var executed = 0

        override suspend fun preview(call: ToolCall) = ToolPreview("confirmar", call.name.name)

        override suspend fun execute(call: ToolCall, confirmed: Boolean): ToolExecutionResult {
            check(confirmed)
            executed += 1
            return if (call.name == CommerceToolName.GET_TODAY_SALES) {
                ToolExecutionResult("Vendas de hoje: R$ 120,00")
            } else if (call.name == CommerceToolName.GET_CUSTOMER_BALANCE) {
                ToolExecutionResult("R$ 101,00", "Fiado de João")
            } else if (call.name == CommerceToolName.CHECK_STOCK) {
                ToolExecutionResult("8 unidades", "Estoque")
            } else {
                ToolExecutionResult("ok")
            }
        }
    }

    private class ClarifyingToolExecutor : ToolExecutor {
        var executed = 0
        var lastPreviewedCall: ToolCall? = null
        private var shouldClarify = true

        override suspend fun preview(call: ToolCall): ToolPreview {
            lastPreviewedCall = call
            if (shouldClarify) {
                shouldClarify = false
                throw ToolClarificationException(
                    "Encontrei Café Maratá e Café Pilão.",
                    argumentKey = "product",
                    options = listOf("Café Maratá", "Café Pilão"),
                )
            }
            return ToolPreview("Alterar preço?", "ok")
        }

        override suspend fun execute(call: ToolCall, confirmed: Boolean): ToolExecutionResult {
            check(confirmed)
            executed += 1
            return ToolExecutionResult("ok")
        }
    }
}
