package com.tino.app.core.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GemmaTextInferenceResult {
    data class Generated(val text: String) : GemmaTextInferenceResult
    data class Unavailable(val reason: String) : GemmaTextInferenceResult
    data class Failed(val reason: String) : GemmaTextInferenceResult
}

/** Single MediaPipe boundary shared by inline extraction and global voice commands. */
interface GemmaTextInference {
    suspend fun generate(prompt: String): GemmaTextInferenceResult

    /** Allows structured adapters to report malformed model output to the breaker. */
    fun reportMalformedOutput() = Unit
}

@Singleton
class MediaPipeGemmaTextInference @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : GemmaTextInference {
    private val requestLock = Mutex()
    private val circuitBreaker = GemmaCircuitBreaker()

    override suspend fun generate(prompt: String): GemmaTextInferenceResult {
        return requestLock.withLock {
            if (!circuitBreaker.tryAcquire()) {
                return@withLock GemmaTextInferenceResult.Unavailable(
                    "A inteligência local está temporariamente indisponível. Tente novamente em instantes.",
                )
            }
            try {
                val result = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { requestIsolatedProcess(prompt) }
                if (result == null) {
                    circuitBreaker.recordFailure()
                    return@withLock GemmaTextInferenceResult.Unavailable(
                        "A inteligência local demorou mais que o esperado. Tente novamente.",
                    )
                }
                when (result) {
                    is GemmaTextInferenceResult.Generated -> circuitBreaker.recordSuccess()
                    is GemmaTextInferenceResult.Unavailable,
                    is GemmaTextInferenceResult.Failed,
                    -> circuitBreaker.recordFailure()
                }
                result
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                circuitBreaker.recordFailure()
                GemmaTextInferenceResult.Unavailable(
                    "Não consegui organizar os dados. Vou continuar pelo caminho seguro.",
                )
            }
        }
    }

    override fun reportMalformedOutput() {
        circuitBreaker.recordFailure()
    }

    private suspend fun requestIsolatedProcess(prompt: String): GemmaTextInferenceResult =
        suspendCancellableCoroutine { continuation ->
            val requestId = nextRequestId++
            var bound = false
            lateinit var connection: ServiceConnection
            val responseHandler = Handler(Looper.getMainLooper()) { message ->
                if (message.what != GemmaInferenceProtocol.RESPONSE || message.arg1 != requestId) {
                    return@Handler true
                }
                if (bound) runCatching { context.unbindService(connection) }
                bound = false
                continuation.resume(GemmaInferenceProtocol.resultFrom(message.data))
                true
            }
            val replyMessenger = Messenger(responseHandler)
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val messenger = Messenger(service)
                    val request = Message.obtain(null, GemmaInferenceProtocol.REQUEST, requestId, 0).apply {
                        data = Bundle().apply { putString(GemmaInferenceProtocol.PROMPT, prompt) }
                        replyTo = replyMessenger
                    }
                    runCatching { messenger.send(request) }.onFailure {
                        if (bound) runCatching { context.unbindService(connection) }
                        bound = false
                        continuation.resume(GemmaTextInferenceResult.Unavailable("Gemma isolado indisponível."))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    bound = false
                    if (continuation.isActive) {
                        continuation.resume(GemmaTextInferenceResult.Unavailable("Gemma isolado foi encerrado; usando fallback."))
                    }
                }

                override fun onBindingDied(name: ComponentName?) {
                    bound = false
                    if (continuation.isActive) {
                        continuation.resume(GemmaTextInferenceResult.Unavailable("Gemma isolado perdeu a conexão; usando fallback."))
                    }
                }
            }
            val intent = Intent(context, GemmaInferenceService::class.java)
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                continuation.resume(GemmaTextInferenceResult.Unavailable("Gemma isolado indisponível."))
            }
            continuation.invokeOnCancellation {
                if (bound) runCatching { context.unbindService(connection) }
                responseHandler.removeCallbacksAndMessages(null)
            }
        }

    companion object {
        /** Must finish before the agent query's 5 s user-facing deadline. */
        private const val REQUEST_TIMEOUT_MS = 4_500L
        private var nextRequestId = 0
    }
}
