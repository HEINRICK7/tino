package com.tino.app.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tino.app.domain.voice.VoiceContext
import com.tino.app.domain.voice.VoiceExtraction
import com.tino.app.domain.voice.VoiceInputPort
import com.tino.app.domain.voice.VoiceInputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ContextualVoiceState {
    data object Idle : ContextualVoiceState
    data class Listening(val context: VoiceContext, val transcript: String = "") : ContextualVoiceState
    data class Understanding(val context: VoiceContext, val transcript: String) : ContextualVoiceState
    data class Extracted(val value: VoiceExtraction) : ContextualVoiceState
    data class NeedsCorrection(
        val value: VoiceExtraction,
        val message: String,
    ) : ContextualVoiceState
    data class Unavailable(val context: VoiceContext, val message: String) : ContextualVoiceState
    data class Error(val context: VoiceContext, val message: String) : ContextualVoiceState
}

@HiltViewModel
class ContextualVoiceViewModel @Inject constructor(
    private val voiceInput: VoiceInputPort,
) : ViewModel() {
    private val _state = MutableStateFlow<ContextualVoiceState>(ContextualVoiceState.Idle)
    val state: StateFlow<ContextualVoiceState> = _state.asStateFlow()
    private var job: Job? = null

    fun listen(context: VoiceContext) {
        if (job?.isActive == true) return
        _state.value = ContextualVoiceState.Listening(context)
        job = viewModelScope.launch {
            when (val result = voiceInput.listen(
                context = context,
                onTranscript = { transcript ->
                    _state.value = ContextualVoiceState.Listening(context, transcript)
                },
                onCommitted = { transcript ->
                    _state.value = ContextualVoiceState.Understanding(context, transcript)
                },
            )) {
                is VoiceInputResult.Extracted -> _state.value = ContextualVoiceState.Extracted(result.value)
                is VoiceInputResult.NeedsCorrection -> _state.value = ContextualVoiceState.NeedsCorrection(
                    value = result.value,
                    message = result.message,
                )
                is VoiceInputResult.Unavailable -> _state.value = ContextualVoiceState.Unavailable(context, result.reason)
                is VoiceInputResult.Failed -> _state.value = ContextualVoiceState.Error(context, result.reason)
            }
        }
    }

    fun stop() {
        if (job?.isActive != true) {
            _state.value = ContextualVoiceState.Idle
            return
        }
        val current = _state.value as? ContextualVoiceState.Listening
        if (current != null) {
            _state.value = ContextualVoiceState.Understanding(current.context, current.transcript)
        }
        viewModelScope.launch {
            voiceInput.stop()
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
