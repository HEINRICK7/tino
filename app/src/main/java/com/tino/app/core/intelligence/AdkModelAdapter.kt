package com.tino.app.core.intelligence

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.tino.app.core.speech.GemmaTextInference
import com.tino.app.core.speech.GemmaTextInferenceResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Adapts the Gemma port already owned by TINO to the official ADK Model contract. */
internal class AdkModelAdapter(
    private val inference: GemmaTextInference,
) : Model {
    override val name: String = "tino-gemma-adk"

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        when (val result = inference.generate(request.toPrompt())) {
            is GemmaTextInferenceResult.Generated -> emit(
                LlmResponse(
                    content = Content(
                        role = Role.MODEL,
                        parts = listOf(Part(text = result.text)),
                    ),
                ),
            )
            is GemmaTextInferenceResult.Unavailable -> emit(LlmResponse(errorMessage = result.reason))
            is GemmaTextInferenceResult.Failed -> emit(LlmResponse(errorMessage = result.reason))
        }
    }

    private fun LlmRequest.toPrompt(): String = buildString {
        config.systemInstruction?.parts?.mapNotNull { it.text }?.takeIf { it.isNotEmpty() }?.let {
            append("SYSTEM\n")
            append(it.joinToString("\n"))
            append("\n\n")
        }
        contents.forEach { content ->
            content.parts.mapNotNull { it.text }.takeIf { it.isNotEmpty() }?.let {
                append((content.role ?: Role.USER).uppercase())
                append("\n")
                append(it.joinToString("\n"))
                append("\n\n")
            }
        }
    }.trim()
}
