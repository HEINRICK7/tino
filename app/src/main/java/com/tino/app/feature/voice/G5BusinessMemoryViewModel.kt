package com.tino.app.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tino.app.domain.language.BusinessMemoryKind
import com.tino.app.domain.language.BusinessMemoryPort
import com.tino.app.domain.language.BusinessMemoryRecord
import com.tino.app.domain.language.MemoryCandidate
import com.tino.app.domain.language.MemoryConfidence
import com.tino.app.domain.language.MemoryProvenance
import com.tino.app.domain.language.MemoryProvenanceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class G5BusinessMemoryState(
    val records: List<BusinessMemoryRecord> = emptyList(),
    val message: String = "Memória de negócio vazia.",
)

@HiltViewModel
class G5BusinessMemoryViewModel @Inject constructor(
    private val memory: BusinessMemoryPort,
) : ViewModel() {
    private val _state = MutableStateFlow(G5BusinessMemoryState())
    val state: StateFlow<G5BusinessMemoryState> = _state

    init { reload() }

    fun recordCorrection() = record(MemoryProvenanceType.USER_CORRECTION, "Café Maratá")

    fun confirm() = record(MemoryProvenanceType.USER_CONFIRMATION, "Café Maratá")

    fun contradict() = record(MemoryProvenanceType.USER_CONTRADICTION, "Café Maratá Tradicional")

    fun remove() {
        viewModelScope.launch {
            memory.remove(SCOPE, KEY)
            reloadNow("Memória removida e mantida como histórico não resolvível.")
        }
    }

    fun reload() {
        viewModelScope.launch { reloadNow("Memória restaurada do Room.") }
    }

    private fun record(type: MemoryProvenanceType, value: String) {
        viewModelScope.launch {
            val result = memory.record(
                MemoryCandidate(
                    scopeKey = SCOPE,
                    memoryKey = KEY,
                    value = value,
                    kind = BusinessMemoryKind.ENTITY_ALIAS,
                    confidence = MemoryConfidence(0.9),
                    provenance = MemoryProvenance(type, sourceInteractionId = "g5-device", occurredAtEpochMs = System.currentTimeMillis()),
                ),
            )
            reloadNow(result.exceptionOrNull()?.message ?: "Evidência registrada.")
        }
    }

    private suspend fun reloadNow(message: String) {
        _state.value = G5BusinessMemoryState(memory.list(SCOPE), message)
    }

    companion object {
        const val SCOPE = "default-store"
        const val KEY = "entity_alias:PRODUCT:maraca"
    }
}
