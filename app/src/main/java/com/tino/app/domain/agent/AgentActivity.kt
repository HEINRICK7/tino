package com.tino.app.domain.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class AgentActivitySource { VOICE, TEXT, UI, AUTOMATION }

enum class AgentUndoState {
    NOT_UNDOABLE,
    AVAILABLE,
    REQUESTED,
    COMPLETED,
    EXPIRED,
    FAILED,
}

enum class AgentUndoPolicy { COMPENSATING_OPERATION }

enum class AgentActivityStatus { SUCCEEDED, FAILED }

/** Structured operational facts kept separately from the human-facing summary. */
sealed interface AgentActivitySummary {
    data class CreditPayment(
        val customerName: String,
        val amountCents: Long,
        val paymentMethod: String,
    ) : AgentActivitySummary

    data class StockEntry(
        val productName: String,
        val quantity: Long,
    ) : AgentActivitySummary

    data class PriceChange(
        val productName: String,
        val oldPriceCents: Long,
        val newPriceCents: Long,
    ) : AgentActivitySummary

    data class Generic(val value: String) : AgentActivitySummary
}

data class AgentUndoEligibility(
    val policy: AgentUndoPolicy,
    val compensatingCapability: TinoCapabilityId,
    val deadlineEpochMs: Long? = null,
)

data class AgentActivityEntry(
    val id: String = UUID.randomUUID().toString(),
    val occurredAtEpochMs: Long,
    val capability: TinoCapabilityId,
    val summary: String,
    val source: AgentActivitySource,
    val operationId: String?,
    val undo: AgentUndoEligibility? = null,
    val undoState: AgentUndoState = if (undo == null) {
        AgentUndoState.NOT_UNDOABLE
    } else {
        AgentUndoState.AVAILABLE
    },
    val compensatesActivityId: String? = null,
    val summaryData: AgentActivitySummary? = null,
    val status: AgentActivityStatus = AgentActivityStatus.SUCCEEDED,
)

interface AgentActivityRepository {
    suspend fun all(): List<AgentActivityEntry>
    suspend fun findById(id: String): AgentActivityEntry?
    suspend fun findByOperationId(operationId: String): AgentActivityEntry?
    suspend fun upsert(entry: AgentActivityEntry)
}

private class InMemoryAgentActivityRepository : AgentActivityRepository {
    private val entries = linkedMapOf<String, AgentActivityEntry>()

    override suspend fun all(): List<AgentActivityEntry> = entries.values
        .sortedWith(compareBy<AgentActivityEntry> { it.occurredAtEpochMs }.thenBy { it.id })

    override suspend fun findById(id: String): AgentActivityEntry? = entries[id]

    override suspend fun findByOperationId(operationId: String): AgentActivityEntry? = entries.values
        .firstOrNull { it.operationId == operationId }

    override suspend fun upsert(entry: AgentActivityEntry) {
        entries[entry.id] = entry
    }
}

/**
 * Operational ledger, intentionally separate from the conversational transcript.
 * The ledger owns state transitions; persistence is an adapter behind the domain
 * repository port. The UI never mutates an activity directly.
 */
@Singleton
class AgentActivityLedger @Inject constructor(
    private val repository: AgentActivityRepository,
) {
    constructor() : this(InMemoryAgentActivityRepository())

    private val _entries = MutableStateFlow<List<AgentActivityEntry>>(emptyList())
    val entries: StateFlow<List<AgentActivityEntry>> = _entries.asStateFlow()
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistenceJobs = mutableListOf<Job>()
    private val restoreJob = persistenceScope.launch {
        val restored = repository.all()
        synchronized(this@AgentActivityLedger) {
            _entries.value = (_entries.value + restored)
                .distinctBy { it.id }
                .sortedWith(compareBy<AgentActivityEntry> { it.occurredAtEpochMs }.thenBy { it.id })
        }
    }
    @Synchronized
    fun append(entry: AgentActivityEntry): AgentActivityEntry {
        val existing = entry.operationId?.let { operationId ->
            _entries.value.firstOrNull { it.operationId == operationId }
        }
        if (existing != null) return existing
        _entries.value = _entries.value + entry
        persist(entry)
        return entry
    }

    fun record(
        capability: TinoCapabilityId,
        summary: String,
        source: AgentActivitySource,
        operationId: String?,
        undo: AgentUndoEligibility? = null,
        summaryData: AgentActivitySummary? = null,
        compensatesActivityId: String? = null,
        occurredAtEpochMs: Long = System.currentTimeMillis(),
    ): AgentActivityEntry = append(
        AgentActivityEntry(
            occurredAtEpochMs = occurredAtEpochMs,
            capability = capability,
            summary = summary,
            source = source,
            operationId = operationId,
            undo = undo,
            summaryData = summaryData,
            compensatesActivityId = compensatesActivityId,
        ),
    )

    @Synchronized
    fun requestUndo(activityId: String, nowEpochMs: Long = System.currentTimeMillis()): AgentActivityEntry {
        val current = _entries.value.firstOrNull { it.id == activityId }
            ?: error("Atividade não encontrada.")
        check(current.undo != null) { "Esta operação não possui Undo." }
        if (current.undoState == AgentUndoState.REQUESTED) return current
        check(current.undoState == AgentUndoState.AVAILABLE) { "Esta operação não está disponível para Undo." }
        if (current.undo.deadlineEpochMs != null && nowEpochMs > current.undo.deadlineEpochMs) {
            update(current.copy(undoState = AgentUndoState.EXPIRED))
            error("O prazo para desfazer esta operação terminou.")
        }
        return update(current.copy(undoState = AgentUndoState.REQUESTED))
    }

    @Synchronized
    fun completeUndo(activityId: String): AgentActivityEntry {
        val current = _entries.value.firstOrNull { it.id == activityId }
            ?: error("Atividade não encontrada.")
        if (current.undoState == AgentUndoState.COMPLETED) return current
        check(current.undoState == AgentUndoState.REQUESTED) { "Undo não foi solicitado." }
        return update(current.copy(undoState = AgentUndoState.COMPLETED))
    }

    @Synchronized
    fun failUndo(activityId: String): AgentActivityEntry {
        val current = _entries.value.firstOrNull { it.id == activityId }
            ?: error("Atividade não encontrada.")
        return update(current.copy(undoState = AgentUndoState.FAILED))
    }

    fun latestUndoable(): AgentActivityEntry? = entries.value.lastOrNull {
        it.undoState == AgentUndoState.AVAILABLE && it.undo != null &&
            (it.undo.deadlineEpochMs == null || System.currentTimeMillis() <= it.undo.deadlineEpochMs)
    }

    fun latestSuccessfulMutation(): AgentActivityEntry? = entries.value.lastOrNull {
        it.status == AgentActivityStatus.SUCCEEDED && it.operationId != null &&
            it.compensatesActivityId == null && it.undoState != AgentUndoState.COMPLETED
    }

    /** Lets tests and lifecycle code wait until Room restore/write work is durable. */
    suspend fun awaitPersistence() {
        restoreJob.join()
        val jobs = synchronized(this) { persistenceJobs.toList() }
        jobs.forEach { it.join() }
    }

    private fun update(entry: AgentActivityEntry): AgentActivityEntry {
        _entries.value = _entries.value.map { if (it.id == entry.id) entry else it }
        persist(entry)
        return entry
    }

    private fun persist(entry: AgentActivityEntry) {
        val job = persistenceScope.launch { repository.upsert(entry) }
        synchronized(this) { persistenceJobs += job }
    }
}

data class AgentCompensationPlan(
    val activityId: String,
    val originalOperationId: String,
    val capability: TinoCapabilityId,
)

class AgentUndoPlanner @Inject constructor(
    private val ledger: AgentActivityLedger,
) {
    fun plan(activityId: String, nowEpochMs: Long = System.currentTimeMillis()): AgentCompensationPlan {
        val current = ledger.entries.value.firstOrNull { it.id == activityId }
            ?: error("Atividade não encontrada.")
        val requested = if (current.undoState == AgentUndoState.COMPLETED) {
            current
        } else {
            ledger.requestUndo(activityId, nowEpochMs)
        }
        val undo = requested.undo ?: error("A atividade não possui política de Undo.")
        val operationId = requested.operationId ?: error("A operação não possui referência para compensação.")
        return AgentCompensationPlan(
            activityId = requested.id,
            originalOperationId = operationId,
            capability = undo.compensatingCapability,
        )
    }

    fun markCompleted(plan: AgentCompensationPlan) {
        ledger.completeUndo(plan.activityId)
    }

    fun markFailed(plan: AgentCompensationPlan) {
        ledger.failUndo(plan.activityId)
    }
}
