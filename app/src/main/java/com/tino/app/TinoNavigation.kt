package com.tino.app

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.tino.fiscal.core.ProductImportResult
import com.tino.app.R
import com.tino.app.feature.home.TinoViewModel
import com.tino.app.feature.voice.VoiceUiState
import com.tino.app.feature.voice.VoiceViewModel
import com.tino.app.feature.voice.ContextualVoiceState
import com.tino.app.feature.voice.ContextualVoiceViewModel
import com.tino.app.feature.voice.AgenticVoiceState
import com.tino.app.feature.voice.AgenticVoiceViewModel
import com.tino.app.feature.voice.AgenticVoiceMetrics
import com.tino.app.feature.voice.TinoAgentSessionViewModel
import com.tino.app.feature.fiscal.DocumentScannerScreen
import com.tino.app.feature.fiscal.DocumentUploadScreen
import com.tino.app.domain.voice.VoiceContext
import com.tino.app.ui.a2ui.TinoA2UiRenderer
import com.tino.app.domain.commerce.PaymentMethod
import com.tino.app.domain.commerce.CustomerCreditTimeline
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
import com.tino.app.core.ui.AppOrientationController
import com.tino.app.presentation.splash.TinoSplashScreen
import com.tino.app.domain.agent.ScreenAgentContext
import com.tino.app.domain.agent.TinoCapabilityRegistry
import com.tino.app.domain.agent.AgentCapability
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.agent.TinoAgentSessionSnapshot
import com.tino.app.domain.agent.TinoPresenceMode
import com.tino.app.domain.agent.TinoPresenceState
import com.tino.app.domain.profile.BusinessProfile
import com.tino.app.domain.language.EntityReference
import com.tino.app.domain.language.LanguageEntityType
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay

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

internal enum class TinoScreen {
    Splash, FirstAccess, RestoreStore,
    Home, QuickQueries, Voice, Understood, Correction, Completed,
    QuickSale, ReceiveSale, SelectCustomer, ConfirmCredit,
    CreditList, Customers, CustomerDetail, CustomerAccount, ReceivePayment,
    Products, ProductDetail, AdjustStock, NewProduct,
    StockEntry, FiscalFound, FiscalReview, DocumentCamera, DocumentUpload, PurchaseSuggestions, SupplierOrder, Suppliers,
    Orders, OrderDetail, Picking, Delivery,
    Insights, DailySummary, AskTino, SyncDetails, More, Settings, A2uiValidation, G311MutationSafety, G312Memory, G4AgentLoop, G5BusinessMemory,
    BusinessProfileSettings,
    Offline, VoiceError, Ambiguity, Notification,
}

/** Single authorization map used by direct navigation and the Agentic Shell. */
internal fun TinoScreen.requiredCapability(): TinoCapabilityId? = when (this) {
    TinoScreen.QuickSale,
    TinoScreen.ReceiveSale,
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
    customers: List<CustomerBalance>,
    customerTimeline: CustomerCreditTimeline?,
    todayTotalCents: Long,
    todayReceivedCents: Long,
    todayCashCents: Long,
    todayPixCents: Long,
    todayCardCents: Long,
    todaySales: Int,
    pendingSyncCount: Int,
    suppliers: List<SupplierEntity>,
    storeProfile: StoreProfileEntity?,
    voiceState: VoiceUiState,
    contextualVoiceState: ContextualVoiceState,
    agenticVoiceState: AgenticVoiceState,
    sharedAgentSnapshot: TinoAgentSessionSnapshot = TinoAgentSessionSnapshot(),
    agentPresence: TinoPresenceState = TinoPresenceState(),
    onVoiceStart: () -> Unit,
    onVoiceClarificationRetry: () -> Unit,
    onVoiceConfirmByVoice: () -> Unit,
    onVoiceStop: () -> Unit,
    onVoiceSubmitText: (String) -> Unit,
    onVoiceConfirm: () -> Unit,
    onVoiceCancel: () -> Unit,
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
    onAgenticCapabilitySubmit: (AgentCapability, String) -> Unit,
    onAgenticCapabilityUseOnce: () -> Unit = {},
    onAgenticCapabilityActivate: (TinoCapabilityId) -> Unit = {},
    businessProfile: BusinessProfile? = null,
    activeCapabilities: Set<TinoCapabilityId> = TinoCapabilityId.values().toSet(),
    onUpdateBusinessProfile: (BusinessProfile) -> Unit = {},
    onAddProduct: (String, String, String) -> Unit,
    onSell: (ProductSummary, Int, PaymentMethod) -> Unit,
    onAddCustomer: (String, String?) -> Unit,
    onUpdateCustomer: (CustomerBalance, String, String?) -> Unit,
    onCreditSale: (ProductSummary, String, Int) -> Unit,
    onReceivePayment: (CustomerBalance, String) -> Unit,
    onReceiveStock: (String, String, String, String) -> Unit,
    onAddSupplier: (String, String?) -> Unit,
    onFiscalDocumentProcessed: (ProductImportResult, String?) -> Unit,
    onFiscalImageSelected: (Uri) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message!!)
            onClearMessage()
        }
    }
    val root = when (screen) {
        TinoScreen.Products, TinoScreen.ProductDetail, TinoScreen.AdjustStock, TinoScreen.NewProduct,
        TinoScreen.StockEntry, TinoScreen.FiscalFound, TinoScreen.FiscalReview, TinoScreen.DocumentCamera, TinoScreen.DocumentUpload -> TinoNavDestination.Produtos
        TinoScreen.CreditList, TinoScreen.CustomerAccount, TinoScreen.ReceivePayment,
        TinoScreen.SelectCustomer, TinoScreen.ConfirmCredit -> TinoNavDestination.Fiado
        TinoScreen.More, TinoScreen.Customers, TinoScreen.CustomerDetail, TinoScreen.Settings, TinoScreen.BusinessProfileSettings, TinoScreen.PurchaseSuggestions, TinoScreen.SupplierOrder,
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
    val voiceFabClick = when (voiceFabState) {
        TinoVoiceFabState.Listening -> onAgenticVoiceStop
        TinoVoiceFabState.Processing,
        TinoVoiceFabState.Waiting -> ({})
        else -> onAgenticVoiceStart
    }
    val showVoiceFab = screen !in setOf(TinoScreen.Splash, TinoScreen.FirstAccess, TinoScreen.RestoreStore, TinoScreen.Voice)
    Scaffold(
        containerColor = TinoPaper,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (showVoiceFab) {
                TinoVoiceFab(
                    state = voiceFabState,
                    onClick = voiceFabClick,
                )
            }
        },
        bottomBar = {
            if (screen in setOf(TinoScreen.Home, TinoScreen.Products, TinoScreen.CreditList, TinoScreen.More, TinoScreen.Customers)) {
                TinoBottomNavigation(
                    current = root,
                    visibleDestinations = visibleNavigationDestinations(activeCapabilities),
                    stockAttentionCount = products.count { it.stockQuantity in 0..6 },
                    creditAttentionCount = customers.count { it.balanceCents > 0 },
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
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
                    onQuickQueryOpen = { onNavigate(TinoScreen.QuickQueries) },
                    businessProfile = businessProfile,
                )
                TinoScreen.QuickQueries -> QuickQueriesScreen(
                    state = agenticVoiceState,
                    allowedCapabilities = activeCapabilities,
                    onBack = { onNavigate(TinoScreen.Home) },
                    onQuery = { query -> onAgenticCapabilitySubmit(query.capability, query.title) },
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
                TinoScreen.Voice -> VoiceScreen(
                    onNavigate = onNavigate,
                    state = voiceState,
                    onStart = onVoiceStart,
                    onClarificationRetry = onVoiceClarificationRetry,
                    onConfirmByVoice = onVoiceConfirmByVoice,
                    onStop = onVoiceStop,
                    onSubmitText = onVoiceSubmitText,
                    onConfirm = onVoiceConfirm,
                    onCancel = onVoiceCancel,
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
                )
                TinoScreen.Customers -> CustomersScreen(
                    customers = customers,
                    onNavigate = onNavigate,
                    onSelectCustomer = { onCustomerSelected(it); onNavigate(TinoScreen.CustomerDetail) },
                    onAddCustomer = onAddCustomer,
                )
                TinoScreen.CustomerDetail -> CustomerDetailScreen(
                    customer = selectedCustomer,
                    onNavigate = onNavigate,
                    onUpdateCustomer = onUpdateCustomer,
                )
                TinoScreen.CustomerAccount -> CustomerAccountScreen(selectedCustomer, customerTimeline, onNavigate)
                TinoScreen.ReceivePayment -> ReceivePaymentScreen(selectedCustomer, onNavigate, onReceivePayment) { done ->
                    onCompletionChanged(done)
                    onNavigate(TinoScreen.Completed)
                }
                TinoScreen.Products -> ProductsScreen(
                    products = products,
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
                    contextualVoiceState = contextualVoiceState,
                    onVoiceStart = { onContextualVoiceStart(VoiceContext.STOCK_RECEIPT) },
                    onVoiceStop = onContextualVoiceStop,
                )
                TinoScreen.FiscalFound -> FiscalFoundScreen(onNavigate, onFiscalImageSelected)
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
                TinoScreen.PurchaseSuggestions -> PurchaseSuggestionsScreen(onNavigate)
                TinoScreen.SupplierOrder -> SupplierOrderScreen(onNavigate)
                TinoScreen.Suppliers -> SuppliersScreen(
                    suppliers = suppliers,
                    onNavigate = onNavigate,
                    onAddSupplier = onAddSupplier,
                    contextualVoiceState = contextualVoiceState,
                    onVoiceStart = { onContextualVoiceStart(VoiceContext.SUPPLIER_CREATE) },
                    onVoiceStop = onContextualVoiceStop,
                )
                TinoScreen.Orders -> OrdersScreen(onNavigate)
                TinoScreen.OrderDetail -> OrderDetailScreen(onNavigate)
                TinoScreen.Picking -> PickingScreen(onNavigate)
                TinoScreen.Delivery -> DeliveryScreen(onNavigate)
                TinoScreen.Insights -> InsightsScreen(products, onNavigate)
                TinoScreen.DailySummary -> DailySummaryScreen(todayTotalCents, todaySales, customers.sumOf { it.balanceCents }, onNavigate)
        TinoScreen.AskTino -> AskTinoScreen(onNavigate)
                TinoScreen.A2uiValidation -> A2uiValidationScreen(onNavigate)
                TinoScreen.G311MutationSafety -> G311MutationSafetyScreen(onNavigate)
                TinoScreen.G312Memory -> G312MemoryScreen(onNavigate)
                TinoScreen.G4AgentLoop -> G4AgentLoopScreen(onNavigate)
                TinoScreen.G5BusinessMemory -> G5BusinessMemoryScreen(onNavigate)
                TinoScreen.SyncDetails -> SyncDetailsScreen(pendingSyncCount, onNavigate)
                TinoScreen.More -> MoreScreen(onNavigate)
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
                TinoScreen.Notification -> NotificationScreen(products, onNavigate)
                else -> HomeScreen(
                    todayTotal = todayTotalCents,
                    todayReceived = todayReceivedCents,
                    todayCash = 0,
                    todayPix = 0,
                    todayCard = 0,
                    todaySales = todaySales,
                    creditTotal = 0,
                    creditCustomers = 0,
                    onNavigate = onNavigate,
                )
            }
            val screenOwnsAgentSurface = screen == TinoScreen.Home || screen == TinoScreen.QuickQueries
            if (!screenOwnsAgentSurface && agenticVoiceState !is AgenticVoiceState.Idle && agenticVoiceState !is AgenticVoiceState.Cancelled) {
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
        }
    }
}
