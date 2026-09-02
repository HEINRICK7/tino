package com.tino.app.debug

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tino.app.domain.intelligence.TinoEvidenceSnapshotBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Debug-only, read-only inventory of the evidence/model-readiness boundary.
 * It reports the actual Room-backed dataset without creating transactions.
 */
@AndroidEntryPoint
class IntelligenceEvidenceSnapshotSmokeActivity : ComponentActivity() {
    @Inject lateinit var snapshotBuilder: TinoEvidenceSnapshotBuilder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val output = TextView(this).apply {
            textSize = 18f
            text = "G4.5\n\nLendo evidências locais..."
            setPadding(32, 48, 32, 48)
        }
        setContentView(output)

        lifecycleScope.launch {
            val rendered = runCatching { snapshotBuilder.build(screen = "Home") }
                .fold(
                    onSuccess = { snapshot ->
                        buildString {
                            appendLine("read_status=PASS")
                            appendLine("products=${snapshot.products.size}")
                            appendLine("customers=${snapshot.customers.size}")
                            appendLine("products_with_sales=${snapshot.products.count { it.unitsSoldByDate.isNotEmpty() }}")
                            appendLine("products_with_model_evaluation=${snapshot.products.count { it.demandModelEvaluation != null }}")
                            appendLine("products_with_passing_model=${snapshot.products.count { it.demandModelEvaluation?.passesGate == true }}")
                            appendLine(
                                "model_readiness=" + when {
                                    snapshot.products.isEmpty() -> "INSUFFICIENT_DATA_NO_PRODUCTS"
                                    snapshot.products.none { it.demandModelEvaluation != null } -> "INSUFFICIENT_DATA_NO_VALIDATION_WINDOW"
                                    snapshot.products.any { it.demandModelEvaluation?.passesGate == true } -> "READY_WITH_PASSING_MODEL"
                                    else -> "NO_MODEL_PASSED_GATE"
                                },
                            )
                            snapshot.products.forEach { product ->
                                val evaluation = product.demandModelEvaluation
                                appendLine(
                                    "product=${product.name} stock=${product.stockQuantity} " +
                                        "sale_days=${product.unitsSoldByDate.size} " +
                                        "evaluation=${evaluation?.let { "windows=${it.validationWindows},mape=${it.meanAbsolutePercentageError},coverage=${it.intervalCoverage},passes=${it.passesGate}" } ?: "INSUFFICIENT_DATA"}",
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        "FAIL\n${error::class.simpleName}: ${error.message}"
                    },
                )
            output.text = "G4.5 Evidence/model-readiness smoke\n\n$rendered"
            android.util.Log.i(TAG, rendered.replace('\n', ' '))
        }
    }

    companion object {
        private const val TAG = "TinoEvidenceSmoke"
    }
}
