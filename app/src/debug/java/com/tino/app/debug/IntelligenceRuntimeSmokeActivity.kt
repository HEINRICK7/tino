package com.tino.app.debug

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tino.app.domain.intelligence.IntelligenceRequest
import com.tino.app.domain.intelligence.IntelligenceRuntimePort
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Debug-only read-only smoke for the complete intelligence query path.
 * It uses the deterministic planner; the executor only reads Room-backed
 * facts and deterministic analytics, and cannot mutate commerce.
 */
@AndroidEntryPoint
class IntelligenceRuntimeSmokeActivity : ComponentActivity() {
    @Inject lateinit var runtime: IntelligenceRuntimePort

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val output = TextView(this).apply {
            textSize = 18f
            text = "G4.3\n\nConsultando o Intelligence Runtime..."
            setPadding(32, 48, 32, 48)
        }
        setContentView(output)

        lifecycleScope.launch {
            val utterance = intent.getStringExtra(EXTRA_INPUT)?.replace("%20", " ")?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_INPUT
            val result = runCatching {
                runtime.execute(
                    IntelligenceRequest(
                        requestId = "debug-runtime-${System.nanoTime()}",
                        sessionId = "debug-runtime-smoke",
                        utterance = utterance,
                        screenContext = "Home",
                        timestampEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
            val rendered = result.fold(
                onSuccess = { response ->
                    "PASS\n\ninput=$utterance\nstatus=${response.status}\nplanner=${response.plannerUsed}\nconfidence=${response.confidence}\nanswer=${response.answer}\nfacts=${response.factsUsed.joinToString(",")}\nanalytics=${response.analyticsUsed.joinToString(",")}\nlimitations=${response.limitations.joinToString(" | ")}"
                },
                onFailure = { error ->
                    "FAIL\n\ninput=$utterance\n${error::class.simpleName}: ${error.message}"
                },
            )
            output.text = "G4.3 Intelligence Runtime smoke\n\n$rendered"
            android.util.Log.i(TAG, rendered.replace('\n', ' '))
        }
    }

    companion object {
        private const val TAG = "TinoIntelligenceSmoke"
        const val EXTRA_INPUT = "input"
        private const val DEFAULT_INPUT = "qual produto tem o menor estoque?"
    }
}
