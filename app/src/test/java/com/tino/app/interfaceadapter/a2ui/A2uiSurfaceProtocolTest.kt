package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.intelligence.IntelligenceResponseStatus
import com.tino.app.domain.intelligence.presentation.GroundedEvidence
import com.tino.app.domain.intelligence.presentation.GroundedPresentationHint
import com.tino.app.domain.intelligence.presentation.GroundedResult
import com.tino.app.domain.intelligence.presentation.UiDecision
import com.tino.app.domain.intelligence.presentation.UiSurfaceSemanticType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A2uiSurfaceProtocolTest {
    private val result = GroundedResult(
        status = IntelligenceResponseStatus.ANSWERED,
        answer = "Entraram R$ 215,00 hoje.",
        evidence = listOf(GroundedEvidence("Fonte", "resumo financeiro", "LOCAL_FACTS")),
        presentationHint = GroundedPresentationHint.INSIGHT,
    )

    @Test
    fun composerProducesVersionedCreateSurfaceWithFlatComponentsAndBinding() {
        val message = DeterministicA2uiComposer().compose(
            UiDecision.CreateSurface(
                semanticType = UiSurfaceSemanticType.INSIGHT,
                title = "Resumo de hoje",
                result = result,
            ),
            surfaceId = "financial",
        )

        assertEquals("financial", message.surfaceId)
        assertEquals(A2uiSurfaceOperation.CREATE_SURFACE, message.operation)
        assertEquals(1L, message.sequence)
        assertEquals(listOf("primary"), message.components.map { it.componentId })
        assertEquals("answer", message.components.single().bindings["answer"])
        assertEquals(A2uiSurfaceValidation.Valid, A2uiSurfaceValidator.validate(message))
    }

    @Test
    fun hostAppliesCreateThenDataModelUpdateWithoutRecreatingComponents() {
        val composer = DeterministicA2uiComposer()
        val host = A2uiSurfaceHost()
        val create = composer.compose(
            UiDecision.CreateSurface(UiSurfaceSemanticType.INSIGHT, "Hoje", result),
            surfaceId = "primary",
        )
        assertTrue(host.apply(create) is A2uiSurfaceApplyResult.Applied)

        val update = composer.compose(
            UiDecision.UpdateSurface("primary", UiSurfaceSemanticType.INSIGHT, result.copy(answer = "Entraram R$ 300,00 hoje.")),
        )
        val applied = host.apply(update)
        val state = (applied as A2uiSurfaceApplyResult.Applied).state!!

        assertEquals("Entraram R$ 300,00 hoje.", state.dataModel["answer"])
        assertEquals(create.components, state.components)
        assertEquals(2L, update.sequence)
    }

    @Test
    fun hostUpdatesOnlyMatchingComponentInFlatList() {
        val host = A2uiSurfaceHost()
        val first = A2uiSurfaceComponent("first", TinoA2UiComponentCatalog.INSIGHT_CARD)
        val second = A2uiSurfaceComponent("second", TinoA2UiComponentCatalog.INSIGHT_CARD)
        host.apply(
            A2uiSurfaceMessage("create", "surface", A2uiSurfaceOperation.CREATE_SURFACE, listOf(first, second)),
        )

        host.apply(
            DeterministicA2uiComposer().updateComponents(
                "surface",
                listOf(second.copy(props = mapOf("title" to "Atualizado"))),
            ),
        )

        val state = host.snapshot("surface")!!
        assertEquals("Atualizado", state.components[1].props["title"])
        assertEquals(first, state.components[0])
    }

    @Test
    fun deleteSurfaceRemovesState() {
        val host = A2uiSurfaceHost()
        host.apply(
            A2uiSurfaceMessage(
                "create",
                "surface",
                A2uiSurfaceOperation.CREATE_SURFACE,
                components = listOf(A2uiSurfaceComponent("primary", TinoA2UiComponentCatalog.INSIGHT_CARD)),
            ),
        )
        val result = host.apply(A2uiSurfaceMessage("delete", "surface", A2uiSurfaceOperation.DELETE_SURFACE))

        assertTrue(result is A2uiSurfaceApplyResult.Applied)
        assertEquals(null, host.snapshot("surface"))
    }

    @Test
    fun unknownComponentRemainsInertAndRendersFallbackData() {
        val host = A2uiSurfaceHost()
        host.apply(
            A2uiSurfaceMessage(
                "create",
                "surface",
                A2uiSurfaceOperation.CREATE_SURFACE,
                components = listOf(A2uiSurfaceComponent("primary", "execute_arbitrary_code")),
            ),
        )

        val message = host.snapshot("surface")!!.toRenderableMessage()
        assertTrue(message.component is A2uiComponent.Unsupported)
        assertFalse(TinoA2UiComponentCatalog.isAllowed(message.component.type))
    }

    @Test
    fun invalidSchemaAndLifecyclePayloadAreRejected() {
        val invalid = A2uiSurfaceMessage(
            messageId = "bad",
            surfaceId = "surface",
            operation = A2uiSurfaceOperation.CREATE_SURFACE,
            schema = "future.schema",
        )

        assertTrue(A2uiSurfaceValidator.validate(invalid) is A2uiSurfaceValidation.Invalid)
        assertTrue(A2uiSurfaceHost().apply(invalid) is A2uiSurfaceApplyResult.Rejected)
    }

    @Test
    fun hostRejectsDifferentMessageWithRepeatedPositiveSequence() {
        val host = A2uiSurfaceHost()
        val create = A2uiSurfaceMessage(
            messageId = "m1",
            surfaceId = "surface",
            operation = A2uiSurfaceOperation.CREATE_SURFACE,
            components = listOf(A2uiSurfaceComponent("primary", TinoA2UiComponentCatalog.INSIGHT_CARD)),
            sequence = 4L,
        )
        assertTrue(host.apply(create) is A2uiSurfaceApplyResult.Applied)

        val repeated = create.copy(messageId = "m2")
        assertTrue(host.apply(repeated) is A2uiSurfaceApplyResult.Rejected)
    }

    @Test
    fun hostNormalizesLegacyZeroSequenceAndKeepsItMonotonic() {
        val host = A2uiSurfaceHost()
        val create = A2uiSurfaceMessage(
            "create",
            "surface",
            A2uiSurfaceOperation.CREATE_SURFACE,
            components = listOf(A2uiSurfaceComponent("primary", TinoA2UiComponentCatalog.INSIGHT_CARD)),
        )
        val update = A2uiSurfaceMessage(
            "update",
            "surface",
            A2uiSurfaceOperation.UPDATE_DATA_MODEL,
            dataModel = mapOf("answer" to "parcial"),
        )

        assertTrue(host.apply(create) is A2uiSurfaceApplyResult.Applied)
        assertTrue(host.apply(update) is A2uiSurfaceApplyResult.Applied)
        assertEquals(2L, host.snapshot("surface")?.sequence)
    }

    @Test
    fun finalSurfaceIsExplicitAndRejectsLaterPatchesButAllowsDeletion() {
        val composer = DeterministicA2uiComposer()
        val host = A2uiSurfaceHost()
        val create = composer.compose(
            UiDecision.CreateSurface(UiSurfaceSemanticType.INSIGHT, "Hoje", result),
            surfaceId = "final-surface",
        )
        assertTrue(host.apply(create) is A2uiSurfaceApplyResult.Applied)

        val finalMessage = composer.finalDataModel(
            "final-surface",
            mapOf("answer" to "Resposta final", "status" to "ANSWERED"),
        )
        val finalState = (host.apply(finalMessage) as A2uiSurfaceApplyResult.Applied).state!!
        assertTrue(finalState.isFinal)

        val rejected = host.apply(
            composer.updateComponents("final-surface", listOf(
                A2uiSurfaceComponent("primary", TinoA2UiComponentCatalog.INSIGHT_CARD),
            )),
        )
        assertTrue(rejected is A2uiSurfaceApplyResult.Rejected)
        assertTrue(host.apply(A2uiSurfaceMessage("delete", "final-surface", A2uiSurfaceOperation.DELETE_SURFACE)) is A2uiSurfaceApplyResult.Applied)
        assertEquals(null, host.snapshot("final-surface"))
    }

    @Test
    fun surfaceCodecRoundTripsLifecycleMessageAndEscapedText() {
        val original = A2uiSurfaceMessage(
            messageId = "m-1",
            surfaceId = "primary",
            operation = A2uiSurfaceOperation.UPDATE_DATA_MODEL,
            dataModel = mapOf("answer" to "R$ 10,00\nconfirmado"),
            isFinal = true,
        )

        val decoded = TinoA2UiSurfaceJsonCodec.decode(TinoA2UiSurfaceJsonCodec.encode(original))

        assertEquals(original, decoded)
        assertEquals(A2uiSurfaceValidation.Valid, A2uiSurfaceValidator.validate(decoded))
    }

    @Test
    fun bindingResolvesFromDataModelAtRenderBoundary() {
        val state = A2uiSurfaceState(
            surfaceId = "primary",
            components = listOf(
                A2uiSurfaceComponent(
                    componentId = "primary",
                    type = TinoA2UiComponentCatalog.INSIGHT_CARD,
                    props = mapOf("title" to "Hoje"),
                    bindings = mapOf("answer" to "answer"),
                ),
            ),
            dataModel = mapOf("answer" to "Atualizado sem recriar a surface"),
        )

        val message = state.toRenderableMessage()
        assertEquals("Atualizado sem recriar a surface", (message.component as A2uiComponent.InsightCard).answer)
    }
}
