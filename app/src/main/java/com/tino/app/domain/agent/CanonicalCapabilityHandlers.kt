package com.tino.app.domain.agent

import com.tino.app.domain.usecase.CreditPaymentResult
import com.tino.app.domain.usecase.GetProductPriceUseCase
import com.tino.app.domain.usecase.GetProductStockUseCase
import com.tino.app.domain.usecase.ListOverdueUseCase
import com.tino.app.domain.usecase.ListProductsUseCase
import com.tino.app.domain.usecase.ListReceivablesUseCase
import com.tino.app.domain.usecase.RegisterCreditPaymentCommand
import com.tino.app.domain.usecase.RegisterCreditPaymentUseCase
import com.tino.app.domain.usecase.ProductPriceSnapshot
import com.tino.app.domain.usecase.ProductStockSnapshot
import com.tino.app.domain.usecase.ProductCatalogItem
import com.tino.app.domain.usecase.ReceivableSummary
import com.tino.app.domain.usecase.OverdueReceivableSummary

interface CapabilityHandler<I, O> {
    val capability: TinoCapabilityId
    suspend fun execute(input: I): O
}

data class ProductReferenceInput(val productId: String)
data object NoInput

class ListProductsCapabilityHandler(
    private val useCase: ListProductsUseCase,
) : CapabilityHandler<NoInput, List<ProductCatalogItem>> {
    override val capability = TinoCapabilityId.LIST_PRODUCTS
    override suspend fun execute(input: NoInput): List<ProductCatalogItem> = useCase()
}

class ProductStockCapabilityHandler(
    private val useCase: GetProductStockUseCase,
) : CapabilityHandler<ProductReferenceInput, ProductStockSnapshot> {
    override val capability = TinoCapabilityId.GET_PRODUCT_STOCK
    override suspend fun execute(input: ProductReferenceInput): ProductStockSnapshot = useCase(input.productId)
}

class ProductPriceCapabilityHandler(
    private val useCase: GetProductPriceUseCase,
) : CapabilityHandler<ProductReferenceInput, ProductPriceSnapshot> {
    override val capability = TinoCapabilityId.GET_PRODUCT_PRICE
    override suspend fun execute(input: ProductReferenceInput): ProductPriceSnapshot = useCase(input.productId)
}

class ReceivablesCapabilityHandler(
    private val useCase: ListReceivablesUseCase,
) : CapabilityHandler<NoInput, List<ReceivableSummary>> {
    override val capability = TinoCapabilityId.LIST_RECEIVABLES
    override suspend fun execute(input: NoInput): List<ReceivableSummary> = useCase()
}

class OverdueCapabilityHandler(
    private val useCase: ListOverdueUseCase,
) : CapabilityHandler<NoInput, List<OverdueReceivableSummary>> {
    override val capability = TinoCapabilityId.LIST_OVERDUE
    override suspend fun execute(input: NoInput): List<OverdueReceivableSummary> = useCase()
}

class RegisterCreditPaymentCapabilityHandler(
    private val useCase: RegisterCreditPaymentUseCase,
) : CapabilityHandler<RegisterCreditPaymentCommand, CreditPaymentResult> {
    override val capability = TinoCapabilityId.RECEIVE_CREDIT_PAYMENT
    override suspend fun execute(input: RegisterCreditPaymentCommand): CreditPaymentResult = useCase(input)
}
