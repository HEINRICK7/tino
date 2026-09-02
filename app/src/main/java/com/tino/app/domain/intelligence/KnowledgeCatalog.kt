package com.tino.app.domain.intelligence

/** A reviewed knowledge unit. It contains no transactional business facts. */
data class ApprovedKnowledgeEntry(
    val id: String,
    val collection: String,
    val phrases: List<String>,
    val keywords: List<String>,
    val answer: String,
    val sourceRef: String? = null,
)

data class ApprovedKnowledgeCatalog(
    val version: String,
    val entries: List<ApprovedKnowledgeEntry>,
    val activatedAtEpochMs: Long = 0L,
)

data class KnowledgeCatalogValidation(
    val errors: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** Rejects malformed or ambiguous catalogs before they can become active. */
object ApprovedKnowledgeCatalogValidator {
    fun validate(catalog: ApprovedKnowledgeCatalog): KnowledgeCatalogValidation {
        val errors = buildList {
            if (catalog.version.isBlank()) add("A versão do catálogo não pode ser vazia.")
            if (catalog.entries.isEmpty()) add("O catálogo precisa conter ao menos uma entrada.")

            val duplicateIds = catalog.entries
                .groupingBy { it.collection to it.id }
                .eachCount()
                .filterValues { it > 1 }
                .keys
            duplicateIds.forEach { (collection, id) ->
                add("Entrada duplicada: $collection/$id.")
            }

            catalog.entries.forEach { entry ->
                if (entry.id.isBlank()) add("Entrada sem id.")
                if (entry.collection.isBlank()) add("Entrada ${entry.id} sem coleção.")
                if (entry.answer.isBlank()) add("Entrada ${entry.id} sem resposta aprovada.")
                if (entry.phrases.isEmpty() && entry.keywords.isEmpty()) {
                    add("Entrada ${entry.id} sem termos de busca.")
                }
                if (entry.phrases.any { it.isBlank() } || entry.keywords.any { it.isBlank() }) {
                    add("Entrada ${entry.id} contém termo de busca vazio.")
                }
                if (entry.sourceRef?.isBlank() == true) {
                    add("Entrada ${entry.id} contém uma fonte vazia.")
                }
            }
        }
        return KnowledgeCatalogValidation(errors)
    }
}

enum class KnowledgeCatalogUpdateStatus { ACTIVATED, REJECTED, ROLLED_BACK }

data class KnowledgeCatalogUpdateResult(
    val status: KnowledgeCatalogUpdateStatus,
    val activeVersion: String,
    val errors: List<String> = emptyList(),
)

interface ApprovedKnowledgeCatalogPort {
    suspend fun current(): ApprovedKnowledgeCatalog

    suspend fun activate(candidate: ApprovedKnowledgeCatalog): KnowledgeCatalogUpdateResult

    suspend fun rollback(): KnowledgeCatalogUpdateResult
}

data class KnowledgeRetrievalMetrics(
    val totalQueries: Long,
    val answeredQueries: Long,
    val unavailableQueries: Long,
    val totalLatencyMs: Long,
) {
    val averageLatencyMs: Long
        get() = if (totalQueries == 0L) 0L else totalLatencyMs / totalQueries
}

interface KnowledgeRetrievalMetricsPort {
    fun record(answered: Boolean, latencyMs: Long)

    fun snapshot(): KnowledgeRetrievalMetrics
}

class InMemoryKnowledgeRetrievalMetrics : KnowledgeRetrievalMetricsPort {
    private val lock = Any()
    private var totalQueries = 0L
    private var answeredQueries = 0L
    private var unavailableQueries = 0L
    private var totalLatencyMs = 0L

    override fun record(answered: Boolean, latencyMs: Long) = synchronized(lock) {
        totalQueries++
        if (answered) answeredQueries++ else unavailableQueries++
        totalLatencyMs += latencyMs.coerceAtLeast(0L)
    }

    override fun snapshot(): KnowledgeRetrievalMetrics = synchronized(lock) {
        KnowledgeRetrievalMetrics(
            totalQueries = totalQueries,
            answeredQueries = answeredQueries,
            unavailableQueries = unavailableQueries,
            totalLatencyMs = totalLatencyMs,
        )
    }
}

/**
 * Versioned runtime boundary for approved content. The active and previous
 * versions live together so a bad update can be reversed atomically. Durable
 * remote ingestion is intentionally outside this adapter until its approval,
 * authentication and persistence contract exists.
 */
class VersionedApprovedKnowledgeCatalog(
    initial: ApprovedKnowledgeCatalog,
) : ApprovedKnowledgeCatalogPort {
    private val lock = Any()
    private var active = initial
    private var previous: ApprovedKnowledgeCatalog? = null

    init {
        val validation = ApprovedKnowledgeCatalogValidator.validate(initial)
        require(validation.isValid) { "Catálogo inicial inválido: ${validation.errors}" }
    }

    override suspend fun current(): ApprovedKnowledgeCatalog = synchronized(lock) { active }

    override suspend fun activate(candidate: ApprovedKnowledgeCatalog): KnowledgeCatalogUpdateResult {
        val validation = ApprovedKnowledgeCatalogValidator.validate(candidate)
        if (!validation.isValid) {
            return KnowledgeCatalogUpdateResult(
                status = KnowledgeCatalogUpdateStatus.REJECTED,
                activeVersion = current().version,
                errors = validation.errors,
            )
        }
        return synchronized(lock) {
            if (candidate.version == active.version) {
                KnowledgeCatalogUpdateResult(
                    status = KnowledgeCatalogUpdateStatus.REJECTED,
                    activeVersion = active.version,
                    errors = listOf("A versão ${candidate.version} já está ativa."),
                )
            } else {
                previous = active
                active = candidate.copy(activatedAtEpochMs = System.currentTimeMillis())
                KnowledgeCatalogUpdateResult(
                    status = KnowledgeCatalogUpdateStatus.ACTIVATED,
                    activeVersion = active.version,
                )
            }
        }
    }

    override suspend fun rollback(): KnowledgeCatalogUpdateResult = synchronized(lock) {
        val prior = previous ?: return@synchronized KnowledgeCatalogUpdateResult(
            status = KnowledgeCatalogUpdateStatus.REJECTED,
            activeVersion = active.version,
            errors = listOf("Não há uma versão anterior para reverter."),
        )
        previous = active
        active = prior.copy(activatedAtEpochMs = System.currentTimeMillis())
        KnowledgeCatalogUpdateResult(
            status = KnowledgeCatalogUpdateStatus.ROLLED_BACK,
            activeVersion = active.version,
        )
    }
}

object BuiltInApprovedKnowledgeCatalog {
    val current = ApprovedKnowledgeCatalog(
        version = "v1",
        entries = listOf(
            ApprovedKnowledgeEntry(
                id = "cfop",
                collection = "fiscal-glossary",
                phrases = listOf("cfop", "codigo fiscal"),
                keywords = listOf("cfop", "fiscal", "nota"),
                answer = "CFOP é o código fiscal que descreve a natureza de uma operação. O TINO pode explicar o termo, mas não decide sozinho tributação ou enquadramento: confirme o código da nota com a orientação fiscal do seu comércio.",
            ),
            ApprovedKnowledgeEntry(
                id = "fiado",
                collection = "tino-help",
                phrases = listOf("fiado", "caderneta"),
                keywords = listOf("fiado", "caderneta", "cliente"),
                answer = "Fiado é uma venda registrada para pagamento posterior. O saldo e os pagamentos continuam vindo dos lançamentos do comércio; o TINO não considera uma hipótese como pagamento confirmado.",
            ),
            ApprovedKnowledgeEntry(
                id = "pix",
                collection = "tino-help",
                phrases = listOf("pix"),
                keywords = listOf("pix", "pagamento", "recebimento"),
                answer = "Pix é um meio de recebimento. Um Pix só é relacionado a uma conta quando os dados permitem; se houver mais de uma possibilidade, o TINO pede identificação e não dá baixa sozinho.",
            ),
            ApprovedKnowledgeEntry(
                id = "estoque",
                collection = "tino-help",
                phrases = listOf("estoque", "reposicao"),
                keywords = listOf("estoque", "produto", "repor", "reposicao"),
                answer = "Estoque é a quantidade registrada de cada produto. O TINO pode mostrar saídas, cobertura estimada e sinais de reposição, sempre separando fato atual de previsão.",
            ),
        ),
    )
}
