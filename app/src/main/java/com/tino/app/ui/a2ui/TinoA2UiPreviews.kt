package com.tino.app.ui.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.tino.app.domain.agent.FinancialSummaryResult
import com.tino.app.domain.finance.FinancialPeriod
import com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper
import com.tino.app.interfaceadapter.a2ui.A2uiComponent
import com.tino.app.interfaceadapter.a2ui.A2uiDetailRow
import com.tino.app.interfaceadapter.a2ui.A2uiListItem
import com.tino.app.interfaceadapter.a2ui.A2uiMessage
import com.tino.app.interfaceadapter.a2ui.A2uiVisualStatus
import com.tino.app.interfaceadapter.a2ui.TinoA2UiComponentCatalog
import com.tino.app.ui.theme.TinoTheme
import com.tino.app.ui.theme.TinoSpacing
import java.time.Clock

@Preview(name = "A2UI — Financial summary", showBackground = true, widthDp = 412, heightDp = 520)
@Composable
private fun PreviewFinancialSummaryA2ui() {
    TinoTheme {
        TinoA2UiRenderer(
            FinancialSummaryA2uiMapper().map(
                FinancialSummaryResult(
                    period = FinancialPeriod.today(Clock.systemDefaultZone()),
                    receivedTotalCents = 162_750,
                    receivedCashCents = 58_000,
                    receivedPixCents = 64_750,
                    receivedCardCents = 40_000,
                    receivedUnknownCents = 0,
                    totalReceivableCents = 22_000,
                    creditCreatedCents = 0,
                    creditPaymentsReceivedCents = 0,
                ),
            ),
        )
    }
}

@Preview(name = "A2UI — Empty summary", showBackground = true, widthDp = 412, heightDp = 420)
@Composable
private fun PreviewEmptyFinancialSummaryA2ui() {
    TinoTheme {
        TinoA2UiRenderer(
            FinancialSummaryA2uiMapper().map(
                FinancialSummaryResult(
                    period = FinancialPeriod.today(Clock.systemDefaultZone()),
                    receivedTotalCents = 0,
                    receivedCashCents = 0,
                    receivedPixCents = 0,
                    receivedCardCents = 0,
                    receivedUnknownCents = 0,
                    totalReceivableCents = 0,
                    creditCreatedCents = 0,
                    creditPaymentsReceivedCents = 0,
                ),
            ),
        )
    }
}

@Preview(name = "A2UI — Payment preview", showBackground = true, widthDp = 412, heightDp = 420)
@Composable
private fun PreviewPaymentA2ui() {
    TinoTheme {
        TinoA2UiRenderer(
            A2uiMessage(
                messageId = "preview-payment",
                component = A2uiComponent.ActionConfirmation(
                    title = "Pagamento",
                    detail = "",
                    confirmLabel = "Confirmar",
                    complete = false,
                    semanticType = TinoA2UiComponentCatalog.PAYMENT_PREVIEW,
                    entityName = "Maria Lina",
                    primaryValueText = "R$ 50,00",
                    detailRows = listOf(
                        A2uiDetailRow("Método", "PIX"),
                        A2uiDetailRow("Saldo", "R$ 152,50 → R$ 102,50"),
                    ),
                    iconKey = "payment",
                ),
            ),
        )
    }
}

@Preview(name = "A2UI — Credit preview large text", showBackground = true, widthDp = 412, heightDp = 500, fontScale = 1.3f)
@Composable
private fun PreviewCreditA2uiLargeText() {
    TinoTheme {
        TinoA2UiRenderer(
            A2uiMessage(
                messageId = "preview-credit",
                component = A2uiComponent.ActionConfirmation(
                    title = "Fiado",
                    detail = "",
                    confirmLabel = "Confirmar",
                    complete = false,
                    semanticType = TinoA2UiComponentCatalog.CREDIT_PREVIEW,
                    entityName = "Maria Lina",
                    primaryValueText = "R$ 9.999,99",
                    detailRows = listOf(
                        A2uiDetailRow("Café Maratá", "24 ×"),
                        A2uiDetailRow("Açúcar Refinado 1kg", "3 ×"),
                        A2uiDetailRow("Novo saldo", "R$ 99.999,99"),
                    ),
                    iconKey = "credit",
                ),
            ),
        )
    }
}

@Preview(name = "TINO Catalog v1 — gallery", showBackground = true, widthDp = 412, heightDp = 760, fontScale = 1.15f)
@Composable
private fun PreviewTinoCatalogV1Gallery() {
    TinoTheme {
        Column(
            modifier = androidx.compose.ui.Modifier.padding(TinoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            TinoA2UiRenderer(
                A2uiMessage(
                    messageId = "catalog-product",
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
                            ),
                        ),
                        emptyMessage = null,
                        dataSource = "LOCAL_ONLY",
                        type = TinoA2UiComponentCatalog.PRODUCT_REPLENISHMENT,
                    ),
                ),
            )
            TinoA2UiRenderer(
                A2uiMessage(
                    messageId = "catalog-customer",
                    component = A2uiComponent.ReadListCard(
                        title = "Clientes cadastrados",
                        items = listOf(
                            A2uiListItem(
                                title = "Maria Lina",
                                primaryText = "Cliente",
                                secondaryText = null,
                                context = "Cadastro",
                                supportingText = "Sem telefone",
                                iconKey = "customer",
                            ),
                        ),
                        emptyMessage = null,
                        dataSource = "LOCAL_ONLY",
                        type = TinoA2UiComponentCatalog.CUSTOMER_LIST,
                    ),
                ),
            )
            TinoA2UiRenderer(
                A2uiMessage(
                    messageId = "catalog-status",
                    component = A2uiComponent.ErrorStatusCard(
                        title = "Não consegui concluir",
                        message = "Tente novamente em alguns segundos.",
                    ),
                ),
            )
        }
    }
}
