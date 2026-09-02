package com.tino.app.domain.commerce

import com.tino.app.core.database.CreditEntryEntity
import com.tino.app.core.database.CreditEntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class SharedLedgerTest {
    @Test
    fun projectionUsesEventsAsTruthAndKeepsDisputeOutOfBalance() {
        val events = listOf(
            SharedLedgerEvent("purchase", "customer-1", SharedLedgerEventType.PURCHASE, 2_400, 1),
            SharedLedgerEvent("payment", "customer-1", SharedLedgerEventType.PAYMENT, -500, 2),
            SharedLedgerEvent(
                id = "dispute",
                customerId = "customer-1",
                type = SharedLedgerEventType.DISPUTE,
                signedAmountCents = 0,
                occurredAtEpochMs = 3,
                referenceId = "purchase",
                reason = "Cliente informou que levou apenas uma unidade",
            ),
        )

        val projection = SharedLedgerProjector.project("customer-1", events)

        assertEquals(1_900, projection.balanceCents)
        assertEquals(listOf("purchase", "payment", "dispute"), projection.events.map { it.id })
        assertEquals(setOf("purchase"), projection.disputedEventIds)
        assertTrue(projection.open)
    }

    @Test
    fun legacyCreditEntriesRemainReadableAsPurchaseAndPaymentEvents() {
        val purchase = SharedLedgerProjector.fromCreditEntry(
            CreditEntryEntity("sale", "customer-1", 1_000, CreditEntryType.SALE, null, 1, "credit"),
        )
        val payment = SharedLedgerProjector.fromCreditEntry(
            CreditEntryEntity("payment", "customer-1", -300, CreditEntryType.PAYMENT, null, 2, "pix"),
        )

        assertEquals(SharedLedgerEventType.PURCHASE, purchase.type)
        assertEquals(SharedLedgerEventType.PAYMENT, payment.type)
        assertEquals(700, SharedLedgerProjector.project("customer-1", listOf(purchase, payment)).balanceCents)
    }

    @Test
    fun provenanceRoundTripPreservesSourceActorAndSensitiveFieldsExactly() {
        val original = LedgerProvenance(
            source = LedgerSourceType.VOICE,
            actor = LedgerActorType.AGENT,
            transcript = "Maria levou dois cafés\nconfirmado pelo comerciante",
            agentExecutionId = "exec-123",
            createdAtEpochMs = 42,
        )

        val restored = LedgerProvenanceCodec.decode(LedgerProvenanceCodec.encode(original)!!)

        assertEquals(original, restored)
    }

    @Test
    fun unrelatedCustomerDoesNotEnterProjection() {
        val projection = SharedLedgerProjector.project(
            "customer-1",
            listOf(
                SharedLedgerEvent("one", "customer-1", SharedLedgerEventType.PURCHASE, 100, 1),
                SharedLedgerEvent("two", "customer-2", SharedLedgerEventType.PURCHASE, 900, 2),
            ),
        )

        assertEquals(100, projection.balanceCents)
        assertFalse(projection.events.any { it.customerId != "customer-1" })
    }

    @Test
    fun statementFormatterIsReadableAndDoesNotExposeInternalExecutionData() {
        val statement = SharedLedgerStatement(
            customerId = "customer-1",
            customerName = "Maria Lina",
            balanceCents = 1_250,
            entries = listOf(
                SharedLedgerStatementEntry(
                    id = "purchase",
                    type = SharedLedgerEventType.PURCHASE,
                    signedAmountCents = 2_500,
                    occurredAtEpochMs = 0L,
                    reason = "Compra fiada",
                    source = LedgerSourceType.VOICE,
                ),
                SharedLedgerStatementEntry(
                    id = "payment",
                    type = SharedLedgerEventType.PAYMENT,
                    signedAmountCents = -1_250,
                    occurredAtEpochMs = 0L,
                    reason = null,
                    source = LedgerSourceType.MANUAL_UI,
                ),
            ),
        )

        val text = SharedLedgerStatementFormatter.text(statement, ZoneId.of("UTC"))

        assertTrue(text.contains("Extrato de Maria Lina"))
        assertTrue(text.contains("Saldo em aberto: R$ 12,50"))
        assertTrue(text.contains("Compra fiada · + R$ 25,00 · Compra fiada"))
        assertTrue(text.contains("Pagamento · - R$ 12,50"))
        assertFalse(text.contains("agentExecutionId"))
        assertFalse(text.contains("VOICE"))
    }
}
