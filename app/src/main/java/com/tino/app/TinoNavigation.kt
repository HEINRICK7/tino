package com.tino.app

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tino.app.core.database.CustomerBalance
import com.tino.app.core.database.ProductSummary
import com.tino.app.core.database.StoreProfileEntity
import com.tino.app.core.database.SupplierEntity
import com.tino.app.core.database.OrderSummary
import com.tino.fiscal.core.ProductImportResult
import com.tino.app.R
import com.tino.app.feature.home.TinoViewModel
import com.tino.app.feature.voice.ContextualVoiceState
import com.tino.app.feature.voice.ContextualVoiceViewModel
import com.tino.app.feature.voice.AgenticVoiceState
import com.tino.app.feature.voice.AgenticVoiceViewModel
import com.tino.app.feature.voice.AgenticVoiceMetrics
import com.tino.app.feature.voice.TinoAgentSessionViewModel
import com.tino.app.feature.fiscal.DocumentScannerScreen
import com.tino.app.feature.fiscal.DocumentUploadScreen
import com.tino.app.feature.receiving.NfeKeyEntryScreen
import com.tino.app.feature.receiving.NfePreviewScreen
import com.tino.app.feature.nfce.NfceCaptureScreen
import com.tino.app.domain.nfce.PurchaseDocument
import com.tino.app.domain.nfce.PurchaseDocumentPreview
import com.tino.app.domain.nfce.PurchaseReceipt
import com.tino.app.domain.nfce.PurchaseHistory
import com.tino.app.domain.nfce.PurchaseHistoryDetail
import com.tino.app.domain.nfce.PurchaseInsight
import com.tino.app.domain.receiving.GoodsReceiptConfirmation
import com.tino.app.domain.receiving.GoodsReceiptPreview
import com.tino.app.domain.receiving.GoodsReceiptRemoteState
import com.tino.app.domain.receiving.ProductSearchItem
import com.tino.app.domain.catalog.CatalogSyncState
import com.tino.app.domain.catalog.CatalogSyncDiagnostics
import com.tino.app.domain.voice.VoiceContext
import com.tino.app.ui.a2ui.TinoA2UiRenderer
import com.tino.app.ui.a2ui.TinoContextualCatalogSurface
import com.tino.app.ui.a2ui.TinoQuickCreateOption
import com.tino.app.ui.a2ui.TinoCreateBottomSheet
import com.tino.app.ui.a2ui.TinoThoughtsSurface
import com.tino.app.ui.a2ui.TinoVoiceBackgroundSurface
import com.tino.app.ui.a2ui.isVoiceBackground
import com.tino.app.ui.a2ui.presentsBottomRiseCatalog
import com.tino.app.domain.commerce.PaymentMethod
import com.tino.app.domain.commerce.CustomerCreditTimeline
import com.tino.app.domain.commerce.SharedLedgerStatement
import com.tino.app.domain.intelligence.Recommendation
import com.tino.app.ui.components.TinoBottomNavigation
import com.tino.app.ui.components.TinoCard
import com.tino.app.ui.components.TinoCustomerRow
import com.tino.app.ui.components.TinoEmptyState
import com.tino.app.ui.components.TinoIconButton
import com.tino.app.ui.components.TinoInsightCard
import com.tino.app.ui.components.TinoQuickActionCard
import com.tino.app.ui.components.TinoSectionHeader
import com.tino.app.ui.components.TinoHorizontalCarousel
import com.tino.app.ui.components.TinoActionTile
import com.tino.app.ui.components.TinoMetricCard
import com.tino.app.ui.components.TinoMenuRow
import com.tino.app.ui.components.TinoMenuCard
import com.tino.app.ui.components.TinoMoneyField
import com.tino.app.ui.components.TinoNavDestination
import com.tino.app.ui.components.TinoOfflineBanner
import com.tino.app.ui.components.TinoOrderRow
import com.tino.app.ui.components.TinoPrimaryButton
import com.tino.app.ui.components.TinoProductRow
import com.tino.app.ui.components.TinoQuantitySelector
import com.tino.app.ui.components.TinoSearchField
import com.tino.app.ui.components.TinoSaleProductRow
import com.tino.app.ui.components.TinoSecondaryButton
import com.tino.app.ui.components.TinoSectionLabel
import com.tino.app.ui.components.TinoStatus
import com.tino.app.ui.components.TinoStatusBadge
import com.tino.app.ui.components.TinoFilterChip
import com.tino.app.ui.components.TinoSupplierRow
import com.tino.app.ui.components.TinoSyncIndicator
import com.tino.app.ui.components.TinoTextField
import com.tino.app.ui.components.TinoTopBar
import com.tino.app.ui.components.TinoVoiceCard
import com.tino.app.ui.components.TinoVoiceFab
import com.tino.app.ui.components.TinoMascotFab
import com.tino.app.ui.components.LocalTinoScrollTelemetry
import com.tino.app.ui.components.TinoScrollTelemetry
import com.tino.app.ui.components.LocalTinoInteractionBoundsRegistry
import com.tino.app.ui.components.TinoInteractionBoundsRegistry
import com.tino.app.ui.components.tinoInteractionRoot
import com.tino.app.ui.components.TinoMotionHost
import com.tino.app.ui.components.LocalTinoAnimatedVisibilityScope
import com.tino.app.ui.components.tinoScreenContentTransform
import com.tino.app.ui.components.TinoVoiceFabState
import com.tino.app.ui.components.TinoLogo
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoGreenBright
import com.tino.app.ui.theme.TinoGreenBorder
import com.tino.app.ui.theme.TinoGreenDark
import com.tino.app.ui.theme.TinoGreenLight
import com.tino.app.ui.theme.TinoAmberContainer
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoOrange
import com.tino.app.ui.theme.TinoPaper
import com.tino.app.ui.theme.TinoRed
import com.tino.app.ui.theme.TinoRedContainer
import com.tino.app.ui.theme.TinoBlue
import com.tino.app.ui.theme.TinoSize
import com.tino.app.ui.theme.TinoSpacing
import com.tino.app.ui.theme.TinoSurface
import com.tino.app.ui.theme.TinoGreenTint
import com.tino.app.ui.theme.TinoShapes
import com.tino.app.ui.theme.TinoTheme
import com.tino.app.ui.theme.LocalTinoReduceMotion

import com.tino.app.core.ui.AppOrientationController
import com.tino.app.presentation.splash.TinoSplashScreen
import com.tino.app.domain.agent.ScreenAgentContext
import com.tino.app.domain.intelligence.TinoEvidenceEngine
import com.tino.app.domain.intelligence.TinoEvidenceSnapshot
import com.tino.app.domain.intelligence.TinoThought
import com.tino.app.domain.intelligence.AttentionRecord
import com.tino.app.domain.language.EntityReference
import com.tino.app.domain.language.LanguageEntityType
import com.tino.app.domain.agent.TinoCapabilityRegistry
import com.tino.app.domain.agent.AgentCapability
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.agent.TinoAgentSessionSnapshot
import com.tino.app.domain.agent.TinoPresenceMode
import com.tino.app.domain.agent.TinoPresenceState
import com.tino.app.domain.profile.BusinessProfile
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.util.Locale



internal fun visibleNavigationDestinations(
    activeCapabilities: Set<TinoCapabilityId>,
): Set<TinoNavDestination> = buildSet {
    add(TinoNavDestination.Hoje)
    if (TinoCapabilityId.LIST_PRODUCTS in activeCapabilities) {
        add(TinoNavDestination.Produtos)
    }
    if (TinoCapabilityId.LIST_RECEIVABLES in activeCapabilities) {
        add(TinoNavDestination.Fiado)
    }
    add(TinoNavDestination.Mais)
}

internal fun visibleIntelligenceThoughts(
    screen: TinoScreen,
    snapshot: TinoEvidenceSnapshot?,
    attentionItems: List<AttentionRecord>,
    attentionInitialized: Boolean,
    entityProductId: String? = null,
    entityCustomerId: String? = null,
): List<TinoThought> {
    val screenSnapshot = snapshot?.takeIf { it.screen == screen.name } ?: return emptyList()
    val rankedThoughts = TinoEvidenceEngine.analyze(
        screenSnapshot.copy(
            entityProductId = entityProductId,
            entityCustomerId = entityCustomerId,
        ),
    ).visibleThoughts
    return if (screen == TinoScreen.Home && attentionInitialized) {
        rankedThoughts.filter { thought -> attentionItems.any { it.id == thought.id } }
    } else {
        rankedThoughts
    }
}

internal enum class TinoScreen {
    Splash, FirstAccess, RestoreStore,
    Home, QuickQueries, Understood, Correction, Completed,
    QuickSale, ReceiveSale, SelectCustomer, ConfirmCredit,
    CreditList, Customers, CustomerDetail, CustomerAccount, ReceivePayment,
    Products, ProductDetail, AdjustStock, NewProduct,
    StockEntry, FiscalFound, NfceCapture, NfeKeyEntry, NfePreview, FiscalReview, DocumentCamera, DocumentUpload, PurchaseSuggestions, SupplierOrder, Suppliers,
    Orders, NewOrder, OrderDetail, Picking, Delivery, PurchaseHistory,
    Insights, DailySummary, AskTino, SyncDetails, More, CatalogDiagnostics, Settings, A2uiValidation, G311MutationSafety, G312Memory, G4AgentLoop, G5BusinessMemory,
    BusinessProfileSettings,
    Offline, VoiceError, Ambiguity, Notification,
}

internal fun TinoScreen.transitionLayer(): Int = when (this) {
    TinoScreen.Splash, TinoScreen.FirstAccess, TinoScreen.RestoreStore -> 0
    TinoScreen.Home, TinoScreen.Products, TinoScreen.CreditList, TinoScreen.More, TinoScreen.Customers,
    TinoScreen.QuickQueries, TinoScreen.Insights, TinoScreen.DailySummary, TinoScreen.AskTino,
    TinoScreen.SyncDetails, TinoScreen.Settings, TinoScreen.CatalogDiagnostics, TinoScreen.BusinessProfileSettings, TinoScreen.Offline,
    TinoScreen.Notification, TinoScreen.A2uiValidation, TinoScreen.G311MutationSafety, TinoScreen.G312Memory,
    TinoScreen.G4AgentLoop, TinoScreen.G5BusinessMemory, TinoScreen.PurchaseSuggestions, TinoScreen.PurchaseHistory, TinoScreen.Ambiguity,
    TinoScreen.VoiceError, TinoScreen.Understood, TinoScreen.Correction, TinoScreen.Completed,
    -> 1
    TinoScreen.ProductDetail, TinoScreen.CustomerDetail, TinoScreen.CustomerAccount, TinoScreen.NewProduct,
    TinoScreen.StockEntry, TinoScreen.FiscalFound, TinoScreen.NfceCapture, TinoScreen.NfeKeyEntry, TinoScreen.NfePreview, TinoScreen.DocumentCamera, TinoScreen.DocumentUpload,
    TinoScreen.Orders, TinoScreen.Suppliers, TinoScreen.NewOrder, TinoScreen.QuickSale, TinoScreen.ReceiveSale,
    TinoScreen.SelectCustomer, TinoScreen.SupplierOrder, TinoScreen.Picking, TinoScreen.Delivery,
    -> 2
    TinoScreen.AdjustStock, TinoScreen.FiscalReview, TinoScreen.OrderDetail, TinoScreen.ConfirmCredit,
    TinoScreen.ReceivePayment,
    -> 3
}

/** Single authorization map used by direct navigation and the Agentic Shell. */
internal fun TinoScreen.requiredCapability(): TinoCapabilityId? = when (this) {
    TinoScreen.QuickSale,
    TinoScreen.ReceiveSale,
    TinoScreen.Orders,
    TinoScreen.NewOrder,
    -> TinoCapabilityId.NAVIGATE
    TinoScreen.Products,
    TinoScreen.ProductDetail,
    TinoScreen.AdjustStock,
    TinoScreen.NewProduct,
    -> TinoCapabilityId.LIST_PRODUCTS
    TinoScreen.StockEntry -> TinoCapabilityId.REGISTER_STOCK_ENTRY
    TinoScreen.CreditList,
    TinoScreen.CustomerAccount,
    TinoScreen.ReceivePayment,
    TinoScreen.SelectCustomer,
    TinoScreen.ConfirmCredit,
    -> TinoCapabilityId.LIST_RECEIVABLES
    TinoScreen.Customers,
    TinoScreen.CustomerDetail,
    -> TinoCapabilityId.LIST_CUSTOMERS
    else -> null
}

internal data class SaleLine(val product: ProductSummary, val quantity: Int)

internal data class TinoCompletion(
    val title: String = "Operação concluída",
    val detail: String = "Tudo foi salvo com sucesso.",
)

internal fun tinoScreenAgentContext(
    screen: TinoScreen,
    selectedCustomer: CustomerBalance?,
    selectedProduct: ProductSummary?,
    activeCapabilities: Set<TinoCapabilityId>,
    tags: Set<String> = emptySet(),
): ScreenAgentContext = ScreenAgentContext(
    screen = screen.name,
    activeCustomerId = selectedCustomer?.id,
    activeProductId = selectedProduct?.id,
    primaryEntity = when (screen) {
        TinoScreen.CustomerDetail,
        TinoScreen.CustomerAccount,
        TinoScreen.ReceivePayment,
        -> selectedCustomer?.let { EntityReference(LanguageEntityType.CUSTOMER, it.name) }
        TinoScreen.ProductDetail,
        TinoScreen.AdjustStock,
        -> selectedProduct?.let { EntityReference(LanguageEntityType.PRODUCT, it.name) }
        else -> null
    },
    availableCapabilities = activeCapabilities,
    tags = tags,
)

private fun quickCreateOptions(
    screen: TinoScreen,
    activeCapabilities: Set<TinoCapabilityId>,
    onNavigate: (TinoScreen) -> Unit,
    onRequestCustomerCreate: () -> Unit,
): List<TinoQuickCreateOption> {
    fun option(
        icon: ImageVector,
        title: String,
        description: String,
        target: TinoScreen,
    ) = TinoQuickCreateOption(icon, title, description) { onNavigate(target) }
    fun customerOption(target: TinoScreen) = TinoQuickCreateOption(
        TinoIcons.Person,
        "Novo cliente",
        "Adicionar à caderneta",
    ) { onRequestCustomerCreate(); onNavigate(target) }

    val canProducts = TinoCapabilityId.LIST_PRODUCTS in activeCapabilities
    val canCustomers = TinoCapabilityId.LIST_CUSTOMERS in activeCapabilities
    val canStock = TinoCapabilityId.REGISTER_STOCK_ENTRY in activeCapabilities
    val canNavigate = TinoCapabilityId.NAVIGATE in activeCapabilities
    return when (screen) {
        TinoScreen.Home -> buildList {
            if (canProducts) add(option(TinoIcons.Products, "Novo produto", "Cadastrar no estoque", TinoScreen.NewProduct))
            if (canCustomers) add(customerOption(TinoScreen.Customers))
            if (canStock) add(option(TinoIcons.Supplier, "Nova entrada", "Registrar mercadoria", TinoScreen.StockEntry))
            if (canNavigate) add(option(TinoIcons.Orders, "Novo pedido", "Criar um pedido", TinoScreen.NewOrder))
            add(option(TinoIcons.Supplier, "Novo fornecedor", "Cadastrar quem abastece", TinoScreen.Suppliers))
        }
        TinoScreen.Products -> buildList {
            if (canProducts) add(option(TinoIcons.Products, "Novo produto", "Cadastrar no estoque", TinoScreen.NewProduct))
            if (canStock) add(option(TinoIcons.Supplier, "Entrada de mercadoria", "Atualizar quantidades", TinoScreen.StockEntry))
        }
        TinoScreen.Customers -> if (canCustomers) listOf(
            customerOption(TinoScreen.Customers),
        ) else emptyList()
        TinoScreen.CreditList -> buildList {
            if (canCustomers) add(customerOption(TinoScreen.CreditList))
            if (canNavigate) add(option(TinoIcons.Credit, "Novo fiado", "Registrar uma venda em aberto", TinoScreen.QuickSale))
        }
        TinoScreen.Orders -> buildList {
            if (canNavigate) add(option(TinoIcons.Orders, "Novo pedido", "Criar um pedido", TinoScreen.NewOrder))
        }
        TinoScreen.Suppliers -> buildList {
            add(option(TinoIcons.Supplier, "Novo fornecedor", "Cadastrar quem abastece", TinoScreen.Suppliers))
            if (canProducts) add(option(TinoIcons.Cart, "Comprar", "Encontrar o que precisa repor", TinoScreen.PurchaseSuggestions))
        }
        TinoScreen.More -> buildList {
            if (canNavigate) add(option(TinoIcons.Orders, "Novo pedido", "Criar um pedido", TinoScreen.NewOrder))
            add(option(TinoIcons.Supplier, "Novo fornecedor", "Cadastrar quem abastece", TinoScreen.Suppliers))
            if (canProducts) add(option(TinoIcons.Cart, "Comprar", "Encontrar o que precisa repor", TinoScreen.PurchaseSuggestions))
        }
        else -> emptyList()
    }
}

@Composable
internal fun MainShell(
    screen: TinoScreen,
    onNavigate: (TinoScreen) -> Unit,
    saleLines: List<SaleLine>,
    onSaleLinesChanged: (List<SaleLine>) -> Unit,
    selectedCustomer: CustomerBalance?,
    onCustomerSelected: (CustomerBalance) -> Unit,
    selectedProduct: ProductSummary?,
    onProductSelected: (ProductSummary) -> Unit,
    completion: TinoCompletion,
    onCompletionChanged: (TinoCompletion) -> Unit,
    fiscalDocumentCaptured: Boolean,
    fiscalImportResult: ProductImportResult?,
    fiscalRectifiedPath: String?,
    fiscalUploadUri: Uri?,
    message: String?,
    onClearMessage: () -> Unit,
    products: List<ProductSummary>,
    catalogSyncState: CatalogSyncState? = null,
    catalogDiagnostics: CatalogSyncDiagnostics? = null,
    onSyncCatalog: () -> Unit = {},
    customers: List<CustomerBalance>,
    customerTimeline: CustomerCreditTimeline?,
    customerLedgerStatement: SharedLedgerStatement? = null,
    todayTotalCents: Long,
    todayReceivedCents: Long,
    todayCashCents: Long,
    todayPixCents: Long,
    todayCardCents: Long,
    todaySales: Int,
    pendingSyncCount: Int,
    suppliers: List<SupplierEntity>,
    supplierPurchases: List<com.tino.app.core.database.PurchaseEntity> = emptyList(),
    orders: List<OrderSummary>,
    orderDetail: com.tino.app.core.database.OrderDetail?,
    storeProfile: StoreProfileEntity?,
    contextualVoiceState: ContextualVoiceState,
    agenticVoiceState: AgenticVoiceState,
    sharedAgentSnapshot: TinoAgentSessionSnapshot = TinoAgentSessionSnapshot(),
    agentPresence: TinoPresenceState = TinoPresenceState(),
    onContextualVoiceStart: (VoiceContext) -> Unit,
    onContextualVoiceStop: () -> Unit,
    onAgenticVoiceStart: () -> Unit,
    onAgenticVoiceStop: () -> Unit,
    onAgenticVoiceCancel: () -> Unit,
    onAgenticSubmitText: (String) -> Unit,
    onAgenticActionConfirm: (AgenticVoiceState.ActionPreview) -> Unit,
    onAgenticUndo: (String) -> Unit,
    onAgenticEntityChoiceSelected: (AgenticVoiceState.EntityChoice, String) -> Unit,
    onAgenticTranscriptEdit: () -> Unit,
    onAgenticTranscriptChange: (String) -> Unit,
    onAgenticTranscriptEditCancel: () -> Unit,
    onAgenticTranscriptContinue: () -> Unit,
    onAgenticTranscriptSubmit: () -> Unit,
    onAgenticCapabilitySubmit: (AgentCapability, String, String?) -> Unit,
    onAgenticCapabilityUseOnce: () -> Unit = {},
    onAgenticCapabilityActivate: (TinoCapabilityId) -> Unit = {},
    businessProfile: BusinessProfile? = null,
    recommendations: List<Recommendation> = emptyList(),
    intelligenceSnapshot: TinoEvidenceSnapshot? = null,
    attentionItems: List<AttentionRecord> = emptyList(),
    attentionInitialized: Boolean = false,
    onDismissAttention: (String) -> Unit = {},
    onSnoozeAttention: (String) -> Unit = {},
    onActionAttention: (String) -> Unit = {},
    onRecommendationDecision: (Recommendation, Boolean) -> Unit = { _, _ -> },
    activeCapabilities: Set<TinoCapabilityId> = TinoCapabilityId.values().toSet(),
    onUpdateBusinessProfile: suspend (BusinessProfile) -> Result<Unit> = { Result.success(Unit) },
    onAddProduct: suspend (String, String, String) -> Result<Unit>,
    onSell: suspend (ProductSummary, Int, PaymentMethod) -> Result<Unit>,
    onAddCustomer: suspend (String, String?) -> Result<Unit>,
    onUpdateCustomer: suspend (CustomerBalance, String, String?) -> Result<Unit>,
    onCreditSale: suspend (ProductSummary, String, Int) -> Result<Unit>,
    onReceivePayment: suspend (CustomerBalance, String) -> Result<Unit>,
    onReceiveStock: suspend (String, String, String, String) -> Result<Unit>,
    onAddSupplier: suspend (String, String?) -> Result<Unit>,
    onCreateSupplierOrder: suspend (String, Int, Long, String, Long) -> Result<Unit> = { _, _, _, _, _ -> Result.success(Unit) },
    onReceiveSupplierOrder: suspend (String) -> Result<Unit> = { Result.success(Unit) },
    onCreateOrder: suspend (String, Int, String?, String) -> Result<Unit>,
    onOpenOrder: (String) -> Unit,
    onUpdateOrderStatus: suspend (String, String) -> Result<Unit>,
    onFiscalDocumentProcessed: (ProductImportResult, String?) -> Unit,
    onFiscalImageSelected: (Uri) -> Unit,
    onNfceDocumentCaptured: suspend (PurchaseDocument) -> PurchaseDocumentPreview? = { null },
    onNfcePreviewConfirmed: suspend (PurchaseDocumentPreview) -> PurchaseReceipt? = { null },
    onLoadPurchaseHistory: suspend (String) -> PurchaseHistory = { error("Histórico não configurado.") },
    onLoadPurchaseHistoryDetail: suspend (String) -> PurchaseHistoryDetail = { error("Detalhe não configurado.") },
    onLoadPurchaseInsights: suspend (String) -> List<PurchaseInsight> = { emptyList() },
    goodsReceiptState: GoodsReceiptRemoteState = GoodsReceiptRemoteState.Idle,
    goodsReceiptSearchResults: List<ProductSearchItem> = emptyList(),
    onSubmitNfeKey: (String) -> Unit = {},
    onRetryNfe: () -> Unit = {},
    onSearchNfeProducts: (String) -> Unit = {},
    onConfirmNfe: (GoodsReceiptPreview, GoodsReceiptConfirmation) -> Unit = { _, _ -> },
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollTelemetry = remember { TinoScrollTelemetry() }
    val interactionBoundsRegistry = remember { TinoInteractionBoundsRegistry() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message!!)
            onClearMessage()
        }
    }
    val root = when (screen) {
        TinoScreen.Products, TinoScreen.ProductDetail, TinoScreen.AdjustStock, TinoScreen.NewProduct,
        TinoScreen.StockEntry, TinoScreen.FiscalFound, TinoScreen.NfceCapture, TinoScreen.NfeKeyEntry, TinoScreen.NfePreview, TinoScreen.FiscalReview, TinoScreen.DocumentCamera, TinoScreen.DocumentUpload -> TinoNavDestination.Produtos
        TinoScreen.CreditList, TinoScreen.CustomerAccount, TinoScreen.ReceivePayment,
        TinoScreen.SelectCustomer, TinoScreen.ConfirmCredit -> TinoNavDestination.Fiado
        TinoScreen.More, TinoScreen.CatalogDiagnostics, TinoScreen.Customers, TinoScreen.CustomerDetail, TinoScreen.Settings, TinoScreen.BusinessProfileSettings, TinoScreen.PurchaseSuggestions, TinoScreen.PurchaseHistory, TinoScreen.SupplierOrder,
        TinoScreen.Suppliers, TinoScreen.Orders, TinoScreen.OrderDetail, TinoScreen.Picking,
        TinoScreen.Delivery -> TinoNavDestination.Mais
        else -> TinoNavDestination.Hoje
    }
    val voiceFabState = when {
        agentPresence.mode == TinoPresenceMode.LISTENING -> TinoVoiceFabState.Listening
        agentPresence.mode == TinoPresenceMode.THINKING || agentPresence.mode == TinoPresenceMode.RESOLVING -> TinoVoiceFabState.Processing
        agentPresence.mode == TinoPresenceMode.WAITING_FOR_USER -> TinoVoiceFabState.Waiting
        agentPresence.mode == TinoPresenceMode.COMPLETED -> TinoVoiceFabState.Success
        agentPresence.mode == TinoPresenceMode.ERROR -> TinoVoiceFabState.Error
        agenticVoiceState is AgenticVoiceState.Listening ||
            sharedAgentSnapshot.voiceState == com.tino.app.domain.agent.AgentVoiceState.LISTENING -> TinoVoiceFabState.Listening
        agenticVoiceState is AgenticVoiceState.Understanding ||
            sharedAgentSnapshot.voiceState in setOf(
                com.tino.app.domain.agent.AgentVoiceState.UNDERSTANDING,
                com.tino.app.domain.agent.AgentVoiceState.RESOLVING,
                com.tino.app.domain.agent.AgentVoiceState.EXECUTING,
            ) -> TinoVoiceFabState.Processing
        else -> TinoVoiceFabState.Idle
    }
    var contextCatalogOpen by remember { mutableStateOf(false) }
    var thoughtsOpen by remember { mutableStateOf(false) }
    var quickCreateOpen by remember { mutableStateOf(false) }
    var customerCreateRequested by remember { mutableStateOf(false) }
    LaunchedEffect(screen) {
        contextCatalogOpen = false
        thoughtsOpen = false
        quickCreateOpen = false
    }
    LaunchedEffect(agenticVoiceState) {
        if (agenticVoiceState !is AgenticVoiceState.Idle &&
            agenticVoiceState !is AgenticVoiceState.Cancelled
        ) {
            contextCatalogOpen = false
            thoughtsOpen = false
        }
    }
    val thoughts = remember(
        screen,
        products,
        customers,
        recommendations,
        todayReceivedCents,
        todayPixCents,
        intelligenceSnapshot,
        attentionItems,
        attentionInitialized,
        selectedProduct?.id,
        selectedCustomer?.id,
    ) {
        visibleIntelligenceThoughts(
            screen = screen,
            snapshot = intelligenceSnapshot,
            attentionItems = attentionItems,
            attentionInitialized = attentionInitialized,
            entityProductId = selectedProduct?.id?.takeIf {
                screen == TinoScreen.ProductDetail || screen == TinoScreen.AdjustStock
            },
            entityCustomerId = selectedCustomer?.id?.takeIf {
                screen == TinoScreen.CustomerDetail ||
                    screen == TinoScreen.CustomerAccount ||
                    screen == TinoScreen.ReceivePayment
            },
        )
    }
    val voiceFabClick = when (voiceFabState) {
        TinoVoiceFabState.Listening -> onAgenticVoiceStop
        TinoVoiceFabState.Processing,
        TinoVoiceFabState.Waiting -> ({})
        else -> ({
            if (agenticVoiceState.presentsBottomRiseCatalog()) {
                onAgenticVoiceCancel()
            }
            thoughtsOpen = false
            contextCatalogOpen = !contextCatalogOpen
        })
    }
    val showVoiceFab = screen !in setOf(
        TinoScreen.Splash,
        TinoScreen.FirstAccess,
        TinoScreen.RestoreStore,
        TinoScreen.NfceCapture,
        TinoScreen.DocumentCamera,
        TinoScreen.DocumentUpload,
    )
    val screenContext = remember(screen, selectedCustomer, selectedProduct, activeCapabilities) {
        tinoScreenAgentContext(screen, selectedCustomer, selectedProduct, activeCapabilities)
    }
    Scaffold(
        containerColor = TinoPaper,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (screen in setOf(
                    TinoScreen.Home,
                    TinoScreen.Products,
                    TinoScreen.CreditList,
                    TinoScreen.More,
                    TinoScreen.Customers,
                    TinoScreen.Orders,
                    TinoScreen.Suppliers,
                )
            ) {
                TinoBottomNavigation(
                    current = root,
                    visibleDestinations = visibleNavigationDestinations(activeCapabilities),
                    stockAttentionCount = products.count { it.stockTracked && it.stockQuantity in 0..6 },
                    creditAttentionCount = customers.count { it.balanceCents > 0 },
                    onQuickCreate = { quickCreateOpen = true },
                ) { destination ->
                    onNavigate(
                        when (destination) {
                            TinoNavDestination.Hoje -> TinoScreen.Home
                            TinoNavDestination.Produtos -> TinoScreen.Products
                            TinoNavDestination.Fiado -> TinoScreen.CreditList
                            TinoNavDestination.Mais -> TinoScreen.More
                        },
                    )
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(
            LocalTinoScrollTelemetry provides scrollTelemetry,
            LocalTinoInteractionBoundsRegistry provides interactionBoundsRegistry,
        ) {
            val density = LocalDensity.current
            val reduceMotion = LocalTinoReduceMotion.current
            val mascotPlacement = interactionBoundsRegistry.chooseMascotPlacement(
                mascotSizePx = with(density) { TinoSize.mascotFab.toPx() },
                marginPx = with(density) { TinoSpacing.sm.toPx() },
                collisionPaddingPx = with(density) { TinoSpacing.sm.toPx() },
                belowHeaderPx = with(density) { (TinoSize.topBarHeight + TinoSpacing.lg).toPx() },
            )
            val mascotX by animateDpAsState(
                targetValue = with(density) { mascotPlacement.xPx.toDp() },
                animationSpec = tween(if (reduceMotion) 1 else 500, easing = FastOutSlowInEasing),
                label = "tino-mascot-safe-x",
            )
            val mascotY by animateDpAsState(
                targetValue = with(density) { mascotPlacement.yPx.toDp() },
                animationSpec = tween(if (reduceMotion) 1 else 500, easing = FastOutSlowInEasing),
                label = "tino-mascot-safe-y",
            )
            val mascotAlpha by animateFloatAsState(
                targetValue = if (mascotPlacement.visible) 1f else 0f,
                animationSpec = tween(if (reduceMotion) 1 else 180),
                label = "tino-mascot-safe-alpha",
            )
            TinoMotionHost {
            Box(Modifier.fillMaxSize().padding(padding).tinoInteractionRoot()) {
            val mascotMotion = rememberInfiniteTransition(label = "tino-mascot-safe-roam")
            val mascotHorizontalDrift by mascotMotion.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(if (reduceMotion) 1 else 3_600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "tino-mascot-safe-roam-x",
            )
            val mascotVerticalDrift by mascotMotion.animateFloat(
                initialValue = 0f,
                targetValue = if (reduceMotion) 0f else 3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(if (reduceMotion) 1 else 2_800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "tino-mascot-safe-roam-y",
            )
            AnimatedContent(
                targetState = screen,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    tinoScreenContentTransform(
                        reduceMotion = reduceMotion,
                        fromLayer = initialState.transitionLayer(),
                        toLayer = targetState.transitionLayer(),
                    )
                },
                label = "tino-screen",
            ) { current ->
            CompositionLocalProvider(LocalTinoAnimatedVisibilityScope provides this) {
            when (current) {
                TinoScreen.Home -> HomeScreen(
                    todayTotal = todayTotalCents,
                    todayReceived = todayReceivedCents,
                    todayCash = todayCashCents,
                    todayPix = todayPixCents,
                    todayCard = todayCardCents,
                    todaySales = todaySales,
                    creditTotal = customers.sumOf { it.balanceCents },
                    creditCustomers = customers.count { it.balanceCents > 0 },
                    customers = customers,
                    products = products,
                    suppliers = suppliers,
                    onNavigate = onNavigate,
                    storeProfile = storeProfile,
                    agenticVoiceState = agenticVoiceState,
                    onAgenticVoiceStart = onAgenticVoiceStart,
                    onAgenticVoiceStop = onAgenticVoiceStop,
                    onAgenticVoiceCancel = onAgenticVoiceCancel,
                    onAgenticSubmitText = onAgenticSubmitText,
                    onAgenticActionConfirm = onAgenticActionConfirm,
                    onAgenticUndo = onAgenticUndo,
                    onAgenticEntityChoiceSelected = onAgenticEntityChoiceSelected,
                    onAgenticTranscriptEdit = onAgenticTranscriptEdit,
                    onAgenticTranscriptChange = onAgenticTranscriptChange,
                    onAgenticTranscriptEditCancel = onAgenticTranscriptEditCancel,
                    onAgenticTranscriptContinue = onAgenticTranscriptContinue,
                    onAgenticTranscriptSubmit = onAgenticTranscriptSubmit,
                    onAgenticCapabilityUseOnce = onAgenticCapabilityUseOnce,
                    onAgenticCapabilityActivate = onAgenticCapabilityActivate,
                    onAgenticCardAction = { action -> onNavigate(tinoCardActionDestination(action)) },
                    onQuickQueryOpen = { onNavigate(TinoScreen.QuickQueries) },
                    businessProfile = businessProfile,
                    recommendations = recommendations,
                    onRecommendationDecision = onRecommendationDecision,
                    agentPresence = agentPresence,
                )
                TinoScreen.QuickQueries -> QuickQueriesScreen(
                    state = agenticVoiceState,
                    allowedCapabilities = activeCapabilities,
                    onBack = { onNavigate(TinoScreen.Home) },
                    onQuery = { query -> onAgenticCapabilitySubmit(query.capability, query.title, null) },
                    onStart = onAgenticVoiceStart,
                    onStop = onAgenticVoiceStop,
                    onCancel = onAgenticVoiceCancel,
                    onActionConfirm = onAgenticActionConfirm,
                    onUndo = onAgenticUndo,
                    onEntityChoiceSelected = onAgenticEntityChoiceSelected,
                    onTranscriptEdit = onAgenticTranscriptEdit,
                    onTranscriptChange = onAgenticTranscriptChange,
                    onTranscriptEditCancel = onAgenticTranscriptEditCancel,
                    onTranscriptContinue = onAgenticTranscriptContinue,
                    onTranscriptSubmit = onAgenticTranscriptSubmit,
                    onCapabilityUseOnce = onAgenticCapabilityUseOnce,
                    onCapabilityActivate = onAgenticCapabilityActivate,
                    onCardAction = { action -> onNavigate(tinoCardActionDestination(action)) },
                )
                TinoScreen.Completed -> CompletedScreen(onNavigate, completion)
                TinoScreen.QuickSale -> QuickSaleScreen(
                    products = products,
                    onNavigate = onNavigate,
                    contextualVoiceState = contextualVoiceState,
                    onVoiceStart = { onContextualVoiceStart(VoiceContext.SALE) },
                    onVoiceStop = onContextualVoiceStop,
                ) { lines ->
                    onSaleLinesChanged(lines)
                    onNavigate(TinoScreen.ReceiveSale)
                }
                TinoScreen.ReceiveSale -> ReceiveSaleScreen(saleLines, onNavigate, onSell) { done ->
                    onCompletionChanged(done)
                    onNavigate(TinoScreen.Completed)
                }
                TinoScreen.SelectCustomer -> SelectCustomerScreen(
                    customers = customers,
                    onNavigate = onNavigate,
                    onSelectCustomer = onCustomerSelected,
                    contextualVoiceState = contextualVoiceState,
                    onVoiceStart = { onContextualVoiceStart(VoiceContext.CREDIT_SALE) },
                    onVoiceStop = onContextualVoiceStop,
                )
                TinoScreen.ConfirmCredit -> ConfirmCreditScreen(selectedCustomer, saleLines, onNavigate, onCreditSale) { done ->
                    onCompletionChanged(done)
                    onNavigate(TinoScreen.Completed)
                }
                TinoScreen.CreditList -> CreditListScreen(
                    customers = customers,
                    onNavigate = onNavigate,
                    onAddCustomer = onAddCustomer,
                    onSelectCustomer = onCustomerSelected,
                    contextualVoiceState = contextualVoiceState,
                    onVoiceStart = { onContextualVoiceStart(VoiceContext.CUSTOMER_CREATE) },
                    onVoiceStop = onContextualVoiceStop,
                    openAddCustomerRequest = customerCreateRequested,
                    onAddCustomerRequestConsumed = { customerCreateRequested = false },
                )
                TinoScreen.Customers -> CustomersScreen(
                    customers = customers,
                    onNavigate = onNavigate,
                    onSelectCustomer = { onCustomerSelected(it); onNavigate(TinoScreen.CustomerDetail) },
                    onAddCustomer = onAddCustomer,
                    contextualVoiceState = contextualVoiceState,
                    onVoiceStart = { onContextualVoiceStart(VoiceContext.CUSTOMER_CREATE) },
                    onVoiceStop = onContextualVoiceStop,
                    openAddCustomerRequest = customerCreateRequested,
                    onAddCustomerRequestConsumed = { customerCreateRequested = false },
                )
                TinoScreen.CustomerDetail -> CustomerDetailScreen(
                    customer = selectedCustomer,
                    onNavigate = onNavigate,
                    onUpdateCustomer = onUpdateCustomer,
                )
                TinoScreen.CustomerAccount -> CustomerAccountScreen(
                    selectedCustomer,
                    customerTimeline,
                    onNavigate,
                    customerLedgerStatement,
                )
                TinoScreen.ReceivePayment -> ReceivePaymentScreen(selectedCustomer, onNavigate, onReceivePayment) { done ->
                    onCompletionChanged(done)
                    onNavigate(TinoScreen.Completed)
                }
                TinoScreen.Products -> ProductsScreen(
                    products = products,
                    catalogSyncState = catalogSyncState,
                    onSyncCatalog = onSyncCatalog,
                    onNavigate = onNavigate,
                    onSelectProduct = onProductSelected,
                )
                TinoScreen.ProductDetail -> ProductDetailScreen(selectedProduct, onNavigate)
                TinoScreen.AdjustStock -> AdjustStockScreen(selectedProduct, onNavigate)
                TinoScreen.NewProduct -> NewProductScreen(
                    onNavigate = onNavigate,
                    onAddProduct = onAddProduct,
                    contextualVoiceState = contextualVoiceState,
                    onVoiceStart = { onContextualVoiceStart(VoiceContext.PRODUCT_CREATE) },
                    onVoiceStop = onContextualVoiceStop,
                )
                TinoScreen.StockEntry -> StockEntryScreen(
                    onNavigate = onNavigate,
                    onReceiveStock = onReceiveStock,
                    product = selectedProduct,
                    contextualVoiceState = contextualVoiceState,
                    onVoiceStart = { onContextualVoiceStart(VoiceContext.STOCK_RECEIPT) },
                    onVoiceStop = onContextualVoiceStop,
                )
                TinoScreen.FiscalFound -> FiscalFoundScreen(onNavigate, onFiscalImageSelected)
                TinoScreen.NfceCapture -> NfceCaptureScreen(
                    onBack = { onNavigate(TinoScreen.FiscalFound) },
                    onDocumentCaptured = onNfceDocumentCaptured,
                    onPreviewConfirmed = onNfcePreviewConfirmed,
                )
                TinoScreen.NfeKeyEntry -> NfeKeyEntryScreen(
                    state = goodsReceiptState,
                    onBack = { onNavigate(TinoScreen.FiscalFound) },
                    onSubmit = onSubmitNfeKey,
                    onRetry = onRetryNfe,
                )
                TinoScreen.NfePreview -> NfePreviewScreen(
                    state = goodsReceiptState,
                    searchResults = goodsReceiptSearchResults,
                    onBack = { onNavigate(TinoScreen.NfeKeyEntry) },
                    onSearchProducts = onSearchNfeProducts,
                    onRetry = onRetryNfe,
                    onConfirm = onConfirmNfe,
                )
                TinoScreen.FiscalReview -> FiscalReviewScreen(
                    onNavigate,
                    fiscalDocumentCaptured,
                    fiscalImportResult,
                    fiscalRectifiedPath,
                )
                TinoScreen.DocumentCamera -> DocumentScannerScreen(
                    onBack = { onNavigate(TinoScreen.FiscalFound) },
                    onProcessed = { result, rectifiedPath ->
                        onFiscalDocumentProcessed(result, rectifiedPath)
                        onNavigate(TinoScreen.FiscalReview)
                    },
                )
                TinoScreen.DocumentUpload -> fiscalUploadUri?.let { uri ->
                    DocumentUploadScreen(
                        uri = uri,
                        onBack = { onNavigate(TinoScreen.FiscalFound) },
                        onProcessed = { result, rectifiedPath ->
                            onFiscalDocumentProcessed(result, rectifiedPath)
                            onNavigate(TinoScreen.FiscalReview)
                        },
                    )
                } ?: FiscalFoundScreen(onNavigate, onFiscalImageSelected)
                TinoScreen.PurchaseSuggestions -> PurchaseSuggestionsScreen(
                    products = products,
                    suppliers = suppliers,
                    onCreateSupplierOrder = onCreateSupplierOrder,
                    onNavigate = onNavigate,
                )
                TinoScreen.SupplierOrder -> SupplierOrderScreen(
                    purchases = supplierPurchases,
                    suppliers = suppliers,
                    onReceiveSupplierOrder = onReceiveSupplierOrder,
                    onNavigate = onNavigate,
                )
                TinoScreen.Suppliers -> SuppliersScreen(
                    suppliers = suppliers,
                    onNavigate = onNavigate,
                    onAddSupplier = onAddSupplier,
                    contextualVoiceState = contextualVoiceState,
                    onVoiceStart = { onContextualVoiceStart(VoiceContext.SUPPLIER_CREATE) },
                    onVoiceStop = onContextualVoiceStop,
                )
                TinoScreen.Orders -> OrdersScreen(orders, onNavigate, onOpenOrder)
                TinoScreen.NewOrder -> NewOrderScreen(products, customers, onNavigate, onCreateOrder)
                TinoScreen.OrderDetail -> OrderDetailScreen(orderDetail, onNavigate, onUpdateOrderStatus)
                TinoScreen.Picking -> PickingScreen(orderDetail, onNavigate, onUpdateOrderStatus)
                TinoScreen.Delivery -> DeliveryScreen(orderDetail, onNavigate, onUpdateOrderStatus)
                TinoScreen.Insights -> InsightsScreen(products, attentionItems, onNavigate)
                TinoScreen.DailySummary -> DailySummaryScreen(todayTotalCents, todaySales, customers.sumOf { it.balanceCents }, onNavigate)
        TinoScreen.AskTino -> AskTinoScreen(onNavigate)
                TinoScreen.A2uiValidation -> A2uiValidationScreen(onNavigate)
                TinoScreen.G311MutationSafety -> G311MutationSafetyScreen(onNavigate)
                TinoScreen.G312Memory -> G312MemoryScreen(onNavigate)
                TinoScreen.G4AgentLoop -> G4AgentLoopScreen(onNavigate)
                TinoScreen.G5BusinessMemory -> G5BusinessMemoryScreen(onNavigate)
                TinoScreen.SyncDetails -> SyncDetailsScreen(pendingSyncCount, onNavigate)
                TinoScreen.More -> MoreScreen(onNavigate)
                TinoScreen.CatalogDiagnostics -> CatalogDiagnosticsScreen(catalogDiagnostics, onNavigate)
                TinoScreen.PurchaseHistory -> PurchaseHistoryScreen(onNavigate, onLoadPurchaseHistory, onLoadPurchaseHistoryDetail, onLoadPurchaseInsights)
                TinoScreen.Settings -> SettingsScreen(
                    profile = businessProfile,
                    onOpenBusinessProfile = { onNavigate(TinoScreen.BusinessProfileSettings) },
                    onNavigate = onNavigate,
                )
                TinoScreen.BusinessProfileSettings -> BusinessProfileSettingsScreen(
                    profile = businessProfile,
                    onSave = onUpdateBusinessProfile,
                    onNavigate = onNavigate,
                )
                TinoScreen.Offline -> OfflineScreen(pendingSyncCount, onNavigate)
                TinoScreen.Notification -> NotificationScreen(products, attentionItems, onNavigate)
                else -> HomeScreen(
                    todayTotal = todayTotalCents,
                    todayReceived = todayReceivedCents,
                    todayCash = 0,
                    todayPix = 0,
                    todayCard = 0,
                    todaySales = todaySales,
                    creditTotal = 0,
                    creditCustomers = 0,
                    suppliers = suppliers,
                    onNavigate = onNavigate,
                )
            }
            }
            }
            val screenOwnsAgentSurface = screen == TinoScreen.Home || screen == TinoScreen.QuickQueries
            if (!screenOwnsAgentSurface && agenticVoiceState.presentsBottomRiseCatalog()) {
                GlobalAgentSurface(
                    state = agenticVoiceState,
                    onStart = onAgenticVoiceStart,
                    onStop = onAgenticVoiceStop,
                    onCancel = onAgenticVoiceCancel,
                    onActionConfirm = onAgenticActionConfirm,
                    onUndo = onAgenticUndo,
                    onEntityChoiceSelected = onAgenticEntityChoiceSelected,
                    onTranscriptEdit = onAgenticTranscriptEdit,
                    onTranscriptChange = onAgenticTranscriptChange,
                    onTranscriptEditCancel = onAgenticTranscriptEditCancel,
                    onTranscriptContinue = onAgenticTranscriptContinue,
                    onTranscriptSubmit = onAgenticTranscriptSubmit,
                    onCapabilityUseOnce = onAgenticCapabilityUseOnce,
                    onCapabilityActivate = onAgenticCapabilityActivate,
                )
            }
            if (agenticVoiceState.isVoiceBackground()) {
                TinoVoiceBackgroundSurface(
                    state = agenticVoiceState,
                    onStop = onAgenticVoiceStop,
                    onCancel = onAgenticVoiceCancel,
                    onTranscriptEdit = onAgenticTranscriptEdit,
                    onTranscriptChange = onAgenticTranscriptChange,
                    onTranscriptEditCancel = onAgenticTranscriptEditCancel,
                    onTranscriptContinue = onAgenticTranscriptContinue,
                    onTranscriptSubmit = onAgenticTranscriptSubmit,
                )
            }
            if (thoughtsOpen && thoughts.isNotEmpty()) {
                TinoThoughtsSurface(
                    thoughts = thoughts,
                    onSelect = { thought ->
                        thoughtsOpen = false
                        onActionAttention(thought.id)
                        val capability = thought.capability
                        if (capability != null) {
                            onAgenticCapabilitySubmit(
                                capability,
                                thought.title,
                                thought.actionSubjectId ?: thought.subjectId,
                            )
                        }
                    },
                    onDismissThought = { thought ->
                        onDismissAttention(thought.id)
                        thoughtsOpen = false
                    },
                    onSnoozeThought = { thought ->
                        onSnoozeAttention(thought.id)
                        thoughtsOpen = false
                    },
                    onDismiss = { thoughtsOpen = false },
                )
            }
            if (contextCatalogOpen) {
                TinoContextualCatalogSurface(
                    context = screenContext,
                    onCapability = { capability, label ->
                        contextCatalogOpen = false
                        onAgenticCapabilitySubmit(
                            capability,
                            label,
                            screenContext.activeProductId ?: screenContext.activeCustomerId,
                        )
                    },
                    onSpeak = {
                        contextCatalogOpen = false
                        onAgenticVoiceStart()
                    },
                    onNavigate = { target ->
                        contextCatalogOpen = false
                        runCatching { TinoScreen.valueOf(target) }.getOrNull()?.let(onNavigate)
                    },
                    onSubmitText = { text ->
                        contextCatalogOpen = false
                        onAgenticSubmitText(text)
                    },
                    attentionCount = thoughts.size,
                    onOpenAttention = {
                        contextCatalogOpen = false
                        thoughtsOpen = true
                    },
                    onDismiss = { contextCatalogOpen = false },
                )
            }
            if (quickCreateOpen) {
                TinoCreateBottomSheet(
                    options = quickCreateOptions(
                        screen = screen,
                        activeCapabilities = activeCapabilities,
                        onNavigate = onNavigate,
                        onRequestCustomerCreate = { customerCreateRequested = true },
                    ),
                    onDismiss = { quickCreateOpen = false },
                )
            }
            if (showVoiceFab) {
                TinoMascotFab(
                    mode = agentPresence.mode,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (mascotX + mascotHorizontalDrift.dp).roundToPx(),
                                (mascotY + mascotVerticalDrift.dp).roundToPx(),
                            )
                        }
                        .alpha(mascotAlpha),
                    // Na Home, o mascote pode sobrepor visualmente o card de começo:
                    // ele é o acesso conversacional primário e precisa continuar acionável.
                    // Nas demais telas, o registro de colisão continua protegendo campos,
                    // listas e ações que não podem ser encobertos.
                    enabled = mascotPlacement.enabled || screen == TinoScreen.Home,
                    onClick = voiceFabClick,
                )
            }
            }
            }
        }
    }
}
