package com.tino.app.domain.commerce

import androidx.room.withTransaction
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.common.UuidV7
import com.tino.app.core.database.DomainEventEntity
import com.tino.app.core.database.DirectReceiptDao
import com.tino.app.core.database.DirectReceiptEntity
import com.tino.app.core.database.CustomerDao
import com.tino.app.core.database.CustomerEntity
import com.tino.app.core.database.CreditDao
import com.tino.app.core.database.CreditEntryEntity
import com.tino.app.core.database.CreditEntryType
import com.tino.app.core.database.ProductDao
import com.tino.app.core.database.ProductEntity
import com.tino.app.core.database.ProductSummary
import com.tino.app.core.database.SaleDao
import com.tino.app.core.database.SaleEntity
import com.tino.app.core.database.SaleItemEntity
import com.tino.app.core.database.StockMovementDao
import com.tino.app.core.database.StockMovementEntity
import com.tino.app.core.database.SupplierDao
import com.tino.app.core.database.SupplierEntity
import com.tino.app.core.database.PurchaseDao
import com.tino.app.core.database.PurchaseEntity
import com.tino.app.core.database.PurchaseItemEntity
import com.tino.app.core.database.PurchaseStatus
import com.tino.app.core.database.OrderSummary
import com.tino.app.core.database.OrderDetail
import com.tino.app.core.database.OrderEntity
import com.tino.app.core.database.OrderItemEntity
import com.tino.app.core.sync.SyncScheduler
import com.tino.app.core.database.TinoDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommerceRepository @Inject constructor(
    private val database: TinoDatabase,
    private val productDao: ProductDao,
    private val saleDao: SaleDao,
    private val directReceiptDao: DirectReceiptDao,
    private val stockMovementDao: StockMovementDao,
    private val customerDao: CustomerDao,
    private val creditDao: CreditDao,
    private val supplierDao: SupplierDao,
    private val purchaseDao: PurchaseDao,
    private val identityProvider: IdentityProvider,
    private val syncScheduler: SyncScheduler,
) {
    data class CorrectedCreditPayment(
        val customerId: String,
        val reversalOperationId: String,
        val correctedPaymentOperationId: String,
    )
    fun observeProducts(): Flow<List<ProductSummary>> = productDao.observeAll()

    fun observeOrders(): Flow<List<OrderSummary>> = database.orderDao().observeAll()

    suspend fun findOrderDetail(orderId: String): OrderDetail? {
        val order = database.orderDao().findById(orderId) ?: return null
        return OrderDetail(order, database.orderDao().items(orderId))
    }

    fun observeTodayTotal(): Flow<Long> = saleDao.observeTodayTotal(startOfToday())

    fun observeTodayReceived(): Flow<Long> = saleDao.observeTodayReceived(startOfToday())

    fun observeTodayPayment(method: PaymentMethod): Flow<Long> =
        saleDao.observeTodayTotalByPaymentMethod(startOfToday(), method.storageValue)

    fun observeTodayDirectReceiptTotal(): Flow<Long> = directReceiptDao.observeTodayTotal(startOfToday())

    fun observeTodayDirectReceiptPayment(method: PaymentMethod): Flow<Long> =
        directReceiptDao.observeTodayTotalByPaymentMethod(startOfToday(), method.storageValue)

    fun observeTodaySalesCount(): Flow<Int> = saleDao.observeTodayCount(startOfToday())

    fun observePendingEventCount(): Flow<Int> = database.domainEventDao().observePendingCount()

    fun observeCustomers() = customerDao.observeAll()

    fun observeCustomerBalances() = creditDao.observeBalances()

    fun observeTotalReceivable(): Flow<Long> = creditDao.observeTotalBalance()

    fun observeTodayCreditPaymentReceived(method: PaymentMethod): Flow<Long> =
        creditDao.observeTodayPaymentReceived(startOfToday(), method.storageValue)

    fun observeTodayCreditPaymentTotal(): Flow<Long> =
        creditDao.observeTodayPaymentReceivedTotal(startOfToday())

    fun observeSuppliers() = supplierDao.observeAll()

    suspend fun findProductByName(name: String) = productDao.findByName(name)

    suspend fun findProductById(id: String) = productDao.findById(id)

    suspend fun findCustomerByName(name: String) = customerDao.findByName(name)

    suspend fun findCustomerById(id: String) = customerDao.findById(id)

    suspend fun findCreditEntryByOperation(operationId: String) = creditDao.findById(operationId)

    suspend fun findSupplierByName(name: String) = supplierDao.findByName(name)

    suspend fun allProductsForResolution(): List<ProductEntity> = productDao.all()

    suspend fun allCustomersForResolution(): List<CustomerEntity> = customerDao.all()

    suspend fun allSuppliersForResolution(): List<SupplierEntity> = supplierDao.all()

    suspend fun creditEntriesForTimeline(): List<CreditEntryEntity> = creditDao.all()

    suspend fun stockMovementsForIntelligence(): List<StockMovementEntity> = stockMovementDao.all()

    suspend fun resolveProductByName(name: String): EntityResolution<ProductEntity> =
        resolve(productDao.findByName(name), productDao.searchByName("%${name.trim()}%"))

    suspend fun resolveCustomerByName(name: String): EntityResolution<CustomerEntity> =
        resolve(customerDao.findByName(name), customerDao.searchByName("%${name.trim()}%"))

    suspend fun resolveSupplierByName(name: String): EntityResolution<SupplierEntity> =
        resolve(supplierDao.findByName(name), supplierDao.searchByName("%${name.trim()}%"))

    suspend fun stockBalance(productId: String): Int = stockMovementDao.balance(productId)

    suspend fun todayTotalCents(): Long = observeTodayTotal().first()

    suspend fun todayDirectReceiptTotalCents(): Long = observeTodayDirectReceiptTotal().first()

    suspend fun customerBalance(customerId: String): Long = creditDao.balance(customerId)

    suspend fun totalReceivableCents(): Long = observeTotalReceivable().first()

    suspend fun todayCreditPaymentReceivedCents(method: PaymentMethod): Long =
        observeTodayCreditPaymentReceived(method).first()

    suspend fun todayCreditPaymentTotalCents(): Long = observeTodayCreditPaymentTotal().first()

    private fun <T> resolve(
        exact: T?,
        candidates: List<T>,
    ): EntityResolution<T> {
        exact?.let { return EntityResolution.Resolved(it) }
        return when (candidates.size) {
            0 -> EntityResolution.NotFound
            1 -> EntityResolution.Resolved(candidates.single())
            else -> EntityResolution.Ambiguous(candidates)
        }
    }

    suspend fun createProduct(name: String, priceCents: Long, initialStock: Int) {
        require(name.isNotBlank()) { "Informe o nome do produto." }
        require(priceCents > 0) { "O preço precisa ser maior que zero." }
        require(initialStock >= 0) { "O estoque inicial não pode ser negativo." }

        val now = System.currentTimeMillis()
        val productId = UuidV7.new()
        val identity = identityProvider.current()

        database.withTransaction {
            productDao.insert(ProductEntity(productId, name.trim(), priceCents, "un", now))
            database.domainEventDao().insert(
                event(
                    identity = identity,
                    aggregateId = productId,
                    type = "product.created",
                    occurredAt = now,
                    payload = JSONObject()
                        .put("product_id", productId)
                        .put("name", name.trim())
                        .put("price_cents", priceCents),
                ),
            )

            if (initialStock > 0) {
                val movementId = UuidV7.new()
                stockMovementDao.insert(
                    StockMovementEntity(movementId, productId, initialStock, "initial_stock", null, now),
                )
                database.domainEventDao().insert(
                    event(
                        identity = identity,
                        aggregateId = productId,
                        type = "stock.received",
                        occurredAt = now,
                        payload = JSONObject()
                            .put("product_id", productId)
                            .put("quantity", initialStock)
                            .put("reason", "initial_stock"),
                    ),
                )
            }
        }
        syncScheduler.schedule()
    }

    suspend fun changeProductPrice(productId: String, newPriceCents: Long) {
        require(newPriceCents > 0) { "O preço precisa ser maior que zero." }
        database.withTransaction {
            val product = productDao.findById(productId) ?: error("Produto não encontrado.")
            check(product.priceCents != newPriceCents) { "O produto já está com esse preço." }
            val now = System.currentTimeMillis()
            val identity = identityProvider.current()
            productDao.updatePrice(productId, newPriceCents)
            database.domainEventDao().insert(
                event(
                    identity = identity,
                    aggregateId = productId,
                    type = "product.price.changed",
                    occurredAt = now,
                    payload = JSONObject()
                        .put("product_id", productId)
                        .put("previous_price_cents", product.priceCents)
                        .put("new_price_cents", newPriceCents),
                ),
            )
        }
        syncScheduler.schedule()
    }

    suspend fun registerSale(
        productId: String,
        quantity: Int,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
    ) {
        database.withTransaction {
            val product = productDao.findById(productId) ?: error("Produto não encontrado.")
            val totalCents = CommerceRules.saleTotal(
                unitPriceCents = product.priceCents,
                quantity = quantity,
                availableStock = stockMovementDao.balance(productId),
                productName = product.name,
            )
            val now = System.currentTimeMillis()
            val saleId = UuidV7.new()
            val identity = identityProvider.current()
            saleDao.insert(SaleEntity(saleId, totalCents, paymentMethod.storageValue, now))
            saleDao.insertItems(listOf(SaleItemEntity(saleId, 0, productId, quantity, product.priceCents)))
            stockMovementDao.insert(
                StockMovementEntity(UuidV7.new(), productId, -quantity, "sale", saleId, now),
            )
            database.domainEventDao().insert(
                event(
                    identity = identity,
                    aggregateId = saleId,
                    type = "sale.created",
                    occurredAt = now,
                    payload = JSONObject()
                        .put("sale_id", saleId)
                        .put("product_id", productId)
                        .put("quantity", quantity)
                        .put("unit_price_cents", product.priceCents)
                        .put("total_cents", totalCents)
                        .put("payment_method", paymentMethod.storageValue),
                ),
            )
        }
        syncScheduler.schedule()
    }

    suspend fun registerDirectReceipt(
        amountCents: Long,
        paymentMethod: PaymentMethod,
        source: String = "manual",
        note: String? = null,
        operationId: String = UuidV7.new(),
    ): String {
        require(amountCents > 0) { "O recebimento precisa ser maior que zero." }
        require(source.isNotBlank()) { "A origem do recebimento é obrigatória." }
        database.withTransaction {
            directReceiptDao.findByOperationId(operationId)?.let { existing ->
                check(
                    existing.amountCents == amountCents &&
                        existing.paymentMethod == paymentMethod.storageValue &&
                        existing.source == source.trim() &&
                        existing.note == note?.trim()?.ifBlank { null },
                ) {
                    "A operationId já está associada a outro recebimento."
                }
                return@withTransaction
            }
            val now = System.currentTimeMillis()
            val identity = identityProvider.current()
            directReceiptDao.insert(
                DirectReceiptEntity(
                    id = operationId,
                    amountCents = amountCents,
                    paymentMethod = paymentMethod.storageValue,
                    occurredAt = now,
                    source = source.trim(),
                    note = note?.trim()?.ifBlank { null },
                    operationId = operationId,
                ),
            )
            database.domainEventDao().insert(
                event(
                    identity = identity,
                    aggregateId = operationId,
                    type = "direct.receipt.created",
                    occurredAt = now,
                    payload = JSONObject()
                        .put("receipt_id", operationId)
                        .put("operation_id", operationId)
                        .put("amount_cents", amountCents)
                        .put("payment_method", paymentMethod.storageValue)
                        .put("source", source.trim())
                        .putOpt("note", note?.trim()?.ifBlank { null }),
                ),
            )
        }
        syncScheduler.schedule()
        return operationId
    }

    suspend fun createCustomer(name: String, phone: String? = null) {
        require(name.isNotBlank()) { "Informe o nome do cliente." }
        val id = UuidV7.new()
        val now = System.currentTimeMillis()
        val identity = identityProvider.current()
        database.withTransaction {
            customerDao.insert(CustomerEntity(id, name.trim(), phone?.trim()?.ifBlank { null }, now))
            database.domainEventDao().insert(
                event(identity, id, "customer.created", now, JSONObject()
                    .put("customer_id", id).put("name", name.trim()).putOpt("phone", phone)),
            )
        }
        syncScheduler.schedule()
    }

    suspend fun updateCustomer(customerId: String, name: String, phone: String? = null) {
        require(name.isNotBlank()) { "Informe o nome do cliente." }
        val customer = customerDao.findById(customerId) ?: error("Cliente não encontrado.")
        val normalizedName = name.trim()
        val normalizedPhone = phone?.trim()?.ifBlank { null }
        val now = System.currentTimeMillis()
        val identity = identityProvider.current()
        database.withTransaction {
            customerDao.updateProfile(customer.id, normalizedName, normalizedPhone)
            database.domainEventDao().insert(
                event(identity, customer.id, "customer.updated", now, JSONObject()
                    .put("customer_id", customer.id)
                    .put("name", normalizedName)
                    .putOpt("phone", normalizedPhone)),
            )
        }
        syncScheduler.schedule()
    }

    suspend fun createSupplier(name: String, phone: String? = null) {
        require(name.isNotBlank()) { "Informe o nome do fornecedor." }
        val id = UuidV7.new()
        val now = System.currentTimeMillis()
        val identity = identityProvider.current()
        database.withTransaction {
            supplierDao.insert(SupplierEntity(id, name.trim(), phone?.trim()?.ifBlank { null }, now))
            database.domainEventDao().insert(
                event(identity, id, "supplier.created", now, JSONObject()
                    .put("supplier_id", id).put("name", name.trim()).putOpt("phone", phone)),
            )
        }
        syncScheduler.schedule()
    }

    suspend fun registerCreditSale(
        customerId: String,
        productId: String,
        quantity: Int,
        dueAt: Long? = null,
    ) {
        require(dueAt == null || dueAt > 0) { "O vencimento informado não é válido." }
        database.withTransaction {
            val customer = customerDao.findById(customerId) ?: error("Cliente não encontrado.")
            val product = productDao.findById(productId) ?: error("Produto não encontrado.")
            val totalCents = CommerceRules.saleTotal(
                unitPriceCents = product.priceCents,
                quantity = quantity,
                availableStock = stockMovementDao.balance(productId),
                productName = product.name,
            )
            val now = System.currentTimeMillis()
            val saleId = UuidV7.new()
            val identity = identityProvider.current()
            val creditEntryId = UuidV7.new()
            saleDao.insert(SaleEntity(saleId, totalCents, "credit", now))
            saleDao.insertItems(listOf(SaleItemEntity(saleId, 0, productId, quantity, product.priceCents)))
            stockMovementDao.insert(StockMovementEntity(UuidV7.new(), productId, -quantity, "credit_sale", saleId, now))
            creditDao.insert(
                CreditEntryEntity(
                    creditEntryId,
                    customer.id,
                    totalCents,
                    CreditEntryType.SALE,
                    saleId,
                    now,
                    PaymentMethod.CREDIT.storageValue,
                    dueAt,
                ),
            )
            database.domainEventDao().insert(
                event(identity, saleId, "sale.created", now, JSONObject()
                    .put("sale_id", saleId).put("customer_id", customer.id).put("product_id", productId)
                    .put("quantity", quantity).put("unit_price_cents", product.priceCents)
                    .put("total_cents", totalCents).put("payment_method", "credit")
                    .putOpt("due_at", dueAt)
                    .put("credit_entry_id", creditEntryId)),
            )
            database.domainEventDao().insert(
                event(identity, customer.id, "credit.sale.created", now, JSONObject()
                    .put("customer_id", customer.id).put("sale_id", saleId).put("amount_cents", totalCents)
                    .putOpt("due_at", dueAt)),
            )
        }
        syncScheduler.schedule()
    }

    suspend fun registerCreditByAmount(
        customerId: String,
        amountCents: Long,
        operationId: String = UuidV7.new(),
        dueAt: Long? = null,
    ): String {
        require(amountCents > 0) { "O fiado precisa ser maior que zero." }
        require(dueAt == null || dueAt > 0) { "O vencimento informado não é válido." }
        database.withTransaction {
            customerDao.findById(customerId) ?: error("Cliente não encontrado.")
            creditDao.findById(operationId)?.let { existing ->
                check(
                    existing.customerId == customerId &&
                        existing.amountCents == amountCents &&
                        existing.type == CreditEntryType.SALE &&
                        existing.referenceId == null &&
                        existing.dueAt == dueAt,
                ) {
                    "A operationId já está associada a outro lançamento de fiado."
                }
                return@withTransaction
            }
            val now = System.currentTimeMillis()
            val identity = identityProvider.current()
            creditDao.insert(
                CreditEntryEntity(
                    id = operationId,
                    customerId = customerId,
                    amountCents = amountCents,
                    type = CreditEntryType.SALE,
                    referenceId = null,
                    occurredAt = now,
                    paymentMethod = PaymentMethod.CREDIT.storageValue,
                    dueAt = dueAt,
                ),
            )
            database.domainEventDao().insert(
                event(
                    identity = identity,
                    aggregateId = operationId,
                    type = "credit.receivable.created",
                    occurredAt = now,
                    payload = JSONObject()
                        .put("entry_id", operationId)
                        .put("operation_id", operationId)
                        .put("customer_id", customerId)
                        .put("amount_cents", amountCents)
                        .putOpt("due_at", dueAt),
                ),
            )
        }
        syncScheduler.schedule()
        return operationId
    }

    suspend fun registerCreditPayment(
        customerId: String,
        amountCents: Long,
        paymentMethod: PaymentMethod = PaymentMethod.UNKNOWN,
        operationId: String = UuidV7.new(),
    ): String {
        require(amountCents > 0) { "O pagamento precisa ser maior que zero." }
        require(paymentMethod != PaymentMethod.CREDIT && paymentMethod != PaymentMethod.UNKNOWN) {
            "O pagamento do fiado precisa ser recebido em dinheiro, PIX ou maquininha."
        }
        database.withTransaction {
            customerDao.findById(customerId) ?: error("Cliente não encontrado.")
            creditDao.findById(operationId)?.let { existing ->
                check(
                    existing.customerId == customerId &&
                        existing.amountCents == -amountCents &&
                        existing.type == CreditEntryType.PAYMENT &&
                        existing.paymentMethod == paymentMethod.storageValue,
                ) {
                    "A operationId já está associada a outro pagamento de fiado."
                }
                return@withTransaction
            }
            check(creditDao.balance(customerId) >= amountCents) { "O pagamento não pode ser maior que o saldo." }
            val now = System.currentTimeMillis()
            val identity = identityProvider.current()
            creditDao.insert(
                CreditEntryEntity(
                    id = operationId,
                    customerId = customerId,
                    amountCents = -amountCents,
                    type = CreditEntryType.PAYMENT,
                    referenceId = null,
                    occurredAt = now,
                    paymentMethod = paymentMethod.storageValue,
                ),
            )
            database.domainEventDao().insert(
                event(identity, operationId, "credit.payment.received", now, JSONObject()
                    .put("customer_id", customerId)
                    .put("amount_cents", amountCents)
                    .put("entry_id", operationId)
                    .put("operation_id", operationId)
                    .put("payment_method", paymentMethod.storageValue)),
            )
        }
        syncScheduler.schedule()
        return operationId
    }

    /**
     * Compensates a received payment by appending a positive credit entry.
     * The original payment remains immutable and auditable; this is not a delete.
     */
    suspend fun reverseCreditPayment(
        paymentOperationId: String,
        operationId: String = UuidV7.new(),
    ): String {
        var effectiveOperationId = operationId
        database.withTransaction {
            val payment = creditDao.findById(paymentOperationId)
                ?: error("Pagamento de fiado não encontrado.")
            check(payment.type == CreditEntryType.PAYMENT && payment.amountCents < 0) {
                "A operação informada não é um pagamento de fiado."
            }
            creditDao.findReversalByReference(paymentOperationId)?.let { existing ->
                effectiveOperationId = existing.id
                return@withTransaction
            }
            creditDao.findById(operationId)?.let { existing ->
                check(
                    existing.referenceId == paymentOperationId &&
                        existing.amountCents == -payment.amountCents,
                ) { "A operationId já está associada a outra compensação." }
                effectiveOperationId = existing.id
                return@withTransaction
            }
            val now = System.currentTimeMillis()
            val identity = identityProvider.current()
            creditDao.insert(
                CreditEntryEntity(
                    id = effectiveOperationId,
                    customerId = payment.customerId,
                    amountCents = -payment.amountCents,
                    type = CreditEntryType.SALE,
                    referenceId = paymentOperationId,
                    occurredAt = now,
                    paymentMethod = PaymentMethod.CREDIT.storageValue,
                ),
            )
            database.domainEventDao().insert(
                event(identity, effectiveOperationId, "credit.payment.reversed", now, JSONObject()
                    .put("customer_id", payment.customerId)
                    .put("original_payment_id", paymentOperationId)
                    .put("compensation_entry_id", effectiveOperationId)
                    .put("amount_cents", -payment.amountCents)),
            )
        }
        syncScheduler.schedule()
        return effectiveOperationId
    }

    /** Appends reversal + corrected payment atomically and keeps the original immutable. */
    suspend fun correctCreditPayment(
        originalPaymentOperationId: String,
        amountCents: Long,
        paymentMethod: PaymentMethod,
        operationId: String = UuidV7.new(),
        reversalOperationId: String = UuidV7.new(),
    ): CorrectedCreditPayment {
        require(amountCents > 0) { "O pagamento corrigido precisa ser maior que zero." }
        require(paymentMethod != PaymentMethod.CREDIT && paymentMethod != PaymentMethod.UNKNOWN) {
            "O pagamento do fiado precisa ser recebido em dinheiro, PIX ou maquininha."
        }
        var effectiveReversalId = reversalOperationId
        database.withTransaction {
            val original = creditDao.findById(originalPaymentOperationId)
                ?: error("Pagamento de fiado não encontrado.")
            check(original.type == CreditEntryType.PAYMENT && original.amountCents < 0) {
                "A operação informada não é um pagamento de fiado."
            }
            creditDao.findById(operationId)?.let { existing ->
                check(
                    existing.type == CreditEntryType.PAYMENT &&
                        existing.customerId == original.customerId &&
                        existing.amountCents == -amountCents &&
                        existing.paymentMethod == paymentMethod.storageValue,
                ) { "A operationId já está associada a outra correção." }
                effectiveReversalId = creditDao.findReversalByReference(originalPaymentOperationId)?.id
                    ?: effectiveReversalId
                return@withTransaction
            }
            val now = System.currentTimeMillis()
            val identity = identityProvider.current()
            val existingReversal = creditDao.findReversalByReference(originalPaymentOperationId)
            if (existingReversal != null) {
                effectiveReversalId = existingReversal.id
            } else {
                creditDao.insert(
                    CreditEntryEntity(
                        id = effectiveReversalId,
                        customerId = original.customerId,
                        amountCents = -original.amountCents,
                        type = CreditEntryType.SALE,
                        referenceId = originalPaymentOperationId,
                        occurredAt = now,
                        paymentMethod = PaymentMethod.CREDIT.storageValue,
                    ),
                )
                database.domainEventDao().insert(
                    event(identity, effectiveReversalId, "credit.payment.reversed", now, JSONObject()
                        .put("customer_id", original.customerId)
                        .put("original_payment_id", originalPaymentOperationId)
                        .put("compensation_entry_id", effectiveReversalId)
                        .put("amount_cents", -original.amountCents)
                        .put("reason", "USER_CORRECTION")),
                )
            }
            check(creditDao.balance(original.customerId) >= amountCents) {
                "O pagamento corrigido não pode ser maior que o saldo disponível."
            }
            creditDao.insert(
                CreditEntryEntity(
                    id = operationId,
                    customerId = original.customerId,
                    amountCents = -amountCents,
                    type = CreditEntryType.PAYMENT,
                    referenceId = originalPaymentOperationId,
                    occurredAt = now,
                    paymentMethod = paymentMethod.storageValue,
                ),
            )
            database.domainEventDao().insert(
                event(identity, operationId, "credit.payment.received", now, JSONObject()
                    .put("customer_id", original.customerId)
                    .put("amount_cents", amountCents)
                    .put("entry_id", operationId)
                    .put("operation_id", operationId)
                    .put("payment_method", paymentMethod.storageValue)
                    .put("correction_of", originalPaymentOperationId)),
            )
        }
        syncScheduler.schedule()
        return CorrectedCreditPayment(
            customerId = creditDao.findById(originalPaymentOperationId)!!.customerId,
            reversalOperationId = effectiveReversalId,
            correctedPaymentOperationId = operationId,
        )
    }

    suspend fun registerStockReceipt(
        productId: String,
        quantity: Int,
        unitCostCents: Long,
        supplierId: String? = null,
    ) {
        require(quantity > 0) { "A quantidade precisa ser maior que zero." }
        require(unitCostCents >= 0) { "O custo não pode ser negativo." }
        database.withTransaction {
            productDao.findById(productId) ?: error("Produto não encontrado.")
            supplierId?.let { supplierDao.findById(it) ?: error("Fornecedor não encontrado.") }
            val now = System.currentTimeMillis()
            val identity = identityProvider.current()
            val purchaseId = UuidV7.new()
            val totalCostCents = unitCostCents * quantity
            purchaseDao.insert(PurchaseEntity(purchaseId, supplierId, PurchaseStatus.RECEIVED, totalCostCents, now))
            purchaseDao.insertItems(listOf(PurchaseItemEntity(purchaseId, 0, productId, quantity, unitCostCents)))
            stockMovementDao.insert(StockMovementEntity(UuidV7.new(), productId, quantity, "purchase_receipt", purchaseId, now))
            database.domainEventDao().insert(
                event(identity, purchaseId, "purchase.created", now, JSONObject()
                    .put("purchase_id", purchaseId).putOpt("supplier_id", supplierId)
                    .put("product_id", productId).put("quantity", quantity).put("unit_cost_cents", unitCostCents)
                    .put("total_cost_cents", totalCostCents).put("status", PurchaseStatus.RECEIVED.name)),
            )
            database.domainEventDao().insert(
                event(identity, productId, "stock.received", now, JSONObject()
                    .put("product_id", productId).put("quantity", quantity)
                    .put("purchase_id", purchaseId).put("unit_cost_cents", unitCostCents)),
            )
        }
        syncScheduler.schedule()
    }

    suspend fun createManualOrder(
        productId: String,
        quantity: Int,
        customerName: String?,
        fulfillment: String,
    ) {
        require(quantity > 0) { "A quantidade do pedido precisa ser maior que zero." }
        database.withTransaction {
            val product = productDao.findById(productId) ?: error("Produto não encontrado.")
            val now = System.currentTimeMillis()
            val orderId = UuidV7.new()
            val totalCents = product.priceCents * quantity
            database.orderDao().insert(
                OrderEntity(
                    id = orderId,
                    channel = "MANUAL",
                    fulfillment = fulfillment,
                    customerName = customerName?.trim()?.ifBlank { null },
                    addressReference = null,
                    status = "CONFIRMED",
                    totalCents = totalCents,
                    createdAt = now,
                ),
            )
            database.orderDao().insertItems(
                listOf(OrderItemEntity(orderId, 0, product.id, product.name, quantity, product.priceCents)),
            )
            val identity = identityProvider.current()
            database.domainEventDao().insert(
                event(identity, orderId, "order.created", now, JSONObject()
                    .put("order_id", orderId)
                    .put("channel", "MANUAL")
                    .put("fulfillment", fulfillment)
                    .putOpt("customer_name", customerName)
                    .put("product_id", product.id)
                    .put("quantity", quantity)
                    .put("total_cents", totalCents)),
            )
        }
        syncScheduler.schedule()
    }

    suspend fun updateOrderStatus(orderId: String, status: String) {
        require(status in setOf("CONFIRMED", "PREPARING", "READY", "DELIVERED", "CANCELLED")) {
            "Status de pedido inválido."
        }
        database.withTransaction {
            val order = database.orderDao().findById(orderId) ?: error("Pedido não encontrado.")
            val now = System.currentTimeMillis()
            database.orderDao().updateStatus(orderId, status)
            database.domainEventDao().insert(
                event(
                    identityProvider.current(),
                    orderId,
                    "order.status_changed",
                    now,
                    JSONObject()
                        .put("order_id", orderId)
                        .put("previous_status", order.status)
                        .put("status", status),
                ),
            )
        }
        syncScheduler.schedule()
    }

    private fun event(
        identity: com.tino.app.core.common.InstallationIdentity,
        aggregateId: String,
        type: String,
        occurredAt: Long,
        payload: JSONObject,
    ) = DomainEventEntity(
        eventId = UuidV7.new(),
        storeId = identity.storeId,
        deviceId = identity.deviceId,
        aggregateId = aggregateId,
        type = type,
        schemaVersion = 1,
        occurredAt = occurredAt,
        payloadJson = payload.toString(),
    )

    private fun startOfToday(): Long = LocalDate.now()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
