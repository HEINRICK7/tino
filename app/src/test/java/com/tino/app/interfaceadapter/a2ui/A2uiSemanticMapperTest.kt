package com.tino.app.interfaceadapter.a2ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class A2uiSemanticMapperTest {
    @Test
    fun recoverableErrorsUseAnExplicitErrorPrimitive() {
        val message = A2uiSemanticMapper.error("A consulta demorou mais que o esperado.")

        assertTrue(message.component is A2uiComponent.ErrorStatusCard)
        val component = message.component as A2uiComponent.ErrorStatusCard
        assertEquals(TinoA2UiComponentCatalog.ERROR_RECOVERY, component.type)
        assertEquals("A consulta demorou mais que o esperado.", component.message)
    }

    @Test
    fun errorPrimitiveSurvivesTheA2uiWireRoundTrip() {
        val original = A2uiSemanticMapper.error("Tente novamente.", title = "Falha")

        val decoded = TinoA2UiJsonCodec.decode(TinoA2UiJsonCodec.encode(original))
        assertTrue(decoded.component is A2uiComponent.ErrorStatusCard)
        val component = decoded.component as A2uiComponent.ErrorStatusCard
        assertEquals("Falha", component.title)
        assertEquals("Tente novamente.", component.message)
        assertEquals("TENTAR DE NOVO", component.retryLabel)
    }

    @Test
    fun registryFallbackDoesNotEncodeErrorsAsUnsupportedComponents() {
        val message = A2uiSemanticComponentRegistry.fallback("m-1", "Operação indisponível.")

        assertTrue(message.component is A2uiComponent.ErrorStatusCard)
        assertEquals("m-1", message.messageId)
    }
}
