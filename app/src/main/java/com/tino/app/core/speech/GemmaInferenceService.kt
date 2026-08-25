package com.tino.app.core.speech

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.tino.app.BuildConfig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal object GemmaInferenceProtocol {
    const val REQUEST = 1
    const val RESPONSE = 2
    const val PROMPT = "prompt"
    const val STATUS = "status"
    const val TEXT = "text"
    const val REASON = "reason"
    const val GENERATED = 1
    const val UNAVAILABLE = 2
    const val FAILED = 3

    fun resultFrom(bundle: Bundle): GemmaTextInferenceResult = when (bundle.getInt(STATUS)) {
        GENERATED -> GemmaTextInferenceResult.Generated(bundle.getString(TEXT).orEmpty())
        FAILED -> GemmaTextInferenceResult.Failed(bundle.getString(REASON).orEmpty())
        else -> GemmaTextInferenceResult.Unavailable(bundle.getString(REASON).orEmpty())
    }
}

const val GEMMA_DEBUG_KILL_ACTION = "com.tino.app.debug.KILL_GEMMA"

/**
 * Owns the native MediaPipe call. The service is deliberately in :gemma so a
 * SIGSEGV in libllm_inference_engine_jni.so cannot kill the main app process.
 */
@AndroidEntryPoint
class GemmaInferenceService : Service() {
    @Inject lateinit var engine: IsolatedGemmaInferenceEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what != GemmaInferenceProtocol.REQUEST) return@Handler true
        val prompt = message.data.getString(GemmaInferenceProtocol.PROMPT).orEmpty()
        val requestId = message.arg1
        val replyTo = message.replyTo
        scope.launch {
            val result = engine.generate(prompt)
            val response = Message.obtain(null, GemmaInferenceProtocol.RESPONSE, requestId, 0).apply {
                data = result.toBundle()
            }
            runCatching { replyTo.send(response) }
        }
        true
    })

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (BuildConfig.DEBUG && intent?.action == GEMMA_DEBUG_KILL_ACTION) {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

@Singleton
class IsolatedGemmaInferenceEngine @Inject constructor(
    private val modelStore: GemmaModelStore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    private val inferenceLock = Any()
    private var inference: com.google.mediapipe.tasks.genai.llminference.LlmInference? = null
    private var loadedModelPath: String? = null

    suspend fun generate(prompt: String): GemmaTextInferenceResult = kotlinx.coroutines.withContext(Dispatchers.Default) {
        val modelPath = modelStore.availableModelPath()
            ?: return@withContext GemmaTextInferenceResult.Unavailable(
                "A voz ainda não está disponível neste aparelho. Preencha abaixo.",
            )
        runCatching {
            val response = synchronized(inferenceLock) {
                getOrCreateInference(modelPath).generateResponse(prompt)
            }
            GemmaTextInferenceResult.Generated(response)
        }.getOrElse { error ->
            android.util.Log.e("TinoGemmaIsolated", "inference failed: ${error::class.java.simpleName}: ${error.message}")
            GemmaTextInferenceResult.Failed("Não consegui organizar os dados. Fale novamente ou preencha abaixo.")
        }
    }

    private fun getOrCreateInference(modelPath: String): com.google.mediapipe.tasks.genai.llminference.LlmInference {
        if (loadedModelPath != modelPath) {
            inference?.close()
            inference = com.google.mediapipe.tasks.genai.llminference.LlmInference.createFromOptions(
                context,
                com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
                    .setMaxTopK(64)
                    .build(),
            )
            loadedModelPath = modelPath
        }
        return requireNotNull(inference)
    }

    companion object {
        private const val MAX_TOKENS = 1024
    }
}

private fun GemmaTextInferenceResult.toBundle(): Bundle = Bundle().apply {
    when (this@toBundle) {
        is GemmaTextInferenceResult.Generated -> {
            putInt(GemmaInferenceProtocol.STATUS, GemmaInferenceProtocol.GENERATED)
            putString(GemmaInferenceProtocol.TEXT, text)
        }
        is GemmaTextInferenceResult.Unavailable -> {
            putInt(GemmaInferenceProtocol.STATUS, GemmaInferenceProtocol.UNAVAILABLE)
            putString(GemmaInferenceProtocol.REASON, reason)
        }
        is GemmaTextInferenceResult.Failed -> {
            putInt(GemmaInferenceProtocol.STATUS, GemmaInferenceProtocol.FAILED)
            putString(GemmaInferenceProtocol.REASON, reason)
        }
    }
}
