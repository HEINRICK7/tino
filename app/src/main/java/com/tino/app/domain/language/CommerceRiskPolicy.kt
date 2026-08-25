package com.tino.app.domain.language

enum class CommerceOperationRisk {
    LOW,
    MEDIUM,
    HIGH,
}

enum class CommerceActionDecision {
    AUTO_EXECUTE,
    PREVIEW,
    CONFIRM,
    CLARIFY,
}

data class CommerceRiskAssessment(
    val intent: TinoIntent,
    val confidence: Float?,
    val risk: CommerceOperationRisk,
    val decision: CommerceActionDecision,
    val reason: String,
)

object CommerceRiskPolicy {
    fun assess(
        interpretation: IntentInterpretation,
        entitiesResolved: Boolean = true,
        requiredSlotsPresent: Boolean = true,
    ): CommerceRiskAssessment {
        val risk = riskOf(interpretation.intent)
        val confidence = interpretation.confidence
        if (!entitiesResolved || !requiredSlotsPresent || confidence != null && confidence < 0.75f) {
            return assessment(interpretation, risk, CommerceActionDecision.CLARIFY, "Faltam dados confiáveis para continuar.")
        }
        val decision = when (interpretation.intent) {
            TinoIntent.READ_FINANCIAL_SUMMARY,
            TinoIntent.READ_RECEIVABLES,
            TinoIntent.READ_PRODUCT,
            TinoIntent.READ_STOCK,
            TinoIntent.SEARCH_PRODUCT,
            TinoIntent.SEARCH_CUSTOMER,
            TinoIntent.SEARCH_SUPPLIER,
            TinoIntent.READ_CUSTOMER_BALANCE,
            TinoIntent.READ_CUSTOMER_TIMELINE,
            -> CommerceActionDecision.AUTO_EXECUTE
            TinoIntent.ADD_CREDIT,
            TinoIntent.ADD_CREDIT_ITEM,
            TinoIntent.REGISTER_STOCK_ENTRY,
            -> CommerceActionDecision.PREVIEW
            TinoIntent.RECEIVE_CREDIT_PAYMENT,
            TinoIntent.CHANGE_PRICE,
            -> CommerceActionDecision.CONFIRM
            TinoIntent.CORRECTION,
            TinoIntent.NEGATION,
            TinoIntent.COMPOUND,
            -> CommerceActionDecision.CLARIFY
        }
        return assessment(interpretation, risk, decision, reasonFor(decision))
    }

    fun riskOf(intent: TinoIntent): CommerceOperationRisk = when (intent) {
        TinoIntent.READ_FINANCIAL_SUMMARY,
        TinoIntent.READ_RECEIVABLES,
        TinoIntent.READ_PRODUCT,
        TinoIntent.READ_STOCK,
        TinoIntent.SEARCH_PRODUCT,
        TinoIntent.SEARCH_CUSTOMER,
        TinoIntent.SEARCH_SUPPLIER,
        TinoIntent.READ_CUSTOMER_BALANCE,
        TinoIntent.READ_CUSTOMER_TIMELINE,
        -> CommerceOperationRisk.LOW
        TinoIntent.ADD_CREDIT,
        TinoIntent.ADD_CREDIT_ITEM,
        TinoIntent.REGISTER_STOCK_ENTRY,
        -> CommerceOperationRisk.MEDIUM
        TinoIntent.RECEIVE_CREDIT_PAYMENT,
        TinoIntent.CHANGE_PRICE,
        -> CommerceOperationRisk.HIGH
        TinoIntent.CORRECTION,
        TinoIntent.NEGATION,
        TinoIntent.COMPOUND,
        -> CommerceOperationRisk.MEDIUM
    }

    private fun assessment(
        interpretation: IntentInterpretation,
        risk: CommerceOperationRisk,
        decision: CommerceActionDecision,
        reason: String,
    ) = CommerceRiskAssessment(interpretation.intent, interpretation.confidence, risk, decision, reason)

    private fun reasonFor(decision: CommerceActionDecision): String = when (decision) {
        CommerceActionDecision.AUTO_EXECUTE -> "Consulta sem mutação pronta para execução."
        CommerceActionDecision.PREVIEW -> "A operação altera dados e precisa ser revisada antes de executar."
        CommerceActionDecision.CONFIRM -> "A operação afeta dinheiro ou preço e precisa de confirmação explícita."
        CommerceActionDecision.CLARIFY -> "Preciso esclarecer os dados antes de continuar."
    }
}
