package com.tino.app.domain.agent

import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FastIntentResult {
    data class Match(
        val tool: TinoToolId,
        val intent: AgentIntent,
    ) : FastIntentResult

    data object NoMatch : FastIntentResult
}

enum class FastNavigationTarget {
    QUICK_SALE,
    CUSTOMERS,
    PRODUCTS,
    CREDIT_LIST,
    STOCK_ENTRY,
}

fun FastNavigationTarget.requiredCapability(): TinoCapabilityId = when (this) {
    FastNavigationTarget.QUICK_SALE -> TinoCapabilityId.NAVIGATE
    FastNavigationTarget.CUSTOMERS -> TinoCapabilityId.LIST_CUSTOMERS
    FastNavigationTarget.PRODUCTS -> TinoCapabilityId.LIST_PRODUCTS
    FastNavigationTarget.CREDIT_LIST -> TinoCapabilityId.LIST_RECEIVABLES
    FastNavigationTarget.STOCK_ENTRY -> TinoCapabilityId.REGISTER_STOCK_ENTRY
}

@Singleton
class FastIntentRouter @Inject constructor() {
    private val commandRouter = CommandIntentRouter()

    fun route(input: String): FastIntentResult {
        val normalized = normalize(input)
        if (normalized.isBlank()) return FastIntentResult.NoMatch

        replenishment(normalized)?.let { return it }
        productList(normalized)?.let { return it }
        productFact(normalized)?.let { return it }
            customerList(normalized)?.let { return it }
        supplierList(normalized)?.let { return it }
        receivables(normalized)?.let { return it }
        overdue(normalized)?.let { return it }
        financialSummary(normalized)?.let { return it }
        customerTimeline(normalized)?.let { return it }
        customerContact(normalized)?.let { return it }
        customerBalance(normalized)?.let { return it }
        return FastIntentResult.NoMatch
    }

    /** Deterministic navigation commands never need the agent or a data query. */
    fun navigationTarget(input: String): FastNavigationTarget? {
        val text = normalize(input)
        return when {
            text in setOf("vender", "abrir venda", "nova venda", "abrir vendas") ->
                FastNavigationTarget.QUICK_SALE
            text in setOf(
                "clientes", "cliente", "abrir clientes", "abre clientes",
                "ir para clientes", "ir aos clientes",
            ) -> FastNavigationTarget.CUSTOMERS
            text in setOf(
                "estoque", "produtos", "abrir estoque", "abrir produtos",
                "ir para estoque", "ir aos produtos",
            ) -> FastNavigationTarget.PRODUCTS
            text in setOf(
                "fiado", "caderneta", "abrir fiado", "abrir caderneta",
                "ir para fiado", "ir para caderneta",
            ) -> FastNavigationTarget.CREDIT_LIST
            text in setOf("abrir entrada", "abrir mercadoria", "ir para entrada") ->
                FastNavigationTarget.STOCK_ENTRY
            else -> null
        }
    }

    /** Human-facing status for the short interval between final transcript and card. */
    fun contextLabel(input: String): String {
        when (navigationTarget(input)) {
            FastNavigationTarget.QUICK_SALE -> return "Abrindo venda…"
            FastNavigationTarget.CUSTOMERS -> return "Abrindo clientes…"
            FastNavigationTarget.PRODUCTS -> return "Abrindo estoque…"
            FastNavigationTarget.CREDIT_LIST -> return "Abrindo fiado…"
            FastNavigationTarget.STOCK_ENTRY -> return "Abrindo entrada…"
            null -> Unit
        }
        val result = route(input)
        return when (result) {
            is FastIntentResult.Match -> when (result.intent.capability) {
            AgentCapability.GLOBAL_TOOL -> "Organizando a operação…"
            AgentCapability.LIST_PRODUCTS -> "Consultando produtos…"
            AgentCapability.REPLENISHMENT_QUERY -> "Verificando reposição…"
            AgentCapability.GET_PRODUCT_STOCK -> "Consultando estoque…"
            AgentCapability.GET_PRODUCT_PRICE -> "Consultando preço…"
            AgentCapability.LIST_CUSTOMERS -> "Consultando clientes…"
            AgentCapability.LIST_SUPPLIERS -> "Consultando fornecedores…"
            AgentCapability.LIST_RECEIVABLES,
            AgentCapability.LIST_OVERDUE,
            AgentCapability.GET_CUSTOMER_BALANCE,
            AgentCapability.GET_CUSTOMER_TIMELINE,
            -> "Consultando a caderneta…"
            AgentCapability.GET_CUSTOMER_CONTACT -> "Consultando contato…"
            AgentCapability.READ_FINANCIAL_SUMMARY -> "Consultando recebimentos…"
            AgentCapability.ADD_CREDIT_ITEM -> "Preparando o fiado…"
            AgentCapability.REGISTER_CREDIT_PAYMENT -> "Preparando a baixa do fiado…"
            AgentCapability.CREATE_CUSTOMER -> "Preparando o cadastro…"
            AgentCapability.UPDATE_PRODUCT_PRICE -> "Preparando a alteração de preço…"
            AgentCapability.REGISTER_STOCK_ENTRY -> "Preparando a entrada…"
        }
            FastIntentResult.NoMatch -> when (val command = commandRouter.route(input)) {
                is CommandIntentResult.Match -> when (command.intent.capability) {
                    AgentCapability.REGISTER_CREDIT_PAYMENT -> "Preparando a baixa do fiado…"
                    else -> "Preparando o fiado…"
                }
                CommandIntentResult.NoMatch -> "Consultando seus dados…"
            }
        }
    }

    private fun productList(text: String): FastIntentResult.Match? {
        val matches = containsAny(
            text,
            "quais produtos",
            "produtos cadastrados",
            "produtos temos",
            "listar produtos",
            "lista de produtos",
            "lista meus produtos",
            "listar meus produtos",
            "me mostra meus produtos",
            "me mostre meus produtos",
            "todos os produtos",
            "todos produtos",
            "lista meu estoque",
            "listar meu estoque",
            "me mostra meu estoque",
            "me mostre meu estoque",
            "mostrar estoque",
            "o que tenho cadastrado",
            "o que tenho no estoque",
        ) || text in setOf("estoque", "produtos") || (
            text.contains("quantos produtos") &&
                (text.contains("estoque") || text.contains("tenho") || text.contains("cadastrado"))
        )
        if (!matches || text.contains("quanto custa") || text.contains("qual o preco") ||
            text.contains("qual e o preco")) {
            return null
        }
        return match(TinoToolId.LIST_PRODUCTS, AgentCapability.LIST_PRODUCTS)
    }

    private fun replenishment(text: String): FastIntentResult.Match? {
        val asksForPurchase = containsAny(
            text,
            "tenho que comprar",
            "preciso comprar",
            "o que preciso comprar",
            "preciso repor",
            "tenho que repor",
            "produtos para comprar",
            "produtos pra comprar",
            "produtos para repor",
            "produtos pra repor",
            "o que acabou",
            "o que esta acabando",
            "o que esta zerado",
            "o que esta faltando",
            "reposição",
            "reposicao",
        ) || (text.contains("produtos") && containsAny(text, "acabando", "zerados", "faltando"))
        if (!asksForPurchase) return null
        return match(TinoToolId.REPLENISHMENT_QUERY, AgentCapability.REPLENISHMENT_QUERY)
    }

    private fun productFact(text: String): FastIntentResult.Match? {
        if (text.contains("receber") || text.contains("recebido") || text.contains("entrou")) return null
        val asksStock = text.contains("quanto") &&
            (text.contains("tenho") || text.contains("tem") || text.contains("estoque") || text.contains("unidades"))
        val asksPrice = text.contains("quanto custa") || text.contains("qual o preco") ||
            text.contains("qual e o preco") || text.contains("valor do") || text.contains("valor da")
        val capability = when {
            asksStock -> AgentCapability.GET_PRODUCT_STOCK
            asksPrice -> AgentCapability.GET_PRODUCT_PRICE
            else -> return null
        }
        val reference = extractProduct(text) ?: return null
        return match(
            tool = if (capability == AgentCapability.GET_PRODUCT_STOCK) TinoToolId.PRODUCT_STOCK else TinoToolId.PRODUCT_PRICE,
            capability = capability,
            productRef = reference,
        )
    }

    private fun customerList(text: String): FastIntentResult.Match? {
        val matches = containsAny(
            text,
            "quais clientes",
            "clientes cadastrados",
            "quais clientes eu tenho",
            "me mostra os clientes",
            "me mostre os clientes",
            "quero ver meus clientes",
            "ver meus clientes",
            "lista meus clientes",
            "listar meus clientes",
            "listar clientes",
            "lista de clientes",
        ) || text in setOf("clientes", "cliente") || (
            text.contains("cliente") && containsAny(
                text,
                "todos",
                "lista",
                "listar",
                "mostra",
                "mostre",
                "quais",
            )
        )
        if (!matches) return null
        return match(TinoToolId.LIST_CUSTOMERS, AgentCapability.LIST_CUSTOMERS)
    }

    private fun receivables(text: String): FastIntentResult.Match? {
        val matches = text.contains("quem esta me devendo") ||
            text.contains("quem me deve") ||
            text.contains("clientes devendo") ||
            text.contains("quem esta no fiado")
        if (!matches) return null
        return match(TinoToolId.LIST_RECEIVABLES, AgentCapability.LIST_RECEIVABLES)
    }

    private fun supplierList(text: String): FastIntentResult.Match? {
        val matches = containsAny(
            text,
            "quais fornecedores",
            "fornecedores cadastrados",
            "quais fornecedores eu tenho",
            "me mostra os fornecedores",
            "me mostre os fornecedores",
            "quero ver meus fornecedores",
            "ver meus fornecedores",
            "lista meus fornecedores",
            "listar meus fornecedores",
            "listar fornecedores",
            "lista de fornecedores",
        ) || text in setOf("fornecedores", "fornecedor") || (
            text.contains("fornecedor") && containsAny(
                text,
                "todos",
                "lista",
                "listar",
                "mostra",
                "mostre",
                "quais",
            )
        )
        if (!matches) return null
        return match(TinoToolId.LIST_SUPPLIERS, AgentCapability.LIST_SUPPLIERS)
    }

    private fun overdue(text: String): FastIntentResult.Match? {
        val matches = text.contains("quem esta atrasado") ||
            text.contains("quem esta vencido") ||
            text.contains("fiados vencidos") ||
            (text.contains("fiados") && text.contains("vencidos")) ||
            text.contains("contas vencidas")
        if (!matches) return null
        return match(TinoToolId.LIST_OVERDUE, AgentCapability.LIST_OVERDUE)
    }

    private fun extractProduct(text: String): String? {
        val reference = text
            .replace(Regex("^quanto custa\\s+"), "")
            .replace(Regex("^qual e o preco (do|da)\\s+"), "")
            .replace(Regex("^qual o preco (do|da)\\s+"), "")
            .replace(Regex("^quanto\\s+"), "")
            .replace(Regex("\\b(tenho|tem|em|estoque|unidades|unidade|custa|valor|do|da|de|o|a)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix("?")
            .trim()
        return reference.takeIf { it.isNotBlank() }
    }

    private fun match(
        tool: TinoToolId,
        capability: AgentCapability,
        productRef: String? = null,
    ) = FastIntentResult.Match(
        tool = tool,
        intent = AgentIntent(
            schemaVersion = AgentIntentSchema.VERSION,
            capability = capability,
            period = AgentIntentPeriod.TODAY,
            productRef = productRef,
        ),
    )

    private fun financialSummary(text: String): FastIntentResult.Match? {
        val asksForReceivable = text.contains("receber") &&
            (text.contains("quanto") || text.contains("o que") || text.contains("qual"))
        val asksForReceived = text.contains("quanto") && containsAny(
            text,
            "entrou",
            "recebi",
            "vendi",
            "foi recebido",
        )
        val asksForFinancialOverview = (
            containsAny(text, "financeiro", "movimento", "vendas") &&
                containsAny(text, "como", "qual", "quanto", "mostra", "resumo")
            ) || (text.contains("resumo") && isToday(text)) ||
            (isToday(text) && containsAny(
                text,
                "como foi hoje",
                "como foi meu dia",
                "como recebi hoje",
                "vendas de hoje",
                "movimento de hoje",
                "recebimentos de hoje",
                "meu resumo",
            ))
        if (!asksForReceivable && !asksForReceived && !asksForFinancialOverview) return null
        if (!isToday(text) && !asksForReceivable) return null

        val mentionsPix = text.contains("pix")
        val mentionsCash = text.contains("dinheiro")
        val mentionsCard = text.contains("maquininha") || text.contains("cartao")
        val method = when {
            listOf(mentionsPix, mentionsCash, mentionsCard).count { it } > 1 -> FinancialPaymentMethod.ALL
            mentionsPix -> FinancialPaymentMethod.PIX
            mentionsCash -> FinancialPaymentMethod.CASH
            mentionsCard -> FinancialPaymentMethod.CARD
            else -> FinancialPaymentMethod.ALL
        }
        val metric = when {
            asksForReceived && asksForReceivable -> FinancialMetric.SUMMARY
            asksForReceivable -> FinancialMetric.RECEIVABLE
            else -> FinancialMetric.RECEIVED
        }
        return FastIntentResult.Match(
            tool = TinoToolId.FINANCIAL_SUMMARY,
            intent = AgentIntent(
                schemaVersion = AgentIntentSchema.VERSION,
                capability = AgentCapability.READ_FINANCIAL_SUMMARY,
                period = AgentIntentPeriod.TODAY,
                paymentMethod = method,
                metric = metric,
            ),
        )
    }

    private fun customerBalance(text: String): FastIntentResult.Match? {
        if (!(text.contains("deve") || text.contains("devendo") || text.contains("saldo"))) return null
        if (text.contains("quanto tenho") || text.contains("quanto entrou")) return null
        val reference = extractCustomer(text) ?: return null
        return FastIntentResult.Match(
            tool = TinoToolId.CUSTOMER_BALANCE,
            intent = AgentIntent(
                schemaVersion = AgentIntentSchema.VERSION,
                capability = AgentCapability.GET_CUSTOMER_BALANCE,
                period = AgentIntentPeriod.TODAY,
                customerRef = reference,
            ),
        )
    }

    private fun customerTimeline(text: String): FastIntentResult.Match? {
        val marker = when {
            text.startsWith("mostra a conta ") -> "mostra a conta "
            text.startsWith("mostra a caderneta ") -> "mostra a caderneta "
            text.startsWith("historico da ") -> "historico da "
            text.startsWith("historico de ") -> "historico de "
            else -> return null
        }
        val reference = text.removePrefix(marker).trim().removeSuffix("?").trim()
            .removePrefix("da ").removePrefix("de ").removePrefix("dona ").removePrefix("don ").trim()
            .takeIf { it.isNotBlank() } ?: return null
        return FastIntentResult.Match(
            tool = TinoToolId.CUSTOMER_TIMELINE,
            intent = AgentIntent(
                schemaVersion = AgentIntentSchema.VERSION,
                capability = AgentCapability.GET_CUSTOMER_TIMELINE,
                period = AgentIntentPeriod.TODAY,
                customerRef = reference,
            ),
        )
    }

    private fun customerContact(text: String): FastIntentResult.Match? {
        if (!(text.contains("telefone") || text.contains("contato"))) return null
        val marker = when {
            text.startsWith("qual e o telefone ") -> "qual e o telefone "
            text.startsWith("qual o telefone ") -> "qual o telefone "
            text.startsWith("qual e o contato ") -> "qual e o contato "
            text.startsWith("qual o contato ") -> "qual o contato "
            text.startsWith("me passa o telefone ") -> "me passa o telefone "
            text.startsWith("me mostre o telefone ") -> "me mostre o telefone "
            text.startsWith("telefone ") -> "telefone "
            text.startsWith("contato ") -> "contato "
            else -> return null
        }
        val reference = text.removePrefix(marker).trim()
            .removePrefix("da ").removePrefix("do ").removePrefix("de ")
            .removePrefix("dona ").removePrefix("don ").trim()
            .removeSuffix("?").trim()
            .takeIf { it.isNotBlank() } ?: return null
        return FastIntentResult.Match(
            tool = TinoToolId.CUSTOMER_CONTACT,
            intent = AgentIntent(
                schemaVersion = AgentIntentSchema.VERSION,
                capability = AgentCapability.GET_CUSTOMER_CONTACT,
                period = AgentIntentPeriod.TODAY,
                customerRef = reference,
            ),
        )
    }

    private fun extractCustomer(text: String): String? {
        val reference = text
            .replace(Regex("^quanto\\s+"), "")
            .replace(Regex("^qual e o saldo\\s+"), "")
            .replace(Regex("^saldo (do|da|de)\\s+"), "")
            .replace(Regex("\\b(esta|está|devendo|deve|fiado|tem|a|o|do|da|de|cliente)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix("?")
            .trim()
            .removePrefix("dona ").removePrefix("don ").trim()
        return reference.takeIf { it.isNotBlank() }
    }

    private fun isToday(text: String): Boolean =
        text.contains("hoje") || text.contains(" hj") || text.endsWith("hj") ||
            (!text.contains("ontem") && !text.contains("semana") && !text.contains("mes"))

    private fun normalize(input: String): String = Normalizer
        .normalize(input.lowercase(), Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .replace("pi x", "pix")
        .replace("piquis", "pix")
        .replace("\\bhj\\b".toRegex(), "hoje")
        .replace("[^a-z0-9?]+".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

    private fun containsAny(text: String, vararg aliases: String): Boolean =
        aliases.any(text::contains)
}
