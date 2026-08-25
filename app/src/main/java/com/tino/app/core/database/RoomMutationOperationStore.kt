package com.tino.app.core.database

import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.voice.MutationConfirmation
import com.tino.app.domain.voice.MutationOperationStatus
import com.tino.app.domain.voice.MutationOperationStore
import com.tino.app.domain.voice.OperationRisk
import com.tino.app.domain.voice.PreparedMutation
import com.tino.app.domain.voice.ProposedOperation
import com.tino.app.domain.voice.StoredMutationOperation
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMutationOperationStore @Inject constructor(
    private val dao: MutationOperationDao,
) : MutationOperationStore {
    override suspend fun save(prepared: PreparedMutation) {
        val operation = prepared.operation
        dao.insert(
            MutationOperationEntity(
                operationId = operation.operationId,
                capabilityId = operation.capabilityId.name,
                argumentsJson = JSONObject(operation.arguments).toString(),
                risk = operation.risk.name,
                requiresConfirmation = operation.requiresConfirmation,
                idempotencyKey = operation.idempotencyKey,
                previewFingerprint = operation.previewFingerprint,
                confirmationTokenHash = tokenHash(prepared.confirmation.confirmationToken),
                createdAtEpochMs = operation.createdAtEpochMs,
                expiresAtEpochMs = operation.expiresAtEpochMs,
                status = MutationOperationStatus.PENDING.name,
            ),
        )
    }

    override suspend fun find(operationId: String): StoredMutationOperation? =
        dao.findById(operationId)?.let { entity ->
            StoredMutationOperation(
                prepared = PreparedMutation(
                    operation = ProposedOperation(
                        operationId = entity.operationId,
                        capabilityId = TinoCapabilityId.valueOf(entity.capabilityId),
                        arguments = entity.argumentsJson.toStringMap(),
                        risk = OperationRisk.valueOf(entity.risk),
                        requiresConfirmation = entity.requiresConfirmation,
                        idempotencyKey = entity.idempotencyKey,
                        previewFingerprint = entity.previewFingerprint,
                        createdAtEpochMs = entity.createdAtEpochMs,
                        expiresAtEpochMs = entity.expiresAtEpochMs,
                    ),
                    // The raw token never enters Room. Authorization compares its hash below.
                    confirmation = MutationConfirmation(entity.operationId, ""),
                ),
                confirmationTokenHash = entity.confirmationTokenHash,
                status = MutationOperationStatus.valueOf(entity.status),
            )
        }

    override suspend fun reserve(operationId: String, idempotencyKey: String): Boolean =
        dao.reserve(operationId, idempotencyKey) == 1

    override suspend fun markCommitted(operationId: String, idempotencyKey: String) {
        check(dao.markCommitted(operationId, idempotencyKey) == 1) {
            "Operação inexistente, repetida ou chave de idempotência inválida."
        }
    }

    override suspend fun release(operationId: String, idempotencyKey: String) {
        dao.release(operationId, idempotencyKey)
    }

    override suspend fun delete(operationId: String) {
        dao.deletePendingById(operationId)
    }

    private fun String.toStringMap(): Map<String, String> {
        val json = JSONObject(this)
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    private fun tokenHash(value: String): String = java.security.MessageDigest
        .getInstance("SHA-256")
        .digest("token|$value".toByteArray())
        .joinToString("") { "%02x".format(it) }
}
