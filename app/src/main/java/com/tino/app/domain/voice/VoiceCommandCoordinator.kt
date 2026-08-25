package com.tino.app.domain.voice

import com.tino.app.core.speech.TranscriptEvent
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VoiceCommandState {
    data object Idle : VoiceCommandState
    data object Cancelled : VoiceCommandState
    data class AnswerReady(
        val call: ToolCall,
        val result: ToolExecutionResult,
    ) : VoiceCommandState
    data class Clarification(
        val message: String,
        val options: List<String> = emptyList(),
        val entityType: String? = null,
    ) : VoiceCommandState
    data class ConfirmationNeeded(val message: String) : VoiceCommandState
    data class PreviewReady(val call: ToolCall, val preview: ToolPreview) : VoiceCommandState
    data class Completed(val call: ToolCall) : VoiceCommandState
    data class Ignored(val reason: String) : VoiceCommandState
}

@Singleton
class VoiceCommandCoordinator @Inject constructor(
    private val gemma: GemmaOrchestrator,
    private val dispatcher: ToolExecutor,
) {
    private var pending: ToolCall? = null
    private var pendingPreview: ToolPreview? = null
    private var clarification: ClarificationRequest? = null

    suspend fun accept(event: TranscriptEvent): VoiceCommandState {
        if (event is TranscriptEvent.Failed) {
            return VoiceCommandState.Ignored(event.reason)
        }
        if (event !is TranscriptEvent.Committed) {
            return VoiceCommandState.Ignored("Ainda não ouvi uma frase completa. Fale novamente.")
        }
        if (pending != null) {
            return when {
                event.text.isConfirmation() -> confirm()
                event.text.isCancellation() -> {
                    cancel()
                    VoiceCommandState.Cancelled
                }
                else -> VoiceCommandState.ConfirmationNeeded("Diga sim para confirmar ou cancela para voltar.")
            }
        }
        val clarificationRequest = clarification
        val call = clarificationRequest?.let { request ->
            if (event.text.isCancellation()) {
                pending = null
                clarification = null
                return VoiceCommandState.Cancelled
            }
            request.call.copy(
                arguments = request.call.arguments + (request.argumentKey to request.referenceFor(event.text)),
            )
        } ?: gemma.interpret(event.text)
            ?: return VoiceCommandState.Ignored("Não entendi esse comando.")
        return try {
            if (call.name.isReadOnly) {
                clarification = null
                VoiceCommandState.AnswerReady(
                    call = call,
                    result = dispatcher.execute(call, confirmed = true),
                )
            } else {
                val preview = dispatcher.preview(call)
                clarification = null
                pending = call
                pendingPreview = preview
                VoiceCommandState.PreviewReady(call, preview)
            }
        } catch (error: ToolClarificationException) {
            pending = null
            clarification = error.argumentKey?.let { key -> ClarificationRequest(call, key, error.options) }
            VoiceCommandState.Clarification(
                message = error.message ?: "Preciso de mais um detalhe.",
                options = error.options,
                entityType = error.argumentKey,
            )
        }
    }

    suspend fun confirm(): VoiceCommandState {
        val call = pending ?: return VoiceCommandState.Ignored("Não há operação aguardando confirmação.")
        dispatcher.confirm(call, pendingPreview?.preparedMutation?.confirmation)
        pending = null
        pendingPreview = null
        return VoiceCommandState.Completed(call)
    }

    fun cancel() {
        pending = null
        pendingPreview = null
        clarification = null
    }

    private data class ClarificationRequest(
        val call: ToolCall,
        val argumentKey: String,
        val options: List<String>,
    )

    private fun ClarificationRequest.referenceFor(reply: String): String {
        val normalizedReply = reply.normalizeForVoice()
        val index = when {
            Regex("\\b(primeiro|primeira|1|um|uma)\\b").containsMatchIn(normalizedReply) -> 0
            Regex("\\b(segundo|segunda|2|dois|duas)\\b").containsMatchIn(normalizedReply) -> 1
            Regex("\\b(terceiro|terceira|3|tres)\\b").containsMatchIn(normalizedReply) -> 2
            else -> null
        }
        options.getOrNull(index ?: -1)?.let { return it }

        val replyWords = normalizedReply.split(' ').filter { it.length > 2 }.toSet()
        options.maxByOrNull { option ->
            option.normalizeForVoice().split(' ').count { it.length > 2 && it in replyWords }
        }?.takeIf { option ->
            option.normalizeForVoice().split(' ').any { it.length > 2 && it in replyWords }
        }?.let { return it }

        return reply.trim()
    }

    private fun String.isCancellation(): Boolean = normalizeForVoice().let {
        it == "cancela" || it == "cancelar" || it == "deixa pra la" || it == "nao quero"
    }

    private fun String.isConfirmation(): Boolean = normalizeForVoice().let {
        it == "sim" || it == "pode" || it == "confirmar" || it == "confirma" ||
            it == "anota" || it == "pode anotar" || it == "pode fazer"
    }

    private fun String.normalizeForVoice(): String = java.text.Normalizer
        .normalize(lowercase(), java.text.Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .replace("[^a-z0-9]+".toRegex(), " ")
        .trim()
}
