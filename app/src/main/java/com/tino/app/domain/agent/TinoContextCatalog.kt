package com.tino.app.domain.agent

enum class TinoContextSection {
    SUGGESTED,
    QUERY,
    ACTION,
    HISTORY,
}

data class TinoContextualAction(
    val id: String,
    val title: String,
    val capability: AgentCapability? = null,
    val speak: Boolean = false,
    val navigateScreen: String? = null,
    val mutation: Boolean = false,
    val requiresEntity: Boolean = false,
    val section: TinoContextSection = TinoContextSection.QUERY,
    val priority: Int,
)

data class TinoContextCatalogResult(
    val screen: String,
    val title: String,
    val subtitle: String,
    val primary: List<TinoContextualAction>,
    val more: List<TinoContextualAction>,
)

/**
 * Closed catalog of what TINO can do here. It never invents capabilities;
 * it only ranks registered ones against screen and entity context.
 */
object TinoContextCatalog {
    const val PRIMARY_LIMIT = 3

    fun forContext(context: ScreenAgentContext): TinoContextCatalogResult {
        val entityName = context.primaryEntity?.text
        val screen = context.screen
        val available = context.availableCapabilities
        val candidates = actionsFor(screen, entityName)
            .filter { action ->
                if (action.speak || action.navigateScreen != null) return@filter true
                val required = action.capability?.toTinoCapabilityId()
                required == null || available.isEmpty() || required in available
            }
            .filter { action -> !action.requiresEntity || entityName != null }
            .sortedByDescending { boostedPriority(it, context.tags) }
        val primary = candidates.take(PRIMARY_LIMIT)
        val more = candidates.drop(PRIMARY_LIMIT)
        return TinoContextCatalogResult(
            screen = screen,
            title = titleFor(screen, entityName),
            subtitle = if (entityName != null) {
                "O que você quer ver ou fazer?"
            } else {
                "O que o TINO pode fazer aqui?"
            },
            primary = primary,
            more = more,
        )
    }

    private fun boostedPriority(action: TinoContextualAction, tags: Set<String>): Int {
        var boost = 0
        if ("LOW_STOCK" in tags && action.id == "low-stock") boost += 25
        if ("DEBTORS" in tags && action.id in setOf("receivables", "overdue", "payment")) boost += 25
        return action.priority + boost
    }

    private fun titleFor(screen: String, entityName: String?): String {
        if (entityName != null) return entityName
        return when (screen) {
            "Home" -> "Hoje"
            "Products", "ProductDetail", "StockEntry", "AdjustStock" -> "Estoque"
            "Customers", "CustomerDetail" -> "Clientes"
            "CreditList", "CustomerAccount", "ReceivePayment" -> "Caderneta"
            "Suppliers" -> "Fornecedores"
            "Orders", "OrderDetail", "NewOrder" -> "Pedidos"
            "More", "DailySummary", "Insights" -> "Mais"
            "FiscalFound", "FiscalReview", "DocumentCamera", "DocumentUpload" -> "Notas"
            else -> "TINO"
        }
    }

    private fun actionsFor(screen: String, entityName: String?): List<TinoContextualAction> {
        val named = entityName?.trim().orEmpty()
        return when (screen) {
            "Home" -> listOf(
                action("receivables", "Quem está devendo?", AgentCapability.LIST_RECEIVABLES, TinoContextSection.SUGGESTED, 90),
                action("today", "Quanto entrou hoje?", AgentCapability.READ_FINANCIAL_SUMMARY, TinoContextSection.QUERY, 85),
                action("low-stock", "O que está acabando?", AgentCapability.REPLENISHMENT_QUERY, TinoContextSection.SUGGESTED, 80),
                action("overdue", "Clientes atrasados", AgentCapability.LIST_OVERDUE, TinoContextSection.QUERY, 70),
                action("products", "Meus produtos", AgentCapability.LIST_PRODUCTS, TinoContextSection.QUERY, 65),
                action("customers", "Meus clientes", AgentCapability.LIST_CUSTOMERS, TinoContextSection.QUERY, 60),
            )
            "Products", "AdjustStock", "StockEntry" -> listOf(
                action("low-stock", "Estoque baixo", AgentCapability.REPLENISHMENT_QUERY, TinoContextSection.SUGGESTED, 90),
                action("products", "Todos os produtos", AgentCapability.LIST_PRODUCTS, TinoContextSection.QUERY, 80),
                action("stock", "Quanto tenho disto?", AgentCapability.GET_PRODUCT_STOCK, TinoContextSection.QUERY, 75, requiresEntity = true),
                action("price", "Qual o preço?", AgentCapability.GET_PRODUCT_PRICE, TinoContextSection.QUERY, 70, requiresEntity = true),
                action("entry", "Registrar entrada", AgentCapability.LIST_PRODUCTS, TinoContextSection.ACTION, 55, navigate = "StockEntry"),
                action("suppliers", "Fornecedores", AgentCapability.LIST_SUPPLIERS, TinoContextSection.QUERY, 50),
            )
            "ProductDetail" -> listOf(
                action("stock", "Quanto tenho disto?", AgentCapability.GET_PRODUCT_STOCK, TinoContextSection.SUGGESTED, 90, requiresEntity = true),
                action("price", "Qual o preço?", AgentCapability.GET_PRODUCT_PRICE, TinoContextSection.QUERY, 80, requiresEntity = true),
                action("low-stock", "O que mais está acabando?", AgentCapability.REPLENISHMENT_QUERY, TinoContextSection.QUERY, 70),
                action("entry", "Registrar entrada", AgentCapability.LIST_PRODUCTS, TinoContextSection.ACTION, 60, navigate = "StockEntry"),
            )
            "Customers" -> listOf(
                action("receivables", "Quem está devendo?", AgentCapability.LIST_RECEIVABLES, TinoContextSection.SUGGESTED, 90),
                action("payment", "Registrar pagamento", AgentCapability.REGISTER_CREDIT_PAYMENT, TinoContextSection.ACTION, 86, mutation = true),
                action("customers", "Buscar cliente", AgentCapability.LIST_CUSTOMERS, TinoContextSection.QUERY, 80),
                action("overdue", "Clientes atrasados", AgentCapability.LIST_OVERDUE, TinoContextSection.QUERY, 75),
                action("credit", "Registrar fiado", AgentCapability.ADD_CREDIT_ITEM, TinoContextSection.ACTION, 60, mutation = true),
                action("create", "Cadastrar cliente", AgentCapability.CREATE_CUSTOMER, TinoContextSection.ACTION, 50, mutation = true),
            )
            "CustomerDetail", "CustomerAccount", "ReceivePayment" -> listOf(
                action(
                    "balance",
                    if (named.isBlank()) "Quanto deve?" else "Quanto $named deve?",
                    AgentCapability.GET_CUSTOMER_BALANCE,
                    TinoContextSection.SUGGESTED,
                    95,
                    requiresEntity = true,
                ),
                action("timeline", "Ver histórico", AgentCapability.GET_CUSTOMER_TIMELINE, TinoContextSection.HISTORY, 85, requiresEntity = true),
                action("payment", "Registrar pagamento", AgentCapability.REGISTER_CREDIT_PAYMENT, TinoContextSection.ACTION, 80, mutation = true, requiresEntity = true),
                action("contact", "Ver telefone", AgentCapability.GET_CUSTOMER_CONTACT, TinoContextSection.QUERY, 70, requiresEntity = true),
                action("credit", "Registrar nova compra", AgentCapability.ADD_CREDIT_ITEM, TinoContextSection.ACTION, 65, mutation = true, requiresEntity = true),
                action("debtors", "Quem mais está devendo?", AgentCapability.LIST_RECEIVABLES, TinoContextSection.QUERY, 55),
            )
            "CreditList", "SelectCustomer", "ConfirmCredit" -> listOf(
                action("receivables", "Quem está devendo?", AgentCapability.LIST_RECEIVABLES, TinoContextSection.SUGGESTED, 95),
                action("overdue", "Atrasados", AgentCapability.LIST_OVERDUE, TinoContextSection.SUGGESTED, 85),
                action("payment", "Registrar pagamento", AgentCapability.REGISTER_CREDIT_PAYMENT, TinoContextSection.ACTION, 75, mutation = true),
                action("credit", "Registrar fiado", AgentCapability.ADD_CREDIT_ITEM, TinoContextSection.ACTION, 70, mutation = true),
                action("customers", "Ver clientes", AgentCapability.LIST_CUSTOMERS, TinoContextSection.QUERY, 60),
            )
            "Suppliers" -> listOf(
                action("suppliers", "Todos os fornecedores", AgentCapability.LIST_SUPPLIERS, TinoContextSection.SUGGESTED, 90),
                action("low-stock", "O que preciso repor?", AgentCapability.REPLENISHMENT_QUERY, TinoContextSection.QUERY, 70),
            )
            "More", "DailySummary", "Insights" -> listOf(
                action("today", "Resumo de hoje", AgentCapability.READ_FINANCIAL_SUMMARY, TinoContextSection.SUGGESTED, 90),
                action("receivables", "Quanto tenho a receber?", AgentCapability.LIST_RECEIVABLES, TinoContextSection.QUERY, 80),
                action("suppliers", "Fornecedores", AgentCapability.LIST_SUPPLIERS, TinoContextSection.QUERY, 70),
                action("low-stock", "Estoque baixo", AgentCapability.REPLENISHMENT_QUERY, TinoContextSection.QUERY, 65),
            )
            "FiscalFound", "FiscalReview", "DocumentCamera", "DocumentUpload" -> listOf(
                action("products", "Itens importados", AgentCapability.LIST_PRODUCTS, TinoContextSection.QUERY, 80),
                action("low-stock", "O que precisa repor?", AgentCapability.REPLENISHMENT_QUERY, TinoContextSection.QUERY, 70),
            )
            else -> listOf(
                action("today", "Resumo de hoje", AgentCapability.READ_FINANCIAL_SUMMARY, TinoContextSection.QUERY, 70),
                action("receivables", "Quem está devendo?", AgentCapability.LIST_RECEIVABLES, TinoContextSection.QUERY, 65),
            )
        }
    }

    private fun action(
        id: String,
        title: String,
        capability: AgentCapability,
        section: TinoContextSection,
        priority: Int,
        mutation: Boolean = false,
        requiresEntity: Boolean = false,
        navigate: String? = null,
    ) = TinoContextualAction(
        id = id,
        title = title,
        capability = if (navigate == null) capability else null,
        navigateScreen = navigate,
        mutation = mutation,
        requiresEntity = requiresEntity,
        section = section,
        priority = priority,
    )
}
