package com.tino.app.domain.agent

import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.core.observability.NoOpAuditLogger
import com.tino.app.domain.commerce.EntityResolutionMatch
import com.tino.app.domain.commerce.InventoryPolicy
import com.tino.app.domain.commerce.EntityResolutionService
import com.tino.app.domain.commerce.TemporalCreditService
import com.tino.app.domain.commerce.CommerceRepository
import com.tino.app.domain.usecase.GetProductPriceUseCase
import com.tino.app.domain.usecase.GetProductStockUseCase
import com.tino.app.domain.usecase.ListCustomersUseCase
import com.tino.app.domain.usecase.ListSuppliersUseCase
import com.tino.app.domain.usecase.ListOverdueUseCase
import com.tino.app.domain.usecase.ListProductsUseCase
import com.tino.app.domain.usecase.ListReceivablesUseCase
import com.tino.app.domain.usecase.ObserveProductsUseCase
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

sealed interface DbFirstReadResult {
    data class Products(val value: ProductListResult) : DbFirstReadResult
    data class Replenishment(val value: ReplenishmentResult) : DbFirstReadResult
    data class ProductFact(val value: ProductFactResult) : DbFirstReadResult
    data class Customers(val value: CustomerListResult) : DbFirstReadResult
    data class CustomerContact(val value: CustomerContactResult) : DbFirstReadResult
    data class Suppliers(val value: SupplierListResult) : DbFirstReadResult
    data class Receivables(val value: ReceivablesListResult) : DbFirstReadResult
    data class Overdue(val value: OverdueListResult) : DbFirstReadResult
    data class Ambiguous(val entityType: String, val options: List<String>) : DbFirstReadResult
    data class NotFound(val message: String) : DbFirstReadResult
}

data class ProductListItem(
    val id: String,
    val name: String,
    val priceCents: Long,
    val stockQuantity: Int,
    val unit: String,
    val stockTracked: Boolean = true,
)

data class ProductListResult(
    val items: List<ProductListItem>,
    val emptyMessage: String? = null,
    val dataSource: AgentDataSource = AgentDataSource.LOCAL_ONLY,
)

data class ProductFactResult(
    val product: ProductListItem,
    val dataSource: AgentDataSource = AgentDataSource.LOCAL_ONLY,
)

data class ReplenishmentResult(
    val items: List<ProductListItem>,
    val emptyMessage: String? = null,
    val dataSource: AgentDataSource = AgentDataSource.LOCAL_ONLY,
)

data class CustomerListItem(
    val id: String,
    val name: String,
    val phone: String?,
)

data class CustomerListResult(
    val items: List<CustomerListItem>,
    val emptyMessage: String? = null,
    val dataSource: AgentDataSource = AgentDataSource.LOCAL_ONLY,
)

data class CustomerContactResult(
    val customerId: String,
    val customerName: String,
    val phone: String?,
    val dataSource: AgentDataSource = AgentDataSource.LOCAL_ONLY,
)

data class SupplierListItem(
    val id: String,
    val name: String,
    val phone: String?,
)

data class SupplierListResult(
    val items: List<SupplierListItem>,
    val emptyMessage: String? = null,
    val dataSource: AgentDataSource = AgentDataSource.LOCAL_ONLY,
)

data class ReceivableItem(
    val customerId: String,
    val customerName: String,
    val balanceCents: Long,
)

data class ReceivablesListResult(
    val items: List<ReceivableItem>,
    val emptyMessage: String? = null,
    val dataSource: AgentDataSource = AgentDataSource.LOCAL_ONLY,
)

data class OverdueItem(
    val customerId: String,
    val customerName: String,
    val balanceCents: Long,
    val daysOverdue: Long,
)

data class OverdueListResult(
    val items: List<OverdueItem>,
    val emptyMessage: String? = null,
    val dataSource: AgentDataSource = AgentDataSource.LOCAL_ONLY,
)

interface DbFirstReadCapabilities {
    suspend fun listProducts(): DbFirstReadResult.Products
    suspend fun listReplenishment(): DbFirstReadResult.Replenishment
    suspend fun productFact(capability: AgentCapability, reference: String): DbFirstReadResult
    suspend fun listCustomers(): DbFirstReadResult.Customers
    suspend fun customerContact(reference: String): DbFirstReadResult
    suspend fun listSuppliers(): DbFirstReadResult.Suppliers
    suspend fun supplierFact(reference: String): DbFirstReadResult
    suspend fun listReceivables(): DbFirstReadResult.Receivables
    suspend fun listOverdue(): DbFirstReadResult.Overdue
}

@Singleton
class DbFirstReadCapabilityService @Inject constructor(
    private val listProductsUseCase: ListProductsUseCase,
    private val getProductStockUseCase: GetProductStockUseCase,
    private val getProductPriceUseCase: GetProductPriceUseCase,
    private val listCustomersUseCase: ListCustomersUseCase,
    private val listSuppliersUseCase: ListSuppliersUseCase,
    private val listReceivablesUseCase: ListReceivablesUseCase,
    private val listOverdueUseCase: ListOverdueUseCase,
    private val entityResolver: EntityResolutionService,
    private val auditLogger: AuditLogger,
) : DbFirstReadCapabilities {
    override suspend fun listProducts(): DbFirstReadResult.Products {
        return roomQuery("LIST_PRODUCTS") {
            val items = listProductsUseCase().map {
                ProductListItem(it.id, it.name, it.priceCents, it.stockQuantity, it.unit, it.stockTracked)
            }
            DbFirstReadResult.Products(
                ProductListResult(
                    items = items,
                    emptyMessage = items.takeIf { it.isEmpty() }?.let { "Nenhum produto cadastrado." },
                ),
            )
        }
    }

    override suspend fun listReplenishment(): DbFirstReadResult.Replenishment {
        return roomQuery("REPLENISHMENT_QUERY") {
            val policy = InventoryPolicy.conservativeDefault
            val items = listProductsUseCase()
                .filter { it.stockTracked && policy.needsReplenishment(it.stockQuantity) }
                .map { ProductListItem(it.id, it.name, it.priceCents, it.stockQuantity, it.unit, it.stockTracked) }
            DbFirstReadResult.Replenishment(
                ReplenishmentResult(
                    items = items,
                    emptyMessage = if (items.isEmpty()) "Nenhum produto precisa de reposição agora." else null,
                ),
            )
        }
    }

    override suspend fun productFact(
        capability: AgentCapability,
        reference: String,
    ): DbFirstReadResult {
        return roomQuery("PRODUCT_FACT") { when (val match = entityResolver.resolveProduct(reference)) {
            is EntityResolutionMatch.Resolved -> {
                val product = match.value
                val stock = getProductStockUseCase(product.id)
                val price = getProductPriceUseCase(product.id)
                val item = ProductListItem(
                    id = product.id,
                    name = product.name,
                    priceCents = price.priceCents,
                    stockQuantity = stock.stockQuantity,
                    unit = product.unit,
                    stockTracked = product.stockTracked,
                )
                DbFirstReadResult.ProductFact(ProductFactResult(item))
            }
            is EntityResolutionMatch.Ambiguous -> DbFirstReadResult.Ambiguous(
                entityType = "product",
                options = match.values.map { it.name },
            )
            EntityResolutionMatch.NotFound -> DbFirstReadResult.NotFound(
                "Não encontrei esse produto. Confira o nome ou cadastre o produto antes de consultar.",
            )
        } }
    }

    override suspend fun listCustomers(): DbFirstReadResult.Customers {
        return roomQuery("LIST_CUSTOMERS") {
            val items = listCustomersUseCase().map {
                CustomerListItem(it.id, it.name, it.phone)
            }
            DbFirstReadResult.Customers(
                CustomerListResult(
                    items = items,
                    emptyMessage = items.takeIf { it.isEmpty() }?.let { "Nenhum cliente cadastrado." },
                ),
            )
        }
    }

    override suspend fun customerContact(reference: String): DbFirstReadResult {
        return roomQuery("GET_CUSTOMER_CONTACT") { when (val match = entityResolver.resolveCustomer(reference)) {
            is EntityResolutionMatch.Resolved -> {
                val customer = match.value
                DbFirstReadResult.CustomerContact(
                    CustomerContactResult(
                        customerId = customer.id,
                        customerName = customer.name,
                        phone = customer.phone,
                    ),
                )
            }
            is EntityResolutionMatch.Ambiguous -> DbFirstReadResult.Ambiguous(
                entityType = "customer",
                options = match.values.map { it.name },
            )
            EntityResolutionMatch.NotFound -> DbFirstReadResult.NotFound(
                "Não encontrei esse cliente. Confira o nome ou cadastre o cliente antes de consultar.",
            )
        } }
    }

    override suspend fun listSuppliers(): DbFirstReadResult.Suppliers {
        return roomQuery("LIST_SUPPLIERS") {
            val items = listSuppliersUseCase().map { SupplierListItem(it.id, it.name, it.phone) }
            DbFirstReadResult.Suppliers(
                SupplierListResult(
                    items = items,
                    emptyMessage = items.takeIf { it.isEmpty() }?.let { "Nenhum fornecedor cadastrado." },
                ),
            )
        }
    }

    override suspend fun supplierFact(reference: String): DbFirstReadResult {
        return roomQuery("SUPPLIER_FACT") { when (val match = entityResolver.resolveSupplier(reference)) {
            is EntityResolutionMatch.Resolved -> {
                val supplier = match.value
                DbFirstReadResult.Suppliers(
                    SupplierListResult(
                        items = listOf(SupplierListItem(supplier.id, supplier.name, supplier.phone)),
                    ),
                )
            }
            is EntityResolutionMatch.Ambiguous -> DbFirstReadResult.Ambiguous(
                entityType = "supplier",
                options = match.values.map { it.name },
            )
            EntityResolutionMatch.NotFound -> DbFirstReadResult.NotFound(
                "Não encontrei esse fornecedor. Confira o nome ou cadastre o fornecedor antes de consultar.",
            )
        } }
    }

    override suspend fun listReceivables(): DbFirstReadResult.Receivables {
        return roomQuery("LIST_RECEIVABLES") {
            val items = listReceivablesUseCase().map { ReceivableItem(it.customerId, it.customerName, it.amountCents) }
            DbFirstReadResult.Receivables(
                ReceivablesListResult(
                    items = items,
                    emptyMessage = items.takeIf { it.isEmpty() }?.let { "Ninguém está devendo no momento." },
                ),
            )
        }
    }

    override suspend fun listOverdue(): DbFirstReadResult.Overdue {
        return roomQuery("LIST_OVERDUE") {
            val items = listOverdueUseCase().map {
                OverdueItem(it.customerId, it.customerName, it.amountCents, it.daysOverdue)
            }
            DbFirstReadResult.Overdue(
                OverdueListResult(
                    items = items,
                    emptyMessage = items.takeIf { it.isEmpty() }?.let { "Nenhum fiado vencido." },
                ),
            )
        }
    }

    private suspend fun <T> roomQuery(operation: String, block: suspend () -> T): T {
        val startedAt = System.nanoTime()
        auditLogger.record(
            AuditEventType.VOICE_STAGE,
            mapOf("stage" to "ROOM_QUERY_STARTED", "route" to operation),
        )
        return try {
            block()
        } catch (error: Throwable) {
            auditLogger.record(
                AuditEventType.VOICE_STAGE,
                mapOf(
                    "stage" to "ROOM_QUERY_FAILED",
                    "route" to operation,
                    "duration_ms" to elapsedMs(startedAt).toString(),
                ),
            )
            throw error
        }.also {
            auditLogger.record(
                AuditEventType.VOICE_STAGE,
                mapOf(
                    "stage" to "ROOM_QUERY_COMPLETED",
                    "route" to operation,
                    "duration_ms" to elapsedMs(startedAt).toString(),
                ),
            )
        }
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt).coerceAtLeast(0L) / 1_000_000L

    constructor(
        commerceRepository: CommerceRepository,
        entityResolver: EntityResolutionService,
        temporalCredit: TemporalCreditService,
        clock: Clock,
    ) : this(
        listProductsUseCase = ListProductsUseCase(ObserveProductsUseCase(commerceRepository)),
        getProductStockUseCase = GetProductStockUseCase(commerceRepository),
        getProductPriceUseCase = GetProductPriceUseCase(commerceRepository),
        listCustomersUseCase = ListCustomersUseCase(commerceRepository),
        listSuppliersUseCase = ListSuppliersUseCase(commerceRepository),
        listReceivablesUseCase = ListReceivablesUseCase(commerceRepository),
        listOverdueUseCase = ListOverdueUseCase(temporalCredit, clock),
        entityResolver = entityResolver,
        auditLogger = NoOpAuditLogger,
    )
}

@Singleton
class UnavailableDbFirstReadCapabilities @Inject constructor() : DbFirstReadCapabilities {
    override suspend fun listProducts() = DbFirstReadResult.Products(
        ProductListResult(emptyList(), "Nenhum produto cadastrado."),
    )

    override suspend fun listReplenishment() = DbFirstReadResult.Replenishment(
        ReplenishmentResult(emptyList(), "Não foi possível consultar a reposição neste teste."),
    )

    override suspend fun productFact(capability: AgentCapability, reference: String) =
        DbFirstReadResult.NotFound("A consulta de produto não está disponível neste teste.")

    override suspend fun listCustomers() = DbFirstReadResult.Customers(
        CustomerListResult(emptyList(), "Nenhum cliente cadastrado."),
    )

    override suspend fun customerContact(reference: String) =
        DbFirstReadResult.NotFound("O contato de cliente não está disponível neste teste.")

    override suspend fun listSuppliers() = DbFirstReadResult.Suppliers(
        SupplierListResult(emptyList(), "Nenhum fornecedor cadastrado."),
    )

    override suspend fun supplierFact(reference: String) =
        DbFirstReadResult.NotFound("A consulta de fornecedor não está disponível neste teste.")

    override suspend fun listReceivables() = DbFirstReadResult.Receivables(
        ReceivablesListResult(emptyList(), "Ninguém está devendo no momento."),
    )

    override suspend fun listOverdue() = DbFirstReadResult.Overdue(
        OverdueListResult(emptyList(), "Nenhum fiado vencido."),
    )
}
