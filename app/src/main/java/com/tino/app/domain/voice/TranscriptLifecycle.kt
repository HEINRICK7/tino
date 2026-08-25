package com.tino.app.domain.voice

/** Lifecycle for a voice turn. Only COMMITTED text may enter the agent. */
enum class TranscriptLifecycle {
    IDLE,
    LISTENING,
    PARTIAL,
    REVISING,
    FINALIZING,
    REVIEW,
    EDITING,
    COMMITTED,
    PROCESSING,
    ERROR,
}

data class TranscriptSnapshot(
    val lifecycle: TranscriptLifecycle,
    val text: String = "",
) {
    val canSubmit: Boolean
        get() = lifecycle == TranscriptLifecycle.COMMITTED && text.isNotBlank()
}

/**
 * Pure commit gate. Revised/partial recognition is display-only; it can never
 * be submitted to the Agent Runtime until a committed result is received.
 */
class TranscriptCommitGate {
    var snapshot: TranscriptSnapshot = TranscriptSnapshot(TranscriptLifecycle.IDLE)
        private set

    fun reset() {
        snapshot = TranscriptSnapshot(TranscriptLifecycle.LISTENING)
    }

    fun partial(text: String): TranscriptSnapshot {
        snapshot = TranscriptSnapshot(TranscriptLifecycle.PARTIAL, text.trim())
        return snapshot
    }

    fun revised(text: String): TranscriptSnapshot {
        snapshot = TranscriptSnapshot(TranscriptLifecycle.REVISING, text.trim())
        return snapshot
    }

    fun finalizing(): TranscriptSnapshot {
        snapshot = snapshot.copy(lifecycle = TranscriptLifecycle.FINALIZING)
        return snapshot
    }

    fun commit(text: String): TranscriptSnapshot {
        snapshot = TranscriptSnapshot(TranscriptLifecycle.COMMITTED, text.trim())
        return snapshot
    }

    fun review(text: String): TranscriptSnapshot {
        snapshot = TranscriptSnapshot(TranscriptLifecycle.REVIEW, text.trim())
        return snapshot
    }

    fun edit(text: String): TranscriptSnapshot {
        snapshot = TranscriptSnapshot(TranscriptLifecycle.EDITING, text.trim())
        return snapshot
    }

    fun processing(): TranscriptSnapshot {
        check(snapshot.text.isNotBlank()) { "Não é possível processar uma fala vazia." }
        snapshot = snapshot.copy(lifecycle = TranscriptLifecycle.PROCESSING)
        return snapshot
    }
}
