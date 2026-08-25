package com.tino.app.domain.agent

import com.tino.app.domain.commerce.CommerceRepository
import javax.inject.Inject
import javax.inject.Singleton

data class AgentUndoResult(
    val plan: AgentCompensationPlan,
    val compensationOperationId: String,
    val compensationActivityId: String? = null,
)

/** Executes only explicitly supported compensations; it never deletes history. */
@Singleton
class AgentUndoService @Inject constructor(
    private val planner: AgentUndoPlanner,
    private val commerceRepository: CommerceRepository,
    private val activityLedger: AgentActivityLedger,
) {
    suspend fun undo(activityId: String, nowEpochMs: Long = System.currentTimeMillis()): AgentUndoResult {
        val plan = planner.plan(activityId, nowEpochMs)
        return runCatching {
            val compensationId = when (plan.capability) {
                TinoCapabilityId.REVERSE_CREDIT_PAYMENT ->
                    commerceRepository.reverseCreditPayment(plan.originalOperationId)
                else -> error("A compensação ${plan.capability} ainda não está disponível.")
            }
            planner.markCompleted(plan)
            val compensationActivity = activityLedger.record(
                capability = plan.capability,
                summary = "Operação desfeita",
                source = AgentActivitySource.UI,
                operationId = compensationId,
                compensatesActivityId = plan.activityId,
                summaryData = AgentActivitySummary.Generic("Operação desfeita"),
            )
            AgentUndoResult(plan, compensationId, compensationActivity.id)
        }.getOrElse { error ->
            planner.markFailed(plan)
            throw error
        }
    }
}
