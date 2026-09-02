package com.tino.app.domain.commerce

import com.tino.app.core.database.CreditEntryEntity
import com.tino.app.core.database.CreditEntryType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Semantic event types used by the shared customer ledger. */
enum class SharedLedgerEventType {
    PURCHASE,
    PAYMENT,
    ADJUSTMENT,
    REVERSAL,
    DISPUTE,
    SETTLEMENT,
}

enum class LedgerActorType {
    MERCHANT,
    CUSTOMER,
    AGENT,
    SYSTEM,
}

enum class LedgerSourceType {
    VOICE,
    MANUAL_UI,
    PIX_MATCH,
    CUSTOMER_ACTION,
    AGENT_ACTION,
    IMPORT,
    SYSTEM,
}

/** Provenance explains how a ledger event entered the system. */
data class LedgerProvenance(
    val source: LedgerSourceType,
    val actor: LedgerActorType,
    val transcript: String? = null,
    val agentExecutionId: String? = null,
    val createdAtEpochMs: Long,
)

fun defaultMerchantProvenance(now: Long = System.currentTimeMillis()): LedgerProvenance =
    LedgerProvenance(
        source = LedgerSourceType.MANUAL_UI,
        actor = LedgerActorType.MERCHANT,
        createdAtEpochMs = now,
    )

/** Domain projection of a persisted credit entry. It never owns operational facts. */
data class SharedLedgerEvent(
    val id: String,
    val customerId: String,
    val type: SharedLedgerEventType,
    val signedAmountCents: Long,
    val occurredAtEpochMs: Long,
    val referenceId: String? = null,
    val reason: String? = null,
    val provenance: LedgerProvenance? = null,
    /** Payment method is a business fact, not provenance, and is kept for readable statements. */
    val paymentMethod: String? = null,
)

data class SharedLedgerProjection(
    val customerId: String,
    val balanceCents: Long,
    val events: List<SharedLedgerEvent>,
    val disputedEventIds: Set<String>,
) {
    val open: Boolean get() = balanceCents > 0L
}

/** Safe, customer-facing read model derived from the ledger projection. */
data class SharedLedgerStatementEntry(
    val id: String,
    val type: SharedLedgerEventType,
    val signedAmountCents: Long,
    val occurredAtEpochMs: Long,
    val reason: String?,
    val source: LedgerSourceType?,
    val paymentMethod: String? = null,
)

data class SharedLedgerStatement(
    val customerId: String,
    val customerName: String,
    val balanceCents: Long,
    val entries: List<SharedLedgerStatementEntry>,
) {
    val open: Boolean get() = balanceCents > 0L
}

/** Formats ledger facts without exposing transcript or internal execution identifiers. */
object SharedLedgerStatementFormatter {
    fun text(statement: SharedLedgerStatement, zone: ZoneId): String = buildString {
        append("Extrato de ")
        append(statement.customerName)
        append('\n')
        append("Saldo em aberto: ")
        append(formatCents(statement.balanceCents))
        append('\n')
        if (statement.entries.isEmpty()) {
            append("Nenhum lançamento registrado.")
            return@buildString
        }
        append('\n')
        append("Lançamentos:\n")
        statement.entries.forEachIndexed { index, entry ->
            if (index > 0) append('\n')
            append(formatDate(entry.occurredAtEpochMs, zone))
            append(" · ")
            append(label(entry.type))
            append(" · ")
            append(formatSignedCents(entry.signedAmountCents))
            entry.reason?.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(it)
            }
        }
    }

    private fun label(type: SharedLedgerEventType): String = when (type) {
        SharedLedgerEventType.PURCHASE -> "Compra fiada"
        SharedLedgerEventType.PAYMENT -> "Pagamento"
        SharedLedgerEventType.ADJUSTMENT -> "Ajuste"
        SharedLedgerEventType.REVERSAL -> "Reversão"
        SharedLedgerEventType.DISPUTE -> "Contestação"
        SharedLedgerEventType.SETTLEMENT -> "Quitação"
    }

    private fun formatSignedCents(cents: Long): String {
        val sign = if (cents >= 0L) "+" else "-"
        return sign + " " + formatCents(kotlin.math.abs(cents))
    }

    private fun formatCents(cents: Long): String {
        val whole = cents / 100
        val decimals = (cents % 100).toString().padStart(2, '0')
        return "R$ " + whole + "," + decimals
    }

    private fun formatDate(epochMs: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT)
            .format(Instant.ofEpochMilli(epochMs).atZone(zone))
}

/**
 * Deterministic projection rules for the shared ledger.
 * The event list is authoritative; balance and status are derived values.
 */
object SharedLedgerProjector {
    fun project(customerId: String, events: List<SharedLedgerEvent>): SharedLedgerProjection {
        val ordered = events
            .filter { it.customerId == customerId }
            .sortedWith(compareBy<SharedLedgerEvent> { it.occurredAtEpochMs }.thenBy { it.id })
        return SharedLedgerProjection(
            customerId = customerId,
            balanceCents = ordered.sumOf { it.signedAmountCents },
            events = ordered,
            disputedEventIds = ordered
                .filter { it.type == SharedLedgerEventType.DISPUTE }
                .mapNotNull { it.referenceId }
                .toSet(),
        )
    }

    fun fromCreditEntry(entry: CreditEntryEntity): SharedLedgerEvent = SharedLedgerEvent(
        id = entry.id,
        customerId = entry.customerId,
        type = entry.ledgerType?.let { runCatching { SharedLedgerEventType.valueOf(it) }.getOrNull() }
            ?: when (entry.type) {
                CreditEntryType.SALE -> SharedLedgerEventType.PURCHASE
                CreditEntryType.PAYMENT -> SharedLedgerEventType.PAYMENT
            },
        signedAmountCents = entry.amountCents,
        occurredAtEpochMs = entry.occurredAt,
        referenceId = entry.referenceId,
        reason = entry.reason,
        provenance = entry.provenance?.let { LedgerProvenanceCodec.decode(it) },
        paymentMethod = entry.paymentMethod,
    )
}

/** Small stable JSON codec kept at the persistence boundary. */
object LedgerProvenanceCodec {
    fun encode(value: LedgerProvenance?): String? = value?.let {
        buildString {
            append("{\"source\":")
            append(quote(it.source.name))
            append(",\"actor\":")
            append(quote(it.actor.name))
            append(",\"createdAt\":")
            append(it.createdAtEpochMs)
            it.transcript?.let { transcript ->
                append(",\"transcript\":")
                append(quote(transcript))
            }
            it.agentExecutionId?.let { executionId ->
                append(",\"agentExecutionId\":")
                append(quote(executionId))
            }
            append("}")
        }
    }

    fun decode(value: String): LedgerProvenance? = runCatching {
        LedgerProvenance(
            source = LedgerSourceType.valueOf(requiredString(value, "source")),
            actor = LedgerActorType.valueOf(requiredString(value, "actor")),
            transcript = optionalString(value, "transcript"),
            agentExecutionId = optionalString(value, "agentExecutionId"),
            createdAtEpochMs = Regex("\"createdAt\"\\s*:\\s*(-?\\d+)")
                .find(value)?.groupValues?.get(1)?.toLong()
                ?: error("createdAt ausente"),
        )
    }.getOrNull()

    private fun requiredString(json: String, key: String): String =
        optionalString(json, key) ?: error("Campo ausente: " + key)

    private fun optionalString(json: String, key: String): String? {
        val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val encoded = pattern.find(json)?.groupValues?.get(1) ?: return null
        return buildString {
            var index = 0
            while (index < encoded.length) {
                val character = encoded[index++]
                if (character != '\\' || index >= encoded.length) {
                    append(character)
                    continue
                }
                when (val escaped = encoded[index++]) {
                    '\\' -> append('\\')
                    '"' -> append('"')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(escaped)
                }
            }
        }
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
}
