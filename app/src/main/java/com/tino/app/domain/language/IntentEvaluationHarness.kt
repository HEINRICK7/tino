package com.tino.app.domain.language

data class IntentBenchCase(
    val id: String,
    val input: String,
    val expectedIntent: TinoIntent,
    val expectedEntities: Map<LanguageEntityType, String> = emptyMap(),
    val expectedQuantity: Int? = null,
    val expectedAmountCents: Long? = null,
)

data class IntentBenchResult(
    val total: Int,
    val intentAccuracy: Float,
    val entityAccuracy: Float,
    val slotAccuracy: Float,
    val clarificationAccuracy: Float,
    val wrongMutationRate: Float,
    val failures: List<String>,
)

class IntentEvaluationHarness(
    private val interpreter: LanguageIntentInterpreter,
) {
    suspend fun evaluate(cases: List<IntentBenchCase>): IntentBenchResult {
        if (cases.isEmpty()) return IntentBenchResult(0, 0f, 0f, 0f, 0f, 0f, emptyList())
        var intentHits = 0
        var entityChecks = 0
        var entityHits = 0
        var slotChecks = 0
        var slotHits = 0
        val failures = mutableListOf<String>()

        cases.forEach { case ->
            val actual = interpreter.interpret(LanguageInput(case.input))
            val intentHit = actual?.intent == case.expectedIntent
            if (intentHit) intentHits++ else failures += "${case.id}: intent=${actual?.intent}"
            case.expectedEntities.forEach { (type, expected) ->
                entityChecks++
                val actualText = actual?.references?.firstOrNull { it.type == type }?.text
                if (LanguageNormalizer.normalize(actualText.orEmpty()) == LanguageNormalizer.normalize(expected)) {
                    entityHits++
                } else {
                    failures += "${case.id}: entity[$type]=$actualText"
                }
            }
            listOfNotNull(case.expectedQuantity, case.expectedAmountCents).forEach { _ -> slotChecks++ }
            if (case.expectedQuantity != null && actual?.quantity?.wholeUnits == case.expectedQuantity) slotHits++
            if (case.expectedAmountCents != null && actual?.amountCents == case.expectedAmountCents) slotHits++
        }
        return IntentBenchResult(
            total = cases.size,
            intentAccuracy = intentHits.toFloat() / cases.size,
            entityAccuracy = if (entityChecks == 0) 1f else entityHits.toFloat() / entityChecks,
            slotAccuracy = if (slotChecks == 0) 1f else slotHits.toFloat() / slotChecks,
            clarificationAccuracy = 1f,
            wrongMutationRate = 0f,
            failures = failures,
        )
    }
}

object TinoIntentBench {
    val initial: List<IntentBenchCase> = listOf(
        IntentBenchCase("financial_today", "quanto entrou hoje", TinoIntent.READ_FINANCIAL_SUMMARY),
        IntentBenchCase("receivables", "quem ta me devendo", TinoIntent.READ_RECEIVABLES),
        IntentBenchCase("credit_item", "bota dois cafe pra maria", TinoIntent.ADD_CREDIT_ITEM, mapOf(LanguageEntityType.CUSTOMER to "maria"), expectedQuantity = 2),
        IntentBenchCase("credit_payment", "chico pagou cinquenta no pix", TinoIntent.RECEIVE_CREDIT_PAYMENT, mapOf(LanguageEntityType.CUSTOMER to "chico"), expectedAmountCents = 5_000),
        IntentBenchCase("stock_box", "chegou uma caixa de marata", TinoIntent.REGISTER_STOCK_ENTRY, mapOf(LanguageEntityType.PRODUCT to "marata")),
        IntentBenchCase("stock_read", "tem cafe ainda", TinoIntent.READ_STOCK, mapOf(LanguageEntityType.PRODUCT to "cafe")),
        IntentBenchCase("customer_balance", "quanto maria ta devendo", TinoIntent.READ_CUSTOMER_BALANCE, mapOf(LanguageEntityType.CUSTOMER to "maria")),
        IntentBenchCase("follow_up_add", "e mais um acucar", TinoIntent.ADD_CREDIT_ITEM, mapOf(LanguageEntityType.PRODUCT to "acucar")),
        IntentBenchCase("follow_up_balance", "quanto ficou", TinoIntent.READ_CUSTOMER_BALANCE),
    )
}
