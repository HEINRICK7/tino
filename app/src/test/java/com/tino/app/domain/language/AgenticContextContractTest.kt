package com.tino.app.domain.language

import com.tino.app.domain.agent.AgentVoiceState
import com.tino.app.domain.agent.ScreenAgentContext
import com.tino.app.domain.agent.TinoAgentSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgenticContextContractTest {
    private val base = DeterministicLanguageInterpreter()

    @Test
    fun explicitReferenceWinsOverTheCurrentScreen() = runBlocking {
        val memory = CommerceContextMemory()
        memory.rememberScreen(
            screen = "CUSTOMER_DETAIL",
            primaryEntity = EntityReference(LanguageEntityType.CUSTOMER, "Maria Lina"),
        )

        val result = ContextualLanguageInterpreter(base, memory)
            .interpret(LanguageInput("Quanto Maria José deve", LanguageSource.TEXT))

        assertEquals("maria jose", result?.references?.single()?.text)
        assertEquals(ContextReferenceSource.EXPLICIT, result?.referenceSources?.get(LanguageEntityType.CUSTOMER))
    }

    @Test
    fun screenProductSurvivesProductFollowUps() = runBlocking {
        val memory = CommerceContextMemory()
        memory.rememberScreen(
            screen = "PRODUCT_DETAIL",
            primaryEntity = EntityReference(LanguageEntityType.PRODUCT, "Café Maratá"),
        )
        val contextual = ContextualLanguageInterpreter(base, memory)

        val stock = contextual.interpret(LanguageInput("Quanto tem dele", LanguageSource.TEXT))
        val price = contextual.interpret(LanguageInput("E o preço", LanguageSource.TEXT))

        assertEquals(TinoIntent.READ_STOCK, stock?.intent)
        assertEquals("cafe marata", stock?.references?.single()?.text)
        assertEquals(TinoIntent.READ_PRODUCT, price?.intent)
        assertEquals("cafe marata", price?.references?.single()?.text)
    }

    @Test
    fun customerConversationContinuesIntoTimelineAndExplicitNextCustomer() = runBlocking {
        val memory = CommerceContextMemory()
        val contextual = ContextualLanguageInterpreter(base, memory)

        contextual.interpret(LanguageInput("Quanto a Maria deve", LanguageSource.TEXT))
        val timeline = contextual.interpret(LanguageInput("E quanto ela pagou esse mês", LanguageSource.TEXT))
        val next = contextual.interpret(LanguageInput("E o Chico", LanguageSource.TEXT))

        assertEquals(TinoIntent.READ_CUSTOMER_TIMELINE, timeline?.intent)
        assertEquals("maria", timeline?.references?.single()?.text)
        assertEquals(TinoIntent.READ_CUSTOMER_TIMELINE, next?.intent)
        assertEquals("chico", next?.references?.single()?.text)
    }

    @Test
    fun customerCorrectionDoesNotCreateASecondIntent() = runBlocking {
        val contextual = ContextualLanguageInterpreter(base, CommerceContextMemory())

        contextual.interpret(LanguageInput("Bota dois cafe pra Maria", LanguageSource.TEXT))
        val correction = contextual.interpret(LanguageInput("Não, Maria José", LanguageSource.TEXT))

        assertEquals(TinoIntent.CORRECTION, correction?.intent)
        assertEquals(LanguageCorrectionField.CUSTOMER, correction?.correction?.field)
        assertEquals("maria jose", correction?.correction?.value)
    }

    @Test
    fun weakPronounDoesNotChooseAnArbitraryEntity() = runBlocking {
        val result = ContextualLanguageInterpreter(base, CommerceContextMemory())
            .interpret(LanguageInput("Quanto tem dele", LanguageSource.TEXT))

        assertNull(result)
    }

    @Test
    fun conversationExpiresButScreenAnchorRemains() = runBlocking {
        val memory = CommerceContextMemory()
        memory.rememberScreen(
            screen = "PRODUCT_DETAIL",
            primaryEntity = EntityReference(LanguageEntityType.PRODUCT, "Café Maratá"),
        )
        memory.remember(base.interpret(LanguageInput("Quanto tem de cafe", LanguageSource.TEXT))!!)
        val now = memory.context.contextUpdatedAtEpochMs!!

        memory.expireConversationIfNeeded(now + CommerceContextMemory.DEFAULT_CONVERSATION_TTL_MS + 1)

        assertEquals("PRODUCT_DETAIL", memory.context.currentScreen)
        assertNull(memory.previousInterpretation())
    }

    @Test
    fun cancellingPendingSessionDoesNotEraseScreenContext() {
        val session = TinoAgentSession()
        session.enterScreen(
            ScreenAgentContext(
                screen = "CUSTOMER_DETAIL",
                primaryEntity = EntityReference(LanguageEntityType.CUSTOMER, "Maria Lina"),
            ),
        )
        session.beginListening()
        session.cancel()

        assertEquals(AgentVoiceState.IDLE, session.snapshot.value.voiceState)
        assertEquals("CUSTOMER_DETAIL", session.snapshot.value.screenContext.screen)
        assertTrue(session.snapshot.value.pendingAction == null)
    }
}
