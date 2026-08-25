package com.tino.app.core.database

import com.tino.app.domain.agent.AgentActivityEntry
import com.tino.app.domain.agent.AgentActivityRepository
import com.tino.app.domain.agent.AgentActivitySource
import com.tino.app.domain.agent.AgentActivityStatus
import com.tino.app.domain.agent.AgentActivitySummary
import com.tino.app.domain.agent.AgentUndoEligibility
import com.tino.app.domain.agent.AgentUndoPolicy
import com.tino.app.domain.agent.AgentUndoState
import com.tino.app.domain.agent.TinoCapabilityId
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomAgentActivityRepository @Inject constructor(
    private val dao: AgentActivityDao,
) : AgentActivityRepository {
    override suspend fun all(): List<AgentActivityEntry> = dao.all().map(::toDomain)

    override suspend fun findById(id: String): AgentActivityEntry? = dao.findById(id)?.let(::toDomain)

    override suspend fun findByOperationId(operationId: String): AgentActivityEntry? =
        dao.findByOperationId(operationId)?.let(::toDomain)

    override suspend fun upsert(entry: AgentActivityEntry) {
        dao.upsert(entry.toEntity())
    }

    private fun AgentActivityEntry.toEntity() = AgentActivityEntity(
        id = id,
        capability = capability.name,
        operationId = operationId,
        occurredAt = occurredAtEpochMs,
        source = source.name,
        summary = summary,
        summaryKind = summaryData?.kind(),
        summaryPayloadJson = summaryData?.payload()?.toString(),
        undoPolicy = undo?.policy?.name,
        compensatingCapability = undo?.compensatingCapability?.name,
        undoDeadline = undo?.deadlineEpochMs,
        undoState = undoState.name,
        status = status.name,
        compensatesActivityId = compensatesActivityId,
    )

    private fun toDomain(entity: AgentActivityEntity): AgentActivityEntry {
        val undo = entity.undoPolicy?.let { policy ->
            AgentUndoEligibility(
                policy = AgentUndoPolicy.valueOf(policy),
                compensatingCapability = entity.compensatingCapability
                    ?.let(TinoCapabilityId::valueOf)
                    ?: error("Atividade persistida sem capability compensatória."),
                deadlineEpochMs = entity.undoDeadline,
            )
        }
        return AgentActivityEntry(
            id = entity.id,
            occurredAtEpochMs = entity.occurredAt,
            capability = TinoCapabilityId.valueOf(entity.capability),
            summary = entity.summary,
            source = AgentActivitySource.valueOf(entity.source),
            operationId = entity.operationId,
            undo = undo,
            undoState = AgentUndoState.valueOf(entity.undoState),
            compensatesActivityId = entity.compensatesActivityId,
            summaryData = decodeSummary(entity.summaryKind, entity.summaryPayloadJson),
            status = AgentActivityStatus.valueOf(entity.status),
        )
    }

    private fun AgentActivitySummary.kind(): String = when (this) {
        is AgentActivitySummary.CreditPayment -> "CREDIT_PAYMENT"
        is AgentActivitySummary.StockEntry -> "STOCK_ENTRY"
        is AgentActivitySummary.PriceChange -> "PRICE_CHANGE"
        is AgentActivitySummary.Generic -> "GENERIC"
    }

    private fun AgentActivitySummary.payload(): JSONObject = when (this) {
        is AgentActivitySummary.CreditPayment -> JSONObject()
            .put("customer_name", customerName)
            .put("amount_cents", amountCents)
            .put("payment_method", paymentMethod)
        is AgentActivitySummary.StockEntry -> JSONObject()
            .put("product_name", productName)
            .put("quantity", quantity)
        is AgentActivitySummary.PriceChange -> JSONObject()
            .put("product_name", productName)
            .put("old_price_cents", oldPriceCents)
            .put("new_price_cents", newPriceCents)
        is AgentActivitySummary.Generic -> JSONObject().put("value", value)
    }

    private fun decodeSummary(kind: String?, payloadJson: String?): AgentActivitySummary? {
        if (kind == null || payloadJson == null) return null
        val payload = JSONObject(payloadJson)
        return when (kind) {
            "CREDIT_PAYMENT" -> AgentActivitySummary.CreditPayment(
                customerName = payload.getString("customer_name"),
                amountCents = payload.getLong("amount_cents"),
                paymentMethod = payload.getString("payment_method"),
            )
            "STOCK_ENTRY" -> AgentActivitySummary.StockEntry(
                productName = payload.getString("product_name"),
                quantity = payload.getLong("quantity"),
            )
            "PRICE_CHANGE" -> AgentActivitySummary.PriceChange(
                productName = payload.getString("product_name"),
                oldPriceCents = payload.getLong("old_price_cents"),
                newPriceCents = payload.getLong("new_price_cents"),
            )
            "GENERIC" -> AgentActivitySummary.Generic(payload.getString("value"))
            else -> null
        }
    }
}
