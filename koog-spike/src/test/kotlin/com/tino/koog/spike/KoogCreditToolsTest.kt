package com.tino.koog.spike

import com.tino.agent.contracts.BalanceLookup
import com.tino.agent.contracts.CreditFactsTiming
import com.tino.agent.contracts.CreditPreparationFactsPort
import com.tino.agent.contracts.CreditPreparationLookup
import com.tino.agent.contracts.CreditPlanInferencePort
import com.tino.agent.contracts.CreditPlanInferenceResult
import com.tino.agent.contracts.CustomerLookup
import com.tino.agent.contracts.ProductLookup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KoogCreditToolsTest {
    private val facts = FakeFacts()

    @Test
    fun registryIsAllowlistedAndHasNoCommitTool() {
        KoogCreditToolRegistry.create(facts)

        assertEquals(
            setOf("findCustomer", "findProduct", "getCustomerBalance", "prepareCreditSale"),
            KoogCreditToolRegistry.allowlistedNames,
        )
        assertFalse(KoogCreditToolRegistry.allowlistedNames.contains("commitCreditSale"))
    }

    @Test
    fun toolsReturnOnlyFactsProvidedByThePort() = runBlocking {
        val customer = FindCustomerTool(facts).execute(FindCustomerTool.Args("Dona Maria Lina"))
        val product = FindProductTool(facts).execute(FindProductTool.Args("café maratá"))
        val balance = GetCustomerBalanceTool(facts).execute(GetCustomerBalanceTool.Args("Maria Lina"))

        assertEquals("customer=Maria Lina", customer)
        assertEquals("product=Café Maratá;price_cents=850;stock=24", product)
        assertEquals("balance_cents=0", balance)
    }

    @Test
    fun prepareToolOnlyReturnsPreview() = runBlocking {
        val result = PrepareCreditSaleTool(facts).execute(
            PrepareCreditSaleTool.Args("Maria Lina", "Café Maratá", 1),
        )

        assertTrue(result.startsWith("PREVIEW"))
        assertTrue(result.contains("Café Maratá"))
        assertTrue(result.contains("R$ 8,50"))
        assertFalse(result.contains("COMMIT"))
        assertEquals(0, facts.commitCount)
    }

    @Test
    fun agentOrchestratesAllowlistedToolsToPreviewWithoutCommit() = runBlocking {
        val result = CreditPreparationAgent(facts).prepare(
            CreditPreparationRequest("Maria Lina", "Café Maratá", 1),
        )

        assertTrue(result is CreditPreparationAgentResult.Preview)
        val preview = result as CreditPreparationAgentResult.Preview
        assertEquals(
            listOf("findCustomer", "findProduct", "getCustomerBalance", "prepareCreditSale"),
            preview.trace.map { it.name },
        )
        assertTrue(preview.detail.contains("Café Maratá"))
        assertTrue(preview.detail.contains("R$ 8,50"))
        assertFalse(preview.trace.any { it.result.contains("COMMIT") })
        assertEquals(0, facts.commitCount)
    }

    @Test
    fun agentStopsBeforeMutationWhenEntityIsAmbiguous() = runBlocking {
        val ambiguousFacts = FakeFacts(
            productLookup = ProductLookup.Ambiguous(listOf("Café Maratá", "Café Marabá")),
        )
        val result = CreditPreparationAgent(ambiguousFacts).prepare(
            CreditPreparationRequest("Maria Lina", "Café", 1),
        )

        assertTrue(result is CreditPreparationAgentResult.Blocked)
        val blocked = result as CreditPreparationAgentResult.Blocked
        assertEquals("findProduct", blocked.stage)
        assertTrue(blocked.reason.startsWith("AMBIGUOUS product="))
        assertEquals(listOf("findCustomer", "findProduct"), blocked.trace.map { it.name })
        assertEquals(0, ambiguousFacts.commitCount)
    }

    @Test
    fun modelPlanFlowsIntoRealToolChainAndStopsAtPreview() = runBlocking {
        val inference = CreditPlanInferencePort {
            CreditPlanInferenceResult.Generated(
                """{"schema":"tino.credit-preparation-plan","schema_version":1,"capability":"ADD_CREDIT_ITEM","customer_ref":"Dona Maria Lina","product_ref":"Café Maratá","quantity":1}""",
            )
        }
        val flow = GemmaCreditPreparationFlow(
            planner = GemmaCreditPlanAdapter(inference),
            agent = CreditPreparationAgent(facts),
        )

        val result = flow.prepare("adicionar um café maratá na conta da Dona Maria Lina")

        assertTrue(result is CreditPreparationFlowResult.Preview, "result=$result")
        val preview = (result as CreditPreparationFlowResult.Preview).value
        assertTrue(preview.detail.contains("Café Maratá"))
        assertEquals(4, preview.trace.size)
        assertEquals(0, facts.commitCount)
    }

    @Test
    fun modelFactsAndUnknownFieldsAreRejectedBeforeTools() = runBlocking {
        val inference = CreditPlanInferencePort {
            CreditPlanInferenceResult.Generated(
                """{"schema":"tino.credit-preparation-plan","schema_version":1,"capability":"ADD_CREDIT_ITEM","customer_ref":"Maria Lina","product_ref":"Café Maratá","quantity":1,"price_cents":1}""",
            )
        }
        val planner = GemmaCreditPlanAdapter(inference)

        val result = planner.interpret("adicionar café na conta da Maria")

        assertTrue(result is CreditPlanResult.Rejected)
        assertTrue((result as CreditPlanResult.Rejected).reason.contains("plano"))
    }

    private class FakeFacts(
        private val productLookup: ProductLookup = ProductLookup.Resolved(
            "Café Maratá",
            850,
            24,
            CreditFactsTiming(productResolutionMs = 1),
        ),
    ) : CreditPreparationFactsPort {
        var commitCount = 0

        override suspend fun findCustomer(reference: String): CustomerLookup =
            if (reference.lowercase().contains("maria")) {
                CustomerLookup.Resolved("Maria Lina", CreditFactsTiming(customerResolutionMs = 1))
            } else {
                CustomerLookup.NotFound
            }

        override suspend fun findProduct(reference: String): ProductLookup =
            productLookup

        override suspend fun getCustomerBalance(reference: String): BalanceLookup =
            if (reference.lowercase().contains("maria")) {
                BalanceLookup.Resolved("Maria Lina", 0, CreditFactsTiming(balanceMs = 1))
            } else {
                BalanceLookup.NotFound
            }

        override suspend fun prepareCreditSale(
            customerReference: String,
            productReference: String,
            quantity: Int,
        ): CreditPreparationLookup {
            if (!customerReference.lowercase().contains("maria") || productReference != "Café Maratá") {
                return CreditPreparationLookup.NotFound("entity")
            }
            return CreditPreparationLookup.Preview(
                title = "Registrar venda fiada?",
                detail = "Maria Lina\n1 × Café Maratá · R$ 8,50",
                confirmLabel = "ANOTAR FIADO",
                timing = CreditFactsTiming(prepareMs = 1),
            )
        }
    }
}
