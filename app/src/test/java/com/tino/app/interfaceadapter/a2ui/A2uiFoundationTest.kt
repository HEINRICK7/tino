package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.agent.FinancialSummaryResult
import com.tino.app.domain.agent.AgentDataSource
import com.tino.app.domain.agent.AgentResponse
import com.tino.app.domain.agent.CustomerBalanceResult
import com.tino.app.domain.agent.CustomerListItem
import com.tino.app.domain.agent.CustomerListResult
import com.tino.app.domain.agent.CustomerContactResult
import com.tino.app.domain.agent.DbFirstReadResult
import com.tino.app.domain.agent.SupplierListItem
import com.tino.app.domain.agent.SupplierListResult
import com.tino.app.domain.agent.ProductListItem
import com.tino.app.domain.agent.ProductListResult
import com.tino.app.domain.agent.ReplenishmentResult
import com.tino.app.domain.agent.ReceivableItem
import com.tino.app.domain.agent.ReceivablesListResult
import com.tino.app.domain.agent.AgentCapability
import com.tino.app.domain.voice.ToolPreview
import com.tino.app.domain.voice.ToolPreviewPresentation
import com.tino.app.domain.finance.FinancialPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock

class A2uiFoundationTest {
    private val period = FinancialPeriod.today(
        Clock.fixed(
            java.time.Instant.parse("2026-08-17T15:00:00Z"),
            java.time.ZoneId.of("America/Fortaleza"),
        ),
    )

    @Test
    fun financialResultMapsToAllowlistedA2uiCard() {
        val message = FinancialSummaryA2uiMapper().map(result(received = 16_000))

        assertTrue(message.hasSupportedEnvelope)
        assertTrue(TinoA2UiComponentCatalog.isAllowed(message.component.type))
        val card = message.component as A2uiComponent.FinancialSummaryCard
        assertEquals("Entrou hoje", card.title)
        assertEquals("R$ 160,00", card.primaryValueText)
        assertEquals(listOf("R$ 100,00", "R$ 50,00", "R$ 0,00", "R$ 10,00"), card.metrics.map { it.valueText })
        assertEquals(null, card.emptyMessage)
    }

    @Test
    fun jsonRoundTripPreservesVersionedMessage() {
        val original = FinancialSummaryA2uiMapper().map(result(received = 16_000))

        val decoded = TinoA2UiJsonCodec.decode(TinoA2UiJsonCodec.encode(original))

        assertEquals(original, decoded)
        assertEquals(TinoA2UiProtocol.VERSION, decoded.version)
        assertEquals(TinoA2UiProtocol.SCHEMA, decoded.schema)
    }

    @Test
    fun intelligenceInsightRoundTripsAsGroundedInertData() {
        val original = IntelligenceA2uiMapper().map(
            com.tino.app.domain.intelligence.IntelligenceResponse(
                status = com.tino.app.domain.intelligence.IntelligenceResponseStatus.ANSWERED,
                answer = "Chico Filó está com R$ 62,50 em aberto.",
                factsUsed = listOf("customer_balances"),
                analyticsUsed = listOf("average_payment_delay_days"),
                knowledgeCatalogVersion = "v1",
                limitations = listOf("Histórico local do aparelho."),
            ),
        )

        val decoded = TinoA2UiJsonCodec.decode(TinoA2UiJsonCodec.encode(original))

        assertEquals(original, decoded)
        assertTrue(decoded.component is A2uiComponent.InsightCard)
        assertTrue((decoded.component as A2uiComponent.InsightCard).evidence.any { it.label == "Catálogo" && it.value == "v1" })
        assertTrue(TinoA2UiComponentCatalog.isAllowed(decoded.component.type))
    }

    @Test
    fun unknownComponentIsDataAndFailsClosed() {
        val decoded = TinoA2UiJsonCodec.decode(
            """
            {
              "schema": "tino.a2ui",
              "version": 1,
              "messageId": "untrusted",
              "component": {"type": "execute_arbitrary_code", "payload": "ignore"}
            }
            """.trimIndent(),
        )

        assertTrue(decoded.component is A2uiComponent.Unsupported)
        assertFalse(TinoA2UiComponentCatalog.isAllowed(decoded.component.type))
    }

    @Test
    fun unsupportedVersionIsRejectedByEnvelopeWithoutThrowing() {
        val decoded = TinoA2UiJsonCodec.decode(
            """
            {
              "schema": "tino.a2ui",
              "version": 99,
              "messageId": "future",
              "component": {
                "type": "financial_summary_card",
                "title": "Entrou hoje",
                "primaryLabel": "Recebido hoje",
                "primaryValueText": "R$ 1,00",
                "metrics": [],
                "dataSource": "LOCAL_ONLY"
              }
            }
            """.trimIndent(),
        )

        assertFalse(decoded.hasSupportedEnvelope)
        assertTrue(decoded.component is A2uiComponent.FinancialSummaryCard)
    }

    @Test
    fun emptyFinancialResultProducesExplicitZeroState() {
        val message = FinancialSummaryA2uiMapper().map(result(received = 0))
        val card = message.component as A2uiComponent.FinancialSummaryCard

        assertEquals("R$ 0,00", card.primaryValueText)
        assertEquals("Hoje ainda não entrou nada.", card.emptyMessage)
        assertEquals(4, card.metrics.size)
        assertTrue(card.metrics.all { it.valueText == "R$ 0,00" })
    }

    @Test
    fun unsupportedWireTypeCannotEnterAllowlist() {
        assertFalse(TinoA2UiComponentCatalog.isAllowed("script"))
        assertFalse(TinoA2UiComponentCatalog.isAllowed("navigation"))
        assertEquals(
            setOf(
                TinoA2UiComponentCatalog.FINANCIAL_SUMMARY_CARD,
                TinoA2UiComponentCatalog.ENTITY_CHOICE,
                TinoA2UiComponentCatalog.ACTION_CONFIRMATION,
                TinoA2UiComponentCatalog.OPERATION_SUCCESS,
                TinoA2UiComponentCatalog.UNDO_ACTION,
                TinoA2UiComponentCatalog.ERROR_RECOVERY,
                TinoA2UiComponentCatalog.PAYMENT_PREVIEW,
                TinoA2UiComponentCatalog.STOCK_ENTRY_PREVIEW,
                TinoA2UiComponentCatalog.PRICE_CHANGE_PREVIEW,
                TinoA2UiComponentCatalog.CREDIT_PREVIEW,
                TinoA2UiComponentCatalog.STOCK_STATUS,
                TinoA2UiComponentCatalog.SUPPLIER_SUMMARY,
                TinoA2UiComponentCatalog.CLARIFICATION_SELECTOR,
                TinoA2UiComponentCatalog.CUSTOMER_BALANCE_CARD,
                TinoA2UiComponentCatalog.CUSTOMER_TIMELINE_CARD,
                TinoA2UiComponentCatalog.PRODUCT_LIST,
                TinoA2UiComponentCatalog.PRODUCT_REPLENISHMENT,
                TinoA2UiComponentCatalog.PRODUCT_STOCK,
                TinoA2UiComponentCatalog.PRODUCT_PRICE,
                TinoA2UiComponentCatalog.CUSTOMER_LIST,
                TinoA2UiComponentCatalog.CUSTOMER_CONTACT,
                TinoA2UiComponentCatalog.RECEIVABLES_LIST,
                TinoA2UiComponentCatalog.OVERDUE_LIST,
                TinoA2UiComponentCatalog.INSIGHT_CARD,
            ),
            TinoA2UiComponentCatalog.allowlist,
        )
    }

    @Test
    fun entityChoiceIsVersionedAndRoundTripsOnlySafeLabels() {
        val original = EntityChoiceA2uiMapper().map(
            entityType = "customer",
            options = listOf("Maria Lina", "Maria Luiza", "Maria Lina"),
        )

        val decoded = TinoA2UiJsonCodec.decode(TinoA2UiJsonCodec.encode(original))

        assertEquals(original, decoded)
        val choice = decoded.component as A2uiComponent.EntityChoice
        assertEquals(listOf("Maria Lina", "Maria Luiza"), choice.options.map { it.label })
        assertTrue(TinoA2UiComponentCatalog.isAllowed(choice.type))
    }

    @Test
    fun customerBalanceMapsToExplicitZeroOrTemporalCard() {
        val message = CustomerBalanceA2uiMapper().map(
            AgentResponse.CustomerBalanceReady(
                capability = com.tino.app.domain.agent.AgentCapability.GET_CUSTOMER_BALANCE,
                result = CustomerBalanceResult(
                    customerName = "Maria Lina",
                    currentBalanceCents = 0,
                    openCents = 0,
                    overdueCents = 0,
                    oldestOpenDays = null,
                ),
                dataSource = AgentDataSource.LOCAL_ONLY,
                customerResolutionMs = 1,
            ),
        )

        val decoded = TinoA2UiJsonCodec.decode(TinoA2UiJsonCodec.encode(message))
        val card = decoded.component as A2uiComponent.CustomerBalanceCard
        assertEquals("Maria Lina", card.customerName)
        assertEquals("R$ 0,00", card.currentBalanceText)
        assertEquals("Este cliente não tem saldo em aberto.", card.emptyMessage)
        assertEquals("LOCAL_ONLY", card.dataSource)
    }

    @Test
    fun dbFirstProductsMapToAllowlistedCardWithRealValues() {
        val message = DbFirstReadA2uiMapper().map(
            AgentResponse.ReadListReady(
                capability = AgentCapability.LIST_PRODUCTS,
                result = DbFirstReadResult.Products(
                    ProductListResult(
                        items = listOf(ProductListItem("p1", "Café Maratá", 850, 24, "unidade")),
                    ),
                ),
                dataSource = AgentDataSource.LOCAL_ONLY,
            ),
        )

        val decoded = TinoA2UiJsonCodec.decode(TinoA2UiJsonCodec.encode(message))
        val card = decoded.component as A2uiComponent.ReadListCard
        assertEquals(TinoA2UiComponentCatalog.PRODUCT_LIST, card.type)
        assertEquals("Produtos cadastrados", card.title)
        assertEquals("Café Maratá", card.items.single().title)
        assertEquals("24 unidades", card.items.single().primaryText)
        assertEquals("R$ 8,50", card.items.single().secondaryText)
        assertEquals("Estoque", card.items.single().context)
        assertEquals("Disponível", card.items.single().supportingText)
        assertEquals(A2uiVisualStatus.SUCCESS, card.items.single().status)
        assertEquals("inventory", card.items.single().iconKey)
        assertEquals("LOCAL_ONLY", card.dataSource)
    }

    @Test
    fun replenishmentResultMapsToAttentionProductList() {
        val message = DbFirstReadA2uiMapper().map(
            AgentResponse.ReadListReady(
                capability = AgentCapability.REPLENISHMENT_QUERY,
                result = DbFirstReadResult.Replenishment(
                    ReplenishmentResult(
                        items = listOf(ProductListItem("p1", "Café Maratá", 1250, 0, "unidade")),
                    ),
                ),
                dataSource = AgentDataSource.LOCAL_ONLY,
            ),
        )

        val card = message.component as A2uiComponent.ReadListCard
        assertEquals(TinoA2UiComponentCatalog.PRODUCT_REPLENISHMENT, card.type)
        assertEquals("Produtos para repor", card.title)
        assertEquals("0 unidades", card.items.single().primaryText)
        assertEquals("R$ 12,50 · REPOR", card.items.single().secondaryText)
        assertEquals("Estoque zerado", card.items.single().supportingText)
        assertEquals(A2uiVisualStatus.WARNING, card.items.single().status)
        assertEquals("inventory", card.items.single().iconKey)
    }

    @Test
    fun customersMapToInformationalEntityCardsWithoutDuplicatingPhone() {
        val message = DbFirstReadA2uiMapper().map(
            AgentResponse.ReadListReady(
                capability = AgentCapability.LIST_CUSTOMERS,
                result = DbFirstReadResult.Customers(
                    CustomerListResult(
                        items = listOf(CustomerListItem("c1", "Maria Lina", "86994209350")),
                    ),
                ),
                dataSource = AgentDataSource.LOCAL_ONLY,
            ),
        )

        val card = message.component as A2uiComponent.ReadListCard
        val item = card.items.single()
        assertEquals(TinoA2UiComponentCatalog.CUSTOMER_LIST, card.type)
        assertEquals(A2uiVisualStatus.INFO, item.status)
        assertEquals("86994209350", item.secondaryText)
        assertEquals(null, item.supportingText)
        assertEquals("c1", item.actionId)
    }

    @Test
    fun suppliersMapToInformationalSupplierCardsWithoutInventingContactData() {
        val message = DbFirstReadA2uiMapper().map(
            AgentResponse.ReadListReady(
                capability = AgentCapability.LIST_SUPPLIERS,
                result = DbFirstReadResult.Suppliers(
                    SupplierListResult(
                        items = listOf(SupplierListItem("s1", "Distribuidora Central", null)),
                    ),
                ),
                dataSource = AgentDataSource.LOCAL_ONLY,
            ),
        )

        val card = message.component as A2uiComponent.ReadListCard
        val item = card.items.single()
        assertEquals(TinoA2UiComponentCatalog.SUPPLIER_SUMMARY, card.type)
        assertEquals("Distribuidora Central", item.title)
        assertEquals("Fornecedor", item.primaryText)
        assertEquals("Sem telefone", item.supportingText)
        assertEquals(null, item.secondaryText)
        assertEquals("s1", item.actionId)
    }

    @Test
    fun customerContactMapsPhoneAndMissingPhoneWithoutInventingData() {
        val withPhone = DbFirstReadA2uiMapper().map(
            AgentResponse.ReadListReady(
                capability = AgentCapability.GET_CUSTOMER_CONTACT,
                result = DbFirstReadResult.CustomerContact(
                    CustomerContactResult("c1", "Maria Lina", "86994209350"),
                ),
                dataSource = AgentDataSource.LOCAL_ONLY,
            ),
        ).component as A2uiComponent.ReadListCard

        assertEquals(TinoA2UiComponentCatalog.CUSTOMER_CONTACT, withPhone.type)
        assertEquals("86994209350", withPhone.items.single().primaryText)
        assertEquals("Telefone cadastrado", withPhone.items.single().supportingText)

        val withoutPhone = DbFirstReadA2uiMapper().map(
            AgentResponse.ReadListReady(
                capability = AgentCapability.GET_CUSTOMER_CONTACT,
                result = DbFirstReadResult.CustomerContact(
                    CustomerContactResult("c2", "João", null),
                ),
                dataSource = AgentDataSource.LOCAL_ONLY,
            ),
        ).component as A2uiComponent.ReadListCard

        assertEquals("Sem telefone", withoutPhone.items.single().primaryText)
        assertEquals("Nenhum telefone cadastrado", withoutPhone.items.single().supportingText)
    }

    @Test
    fun customerContactRoundTripsThroughTheVersionedCodec() {
        val original = DbFirstReadA2uiMapper().map(
            AgentResponse.ReadListReady(
                capability = AgentCapability.GET_CUSTOMER_CONTACT,
                result = DbFirstReadResult.CustomerContact(
                    CustomerContactResult("c1", "Maria Lina", "86994209350"),
                ),
                dataSource = AgentDataSource.LOCAL_ONLY,
            ),
        )

        assertEquals(original, TinoA2UiJsonCodec.decode(TinoA2UiJsonCodec.encode(original)))
    }

    @Test
    fun receivablesMapToCreditEntityCards() {
        val message = DbFirstReadA2uiMapper().map(
            AgentResponse.ReadListReady(
                capability = AgentCapability.LIST_RECEIVABLES,
                result = DbFirstReadResult.Receivables(
                    ReceivablesListResult(
                        items = listOf(ReceivableItem("c1", "Maria Lina", 2_985)),
                    ),
                ),
                dataSource = AgentDataSource.LOCAL_ONLY,
            ),
        )

        val card = message.component as A2uiComponent.ReadListCard
        val item = card.items.single()
        assertEquals(TinoA2UiComponentCatalog.RECEIVABLES_LIST, card.type)
        assertEquals(A2uiVisualStatus.CREDIT, item.status)
        assertEquals("credit", item.iconKey)
        assertEquals("R$ 29,85", item.primaryText)
        assertEquals("c1", item.actionId)
    }

    @Test
    fun semanticListItemFieldsSurviveA2uiRoundTrip() {
        val original = A2uiMessage(
            messageId = "semantic-list",
            component = A2uiComponent.ReadListCard(
                title = "Produtos para repor",
                items = listOf(
                    A2uiListItem(
                        title = "Café Maratá",
                        primaryText = "0 unidades",
                        secondaryText = "R$ 12,50",
                        context = "Estoque",
                        supportingText = "Estoque zerado",
                        status = A2uiVisualStatus.WARNING,
                        iconKey = "inventory",
                        actionId = "open_product:p1",
                    ),
                ),
                emptyMessage = null,
                dataSource = "LOCAL_ONLY",
                type = TinoA2UiComponentCatalog.PRODUCT_REPLENISHMENT,
            ),
        )

        assertEquals(original.component, TinoA2UiJsonCodec.decode(TinoA2UiJsonCodec.encode(original)).component)
    }

    @Test
    fun paymentPreviewUsesStructuredTinoLanguageAndKeepsMoneyOnOneLine() {
        val message = CommerceActionA2uiMapper().preview(
            ToolPreview(
                title = "Registrar pagamento?",
                detail = "debug detail is not rendered as the normal layout",
                presentation = ToolPreviewPresentation.Payment(
                    customerName = "Maria Lina",
                    amountText = "R$ 50,00",
                    methodLabel = "PIX",
                    currentBalanceText = "R$ 152,50",
                    projectedBalanceText = "R$ 102,50",
                ),
            ),
        )

        val component = message.component as A2uiComponent.ActionConfirmation
        assertEquals("Pagamento", component.title)
        assertEquals("Maria Lina", component.entityName)
        assertEquals("R$ 50,00", component.primaryValueText)
        assertEquals(listOf("PIX", "R$ 152,50 → R$ 102,50"), component.detailRows.map { it.value })
        assertEquals(component, TinoA2UiJsonCodec.decode(TinoA2UiJsonCodec.encode(message)).component)
    }

    @Test
    fun presentationPolicyKeepsSmallQueriesCompactAndRichListsInBottomSheet() {
        assertEquals(
            com.tino.app.domain.agent.TinoPresentationMode.OVERLAY,
            A2uiPresentationPolicy.forComponent(TinoA2UiComponentCatalog.CUSTOMER_BALANCE_CARD),
        )
        assertEquals(
            com.tino.app.domain.agent.TinoPresentationMode.BOTTOM_SHEET,
            A2uiPresentationPolicy.forComponent(TinoA2UiComponentCatalog.CUSTOMER_LIST, itemCount = 4),
        )
    }

    private fun result(received: Long) = FinancialSummaryResult(
        period = period,
        receivedTotalCents = received,
        receivedCashCents = if (received == 0L) 0 else 10_000,
        receivedPixCents = if (received == 0L) 0 else 5_000,
        receivedCardCents = 0,
        receivedUnknownCents = if (received == 0L) 0 else 1_000,
        totalReceivableCents = 22_000,
        creditCreatedCents = 0,
        creditPaymentsReceivedCents = 0,
    )
}
