package com.tino.app.domain.language

import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class BusinessMemoryKind {
    ENTITY_ALIAS,
    SUPPLIER_DELIVERY_PREFERENCE,
    WORKFLOW_PREFERENCE,
}

enum class MemoryLifecycle {
    CANDIDATE,
    LEARNED,
    TRUSTED,
    DEMOTED,
    REMOVED,
}

enum class MemoryProvenanceType {
    USER_CORRECTION,
    USER_CONFIRMATION,
    USER_CONTRADICTION,
    SYSTEM_DEMOTION,
    USER_REMOVAL,
}

data class MemoryProvenance(
    val type: MemoryProvenanceType,
    val sourceInteractionId: String? = null,
    val occurredAtEpochMs: Long,
)

data class MemoryConfidence(val value: Double) {
    init { require(value in 0.0..1.0) { "confidence deve estar entre 0 e 1" } }
}

data class MemoryCandidate(
    val id: String = UUID.randomUUID().toString(),
    val scopeKey: String,
    val memoryKey: String,
    val value: String,
    val kind: BusinessMemoryKind,
    val confidence: MemoryConfidence = MemoryConfidence(0.5),
    val provenance: MemoryProvenance,
)

data class BusinessMemoryRecord(
    val id: String,
    val scopeKey: String,
    val memoryKey: String,
    val value: String,
    val kind: BusinessMemoryKind,
    val lifecycle: MemoryLifecycle,
    val confidence: MemoryConfidence,
    val supportCount: Int,
    val contradictionCount: Int,
    val provenance: List<MemoryProvenance>,
    val sourceEventIds: List<String>,
    val updatedAtEpochMs: Long,
    val demotionReason: String? = null,
)

sealed interface MemoryPolicyDecision {
    data object Allowed : MemoryPolicyDecision
    data class Rejected(val reason: String) : MemoryPolicyDecision
}

interface BusinessMemoryPolicy {
    fun evaluate(candidate: MemoryCandidate): MemoryPolicyDecision
}

/** Only durable preferences/interpretations may enter Business Memory. */
class DefaultBusinessMemoryPolicy @Inject constructor() : BusinessMemoryPolicy {
    private val forbiddenFactTerms = setOf(
        "saldo", "deve", "devendo", "receber", "recebido", "estoque", "quantidade",
        "preco", "preço", "pix", "dinheiro", "venda", "fiado", "pagamento", "total", "balance", "valor", "amount",
    )

    override fun evaluate(candidate: MemoryCandidate): MemoryPolicyDecision {
        if (candidate.scopeKey.isBlank() || candidate.memoryKey.isBlank() || candidate.value.isBlank()) {
            return MemoryPolicyDecision.Rejected("memória sem escopo, chave ou valor")
        }
        if (candidate.memoryKey.length > 120 || candidate.value.length > 240) {
            return MemoryPolicyDecision.Rejected("memória excede o tamanho permitido")
        }
        if (candidate.kind !in BusinessMemoryKind.entries) {
            return MemoryPolicyDecision.Rejected("tipo de memória não permitido")
        }
        val searchable = LanguageNormalizer.normalize("${candidate.memoryKey} ${candidate.value}")
        if (forbiddenFactTerms.any { term -> searchable.split(' ').contains(term) }) {
            return MemoryPolicyDecision.Rejected("fato transacional deve continuar vindo do Room")
        }
        return MemoryPolicyDecision.Allowed
    }
}

interface BusinessMemoryStorePort {
    suspend fun find(scopeKey: String, memoryKey: String, value: String): BusinessMemoryRecord?
    suspend fun findByKey(scopeKey: String, memoryKey: String): List<BusinessMemoryRecord>
    suspend fun upsert(record: BusinessMemoryRecord)
    suspend fun list(scopeKey: String): List<BusinessMemoryRecord>
}

class InMemoryBusinessMemoryStore : BusinessMemoryStorePort {
    private val values = linkedMapOf<String, BusinessMemoryRecord>()
    override suspend fun find(scopeKey: String, memoryKey: String, value: String) =
        values.values.firstOrNull { it.scopeKey == scopeKey && it.memoryKey == memoryKey && it.value == value }
    override suspend fun findByKey(scopeKey: String, memoryKey: String) =
        values.values.filter { it.scopeKey == scopeKey && it.memoryKey == memoryKey }
    override suspend fun upsert(record: BusinessMemoryRecord) { values[record.id] = record }
    override suspend fun list(scopeKey: String) = values.values.filter { it.scopeKey == scopeKey }
}

interface BusinessMemoryPort {
    suspend fun record(candidate: MemoryCandidate): Result<BusinessMemoryRecord>
    suspend fun resolve(scopeKey: String, memoryKey: String): BusinessMemoryRecord?
    suspend fun demote(scopeKey: String, memoryKey: String, reason: String): List<BusinessMemoryRecord>
    suspend fun remove(scopeKey: String, memoryKey: String, value: String? = null): List<BusinessMemoryRecord>
    suspend fun list(scopeKey: String): List<BusinessMemoryRecord>
}

/** Application policy around the storage port; no Room or ADK dependency. */
@Singleton
class GovernedBusinessMemory @Inject constructor(
    private val store: BusinessMemoryStorePort,
    private val policy: BusinessMemoryPolicy = DefaultBusinessMemoryPolicy(),
    private val clock: Clock = Clock.systemUTC(),
) : BusinessMemoryPort {
    override suspend fun record(candidate: MemoryCandidate): Result<BusinessMemoryRecord> {
        when (val decision = policy.evaluate(candidate)) {
            MemoryPolicyDecision.Allowed -> Unit
            is MemoryPolicyDecision.Rejected -> return Result.failure(IllegalArgumentException(decision.reason))
        }
        val now = clock.millis()
        val normalizedKey = LanguageNormalizer.normalize(candidate.memoryKey)
        val normalizedValue = LanguageNormalizer.normalize(candidate.value)
        if (candidate.provenance.type == MemoryProvenanceType.USER_CONTRADICTION) {
            store.findByKey(candidate.scopeKey, normalizedKey)
                .filter { LanguageNormalizer.normalize(it.value) != normalizedValue }
                .forEach { store.upsert(it.demote("contradição recebida", now)) }
        }
        val existing = store.find(candidate.scopeKey, normalizedKey, candidate.value.trim())
        val support = (existing?.supportCount ?: 0) + 1
        val record = BusinessMemoryRecord(
            id = existing?.id ?: candidate.id,
            scopeKey = candidate.scopeKey,
            memoryKey = normalizedKey,
            value = candidate.value.trim(),
            kind = candidate.kind,
            lifecycle = lifecycleFor(support),
            confidence = MemoryConfidence(maxOf(existing?.confidence?.value ?: 0.0, candidate.confidence.value)),
            supportCount = support,
            contradictionCount = existing?.contradictionCount ?: 0,
            provenance = (existing?.provenance.orEmpty() + candidate.provenance).takeLast(20),
            sourceEventIds = (existing?.sourceEventIds.orEmpty() + candidate.id).takeLast(20),
            updatedAtEpochMs = now,
        )
        store.upsert(record)
        return Result.success(record)
    }

    override suspend fun resolve(scopeKey: String, memoryKey: String): BusinessMemoryRecord? =
        store.findByKey(scopeKey, LanguageNormalizer.normalize(memoryKey))
            .filter { it.lifecycle == MemoryLifecycle.LEARNED || it.lifecycle == MemoryLifecycle.TRUSTED }
            .maxWithOrNull(compareBy<BusinessMemoryRecord> { it.supportCount }.thenBy { it.updatedAtEpochMs })

    override suspend fun demote(scopeKey: String, memoryKey: String, reason: String): List<BusinessMemoryRecord> {
        val now = clock.millis()
        val records = store.findByKey(scopeKey, LanguageNormalizer.normalize(memoryKey))
        records.filter { it.lifecycle == MemoryLifecycle.LEARNED || it.lifecycle == MemoryLifecycle.TRUSTED }
            .forEach { store.upsert(it.demote(reason, now)) }
        return records.map { if (it.lifecycle == MemoryLifecycle.LEARNED || it.lifecycle == MemoryLifecycle.TRUSTED) it.demote(reason, now) else it }
    }

    override suspend fun remove(scopeKey: String, memoryKey: String, value: String?): List<BusinessMemoryRecord> {
        val now = clock.millis()
        val records = store.findByKey(scopeKey, LanguageNormalizer.normalize(memoryKey))
            .filter { value == null || LanguageNormalizer.normalize(it.value) == LanguageNormalizer.normalize(value) }
        records.forEach { store.upsert(it.copy(lifecycle = MemoryLifecycle.REMOVED, provenance = it.provenance + MemoryProvenance(MemoryProvenanceType.USER_REMOVAL, occurredAtEpochMs = now), updatedAtEpochMs = now)) }
        return records
    }

    override suspend fun list(scopeKey: String): List<BusinessMemoryRecord> = store.list(scopeKey)

    private fun lifecycleFor(support: Int): MemoryLifecycle = when {
        support >= 3 -> MemoryLifecycle.TRUSTED
        support >= 2 -> MemoryLifecycle.LEARNED
        else -> MemoryLifecycle.CANDIDATE
    }

    private fun BusinessMemoryRecord.demote(reason: String, now: Long) = copy(
        lifecycle = MemoryLifecycle.DEMOTED,
        contradictionCount = contradictionCount + 1,
        provenance = provenance + MemoryProvenance(MemoryProvenanceType.SYSTEM_DEMOTION, occurredAtEpochMs = now),
        updatedAtEpochMs = now,
        demotionReason = reason,
    )
}
