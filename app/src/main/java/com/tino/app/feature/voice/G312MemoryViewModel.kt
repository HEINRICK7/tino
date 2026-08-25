package com.tino.app.feature.voice

import androidx.lifecycle.ViewModel
import com.tino.app.domain.agent.PendingAgentAction
import com.tino.app.domain.agent.PendingClarification
import com.tino.app.domain.agent.ScreenAgentContext
import com.tino.app.domain.agent.TinoAgentSession
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.language.EntityReference
import com.tino.app.domain.language.LanguageEntityType
import com.tino.app.domain.language.TinoIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Device harness for G3.12; it stores context only, never a commerce amount or balance. */
@HiltViewModel
class G312MemoryViewModel @Inject constructor(
    private val session: TinoAgentSession,
) : ViewModel() {
    val state = session.snapshot

    fun seedSessionContext() {
        session.enterScreen(
            ScreenAgentContext(
                screen = "CUSTOMER_DETAIL",
                activeCustomerId = "maria-1",
                primaryEntity = EntityReference(LanguageEntityType.CUSTOMER, "Maria"),
            ),
        )
        session.rememberIntent(
            intent = TinoIntent.READ_CUSTOMER_BALANCE,
            entities = listOf(EntityReference(LanguageEntityType.CUSTOMER, "Maria")),
        )
        session.rememberSurface("g312-memory-surface")
        session.rememberResult("grounded-result-reference-only")
    }

    fun seedWorkingMemory() {
        session.updateDraft(
            PendingAgentAction(
                capability = TinoCapabilityId.ADD_CREDIT_ITEM,
                summary = "Rascunho de operação para Maria",
                requiresConfirmation = true,
                intent = TinoIntent.ADD_CREDIT_ITEM,
                collectedSlots = mapOf(
                    "customer" to "Maria",
                    "product" to "Café Maratá",
                    "quantity" to "2",
                ),
                missingSlots = setOf("payment_method"),
            ),
        )
        session.rememberClarification(
            PendingClarification(
                entityType = "payment_method",
                slot = "payment_method",
                prompt = "Qual forma de pagamento?",
                options = listOf("Fiado", "Pix"),
            ),
        )
    }

    fun clearWorkingMemory() {
        session.cancel()
        session.clearClarification()
    }

    fun clearAll() = session.reset()
}
