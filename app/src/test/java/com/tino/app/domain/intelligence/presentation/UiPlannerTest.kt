package com.tino.app.domain.intelligence.presentation

import com.tino.app.domain.intelligence.IntelligenceResponseStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPlannerTest {
    private val planner = DeterministicUiPlanner()

    @Test
    fun simpleGroundedAnswerBecomesTextWithoutA2uiKnowledge() = runBlocking {
        val decision = planner.plan(
            UiContext(currentScreen = "CUSTOMER_DETAIL"),
            grounded(
                answer = "Maria deve R$ 62,50.",
                evidence = listOf(GroundedEvidence("Saldo", "R$ 62,50", "ROOM")),
                hint = GroundedPresentationHint.TEXT,
            ),
        )

        assertEquals(UiDecisionKind.TEXT, decision.kind)
        assertEquals("Maria deve R$ 62,50.", (decision as UiDecision.Text).value)
    }

    @Test
    fun comparisonBecomesCreateSurface() = runBlocking {
        val decision = planner.plan(
            UiContext(),
            grounded(
                answer = "Esta semana entrou mais.",
                evidence = listOf(GroundedEvidence("Variação", "+14%", "ANALYTICS")),
                hint = GroundedPresentationHint.COMPARISON,
            ),
        )

        val surface = decision as UiDecision.CreateSurface
        assertEquals(UiDecisionKind.CREATE_SURFACE, surface.kind)
        assertEquals(UiSurfaceSemanticType.COMPARISON, surface.semanticType)
    }

    @Test
    fun existingSurfaceIsUpdatedWhenSemanticTypeMatches() = runBlocking {
        val decision = planner.plan(
            UiContext(
                activeSurfaceId = "surface-1",
                activeSurfaceSemanticType = UiSurfaceSemanticType.COMPARISON,
            ),
            grounded(
                answer = "Agora entrou menos.",
                evidence = listOf(GroundedEvidence("Variação", "-3%", "ANALYTICS")),
                hint = GroundedPresentationHint.COMPARISON,
            ),
        )

        val update = decision as UiDecision.UpdateSurface
        assertEquals("surface-1", update.surfaceId)
        assertEquals(UiDecisionKind.UPDATE_SURFACE, update.kind)
    }

    @Test
    fun ambiguousEntityRequestsClarification() = runBlocking {
        val decision = planner.plan(
            UiContext(),
            grounded(
                status = IntelligenceResponseStatus.AMBIGUOUS_ENTITY,
                answer = "Encontrei Maria Lina e Maria Luiza.",
                options = listOf("Maria Lina", "Maria Luiza"),
            ),
        )

        val clarification = decision as UiDecision.RequestClarification
        assertEquals(UiDecisionKind.REQUEST_CLARIFICATION, clarification.kind)
        assertEquals(2, clarification.options.size)
    }

    @Test
    fun preparedMutationRequestsConfirmation() = runBlocking {
        val decision = planner.plan(
            UiContext(),
            grounded(
                answer = "Alterar o preço do Café para R$ 10,90?",
                requiresConfirmation = true,
                operationId = "op-1",
            ),
        )

        val confirmation = decision as UiDecision.RequestConfirmation
        assertEquals(UiDecisionKind.REQUEST_CONFIRMATION, confirmation.kind)
        assertEquals("op-1", confirmation.operationId)
    }

    @Test
    fun missingSlotsRequestInputBeforeAnySurface() = runBlocking {
        val decision = planner.plan(
            UiContext(),
            grounded(answer = "Qual produto você quer alterar?", missingInputs = listOf("produto")),
        )

        val input = decision as UiDecision.RequestInput
        assertEquals(UiDecisionKind.REQUEST_INPUT, input.kind)
        assertEquals(listOf("produto"), input.fields)
    }

    @Test
    fun insufficientDataShowsErrorAndNeverInventsInsight() = runBlocking {
        val decision = planner.plan(
            UiContext(),
            grounded(
                status = IntelligenceResponseStatus.INSUFFICIENT_DATA,
                answer = "Ainda não tenho histórico suficiente.",
                hint = GroundedPresentationHint.INSIGHT,
            ),
        )

        val error = decision as UiDecision.ShowError
        assertEquals(UiDecisionKind.SHOW_ERROR, error.kind)
        assertEquals(IntelligenceResponseStatus.INSUFFICIENT_DATA, error.status)
    }

    @Test
    fun emptyAnsweredResultProducesNoUi() = runBlocking {
        val decision = planner.plan(UiContext(), grounded(answer = ""))

        assertEquals(UiDecisionKind.NO_UI, decision.kind)
    }

    @Test
    fun fallbackPlannerUsesDeterministicPolicyWhenPrimaryFails() = runBlocking {
        val failing = object : UiPlannerPort {
            override suspend fun plan(context: UiContext, result: GroundedResult): UiDecision =
                error("planner unavailable")
        }

        val decision = FallbackUiPlanner(failing).plan(
            UiContext(),
            grounded(answer = "Resposta simples"),
        )

        assertTrue(decision is UiDecision.Text)
    }

    private fun grounded(
        status: IntelligenceResponseStatus = IntelligenceResponseStatus.ANSWERED,
        answer: String,
        evidence: List<GroundedEvidence> = emptyList(),
        hint: GroundedPresentationHint = GroundedPresentationHint.TEXT,
        missingInputs: List<String> = emptyList(),
        options: List<String> = emptyList(),
        requiresConfirmation: Boolean = false,
        operationId: String? = null,
    ) = GroundedResult(
        status = status,
        answer = answer,
        evidence = evidence,
        presentationHint = hint,
        missingInputs = missingInputs,
        clarificationOptions = options,
        requiresConfirmation = requiresConfirmation,
        operationId = operationId,
    )
}
