package com.tino.app.domain.voice

import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.agent.TinoCapabilityRegistry
import com.tino.app.domain.agent.HumanGateDecision
import com.tino.app.domain.agent.HumanGatePolicy
import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.core.observability.NoOpAuditLogger
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class OperationRisk {
    READ_ONLY,
    LOW_RISK_MUTATION,
    FINANCIAL_MUTATION,
    DESTRUCTIVE_MUTATION,
}

internal fun mutationTokenHash(value: String): String = MessageDigest
    .getInstance("SHA-256")
    .digest("token|$value".toByteArray())
    .joinToString("") { "%02x".format(it) }

data class ProposedOperation(
    val operationId: String,
    val capabilityId: TinoCapabilityId,
    val arguments: Map<String, String>,
    val risk: OperationRisk,
    val requiresConfirmation: Boolean,
    val idempotencyKey: String,
    val previewFingerprint: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

data class MutationConfirmation(
    val operationId: String,
    val confirmationToken: String,
)

data class PreparedMutation(
    val operation: ProposedOperation,
    val confirmation: MutationConfirmation,
)

sealed interface MutationAuthorization {
    data class Allowed(val operation: ProposedOperation) : MutationAuthorization
    data class Denied(val reason: String) : MutationAuthorization
}

interface MutationSafetyPort {
    suspend fun prepare(
        call: ToolCall,
        preview: ToolPreview,
        operationId: String? = null,
    ): PreparedMutation

    suspend fun authorize(
        call: ToolCall,
        confirmation: MutationConfirmation?,
        currentPreview: ToolPreview,
    ): MutationAuthorization

    suspend fun commit(operation: ProposedOperation)

    suspend fun release(operation: ProposedOperation)

    suspend fun cancel(confirmation: MutationConfirmation?)
}

enum class MutationOperationStatus {
    PENDING,
    EXECUTING,
    COMMITTED,
}

data class StoredMutationOperation(
    val prepared: PreparedMutation,
    val confirmationTokenHash: String,
    val status: MutationOperationStatus,
)

interface MutationOperationStore {
    suspend fun save(prepared: PreparedMutation)
    suspend fun find(operationId: String): StoredMutationOperation?
    suspend fun reserve(operationId: String, idempotencyKey: String): Boolean
    suspend fun markCommitted(operationId: String, idempotencyKey: String)
    suspend fun release(operationId: String, idempotencyKey: String)
    suspend fun delete(operationId: String)
}

class InMemoryMutationOperationStore : MutationOperationStore {
    private val operations = mutableMapOf<String, StoredMutationOperation>()

    override suspend fun save(prepared: PreparedMutation) {
        synchronized(this) {
            check(prepared.operation.operationId !in operations) {
                "Operação já existe. Gere uma nova prévia."
            }
            operations[prepared.operation.operationId] = StoredMutationOperation(
                prepared = prepared,
                confirmationTokenHash = mutationTokenHash(prepared.confirmation.confirmationToken),
                status = MutationOperationStatus.PENDING,
            )
        }
    }

    override suspend fun find(operationId: String): StoredMutationOperation? = synchronized(this) {
        operations[operationId]
    }

    override suspend fun reserve(operationId: String, idempotencyKey: String): Boolean = synchronized(this) {
        val current = operations[operationId] ?: return@synchronized false
        if (current.prepared.operation.idempotencyKey != idempotencyKey || current.status != MutationOperationStatus.PENDING) {
            return@synchronized false
        }
        operations[operationId] = current.copy(status = MutationOperationStatus.EXECUTING)
        true
    }

    override suspend fun markCommitted(operationId: String, idempotencyKey: String) {
        synchronized(this) {
            val current = operations[operationId] ?: error("Operação inexistente.")
            if (current.prepared.operation.idempotencyKey != idempotencyKey) {
                error("Chave de idempotência não corresponde à operação.")
            }
            check(current.status == MutationOperationStatus.EXECUTING) {
                "Operação inexistente, repetida ou não reservada."
            }
            operations[operationId] = current.copy(status = MutationOperationStatus.COMMITTED)
        }
    }

    override suspend fun release(operationId: String, idempotencyKey: String) {
        synchronized(this) {
            val current = operations[operationId] ?: return
            if (current.prepared.operation.idempotencyKey == idempotencyKey && current.status == MutationOperationStatus.EXECUTING) {
                operations[operationId] = current.copy(status = MutationOperationStatus.PENDING)
            }
        }
    }

    override suspend fun delete(operationId: String) {
        synchronized(this) {
            if (operations[operationId]?.status == MutationOperationStatus.PENDING) {
                operations.remove(operationId)
            }
        }
    }
}

/**
 * Application policy for every commerce mutation. It is deliberately independent
 * of Room and Compose; the dispatcher is the outer adapter that invokes it.
 */
@Singleton
class MutationSafetyCoordinator @Inject constructor(
    private val clock: Clock,
    private val store: MutationOperationStore = InMemoryMutationOperationStore(),
    private val auditLogger: AuditLogger = NoOpAuditLogger,
) : MutationSafetyPort {
    override suspend fun prepare(call: ToolCall, preview: ToolPreview, operationId: String?): PreparedMutation {
        val capability = capabilityFor(call)
        check(HumanGatePolicy.evaluate(capability) == HumanGateDecision.CONFIRM) {
            "Mutation sem confirmação HITL não pode ser preparada."
        }
        val now = clock.millis()
        val stableOperationId = operationId ?: "op-${UUID.randomUUID()}"
        val operation = ProposedOperation(
            operationId = stableOperationId,
            capabilityId = capability,
            arguments = call.arguments.toSortedMap(),
            risk = riskFor(capability),
            requiresConfirmation = true,
            idempotencyKey = digest("$stableOperationId|${callFingerprint(call)}"),
            previewFingerprint = previewFingerprint(preview),
            createdAtEpochMs = now,
            expiresAtEpochMs = now + PREVIEW_TTL_MS,
        )
        val prepared = PreparedMutation(
            operation = operation,
            confirmation = MutationConfirmation(stableOperationId, UUID.randomUUID().toString()),
        )
        store.save(prepared)
        audit(
            status = "PREPARED",
            capability = capability,
            risk = operation.risk,
        )
        return prepared
    }

    override suspend fun authorize(
        call: ToolCall,
        confirmation: MutationConfirmation?,
        currentPreview: ToolPreview,
    ): MutationAuthorization {
        if (confirmation == null) return denied("UNKNOWN", "MISSING_CONFIRMATION")
        val stored = store.find(confirmation.operationId)
            ?: return denied("UNKNOWN", "NOT_FOUND")
        val prepared = stored.prepared
        if (stored.status != MutationOperationStatus.PENDING) {
            return denied(prepared.operation.capabilityId, "REPLAY_BLOCKED")
        }
        val now = clock.millis()
        if (now >= prepared.operation.expiresAtEpochMs) {
            store.delete(confirmation.operationId)
            return denied(prepared.operation.capabilityId, "EXPIRED")
        }
        if (stored.confirmationTokenHash != mutationTokenHash(confirmation.confirmationToken)) {
            return denied(prepared.operation.capabilityId, "INVALID_TOKEN")
        }
        if (prepared.operation.previewFingerprint != previewFingerprint(currentPreview)) {
            store.delete(confirmation.operationId)
            return denied(prepared.operation.capabilityId, "STALE_PREVIEW")
        }
        if (prepared.operation.capabilityId != capabilityFor(call)) {
            return denied(prepared.operation.capabilityId, "CAPABILITY_MISMATCH")
        }
        if (prepared.operation.arguments != call.arguments.toSortedMap()) {
            return denied(prepared.operation.capabilityId, "ARGUMENTS_CHANGED")
        }
        if (!store.reserve(prepared.operation.operationId, prepared.operation.idempotencyKey)) {
            return denied(prepared.operation.capabilityId, "RESERVE_CONFLICT")
        }
        audit("AUTHORIZED", prepared.operation.capabilityId, prepared.operation.risk)
        return MutationAuthorization.Allowed(prepared.operation)
    }

    override suspend fun commit(operation: ProposedOperation) {
        store.markCommitted(operation.operationId, operation.idempotencyKey)
        audit("COMMITTED", operation.capabilityId, operation.risk)
    }

    override suspend fun release(operation: ProposedOperation) {
        store.release(operation.operationId, operation.idempotencyKey)
        audit("RELEASED", operation.capabilityId, operation.risk)
    }

    override suspend fun cancel(confirmation: MutationConfirmation?) {
        confirmation ?: return
        val stored = store.find(confirmation.operationId) ?: return
        store.delete(confirmation.operationId)
        audit("CANCELLED", stored.prepared.operation.capabilityId, stored.prepared.operation.risk)
    }

    private fun denied(capability: String, reason: String): MutationAuthorization.Denied {
        audit("DENIED", capability, null, reason)
        return MutationAuthorization.Denied(
            when (reason) {
                "MISSING_CONFIRMATION" -> "Confirmação ausente."
                "NOT_FOUND" -> "Operação inexistente, expirada ou já concluída."
                "REPLAY_BLOCKED", "RESERVE_CONFLICT" -> "Operação repetida bloqueada por idempotência."
                "EXPIRED" -> "A confirmação expirou. Gere uma nova prévia."
                "INVALID_TOKEN" -> "Token de confirmação inválido."
                "STALE_PREVIEW" -> "Os dados mudaram desde a prévia. Gere uma nova confirmação."
                "CAPABILITY_MISMATCH" -> "A confirmação não corresponde à operação proposta."
                "ARGUMENTS_CHANGED" -> "Os argumentos mudaram desde a prévia."
                else -> "A confirmação foi negada."
            },
        )
    }

    private fun denied(capability: TinoCapabilityId, reason: String): MutationAuthorization.Denied =
        denied(capability.name, reason)

    private fun audit(
        status: String,
        capability: String,
        risk: OperationRisk?,
        reason: String? = null,
    ) {
        auditLogger.record(
            AuditEventType.CONFIRMATION,
            buildMap {
                put("status", status)
                put("capability", capability)
                risk?.let { put("risk", it.name) }
                reason?.let { put("reason_code", it) }
            },
        )
    }

    private fun audit(status: String, capability: TinoCapabilityId, risk: OperationRisk) =
        audit(status, capability.name, risk)

    private fun capabilityFor(call: ToolCall): TinoCapabilityId = when (call.name) {
        CommerceToolName.REGISTER_CREDIT_PAYMENT -> TinoCapabilityId.RECEIVE_CREDIT_PAYMENT
        CommerceToolName.CORRECT_CREDIT_PAYMENT -> TinoCapabilityId.REVERSE_CREDIT_PAYMENT
        CommerceToolName.ADD_CREDIT_ITEM,
        CommerceToolName.REGISTER_CREDIT_SALE,
        -> TinoCapabilityId.ADD_CREDIT_ITEM
        CommerceToolName.REGISTER_STOCK_RECEIPT -> TinoCapabilityId.REGISTER_STOCK_ENTRY
        CommerceToolName.CHANGE_PRODUCT_PRICE -> TinoCapabilityId.CHANGE_PRODUCT_PRICE
        CommerceToolName.REGISTER_SALE -> TinoCapabilityId.ADD_CREDIT
        CommerceToolName.PREPARE_PURCHASE -> TinoCapabilityId.REGISTER_STOCK_ENTRY
        else -> error("Ação sem capability de mutation: ${call.name}")
    }

    private fun riskFor(capability: TinoCapabilityId): OperationRisk = when {
        capability == TinoCapabilityId.RECEIVE_CREDIT_PAYMENT ||
            capability == TinoCapabilityId.REVERSE_CREDIT_PAYMENT -> OperationRisk.FINANCIAL_MUTATION
        capability == TinoCapabilityId.CHANGE_PRODUCT_PRICE ||
            capability == TinoCapabilityId.REGISTER_STOCK_ENTRY -> OperationRisk.LOW_RISK_MUTATION
        else -> TinoCapabilityRegistry.require(capability).let {
            if (it.type.name == "MUTATION") OperationRisk.LOW_RISK_MUTATION else OperationRisk.READ_ONLY
        }
    }

    private fun callFingerprint(call: ToolCall): String =
        "${call.name.name}|${call.arguments.toSortedMap()}"

    private fun previewFingerprint(preview: ToolPreview): String =
        digest("${preview.title}|${preview.detail}|${preview.confirmLabel}")

    private fun digest(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val PREVIEW_TTL_MS = 5 * 60 * 1_000L
    }
}

/** Input port used by non-Compose adapters to confirm a persisted operation. */
interface MutationConfirmationPort {
    suspend fun confirm(confirmation: MutationConfirmation): ToolExecutionResult
    suspend fun cancel(confirmation: MutationConfirmation): Boolean
}

/** Rehydrates the call from the durable proposal and delegates to the guarded executor. */
@Singleton
class MutationConfirmationService @Inject constructor(
    private val store: MutationOperationStore,
    private val toolExecutor: ToolExecutor,
    private val safety: MutationSafetyPort,
) : MutationConfirmationPort {
    override suspend fun confirm(confirmation: MutationConfirmation): ToolExecutionResult {
        val stored = store.find(confirmation.operationId)
            ?: error("Operação inexistente, expirada ou já concluída.")
        val call = ToolCall(
            name = stored.prepared.operation.toolName(),
            arguments = stored.prepared.operation.arguments,
        )
        return toolExecutor.confirm(call, confirmation)
    }

    override suspend fun cancel(confirmation: MutationConfirmation): Boolean {
        val exists = store.find(confirmation.operationId) != null
        safety.cancel(confirmation)
        return exists
    }

    private fun ProposedOperation.toolName(): CommerceToolName = when (capabilityId) {
        TinoCapabilityId.RECEIVE_CREDIT_PAYMENT -> CommerceToolName.REGISTER_CREDIT_PAYMENT
        TinoCapabilityId.REVERSE_CREDIT_PAYMENT -> CommerceToolName.CORRECT_CREDIT_PAYMENT
        TinoCapabilityId.ADD_CREDIT_ITEM -> CommerceToolName.ADD_CREDIT_ITEM
        TinoCapabilityId.REGISTER_STOCK_ENTRY -> CommerceToolName.REGISTER_STOCK_RECEIPT
        TinoCapabilityId.CHANGE_PRODUCT_PRICE -> CommerceToolName.CHANGE_PRODUCT_PRICE
        TinoCapabilityId.ADD_CREDIT -> CommerceToolName.REGISTER_SALE
        else -> error("Capability não representa uma mutation confirmável: $capabilityId")
    }
}
