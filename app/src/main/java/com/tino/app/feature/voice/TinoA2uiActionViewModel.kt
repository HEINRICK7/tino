package com.tino.app.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tino.app.interfaceadapter.a2ui.A2uiActionDispatchResult
import com.tino.app.interfaceadapter.a2ui.A2uiActionEvent
import com.tino.app.interfaceadapter.a2ui.A2uiActionRouter
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

sealed interface TinoA2uiActionState {
    data object Idle : TinoA2uiActionState
    data class Processing(val event: A2uiActionEvent) : TinoA2uiActionState
    data class Rejected(val event: A2uiActionEvent, val reason: String) : TinoA2uiActionState
    data class Completed(val result: A2uiActionDispatchResult) : TinoA2uiActionState
}

@HiltViewModel
class TinoA2uiActionViewModel @Inject constructor(
    private val router: A2uiActionRouter,
) : ViewModel() {
    private val _state = MutableStateFlow<TinoA2uiActionState>(TinoA2uiActionState.Idle)
    val state: StateFlow<TinoA2uiActionState> = _state.asStateFlow()

    fun dispatch(event: A2uiActionEvent, surface: A2uiSurfaceState) {
        viewModelScope.launch {
            _state.value = TinoA2uiActionState.Processing(event)
            try {
                when (val result = router.dispatch(event, surface)) {
                    is A2uiActionDispatchResult.Rejected -> _state.value = TinoA2uiActionState.Rejected(event, result.reason)
                    else -> _state.value = TinoA2uiActionState.Completed(result)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = TinoA2uiActionState.Rejected(
                    event,
                    error.message ?: "Não foi possível concluir a ação com segurança.",
                )
            }
        }
    }

    fun reset() {
        _state.value = TinoA2uiActionState.Idle
    }
}
