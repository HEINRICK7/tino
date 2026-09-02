package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.agent.AgentActivityLedger
import com.tino.app.domain.agent.AgentActivitySource
import com.tino.app.domain.agent.AgentUndoEligibility
import com.tino.app.domain.agent.AgentUndoPlanner
import com.tino.app.domain.agent.AgentUndoPolicy
import com.tino.app.domain.agent.AgentUndoState
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.agent.TinoPresentationMode
import com.tino.app.domain.language.TinoIntent
import com.tino.app.domain.voice.ToolExecutionResult
import com.tino.app.domain.voice.ToolUndoMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A2uiIntentDrivenTest {
    @Test
    fun semanticRegistryIsAllowlistedAndIntentDriven() {
        val payment = A2uiSemanticComponentRegistry.forIntent(TinoIntent.RECEIVE_CREDIT_PAYMENT)
        val correction = A2uiSemanticComponentRegistry.forIntent(TinoIntent.CORRECTION)

        assertEquals(TinoA2UiComponentCatalog.PAYMENT_PREVIEW, payment)
        assertEquals(TinoA2UiComponentCatalog.ERROR_RECOVERY, correction)
        assertEquals(
            TinoA2UiComponentCatalog.ACTION_CONFIRMATION,
            A2uiSemanticComponentRegistry.forCapability(TinoCapabilityId.CREATE_CUSTOMER),
        )
        assertTrue(A2uiPresentationPolicy.isSafeComponent(payment))
        assertFalse(A2uiPresentationPolicy.isSafeComponent("execute_command"))
        assertEquals(TinoPresentationMode.BOTTOM_SHEET, A2uiPresentationPolicy.forIntent(TinoIntent.RECEIVE_CREDIT_PAYMENT))
        assertEquals(TinoPresentationMode.OVERLAY, A2uiPresentationPolicy.forIntent(TinoIntent.READ_FINANCIAL_SUMMARY))
    }

    @Test
    fun operationSuccessCarriesExplicitUndoMetadata() {
        val message = CommerceActionA2uiMapper().completed(
            ToolExecutionResult(
                message = "Pagamento recebido.",
                title = "Concluído",
                operationId = "payment-1",
                undo = ToolUndoMetadata("REVERSE_CREDIT_PAYMENT"),
            ),
            activityId = "activity-1",
        )

        val component = message.component as A2uiComponent.ActionConfirmation
        assertEquals(TinoA2UiComponentCatalog.OPERATION_SUCCESS, component.type)
        assertTrue(component.undoAvailable)
        assertEquals("payment-1", component.operationId)
        assertEquals("activity-1", component.activityId)

        val decoded = TinoA2UiJsonCodec.decode(TinoA2UiJsonCodec.encode(message))
        assertEquals(component, decoded.component)
    }

    @Test
    fun activityLedgerPlansCompensationWithoutDeletingOriginalOperation() {
        val ledger = AgentActivityLedger()
        val entry = ledger.record(
            capability = TinoCapabilityId.RECEIVE_CREDIT_PAYMENT,
            summary = "Pagamento recebido",
            source = AgentActivitySource.VOICE,
            operationId = "payment-1",
            undo = AgentUndoEligibility(
                policy = AgentUndoPolicy.COMPENSATING_OPERATION,
                compensatingCapability = TinoCapabilityId.REVERSE_CREDIT_PAYMENT,
            ),
            occurredAtEpochMs = 1_000L,
        )
        val planner = AgentUndoPlanner(ledger)

        val plan = planner.plan(entry.id, nowEpochMs = 2_000L)
        assertEquals("payment-1", plan.originalOperationId)
        assertEquals(AgentUndoState.REQUESTED, ledger.entries.value.single().undoState)

        planner.markCompleted(plan)

        assertEquals(AgentUndoState.COMPLETED, ledger.entries.value.single().undoState)
        assertEquals("payment-1", ledger.entries.value.single().operationId)
        assertEquals(null, ledger.latestUndoable())
    }

    @Test
    fun expiredUndoIsRejectedAndOperationRemainsAvailableForAudit() {
        val ledger = AgentActivityLedger()
        val entry = ledger.record(
            capability = TinoCapabilityId.RECEIVE_CREDIT_PAYMENT,
            summary = "Pagamento recebido",
            source = AgentActivitySource.TEXT,
            operationId = "payment-expired",
            undo = AgentUndoEligibility(
                policy = AgentUndoPolicy.COMPENSATING_OPERATION,
                compensatingCapability = TinoCapabilityId.REVERSE_CREDIT_PAYMENT,
                deadlineEpochMs = 10L,
            ),
            occurredAtEpochMs = 1L,
        )

        var rejected = false
        try {
            AgentUndoPlanner(ledger).plan(entry.id, nowEpochMs = 11L)
        } catch (_: IllegalStateException) {
            rejected = true
        }

        assertTrue(rejected)
        assertEquals(AgentUndoState.EXPIRED, ledger.entries.value.single().undoState)
        assertEquals("payment-expired", ledger.entries.value.single().operationId)
    }
}
