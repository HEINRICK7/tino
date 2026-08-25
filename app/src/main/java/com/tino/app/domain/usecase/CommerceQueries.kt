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
    suspend operator fun invoke(): List<ReceivableSummary> = commerceRepository.observeCustomerBalances()
        .first()
        .filter { it.balanceCents > 0L }
        .map { balance -> ReceivableSummary(balance.id, balance.name, balance.balanceCents) }
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
