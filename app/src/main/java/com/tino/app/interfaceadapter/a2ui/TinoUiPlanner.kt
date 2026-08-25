package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.agent.CustomerListResult
import com.tino.app.domain.agent.FinancialSummaryResult
import com.tino.app.domain.agent.ProductListResult
import com.tino.app.domain.agent.ProductListItem
import com.tino.app.domain.agent.ReceivablesListResult
import com.tino.app.domain.agent.ReplenishmentResult
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Intent understood by the composition policy, not a new command language. */
enum class TinoUiIntent {
    REPLENISHMENT_QUERY,
    LIST_PRODUCTS,
    LIST_CUSTOMERS,
    WEEKLY_SALES,
    RECEIVABLES,
    UNKNOWN,
}

/** Device facts affect composition density, never domain truth. */
data class TinoUiPlannerContext(
    val intent: TinoUiIntent = TinoUiIntent.UNKNOWN,
    val widthDp: Int = 360,
    val fontScale: Float = 1f,
    val surfaceId: String = "tino-primary-surface",
    val chartSeries: String? = null,
    val chartLabels: String? = null,
) {
    val compact: Boolean get() = widthDp < 360 || fontScale > 1.2f
    val canShowMiniChart: Boolean get() = !compact && !chartSeries.isNullOrBlank()
}

/** Structured facts are the only input to composition; no raw Room objects cross this boundary. */
sealed interface TinoUiPlannerResult {
    data class Products(val value: ProductListResult) : TinoUiPlannerResult
    data class Replenishment(val value: ReplenishmentResult) : TinoUiPlannerResult
    data class Customers(val value: CustomerListResult) : TinoUiPlannerResult
    data class Receivables(val value: ReceivablesListResult) : TinoUiPlannerResult
    data class FinancialSummary(val value: FinancialSummaryResult) : TinoUiPlannerResult
    data class Unsupported(val reason: String, val requestedPattern: String) : TinoUiPlannerResult
}

sealed interface TinoA2UiTreeNode {
    data class Column(val children: List<TinoA2UiTreeNode>) : TinoA2UiTreeNode

    data class Text(val text: String) : TinoA2UiTreeNode

    data class CatalogComponent(
        val type: String,
        val props: Map<String, String> = emptyMap(),
        val actions: List<String> = emptyList(),
        val actionLabels: Map<String, String> = emptyMap(),
        val actionPayloads: Map<String, Map<String, String>> = emptyMap(),
    ) : TinoA2UiTreeNode

    data class Button(
        val label: String,
        val actionName: String,
        val payload: Map<String, String> = emptyMap(),
    ) : TinoA2UiTreeNode
}

data class TinoA2UiTree(val root: TinoA2UiTreeNode.Column) {
    fun toSurfaceMessage(surfaceId: String): A2uiSurfaceMessage = A2uiSurfaceMessage(
        messageId = UUID.randomUUID().toString(),
        surfaceId = surfaceId,
        operation = A2uiSurfaceOperation.CREATE_SURFACE,
        components = flatten(root).mapIndexed { index, node ->
            when (node) {
                is TinoA2UiTreeNode.Text -> A2uiSurfaceComponent(
                    componentId = "text-$index",
                    type = CoreTinoComponentCatalog.TEXT,
                    props = mapOf("text" to node.text),
                )
                is TinoA2UiTreeNode.CatalogComponent -> A2uiSurfaceComponent(
                    componentId = "catalog-$index",
                    type = node.type,
                    props = node.props,
                    actions = node.actions,
                    actionLabels = node.actionLabels,
                    actionPayloads = node.actionPayloads,
                )
                is TinoA2UiTreeNode.Button -> A2uiSurfaceComponent(
                    componentId = "button-$index",
                    type = CoreTinoComponentCatalog.BUTTON,
                    props = mapOf("label" to node.label),
                    actions = listOf(node.actionName),
                    actionLabels = mapOf(node.actionName to node.label),
                    actionPayloads = mapOf(node.actionName to node.payload),
                )
                is TinoA2UiTreeNode.Column -> error("Column deve ser achatado antes da emissão.")
            }
        },
    )

    private fun flatten(node: TinoA2UiTreeNode): List<TinoA2UiTreeNode> = when (node) {
        is TinoA2UiTreeNode.Column -> node.children.flatMap(::flatten)
        else -> listOf(node)
    }
}

data class CatalogCandidate(
    val patternId: String,
    val reason: String,
    val requestedPattern: String,
)

data class TinoUiPlan(
    val tree: TinoA2UiTree,
    val catalogVersion: String = TinoCatalogVersion.ID,
    val candidate: CatalogCandidate? = null,
)

interface TinoUiPlannerPort {
    suspend fun compose(result: TinoUiPlannerResult, context: TinoUiPlannerContext): TinoUiPlan
}

/**
 * Closed-world composition policy. It selects registered catalog pieces but
 * never creates a component type from user/model text.
 */
@Singleton
class TinoUiPlanner @Inject constructor() : TinoUiPlannerPort {
    override suspend fun compose(result: TinoUiPlannerResult, context: TinoUiPlannerContext): TinoUiPlan {
        val plan = when (result) {
            is TinoUiPlannerResult.Products -> products(result.value, context)
            is TinoUiPlannerResult.Replenishment -> replenishment(result.value, context)
            is TinoUiPlannerResult.Customers -> customers(result.value, context)
            is TinoUiPlannerResult.Receivables -> receivables(result.value, context)
            is TinoUiPlannerResult.FinancialSummary -> financialSummary(result.value, context)
            is TinoUiPlannerResult.Unsupported -> unsupported(result, context)
        }
        require(plan.tree.toSurfaceMessage(context.surfaceId).components.all { component ->
            TinoComponentCatalog.core.types.contains(component.type)
        }) { "O planner emitiu componente fora do catálogo." }
        return plan
    }

    private fun products(value: ProductListResult, context: TinoUiPlannerContext): TinoUiPlan =
        TinoUiPlan(
            tree = TinoA2UiTree(
                TinoA2UiTreeNode.Column(
                    buildList {
                        add(TinoA2UiTreeNode.Text("Produtos cadastrados"))
                        addAll(value.items.map(::productCard))
                        add(viewInventoryButton())
                        if (value.items.isEmpty()) add(TinoA2UiTreeNode.Text(value.emptyMessage ?: "Nenhum produto cadastrado."))
                    },
                ),
            ),
        )

    private fun replenishment(value: ReplenishmentResult, context: TinoUiPlannerContext): TinoUiPlan =
        TinoUiPlan(
            tree = TinoA2UiTree(
                TinoA2UiTreeNode.Column(
                    buildList {
                        add(TinoA2UiTreeNode.Text("Produtos para repor"))
                        addAll(value.items.map(::inventoryAlertCard))
                        if (value.items.isNotEmpty()) add(viewInventoryButton())
                        if (value.items.isEmpty()) add(TinoA2UiTreeNode.Text(value.emptyMessage ?: "Nenhum produto precisa de reposição."))
                    },
                ),
            ),
        )

    private fun customers(value: CustomerListResult, context: TinoUiPlannerContext): TinoUiPlan =
        TinoUiPlan(
            tree = TinoA2UiTree(
                TinoA2UiTreeNode.Column(
                    buildList {
                        add(TinoA2UiTreeNode.Text("Clientes cadastrados"))
                        addAll(value.items.map { item ->
                            TinoA2UiTreeNode.CatalogComponent(
                                type = TinoCustomComponentCatalog.CUSTOMER_CARD,
                                props = mapOf(
                                    "icon" to "customer",
                                    "title" to item.name,
                                    "context" to "Cadastro",
                                    "supportingText" to (item.phone?.takeIf { it.isNotBlank() } ?: "Sem telefone"),
                                    "status" to "NORMAL",
                                ),
                                actions = listOf("request_details"),
                                actionLabels = mapOf("request_details" to "Ver detalhes"),
                                actionPayloads = mapOf("request_details" to mapOf("entityId" to item.id)),
                            )
                        })
                        if (value.items.isEmpty()) add(TinoA2UiTreeNode.Text(value.emptyMessage ?: "Nenhum cliente cadastrado."))
                    },
                ),
            ),
        )

    private fun receivables(value: ReceivablesListResult, context: TinoUiPlannerContext): TinoUiPlan {
        val total = value.items.sumOf { it.balanceCents }
        return TinoUiPlan(
            tree = TinoA2UiTree(
                TinoA2UiTreeNode.Column(
                    buildList {
                        if (value.items.isNotEmpty()) {
                            add(
                                TinoA2UiTreeNode.CatalogComponent(
                                    type = TinoCustomComponentCatalog.SUMMARY_CARD,
                                    props = mapOf(
                                        "title" to "Quem está devendo",
                                        "salesValue" to "${value.items.size} clientes",
                                        "receivedValue" to money(total),
                                        "creditValue" to "Em aberto",
                                    ),
                                ),
                            )
                            addAll(value.items.map { item -> debtCard(item.customerId, item.customerName, item.balanceCents) })
                        } else {
                            add(TinoA2UiTreeNode.Text(value.emptyMessage ?: "Ninguém está devendo no momento."))
                        }
                    },
                ),
            ),
        )
    }

    private fun financialSummary(value: FinancialSummaryResult, context: TinoUiPlannerContext): TinoUiPlan =
        TinoUiPlan(
            tree = TinoA2UiTree(
                TinoA2UiTreeNode.Column(
                    buildList {
                        add(
                            TinoA2UiTreeNode.CatalogComponent(
                                type = TinoCustomComponentCatalog.METRIC_CARD,
                                props = mapOf(
                                    "icon" to "metric",
                                    "title" to if (context.intent == TinoUiIntent.WEEKLY_SALES) "Recebidos na semana" else "Recebidos",
                                    "value" to money(value.receivedTotalCents),
                                    "supportingText" to "Dados locais",
                                    "status" to "NORMAL",
                                ),
                            ),
                        )
                        if (context.canShowMiniChart) {
                            add(
                                TinoA2UiTreeNode.CatalogComponent(
                                    type = TinoCustomComponentCatalog.MINI_CHART,
                                    props = mapOf(
                                        "title" to "Tendência",
                                        "value" to money(value.receivedTotalCents),
                                        "series" to context.chartSeries.orEmpty(),
                                        "labels" to context.chartLabels.orEmpty(),
                                    ),
                                ),
                            )
                        }
                        add(TinoA2UiTreeNode.Text("Fonte: dados locais"))
                    },
                ),
            ),
        )

    private fun unsupported(result: TinoUiPlannerResult.Unsupported, context: TinoUiPlannerContext): TinoUiPlan =
        TinoUiPlan(
            tree = TinoA2UiTree(TinoA2UiTreeNode.Column(listOf(TinoA2UiTreeNode.Text(result.reason)))),
            candidate = CatalogCandidate(
                patternId = "candidate-${result.requestedPattern}",
                reason = result.reason,
                requestedPattern = result.requestedPattern,
            ),
        )

    private fun productCard(item: ProductListItem): TinoA2UiTreeNode.CatalogComponent =
        TinoA2UiTreeNode.CatalogComponent(
            type = TinoCustomComponentCatalog.PRODUCT_CARD,
            props = mapOf(
                "icon" to "inventory",
                "title" to item.name,
                "context" to "Estoque",
                "value" to stock(item),
                "supportingText" to stockStatus(item.stockQuantity),
                "status" to if (item.stockQuantity <= 0) "OUT_OF_STOCK" else "NORMAL",
            ),
            actions = listOf("request_details"),
            actionLabels = mapOf("request_details" to "Ver detalhes"),
            actionPayloads = mapOf("request_details" to mapOf("entityId" to item.id)),
        )

    private fun inventoryAlertCard(item: ProductListItem): TinoA2UiTreeNode.CatalogComponent =
        productCard(item).copy(
            type = TinoCustomComponentCatalog.INVENTORY_ALERT_CARD,
            props = productCard(item).props + ("supportingText" to stockStatus(item.stockQuantity)),
        )

    private fun debtCard(id: String, name: String, balanceCents: Long) =
        TinoA2UiTreeNode.CatalogComponent(
            type = TinoCustomComponentCatalog.DEBT_CARD,
            props = mapOf(
                "icon" to "customer",
                "title" to name,
                "context" to "Fiado",
                "value" to money(balanceCents),
                "supportingText" to "Em aberto",
                "status" to "OPEN",
            ),
            actions = listOf("request_details"),
            actionLabels = mapOf("request_details" to "Ver detalhes"),
            actionPayloads = mapOf("request_details" to mapOf("entityId" to id)),
        )

    private fun viewInventoryButton() = TinoA2UiTreeNode.Button(
        label = "Ver estoque",
        actionName = CoreTinoComponentCatalog.SELECT_TAB.name,
        payload = mapOf("tab" to "STOCK"),
    )

    private fun stock(item: ProductListItem): String =
        "${item.stockQuantity} ${item.unit}${if (item.stockQuantity == 1) "" else "s"}"

    private fun stockStatus(quantity: Int): String = when {
        quantity <= 0 -> "Estoque zerado"
        quantity <= 6 -> "Estoque baixo"
        else -> "Disponível"
    }

    private fun money(cents: Long): String =
        "R$ %.2f".format(Locale("pt", "BR"), cents / 100.0)
}
