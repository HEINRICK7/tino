package com.tino.app.debug

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.tino.app.core.intelligence.TinoAttentionNotificationPublisher
import com.tino.app.domain.intelligence.AttentionDigest
import com.tino.app.domain.intelligence.AttentionRecord
import com.tino.app.domain.intelligence.AttentionState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Debug-only publisher smoke. It exercises the Android notification boundary
 * without inserting an artificial insight into Room or changing commerce data.
 */
@AndroidEntryPoint
class AttentionNotificationSmokeActivity : ComponentActivity() {
    @Inject lateinit var publisher: TinoAttentionNotificationPublisher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        publisher.publish(
            AttentionDigest(
                generatedAtEpochMs = System.currentTimeMillis(),
                items = listOf(
                    AttentionRecord(
                        id = DEBUG_ATTENTION_ID,
                        insightId = DEBUG_ATTENTION_ID,
                        subjectId = null,
                        title = "Teste de atenção",
                        explanation = "Notificação local de validação do TINO.",
                        evidenceIds = emptyList(),
                        relevance = 100,
                        urgency = 100,
                        confidence = 1.0,
                        state = AttentionState.ACTIVE,
                        createdAtEpochMs = System.currentTimeMillis(),
                        lastSeenAtEpochMs = System.currentTimeMillis(),
                    ),
                ),
            ),
        )
        setContentView(TextView(this).apply {
            text = "G4.2\n\nNotificação publicada."
            textSize = 18f
            setPadding(32, 48, 32, 48)
        })
    }

    companion object {
        const val DEBUG_ATTENTION_ID = "debug-attention-smoke"
    }
}
