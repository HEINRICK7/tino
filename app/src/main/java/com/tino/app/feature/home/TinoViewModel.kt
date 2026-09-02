package com.tino.app.feature.home

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tino.app.core.database.ProductSummary
import com.tino.app.core.database.CustomerBalance
import com.tino.app.core.database.SupplierEntity
import com.tino.app.core.database.OrderSummary
import com.tino.app.core.database.OrderDetail
import com.tino.app.core.database.PurchaseEntity
import com.tino.app.domain.commerce.CommerceRepository
import com.tino.app.domain.commerce.PaymentMethod
import com.tino.app.domain.commerce.CustomerCreditTimeline
import com.tino.app.domain.commerce.SharedLedgerStatement
import com.tino.app.domain.commerce.TemporalCreditService
import com.tino.app.domain.profile.StoreProfileRepository
import com.tino.app.domain.profile.BusinessProfile
import com.tino.app.domain.profile.BusinessVertical
import com.tino.app.domain.profile.BusinessModule
import com.tino.app.domain.profile.VerticalPresetCatalog
import com.tino.app.domain.profile.TinoModuleRegistry
import com.tino.app.domain.usecase.ObserveProductsUseCase
import com.tino.app.domain.usecase.RegisterCreditPaymentCommand
import com.tino.app.domain.usecase.RegisterCreditPaymentUseCase
import com.tino.app.domain.intelligence.PredictiveRecommendationService
import com.tino.app.domain.intelligence.Recommendation
import com.tino.app.domain.intelligence.RecommendationDecision
import com.tino.app.domain.intelligence.RecommendationOutcome
import com.tino.app.domain.intelligence.RecommendationRepository
import com.tino.app.domain.intelligence.AttentionRecord
import com.tino.app.domain.intelligence.TinoAttentionEngine
import com.tino.app.domain.intelligence.TinoEvidenceEngine
import com.tino.app.domain.intelligence.TinoEvidenceRepository
import com.tino.app.domain.intelligence.TinoEvidenceSnapshot
import com.tino.app.domain.intelligence.TinoEvidenceSnapshotBuilder
import com.tino.app.core.common.UuidV7
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.database.StoreProfileEntity
import com.tino.app.core.security.SecureTokenStore
import com.tino.app.core.intelligence.AttentionNotificationScheduler
import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.domain.catalog.CatalogSyncState
import com.tino.app.domain.catalog.CatalogSyncStatus
import com.tino.app.domain.catalog.CatalogSyncDiagnostics
import com.tino.app.domain.catalog.SyncCatalog
import com.tino.app.domain.onboarding.BootstrapOnboarding
import com.tino.app.domain.onboarding.OnboardingDataSourceChoice
import com.tino.app.domain.onboarding.OnboardingState
import com.tino.app.domain.onboarding.OtpChallenge
import com.tino.app.domain.onboarding.OtpCodeAttempt
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class TinoViewModel @Inject constructor(
    private val repository: CommerceRepository,
    private val storeProfileRepository: StoreProfileRepository,
    private val temporalCredit: TemporalCreditService,
    private val observeProducts: ObserveProductsUseCase,
    private val registerCreditPayment: RegisterCreditPaymentUseCase,
    private val auditLogger: AuditLogger,
    private val recommendationRepository: RecommendationRepository,
    private val predictiveRecommendations: PredictiveRecommendationService,
    private val intelligenceSnapshotBuilder: TinoEvidenceSnapshotBuilder,
    private val attentionEngine: TinoAttentionEngine,
    private val evidenceRepository: TinoEvidenceRepository,
    private val attentionNotificationScheduler: AttentionNotificationScheduler,
    private val syncCatalog: SyncCatalog,
    private val identityProvider: IdentityProvider,
    private val tokenStore: SecureTokenStore,
    private val bootstrapOnboarding: BootstrapOnboarding,
) : ViewModel() {
    private val _products = MutableStateFlow<List<ProductSummary>>(emptyList())
    val products: StateFlow<List<ProductSummary>> = _products.asStateFlow()

    private val _customers = MutableStateFlow<List<CustomerBalance>>(emptyList())
    val customers: StateFlow<List<CustomerBalance>> = _customers.asStateFlow()

    private val _customerTimeline = MutableStateFlow<CustomerCreditTimeline?>(null)
    val customerTimeline: StateFlow<CustomerCreditTimeline?> = _customerTimeline.asStateFlow()

    private val _customerLedgerStatement = MutableStateFlow<SharedLedgerStatement?>(null)
    val customerLedgerStatement: StateFlow<SharedLedgerStatement?> = _customerLedgerStatement.asStateFlow()

    private val _todayTotalCents = MutableStateFlow(0L)
    val todayTotalCents: StateFlow<Long> = _todayTotalCents.asStateFlow()

    private val _todayReceivedCents = MutableStateFlow(0L)
    val todayReceivedCents: StateFlow<Long> = _todayReceivedCents.asStateFlow()

    private val _todayCashCents = MutableStateFlow(0L)
    val todayCashCents: StateFlow<Long> = _todayCashCents.asStateFlow()

    private val _todayPixCents = MutableStateFlow(0L)
    val todayPixCents: StateFlow<Long> = _todayPixCents.asStateFlow()

    private val _todayCardCents = MutableStateFlow(0L)
    val todayCardCents: StateFlow<Long> = _todayCardCents.asStateFlow()

    private val _todaySalesCount = MutableStateFlow(0)
    val todaySalesCount: StateFlow<Int> = _todaySalesCount.asStateFlow()

    private val _suppliers = MutableStateFlow<List<SupplierEntity>>(emptyList())
    val suppliers: StateFlow<List<SupplierEntity>> = _suppliers.asStateFlow()

    private val _orders = MutableStateFlow<List<OrderSummary>>(emptyList())
    val orders: StateFlow<List<OrderSummary>> = _orders.asStateFlow()

    private val _supplierPurchases = MutableStateFlow<List<PurchaseEntity>>(emptyList())
    val supplierPurchases: StateFlow<List<PurchaseEntity>> = _supplierPurchases.asStateFlow()

    private val _orderDetail = MutableStateFlow<OrderDetail?>(null)
    val orderDetail: StateFlow<OrderDetail?> = _orderDetail.asStateFlow()

    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _catalogSyncState = MutableStateFlow<CatalogSyncState?>(null)
    val catalogSyncState: StateFlow<CatalogSyncState?> = _catalogSyncState.asStateFlow()
    private val _catalogDiagnostics = MutableStateFlow<CatalogSyncDiagnostics?>(null)
    val catalogDiagnostics: StateFlow<CatalogSyncDiagnostics?> = _catalogDiagnostics.asStateFlow()
    private val _storeProfile = MutableStateFlow<StoreProfileEntity?>(null)
    val storeProfile: StateFlow<StoreProfileEntity?> = _storeProfile.asStateFlow()
    private val _profileLoaded = MutableStateFlow(false)
    val profileLoaded: StateFlow<Boolean> = _profileLoaded.asStateFlow()
    private val _authenticated = MutableStateFlow(tokenStore.readSession()?.accessToken?.isNotBlank() == true)
    val authenticated: StateFlow<Boolean> = _authenticated.asStateFlow()
    private val _businessProfile = MutableStateFlow<BusinessProfile?>(null)
    val businessProfile: StateFlow<BusinessProfile?> = _businessProfile.asStateFlow()
    private val _onboardingState = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val onboardingState: StateFlow<OnboardingState> = _onboardingState.asStateFlow()
    private val _remoteBusinessId = MutableStateFlow(identityProvider.current().businessId)
    val remoteBusinessId: StateFlow<String?> = _remoteBusinessId.asStateFlow()
    private var catalogObservationJob: Job? = null
    private var catalogDiagnosticsObservationJob: Job? = null

    private val _recommendations = MutableStateFlow<List<Recommendation>>(emptyList())
    val recommendations: StateFlow<List<Recommendation>> = _recommendations.asStateFlow()

    private val _intelligenceSnapshot = MutableStateFlow<TinoEvidenceSnapshot?>(null)
    val intelligenceSnapshot: StateFlow<TinoEvidenceSnapshot?> = _intelligenceSnapshot.asStateFlow()

    private val _intelligenceRequest = MutableStateFlow(IntelligenceSnapshotRequest(screen = "Home"))

    private val _attentionItems = MutableStateFlow<List<AttentionRecord>>(emptyList())
    val attentionItems: StateFlow<List<AttentionRecord>> = _attentionItems.asStateFlow()
    private val _attentionInitialized = MutableStateFlow(false)
    val attentionInitialized: StateFlow<Boolean> = _attentionInitialized.asStateFlow()

    init {
        identityProvider.current().businessId?.let(::observeCatalogForBusiness)
        viewModelScope.launch {
            observeProducts().collect { products ->
                _products.value = products.map {
                    ProductSummary(it.id, it.name, it.priceCents, it.unit, it.stockQuantity, it.stockQuantityExact, it.stockTracked)
                }
            }
        }
        viewModelScope.launch { repository.observeCustomerBalances().collect { _customers.value = it } }
        viewModelScope.launch { repository.observeTodayTotal().collect { _todayTotalCents.value = it } }
        viewModelScope.launch { repository.observeTodayReceived().collect { _todayReceivedCents.value = it } }
        viewModelScope.launch { repository.observeTodayPayment(PaymentMethod.CASH).collect { _todayCashCents.value = it } }
        viewModelScope.launch { repository.observeTodayPayment(PaymentMethod.PIX).collect { _todayPixCents.value = it } }
        viewModelScope.launch { repository.observeTodayPayment(PaymentMethod.CARD).collect { _todayCardCents.value = it } }
        viewModelScope.launch { repository.observeTodaySalesCount().collect { _todaySalesCount.value = it } }
        viewModelScope.launch { repository.observeSuppliers().collect { _suppliers.value = it } }
        viewModelScope.launch { repository.observeOrders().collect { _orders.value = it } }
        viewModelScope.launch {
            repository.observeSupplierPurchases().collect { _supplierPurchases.value = it }
        }
        viewModelScope.launch { repository.observePendingEventCount().collect { _pendingSyncCount.value = it } }
        viewModelScope.launch {
            storeProfileRepository.observe().collect {
                _storeProfile.value = it
                _profileLoaded.value = true
            }
        }
        viewModelScope.launch {
            storeProfileRepository.observeBusinessProfile().collect {
                _businessProfile.value = it
                it?.let { profile ->
                    auditLogger.record(
                        AuditEventType.DOMAIN_OPERATION,
                        mapOf(
                            "profile_action" to "loaded",
                            "vertical" to profile.primaryVertical.name,
                            "module_count" to profile.enabledModules.size.toString(),
                            "patterns" to profile.effectiveOperationalPatterns().joinToString(",") { it.name },
                            "capability_count" to (TinoModuleRegistry.capabilitiesFor(profile) + profile.permanentCapabilities).size.toString(),
                            "permanent_capability_count" to profile.permanentCapabilities.size.toString(),
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            recommendationRepository.observePending().collect { pending ->
                _recommendations.value = pending
                pending.forEach { recommendation ->
                    recommendationRepository.recordOutcome(
                        recommendationId = recommendation.id,
                        outcome = RecommendationOutcome.SHOWN,
                    )
                }
            }
        }
        viewModelScope.launch {
            runCatching { predictiveRecommendations.generate(System.currentTimeMillis()) }
        }
        viewModelScope.launch {
            val commerceChanges = combine(
                _products,
                _customers,
                _recommendations,
                _supplierPurchases,
            ) { _, _, recommendations, purchases ->
                IntelligenceCommerceChange(recommendations, purchases.size)
            }
            val dailyChanges = combine(
                _todayReceivedCents,
                _todayPixCents,
                _todaySalesCount,
            ) { received, pix, sales -> Triple(received, pix, sales) }
            combine(_intelligenceRequest, commerceChanges, dailyChanges) { request, commerce, daily ->
                IntelligenceRefreshInput(
                    request = request,
                    recommendations = commerce.recommendations,
                    received = daily.first,
                    pix = daily.second,
                    sales = daily.third,
                )
            }.collectLatest { input ->
                val snapshot = runCatching {
                    intelligenceSnapshotBuilder.build(
                        screen = input.request.screen,
                        recommendations = input.recommendations,
                        todayReceivedCents = input.received,
                        todayPixCents = input.pix,
                        todaySales = input.sales,
                        entityProductId = input.request.entityProductId,
                        entityCustomerId = input.request.entityCustomerId,
                    )
                }.getOrNull()
                _intelligenceSnapshot.value = snapshot
                if (snapshot != null && snapshot.screen == "Home") {
                    val analysis = TinoEvidenceEngine.analyze(snapshot)
                    runCatching { evidenceRepository.upsertAll(analysis.evidence) }
                    runCatching {
                        attentionEngine.reconcile(analysis, snapshot.nowEpochMs)
                    }.onSuccess { items ->
                        _attentionItems.value = items
                        _attentionInitialized.value = true
                        attentionNotificationScheduler.refreshNow()
                    }
                }
            }
        }
    }

    private data class IntelligenceRefreshInput(
        val request: IntelligenceSnapshotRequest,
        val recommendations: List<Recommendation>,
        val received: Long,
        val pix: Long,
        val sales: Int,
    )

    private data class IntelligenceSnapshotRequest(
        val screen: String,
        val entityProductId: String? = null,
        val entityCustomerId: String? = null,
    )

    private data class IntelligenceCommerceChange(
        val recommendations: List<Recommendation>,
        val purchaseCount: Int,
    )

    /**
     * Requests the same governed evidence pipeline for the screen currently
     * visible. The UI must never reconstruct a reduced intelligence snapshot
     * from presentation lists because that drops temporal, supplier,
     * financial and memory evidence.
     */
    fun refreshIntelligenceSnapshot(
        screen: String,
        entityProductId: String? = null,
        entityCustomerId: String? = null,
    ) {
        _intelligenceRequest.value = IntelligenceSnapshotRequest(
            screen = screen,
            entityProductId = entityProductId,
            entityCustomerId = entityCustomerId,
        )
    }

    private suspend fun refreshSupplierPurchases() {
        _supplierPurchases.value = repository.purchasesForIntelligence()
    }

    fun dismissAttention(attentionId: String) {
        viewModelScope.launch {
            attentionEngine.dismiss(attentionId)
            _attentionItems.value = _attentionItems.value.filterNot { it.id == attentionId }
        }
    }

    fun snoozeAttention(attentionId: String) {
        viewModelScope.launch {
            runCatching {
                attentionEngine.snooze(attentionId, System.currentTimeMillis() + 24L * 60L * 60L * 1_000L)
            }.onSuccess {
                _attentionItems.value = _attentionItems.value.filterNot { it.id == attentionId }
            }
        }
    }

    fun actionAttention(attentionId: String) {
        viewModelScope.launch { attentionEngine.actioned(attentionId) }
    }

    fun decideRecommendation(recommendation: Recommendation, accepted: Boolean) {
        viewModelScope.launch {
            val outcome = if (accepted) RecommendationOutcome.ACCEPTED else RecommendationOutcome.REJECTED
            val updated = recommendationRepository.updateDecision(
                recommendation.id,
                if (accepted) RecommendationDecision.ACCEPTED else RecommendationDecision.REJECTED,
            )
            if (updated != null) {
                recommendationRepository.recordOutcome(recommendation.id, outcome)
            }
        }
    }

    fun saveStoreProfile(
        storeName: String,
        ownerName: String,
        phone: String,
        vertical: BusinessVertical = BusinessVertical.RETAIL,
        activeModules: Set<BusinessModule> = VerticalPresetCatalog.forVertical(vertical).defaultModules,
    ) {
        viewModelScope.launch {
            runCatching { storeProfileRepository.save(storeName, ownerName, phone, vertical, activeModules) }
                .onFailure { _message.value = it.message ?: "Não foi possível salvar o cadastro." }
        }
    }

    fun completeOnboarding(
        activity: Activity,
        storeName: String,
        ownerName: String,
        phone: String,
        vertical: BusinessVertical,
        activeModules: Set<BusinessModule>,
        dataSource: OnboardingDataSourceChoice,
        otpCodeProvider: suspend (OtpChallenge) -> OtpCodeAttempt,
    ) {
        if (_onboardingState.value !is OnboardingState.Idle &&
            _onboardingState.value !is OnboardingState.Error
        ) return
        viewModelScope.launch {
            try {
                bootstrapOnboarding.complete(
                    activity = activity,
                    tradeName = storeName,
                    vertical = vertical.name,
                    phone = phone,
                    dataSource = dataSource,
                    otpCodeProvider = otpCodeProvider,
                    onStage = { stage -> _onboardingState.value = stage },
                ).also { result ->
                    _remoteBusinessId.value = result.business.id
                    _authenticated.value = true
                    storeProfileRepository.save(storeName, ownerName, phone, vertical, activeModules)
                    observeCatalogForBusiness(result.business.id)
                }
                _onboardingState.value = OnboardingState.Ready
            } catch (error: CancellationException) {
                _onboardingState.value = OnboardingState.Idle
                throw error
            } catch (error: Throwable) {
                _onboardingState.value = OnboardingState.Error(bootstrapOnboarding.userMessage(error))
            }
        }
    }

    fun reauthenticateExistingBusiness(
        activity: Activity,
        phone: String,
        otpCodeProvider: suspend (OtpChallenge) -> OtpCodeAttempt,
    ) {
        if (_onboardingState.value !is OnboardingState.Idle &&
            _onboardingState.value !is OnboardingState.Error
        ) return
        viewModelScope.launch {
            try {
                bootstrapOnboarding.reauthenticateExistingBusiness(
                    activity = activity,
                    phone = phone,
                    otpCodeProvider = otpCodeProvider,
                    onStage = { stage -> _onboardingState.value = stage },
                ).also { result ->
                    _remoteBusinessId.value = result.business.id
                    _authenticated.value = true
                    observeCatalogForBusiness(result.business.id)
                }
                _onboardingState.value = OnboardingState.Ready
            } catch (error: CancellationException) {
                _onboardingState.value = OnboardingState.Idle
                throw error
            } catch (error: Throwable) {
                _onboardingState.value = OnboardingState.Error(bootstrapOnboarding.userMessage(error))
            }
        }
    }

    fun syncCatalog() {
        viewModelScope.launch(Dispatchers.IO) {
            val businessId = identityProvider.current().businessId
            if (businessId == null) {
                _message.value = "Finalize o cadastro do comércio para sincronizar o catálogo."
                return@launch
            }
            runCatching { syncCatalog(businessId) }
                .onSuccess { result ->
                    _message.value = when {
                        result.possiblyPartial -> "Catálogo atualizado parcialmente. Há mais itens no backend."
                        result.rejected > 0 -> "Catálogo atualizado com ${result.rejected} item(ns) rejeitado(s)."
                        else -> "Catálogo atualizado: ${result.accepted} item(ns)."
                    }
                }
                .onFailure { _message.value = it.message ?: "Não foi possível atualizar o catálogo." }
        }
    }

    private fun observeCatalogForBusiness(businessId: String) {
        catalogObservationJob?.cancel()
        catalogDiagnosticsObservationJob?.cancel()
        catalogObservationJob = viewModelScope.launch {
            syncCatalog.observe(businessId).collect { _catalogSyncState.value = it }
        }
        catalogDiagnosticsObservationJob = viewModelScope.launch {
            syncCatalog.observeDiagnostics(businessId).collect { _catalogDiagnostics.value = it }
        }
    }

    fun updateBusinessProfile(profile: BusinessProfile) {
        viewModelScope.launch { updateBusinessProfileAndWait(profile) }
    }

    suspend fun updateBusinessProfileAndWait(profile: BusinessProfile): Result<Unit> = runCatching {
        storeProfileRepository.updateProfile(profile)
        auditLogger.record(
            AuditEventType.DOMAIN_OPERATION,
            mapOf(
                "profile_action" to "updated",
                "vertical" to profile.primaryVertical.name,
                "module_count" to profile.enabledModules.size.toString(),
                "patterns" to profile.effectiveOperationalPatterns().joinToString(",") { it.name },
                "permanent_capability_count" to profile.permanentCapabilities.size.toString(),
            ),
        )
    }.onSuccess {
        _message.value = "Configuração do negócio atualizada."
    }.onFailure {
        _message.value = it.message ?: "Não foi possível atualizar o negócio."
    }

    fun addProduct(name: String, price: String, stock: String) {
        viewModelScope.launch { addProductAndWait(name, price, stock) }
    }

    suspend fun addProductAndWait(name: String, price: String, stock: String): Result<Unit> = runCatching {
        repository.createProduct(
            name = name,
            priceCents = price.toCents(),
            initialStock = stock.toIntOrNull() ?: 0,
        )
        Unit
    }.onSuccess {
        _message.value = "Produto salvo neste aparelho."
    }.onFailure {
        _message.value = it.message ?: "Não foi possível salvar o produto."
    }

    fun loadCustomerTimeline(customerId: String?) {
        if (customerId == null) {
            _customerTimeline.value = null
            _customerLedgerStatement.value = null
            return
        }
        viewModelScope.launch {
            _customerTimeline.value = runCatching { temporalCredit.customerTimeline(customerId) }.getOrNull()
            _customerLedgerStatement.value = runCatching { repository.sharedLedgerStatement(customerId) }.getOrNull()
        }
    }

    fun sellOne(product: ProductSummary) {
        sell(product, 1)
    }

    fun sell(
        product: ProductSummary,
        quantity: Int,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
    ) {
        viewModelScope.launch { sellAndWait(product, quantity, paymentMethod) }
    }

    suspend fun sellAndWait(
        product: ProductSummary,
        quantity: Int,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
    ): Result<Unit> = runCatching {
        repository.registerSale(product.id, quantity, paymentMethod)
    }.onSuccess {
        _message.value = "Venda salva. Estoque atualizado."
    }.onFailure {
        _message.value = it.message ?: "Não foi possível registrar a venda."
    }

    fun addCustomer(name: String, phone: String? = null) {
        viewModelScope.launch { addCustomerAndWait(name, phone) }
    }

    suspend fun addCustomerAndWait(name: String, phone: String? = null): Result<Unit> = runCatching {
        repository.createCustomer(name, phone)
        Unit
    }.onSuccess {
        _message.value = "Cliente salvo neste aparelho."
    }.onFailure {
        _message.value = it.message ?: "Não foi possível salvar o cliente."
    }

    fun updateCustomer(customer: CustomerBalance, name: String, phone: String?) {
        viewModelScope.launch { updateCustomerAndWait(customer, name, phone) }
    }

    suspend fun updateCustomerAndWait(customer: CustomerBalance, name: String, phone: String?): Result<Unit> = runCatching {
        repository.updateCustomer(customer.id, name, phone)
        Unit
    }.onSuccess {
        _message.value = "Cliente atualizado neste aparelho."
    }.onFailure {
        _message.value = it.message ?: "Não foi possível atualizar o cliente."
    }

    fun addSupplier(name: String, phone: String? = null) {
        viewModelScope.launch { addSupplierAndWait(name, phone) }
    }

    suspend fun addSupplierAndWait(name: String, phone: String? = null): Result<Unit> = runCatching {
        repository.createSupplier(name, phone)
        Unit
    }.onSuccess {
        _message.value = "Fornecedor salvo neste aparelho."
    }.onFailure {
        _message.value = it.message ?: "Não foi possível salvar o fornecedor."
    }

    suspend fun createSupplierOrderAndWait(
        productId: String,
        quantity: Int,
        unitCostCents: Long,
        supplierId: String,
        expectedDeliveryAt: Long,
    ): Result<Unit> = runCatching {
        repository.createSupplierOrder(productId, quantity, unitCostCents, supplierId, expectedDeliveryAt)
        refreshSupplierPurchases()
    }.onSuccess {
        _message.value = "Pedido ao fornecedor registrado neste aparelho."
    }.onFailure {
        _message.value = it.message ?: "Não foi possível registrar o pedido ao fornecedor."
    }

    suspend fun receiveSupplierOrderAndWait(purchaseId: String): Result<Unit> = runCatching {
        repository.receiveSupplierOrder(purchaseId)
        refreshSupplierPurchases()
    }.onSuccess {
        _message.value = "Entrega do fornecedor registrada."
    }.onFailure {
        _message.value = it.message ?: "Não foi possível registrar a entrega."
    }

    suspend fun createOrderAndWait(
        productId: String,
        quantity: Int,
        customerName: String?,
        fulfillment: String,
    ): Result<Unit> = runCatching {
        repository.createManualOrder(productId, quantity, customerName, fulfillment)
    }.onSuccess {
        _message.value = "Pedido criado neste aparelho."
    }.onFailure {
        _message.value = it.message ?: "Não foi possível criar o pedido."
    }

    fun openOrder(orderId: String) {
        viewModelScope.launch { _orderDetail.value = repository.findOrderDetail(orderId) }
    }

    suspend fun updateOrderStatusAndWait(orderId: String, status: String): Result<Unit> = runCatching {
        repository.updateOrderStatus(orderId, status)
        _orderDetail.value = repository.findOrderDetail(orderId)
    }.onSuccess {
        _message.value = "Status do pedido atualizado."
    }.onFailure {
        _message.value = it.message ?: "Não foi possível atualizar o pedido."
    }

    fun sellOnCredit(product: ProductSummary, customerName: String, quantity: Int) {
        viewModelScope.launch { sellOnCreditAndWait(product, customerName, quantity) }
    }

    suspend fun sellOnCreditAndWait(
        product: ProductSummary,
        customerName: String,
        quantity: Int,
    ): Result<Unit> = runCatching {
        val customer = repository.findCustomerByName(customerName)
            ?: error("Cliente não encontrado.")
        repository.registerCreditSale(customer.id, product.id, quantity)
    }.onSuccess {
        _message.value = "Fiado salvo. Estoque atualizado."
    }.onFailure {
        _message.value = it.message ?: "Não foi possível registrar o fiado."
    }
    fun receivePayment(customer: CustomerBalance, amount: String) {
        viewModelScope.launch { receivePaymentAndWait(customer, amount) }
    }

    suspend fun receivePaymentAndWait(customer: CustomerBalance, amount: String): Result<Unit> = runCatching {
        registerCreditPayment(
            RegisterCreditPaymentCommand(
                customerId = customer.id,
                amountCents = amount.toCents(),
                paymentMethod = PaymentMethod.CASH,
                operationId = UuidV7.new(),
            ),
        )
        Unit
    }.onSuccess {
        _message.value = "Pagamento salvo."
    }.onFailure {
        _message.value = it.message ?: "Não foi possível registrar o pagamento."
    }
    fun receiveStock(productName: String, quantity: String, cost: String, supplierName: String) {
        viewModelScope.launch { receiveStockAndWait(productName, quantity, cost, supplierName) }
    }

    suspend fun receiveStockAndWait(
        productName: String,
        quantity: String,
        cost: String,
        supplierName: String,
    ): Result<Unit> = runCatching {
        val product = repository.findProductByName(productName) ?: error("Produto não encontrado.")
        val supplier = supplierName.trim().takeIf { it.isNotEmpty() }
            ?.let { repository.findSupplierByName(it) ?: error("Fornecedor não encontrado.") }
        repository.registerStockReceipt(product.id, quantity.toInt(), cost.toCents(), supplier?.id)
        Unit
    }.onSuccess {
        _message.value = "Entrada de mercadoria salva."
    }.onFailure {
        _message.value = it.message ?: "Não foi possível registrar a entrada."
    }

    fun clearMessage() {
        _message.value = null
    }
}

private fun String.toCents(): Long {
    val normalized = trim().replace("R$", "").replace(" ", "").replace(',', '.')
    return BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
}
