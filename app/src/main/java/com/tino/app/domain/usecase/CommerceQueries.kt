package com.tino.app.domain.usecase

import com.tino.app.domain.commerce.CommerceRepository
import com.tino.app.domain.commerce.CreditTemporalStatus
import com.tino.app.domain.commerce.PaymentMethod
import com.tino.app.domain.commerce.TemporalCreditService
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

data class ProductCatalogItem(
    val id: String,
    val name: String,
    val priceCents: Long,
    val stockQuantity: Int,
    val unit: String,
    val stockQuantityExact: String = stockQuantity.toString(),
    val stockTracked: Boolean = true,
)

data class ProductStockSnapshot(
    val productId: String,
    val stockQuantity: Int,
)

data class ProductPriceSnapshot(
    val productId: String,
    val priceCents: Long,
)

data class ReceivableSummary(
    val customerId: String,
    val customerName: String,
    val amountCents: Long,
)

data class CustomerCatalogItem(
    val id: String,
    val name: String,
    val phone: String?,
)

data class SupplierCatalogItem(
    val id: String,
    val name: String,
    val phone: String?,
)

data class OverdueReceivableSummary(
    val customerId: String,
    val customerName: String,
    val amountCents: Long,
    val daysOverdue: Long,
)

data class RegisterCreditPaymentCommand(
    val customerId: String,
    val amountCents: Long,
    val paymentMethod: PaymentMethod,
    val operationId: String,
)

data class CreditPaymentResult(
    val customerId: String,
    val amountCents: Long,
    val paymentMethod: PaymentMethod,
    val operationId: String,
)

data class CreateCustomerCommand(
    val name: String,
    val phone: String?,
)

data class CustomerCreatedResult(
    val customerId: String,
    val name: String,
    val phone: String?,
)

data class UpdateProductPriceCommand(
    val productId: String,
    val newPriceCents: Long,
)

data class ProductPriceUpdatedResult(
    val productId: String,
    val previousPriceCents: Long,
    val newPriceCents: Long,
)

data class RegisterStockEntryCommand(
    val productId: String,
    val quantity: Int,
    val unitCostCents: Long,
    val supplierId: String? = null,
)

data class StockEntryRegisteredResult(
    val productId: String,
    val quantity: Int,
    val unitCostCents: Long,
    val supplierId: String?,
)

@Singleton
class ObserveProductsUseCase @Inject constructor(
    private val commerceRepository: CommerceRepository,
) {
    operator fun invoke(): Flow<List<ProductCatalogItem>> = commerceRepository.observeProducts().map { products ->
        products.map { product ->
            ProductCatalogItem(
                id = product.id,
                name = product.name,
                priceCents = product.priceCents,
                stockQuantity = product.stockQuantity,
                unit = product.unit,
                stockQuantityExact = product.stockQuantityExact,
                stockTracked = product.stockTracked,
            )
        }
    }
}

@Singleton
class ListProductsUseCase @Inject constructor(
    private val observeProducts: ObserveProductsUseCase,
) {
    suspend operator fun invoke(): List<ProductCatalogItem> = observeProducts().first()
}

@Singleton
class GetProductStockUseCase @Inject constructor(
    private val commerceRepository: CommerceRepository,
) {
    suspend operator fun invoke(productId: String): ProductStockSnapshot = ProductStockSnapshot(
        productId = productId,
        stockQuantity = commerceRepository.stockBalance(productId),
    )
}

@Singleton
class GetProductPriceUseCase @Inject constructor(
    private val commerceRepository: CommerceRepository,
) {
    suspend operator fun invoke(productId: String): ProductPriceSnapshot {
        val product = commerceRepository.findProductById(productId)
            ?: error("Produto não encontrado.")
        return ProductPriceSnapshot(product.id, product.priceCents)
    }
}

@Singleton
class ListReceivablesUseCase @Inject constructor(
    private val commerceRepository: CommerceRepository,
) {
    suspend operator fun invoke(): List<ReceivableSummary> {
        val names = commerceRepository.allCustomersForResolution().associateBy { it.id }
        return commerceRepository.allSharedLedgerProjections()
            .filter { it.balanceCents > 0L }
            .mapNotNull { projection ->
                names[projection.customerId]?.let { customer ->
                    ReceivableSummary(customer.id, customer.name, projection.balanceCents)
                }
            }
    }
}

@Singleton
class ListCustomersUseCase @Inject constructor(
    private val commerceRepository: CommerceRepository,
) {
    suspend operator fun invoke(): List<CustomerCatalogItem> = commerceRepository
        .observeCustomers()
        .first()
        .map { customer -> CustomerCatalogItem(customer.id, customer.name, customer.phone) }
}

@Singleton
class ListSuppliersUseCase @Inject constructor(
    private val commerceRepository: CommerceRepository,
) {
    suspend operator fun invoke(): List<SupplierCatalogItem> = commerceRepository
        .observeSuppliers()
        .first()
        .map { supplier -> SupplierCatalogItem(supplier.id, supplier.name, supplier.phone) }
}

@Singleton
class ListOverdueUseCase @Inject constructor(
    private val temporalCredit: TemporalCreditService,
    private val clock: Clock,
) {
    suspend operator fun invoke(): List<OverdueReceivableSummary> = temporalCredit
        .allCustomerTimelines(clock.millis(), clock.zone)
        .mapNotNull { timeline ->
            val overdue = timeline.entries.filter {
                it.status == CreditTemporalStatus.OVERDUE && it.outstandingCents > 0L
            }
            overdue.takeIf { it.isNotEmpty() }?.let {
                OverdueReceivableSummary(
                    customerId = timeline.customerId,
                    customerName = timeline.customerName ?: "Cliente",
                    amountCents = it.sumOf { entry -> entry.outstandingCents },
                    daysOverdue = it.maxOf { entry -> entry.daysOverdue },
                )
            }
        }
        .sortedWith(compareByDescending<OverdueReceivableSummary> { it.daysOverdue }.thenBy { it.customerName })
}

@Singleton
class RegisterCreditPaymentUseCase @Inject constructor(
    private val commerceRepository: CommerceRepository,
) {
    suspend operator fun invoke(command: RegisterCreditPaymentCommand): CreditPaymentResult {
        commerceRepository.registerCreditPayment(
            customerId = command.customerId,
            amountCents = command.amountCents,
            paymentMethod = command.paymentMethod,
            operationId = command.operationId,
        )
        return CreditPaymentResult(
            customerId = command.customerId,
            amountCents = command.amountCents,
            paymentMethod = command.paymentMethod,
            operationId = command.operationId,
        )
    }
}

@Singleton
class CreateCustomerUseCase @Inject constructor(
    private val commerceRepository: CommerceRepository,
) {
    suspend operator fun invoke(command: CreateCustomerCommand): CustomerCreatedResult {
        val customerId = commerceRepository.createCustomer(command.name, command.phone)
        val customer = commerceRepository.findCustomerById(customerId)
            ?: error("Cliente recém-criado não encontrado.")
        return CustomerCreatedResult(customer.id, customer.name, customer.phone)
    }
}

@Singleton
class UpdateProductPriceUseCase @Inject constructor(
    private val commerceRepository: CommerceRepository,
) {
    suspend operator fun invoke(command: UpdateProductPriceCommand): ProductPriceUpdatedResult {
        val product = commerceRepository.findProductById(command.productId)
            ?: error("Produto não encontrado.")
        commerceRepository.changeProductPrice(command.productId, command.newPriceCents)
        return ProductPriceUpdatedResult(
            productId = product.id,
            previousPriceCents = product.priceCents,
            newPriceCents = command.newPriceCents,
        )
    }
}

@Singleton
class RegisterStockEntryUseCase @Inject constructor(
    private val commerceRepository: CommerceRepository,
) {
    suspend operator fun invoke(command: RegisterStockEntryCommand): StockEntryRegisteredResult {
        commerceRepository.registerStockReceipt(
            productId = command.productId,
            quantity = command.quantity,
            unitCostCents = command.unitCostCents,
            supplierId = command.supplierId,
        )
        return StockEntryRegisteredResult(
            productId = command.productId,
            quantity = command.quantity,
            unitCostCents = command.unitCostCents,
            supplierId = command.supplierId,
        )
    }
}
