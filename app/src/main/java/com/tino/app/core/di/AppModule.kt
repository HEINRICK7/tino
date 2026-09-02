package com.tino.app.core.di

import com.tino.app.BuildConfig
import android.content.Context
import androidx.room.Room
import com.tino.agent.contracts.CreditPreparationFactsPort
import com.tino.app.core.agent.AndroidCreditPreparationFactsAdapter
import com.tino.app.core.intelligence.RoomCommerceIntelligenceFacts
import com.tino.app.core.database.MIGRATION_1_2
import com.tino.app.core.database.MIGRATION_2_3
import com.tino.app.core.database.MIGRATION_3_4
import com.tino.app.core.database.MIGRATION_4_5
import com.tino.app.core.database.MIGRATION_5_6
import com.tino.app.core.database.MIGRATION_6_7
import com.tino.app.core.database.MIGRATION_7_8
import com.tino.app.core.database.MIGRATION_8_9
import com.tino.app.core.database.MIGRATION_9_10
import com.tino.app.core.database.MIGRATION_10_11
import com.tino.app.core.database.MIGRATION_11_12
import com.tino.app.core.database.MIGRATION_12_13
import com.tino.app.core.database.MIGRATION_13_14
import com.tino.app.core.database.MIGRATION_14_15
import com.tino.app.core.database.MIGRATION_15_16
import com.tino.app.core.database.MIGRATION_16_17
import com.tino.app.core.database.MIGRATION_17_18
import com.tino.app.core.database.MIGRATION_18_19
import com.tino.app.core.database.MIGRATION_19_20
import com.tino.app.core.database.MIGRATION_20_21
import com.tino.app.core.database.MIGRATION_21_22
import com.tino.app.core.database.MIGRATION_22_23
import com.tino.app.core.database.MIGRATION_23_24
import com.tino.app.core.database.MIGRATION_24_25
import com.tino.app.core.database.MIGRATION_25_26
import com.tino.app.core.database.MIGRATION_26_27
import com.tino.app.core.database.MIGRATION_27_28
import com.tino.app.core.database.MIGRATION_28_29
import com.tino.app.core.database.CustomerDao
import com.tino.app.core.database.CreditDao
import com.tino.app.core.database.DomainEventDao
import com.tino.app.core.database.DirectReceiptDao
import com.tino.app.core.database.FinancialProjectionDao
import com.tino.app.core.database.ProductDao
import com.tino.app.core.database.PurchaseDao
import com.tino.app.core.database.OrderDao
import com.tino.app.core.database.SaleDao
import com.tino.app.core.database.StockMovementDao
import com.tino.app.core.database.SupplierDao
import com.tino.app.core.database.SyncCursorDao
import com.tino.app.core.database.StoreProfileDao
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.database.CatalogSyncStateDao
import com.tino.app.core.database.FiscalImportDao
import com.tino.app.core.database.SupplierProductMappingDao
import com.tino.app.core.database.ProductPurchaseHistoryDao
import com.tino.app.core.database.GoodsReceiptOperationDao
import com.tino.app.core.database.RemoteGoodsReceiptDao
import com.tino.app.core.database.RemoteProductMappingDao
import com.tino.app.core.database.AgentActivityDao
import com.tino.app.core.database.RoomAgentActivityRepository
import com.tino.app.core.database.IntelligenceTelemetryDao
import com.tino.app.core.database.RoomIntelligenceTelemetryRepository
import com.tino.app.core.database.InteractionStateDao
import com.tino.app.core.database.MutationOperationDao
import com.tino.app.core.database.BusinessMemoryDao
import com.tino.app.core.database.RoomMutationOperationStore
import com.tino.app.core.database.RoomInteractionStateStore
import com.tino.app.core.database.RoomBusinessMemoryRepository
import com.tino.app.core.database.RecommendationDao
import com.tino.app.core.database.RoomRecommendationRepository
import com.tino.app.core.database.AttentionDao
import com.tino.app.core.database.RoomAttentionRepository
import com.tino.app.core.database.IntelligenceEvidenceDao
import com.tino.app.core.database.RoomApprovedKnowledgeCatalog
import com.tino.app.core.database.RoomTinoEvidenceRepository
import com.tino.app.domain.agent.AgentActivityRepository
import com.tino.app.domain.intelligence.IntelligenceTelemetryPort
import com.tino.app.core.sync.SyncGateway
import com.tino.app.core.sync.SyncScheduler
import com.tino.app.core.sync.RestSyncGateway
import com.tino.app.core.sync.UnavailableSyncGateway
import com.tino.app.core.network.GoodsReceiptApi
import com.tino.app.core.network.RestGoodsReceiptApi
import com.tino.app.core.network.UnavailableGoodsReceiptApi
import com.tino.app.core.network.UrlConnectionBackendTransport
import com.tino.app.core.network.NfcePurchaseDocumentApi
import com.tino.app.core.network.RestNfcePurchaseDocumentApi
import com.tino.app.core.network.UnavailableNfcePurchaseDocumentApi
import com.tino.app.core.network.RestCatalogApi
import com.tino.app.core.network.UnavailableCatalogApi
import com.tino.app.core.network.RestBootstrapApi
import com.tino.app.core.network.UnavailableBootstrapApi
import com.tino.app.core.network.RestBusinessDataSourceApi
import com.tino.app.core.network.UnavailableBusinessDataSourceApi
import com.tino.app.core.network.RestOtpAuthApi
import com.tino.app.core.network.UnavailableOtpAuthApi
import com.tino.app.core.auth.OidcAuthCoordinator
import com.tino.app.domain.onboarding.BootstrapApi
import com.tino.app.domain.onboarding.BusinessDataSourceApi
import com.tino.app.domain.onboarding.OtpAuthApi as OtpAuthPort
import com.tino.app.domain.catalog.CatalogApi
import com.tino.app.domain.catalog.CatalogProductStore
import com.tino.app.domain.catalog.CatalogSyncStateStore
import com.tino.app.core.catalog.RoomCatalogProductStore
import com.tino.app.core.catalog.RoomCatalogSyncStateStore
import com.tino.app.core.sync.WorkManagerSyncScheduler
import com.tino.app.core.security.SecureTokenStore
import com.tino.app.domain.agent.AgentIntentInterpreter
import com.tino.app.domain.agent.AgenticTextQueryCoordinator
import com.tino.app.domain.agent.AgenticTextQueryPort
import com.tino.app.domain.agent.AgentProgressRuntime
import com.tino.app.domain.agent.AgentStreamingRuntime
import com.tino.app.domain.agent.AgentQueryBoundary
import com.tino.app.domain.agent.TinoAgentBoundary
import com.tino.app.domain.agent.CustomerBalanceQueryPort
import com.tino.app.domain.agent.CustomerBalanceQueryTool
import com.tino.app.domain.agent.CustomerTimelineQueryPort
import com.tino.app.domain.agent.CustomerTimelineQueryTool
import com.tino.app.domain.agent.DbFirstReadCapabilities
import com.tino.app.domain.agent.DbFirstReadCapabilityService
import com.tino.app.core.observability.AuditLogger
import com.tino.app.core.observability.RedactedAuditLogger
import com.tino.app.domain.voice.CommerceToolDispatcher
import com.tino.app.domain.voice.MutationSafeToolExecutor
import com.tino.app.domain.voice.MutationSafetyCoordinator
import com.tino.app.domain.voice.MutationSafetyPort
import com.tino.app.domain.voice.MutationOperationStore
import com.tino.app.domain.voice.MutationConfirmationPort
import com.tino.app.domain.voice.MutationConfirmationService
import com.tino.app.domain.voice.ToolExecutor
import com.tino.app.domain.orders.CatalogLookup
import com.tino.app.domain.orders.CommerceCatalogLookup
import com.tino.app.core.speech.LiveTranscriber
import com.tino.app.core.speech.SpeechTranscriberRuntime
import com.tino.app.core.speech.LiveTranscriberPort
import com.tino.app.core.speech.AndroidSpeechRecognizerRuntime
import com.tino.app.core.speech.ManualVoiceInputAdapter
import com.tino.app.domain.agent.DeterministicAgentIntentInterpreter
import com.tino.app.domain.voice.VoiceInputPort
import com.tino.app.domain.intelligence.BusinessAnalyticsPort
import com.tino.app.domain.intelligence.DeterministicBusinessAnalytics
import com.tino.app.domain.intelligence.DeterministicIntelligencePlanExecutor
import com.tino.app.domain.intelligence.DeterministicIntelligencePlanValidator
import com.tino.app.domain.intelligence.DeterministicIntelligenceQueryPlanner
import com.tino.app.domain.intelligence.DeterministicIntelligenceRuntime
import com.tino.app.domain.intelligence.IntelligenceFactsPort
import com.tino.app.domain.intelligence.IntelligenceRuntimePort
import com.tino.app.domain.intelligence.InMemoryLongTermMemory
import com.tino.app.domain.intelligence.KnowledgeQueryPort
import com.tino.app.domain.intelligence.MemoryPort
import com.tino.app.domain.intelligence.LocalApprovedKnowledgeAdapter
import com.tino.app.domain.intelligence.ApprovedKnowledgeCatalogPort
import com.tino.app.domain.intelligence.KnowledgeRetrievalMetricsPort
import com.tino.app.domain.intelligence.InMemoryKnowledgeRetrievalMetrics
import com.tino.app.domain.intelligence.IntelligencePlanExecutor
import com.tino.app.domain.intelligence.IntelligencePlanValidator
import com.tino.app.domain.intelligence.LocalHeuristicRecommendationEngine
import com.tino.app.domain.intelligence.RecommendationEngine
import com.tino.app.domain.intelligence.RecommendationRepository
import com.tino.app.domain.intelligence.AttentionRepository
import com.tino.app.domain.intelligence.TinoEvidenceRepository
import com.tino.app.core.intelligence.AttentionNotificationScheduler
import com.tino.app.core.intelligence.WorkManagerAttentionNotificationScheduler
import com.tino.app.domain.intelligence.PlannerPort
import com.tino.app.domain.intelligence.agent.AgentRuntimePort
import com.tino.app.domain.intelligence.agent.DefaultAgentRuntime
import com.tino.app.domain.intelligence.agent.AgentDecisionPolicy
import com.tino.app.domain.intelligence.agent.DeterministicAgentDecisionPolicy
import com.tino.app.domain.agent.InteractionStateStore
import com.tino.app.domain.language.AdaptiveLexicon
import com.tino.app.domain.language.AdaptiveLexiconPort
import com.tino.app.domain.language.BusinessMemoryPort
import com.tino.app.domain.language.BusinessMemoryStorePort
import com.tino.app.domain.language.BusinessMemoryPolicy
import com.tino.app.domain.language.DefaultBusinessMemoryPolicy
import com.tino.app.domain.language.GovernedBusinessMemory
import com.tino.app.domain.intelligence.presentation.DeterministicUiPlanner
import com.tino.app.domain.intelligence.presentation.UiPlannerPort
import com.tino.app.interfaceadapter.a2ui.A2uiComposerPort
import com.tino.app.interfaceadapter.a2ui.DeterministicA2uiComposer
import com.tino.app.interfaceadapter.a2ui.TinoUiPlanner
import com.tino.app.interfaceadapter.a2ui.TinoUiPlannerPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAgentProgressRuntime(): AgentProgressRuntime = AgentProgressRuntime()

    @Provides
    @Singleton
    fun provideAgentStreamingRuntime(): AgentStreamingRuntime = AgentStreamingRuntime()

    @Provides
    @Singleton
    fun provideFinancialClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TinoDatabase =
        Room.databaseBuilder(context, TinoDatabase::class.java, "tino.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29)
            .build()

    @Provides fun provideProductDao(database: TinoDatabase): ProductDao = database.productDao()
    @Provides fun provideCatalogSyncStateDao(database: TinoDatabase): CatalogSyncStateDao = database.catalogSyncStateDao()
    @Provides fun provideGoodsReceiptOperationDao(database: TinoDatabase): GoodsReceiptOperationDao = database.goodsReceiptOperationDao()
    @Provides fun provideRemoteGoodsReceiptDao(database: TinoDatabase): RemoteGoodsReceiptDao = database.remoteGoodsReceiptDao()
    @Provides fun provideRemoteProductMappingDao(database: TinoDatabase): RemoteProductMappingDao = database.remoteProductMappingDao()
    @Provides fun provideSaleDao(database: TinoDatabase): SaleDao = database.saleDao()
    @Provides fun provideDirectReceiptDao(database: TinoDatabase): DirectReceiptDao = database.directReceiptDao()
    @Provides fun provideFinancialProjectionDao(database: TinoDatabase): FinancialProjectionDao = database.financialProjectionDao()
    @Provides fun provideStockMovementDao(database: TinoDatabase): StockMovementDao = database.stockMovementDao()
    @Provides fun provideDomainEventDao(database: TinoDatabase): DomainEventDao = database.domainEventDao()
    @Provides fun provideCustomerDao(database: TinoDatabase): CustomerDao = database.customerDao()
    @Provides fun provideSupplierDao(database: TinoDatabase): SupplierDao = database.supplierDao()
    @Provides fun provideCreditDao(database: TinoDatabase): CreditDao = database.creditDao()
    @Provides fun providePurchaseDao(database: TinoDatabase): PurchaseDao = database.purchaseDao()
    @Provides fun provideOrderDao(database: TinoDatabase): OrderDao = database.orderDao()
    @Provides fun provideSyncCursorDao(database: TinoDatabase): SyncCursorDao = database.syncCursorDao()
    @Provides fun provideStoreProfileDao(database: TinoDatabase): StoreProfileDao = database.storeProfileDao()
    @Provides fun provideFiscalImportDao(database: TinoDatabase): FiscalImportDao = database.fiscalImportDao()
    @Provides fun provideSupplierProductMappingDao(database: TinoDatabase): SupplierProductMappingDao = database.supplierProductMappingDao()
    @Provides fun provideProductPurchaseHistoryDao(database: TinoDatabase): ProductPurchaseHistoryDao = database.productPurchaseHistoryDao()
    @Provides fun provideAgentActivityDao(database: TinoDatabase): AgentActivityDao = database.agentActivityDao()
    @Provides fun provideIntelligenceTelemetryDao(database: TinoDatabase): IntelligenceTelemetryDao = database.intelligenceTelemetryDao()
    @Provides fun provideInteractionStateDao(database: TinoDatabase): InteractionStateDao = database.interactionStateDao()
    @Provides fun provideMutationOperationDao(database: TinoDatabase): MutationOperationDao = database.mutationOperationDao()
    @Provides fun provideBusinessMemoryDao(database: TinoDatabase): BusinessMemoryDao = database.businessMemoryDao()
    @Provides fun provideRecommendationDao(database: TinoDatabase): RecommendationDao = database.recommendationDao()
    @Provides fun provideAttentionDao(database: TinoDatabase): AttentionDao = database.attentionDao()
    @Provides fun provideIntelligenceEvidenceDao(database: TinoDatabase): IntelligenceEvidenceDao = database.intelligenceEvidenceDao()
    @Provides
    @Singleton
    fun provideAgentActivityRepository(implementation: RoomAgentActivityRepository): AgentActivityRepository = implementation

    @Provides
    @Singleton
    fun provideIntelligenceTelemetry(implementation: RoomIntelligenceTelemetryRepository): IntelligenceTelemetryPort = implementation

    @Provides
    @Singleton
    fun provideInteractionStateStore(implementation: RoomInteractionStateStore): InteractionStateStore = implementation

    @Provides
    @Singleton
    fun provideBusinessMemoryStore(implementation: RoomBusinessMemoryRepository): BusinessMemoryStorePort = implementation

    @Provides
    @Singleton
    fun provideBusinessMemoryPolicy(implementation: DefaultBusinessMemoryPolicy): BusinessMemoryPolicy = implementation

    @Provides
    @Singleton
    fun provideBusinessMemory(implementation: GovernedBusinessMemory): BusinessMemoryPort = implementation

    @Provides
    @Singleton
    fun provideAdaptiveLexicon(implementation: AdaptiveLexicon): AdaptiveLexiconPort = implementation

    @Provides
    @Singleton
    fun provideUiPlanner(implementation: DeterministicUiPlanner): UiPlannerPort = implementation

    @Provides
    @Singleton
    fun provideTinoUiPlanner(implementation: TinoUiPlanner): TinoUiPlannerPort = implementation

    @Provides
    @Singleton
    fun provideA2uiComposer(implementation: DeterministicA2uiComposer): A2uiComposerPort = implementation

    @Provides
    @Singleton
    fun provideSyncGateway(tokenStore: SecureTokenStore): SyncGateway = BuildConfig.TINO_SYNC_BASE_URL
        .takeIf { it.isNotBlank() }
        ?.let { RestSyncGateway(it, tokenStore) }
        ?: UnavailableSyncGateway()

    @Provides
    @Singleton
    fun provideGoodsReceiptApi(
        tokenStore: SecureTokenStore,
        auth: OidcAuthCoordinator,
    ): GoodsReceiptApi = BuildConfig.TINO_BACKEND_BASE_URL
        .takeIf { it.isNotBlank() }
        ?.let { RestGoodsReceiptApi(it, UrlConnectionBackendTransport(it, tokenStore, tokenRefresher = auth)) }
        ?: UnavailableGoodsReceiptApi()

    @Provides
    @Singleton
    fun provideNfcePurchaseDocumentApi(
        tokenStore: SecureTokenStore,
        auth: OidcAuthCoordinator,
    ): NfcePurchaseDocumentApi = BuildConfig.TINO_BACKEND_BASE_URL
        .takeIf { it.isNotBlank() }
        ?.let { RestNfcePurchaseDocumentApi(UrlConnectionBackendTransport(it, tokenStore, tokenRefresher = auth)) }
        ?: UnavailableNfcePurchaseDocumentApi()

    @Provides
    @Singleton
    fun provideCatalogApi(
        tokenStore: SecureTokenStore,
        auth: OidcAuthCoordinator,
    ): CatalogApi = BuildConfig.TINO_BACKEND_BASE_URL
        .takeIf { it.isNotBlank() }
        ?.let { RestCatalogApi(it, UrlConnectionBackendTransport(it, tokenStore, tokenRefresher = auth)) }
        ?: UnavailableCatalogApi()

    @Provides
    @Singleton
    fun provideBootstrapApi(
        tokenStore: SecureTokenStore,
        auth: OidcAuthCoordinator,
    ): BootstrapApi = BuildConfig.TINO_BACKEND_BASE_URL
        .takeIf { it.isNotBlank() }
        ?.let { RestBootstrapApi(it, UrlConnectionBackendTransport(it, tokenStore, tokenRefresher = auth)) }
        ?: UnavailableBootstrapApi()

    @Provides
    @Singleton
    fun provideBusinessDataSourceApi(
        tokenStore: SecureTokenStore,
        auth: OidcAuthCoordinator,
    ): BusinessDataSourceApi = BuildConfig.TINO_BACKEND_BASE_URL
        .takeIf { it.isNotBlank() }
        ?.let { RestBusinessDataSourceApi(it, UrlConnectionBackendTransport(it, tokenStore, tokenRefresher = auth)) }
        ?: UnavailableBusinessDataSourceApi()

    @Provides
    @Singleton
    fun provideOtpAuthApi(tokenStore: SecureTokenStore): OtpAuthPort = BuildConfig.TINO_BACKEND_BASE_URL
        .takeIf { it.isNotBlank() }
        ?.let { RestOtpAuthApi(it, UrlConnectionBackendTransport(it, tokenStore)) }
        ?: UnavailableOtpAuthApi()

    @Provides
    fun provideCatalogProductStore(implementation: RoomCatalogProductStore): CatalogProductStore = implementation

    @Provides
    fun provideCatalogSyncStateStore(implementation: RoomCatalogSyncStateStore): CatalogSyncStateStore = implementation

    @Provides
    fun provideSyncScheduler(implementation: WorkManagerSyncScheduler): SyncScheduler = implementation

    @Provides
    fun provideAttentionNotificationScheduler(
        implementation: WorkManagerAttentionNotificationScheduler,
    ): AttentionNotificationScheduler = implementation

    @Provides
    @Singleton
    fun provideAgentIntentInterpreter(): AgentIntentInterpreter = DeterministicAgentIntentInterpreter()

    @Provides
    @Singleton
    fun provideAgentQueryBoundary(
        implementation: TinoAgentBoundary,
    ): AgentQueryBoundary = implementation

    @Provides
    @Singleton
    fun provideCustomerBalanceQuery(
        implementation: CustomerBalanceQueryTool,
    ): CustomerBalanceQueryPort = implementation

    @Provides
    @Singleton
    fun provideCustomerTimelineQuery(
        implementation: CustomerTimelineQueryTool,
    ): CustomerTimelineQueryPort = implementation

    @Provides
    @Singleton
    fun provideDbFirstReadCapabilities(
        implementation: DbFirstReadCapabilityService,
    ): DbFirstReadCapabilities = implementation

    @Provides
    @Singleton
    fun provideAuditLogger(implementation: RedactedAuditLogger): AuditLogger = implementation

    @Provides
    @Singleton
    fun provideAgenticTextQueryPort(
        implementation: AgenticTextQueryCoordinator,
    ): AgenticTextQueryPort = implementation

    @Provides
    @Singleton
    fun provideIntelligenceFactsPort(implementation: RoomCommerceIntelligenceFacts): IntelligenceFactsPort = implementation

    @Provides
    @Singleton
    fun provideIntelligenceAnalytics(implementation: DeterministicBusinessAnalytics): BusinessAnalyticsPort = implementation

    @Provides
    @Singleton
    fun provideRecommendationEngine(implementation: LocalHeuristicRecommendationEngine): RecommendationEngine = implementation

    @Provides
    @Singleton
    fun provideRecommendationRepository(implementation: RoomRecommendationRepository): RecommendationRepository = implementation

    @Provides
    @Singleton
    fun provideAttentionRepository(implementation: RoomAttentionRepository): AttentionRepository = implementation

    @Provides
    @Singleton
    fun provideTinoEvidenceRepository(implementation: RoomTinoEvidenceRepository): TinoEvidenceRepository = implementation

    @Provides
    @Singleton
    fun provideIntelligenceMemory(implementation: InMemoryLongTermMemory): MemoryPort = implementation

    @Provides
    @Singleton
    fun provideApprovedKnowledgeCatalog(
        database: TinoDatabase,
    ): ApprovedKnowledgeCatalogPort = RoomApprovedKnowledgeCatalog(
        dao = database.approvedKnowledgeCatalogDao(),
        database = database,
    )

    @Provides
    @Singleton
    fun provideKnowledgeRetrievalMetrics(): KnowledgeRetrievalMetricsPort = InMemoryKnowledgeRetrievalMetrics()

    @Provides
    @Singleton
    fun provideKnowledgeQueryPort(implementation: LocalApprovedKnowledgeAdapter): KnowledgeQueryPort = implementation

    @Provides
    @Singleton
    fun provideDeterministicIntelligencePlanner(): DeterministicIntelligenceQueryPlanner =
        DeterministicIntelligenceQueryPlanner()

    @Provides
    @Singleton
    fun provideIntelligencePlanner(
        deterministic: DeterministicIntelligenceQueryPlanner,
    ): PlannerPort = deterministic

    @Provides
    @Singleton
    fun provideIntelligencePlanValidator(): IntelligencePlanValidator = DeterministicIntelligencePlanValidator()

    @Provides
    @Singleton
    fun provideIntelligencePlanExecutor(
        facts: IntelligenceFactsPort,
        analytics: BusinessAnalyticsPort,
        knowledge: KnowledgeQueryPort,
        clock: Clock,
        recommendationEngine: RecommendationEngine,
        recommendationRepository: RecommendationRepository,
    ): IntelligencePlanExecutor = DeterministicIntelligencePlanExecutor(
        facts = facts,
        analytics = analytics,
        knowledge = knowledge,
        clock = clock,
        recommendationEngine = recommendationEngine,
        recommendationRepository = recommendationRepository,
    )

    @Provides
    @Singleton
    fun provideIntelligenceRuntime(implementation: DeterministicIntelligenceRuntime): IntelligenceRuntimePort = implementation

    @Provides
    @Singleton
    fun provideAgentRuntime(implementation: DefaultAgentRuntime): AgentRuntimePort = implementation

    @Provides
    @Singleton
    fun provideAgentDecisionPolicy(implementation: DeterministicAgentDecisionPolicy): AgentDecisionPolicy = implementation


    @Provides
    @Singleton
    fun provideCreditPreparationFactsPort(
        implementation: AndroidCreditPreparationFactsAdapter,
    ): CreditPreparationFactsPort = implementation

    @Provides
    @Singleton
    fun provideSpeechTranscriberRuntime(
        implementation: AndroidSpeechRecognizerRuntime,
    ): SpeechTranscriberRuntime = implementation

    @Provides
    @Singleton
    fun provideLiveTranscriber(implementation: LiveTranscriber): LiveTranscriberPort = implementation

    @Provides
    @Singleton
    fun provideVoiceInputPort(implementation: ManualVoiceInputAdapter): VoiceInputPort = implementation

    @Provides
    fun provideMutationSafety(implementation: MutationSafetyCoordinator): MutationSafetyPort = implementation

    @Provides
    @Singleton
    fun provideMutationOperationStore(implementation: RoomMutationOperationStore): MutationOperationStore = implementation

    @Provides
    @Singleton
    fun provideMutationConfirmationPort(implementation: MutationConfirmationService): MutationConfirmationPort = implementation

    @Provides
    fun provideToolExecutor(
        dispatcher: CommerceToolDispatcher,
        safety: MutationSafetyPort,
    ): ToolExecutor = MutationSafeToolExecutor(dispatcher, safety)

    @Provides
    fun provideCatalogLookup(implementation: CommerceCatalogLookup): CatalogLookup = implementation
}
