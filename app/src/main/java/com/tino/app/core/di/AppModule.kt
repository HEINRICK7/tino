package com.tino.app.core.di

import com.tino.app.BuildConfig
import android.content.Context
import androidx.room.Room
import com.tino.agent.contracts.CreditPreparationFactsPort
import com.tino.agent.contracts.CreditPlanInferencePort
import com.tino.app.core.agent.AndroidCreditPreparationFactsAdapter
import com.tino.app.core.agent.AndroidGemmaCreditPlanInferenceAdapter
import com.tino.app.core.intelligence.RoomCommerceIntelligenceFacts
import com.tino.app.core.intelligence.GoogleAdkGemmaPlanProposal
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
import com.tino.app.core.database.FiscalImportDao
import com.tino.app.core.database.SupplierProductMappingDao
import com.tino.app.core.database.ProductPurchaseHistoryDao
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
import com.tino.app.domain.agent.AgentActivityRepository
import com.tino.app.domain.intelligence.IntelligenceTelemetryPort
import com.tino.app.core.sync.SyncGateway
import com.tino.app.core.sync.SyncScheduler
import com.tino.app.core.sync.RestSyncGateway
import com.tino.app.core.sync.UnavailableSyncGateway
import com.tino.app.core.sync.WorkManagerSyncScheduler
import com.tino.app.core.security.SecureTokenStore
import com.tino.app.domain.voice.GemmaOrchestrator
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
import com.tino.app.core.speech.GemmaLiveTranscriber
import com.tino.app.core.speech.GemmaTranscriberRuntime
import com.tino.app.core.speech.LiveTranscriberPort
import com.tino.app.core.speech.UnavailableGemmaTranscriberRuntime
import com.tino.app.core.speech.AndroidSpeechRecognizerRuntime
import com.tino.app.core.speech.GemmaStructuredExtractor
import com.tino.app.core.speech.GemmaVoiceInputAdapter
import com.tino.app.core.speech.MediaPipeGemmaStructuredExtractor
import com.tino.app.core.speech.MediaPipeGemmaOrchestrator
import com.tino.app.core.speech.MediaPipeGemmaAgentIntentAdapter
import com.tino.app.core.speech.GemmaTextInference
import com.tino.app.core.speech.MediaPipeGemmaTextInference
import com.tino.app.domain.voice.VoiceInputPort
import com.tino.app.domain.intelligence.BusinessAnalyticsPort
import com.tino.app.domain.intelligence.DeterministicBusinessAnalytics
import com.tino.app.domain.intelligence.DeterministicIntelligencePlanExecutor
import com.tino.app.domain.intelligence.DeterministicIntelligencePlanValidator
import com.tino.app.domain.intelligence.DeterministicIntelligenceQueryPlanner
import com.tino.app.domain.intelligence.DeterministicIntelligenceRuntime
import com.tino.app.domain.intelligence.GoogleAdkOrchestratorPort
import com.tino.app.domain.intelligence.GoogleAdkRuntimeAdapter
import com.tino.app.domain.intelligence.UnavailableGoogleAdkOrchestrator
import com.tino.app.domain.intelligence.IntelligenceFactsPort
import com.tino.app.domain.intelligence.IntelligenceRuntimePort
import com.tino.app.domain.intelligence.InMemoryLongTermMemory
import com.tino.app.domain.intelligence.KnowledgeQueryPort
import com.tino.app.domain.intelligence.MemoryPort
import com.tino.app.domain.intelligence.UnavailableKnowledgeAdapter
import com.tino.app.domain.intelligence.IntelligencePlanExecutor
import com.tino.app.domain.intelligence.IntelligencePlanValidator
import com.tino.app.domain.intelligence.LocalHeuristicRecommendationEngine
import com.tino.app.domain.intelligence.RecommendationEngine
import com.tino.app.domain.intelligence.PlannerPort
import com.tino.app.domain.intelligence.AdkPlanProposalPort
import com.tino.app.domain.intelligence.AdkQueryPlanner
import com.tino.app.domain.intelligence.agent.AgentRuntimePort
import com.tino.app.domain.intelligence.agent.AdkAgentRuntime
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
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
            .build()

    @Provides fun provideProductDao(database: TinoDatabase): ProductDao = database.productDao()
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
    fun provideSyncScheduler(implementation: WorkManagerSyncScheduler): SyncScheduler = implementation

    @Provides
    fun provideGemmaOrchestrator(implementation: MediaPipeGemmaOrchestrator): GemmaOrchestrator = implementation

    @Provides
    @Singleton
    fun provideAgentIntentInterpreter(
        implementation: MediaPipeGemmaAgentIntentAdapter,
    ): AgentIntentInterpreter = implementation

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
    fun provideIntelligenceMemory(implementation: InMemoryLongTermMemory): MemoryPort = implementation

    @Provides
    @Singleton
    fun provideKnowledgeQueryPort(implementation: UnavailableKnowledgeAdapter): KnowledgeQueryPort = implementation

    @Provides
    @Singleton
    fun provideDeterministicIntelligencePlanner(): DeterministicIntelligenceQueryPlanner =
        DeterministicIntelligenceQueryPlanner()

    @Provides
    @Singleton
    fun provideAdkPlanProposal(
        implementation: GoogleAdkGemmaPlanProposal,
    ): AdkPlanProposalPort = implementation

    @Provides
    @Singleton
    fun provideIntelligencePlanner(
        proposal: AdkPlanProposalPort,
        deterministic: DeterministicIntelligenceQueryPlanner,
    ): PlannerPort = AdkQueryPlanner(
        proposalPort = proposal,
        deterministicFallback = deterministic,
    )

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
    ): IntelligencePlanExecutor = DeterministicIntelligencePlanExecutor(
        facts = facts,
        analytics = analytics,
        knowledge = knowledge,
        clock = clock,
        recommendationEngine = recommendationEngine,
    )

    @Provides
    @Singleton
    fun provideGoogleAdkOrchestrator(implementation: UnavailableGoogleAdkOrchestrator): GoogleAdkOrchestratorPort = implementation

    @Provides
    @Singleton
    fun provideIntelligenceRuntime(implementation: GoogleAdkRuntimeAdapter): IntelligenceRuntimePort = implementation

    @Provides
    @Singleton
    fun provideAgentRuntime(implementation: AdkAgentRuntime): AgentRuntimePort = implementation

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
    fun provideCreditPlanInferencePort(
        implementation: AndroidGemmaCreditPlanInferenceAdapter,
    ): CreditPlanInferencePort = implementation

    @Provides
    @Singleton
    fun provideGemmaTextInference(
        implementation: MediaPipeGemmaTextInference,
    ): GemmaTextInference = implementation

    @Provides
    @Singleton
    fun provideGemmaTranscriberRuntime(
        implementation: AndroidSpeechRecognizerRuntime,
    ): GemmaTranscriberRuntime = implementation

    @Provides
    @Singleton
    fun provideLiveTranscriber(implementation: GemmaLiveTranscriber): LiveTranscriberPort = implementation

    @Provides
    @Singleton
    fun provideGemmaStructuredExtractor(
        implementation: MediaPipeGemmaStructuredExtractor,
    ): GemmaStructuredExtractor = implementation

    @Provides
    @Singleton
    fun provideVoiceInputPort(implementation: GemmaVoiceInputAdapter): VoiceInputPort = implementation

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
