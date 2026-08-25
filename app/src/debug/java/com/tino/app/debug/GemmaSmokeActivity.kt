package com.tino.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.tino.app.core.speech.GemmaTextInference
import com.tino.app.core.speech.GemmaTextInferenceResult
import com.tino.app.core.speech.GemmaInferenceService
import com.tino.app.core.speech.GEMMA_DEBUG_KILL_ACTION
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Debug-only physical smoke for the isolated Gemma process.
 * It never reaches a tool, Room, mutation or Agent Runtime.
 */
@AndroidEntryPoint
class GemmaSmokeActivity : ComponentActivity() {
    @Inject lateinit var inference: GemmaTextInference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val output = TextView(this).apply {
            textSize = 18f
            text = "G4.1\n\nChamando Gemma isolado..."
            setPadding(32, 48, 32, 48)
        }
        setContentView(output)

        lifecycleScope.launch {
            if (intent.getBooleanExtra(EXTRA_KILL_GEMMA, false)) {
                launch {
                    kotlinx.coroutines.delay(400)
                    startService(android.content.Intent(this@GemmaSmokeActivity, GemmaInferenceService::class.java).apply {
                        action = GEMMA_DEBUG_KILL_ACTION
                    })
                }
            }
            val result = inference.generate(SMOKE_PROMPT)
            val rendered = when (result) {
                is GemmaTextInferenceResult.Generated ->
                    "GENERATED\n\n${result.text.take(MAX_PREVIEW_CHARS)}"
                is GemmaTextInferenceResult.Unavailable -> "UNAVAILABLE\n\n${result.reason}"
                is GemmaTextInferenceResult.Failed -> "FAILED\n\n${result.reason}"
            }
            output.text = "G4.1 Gemma smoke\n\n$rendered"
            android.util.Log.i(TAG, rendered.replace('\n', ' '))
        }
    }

    companion object {
        private const val TAG = "TinoGemmaSmoke"
        private const val MAX_PREVIEW_CHARS = 500
        const val EXTRA_KILL_GEMMA = "kill_gemma"
        private const val SMOKE_PROMPT =
            "Responda somente com a palavra OK. Este é um teste local de inferência."
    }
}
