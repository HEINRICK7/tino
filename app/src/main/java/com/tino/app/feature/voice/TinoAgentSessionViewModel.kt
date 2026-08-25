package com.tino.app.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tino.app.domain.agent.AgentProgressRuntime
import com.tino.app.domain.agent.ScreenAgentContext
import com.tino.app.domain.agent.ScreenContextRegistry
import com.tino.app.domain.agent.SharedAgentState
import com.tino.app.domain.agent.TinoAgentSession
import com.tino.app.domain.agent.TinoPresenceResolver
import com.tino.app.domain.agent.TinoPresenceState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TinoAgentSessionViewModel @Inject constructor(
    val session: TinoAgentSession,
    private val screenContextRegistry: ScreenContextRegistry,
    private val progressRuntime: AgentProgressRuntime,
) : ViewModel() {
    /** Read-only adapter for Compose/A2UI; mutations remain on the domain session. */
    val sharedState: SharedAgentState = session
    val presence: StateFlow<TinoPresenceState> = combine(
        session.snapshot,
        progressRuntime.snapshot,
    ) { snapshot, progress ->
        TinoPresenceResolver.resolve(snapshot, progress)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TinoPresenceState())

    fun enterScreen(context: ScreenAgentContext) {
        screenContextRegistry.register(context)
        session.enterScreen(context)
    }
}
