package com.tino.app.domain.catalog

import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

data class CatalogSyncResult(
    val status: CatalogSyncStatus,
    val total: Int,
    val accepted: Int,
    val rejected: Int,
    val created: Int,
    val updated: Int,
    val possiblyPartial: Boolean,
    val completedAt: Long,
    val errorMessage: String? = null,
)

@Singleton
class SyncCatalog @Inject constructor(
    private val api: CatalogApi,
    private val productStore: CatalogProductStore,
    private val stateStore: CatalogSyncStateStore,
) {
    private companion object {
        const val MAX_CATALOG_PRODUCTS = 10_000
    }

    private val mutex = Mutex()
    private val _diagnostics = MutableStateFlow<Map<String, CatalogSyncDiagnostics>>(emptyMap())

    fun observeDiagnostics(businessId: String): kotlinx.coroutines.flow.Flow<CatalogSyncDiagnostics?> =
        _diagnostics.map { it[businessId] }

    fun observe(businessId: String) = stateStore.observe(businessId)

    suspend operator fun invoke(
        businessId: String,
        query: String? = null,
        gtin: String? = null,
        limit: Int = 100,
    ): CatalogSyncResult = mutex.withLock {
        require(businessId.isNotBlank()) { "O negócio não está identificado." }
        require(limit in 1..100) { "O limite do catálogo deve estar entre 1 e 100." }
        val previous = stateStore.current(businessId)
        var logs = listOf(log("INÍCIO", "RUNNING", "Sincronização iniciada."))
        publishDiagnostics(
            CatalogSyncDiagnostics(businessId, CatalogSyncStatus.SYNCING, logs = logs),
        )
        stateStore.save(
            businessId,
            CatalogSyncState(
                status = CatalogSyncStatus.SYNCING,
                lastSuccessfulAt = previous?.lastSuccessfulAt,
            ),
        )
        try {
            logs += log("FONTE EXTERNA", "RUNNING", "Solicitando sincronização Doces & Sonhos.")
            publishDiagnostics(CatalogSyncDiagnostics(businessId, CatalogSyncStatus.SYNCING, logs = logs))
            val stockMode = api.syncExternalCatalog(businessId)
            logs += log(
                "FONTE EXTERNA",
                "OK",
                if (stockMode == CatalogStockMode.MADE_TO_ORDER) {
                    "Doces & Sonhos: produtos feitos sob demanda, sem controle de estoque."
                } else {
                    "Sincronização externa concluída."
                },
            )
            publishDiagnostics(CatalogSyncDiagnostics(businessId, CatalogSyncStatus.SYNCING, logs = logs))
            logs += log("CONSULTA", "RUNNING", "Buscando o catálogo em páginas de até $limit produto(s).")
            publishDiagnostics(CatalogSyncDiagnostics(businessId, CatalogSyncStatus.SYNCING, logs = logs))
            val remote = buildList {
                var offset = 0
                while (true) {
                    val page = api.listProducts(businessId, query, gtin, limit, offset)
                    addAll(page)
                    logs += log("CONSULTA", "OK", "Página ${offset / limit + 1}: ${page.size} produto(s). Total: $size.")
                    if (page.size < limit) break
                    check(size < MAX_CATALOG_PRODUCTS) {
                        "O catálogo excede o limite seguro de $MAX_CATALOG_PRODUCTS produtos."
                    }
                    offset += page.size
                }
            }
            logs += log("CONSULTA", "OK", "${remote.size} produto(s) retornado(s) pelo backend; consulta concluída.")
            publishDiagnostics(CatalogSyncDiagnostics(businessId, CatalogSyncStatus.SYNCING, total = remote.size, logs = logs))
            val valid = mutableListOf<CatalogProduct>()
            val validationFailures = mutableListOf<CatalogUpsertFailure>()
            remote.forEach { item ->
                runCatching { item.toCatalogProduct(stockMode) }
                    .onSuccess(valid::add)
                    .onFailure {
                        validationFailures += CatalogUpsertFailure(
                            item.productId,
                            it.message?.takeIf(String::isNotBlank) ?: "item inválido",
                        )
                    }
            }
            if (validationFailures.isNotEmpty()) {
                logs += log(
                    "VALIDAÇÃO",
                    "PARTIAL",
                    "${validationFailures.size} item(ns) rejeitado(s): ${summarizeFailures(validationFailures)}.",
                )
                publishDiagnostics(CatalogSyncDiagnostics(businessId, CatalogSyncStatus.SYNCING, total = remote.size, logs = logs))
            }
            logs += log("GRAVAÇÃO LOCAL", "RUNNING", "Aplicando ${valid.size} produto(s) no catálogo do aparelho.")
            publishDiagnostics(CatalogSyncDiagnostics(businessId, CatalogSyncStatus.SYNCING, total = remote.size, logs = logs))
            val stored = productStore.upsert(valid)
            val rejected = validationFailures.size + stored.failures.size
            val possiblyPartial = false
            val status = if (rejected > 0 || possiblyPartial) CatalogSyncStatus.PARTIAL else CatalogSyncStatus.SUCCESS
            val completedAt = System.currentTimeMillis()
            logs += log(
                "GRAVAÇÃO LOCAL",
                if (stored.failures.isEmpty()) "OK" else "PARTIAL",
                "${stored.created} criado(s), ${stored.updated} atualizado(s), ${stored.failures.size} falha(s)" +
                    stored.failures.takeIf { it.isNotEmpty() }?.let {
                        ": ${summarizeFailures(it)}"
                    }.orEmpty() + ".",
            )
            val result = CatalogSyncResult(
                status = status,
                total = remote.size,
                accepted = stored.created + stored.updated,
                rejected = rejected,
                created = stored.created,
                updated = stored.updated,
                possiblyPartial = possiblyPartial,
                completedAt = completedAt,
                errorMessage = if (rejected > 0) "Alguns itens não puderam ser aplicados." else null,
            )
            stateStore.save(
                businessId,
                CatalogSyncState(
                    status = status,
                    lastSuccessfulAt = if (result.accepted > 0 || remote.isEmpty()) completedAt else previous?.lastSuccessfulAt,
                    completedAt = completedAt,
                    total = result.total,
                    accepted = result.accepted,
                    rejected = result.rejected,
                    possiblyPartial = possiblyPartial,
                    errorMessage = result.errorMessage,
                ),
            )
            publishDiagnostics(
                CatalogSyncDiagnostics(
                    businessId = businessId,
                    status = status,
                    total = result.total,
                    accepted = result.accepted,
                    rejected = result.rejected,
                    possiblyPartial = possiblyPartial,
                    errorMessage = result.errorMessage,
                    logs = logs + log("FIM", status.name, "Operação concluída."),
                ),
            )
            result
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val message = sanitize(error)
            stateStore.save(
                businessId,
                CatalogSyncState(
                    status = CatalogSyncStatus.FAILED,
                    lastSuccessfulAt = previous?.lastSuccessfulAt,
                    completedAt = System.currentTimeMillis(),
                    errorMessage = message,
                ),
            )
            publishDiagnostics(
                CatalogSyncDiagnostics(
                    businessId = businessId,
                    status = CatalogSyncStatus.FAILED,
                    errorMessage = message,
                    logs = logs + log("FIM", "FAILED", message),
                ),
            )
            throw CatalogSyncException(message, error)
        }
    }

    private fun publishDiagnostics(value: CatalogSyncDiagnostics) {
        _diagnostics.value = _diagnostics.value + (value.businessId to value)
    }

    private fun summarizeFailures(failures: List<CatalogUpsertFailure>): String = failures
        .groupingBy { it.reason }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .joinToString(", ") { (reason, count) -> "$reason ($count)" }

    private fun log(step: String, status: String, detail: String) = CatalogSyncLogEntry(
        timestamp = System.currentTimeMillis(),
        step = step,
        status = status,
        detail = detail,
    )
}

class CatalogSyncException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

internal fun RemoteCatalogProduct.toCatalogProduct(stockMode: CatalogStockMode = CatalogStockMode.TRACKED): CatalogProduct = CatalogProduct(
    productId = productId.required("product_id"),
    name = name.required("name"),
    baseUnit = baseUnit.required("base_unit"),
    gtin = gtin?.trim()?.takeIf { it.isNotEmpty() },
    priceCents = price.toPriceCents(),
    stockTracked = stockMode == CatalogStockMode.TRACKED,
)

internal fun String?.required(field: String): String = this?.trim()?.takeIf { it.isNotEmpty() }
    ?: throw IllegalArgumentException("Campo inválido: $field")

internal fun String?.toPriceCents(): Long {
    val decimal = this?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
        runCatching { BigDecimal(value) }.getOrNull()
    } ?: throw IllegalArgumentException("Preço inválido")
    if (decimal.signum() < 0) throw IllegalArgumentException("Preço inválido")
    val cents = decimal.movePointRight(2)
    if (cents.stripTrailingZeros().scale() > 0) throw IllegalArgumentException("Preço inválido")
    return runCatching { cents.setScale(0, RoundingMode.UNNECESSARY).longValueExact() }
        .getOrElse { throw IllegalArgumentException("Preço inválido") }
}

private fun sanitize(error: Throwable): String = when (error) {
    is CatalogSyncException -> error.message ?: "Não foi possível atualizar o catálogo."
    else -> "Não foi possível atualizar o catálogo. Verifique sua conexão e tente novamente."
}
