package com.tino.app.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tino.app.core.speech.LiveTranscriberPort
import com.tino.app.core.speech.TranscriptEvent
import com.tino.app.domain.voice.ToolPreview
import com.tino.app.domain.voice.VoiceCommandCoordinator
import com.tino.app.domain.voice.VoiceCommandState
import com.tino.app.interfaceadapter.a2ui.A2uiMessage
import com.tino.app.interfaceadapter.a2ui.EntityChoiceA2uiMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data object Cancelled : VoiceUiState
    data object Listening : VoiceUiState
    data class Transcript(val text: String, val committed: Boolean) : VoiceUiState
    data class Understanding(val text: String) : VoiceUiState
    data class Answer(val title: String, val message: String) : VoiceUiState
    data class Clarification(
        val message: String,
        val entityChoice: A2uiMessage? = null,
    ) : VoiceUiState
    data class ConfirmationNeeded(val message: String) : VoiceUiState
    data class Preview(val preview: ToolPreview) : VoiceUiState
    data class Unavailable(val message: String) : VoiceUiState
    data class Error(val message: String) : VoiceUiState
    data class Completed(val message: String) : VoiceUiState
}

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val transcriber: LiveTranscriberPort,
    private val coordinator: VoiceCommandCoordinator,
    private val entityChoiceMapper: EntityChoiceA2uiMapper,
) : ViewModel() {
    private val _state = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var sessionJob: Job? = null

    fun start() {
        startSession(preserveClarification = false)
    }

    fun retryClarification() {
        startSession(preserveClarification = true)
    }

    fun confirmByVoice() {
        startSession(preserveClarification = true)
    }

    private fun startSession(preserveClarification: Boolean) {
        if (sessionJob?.isActive == true) return
        if (!preserveClarification) coordinator.cancel()
        _state.value = VoiceUiState.Listening
        sessionJob = viewModelScope.launch {
            runCatching {
                transcriber.start().collect(::handle)
            }.onFailure { error ->
                _state.value = VoiceUiState.Error(error.message ?: "Não foi possível iniciar a voz.")
            }
        }
    }

    fun stop() {
        if (sessionJob?.isActive != true) {
            _state.value = VoiceUiState.Idle
            return
        }
        val transcript = (_state.value as? VoiceUiState.Transcript)?.text.orEmpty()
        _state.value = VoiceUiState.Understanding(transcript)
        viewModelScope.launch {
            runCatching { transcriber.stop() }
        }
    }

    fun submitText(text: String) {
        val committedText = text.trim()
        if (committedText.isBlank()) return
        sessionJob?.cancel()
        _state.value = VoiceUiState.Understanding(committedText)
        viewModelScope.launch {
            when (val result = coordinator.accept(TranscriptEvent.Committed(committedText))) {
                VoiceCommandState.Cancelled -> _state.value = VoiceUiState.Cancelled
                is VoiceCommandState.ConfirmationNeeded -> _state.value = VoiceUiState.ConfirmationNeeded(result.message)
                is VoiceCommandState.AnswerReady -> _state.value = VoiceUiState.Answer(result.result.title, result.result.message)
                is VoiceCommandState.Clarification -> _state.value = result.toUiState()
                is VoiceCommandState.PreviewReady -> _state.value = VoiceUiState.Preview(result.preview)
                is VoiceCommandState.Ignored -> _state.value = VoiceUiState.Error(result.reason)
                else -> _state.value = VoiceUiState.Error("Não foi possível preparar esse comando.")
            }
        }
    }

    fun confirm() {
        viewModelScope.launch {
            runCatching { coordinator.confirm() }
                .onSuccess { result ->
                    _state.value = when (result) {
                        VoiceCommandState.Cancelled -> VoiceUiState.Cancelled
                        is VoiceCommandState.ConfirmationNeeded -> VoiceUiState.ConfirmationNeeded(result.message)
                        is VoiceCommandState.AnswerReady -> VoiceUiState.Answer(result.result.title, result.result.message)
                        is VoiceCommandState.Clarification -> result.toUiState()
                        is VoiceCommandState.Completed -> VoiceUiState.Completed("A operação foi salva neste aparelho.")
                        is VoiceCommandState.Ignored -> VoiceUiState.Error(result.reason)
                        else -> VoiceUiState.Error("Não há operação aguardando confirmação.")
                    }
                }
                .onFailure { error ->
                    _state.value = VoiceUiState.Error(error.message ?: "Não foi possível concluir o comando.")
                }
        }
    }

    fun cancel() {
        coordinator.cancel()
        _state.value = VoiceUiState.Idle
    }

    override fun onCleared() {
        sessionJob?.cancel()
        super.onCleared()
    }

    private suspend fun handle(event: TranscriptEvent) {
        when (event) {
            TranscriptEvent.MicStarted,
            TranscriptEvent.SpeechStarted,
            TranscriptEvent.EndOfSpeech,
            -> Unit
            is TranscriptEvent.Partial -> _state.value = VoiceUiState.Transcript(event.text, committed = false)
            is TranscriptEvent.Revised -> _state.value = VoiceUiState.Transcript(event.text, committed = false)
            is TranscriptEvent.Failed -> _state.value = VoiceUiState.Unavailable(event.reason)
            is TranscriptEvent.Committed -> {
                _state.value = VoiceUiState.Understanding(event.text)
                when (val result = coordinator.accept(event)) {
                    VoiceCommandState.Cancelled -> _state.value = VoiceUiState.Cancelled
                    is VoiceCommandState.ConfirmationNeeded -> _state.value = VoiceUiState.ConfirmationNeeded(result.message)
                    is VoiceCommandState.AnswerReady -> _state.value = VoiceUiState.Answer(result.result.title, result.result.message)
                    is VoiceCommandState.Clarification -> _state.value = result.toUiState()
                    is VoiceCommandState.PreviewReady -> _state.value = VoiceUiState.Preview(result.preview)
                    is VoiceCommandState.Ignored -> _state.value = VoiceUiState.Error(result.reason)
                    else -> _state.value = VoiceUiState.Error("Não foi possível preparar esse comando.")
                }
            }
        }
    }

    private fun VoiceCommandState.Clarification.toUiState(): VoiceUiState.Clarification =
        VoiceUiState.Clarification(
            message = message,
            entityChoice = options.takeIf { it.isNotEmpty() }?.let {
                entityChoiceMapper.map(entityType ?: "entity", it)
            },
        )
}
