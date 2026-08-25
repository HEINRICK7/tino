package com.tino.app.core.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android ASR adapter. It prefers the device recognizer and offline mode, then sends only
 * committed text to the Gemma boundary. Gemma remains responsible for understanding fields
 * and commands, not for pretending to be an audio decoder.
 */
@Singleton
class AndroidSpeechRecognizerRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auditLogger: AuditLogger,
) : GemmaTranscriberRuntime {
    private var activeRecognizer: SpeechRecognizer? = null
    private var activeChannel: SendChannel<TranscriptEvent>? = null
    private var latestTranscript: String = ""
    private var committedSent: Boolean = false

    override suspend fun start(): Flow<TranscriptEvent> = callbackFlow {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            trySend(TranscriptEvent.Failed("Permita o uso do microfone para falar com o TINO."))
            close()
            return@callbackFlow
        }

        val recognizerAvailable = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
        val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        val provider = selectSpeechProvider(
            onDeviceAvailable = onDeviceAvailable,
            recognizerAvailable = recognizerAvailable,
        )
        auditLogger.record(
            AuditEventType.VOICE_STAGE,
            mapOf(
                "stage" to "SPEECH_PROVIDER_SELECTED",
                "speech_provider" to provider.name,
                "recognizer_available" to recognizerAvailable.toString(),
                "on_device_available" to onDeviceAvailable.toString(),
                "locale" to "pt-BR",
            ),
        )
        if (provider == SpeechProvider.NONE) {
            trySend(TranscriptEvent.Failed("Nenhum serviço de reconhecimento de voz está disponível. Use o teclado para continuar."))
            close()
            return@callbackFlow
        }

        var usingOnDeviceRecognizer = provider == SpeechProvider.ON_DEVICE &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val recognizer = try {
            if (usingOnDeviceRecognizer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (_: Throwable) {
            if (!usingOnDeviceRecognizer) {
                trySend(TranscriptEvent.Failed("Não foi possível iniciar o reconhecimento de voz. Use o teclado para continuar."))
                close()
                return@callbackFlow
            }
            usingOnDeviceRecognizer = false
            auditLogger.record(
                AuditEventType.VOICE_STAGE,
                mapOf(
                    "stage" to "SPEECH_PROVIDER_FALLBACK",
                    "speech_provider" to SpeechProvider.ANDROID_STANDARD.name,
                    "reason_code" to "ON_DEVICE_INIT_FAILED",
                    "locale" to "pt-BR",
                ),
            )
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
                ?: run {
                    trySend(TranscriptEvent.Failed("Não foi possível iniciar o reconhecimento de voz. Use o teclado para continuar."))
                    close()
                    return@callbackFlow
                }
        }

        activeRecognizer = recognizer
        activeChannel = this
        latestTranscript = ""
        committedSent = false

        fun commit(text: String) {
            if (committedSent || text.isBlank()) return
            committedSent = true
            latestTranscript = text
            trySend(TranscriptEvent.Committed(text))
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() {
                activeChannel?.trySend(TranscriptEvent.SpeechStarted)
            }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                activeChannel?.trySend(TranscriptEvent.EndOfSpeech)
            }
            override fun onPartialResults(results: android.os.Bundle?) {
                results.firstRecognition()?.let {
                    latestTranscript = it
                    trySend(TranscriptEvent.Partial(it))
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                results.firstRecognition()?.let(::commit)
                close()
            }

            override fun onError(error: Int) {
                trySend(TranscriptEvent.Failed(errorMessage(error)))
                close()
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })

        trySend(TranscriptEvent.MicStarted)
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                if (usingOnDeviceRecognizer) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            },
        )

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
            if (activeRecognizer === recognizer) {
                activeRecognizer = null
                activeChannel = null
                latestTranscript = ""
                committedSent = false
            }
        }
    }.flowOn(Dispatchers.Main.immediate)

    override suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        val recognizer = activeRecognizer ?: return@withContext
        val channel = activeChannel
        val transcript = latestTranscript
        if (!committedSent && transcript.isNotBlank()) {
            committedSent = true
            channel?.trySend(TranscriptEvent.Committed(transcript))
        }
        recognizer.stopListening()
        channel?.close()
    }

    private fun android.os.Bundle?.firstRecognition(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Permita o uso do microfone para falar com o TINO."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        -> "Não consegui ouvir agora. Tente novamente ou preencha abaixo."
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> "Não entendi direito. Fale novamente ou preencha abaixo."
        else -> "Não consegui ouvir agora. Tente novamente ou preencha abaixo."
    }
}
