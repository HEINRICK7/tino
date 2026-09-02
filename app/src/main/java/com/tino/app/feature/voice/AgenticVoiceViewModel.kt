package com.tino.app.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tino.app.core.speech.LiveTranscriberPort
import com.tino.app.core.speech.TranscriptEvent
import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.domain.agent.AgentA2uiResponse
import com.tino.app.domain.agent.AgentCapability
import com.tino.app.domain.agent.AgentIntentDebugInfo
import com.tino.app.domain.agent.AgenticTextQueryPort
import com.tino.app.domain.agent.AgentUndoService
import com.tino.app.domain.agent.AgentStreamEventType
import com.tino.app.domain.agent.AgentStreamingRuntime
import com.tino.app.domain.agent.FastIntentRouter
import com.tino.app.domain.agent.FastNavigationTarget
import com.tino.app.domain.agent.requiredCapability
import com.tino.app.domain.agent.TinoAgentSession
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.agent.toTinoCapabilityId
import com.tino.app.domain.language.CommerceContextMemory
import com.tino.app.domain.language.EntityReference
import com.tino.app.domain.language.LanguageEntityType
import com.tino.app.domain.voice.TranscriptCommitGate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed interface AgenticVoiceState {
    data object Idle : AgenticVoiceState
    data object Cancelled : AgenticVoiceState
    data class Listening(val transcript: String = "") : AgenticVoiceState
    data class TranscriptReview(
        val originalTranscript: String,
        val transcript: String,
        val editing: Boolean = false,
    ) : AgenticVoiceState
    data class Understanding(
        val transcript: String,
        val contextLabel: String = "Consultando seus dados…",
    ) : AgenticVoiceState
    data class Navigation(
        val transcript: String,
        val target: FastNavigationTarget,
    ) : AgenticVoiceState
    data class Result(
        val transcript: String,
        val response: AgentA2uiResponse.Ready,
        val metrics: AgenticVoiceMetrics,
    ) : AgenticVoiceState
    data class CustomerBalanceResult(
        val transcript: String,
        val response: AgentA2uiResponse.CustomerBalanceReady,
        val metrics: AgenticVoiceMetrics,
    ) : AgenticVoiceState
    data class CustomerTimelineResult(
        val transcript: String,
        val response: AgentA2uiResponse.CustomerTimelineReady,
        val metrics: AgenticVoiceMetrics,
    ) : AgenticVoiceState
    data class ReadListResult(
        val transcript: String,
        val response: AgentA2uiResponse.ReadListReady,
        val metrics: AgenticVoiceMetrics,
    ) : AgenticVoiceState
    data class IntelligenceResult(
        val transcript: String,
        val response: AgentA2uiResponse.IntelligenceReady,
        val metrics: AgenticVoiceMetrics,
    ) : AgenticVoiceState
    data class EntityChoice(
        val transcript: String,
        val response: AgentA2uiResponse.EntityChoice,
        val metrics: AgenticVoiceMetrics,
    ) : AgenticVoiceState
    data class ActionPreview(
        val transcript: String,
        val response: AgentA2uiResponse.ActionPreview,
        val metrics: AgenticVoiceMetrics,
    ) : AgenticVoiceState
    data class ActionCompleted(
        val transcript: String,
        val response: AgentA2uiResponse.ActionCompleted,
    ) : AgenticVoiceState
    data class Unsupported(
        val transcript: String,
        val message: String,
        val debug: AgentIntentDebugInfo? = null,
    ) : AgenticVoiceState
    data class Error(val transcript: String, val message: String) : AgenticVoiceState
}

data class AgenticVoiceMetrics(
    val ttfpMs: Long?,
    val voiceFinalMs: Long?,
    val fastRouterMs: Long = 0L,
    val fastRouterHit: Boolean = false,
    val commandRouterMs: Long = 0L,
    val commandRouterHit: Boolean = false,
    val intentMs: Long,
    val customerResolutionMs: Long? = null,
    val productResolutionMs: Long? = null,
    val capabilityMs: Long,
    val a2uiMs: Long,
    val totalToCardMs: Long,
    val firstPartialMs: Long? = null,
    val lastPartialMs: Long? = null,
    val endOfSpeechMs: Long? = null,
    val finalResultMs: Long? = null,
    val speculativeRouterHit: Boolean = false,
    val speculativeRouterCancelled: Boolean = false,
    val speculativeResultReady: Boolean = false,
    val finalTranscriptConfirmed: Boolean = true,
) {
    /** Time from the final transcript handoff until the rendered card is ready. */
    val postFinalToCardMs: Long
        get() = (totalToCardMs - (voiceFinalMs ?: totalToCardMs)).coerceAtLeast(0L)

    val firstToLastPartialMs: Long?
        get() = firstPartialMs?.let { first -> lastPartialMs?.minus(first)?.coerceAtLeast(0L) }

    val lastPartialToEndOfSpeechMs: Long?
        get() = lastPartialMs?.let { last -> endOfSpeechMs?.minus(last)?.coerceAtLeast(0L) }

    val endOfSpeechToFinalMs: Long?
        get() = endOfSpeechMs?.let { end -> finalResultMs?.minus(end)?.coerceAtLeast(0L) }
}

data class VoiceTranscriptValidationMetrics(
    val partialCount: Int = 0,
    val revisedCount: Int = 0,
    val committedCount: Int = 0,
    val agentExecutionsBeforeSend: Int = 0,
    val agentExecutionsAfterSend: Int = 0,
    val originalTranscript: String? = null,
    val correctedTranscript: String? = null,
    val correctionEventCreated: Boolean = false,
)

private data class ProcessedTranscript(
    val succeeded: Boolean,
    val response: AgentA2uiResponse? = null,
)

@HiltViewModel
class AgenticVoiceViewModel @Inject constructor(
    private val transcriber: LiveTranscriberPort,
    private val query: AgenticTextQueryPort,
    private val fastIntentRouter: FastIntentRouter,
    private val undoService: AgentUndoService,
    private val agentSession: TinoAgentSession,
    private val languageContextMemory: CommerceContextMemory,
    private val auditLogger: AuditLogger,
    private val streamingRuntime: AgentStreamingRuntime,
) : ViewModel() {
    private val _state = MutableStateFlow<AgenticVoiceState>(AgenticVoiceState.Idle)
    val state: StateFlow<AgenticVoiceState> = _state.asStateFlow()
    private val _transcriptValidation = MutableStateFlow(VoiceTranscriptValidationMetrics())
    val transcriptValidation: StateFlow<VoiceTranscriptValidationMetrics> = _transcriptValidation.asStateFlow()
    private var sessionJob: Job? = null
    private var startedAtNanos: Long = 0L
    private var firstTranscriptAtNanos: Long? = null
    private var lastPartialAtNanos: Long? = null
    private var endOfSpeechAtNanos: Long? = null
    private var finalResultAtNanos: Long? = null
    private var cancelRequested = false
    private var pendingCapabilityRecovery: Pair<TinoCapabilityId, String>? = null
    private val transcriptGate = TranscriptCommitGate()
    private var transcriptAccumulated = ""
    private var originalTranscript: String? = null
    private var voiceStreamRunId: String? = null

    fun start() {
        startVoiceSession(resetDraft = true)
    }

    private fun startVoiceSession(resetDraft: Boolean) {
        if (sessionJob?.isActive == true) return
        auditLogger.record(AuditEventType.VOICE_START)
        startedAtNanos = System.nanoTime()
        firstTranscriptAtNanos = null
        lastPartialAtNanos = null
        endOfSpeechAtNanos = null
        finalResultAtNanos = null
        cancelRequested = false
        if (resetDraft) voiceStreamRunId = "voice-${UUID.randomUUID()}"
        val streamRunId = voiceStreamRunId ?: "voice-${UUID.randomUUID()}".also { voiceStreamRunId = it }
        if (resetDraft) {
            transcriptAccumulated = ""
            originalTranscript = null
            languageContextMemory.resetVoiceCorrectionTelemetry()
            _transcriptValidation.value = VoiceTranscriptValidationMetrics()
        }
        transcriptGate.reset()
        _state.value = AgenticVoiceState.Listening()
        agentSession.beginListening()
        sessionJob = viewModelScope.launch {
            var finalTranscript: String? = null
            try {
                publishStreamEvent(
                    streamRunId,
                    AgentStreamEventType.SPEECH,
                    mapOf("phase" to "LISTENING"),
                )
                val finalEvent = withTimeout(VOICE_TIMEOUT_MS) {
                    transcriber.start()
                        .onEach { event -> handleTranscriptEvent(event) }
                        .first { event -> event is TranscriptEvent.Committed || event is TranscriptEvent.Failed }
                }
                if (cancelRequested) return@launch
                when (finalEvent) {
                    is TranscriptEvent.Committed -> {
                        finalTranscript = finalEvent.text
                    }
                    is TranscriptEvent.Failed -> {
                        auditLogger.record(AuditEventType.VOICE_FAILURE, mapOf("stage" to "transcription"))
                        _state.value = AgenticVoiceState.Error(
                            transcript = currentTranscript(),
                            message = finalEvent.reason,
                        )
                    }
                    else -> Unit
                }
            } catch (_: TimeoutCancellationException) {
                closeVoiceStream(AgentStreamEventType.FAILED, mapOf("reason" to "timeout"))
                auditLogger.record(AuditEventType.VOICE_FAILURE, mapOf("stage" to "timeout"))
                _state.value = AgenticVoiceState.Error(
                    transcript = currentTranscript(),
                    message = if (finalTranscript != null || _state.value is AgenticVoiceState.Understanding) {
                        "Recebi sua frase, mas demorei para processá-la. Tente novamente."
                    } else {
                        "Não ouvi uma frase completa. Tente novamente."
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                closeVoiceStream(AgentStreamEventType.FAILED, mapOf("reason" to (error.message ?: "runtime")))
                auditLogger.record(AuditEventType.VOICE_FAILURE, mapOf("stage" to "runtime"))
                _state.value = AgenticVoiceState.Error(
                    transcript = currentTranscript(),
                    message = error.message ?: "Não foi possível processar sua fala.",
                )
            } finally {
                withContext(NonCancellable) { runCatching { transcriber.stop() } }
            }
        }
    }

    fun beginTranscriptEdit() {
        val review = _state.value as? AgenticVoiceState.TranscriptReview ?: return
        transcriptGate.edit(review.transcript)
        _state.value = review.copy(editing = true)
    }

    fun updateTranscript(text: String) {
        val review = _state.value as? AgenticVoiceState.TranscriptReview ?: return
        transcriptGate.edit(text)
        _state.value = review.copy(transcript = text, editing = true)
    }

    fun cancelTranscriptEdit() {
        val review = _state.value as? AgenticVoiceState.TranscriptReview ?: return
        languageContextMemory.discardVoiceCorrection()
        transcriptGate.review(review.originalTranscript)
        _state.value = review.copy(transcript = review.originalTranscript, editing = false)
    }

    fun continueSpeaking() {
        val review = _state.value as? AgenticVoiceState.TranscriptReview ?: return
        if (review.transcript.isBlank()) return
        transcriptAccumulated = review.transcript.trim()
        if (originalTranscript == null) originalTranscript = review.originalTranscript
        startVoiceSession(resetDraft = false)
    }

    fun submitTranscriptReview() {
        val review = _state.value as? AgenticVoiceState.TranscriptReview ?: return
        val transcript = review.transcript.trim()
        if (transcript.isBlank() || sessionJob?.isActive == true) return
        transcriptGate.commit(transcript)
        if (review.originalTranscript.trim() != transcript) {
            languageContextMemory.queueVoiceCorrection(review.originalTranscript, transcript)
            _transcriptValidation.value = _transcriptValidation.value.copy(
                correctedTranscript = transcript,
            )
            auditLogger.record(
                AuditEventType.VOICE_CORRECTION_QUEUED,
                mapOf("transcript_state" to "REVIEW_EDITED"),
            )
        }
        sessionJob = viewModelScope.launch {
            runCatching {
                transcriptGate.processing()
                val validation = _transcriptValidation.value
                _transcriptValidation.value = validation.copy(
                    agentExecutionsBeforeSend = 0,
                    agentExecutionsAfterSend = validation.agentExecutionsAfterSend + 1,
                )
                auditLogger.record(
                    AuditEventType.VOICE_AGENT_SUBMITTED,
                    mapOf(
                        "agent_execution_count" to _transcriptValidation.value.agentExecutionsAfterSend.toString(),
                        "agent_executions_before_send" to "0",
                        "transcript_state" to "COMMITTED_REVIEW",
                    ),
                )
                val processed = processFinalTranscript(transcript)
                val correctionEvent = if (processed.succeeded) {
                    processed.response
                        ?.resolvedCorrectionReference()
                        ?.let { languageContextMemory.prepareVoiceCorrectionForResolvedReference(it) }
                    languageContextMemory.commitVoiceCorrection()
                } else {
                    languageContextMemory.discardVoiceCorrection()
                    null
                }
                correctionEvent?.let {
                    _transcriptValidation.value = _transcriptValidation.value.copy(correctionEventCreated = true)
                    auditLogger.record(
                        AuditEventType.VOICE_CORRECTION_EVENT,
                        mapOf("correction_status" to "CREATED"),
                    )
                }
            }.onFailure { error ->
                languageContextMemory.discardVoiceCorrection()
                if (error !is CancellationException && !cancelRequested) {
                    auditLogger.record(AuditEventType.INTENT_RESOLUTION_FAILURE, mapOf("stage" to "review"))
                    _state.value = AgenticVoiceState.Error(
                        transcript,
                        error.message ?: "Não foi possível consultar o TINO.",
                    )
                }
            }.also { voiceStreamRunId = null }
        }
    }

    fun stop() {
        if (sessionJob?.isActive != true) return
        viewModelScope.launch {
            runCatching { transcriber.stop() }
        }
    }

    fun cancel() {
        cancelRequested = true
        languageContextMemory.discardVoiceCorrection()
        sessionJob?.cancel()
        sessionJob = null
        val streamRunId = voiceStreamRunId
        voiceStreamRunId = null
        _state.value = AgenticVoiceState.Cancelled
        agentSession.cancel()
        viewModelScope.launch {
            withContext(NonCancellable) {
                if (streamRunId != null) {
                    runCatching { streamingRuntime.close(streamRunId, AgentStreamEventType.CANCELLED) }
                }
                runCatching { transcriber.stop() }
            }
        }
    }

    fun retry() = start()

    fun consumeNavigation() {
        if (_state.value is AgenticVoiceState.Navigation) _state.value = AgenticVoiceState.Idle
    }

    /** Text and voice share the same canonical AgenticTextQueryPort pipeline. */
    fun submitText(text: String) {
        val normalized = text.trim()
        if (normalized.isBlank() || sessionJob?.isActive == true) return
        startedAtNanos = System.nanoTime()
        firstTranscriptAtNanos = startedAtNanos
        lastPartialAtNanos = null
        endOfSpeechAtNanos = null
        finalResultAtNanos = startedAtNanos
        cancelRequested = false
        sessionJob = viewModelScope.launch {
            runCatching { processFinalTranscript(normalized) }
                .onFailure { error ->
                    if (error !is CancellationException && !cancelRequested) {
                        auditLogger.record(AuditEventType.INTENT_RESOLUTION_FAILURE, mapOf("stage" to "text"))
                        _state.value = AgenticVoiceState.Error(
                            normalized,
                            error.message ?: "Não foi possível consultar o TINO.",
                        )
                    }
                }
        }
    }

    /** Semantic action path used by Home Quick Queries; it never synthesizes speech. */
    fun submitCapability(capability: AgentCapability, label: String, subjectId: String? = null) {
        if (sessionJob?.isActive == true) return
        val transcript = label.trim().ifBlank { "Consulta rápida" }
        val requiredCapability = capability.toTinoCapabilityId()
        val availableCapabilities = agentSession.availableCapabilities()
        if (requiredCapability != null && availableCapabilities.isNotEmpty() && requiredCapability !in availableCapabilities) {
            pendingCapabilityRecovery = requiredCapability to transcript
            _state.value = AgenticVoiceState.Unsupported(
                transcript,
                "Esse recurso não está ativo para este negócio.",
                AgentIntentDebugInfo(
                    code = "CAPABILITY_DISABLED",
                    capability = requiredCapability.name,
                    observedKeys = emptySet(),
                ),
            )
            return
        }
        cancelRequested = false
        sessionJob = viewModelScope.launch {
            agentSession.beginUnderstanding()
            _state.value = AgenticVoiceState.Understanding(transcript, "Consultando…")
            try {
                val response = withTimeout(FAST_QUERY_TIMEOUT_MS) { query.askCapability(capability, subjectId) }
                when (response) {
                    is AgentA2uiResponse.Ready -> _state.value = AgenticVoiceState.Result(transcript, response, metrics(response))
                    is AgentA2uiResponse.CustomerBalanceReady -> _state.value = AgenticVoiceState.CustomerBalanceResult(transcript, response, metrics(response))
                    is AgentA2uiResponse.CustomerTimelineReady -> _state.value = AgenticVoiceState.CustomerTimelineResult(transcript, response, metrics(response))
                    is AgentA2uiResponse.ReadListReady -> _state.value = AgenticVoiceState.ReadListResult(transcript, response, metrics(response))
                    is AgentA2uiResponse.IntelligenceReady -> _state.value = AgenticVoiceState.IntelligenceResult(transcript, response, metrics(response))
                    is AgentA2uiResponse.EntityChoice -> _state.value = AgenticVoiceState.EntityChoice(transcript, response, metrics(response))
                    is AgentA2uiResponse.ActionPreview -> _state.value = AgenticVoiceState.ActionPreview(transcript, response, metrics(response))
                    is AgentA2uiResponse.ActionCompleted -> _state.value = AgenticVoiceState.ActionCompleted(transcript, response)
                    is AgentA2uiResponse.Unsupported -> _state.value = AgenticVoiceState.Unsupported(transcript, response.message, response.debug)
                }
            } catch (_: TimeoutCancellationException) {
                _state.value = AgenticVoiceState.Error(transcript, "A consulta demorou mais que o esperado. Tente novamente.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.value = AgenticVoiceState.Error(transcript, error.message ?: "Não foi possível consultar agora.")
            }
        }
    }

    /** Runs the blocked read/mutation once without changing the persisted profile. */
    fun useCapabilityOnce() {
        val recovery = pendingCapabilityRecovery ?: return
        pendingCapabilityRecovery = null
        auditLogger.record(
            AuditEventType.VOICE_STAGE,
            mapOf(
                "stage" to "CAPABILITY_RECOVERY_GRANTED",
                "mode" to "EPHEMERAL",
                "capability" to recovery.first.name,
            ),
        )
        agentSession.grantEphemeralCapability(recovery.first)
        submitText(recovery.second)
    }

    fun confirmAction(action: AgenticVoiceState.ActionPreview) {
        if (sessionJob?.isActive == true) return
        _state.value = AgenticVoiceState.Understanding(action.transcript, "Registrando…")
        sessionJob = viewModelScope.launch {
            try {
                val response = withTimeout(CONFIRMATION_TIMEOUT_MS) { query.confirm(action.response) }
                if (!cancelRequested) {
                    _state.value = AgenticVoiceState.ActionCompleted(action.transcript, response)
                }
            } catch (_: TimeoutCancellationException) {
                auditLogger.record(
                    AuditEventType.VOICE_STAGE,
                    mapOf("stage" to "TIMEOUT", "route" to "confirm", "timeout_ms" to CONFIRMATION_TIMEOUT_MS.toString()),
                )
                _state.value = AgenticVoiceState.Error(
                    action.transcript,
                    "A confirmação demorou mais que o esperado. Nada foi alterado; tente novamente.",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                auditLogger.record(AuditEventType.MUTATION_FAILURE, mapOf("stage" to "confirm"))
                _state.value = AgenticVoiceState.Error(
                    action.transcript,
                    error.message ?: "Não foi possível concluir o fiado.",
                )
            }
        }
    }

    fun undo(activityId: String) {
        if (sessionJob?.isActive == true) return
        sessionJob = viewModelScope.launch {
            try {
                withTimeout(UNDO_TIMEOUT_MS) { undoService.undo(activityId) }
                _state.value = AgenticVoiceState.Cancelled
            } catch (_: TimeoutCancellationException) {
                auditLogger.record(
                    AuditEventType.VOICE_STAGE,
                    mapOf("stage" to "TIMEOUT", "route" to "undo", "timeout_ms" to UNDO_TIMEOUT_MS.toString()),
                )
                _state.value = AgenticVoiceState.Error(
                    transcript = "",
                    message = "Desfazer demorou mais que o esperado. Tente novamente.",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                auditLogger.record(AuditEventType.MUTATION_FAILURE, mapOf("stage" to "undo"))
                _state.value = AgenticVoiceState.Error(
                    transcript = "",
                    message = error.message ?: "Não foi possível desfazer a operação.",
                )
            }
        }
    }

    fun selectEntityChoice(state: AgenticVoiceState.EntityChoice, label: String) {
        if (sessionJob?.isActive == true) return
        cancelRequested = false
        sessionJob = viewModelScope.launch {
            _state.value = AgenticVoiceState.Understanding(
                state.transcript,
                fastIntentRouter.contextLabel(state.transcript),
            )
            try {
                val response = withTimeout(AGENT_QUERY_TIMEOUT_MS) {
                    query.selectEntityChoice(state.response, label)
                }
                when (response) {
                        is AgentA2uiResponse.Ready -> _state.value = AgenticVoiceState.Result(
                            state.transcript,
                            response,
                            metrics(response),
                        )
                        is AgentA2uiResponse.CustomerBalanceReady -> _state.value = AgenticVoiceState.CustomerBalanceResult(
                            state.transcript,
                            response,
                            metrics(response),
                        )
                        is AgentA2uiResponse.CustomerTimelineReady -> _state.value = AgenticVoiceState.CustomerTimelineResult(
                            state.transcript,
                            response,
                            metrics(response),
                        )
                        is AgentA2uiResponse.ReadListReady -> _state.value = AgenticVoiceState.ReadListResult(
                            state.transcript,
                            response,
                            metrics(response),
                        )
                        is AgentA2uiResponse.IntelligenceReady -> _state.value = AgenticVoiceState.IntelligenceResult(
                            state.transcript,
                            response,
                            metrics(response),
                        )
                        is AgentA2uiResponse.ActionPreview -> _state.value = AgenticVoiceState.ActionPreview(
                            state.transcript,
                            response,
                            metrics(response),
                        )
                        is AgentA2uiResponse.EntityChoice -> _state.value = AgenticVoiceState.EntityChoice(
                            state.transcript,
                            response,
                            metrics(response),
                        )
                        is AgentA2uiResponse.Unsupported -> _state.value = AgenticVoiceState.Unsupported(
                            state.transcript,
                            response.message,
                            response.debug,
                        )
                        is AgentA2uiResponse.ActionCompleted -> if (!cancelRequested) {
                            agentSession.rememberResult(response.result.message)
                            _state.value = AgenticVoiceState.ActionCompleted(state.transcript, response)
                            auditLogger.record(
                                AuditEventType.VOICE_STAGE,
                                mapOf(
                                    "stage" to "RENDERED",
                                    "route" to "entity_choice",
                                ),
                            )
                        }
                }
            } catch (_: TimeoutCancellationException) {
                auditLogger.record(
                    AuditEventType.VOICE_STAGE,
                    mapOf("stage" to "TIMEOUT", "route" to "entity_choice", "timeout_ms" to AGENT_QUERY_TIMEOUT_MS.toString()),
                )
                _state.value = AgenticVoiceState.Error(
                    state.transcript,
                    "A escolha demorou mais que o esperado. Tente novamente.",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.value = AgenticVoiceState.Error(
                    state.transcript,
                    error.message ?: "Não foi possível continuar essa operação.",
                )
            }
        }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        super.onCleared()
    }

    private fun markFirstTranscript() {
        if (firstTranscriptAtNanos == null) firstTranscriptAtNanos = System.nanoTime()
    }

    private suspend fun processFinalTranscript(transcript: String): ProcessedTranscript {
        agentSession.beginUnderstanding()
        _state.value = AgenticVoiceState.Understanding(
            transcript,
            fastIntentRouter.contextLabel(transcript),
        )
        fastIntentRouter.navigationTarget(transcript)?.let { target ->
            val requiredCapability = target.requiredCapability()
            val availableCapabilities = agentSession.availableCapabilities()
            if (availableCapabilities.isNotEmpty() && requiredCapability !in availableCapabilities) {
                pendingCapabilityRecovery = requiredCapability to transcript
                _state.value = AgenticVoiceState.Unsupported(
                    transcript,
                    "Esse recurso não está ativo para este negócio.",
                    AgentIntentDebugInfo(
                        code = "CAPABILITY_DISABLED",
                        capability = requiredCapability.name,
                        observedKeys = emptySet(),
                    ),
                )
                return ProcessedTranscript(succeeded = false)
            }
            auditLogger.record(
                AuditEventType.VOICE_STAGE,
                mapOf("stage" to "NAVIGATION_COMPLETED", "route" to target.name, "fast_path" to "true"),
            )
            _state.value = AgenticVoiceState.Navigation(transcript, target)
            completeVoiceStream(mapOf("target" to target.name))
            return ProcessedTranscript(succeeded = false)
        }
        val queryStartedAt = System.nanoTime()
        val timeoutMs = if (fastIntentRouter.route(transcript) is com.tino.app.domain.agent.FastIntentResult.Match) {
            FAST_QUERY_TIMEOUT_MS
        } else {
            AGENT_QUERY_TIMEOUT_MS
        }
        auditLogger.record(
            AuditEventType.VOICE_STAGE,
            mapOf("stage" to "QUERY_STARTED", "timeout_ms" to timeoutMs.toString()),
        )
        val response = try {
            withTimeout(timeoutMs) { query.ask(transcript) }
        } catch (_: TimeoutCancellationException) {
            val durationMs = elapsedMs(queryStartedAt, System.nanoTime())
            auditLogger.record(
                AuditEventType.VOICE_STAGE,
                mapOf(
                    "stage" to "QUERY_TIMEOUT",
                    "duration_ms" to durationMs.toString(),
                    "timeout_ms" to timeoutMs.toString(),
                ),
            )
            _state.value = AgenticVoiceState.Error(
                transcript = transcript,
                message = "A consulta demorou mais que o esperado. Tente novamente.",
            )
            return ProcessedTranscript(succeeded = false)
        }
        auditLogger.record(
            AuditEventType.VOICE_STAGE,
            mapOf(
                "stage" to "QUERY_COMPLETED",
                "duration_ms" to elapsedMs(queryStartedAt, System.nanoTime()).toString(),
                "fast_path" to (fastIntentRouter.route(transcript) is com.tino.app.domain.agent.FastIntentResult.Match).toString(),
            ),
        )
        val succeeded = when (response) {
            is AgentA2uiResponse.Ready -> if (!cancelRequested) {
                _state.value = AgenticVoiceState.Result(transcript, response, metrics(response))
                true
            } else false
            is AgentA2uiResponse.CustomerBalanceReady -> if (!cancelRequested) {
                _state.value = AgenticVoiceState.CustomerBalanceResult(transcript, response, metrics(response))
                true
            } else false
            is AgentA2uiResponse.CustomerTimelineReady -> if (!cancelRequested) {
                _state.value = AgenticVoiceState.CustomerTimelineResult(transcript, response, metrics(response))
                true
            } else false
            is AgentA2uiResponse.ReadListReady -> if (!cancelRequested) {
                _state.value = AgenticVoiceState.ReadListResult(transcript, response, metrics(response))
                true
            } else false
            is AgentA2uiResponse.IntelligenceReady -> if (!cancelRequested) {
                _state.value = AgenticVoiceState.IntelligenceResult(transcript, response, metrics(response))
                true
            } else false
            is AgentA2uiResponse.EntityChoice -> if (!cancelRequested) {
                _state.value = AgenticVoiceState.EntityChoice(transcript, response, metrics(response))
                false
            } else false
            is AgentA2uiResponse.ActionPreview -> if (!cancelRequested) {
                _state.value = AgenticVoiceState.ActionPreview(transcript, response, metrics(response))
                false
            } else false
            is AgentA2uiResponse.ActionCompleted -> if (!cancelRequested) {
                agentSession.rememberResult(response.result.message)
                _state.value = AgenticVoiceState.ActionCompleted(transcript, response)
                true
            } else false
            is AgentA2uiResponse.Unsupported -> if (!cancelRequested) {
                _state.value = AgenticVoiceState.Unsupported(transcript, response.message, response.debug)
                false
            } else false
        }
        if (!cancelRequested) {
            auditLogger.record(
                AuditEventType.VOICE_STAGE,
                mapOf(
                    "stage" to "RENDERED",
                    "duration_ms" to elapsedMs(queryStartedAt, System.nanoTime()).toString(),
                ),
            )
        }
        return ProcessedTranscript(
            succeeded = succeeded,
            response = response.takeIf { succeeded },
        )
    }

    private fun AgentA2uiResponse.resolvedCorrectionReference(): EntityReference? {
        val intent = when (this) {
            is AgentA2uiResponse.Ready -> intent
            is AgentA2uiResponse.ActionPreview -> intent
            is AgentA2uiResponse.ActionCompleted -> intent
            is AgentA2uiResponse.CustomerBalanceReady -> intent
            is AgentA2uiResponse.CustomerTimelineReady -> intent
            is AgentA2uiResponse.ReadListReady -> intent
            is AgentA2uiResponse.EntityChoice -> null
            is AgentA2uiResponse.IntelligenceReady -> null
            is AgentA2uiResponse.Unsupported -> null
        } ?: return null
        return when {
            !intent.productRef.isNullOrBlank() -> EntityReference(
                type = LanguageEntityType.PRODUCT,
                text = intent.productRef,
            )
            !intent.customerRef.isNullOrBlank() -> EntityReference(
                type = LanguageEntityType.CUSTOMER,
                text = intent.customerRef,
            )
            else -> null
        }
    }

    private suspend fun handleTranscriptEvent(event: TranscriptEvent) {
        if (cancelRequested) return
        val streamRunId = voiceStreamRunId ?: return
        publishStreamEvent(streamRunId, event)
        when (event) {
            is TranscriptEvent.Partial -> {
                markFirstTranscript()
                lastPartialAtNanos = System.nanoTime()
                val text = combineTranscript(event.text)
                transcriptGate.partial(text)
                _state.value = AgenticVoiceState.Listening(text)
                val metrics = _transcriptValidation.value.copy(partialCount = _transcriptValidation.value.partialCount + 1)
                _transcriptValidation.value = metrics
                auditLogger.record(
                    AuditEventType.VOICE_TRANSCRIPT_PARTIAL,
                    mapOf("partial_count" to metrics.partialCount.toString(), "transcript_state" to "PARTIAL"),
                )
            }
            is TranscriptEvent.Revised -> {
                markFirstTranscript()
                lastPartialAtNanos = System.nanoTime()
                val text = combineTranscript(event.text)
                transcriptGate.revised(text)
                _state.value = AgenticVoiceState.Listening(text)
                val metrics = _transcriptValidation.value.copy(revisedCount = _transcriptValidation.value.revisedCount + 1)
                _transcriptValidation.value = metrics
                auditLogger.record(
                    AuditEventType.VOICE_TRANSCRIPT_REVISED,
                    mapOf("revised_count" to metrics.revisedCount.toString(), "transcript_state" to "REVISED"),
                )
            }
            TranscriptEvent.MicStarted,
            TranscriptEvent.SpeechStarted,
            -> Unit
            TranscriptEvent.EndOfSpeech -> endOfSpeechAtNanos = System.nanoTime()
            is TranscriptEvent.Committed -> {
                markFirstTranscript()
                finalResultAtNanos = System.nanoTime()
                val text = combineTranscript(event.text)
                transcriptAccumulated = text
                if (originalTranscript == null) originalTranscript = text
                transcriptGate.commit(text)
                _state.value = AgenticVoiceState.TranscriptReview(
                    originalTranscript = originalTranscript ?: text,
                    transcript = text,
                )
                val metrics = _transcriptValidation.value.copy(
                    committedCount = _transcriptValidation.value.committedCount + 1,
                    originalTranscript = originalTranscript ?: text,
                )
                _transcriptValidation.value = metrics
                auditLogger.record(
                    AuditEventType.VOICE_TRANSCRIPT_COMMITTED,
                    mapOf("committed_count" to metrics.committedCount.toString(), "transcript_state" to "REVIEW"),
                )
                auditLogger.record(
                    AuditEventType.VOICE_STAGE,
                    mapOf(
                        "stage" to "VOICE_COMMITTED",
                        "duration_ms" to elapsedMs(startedAtNanos, System.nanoTime()).toString(),
                    ),
                )
            }
            is TranscriptEvent.Failed -> _state.value = AgenticVoiceState.Error(
                transcript = currentTranscript(),
                message = event.reason,
            )
        }
    }

    private suspend fun publishStreamEvent(
        runId: String?,
        type: AgentStreamEventType,
        payload: Map<String, String> = emptyMap(),
    ) {
        if (runId == null) return
        try {
            streamingRuntime.emit(runId, type, payload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalStateException) {
            // A terminal event may already have been emitted by the query path.
        }
    }

    private suspend fun publishStreamEvent(runId: String, event: TranscriptEvent) {
        when (event) {
            TranscriptEvent.MicStarted -> publishStreamEvent(
                runId,
                AgentStreamEventType.SPEECH,
                mapOf("phase" to "MIC_STARTED"),
            )
            TranscriptEvent.SpeechStarted -> publishStreamEvent(
                runId,
                AgentStreamEventType.SPEECH,
                mapOf("phase" to "SPEECH_STARTED"),
            )
            TranscriptEvent.EndOfSpeech -> publishStreamEvent(
                runId,
                AgentStreamEventType.STATE_CHANGED,
                mapOf("state" to "END_OF_SPEECH"),
            )
            is TranscriptEvent.Partial -> publishStreamEvent(
                runId,
                AgentStreamEventType.TRANSCRIPT_PARTIAL,
                mapOf("text" to event.text.take(2_048), "kind" to "PARTIAL"),
            )
            is TranscriptEvent.Revised -> publishStreamEvent(
                runId,
                AgentStreamEventType.TRANSCRIPT_PARTIAL,
                mapOf("text" to event.text.take(2_048), "kind" to "REVISED"),
            )
            is TranscriptEvent.Committed -> publishStreamEvent(
                runId,
                AgentStreamEventType.TRANSCRIPT_COMMITTED,
                mapOf("text" to event.text.take(2_048), "source" to "speech"),
            )
            is TranscriptEvent.Failed -> {
                try {
                    streamingRuntime.close(
                        runId,
                        AgentStreamEventType.FAILED,
                        mapOf("reason" to event.reason.take(512)),
                    )
                } catch (_: IllegalStateException) {
                    // A query may have closed the same run during cancellation.
                }
            }
        }
    }

    private suspend fun closeVoiceStream(
        type: AgentStreamEventType,
        payload: Map<String, String> = emptyMap(),
    ) {
        val runId = voiceStreamRunId ?: return
        try {
            streamingRuntime.close(runId, type, payload)
        } catch (_: IllegalStateException) {
            // Terminal emission is idempotent from the UI's perspective.
        }
    }

    private suspend fun completeVoiceStream(payload: Map<String, String> = emptyMap()) {
        val runId = voiceStreamRunId ?: return
        try {
            streamingRuntime.emit(runId, AgentStreamEventType.A2UI_UPDATED, payload)
            streamingRuntime.close(runId, AgentStreamEventType.COMPLETED)
        } catch (_: IllegalStateException) {
            // The coordinator may already have completed this stream.
        }
    }

    private fun currentTranscript(): String = when (val current = _state.value) {
        is AgenticVoiceState.Listening -> current.transcript
        is AgenticVoiceState.TranscriptReview -> current.transcript
        is AgenticVoiceState.Understanding -> current.transcript
        is AgenticVoiceState.Navigation -> current.transcript
        is AgenticVoiceState.Result -> current.transcript
        is AgenticVoiceState.CustomerBalanceResult -> current.transcript
        is AgenticVoiceState.CustomerTimelineResult -> current.transcript
        is AgenticVoiceState.ReadListResult -> current.transcript
        is AgenticVoiceState.IntelligenceResult -> current.transcript
        is AgenticVoiceState.EntityChoice -> current.transcript
        is AgenticVoiceState.ActionPreview -> current.transcript
        is AgenticVoiceState.ActionCompleted -> current.transcript
        is AgenticVoiceState.Unsupported -> current.transcript
        is AgenticVoiceState.Error -> current.transcript
        AgenticVoiceState.Idle,
        AgenticVoiceState.Cancelled,
        -> ""
    }

    private fun combineTranscript(newText: String): String =
        listOf(transcriptAccumulated, newText.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")

    private fun metrics(response: AgentA2uiResponse) = AgenticVoiceMetrics(
        ttfpMs = firstTranscriptAtNanos?.let { elapsedMs(startedAtNanos, it) },
        voiceFinalMs = finalResultAtNanos?.let { elapsedMs(startedAtNanos, it) }
            ?: elapsedMs(startedAtNanos, System.nanoTime()),
        fastRouterMs = response.fastRouterMs,
        fastRouterHit = response.fastRouterHit,
        commandRouterMs = response.commandRouterMs,
        commandRouterHit = response.commandRouterHit,
        intentMs = response.intentLatencyMs,
        customerResolutionMs = (response as? AgentA2uiResponse.ActionPreview)
            ?.preview?.diagnostics?.customerResolutionMs
            ?: (response as? AgentA2uiResponse.CustomerBalanceReady)?.customerResolutionMs
            ?: (response as? AgentA2uiResponse.CustomerTimelineReady)?.customerResolutionMs,
        productResolutionMs = (response as? AgentA2uiResponse.ActionPreview)
            ?.preview?.diagnostics?.productResolutionMs,
        capabilityMs = response.capabilityLatencyMs,
        a2uiMs = response.a2uiLatencyMs,
        totalToCardMs = elapsedMs(startedAtNanos, System.nanoTime()),
        firstPartialMs = firstTranscriptAtNanos?.let { elapsedMs(startedAtNanos, it) },
        lastPartialMs = lastPartialAtNanos?.let { elapsedMs(startedAtNanos, it) },
        endOfSpeechMs = endOfSpeechAtNanos?.let { elapsedMs(startedAtNanos, it) },
        finalResultMs = finalResultAtNanos?.let { elapsedMs(startedAtNanos, it) },
    )

    private fun elapsedMs(start: Long, end: Long): Long =
        ((end - start).coerceAtLeast(0L) / 1_000_000L)

    companion object {
        const val VOICE_TIMEOUT_MS = 15_000L
        const val FAST_QUERY_TIMEOUT_MS = 3_000L
        /** Keeps the local interpretation path bounded; never waits indefinitely. */
        const val AGENT_QUERY_TIMEOUT_MS = 45_000L
        const val CONFIRMATION_TIMEOUT_MS = 5_000L
        const val UNDO_TIMEOUT_MS = 5_000L
    }
}
