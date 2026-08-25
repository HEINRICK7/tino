package com.tino.app.core.speech

import android.util.Log
import com.tino.app.domain.voice.VoiceContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real on-device Gemma adapter for extracting structured fields from a committed transcript.
 * It has no permission to save data or call repositories; those remain outside this adapter.
 */
@Singleton
class MediaPipeGemmaStructuredExtractor @Inject constructor(
    private val inference: GemmaTextInference,
) : GemmaStructuredExtractor {
    override suspend fun extract(
        context: VoiceContext,
        transcript: String,
    ): GemmaExtractionResult {
        val prompt = GemmaExtractionPrompt.forContext(context, transcript)
        return when (val generated = inference.generate(prompt)) {
            is GemmaTextInferenceResult.Generated -> {
                val fields = GemmaJsonOutputParser.parse(generated.text)
                    ?: run {
                        inference.reportMalformedOutput()
                        Log.e(
                            TAG,
                            "invalid JSON response: length=${generated.text.length}, " +
                                "hasObject=${generated.text.contains('{') && generated.text.contains('}')}",
                        )
                        return onboardingFallback(context, transcript)
                    }
                if (context == VoiceContext.ONBOARDING) {
                    OnboardingTranscriptFallback.extract(transcript)?.let {
                        return GemmaExtractionResult.Extracted(it)
                    }
                }
                GemmaExtractionResult.Extracted(fields)
            }
            is GemmaTextInferenceResult.Unavailable -> onboardingFallback(context, transcript)
                ?: GemmaExtractionResult.Unavailable(generated.reason)
            is GemmaTextInferenceResult.Failed -> onboardingFallback(context, transcript)
                ?: GemmaExtractionResult.Failed(generated.reason)
        }
    }

    private fun onboardingFallback(
        context: VoiceContext,
        transcript: String,
    ): GemmaExtractionResult = if (context == VoiceContext.ONBOARDING) {
        OnboardingTranscriptFallback.extract(transcript)?.let { GemmaExtractionResult.Extracted(it) }
            ?: GemmaExtractionResult.Failed("Não consegui organizar os dados. Fale novamente ou preencha abaixo.")
    } else {
        GemmaExtractionResult.Failed("Não consegui organizar os dados. Fale novamente ou preencha abaixo.")
    }
}

private object GemmaExtractionPrompt {
    fun forContext(context: VoiceContext, transcript: String): String {
        val fields = when (context) {
            VoiceContext.ONBOARDING -> "store_name, owner_name, phone"
            VoiceContext.PRODUCT_CREATE -> "product_name, sale_price, stock_initial, size, unit"
            VoiceContext.STOCK_RECEIPT -> "product, quantity, boxes, units_per_box, unit_cost, supplier"
            VoiceContext.CUSTOMER_CREATE,
            VoiceContext.SUPPLIER_CREATE,
            -> "name, phone"
            VoiceContext.CREDIT_SALE -> "customer, products, quantity"
            VoiceContext.SALE -> "products, quantity, payment_method"
            VoiceContext.GLOBAL -> "intent"
        }

        return """
            <start_of_turn>user
            Você organiza uma fala de um comerciante brasileiro para a tela atual do TINO.
            Contexto: ${context.name}.
            Retorne SOMENTE um objeto JSON, sem markdown, sem explicação e sem chaves fora desta lista:
            [$fields]
            Se um dado não estiver claro, omita a chave. Preserve nomes como foram falados.
            Para valores numéricos, use somente dígitos e ponto decimal quando necessário.
            Fala: ${JSONObject.quote(transcript)}
            <end_of_turn>
            <start_of_turn>model
        """.trimIndent()
    }
}

internal object GemmaJsonOutputParser {
    fun parse(response: String): Map<String, String>? {
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        return runCatching {
            val json = JSONObject(response.substring(start, end + 1))
            val fields = linkedMapOf<String, String>()
            val aliases = mapOf(
                "store" to "store_name",
                "business_name" to "store_name",
                "commerce_name" to "store_name",
                "owner" to "owner_name",
                "person_name" to "owner_name",
                "cellphone" to "phone",
                "telephone" to "phone",
            )
            json.keys().forEach { key ->
                val value = json.opt(key)
                if (value != null && value != JSONObject.NULL) {
                    fields[aliases[key.lowercase()] ?: key] = value.toString().trim()
                }
            }
            fields.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}

internal object OnboardingTranscriptFallback {
    private val ownerMarker = Regex("meu\\s+nome\\s*(?:é|e)\\s*", RegexOption.IGNORE_CASE)
    private val phoneMarker = Regex("(?:e\\s+)?(?:o\\s+)?(?:telefone|celular)\\b", RegexOption.IGNORE_CASE)
    private val leadingStoreWords = Regex(
        "^(?:o )?(?:nome do meu comércio é|meu comércio é|meu comércio se chama|tenho um comércio chamado)\\s+",
        RegexOption.IGNORE_CASE,
    )

    fun extract(transcript: String): Map<String, String>? {
        val ownerMatch = ownerMarker.find(transcript) ?: return null
        val ownerStart = ownerMatch.range.last + 1
        val phoneMatch = phoneMarker.find(transcript, ownerStart)
        val owner = transcript.substring(ownerStart, phoneMatch?.range?.first ?: transcript.length)
            .trim(' ', ',', '.', ';', ':')
            .takeIf { it.isNotBlank() }
            ?: return null
        val store = transcript.substring(0, ownerMatch.range.first)
            .replace(leadingStoreWords, "")
            .trim(' ', ',', '.', ';', ':')
            .takeIf { it.isNotBlank() }
            ?: return null
        val phone = transcript.filter(Char::isDigit)
            .takeLast(13)
            .takeIf { it.length in 10..13 }
            ?: return null
        return mapOf("store_name" to store, "owner_name" to owner, "phone" to phone)
    }
}

private const val TAG = "TinoGemma"
