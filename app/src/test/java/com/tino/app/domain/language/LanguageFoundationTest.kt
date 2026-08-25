package com.tino.app.domain.language

import com.tino.app.domain.commerce.PaymentMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageFoundationTest {
    private val interpreter = DeterministicLanguageInterpreter()

    @Test
    fun normalizesCommercialTermsWithoutChangingMeaning() {
        assertEquals("cafe marata", LanguageNormalizer.normalize("Café Maratá"))
        assertEquals(CommercialUnit.BOX, UnitLexicon.resolve("CAIXAS"))
        assertEquals(PaymentMethod.PIX, PaymentMethodLexicon.resolve("pelo PIX"))
    }

    @Test
    fun parsesCommercialQuantitiesDeterministically() {
        assertEquals(6, QuantityParser.parse("meia dúzia")?.wholeUnits)
        assertEquals(24, QuantityParser.parse("vinte e quatro unidades")?.wholeUnits)
        assertEquals("0.5", QuantityParser.parse("meio quilo")?.amount?.toPlainString())
        assertEquals("1.5", QuantityParser.parse("um quilo e meio")?.amount?.toPlainString())
        assertEquals(CommercialUnit.BOX, QuantityParser.parse("uma caixa")?.unit)
    }

    @Test
    fun parsesMoneyAsCentsWithoutFloatingPoint() {
        assertEquals(1_000L, MoneyParser.parse("dez conto"))
        assertEquals(1_050L, MoneyParser.parse("dez reais e cinquenta centavos"))
        assertEquals(1_050L, MoneyParser.parse("R$ 10,50"))
    }

    @Test
    fun interpretsCreditPaymentAsReferencesAndFactsWithoutIds() = runBlocking {
        val result = interpreter.interpret(LanguageInput("Chico pagou cinquenta no Pix"))

        assertNotNull(result)
        assertEquals(TinoIntent.RECEIVE_CREDIT_PAYMENT, result?.intent)
        assertEquals("chico", result?.references?.single()?.text)
        assertEquals(5_000L, result?.amountCents)
        assertEquals(PaymentMethod.PIX, result?.paymentMethod)
    }

    @Test
    fun interpretsCreditItemWithTextualReferencesOnly() = runBlocking {
        val result = interpreter.interpret(LanguageInput("Bota dois cafés Maratá na conta da Maria"))

        assertEquals(TinoIntent.ADD_CREDIT_ITEM, result?.intent)
        assertEquals(setOf("maria", "cafes marata"), result?.references?.map { it.text }?.toSet())
        assertEquals(2, result?.quantity?.wholeUnits)
    }

    @Test
    fun interpretsStockEntryWithoutAssumingPackageContents() = runBlocking {
        val result = interpreter.interpret(LanguageInput("Chegou uma caixa de Maratá"))

        assertEquals(TinoIntent.REGISTER_STOCK_ENTRY, result?.intent)
        assertEquals("marata", result?.references?.single()?.text)
        assertEquals(CommercialUnit.BOX, result?.quantity?.unit)
        assertTrue(result?.quantity?.requiresCatalogPackaging == true)
    }

    @Test
    fun recognizesReadIntentsAndCustomerBalance() = runBlocking {
        assertEquals(
            TinoIntent.READ_FINANCIAL_SUMMARY,
            interpreter.interpret(LanguageInput("Quanto entrou hoje"))?.intent,
        )
        assertEquals(
            TinoIntent.READ_RECEIVABLES,
            interpreter.interpret(LanguageInput("Quem tá me devendo"))?.intent,
        )
        assertEquals(
            "maria",
            interpreter.interpret(LanguageInput("Quanto a Maria tá devendo"))?.references?.single()?.text,
        )
    }

    @Test
    fun corpusHasARealInitialRegressionSet() {
        assertTrue(PtBrUtteranceCorpus.initial.size >= 50)
        assertTrue(PtBrUtteranceCorpus.initial.all { it.text.isNotBlank() })
    }

    @Test
    fun blankInputDoesNotCreateAnIntent() = runBlocking {
        assertNull(interpreter.interpret(LanguageInput("   ")))
    }

    @Test
    fun contextCarriesCustomerIntoShortFollowUps() = runBlocking {
        val memory = CommerceContextMemory()
        val contextual = ContextualLanguageInterpreter(interpreter, memory)

        contextual.interpret(LanguageInput("Bota dois cafés pra Maria"))
        val more = contextual.interpret(LanguageInput("E mais um açúcar"))
        val balance = contextual.interpret(LanguageInput("Quanto ficou"))

        assertEquals("maria", more?.references?.first { it.type == LanguageEntityType.CUSTOMER }?.text)
        assertEquals("acucar", more?.references?.first { it.type == LanguageEntityType.PRODUCT }?.text)
        assertEquals(1, more?.quantity?.wholeUnits)
        assertEquals(TinoIntent.READ_CUSTOMER_BALANCE, balance?.intent)
        assertEquals("maria", balance?.references?.single()?.text)
    }

    @Test
    fun screenContextResolvesPronounsWithoutBlockingExplicitCommerceIntent() = runBlocking {
        val memory = CommerceContextMemory()
        memory.rememberScreen(
            screen = "PRODUCT_DETAIL",
            primaryEntity = EntityReference(LanguageEntityType.PRODUCT, "cafe marata"),
        )
        val contextual = ContextualLanguageInterpreter(interpreter, memory)

        val result = contextual.interpret(LanguageInput("Bota dois dele pra Maria"))

        assertEquals(TinoIntent.ADD_CREDIT_ITEM, result?.intent)
        assertEquals("cafe marata", result?.references?.first { it.type == LanguageEntityType.PRODUCT }?.text)
        assertEquals("maria", result?.references?.first { it.type == LanguageEntityType.CUSTOMER }?.text)
        assertEquals(2, result?.quantity?.wholeUnits)
    }

    @Test
    fun customerScreenContextResolvesPronounForPayment() = runBlocking {
        val memory = CommerceContextMemory()
        memory.rememberScreen(
            screen = "CUSTOMER_DETAIL",
            primaryEntity = EntityReference(LanguageEntityType.CUSTOMER, "maria lina"),
        )
        val contextual = ContextualLanguageInterpreter(interpreter, memory)

        val result = contextual.interpret(LanguageInput("Ela pagou vinte no Pix"))

        assertEquals(TinoIntent.RECEIVE_CREDIT_PAYMENT, result?.intent)
        assertEquals("maria lina", result?.references?.single()?.text)
        assertEquals(2_000L, result?.amountCents)
        assertEquals(PaymentMethod.PIX, result?.paymentMethod)
    }

    @Test
    fun intentGraphKeepsNeighboringCommerceOperationsExplicit() {
        assertTrue(TinoIntent.READ_CUSTOMER_BALANCE in IntentGraph.relatedTo(TinoIntent.ADD_CREDIT_ITEM))
        assertTrue(TinoIntent.REGISTER_STOCK_ENTRY in IntentGraph.relatedTo(TinoIntent.READ_STOCK))
        assertEquals(CommerceConcept.CUSTOMER, IntentGraph.node(TinoIntent.RECEIVE_CREDIT_PAYMENT)?.concept)
    }

    @Test
    fun contextualResolverUsesUsageAndScreenWithoutChangingEntityIdentity() {
        val resolver = ContextualEntityResolver<String>()
        val candidates = listOf(
            ContextualCandidate("cafe-250", "Café Maratá 250g", usageFrequency = 20, recentUses = 5, screenTags = setOf("sale")),
            ContextualCandidate("cafe-500", "Café Maratá 500g", usageFrequency = 1, screenTags = setOf("stock")),
        )

        val result = resolver.resolve("Maratá", candidates, CommerceContext(currentScreen = "sale"))

        val resolved = result as LanguageEntityResolution.Resolved
        assertEquals("cafe-250", resolved.entity)
    }

    @Test
    fun riskPolicySeparatesReadPreviewAndHighRiskConfirmation() {
        val read = CommerceRiskPolicy.assess(
            IntentInterpretation(TinoIntent.READ_STOCK, source = LanguageSource.VOICE, transcript = "tem cafe", confidence = 0.78f),
        )
        val credit = CommerceRiskPolicy.assess(
            IntentInterpretation(TinoIntent.ADD_CREDIT_ITEM, source = LanguageSource.VOICE, transcript = "anota", confidence = 0.95f),
        )
        val price = CommerceRiskPolicy.assess(
            IntentInterpretation(TinoIntent.CHANGE_PRICE, source = LanguageSource.VOICE, transcript = "muda", confidence = 0.95f),
        )

        assertEquals(CommerceActionDecision.AUTO_EXECUTE, read.decision)
        assertEquals(CommerceActionDecision.PREVIEW, credit.decision)
        assertEquals(CommerceActionDecision.CONFIRM, price.decision)
        assertEquals(CommerceOperationRisk.HIGH, price.risk)
    }

    @Test
    fun lowConfidenceNeverAutoExecutesEvenForRead() {
        val assessment = CommerceRiskPolicy.assess(
            IntentInterpretation(TinoIntent.READ_STOCK, source = LanguageSource.VOICE, transcript = "talvez", confidence = 0.60f),
        )

        assertEquals(CommerceActionDecision.CLARIFY, assessment.decision)
    }

    @Test
    fun intentBenchMeasuresTheSafeDeterministicSlice() = runBlocking {
        val bench = IntentEvaluationHarness(ContextualLanguageInterpreter(interpreter, CommerceContextMemory()))
        val result = bench.evaluate(TinoIntentBench.initial)

        assertEquals(9, result.total)
        assertEquals(1f, result.intentAccuracy, 0f)
        assertEquals(1f, result.entityAccuracy, 0f)
        assertEquals(1f, result.slotAccuracy, 0f)
        assertEquals(0f, result.wrongMutationRate, 0f)
    }

    @Test
    fun correctionsUpdateThePendingOperationWithoutBecomingASecondMutation() = runBlocking {
        val contextual = ContextualLanguageInterpreter(interpreter, CommerceContextMemory())

        contextual.interpret(LanguageInput("Chico pagou vinte no Pix"))
        val correction = contextual.interpret(LanguageInput("Não, foi dinheiro"))

        assertEquals(TinoIntent.CORRECTION, correction?.intent)
        assertEquals(PaymentMethod.CASH, correction?.paymentMethod)
        assertEquals(LanguageCorrectionField.PAYMENT_METHOD, correction?.correction?.field)
        assertEquals(TinoIntent.RECEIVE_CREDIT_PAYMENT, correction?.correction?.previousIntent)
    }

    @Test
    fun quantityCorrectionsAreExplicitAndPreserveCustomerAndProduct() = runBlocking {
        val contextual = ContextualLanguageInterpreter(interpreter, CommerceContextMemory())

        contextual.interpret(LanguageInput("Bota dois cafe pra Maria"))
        val correction = contextual.interpret(LanguageInput("Não, três"))

        assertEquals(TinoIntent.CORRECTION, correction?.intent)
        assertEquals(LanguageCorrectionField.QUANTITY, correction?.correction?.field)
        assertEquals(3, correction?.quantity?.wholeUnits)
        assertEquals(setOf("maria", "cafe"), correction?.references?.map { it.text }?.toSet())
    }

    @Test
    fun negationCannotBeInterpretedAsAnAction() = runBlocking {
        val result = ContextualLanguageInterpreter(interpreter, CommerceContextMemory())
            .interpret(LanguageInput("Não coloca no fiado"))

        assertEquals(TinoIntent.NEGATION, result?.intent)
        assertTrue(result?.negated == true)
    }

    @Test
    fun compoundIntentKeepsPaymentAndCreditOperationsSeparate() = runBlocking {
        val result = interpreter.interpret(LanguageInput("Maria pagou vinte no pix e levou um cafe"))

        assertEquals(TinoIntent.COMPOUND, result?.intent)
        assertEquals(2, result?.operations?.size)
        assertEquals(TinoIntent.RECEIVE_CREDIT_PAYMENT, result?.operations?.get(0)?.intent)
        assertEquals(TinoIntent.ADD_CREDIT_ITEM, result?.operations?.get(1)?.intent)
        assertEquals(2_000L, result?.operations?.get(0)?.amountCents)
        assertEquals(1, result?.operations?.get(1)?.quantity?.wholeUnits)
    }

    @Test
    fun aliasesNeedConsistentCorrectionsBeforePromotion() = runBlocking {
        val memory = CommerceContextMemory()
        val contextual = ContextualLanguageInterpreter(interpreter, memory)

        contextual.interpret(LanguageInput("Bota um maraca pra Maria"))
        contextual.interpret(LanguageInput("Não, Maratá"))
        assertNull(memory.learnedAlias("maraca", LanguageEntityType.PRODUCT))
        contextual.interpret(LanguageInput("Não, Maratá"))

        assertEquals("marata", memory.learnedAlias("maraca", LanguageEntityType.PRODUCT))
        assertEquals("marata", memory.context.localAliases["maraca"])
    }

    @Test
    fun riskPolicyClarifiesCompoundAndCorrectionInsteadOfExecutingThem() {
        val compound = CommerceRiskPolicy.assess(
            IntentInterpretation(TinoIntent.COMPOUND, source = LanguageSource.TEXT, transcript = "pagou e levou"),
        )
        val correction = CommerceRiskPolicy.assess(
            IntentInterpretation(TinoIntent.CORRECTION, source = LanguageSource.TEXT, transcript = "não, três"),
        )

        assertEquals(CommerceActionDecision.CLARIFY, compound.decision)
        assertEquals(CommerceActionDecision.CLARIFY, correction.decision)
    }
}
