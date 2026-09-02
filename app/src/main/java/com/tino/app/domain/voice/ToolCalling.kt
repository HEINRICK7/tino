package com.tino.app.domain.voice

import com.tino.app.domain.commerce.CommerceRepository
import com.tino.app.domain.commerce.CommerceRules
import com.tino.app.domain.commerce.EntityResolutionMatch
import com.tino.app.domain.commerce.EntityResolutionService
import com.tino.app.domain.commerce.NoopAuditLogger
import com.tino.app.domain.commerce.PaymentMethod
import com.tino.app.core.common.UuidV7
import com.tino.app.domain.usecase.RegisterCreditPaymentCommand
import com.tino.app.domain.usecase.RegisterCreditPaymentUseCase
import com.tino.app.domain.usecase.CreateCustomerCommand
import com.tino.app.domain.usecase.CreateCustomerUseCase
import com.tino.app.domain.usecase.UpdateProductPriceCommand
import com.tino.app.domain.usecase.UpdateProductPriceUseCase
import com.tino.app.domain.usecase.RegisterStockEntryCommand
import com.tino.app.domain.usecase.RegisterStockEntryUseCase
import javax.inject.Inject
import javax.inject.Singleton

enum class CommerceToolName {
    SEARCH_PRODUCT,
    SEARCH_CUSTOMER,
    REGISTER_SALE,
    REGISTER_CREDIT_SALE,
    ADD_CREDIT_ITEM,
    REGISTER_STOCK_RECEIPT,
    CHANGE_PRODUCT_PRICE,
    CHECK_STOCK,
    GET_CUSTOMER_BALANCE,
    REGISTER_CREDIT_PAYMENT,
    CORRECT_CREDIT_PAYMENT,
    GET_TODAY_SALES,
    PREPARE_PURCHASE,
    FIND_SUPPLIER,
    CREATE_CUSTOMER,
}

val CommerceToolName.isReadOnly: Boolean
    get() = when (this) {
        CommerceToolName.SEARCH_PRODUCT,
        CommerceToolName.SEARCH_CUSTOMER,
        CommerceToolName.CHECK_STOCK,
        CommerceToolName.GET_CUSTOMER_BALANCE,
        CommerceToolName.GET_TODAY_SALES,
        CommerceToolName.FIND_SUPPLIER,
        -> true
        CommerceToolName.REGISTER_SALE,
        CommerceToolName.REGISTER_CREDIT_SALE,
        CommerceToolName.ADD_CREDIT_ITEM,
        CommerceToolName.REGISTER_STOCK_RECEIPT,
        CommerceToolName.REGISTER_CREDIT_PAYMENT,
        CommerceToolName.CORRECT_CREDIT_PAYMENT,
        CommerceToolName.PREPARE_PURCHASE,
        CommerceToolName.CHANGE_PRODUCT_PRICE,
        CommerceToolName.CREATE_CUSTOMER,
        -> false
    }

data class ToolCall(
    val name: CommerceToolName,
    val arguments: Map<String, String>,
)

data class ToolPreview(
    val title: String,
    val detail: String,
    val confirmLabel: String = "CONFIRMAR AÇÃO",
    val diagnostics: ToolPreviewDiagnostics? = null,
    val presentation: ToolPreviewPresentation? = null,
    val preparedMutation: PreparedMutation? = null,
)

/** Canonical facts for the A2UI mapper; the renderer never parses free-form detail text. */
sealed interface ToolPreviewPresentation {
    data class Payment(
        val customerName: String,
        val amountText: String,
        val methodLabel: String,
        val currentBalanceText: String,
        val projectedBalanceText: String,
    ) : ToolPreviewPresentation

    data class Credit(
        val customerName: String,
        val lines: List<CreditLine>,
        val totalText: String,
        val projectedBalanceText: String,
    ) : ToolPreviewPresentation

    data class CreditLine(
        val quantityText: String,
        val productName: String? = null,
    )

    data class StockEntry(
        val productName: String,
        val quantityText: String,
        val unitCostText: String,
        val supplierName: String? = null,
    ) : ToolPreviewPresentation

    data class PriceChange(
        val productName: String,
        val oldPriceText: String,
        val newPriceText: String,
    ) : ToolPreviewPresentation
}

data class ToolPreviewDiagnostics(
    val customerResolutionMs: Long? = null,
    val productResolutionMs: Long? = null,
    val capabilityMs: Long? = null,
)

data class ToolUndoMetadata(
    val compensatingCapability: String,
    val deadlineEpochMs: Long? = null,
)

data class ToolExecutionResult(
    val message: String,
    val title: String = "TINO ENCONTROU",
    val operationId: String? = null,
    val undo: ToolUndoMetadata? = null,
    val presentation: ToolResultPresentation? = null,
    val compensatesActivityId: String? = null,
)

sealed interface ToolResultPresentation {
    data class Payment(
        val customerName: String,
        val amountText: String,
        val methodLabel: String,
        val amountCents: Long? = null,
        val methodStorageValue: String? = null,
    ) : ToolResultPresentation
}

class ToolClarificationException(
    message: String,
    val argumentKey: String? = null,
    val options: List<String> = emptyList(),
) : IllegalArgumentException(message)

interface ToolExecutor {
    suspend fun preview(call: ToolCall): ToolPreview
    suspend fun execute(call: ToolCall, confirmed: Boolean): ToolExecutionResult

    /** The only confirmation input allowed to a mutation-capable adapter. */
    suspend fun confirm(
        call: ToolCall,
        confirmation: MutationConfirmation?,
    ): ToolExecutionResult = execute(call, confirmed = true)
}

/**
 * Single mutation gate for the application. Read-only calls retain the existing
 * path; every mutation must carry the exact token issued with its preview.
 */
@Singleton
class MutationSafeToolExecutor(
    private val delegate: ToolExecutor,
    private val safety: MutationSafetyPort,
) : ToolExecutor {
    override suspend fun preview(call: ToolCall): ToolPreview {
        val preview = delegate.preview(call)
        if (call.name.isReadOnly) return preview
        return preview.copy(preparedMutation = safety.prepare(call, preview))
    }

    override suspend fun execute(call: ToolCall, confirmed: Boolean): ToolExecutionResult {
        if (!call.name.isReadOnly) {
            error("Mutation só pode ser executada por confirm(call, token).")
        }
        return delegate.execute(call, confirmed = true)
    }

    override suspend fun confirm(
        call: ToolCall,
        confirmation: MutationConfirmation?,
    ): ToolExecutionResult {
        if (call.name.isReadOnly) return delegate.execute(call, confirmed = true)
        val currentPreview = delegate.preview(call)
        return when (val authorization = safety.authorize(call, confirmation, currentPreview)) {
            is MutationAuthorization.Denied -> error(authorization.reason)
            is MutationAuthorization.Allowed -> {
                try {
                    val result = delegate.execute(call, confirmed = true)
                    safety.commit(authorization.operation)
                    result
                } catch (error: Exception) {
                    safety.release(authorization.operation)
                    throw error
                }
            }
        }
    }
}

@Singleton
class CommerceToolDispatcher @Inject constructor(
    private val commerceRepository: CommerceRepository,
    private val entityResolver: EntityResolutionService,
    private val registerCreditPayment: RegisterCreditPaymentUseCase,
    private val createCustomer: CreateCustomerUseCase,
    private val updateProductPrice: UpdateProductPriceUseCase,
    private val registerStockEntry: RegisterStockEntryUseCase,
) : ToolExecutor {
    constructor(commerceRepository: CommerceRepository) : this(
        commerceRepository,
        EntityResolutionService(commerceRepository, NoopAuditLogger),
        RegisterCreditPaymentUseCase(commerceRepository),
        CreateCustomerUseCase(commerceRepository),
        UpdateProductPriceUseCase(commerceRepository),
        RegisterStockEntryUseCase(commerceRepository),
    )
    constructor(
        commerceRepository: CommerceRepository,
        entityResolver: EntityResolutionService,
    ) : this(
        commerceRepository,
        entityResolver,
        RegisterCreditPaymentUseCase(commerceRepository),
        CreateCustomerUseCase(commerceRepository),
        UpdateProductPriceUseCase(commerceRepository),
        RegisterStockEntryUseCase(commerceRepository),
    )
    override suspend fun preview(call: ToolCall): ToolPreview = when (call.name) {
        CommerceToolName.SEARCH_PRODUCT -> {
            val product = productFor(call.required("product"))
            ToolPreview("Consultar produto?", "${product.name} · ${formatCents(product.priceCents)}")
        }
        CommerceToolName.SEARCH_CUSTOMER -> {
            val customer = customerFor(call.required("customer"))
            ToolPreview("Consultar cliente?", customer.name)
        }
        CommerceToolName.CHECK_STOCK -> {
            val product = productFor(call.required("product"))
            ToolPreview("Consultar estoque?", "${product.name} · ${commerceRepository.stockBalance(product.id)} unidades")
        }
        CommerceToolName.REGISTER_CREDIT_SALE,
        CommerceToolName.ADD_CREDIT_ITEM,
        -> {
            val capabilityStartedAt = System.nanoTime()
            val productStartedAt = System.nanoTime()
            val product = productFor(call.required("product"))
            val productResolutionMs = elapsedMs(productStartedAt)
            val customerStartedAt = System.nanoTime()
            val customer = customerFor(call.required("customer"))
            val customerResolutionMs = elapsedMs(customerStartedAt)
            val quantity = call.required("quantity").toInt()
            val availableStock = commerceRepository.stockBalance(product.id)
            val totalCents = CommerceRules.saleTotal(
                unitPriceCents = product.priceCents,
                quantity = quantity,
                availableStock = availableStock,
                productName = product.name,
            )
            val currentBalance = commerceRepository.customerBalance(customer.id)
            ToolPreview(
                title = "Registrar venda fiada?",
                detail = listOf(
                    customer.name,
                    "$quantity × ${product.name} · ${formatCents(totalCents)}",
                    "Fiado atual: ${formatCents(currentBalance)}",
                    "Depois: ${formatCents(currentBalance + totalCents)}",
                    "Estoque depois: ${availableStock - quantity}",
                ).joinToString("\n"),
                confirmLabel = "ANOTAR FIADO",
                diagnostics = ToolPreviewDiagnostics(
                    customerResolutionMs = customerResolutionMs,
                    productResolutionMs = productResolutionMs,
                    capabilityMs = elapsedMs(capabilityStartedAt),
                ),
                presentation = ToolPreviewPresentation.Credit(
                    customerName = customer.name,
                    lines = listOf(
                        ToolPreviewPresentation.CreditLine(
                            quantityText = "$quantity ×",
                            productName = product.name,
                        ),
                    ),
                    totalText = formatCents(totalCents),
                    projectedBalanceText = formatCents(currentBalance + totalCents),
                ),
            )
        }
        CommerceToolName.REGISTER_SALE -> {
            val product = productFor(call.required("product"))
            val quantity = call.arguments["quantity"]?.toIntOrNull()?.takeIf { it > 0 } ?: 1
            val paymentMethod = call.arguments["payment_method"]?.let(::paymentMethodFor)
            ToolPreview(
                "Registrar venda?",
                listOf(
                    "$quantity × ${product.name} · ${formatCents(product.priceCents * quantity)}",
                    paymentMethod?.let { "Via: ${it.label}" },
                ).filterNotNull().joinToString("\n"),
                "RECEBER VENDA",
            )
        }
        CommerceToolName.REGISTER_CREDIT_PAYMENT -> {
            val customer = customerFor(call.required("customer"))
            val paymentMethod = paymentMethodFor(call.arguments["payment_method"])
            val amountCents = call.required("amount_cents").toLong()
            val currentBalance = commerceRepository.customerBalance(customer.id)
            ToolPreview(
                "Registrar pagamento do fiado?",
                listOf(
                    customer.name,
                    "Pagamento: ${formatCents(amountCents)}",
                    "Via: ${paymentMethod.label}",
                    "Saldo atual: ${formatCents(currentBalance)}",
                    "Depois: ${formatCents(currentBalance - amountCents)}",
                ).joinToString("\n"),
                "RECEBER PAGAMENTO",
                presentation = ToolPreviewPresentation.Payment(
                    customerName = customer.name,
                    amountText = formatCents(amountCents),
                    methodLabel = paymentMethod.label,
                    currentBalanceText = formatCents(currentBalance),
                    projectedBalanceText = formatCents(currentBalance - amountCents),
                ),
            )
        }
        CommerceToolName.CREATE_CUSTOMER -> {
            val name = call.required("name")
            val phone = call.arguments["phone"]?.takeIf { it.isNotBlank() }
            ToolPreview(
                title = "Cadastrar cliente?",
                detail = listOf(
                    name,
                    phone?.let { "Telefone: $it" },
                ).filterNotNull().joinToString("\n"),
                confirmLabel = "CADASTRAR CLIENTE",
            )
        }
        CommerceToolName.CORRECT_CREDIT_PAYMENT -> {
            val originalOperationId = call.required("original_operation_id")
            val original = commerceRepository.findCreditEntryByOperation(originalOperationId)
                ?: error("Pagamento original não encontrado.")
            val customer = commerceRepository.findCustomerById(original.customerId)
                ?: error("Cliente do pagamento não encontrado.")
            val amountCents = call.required("amount_cents").toLong()
            val method = paymentMethodFor(call.arguments["payment_method"])
            val currentBalance = commerceRepository.customerBalance(customer.id)
            ToolPreview(
                "Corrigir pagamento?",
                listOf(
                    customer.name,
                    "Registrado: ${formatCents(-original.amountCents)}",
                    "Correto: ${formatCents(amountCents)}",
                    "Via: ${method.label}",
                    "Saldo depois: ${formatCents(currentBalance - amountCents - original.amountCents)}",
                ).joinToString("\n"),
                "CORRIGIR PAGAMENTO",
                presentation = ToolPreviewPresentation.Payment(
                    customerName = customer.name,
                    amountText = formatCents(amountCents),
                    methodLabel = method.label,
                    currentBalanceText = formatCents(currentBalance),
                    projectedBalanceText = formatCents(currentBalance - amountCents - original.amountCents),
                ),
            )
        }
        CommerceToolName.REGISTER_STOCK_RECEIPT -> {
            val product = productFor(call.required("product"))
            val quantity = call.required("quantity").toInt()
            require(quantity > 0) { "A quantidade precisa ser maior que zero." }
            val unitCostCents = call.required("unit_cost_cents").toLong()
            require(unitCostCents >= 0) { "O custo não pode ser negativo." }
            val currentStock = commerceRepository.stockBalance(product.id)
            val supplier = call.arguments["supplier"]?.takeIf { it.isNotBlank() }?.let {
                supplierFor(it)
            }
            ToolPreview(
                "Registrar entrada?",
                listOf(
                    product.name,
                    "$quantity unidades · custo ${formatCents(unitCostCents)}",
                    "Estoque atual: $currentStock",
                    "Depois: ${currentStock + quantity}",
                    supplier?.let { "Fornecedor: ${it.name}" },
                ).filterNotNull().joinToString("\n"),
                "CONFIRMAR ENTRADA",
                presentation = ToolPreviewPresentation.StockEntry(
                    productName = product.name,
                    quantityText = "$quantity un",
                    unitCostText = formatCents(unitCostCents),
                    supplierName = supplier?.name,
                ),
            )
        }
        CommerceToolName.CHANGE_PRODUCT_PRICE -> {
            val product = productFor(call.required("product"))
            val newPriceCents = call.required("new_price_cents").toLong()
            ToolPreview(
                "Alterar preço?",
                "${product.name} · ${formatCents(product.priceCents)} → ${formatCents(newPriceCents)}",
                "ALTERAR PREÇO",
                presentation = ToolPreviewPresentation.PriceChange(
                    productName = product.name,
                    oldPriceText = formatCents(product.priceCents),
                    newPriceText = formatCents(newPriceCents),
                ),
            )
        }
        CommerceToolName.GET_CUSTOMER_BALANCE -> {
            val customer = customerFor(call.required("customer"))
            ToolPreview("Consultar fiado?", "Saldo de ${customer.name}: ${formatCents(commerceRepository.customerBalance(customer.id))}")
        }
        CommerceToolName.GET_TODAY_SALES ->
            ToolPreview("Consultar vendas de hoje?", formatCents(commerceRepository.todayTotalCents()))
        CommerceToolName.PREPARE_PURCHASE ->
            ToolPreview("Preparar lista de compras?", "Vou reunir os produtos que precisam de reposição.")
        CommerceToolName.FIND_SUPPLIER -> {
            val supplier = supplierFor(call.required("supplier"))
            ToolPreview("Consultar fornecedor?", supplier.name)
        }
    }

    override suspend fun execute(call: ToolCall, confirmed: Boolean): ToolExecutionResult {
        check(confirmed) { "A operação precisa de confirmação humana." }
        return when (call.name) {
            CommerceToolName.SEARCH_PRODUCT -> {
                val product = productFor(call.required("product"))
                ToolExecutionResult("${product.name}: ${formatCents(product.priceCents)}", "Produto encontrado")
            }
            CommerceToolName.SEARCH_CUSTOMER -> {
                val customer = customerFor(call.required("customer"))
                ToolExecutionResult(customer.name, "Cliente encontrado")
            }
            CommerceToolName.CHECK_STOCK -> {
                val product = productFor(call.required("product"))
                ToolExecutionResult(
                    "${product.name}: ${commerceRepository.stockBalance(product.id)} unidades",
                    "Estoque",
                )
            }
            CommerceToolName.REGISTER_CREDIT_SALE,
            CommerceToolName.ADD_CREDIT_ITEM,
            -> {
                val customer = customerFor(call.required("customer"))
                val product = productFor(call.required("product"))
                commerceRepository.registerCreditSale(
                    customerId = customer.id,
                    productId = product.id,
                    quantity = call.required("quantity").toInt(),
                )
                ToolExecutionResult(
                    message = "Fiado registrado para ${customer.name}.",
                    title = "Fiado registrado",
                )
            }
            CommerceToolName.REGISTER_SALE -> {
                val product = productFor(call.required("product"))
                commerceRepository.registerSale(
                    product.id,
                    call.arguments["quantity"]?.toIntOrNull()?.takeIf { it > 0 } ?: 1,
                    call.arguments["payment_method"]?.let(::paymentMethodFor) ?: PaymentMethod.CASH,
                )
                ToolExecutionResult("Venda registrada.")
            }
            CommerceToolName.REGISTER_STOCK_RECEIPT -> {
                val product = productFor(call.required("product"))
                val supplier = call.arguments["supplier"]?.takeIf { it.isNotBlank() }
                    ?.let { supplierFor(it) }
                registerStockEntry(
                    RegisterStockEntryCommand(
                        productId = product.id,
                        quantity = call.required("quantity").toInt(),
                        unitCostCents = call.required("unit_cost_cents").toLong(),
                        supplierId = supplier?.id,
                    ),
                )
                ToolExecutionResult(
                    message = "Entrada de mercadoria registrada.",
                    title = "Entrada registrada",
                )
            }
            CommerceToolName.CHANGE_PRODUCT_PRICE -> {
                val product = productFor(call.required("product"))
                updateProductPrice(
                    UpdateProductPriceCommand(
                        productId = product.id,
                        newPriceCents = call.required("new_price_cents").toLong(),
                    ),
                )
                ToolExecutionResult(
                    message = "Preço de ${product.name} alterado.",
                    title = "Preço alterado",
                )
            }
            CommerceToolName.GET_CUSTOMER_BALANCE -> {
                val customer = customerFor(call.required("customer"))
                ToolExecutionResult(
                    formatCents(commerceRepository.customerBalance(customer.id)),
                    "Fiado de ${customer.name}",
                )
            }
            CommerceToolName.REGISTER_CREDIT_PAYMENT -> {
                val customer = customerFor(call.required("customer"))
                val paymentMethod = paymentMethodFor(call.arguments["payment_method"])
                val operationId = call.arguments["operation_id"] ?: UuidV7.new()
                registerCreditPayment(
                    RegisterCreditPaymentCommand(
                        customerId = customer.id,
                        amountCents = call.required("amount_cents").toLong(),
                        paymentMethod = paymentMethod,
                        operationId = operationId,
                    ),
                )
                ToolExecutionResult(
                    message = "Pagamento de ${formatCents(call.required("amount_cents").toLong())} recebido de ${customer.name}.",
                    title = "Pagamento registrado",
                    operationId = operationId,
                    undo = ToolUndoMetadata(compensatingCapability = "REVERSE_CREDIT_PAYMENT"),
                    presentation = ToolResultPresentation.Payment(
                        customerName = customer.name,
                        amountText = formatCents(call.required("amount_cents").toLong()),
                        methodLabel = paymentMethod.label,
                        amountCents = call.required("amount_cents").toLong(),
                        methodStorageValue = paymentMethod.storageValue,
                    ),
                )
            }
            CommerceToolName.CREATE_CUSTOMER -> {
                val created = createCustomer(
                    CreateCustomerCommand(
                        name = call.required("name"),
                        phone = call.arguments["phone"]?.takeIf { it.isNotBlank() },
                    ),
                )
                ToolExecutionResult(
                    message = "Cliente ${created.name} cadastrado.",
                    title = "Cliente cadastrado",
                )
            }
            CommerceToolName.CORRECT_CREDIT_PAYMENT -> {
                val originalOperationId = call.required("original_operation_id")
                val amountCents = call.required("amount_cents").toLong()
                val method = paymentMethodFor(call.arguments["payment_method"])
                val correction = commerceRepository.correctCreditPayment(
                    originalPaymentOperationId = originalOperationId,
                    amountCents = amountCents,
                    paymentMethod = method,
                    operationId = call.arguments["operation_id"] ?: UuidV7.new(),
                )
                val customer = commerceRepository.findCustomerById(correction.customerId)
                    ?: error("Cliente do pagamento não encontrado.")
                ToolExecutionResult(
                    message = "Pagamento de ${formatCents(amountCents)} corrigido para ${customer.name}.",
                    title = "Pagamento corrigido",
                    operationId = correction.correctedPaymentOperationId,
                    presentation = ToolResultPresentation.Payment(
                        customerName = customer.name,
                        amountText = formatCents(amountCents),
                        methodLabel = method.label,
                        amountCents = amountCents,
                        methodStorageValue = method.storageValue,
                    ),
                    compensatesActivityId = call.arguments["original_activity_id"],
                )
            }
            CommerceToolName.GET_TODAY_SALES ->
                ToolExecutionResult(formatCents(commerceRepository.todayTotalCents()), "Vendas de hoje")
            CommerceToolName.PREPARE_PURCHASE ->
                ToolExecutionResult("Lista de compras preparada para confirmação.")
            CommerceToolName.FIND_SUPPLIER -> {
                val supplier = supplierFor(call.required("supplier"))
                ToolExecutionResult(supplier.name, "Fornecedor encontrado")
            }
        }
    }

    private fun ToolCall.required(key: String): String = arguments[key]
        ?.takeIf { it.isNotBlank() }
        ?: error("Argumento ausente: $key")

    private suspend fun productFor(reference: String) = when (val result = entityResolver.resolveProduct(reference)) {
        is EntityResolutionMatch.Resolved -> result.value
        EntityResolutionMatch.NotFound -> throw ToolClarificationException(
            "Não encontrei esse produto. Confira o nome ou cadastre o produto antes de continuar.",
            argumentKey = "product",
        )
        is EntityResolutionMatch.Ambiguous -> throw ToolClarificationException(
            "Encontrei mais de um produto: ${result.values.joinToString { it.name }}. Diga o nome completo.",
            argumentKey = "product",
            options = result.values.map { it.name },
        )
    }

    private suspend fun customerFor(reference: String) = when (val result = entityResolver.resolveCustomer(reference)) {
        is EntityResolutionMatch.Resolved -> result.value
        EntityResolutionMatch.NotFound -> throw ToolClarificationException(
            "Não encontrei esse cliente. Confira o nome ou cadastre o cliente antes de continuar.",
            argumentKey = "customer",
        )
        is EntityResolutionMatch.Ambiguous -> throw ToolClarificationException(
            "Encontrei mais de um cliente: ${result.values.joinToString { it.name }}. Diga o nome completo.",
            argumentKey = "customer",
            options = result.values.map { it.name },
        )
    }

    private suspend fun supplierFor(reference: String) = when (val result = entityResolver.resolveSupplier(reference)) {
        is EntityResolutionMatch.Resolved -> result.value
        EntityResolutionMatch.NotFound -> throw ToolClarificationException(
            "Não encontrei esse fornecedor. Confira o nome ou cadastre o fornecedor antes de continuar.",
            argumentKey = "supplier",
        )
        is EntityResolutionMatch.Ambiguous -> throw ToolClarificationException(
            "Encontrei mais de um fornecedor: ${result.values.joinToString { it.name }}. Diga o nome completo.",
            argumentKey = "supplier",
            options = result.values.map { it.name },
        )
    }

    private fun formatCents(cents: Long): String = "R$ %.2f".format(java.util.Locale("pt", "BR"), cents / 100.0)

    private fun paymentMethodFor(raw: String?): PaymentMethod = when (raw?.trim()?.lowercase()) {
        "cash" -> PaymentMethod.CASH
        "pix" -> PaymentMethod.PIX
        "card" -> PaymentMethod.CARD
        else -> throw ToolClarificationException(
            "Como você recebeu esse pagamento?",
            argumentKey = "payment_method",
            options = listOf("Dinheiro", "PIX", "Maquininha"),
        )
    }

    private val PaymentMethod.label: String
        get() = when (this) {
            PaymentMethod.CASH -> "Dinheiro"
            PaymentMethod.PIX -> "PIX"
            PaymentMethod.CARD -> "Maquininha"
            else -> "Não identificado"
        }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt).coerceAtLeast(0L) / 1_000_000L
}
