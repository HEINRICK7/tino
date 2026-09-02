package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.agent.AgentCapability
import com.tino.app.domain.agent.AgentDataSource
import com.tino.app.domain.agent.AgentResponse
import com.tino.app.domain.agent.DbFirstReadResult
import com.tino.app.domain.agent.ProductListItem
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DbFirstReadA2uiMapper @Inject constructor() {
    fun map(response: AgentResponse.ReadListReady): A2uiMessage {
        val (type, title, items, emptyMessage, source) = when (val result = response.result) {
            is DbFirstReadResult.Products -> Quad(
                TinoA2UiComponentCatalog.PRODUCT_LIST,
                "Produtos cadastrados",
                result.value.items.map { it.toProductItem() },
                result.value.emptyMessage,
                result.value.dataSource,
            )
            is DbFirstReadResult.Replenishment -> Quad(
                TinoA2UiComponentCatalog.PRODUCT_REPLENISHMENT,
                "Produtos para repor",
                result.value.items.map { item ->
                    A2uiListItem(
                        title = item.name,
                        primaryText = "${item.stockQuantity} ${item.unit}${if (item.stockQuantity == 1) "" else "s"}",
                        secondaryText = "${formatCents(item.priceCents)} · REPOR",
                        context = "Estoque",
                        supportingText = item.stockSupportingText(),
                        status = A2uiVisualStatus.WARNING,
                        iconKey = "inventory",
                    )
                },
                result.value.emptyMessage,
                result.value.dataSource,
            )
            is DbFirstReadResult.ProductFact -> {
                val item = result.value.product
                val type = when (response.capability) {
                    AgentCapability.GET_PRODUCT_STOCK -> TinoA2UiComponentCatalog.PRODUCT_STOCK
                    AgentCapability.GET_PRODUCT_PRICE -> TinoA2UiComponentCatalog.PRODUCT_PRICE
                    AgentCapability.REPLENISHMENT_QUERY -> TinoA2UiComponentCatalog.PRODUCT_STOCK
                    AgentCapability.LIST_PRODUCTS -> TinoA2UiComponentCatalog.PRODUCT_STOCK
                    else -> TinoA2UiComponentCatalog.PRODUCT_LIST
                }
                Quad(
                    type,
                    "Produto",
                    listOf(
                        item.toProductItem(
                            primaryText = when (response.capability) {
                                AgentCapability.GET_PRODUCT_PRICE -> formatCents(item.priceCents)
                                else -> item.stockText()
                            },
                            secondaryText = when (response.capability) {
                                AgentCapability.GET_PRODUCT_PRICE -> item.stockText()
                                else -> formatCents(item.priceCents)
                            },
                            context = when (response.capability) {
                                AgentCapability.GET_PRODUCT_PRICE -> "Preço"
                                else -> "Estoque"
                            },
                        ),
                    ),
                    null,
                    result.value.dataSource,
                )
            }
            is DbFirstReadResult.Customers -> Quad(
                TinoA2UiComponentCatalog.CUSTOMER_LIST,
                "Clientes cadastrados",
                result.value.items.map { item ->
                    A2uiListItem(
                        title = item.name,
                        primaryText = "Cliente",
                        secondaryText = item.phone?.takeIf { it.isNotBlank() },
                        context = "Cadastro",
                        supportingText = if (item.phone.isNullOrBlank()) "Sem telefone" else null,
                        status = A2uiVisualStatus.INFO,
                        iconKey = "customer",
                        actionId = item.id,
                    )
                },
                result.value.emptyMessage,
                result.value.dataSource,
            )
            is DbFirstReadResult.CustomerContact -> Quad(
                TinoA2UiComponentCatalog.CUSTOMER_CONTACT,
                "Contato do cliente",
                listOf(
                    A2uiListItem(
                        title = result.value.customerName,
                        primaryText = result.value.phone?.takeIf { it.isNotBlank() } ?: "Sem telefone",
                        secondaryText = null,
                        context = "Cliente",
                        supportingText = if (result.value.phone.isNullOrBlank()) {
                            "Nenhum telefone cadastrado"
                        } else {
                            "Telefone cadastrado"
                        },
                        status = A2uiVisualStatus.INFO,
                        iconKey = "customer",
                        actionId = result.value.customerId,
                    ),
                ),
                null,
                result.value.dataSource,
            )
            is DbFirstReadResult.Suppliers -> Quad(
                TinoA2UiComponentCatalog.SUPPLIER_SUMMARY,
                "Fornecedores cadastrados",
                result.value.items.map { item ->
                    A2uiListItem(
                        title = item.name,
                        primaryText = "Fornecedor",
                        secondaryText = item.phone?.takeIf { it.isNotBlank() },
                        context = "Cadastro",
                        supportingText = if (item.phone.isNullOrBlank()) "Sem telefone" else null,
                        status = A2uiVisualStatus.INFO,
                        iconKey = "supplier",
                        actionId = item.id,
                    )
                },
                result.value.emptyMessage,
                result.value.dataSource,
            )
            is DbFirstReadResult.Receivables -> Quad(
                TinoA2UiComponentCatalog.RECEIVABLES_LIST,
                "Quem está devendo",
                result.value.items.map { item ->
                    A2uiListItem(
                        title = item.customerName,
                        primaryText = formatCents(item.balanceCents),
                        secondaryText = null,
                        context = "Fiado",
                        supportingText = "Em aberto",
                        status = A2uiVisualStatus.CREDIT,
                        iconKey = "credit",
                        actionId = item.customerId,
                    )
                },
                result.value.emptyMessage,
                result.value.dataSource,
            )
            is DbFirstReadResult.Overdue -> Quad(
                TinoA2UiComponentCatalog.OVERDUE_LIST,
                "Fiados vencidos",
                result.value.items.map { item ->
                    A2uiListItem(
                        item.customerName,
                        formatCents(item.balanceCents),
                        null,
                        context = "Fiado",
                        supportingText = "Atrasado há ${item.daysOverdue} dias",
                        status = A2uiVisualStatus.ERROR,
                        iconKey = "credit",
                        actionId = item.customerId,
                    )
                },
                result.value.emptyMessage,
                result.value.dataSource,
            )
            is DbFirstReadResult.Ambiguous,
            is DbFirstReadResult.NotFound,
            -> error("Resultado DB-first sem surface: $result")
        }
        return A2uiMessage(
            messageId = UUID.randomUUID().toString(),
            component = A2uiComponent.ReadListCard(
                title = title,
                items = items,
                emptyMessage = emptyMessage,
                dataSource = source.name,
                type = type,
            ),
        )
    }

    private fun ProductListItem.toProductItem(
        primaryText: String = stockText(),
        secondaryText: String? = formatCents(priceCents),
        context: String = "Estoque",
    ): A2uiListItem = A2uiListItem(
        title = name,
        primaryText = primaryText,
        secondaryText = secondaryText,
        context = context,
        supportingText = stockSupportingText(),
        status = if (!stockTracked || stockQuantity > 0) A2uiVisualStatus.SUCCESS else A2uiVisualStatus.WARNING,
        iconKey = "inventory",
    )

    private fun ProductListItem.stockText(): String =
        if (stockTracked) "$stockQuantity $unit${if (stockQuantity == 1) "" else "s"}" else "Feito sob demanda"

    private fun ProductListItem.stockSupportingText(): String = when {
        !stockTracked -> "Sem controle de estoque"
        stockQuantity <= 0 -> "Estoque zerado"
        stockQuantity <= 6 -> "Estoque baixo"
        else -> "Disponível"
    }

    private fun formatCents(cents: Long): String =
        "R$ %.2f".format(Locale("pt", "BR"), cents / 100.0)

    private data class Quad(
        val type: String,
        val title: String,
        val items: List<A2uiListItem>,
        val emptyMessage: String?,
        val source: AgentDataSource,
    )
}
