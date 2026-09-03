package com.tino.app
import android.Manifest
import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Intent
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.tino.app.feature.receiving.GoodsReceiptViewModel
import com.tino.app.domain.receiving.GoodsReceiptConfirmation
import com.tino.app.domain.receiving.GoodsReceiptPreview
import com.tino.app.domain.receiving.GoodsReceiptRemoteState
import com.tino.app.domain.nfce.PurchaseHistory
import com.tino.app.domain.nfce.PurchaseHistoryDetail
import com.tino.app.domain.nfce.PurchaseInsight
import com.tino.app.domain.catalog.CatalogSyncState
import com.tino.app.domain.catalog.CatalogSyncStatus
import com.tino.app.domain.catalog.CatalogSyncDiagnostics
import com.tino.app.domain.onboarding.OnboardingState
import com.tino.app.domain.onboarding.BusinessDataSourceType
import com.tino.app.domain.onboarding.OnboardingDataSourceChoice
import com.tino.app.domain.onboarding.OtpChallenge
import com.tino.app.domain.onboarding.OtpCodeAttempt
import com.tino.app.feature.voice.ContextualVoiceState
import com.tino.app.feature.voice.ContextualVoiceViewModel
import com.tino.app.feature.voice.AgenticVoiceState
import com.tino.app.feature.voice.AgenticVoiceViewModel
import com.tino.app.feature.voice.AgenticVoiceMetrics
import com.tino.app.feature.voice.TinoAgentSessionViewModel
import com.tino.app.feature.voice.TinoA2uiActionViewModel
import com.tino.app.feature.voice.TinoA2uiActionState
import com.tino.app.feature.voice.G311MutationSafetyViewModel
import com.tino.app.feature.voice.G312MemoryViewModel
import com.tino.app.feature.voice.G4AgentLoopViewModel
import com.tino.app.feature.voice.G5BusinessMemoryViewModel
import com.tino.app.feature.fiscal.DocumentScannerScreen
import com.tino.app.feature.fiscal.DocumentUploadScreen
import com.tino.app.domain.voice.VoiceContext
import com.tino.app.ui.a2ui.TinoA2UiRenderer
import com.tino.app.ui.a2ui.TinoA2UiSurfaceHost
import com.tino.app.ui.a2ui.TinoAgentCatalogSurface
import com.tino.app.ui.a2ui.TinoVoiceBackgroundSurface
import com.tino.app.ui.a2ui.isVoiceBackground
import com.tino.app.ui.a2ui.presentsBottomRiseCatalog
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceMessage
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceOperation
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceComponent
import com.tino.app.interfaceadapter.a2ui.CoreTinoComponentCatalog
import com.tino.app.domain.commerce.PaymentMethod
import com.tino.app.domain.commerce.CustomerCreditTimeline
import com.tino.app.domain.commerce.SharedLedgerEventType
import com.tino.app.domain.commerce.SharedLedgerStatement
import com.tino.app.domain.commerce.SharedLedgerStatementFormatter
import com.tino.app.ui.components.TinoBottomNavigation
import com.tino.app.ui.components.TinoCard
import com.tino.app.ui.components.TinoCardSurface
import com.tino.app.ui.components.TinoCardStatus
import com.tino.app.ui.components.TinoCardRenderer
import com.tino.app.ui.components.TinoCardSpec
import com.tino.app.ui.components.LocalTinoScrollTelemetry
import com.tino.app.ui.components.tinoClickable
import com.tino.app.ui.components.tinoAnimateContentSize
import com.tino.app.ui.components.tinoEnter
import com.tino.app.ui.components.tinoExit
import com.tino.app.ui.components.tinoScreenContentTransform
import com.tino.app.ui.theme.LocalTinoReduceMotion
import com.tino.app.ui.components.tinoSharedBounds
import com.tino.app.ui.components.TinoSharedKeys
import com.tino.app.ui.components.tinoInteractiveBounds
import com.tino.app.ui.components.tinoOccupiedBounds
import com.tino.app.ui.components.TinoCustomerRow
import com.tino.app.ui.components.TinoEmptyState
import com.tino.app.ui.components.TinoLoadingState
import com.tino.app.ui.components.TinoIconButton
import com.tino.app.ui.components.TinoInsightCard
import com.tino.app.ui.components.TinoQuickActionCard
import com.tino.app.ui.components.TinoSectionHeader
import com.tino.app.ui.components.TinoHorizontalCarousel
import com.tino.app.ui.components.TinoActionTile
import com.tino.app.ui.components.TinoMetricCard
import com.tino.app.ui.components.TinoListRow
import com.tino.app.ui.components.TinoMenuRow
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
import com.tino.app.ui.components.TinoSystemAction
import com.tino.app.ui.components.TinoSystemActionListCard
import com.tino.app.ui.components.TinoSupplierRow
import com.tino.app.ui.illustration.TinoIllustrationState
import com.tino.app.ui.components.TinoSyncIndicator
import com.tino.app.ui.components.TinoTextField
import com.tino.app.ui.components.TinoTopBar
import com.tino.app.ui.components.TinoTextAction
import com.tino.app.ui.components.TinoContextHeader
import com.tino.app.ui.components.TinoHeaderStyle
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
import com.tino.app.domain.agent.TinoAgentSession
import com.tino.app.domain.agent.TinoAgentSessionSnapshot
import com.tino.app.domain.agent.AgentCapability
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.profile.BusinessModule
import com.tino.app.domain.profile.DefaultBusinessContextResolver
import com.tino.app.domain.profile.BusinessVertical
import com.tino.app.domain.profile.BusinessProfile
import com.tino.app.domain.profile.VerticalPresetCatalog
import com.tino.app.domain.profile.OperationalPatternCatalog
import com.tino.app.domain.agent.TinoCapabilityRegistry
import com.tino.app.domain.agent.FastNavigationTarget
import com.tino.app.domain.agent.requiredCapability
import com.tino.app.domain.intelligence.Recommendation
import com.tino.app.domain.intelligence.AttentionRecord
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun TinoApp(openNotification: Boolean = false) {
    val application = LocalContext.current.applicationContext as? TinoApplication
    val reduceMotion = LocalTinoReduceMotion.current
    var contentReady by remember { mutableStateOf(false) }
    var splashFinished by remember { mutableStateOf(false) }
    var splashOnScreen by remember { mutableStateOf(true) }
    val splashAlpha = remember { Animatable(1f) }

    LaunchedEffect(splashFinished, contentReady, reduceMotion) {
        if (!splashFinished) return@LaunchedEffect
        if (reduceMotion) {
            splashAlpha.snapTo(0f)
            splashOnScreen = false
            return@LaunchedEffect
        }
        splashAlpha.animateTo(0f, tween(durationMillis = 400, easing = FastOutSlowInEasing))
        delay(32)
        splashOnScreen = false
    }

    Box(Modifier.fillMaxSize().background(TinoPaper)) {
        TinoAppContent(
            onReady = { contentReady = true },
            openNotification = openNotification,
        )
        if (splashOnScreen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .graphicsLayer { alpha = splashAlpha.value },
            ) {
                TinoSplashScreen(
                    onFirstFrame = { application?.startDeferredRuntime() },
                    onFinished = { splashFinished = true },
                )
            }
        }
    }
}

@Composable
private fun TinoAppContent(
    onReady: () -> Unit,
    openNotification: Boolean,
    viewModel: TinoViewModel = hiltViewModel(),
    contextualVoiceViewModel: ContextualVoiceViewModel = hiltViewModel(),
    agenticVoiceViewModel: AgenticVoiceViewModel = hiltViewModel(),
    agentSessionViewModel: TinoAgentSessionViewModel = hiltViewModel(),
    goodsReceiptViewModel: GoodsReceiptViewModel = hiltViewModel(),
    nfcePreviewViewModel: com.tino.app.feature.nfce.NfcePreviewViewModel = hiltViewModel(),
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val customerTimeline by viewModel.customerTimeline.collectAsStateWithLifecycle()
    val customerLedgerStatement by viewModel.customerLedgerStatement.collectAsStateWithLifecycle()
    val todayTotal by viewModel.todayTotalCents.collectAsStateWithLifecycle()
    val todayReceived by viewModel.todayReceivedCents.collectAsStateWithLifecycle()
    val todayCash by viewModel.todayCashCents.collectAsStateWithLifecycle()
    val todayPix by viewModel.todayPixCents.collectAsStateWithLifecycle()
    val todayCard by viewModel.todayCardCents.collectAsStateWithLifecycle()
    val todaySales by viewModel.todaySalesCount.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val supplierPurchases by viewModel.supplierPurchases.collectAsStateWithLifecycle()
    val orderDetail by viewModel.orderDetail.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val catalogSyncState by viewModel.catalogSyncState.collectAsStateWithLifecycle()
    val catalogDiagnostics by viewModel.catalogDiagnostics.collectAsStateWithLifecycle()
    val storeProfile by viewModel.storeProfile.collectAsStateWithLifecycle()
    val profileLoaded by viewModel.profileLoaded.collectAsStateWithLifecycle()
    val authenticated by viewModel.authenticated.collectAsStateWithLifecycle()
    val remoteBusinessId by viewModel.remoteBusinessId.collectAsStateWithLifecycle()
    val onboardingState by viewModel.onboardingState.collectAsStateWithLifecycle()
    val businessProfile by viewModel.businessProfile.collectAsStateWithLifecycle()
    val goodsReceiptState by goodsReceiptViewModel.state.collectAsStateWithLifecycle()
    val goodsReceiptSearchResults by goodsReceiptViewModel.searchResults.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val intelligenceSnapshot by viewModel.intelligenceSnapshot.collectAsStateWithLifecycle()
    val attentionItems by viewModel.attentionItems.collectAsStateWithLifecycle()
    val attentionInitialized by viewModel.attentionInitialized.collectAsStateWithLifecycle()
    val businessContext = businessProfile?.let { DefaultBusinessContextResolver().resolve(it) }
    val activeCapabilities = businessContext?.capabilities.orEmpty()
    var screen by remember { mutableStateOf(TinoScreen.Splash) }
    var saleLines by remember { mutableStateOf<List<SaleLine>>(emptyList()) }
    var selectedCustomer by remember { mutableStateOf<CustomerBalance?>(null) }
    var selectedProduct by remember { mutableStateOf<ProductSummary?>(null) }
    var fiscalDocumentCaptured by remember { mutableStateOf(false) }
    var fiscalImportResult by remember { mutableStateOf<ProductImportResult?>(null) }
    var fiscalRectifiedPath by remember { mutableStateOf<String?>(null) }
    var fiscalUploadUri by remember { mutableStateOf<Uri?>(null) }
    var completion by remember { mutableStateOf(TinoCompletion()) }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val contextualVoiceState by contextualVoiceViewModel.state.collectAsStateWithLifecycle()
    val agenticVoiceState by agenticVoiceViewModel.state.collectAsStateWithLifecycle()
    val sharedAgentSnapshot by agentSessionViewModel.sharedState.snapshot.collectAsStateWithLifecycle()
    val agentPresence by agentSessionViewModel.presence.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingVoiceAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingOtpPrompt by remember { mutableStateOf<PendingOtpPrompt?>(null) }

    val requestOtpCode: suspend (OtpChallenge) -> OtpCodeAttempt = { challenge ->
        suspendCancellableCoroutine { continuation ->
            pendingOtpPrompt = PendingOtpPrompt(challenge, continuation)
            continuation.invokeOnCancellation {
                if (pendingOtpPrompt?.continuation === continuation) {
                    pendingOtpPrompt = null
                }
            }
        }
    }

    LaunchedEffect(goodsReceiptState) {
        if (screen == TinoScreen.NfeKeyEntry && (
                goodsReceiptState is GoodsReceiptRemoteState.PreviewReady ||
                    goodsReceiptState is GoodsReceiptRemoteState.ReviewRequired ||
                    goodsReceiptState is GoodsReceiptRemoteState.Confirmed
                )
        ) {
            screen = TinoScreen.NfePreview
        }
    }

    LaunchedEffect(screen, context) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val barColor = TinoPaper.toArgb()
        activity.window.statusBarColor = barColor
        activity.window.navigationBarColor = barColor
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingVoiceAction
        pendingVoiceAction = null
        if (granted) action?.invoke()
    }

    fun requestVoiceAccess(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingVoiceAction = action
            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(profileLoaded, storeProfile, remoteBusinessId, authenticated, openNotification) {
        if (!profileLoaded) return@LaunchedEffect
        screen = when {
            !authenticated -> TinoScreen.FirstAccess
            storeProfile == null || remoteBusinessId == null -> TinoScreen.FirstAccess
            openNotification -> TinoScreen.Notification
            else -> TinoScreen.Home
        }
    }
    LaunchedEffect(screen) {
        if (screen == TinoScreen.Splash) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        onReady()
    }
    LaunchedEffect(agenticVoiceState, activeCapabilities) {
        val navigation = agenticVoiceState as? AgenticVoiceState.Navigation ?: return@LaunchedEffect
        val targetScreen = when (navigation.target) {
            FastNavigationTarget.QUICK_SALE -> TinoScreen.QuickSale
            FastNavigationTarget.CUSTOMERS -> TinoScreen.Customers
            FastNavigationTarget.PRODUCTS -> TinoScreen.Products
            FastNavigationTarget.CREDIT_LIST -> TinoScreen.CreditList
            FastNavigationTarget.STOCK_ENTRY -> TinoScreen.StockEntry
        }
        val allowed = navigation.target.requiredCapability() in activeCapabilities
        if (allowed) screen = targetScreen
        agenticVoiceViewModel.consumeNavigation()
    }
    LaunchedEffect(selectedCustomer?.id) {
        viewModel.loadCustomerTimeline(selectedCustomer?.id)
    }
    LaunchedEffect(screen, selectedCustomer?.id, selectedProduct?.id) {
        if (screen == TinoScreen.Splash ||
            screen == TinoScreen.FirstAccess ||
            screen == TinoScreen.RestoreStore
        ) {
            return@LaunchedEffect
        }
        viewModel.refreshIntelligenceSnapshot(
            screen = screen.name,
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
    LaunchedEffect(screen, selectedCustomer?.id, selectedProduct?.id, activeCapabilities) {
        agentSessionViewModel.enterScreen(
            tinoScreenAgentContext(
                screen = screen,
                selectedCustomer = selectedCustomer,
                selectedProduct = selectedProduct,
                activeCapabilities = activeCapabilities,
            ),
        )
    }

    fun navigate(target: TinoScreen) {
        val context = businessContext
        val requiredCapability = target.requiredCapability()
        val allowed = context == null || requiredCapability == null || context.hasCapability(requiredCapability)
        if (allowed) screen = target
    }
    LaunchedEffect(customers, selectedCustomer?.id) {
        selectedCustomer?.id
            ?.let { id -> customers.firstOrNull { it.id == id } }
            ?.takeIf { it != selectedCustomer }
            ?.let { selectedCustomer = it }
    }

    val reduceMotion = LocalTinoReduceMotion.current
    val rootPhase = when (screen) {
        TinoScreen.Splash -> "boot"
        TinoScreen.FirstAccess -> "first"
        TinoScreen.RestoreStore -> "restore"
        else -> "app"
    }
    Surface(Modifier.fillMaxSize(), color = TinoPaper) {
        AnimatedContent(
            targetState = rootPhase,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (initialState == "boot" || targetState == "boot") {
                    ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                } else {
                    tinoScreenContentTransform(
                        reduceMotion = reduceMotion,
                        fromLayer = if (initialState == "app") 1 else 0,
                        toLayer = if (targetState == "app") 1 else 0,
                    )
                }
            },
            label = "tino-root-phase",
        ) { phase ->
        when (phase) {
            "boot" -> BootLoadingScreen()
            "first" -> FirstAccessScreen(
                onContinue = { storeName, ownerName, phone, vertical, modules, dataSource ->
                    (context as? Activity)?.let { activity ->
                        viewModel.completeOnboarding(
                            activity,
                            storeName,
                            ownerName,
                            phone,
                            vertical,
                            modules,
                            dataSource,
                            requestOtpCode,
                        )
                    }
                },
                onRestore = { screen = TinoScreen.RestoreStore },
                contextualVoiceState = contextualVoiceState,
                onVoiceStart = {
                    requestVoiceAccess { contextualVoiceViewModel.listen(VoiceContext.ONBOARDING) }
                },
                onVoiceStop = contextualVoiceViewModel::stop,
                onboardingState = onboardingState,
            )
            "restore" -> RestoreStoreScreen(
                onBack = { screen = TinoScreen.FirstAccess },
                onContinue = { phone ->
                    (context as? Activity)?.let { activity ->
                        viewModel.reauthenticateExistingBusiness(activity, phone, requestOtpCode)
                    }
                },
                onboardingState = onboardingState,
            )
            else -> MainShell(
                screen = screen,
                onNavigate = ::navigate,
                saleLines = saleLines,
                onSaleLinesChanged = { saleLines = it },
                selectedCustomer = selectedCustomer,
                onCustomerSelected = { selectedCustomer = it },
                selectedProduct = selectedProduct,
                onProductSelected = { selectedProduct = it },
                completion = completion,
                onCompletionChanged = { completion = it },
                fiscalDocumentCaptured = fiscalDocumentCaptured,
                fiscalImportResult = fiscalImportResult,
                fiscalRectifiedPath = fiscalRectifiedPath,
                fiscalUploadUri = fiscalUploadUri,
                message = message,
                onClearMessage = viewModel::clearMessage,
                products = products,
                catalogSyncState = catalogSyncState,
                catalogDiagnostics = catalogDiagnostics,
                onSyncCatalog = viewModel::syncCatalog,
                customers = customers,
                customerTimeline = customerTimeline,
                customerLedgerStatement = customerLedgerStatement,
                todayTotalCents = todayTotal,
                todayReceivedCents = todayReceived,
                todayCashCents = todayCash,
                todayPixCents = todayPix,
                todayCardCents = todayCard,
                todaySales = todaySales,
                pendingSyncCount = pendingSyncCount,
                suppliers = suppliers,
                supplierPurchases = supplierPurchases,
                orders = orders,
                orderDetail = orderDetail,
                storeProfile = storeProfile,
                contextualVoiceState = contextualVoiceState,
                agenticVoiceState = agenticVoiceState,
                sharedAgentSnapshot = sharedAgentSnapshot,
                agentPresence = agentPresence,
                onContextualVoiceStart = { voiceContext ->
                    requestVoiceAccess { contextualVoiceViewModel.listen(voiceContext) }
                },
                onContextualVoiceStop = contextualVoiceViewModel::stop,
                onAgenticVoiceStart = { requestVoiceAccess(agenticVoiceViewModel::start) },
                onAgenticVoiceStop = agenticVoiceViewModel::stop,
                onAgenticVoiceCancel = agenticVoiceViewModel::cancel,
                onAgenticSubmitText = agenticVoiceViewModel::submitText,
                onAgenticActionConfirm = agenticVoiceViewModel::confirmAction,
                onAgenticUndo = agenticVoiceViewModel::undo,
                onAgenticEntityChoiceSelected = agenticVoiceViewModel::selectEntityChoice,
                onAgenticTranscriptEdit = agenticVoiceViewModel::beginTranscriptEdit,
                onAgenticTranscriptChange = agenticVoiceViewModel::updateTranscript,
                onAgenticTranscriptEditCancel = agenticVoiceViewModel::cancelTranscriptEdit,
                onAgenticTranscriptContinue = agenticVoiceViewModel::continueSpeaking,
                onAgenticTranscriptSubmit = agenticVoiceViewModel::submitTranscriptReview,
                onAgenticCapabilitySubmit = { capability, label, subjectId ->
                    agenticVoiceViewModel.submitCapability(capability, label, subjectId)
                },
                onAgenticCapabilityUseOnce = agenticVoiceViewModel::useCapabilityOnce,
                onAgenticCapabilityActivate = { capability ->
                    businessProfile?.let { profile ->
                        viewModel.updateBusinessProfile(
                            profile.copy(permanentCapabilities = profile.permanentCapabilities + capability),
                        )
                    }
                },
                businessProfile = businessProfile,
                recommendations = recommendations,
                intelligenceSnapshot = intelligenceSnapshot,
                attentionItems = attentionItems,
                attentionInitialized = attentionInitialized,
                onDismissAttention = viewModel::dismissAttention,
                onSnoozeAttention = viewModel::snoozeAttention,
                onActionAttention = viewModel::actionAttention,
                onRecommendationDecision = viewModel::decideRecommendation,
                activeCapabilities = activeCapabilities,
                onUpdateBusinessProfile = viewModel::updateBusinessProfileAndWait,
                onAddProduct = viewModel::addProductAndWait,
                onSell = { product, quantity, paymentMethod -> viewModel.sellAndWait(product, quantity, paymentMethod) },
                onAddCustomer = viewModel::addCustomerAndWait,
                onUpdateCustomer = viewModel::updateCustomerAndWait,
                onCreditSale = viewModel::sellOnCreditAndWait,
                onReceivePayment = viewModel::receivePaymentAndWait,
                onReceiveStock = viewModel::receiveStockAndWait,
                onAddSupplier = viewModel::addSupplierAndWait,
                onCreateSupplierOrder = viewModel::createSupplierOrderAndWait,
                onReceiveSupplierOrder = viewModel::receiveSupplierOrderAndWait,
                onCreateOrder = viewModel::createOrderAndWait,
                onOpenOrder = { orderId ->
                    viewModel.openOrder(orderId)
                    navigate(TinoScreen.OrderDetail)
                },
                onUpdateOrderStatus = viewModel::updateOrderStatusAndWait,
                onFiscalDocumentProcessed = { result, rectifiedPath ->
                    fiscalDocumentCaptured = true
                    fiscalImportResult = result
                    fiscalRectifiedPath = rectifiedPath
                },
                onFiscalImageSelected = { uri ->
                    fiscalUploadUri = uri
                    screen = TinoScreen.DocumentUpload
                },
                onNfceDocumentCaptured = nfcePreviewViewModel::createPreview,
                onNfcePreviewConfirmed = nfcePreviewViewModel::confirmPreview,
                onLoadPurchaseHistory = nfcePreviewViewModel::getPurchaseHistory,
                onLoadPurchaseHistoryDetail = nfcePreviewViewModel::getPurchaseHistoryDetail,
                onLoadPurchaseInsights = nfcePreviewViewModel::getPurchaseInsights,
                goodsReceiptState = goodsReceiptState,
                goodsReceiptSearchResults = goodsReceiptSearchResults,
                onSubmitNfeKey = goodsReceiptViewModel::submitAccessKey,
                onRetryNfe = goodsReceiptViewModel::retry,
                onSearchNfeProducts = goodsReceiptViewModel::searchProducts,
                onConfirmNfe = goodsReceiptViewModel::confirm,
            )
        }
        }
    }

    pendingOtpPrompt?.let { prompt ->
        OtpCodeDialog(
            challenge = prompt.challenge,
            onSubmit = { code ->
                prompt.continuation.resume(OtpCodeAttempt.Submit(code))
                pendingOtpPrompt = null
            },
            onCancel = {
                prompt.continuation.cancel()
                pendingOtpPrompt = null
            },
            onResend = {
                prompt.continuation.resume(OtpCodeAttempt.Resend)
                pendingOtpPrompt = null
            },
        )
    }
}

private fun maskOtpPhone(phone: String): String {
    val digits = phone.filter(Char::isDigit)
    if (digits.length <= 4) return phone
    return "•••• ${digits.takeLast(4)}"
}

private data class PendingOtpPrompt(
    val challenge: OtpChallenge,
    val continuation: CancellableContinuation<OtpCodeAttempt>,
)

@Composable
private fun OtpCodeDialog(
    challenge: OtpChallenge,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    onResend: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var manualEntryVisible by remember(challenge.challengeId) { mutableStateOf(false) }
    var resendInSeconds by remember(challenge.challengeId) {
        mutableStateOf(challenge.resendAvailableInSeconds)
    }
    var expiresInSeconds by remember(challenge.challengeId) {
        mutableStateOf(challenge.expiresInSeconds)
    }
    LaunchedEffect(challenge.challengeId) {
        while (expiresInSeconds > 0) {
            delay(1_000)
            expiresInSeconds -= 1
            if (resendInSeconds > 0) resendInSeconds -= 1
        }
    }
    val expired = expiresInSeconds <= 0L
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Confira seu WhatsApp") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                Text(
                    if (expired) {
                        "Este código expirou. Solicite um novo código para continuar."
                    } else {
                        "Aguardando confirmação no WhatsApp… " +
                            "Se preferir, digite o código manualmente; ele expira em " +
                            "${expiresInSeconds / 60} min ${expiresInSeconds % 60}s."
                    },
                    color = TinoMuted,
                )
                if (!manualEntryVisible && !expired) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = TinoGreen,
                        )
                        Text("Aguardando confirmação…", color = TinoMuted)
                    }
                    TextButton(onClick = { manualEntryVisible = true }) {
                        Text("DIGITAR CÓDIGO MANUALMENTE")
                    }
                }
                if (manualEntryVisible) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { value ->
                            code = value.filter(Char::isDigit).take(6)
                        },
                        label = { Text("Código de 6 dígitos") },
                        singleLine = true,
                        enabled = !expired,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(code) },
                enabled = code.length == 6 && !expired,
            ) {
                Text("CONFIRMAR")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onResend, enabled = resendInSeconds <= 0L) {
                    Text(if (resendInSeconds > 0L) "REENVIAR MENSAGEM (${resendInSeconds}s)" else "REENVIAR MENSAGEM")
                }
                TextButton(onClick = onCancel) { Text("CANCELAR") }
            }
        },
    )
}

@Composable
internal fun GlobalAgentSurface(
    state: AgenticVoiceState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onActionConfirm: (AgenticVoiceState.ActionPreview) -> Unit,
    onUndo: (String) -> Unit,
    onEntityChoiceSelected: (AgenticVoiceState.EntityChoice, String) -> Unit,
    onTranscriptEdit: () -> Unit,
    onTranscriptChange: (String) -> Unit,
    onTranscriptEditCancel: () -> Unit,
    onTranscriptContinue: () -> Unit,
    onTranscriptSubmit: () -> Unit,
    onCapabilityUseOnce: () -> Unit = {},
    onCapabilityActivate: (TinoCapabilityId) -> Unit = {},
) {
    if (state.presentsBottomRiseCatalog()) {
        TinoAgentCatalogSurface(
            state = state,
            onDismiss = onCancel,
            onStart = onStart,
            onActionConfirm = onActionConfirm,
            onUndo = onUndo,
            onEntityChoiceSelected = onEntityChoiceSelected,
            onCapabilityUseOnce = onCapabilityUseOnce,
            onCapabilityActivate = onCapabilityActivate,
            onCardAction = {},
        )
        return
    }
    if (state.isVoiceBackground()) {
        TinoVoiceBackgroundSurface(
            state = state,
            onStop = onStop,
            onCancel = onCancel,
            onTranscriptEdit = onTranscriptEdit,
            onTranscriptChange = onTranscriptChange,
            onTranscriptEditCancel = onTranscriptEditCancel,
            onTranscriptContinue = onTranscriptContinue,
            onTranscriptSubmit = onTranscriptSubmit,
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TinoSpacing.md, vertical = TinoSpacing.sm),
        contentAlignment = Alignment.BottomCenter,
    ) {
        TinoCardSurface(
            modifier = Modifier,
            description = "Resposta do TINO",
        ) {
            Column(
                Modifier.padding(TinoSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("TINO", color = TinoGreenDark, style = MaterialTheme.typography.labelLarge)
                    TinoIconButton(TinoIcons.Close, "Fechar resposta do TINO", onCancel)
                }
                HomeVoiceSurface(
                    state = state,
                    onStart = onStart,
                    onStop = onStop,
                    onCancel = onCancel,
                    onActionConfirm = onActionConfirm,
                onUndo = onUndo,
                onEntityChoiceSelected = onEntityChoiceSelected,
                onTranscriptEdit = onTranscriptEdit,
                onTranscriptChange = onTranscriptChange,
                onTranscriptEditCancel = onTranscriptEditCancel,
                onTranscriptContinue = onTranscriptContinue,
                onTranscriptSubmit = onTranscriptSubmit,
                onCapabilityUseOnce = onCapabilityUseOnce,
                onCapabilityActivate = onCapabilityActivate,
            )
            }
        }
    }
}

internal fun ContextualVoiceState.forContext(context: VoiceContext): ContextualVoiceState = when (this) {
    ContextualVoiceState.Idle -> ContextualVoiceState.Idle
    is ContextualVoiceState.Listening -> if (this.context == context) this else ContextualVoiceState.Idle
    is ContextualVoiceState.Understanding -> if (this.context == context) this else ContextualVoiceState.Idle
    is ContextualVoiceState.Extracted -> if (value.context == context) this else ContextualVoiceState.Idle
    is ContextualVoiceState.NeedsCorrection -> if (value.context == context) this else ContextualVoiceState.Idle
    is ContextualVoiceState.Unavailable -> if (this.context == context) this else ContextualVoiceState.Idle
    is ContextualVoiceState.Error -> if (this.context == context) this else ContextualVoiceState.Idle
}

internal fun ContextualVoiceState.fieldsFor(context: VoiceContext): Map<String, String> =
    when (val current = forContext(context)) {
        is ContextualVoiceState.Extracted -> current.value.fields
        is ContextualVoiceState.NeedsCorrection -> current.value.fields
        else -> emptyMap()
    }

@Composable
internal fun ContextualVoicePanel(
    context: VoiceContext,
    state: ContextualVoiceState,
    hint: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val current = state.forContext(context)
    when (current) {
        ContextualVoiceState.Idle -> TinoVoiceCard(
            title = "Preencher falando",
            message = hint,
            onClick = onStart,
        )
        is ContextualVoiceState.Listening -> TinoVoiceCard(
            title = "Estou ouvindo…",
            message = current.transcript.ifBlank { "Fale naturalmente. Toque aqui para parar." },
            showForward = false,
            emphasized = true,
            onClick = onStop,
        )
        is ContextualVoiceState.Understanding -> TinoCardSurface(description = "Processando fala") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(TinoSize.iconNormal),
                    color = TinoGreen,
                    strokeWidth = TinoSize.progressStrokeWidth,
                )
                Column(Modifier.weight(1f)) {
                    Text("Organizando sua fala…", fontWeight = FontWeight.SemiBold)
                    Text(current.transcript, color = TinoMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        is ContextualVoiceState.Extracted -> TinoCardSurface(description = "Dados preenchidos") {
            Text("Pronto — preenchi os dados da sua fala.", color = TinoGreenDark, fontWeight = FontWeight.SemiBold)
        }
        is ContextualVoiceState.NeedsCorrection -> TinoVoiceCard(
            title = "Falta só confirmar",
            message = current.message,
            showForward = true,
            emphasized = true,
            onClick = onStart,
        )
        is ContextualVoiceState.Unavailable -> TinoVoiceCard(
            title = "Voz indisponível",
            message = current.message,
            onClick = onStart,
        )
        is ContextualVoiceState.Error -> TinoVoiceCard(
            title = "Não consegui ouvir",
            message = current.message,
            onClick = onStart,
        )
    }
}

@Composable
internal fun FirstAccessScreen(
    onContinue: (String, String, String, BusinessVertical, Set<BusinessModule>, OnboardingDataSourceChoice) -> Unit,
    onRestore: () -> Unit,
    contextualVoiceState: ContextualVoiceState = ContextualVoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
    onboardingState: OnboardingState = OnboardingState.Idle,
) {
    var store by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var submitAttempted by remember { mutableStateOf(false) }
    var configurationError by remember { mutableStateOf<String?>(null) }
    var vertical by remember { mutableStateOf(BusinessVertical.RETAIL) }
    var modules by remember { mutableStateOf(VerticalPresetCatalog.forVertical(vertical).defaultModules) }
    var customizeModules by remember { mutableStateOf(false) }
    var dataSourceChoice by remember { mutableStateOf(OnboardingDataSourceChoice.Native) }
    val missingFields = buildList {
        if (store.isBlank()) add("nome do comércio")
        if (owner.isBlank()) add("seu nome")
        if (phone.isBlank()) add("celular")
    }
    val phoneLocked = onboardingState is OnboardingState.AwaitingOtp ||
        onboardingState == OnboardingState.WhatsAppConfirmed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(TinoPaper)
            .padding(horizontal = TinoSpacing.screen, vertical = TinoSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(TinoSpacing.sm))
        TinoLogo()
        Spacer(Modifier.height(TinoSpacing.xxl))
        Text(
            text = "Vamos preparar\nseu comércio.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = TinoGreenDark,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(TinoSpacing.xxl))
        LaunchedEffect(contextualVoiceState) {
            val fields = contextualVoiceState.fieldsFor(VoiceContext.ONBOARDING)
            fields["store_name"]?.takeIf { it.isNotBlank() }?.let { store = it }
            fields["owner_name"]?.takeIf { it.isNotBlank() }?.let { owner = it }
            fields["phone"]?.takeIf { it.isNotBlank() }?.let { phone = it }
        }
        ContextualVoicePanel(
            context = VoiceContext.ONBOARDING,
            state = contextualVoiceState,
            hint = "Conte o nome do comércio, seu nome e celular",
            onStart = onVoiceStart,
            onStop = onVoiceStop,
        )
        Spacer(Modifier.height(TinoSpacing.lg))
        TinoTextField(
            value = store,
            onValueChange = { store = it },
            label = "Nome do comércio",
            placeholder = "Mercadinho São José",
            labelAbove = true,
        )
        Spacer(Modifier.height(TinoSpacing.md))
        TinoTextField(
            value = owner,
            onValueChange = { owner = it },
            label = "Seu nome",
            placeholder = "João da Silva",
            labelAbove = true,
        )
        Spacer(Modifier.height(TinoSpacing.md))
        TinoTextField(
            value = if (phoneLocked) maskOtpPhone(phone) else phone,
            onValueChange = { phone = it },
            label = "Celular",
            placeholder = "(86) 9 1234-5678",
            labelAbove = true,
            enabled = !phoneLocked,
        )
        Spacer(Modifier.height(TinoSpacing.md))
        Text("Qual é o seu tipo de negócio?", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
        BusinessVertical.values().toList().chunked(2).forEach { options ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                options.forEach { option ->
                    TinoFilterChip(
                        label = when (option) {
                            BusinessVertical.RETAIL -> "Loja / Varejo"
                            BusinessVertical.BAKERY -> "Padaria"
                            BusinessVertical.RESTAURANT -> "Restaurante"
                            BusinessVertical.STORE -> "Comércio"
                            BusinessVertical.OTHER -> "Outro"
                        },
                        selected = vertical == option,
                        onClick = {
                            configurationError = null
                            vertical = option
                            modules = VerticalPresetCatalog.forVertical(option).defaultModules
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(TinoSpacing.md))
        Text(
            "Você já usa um sistema?",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
        )
        OnboardingChoiceRow(
            label = "Não, começar no TINO",
            selected = dataSourceChoice.sourceType == BusinessDataSourceType.TINO_NATIVE,
            onClick = { dataSourceChoice = OnboardingDataSourceChoice.Native },
        )
        OnboardingChoiceRow(
            label = "Sim, conectar meu sistema",
            selected = dataSourceChoice.sourceType == BusinessDataSourceType.EXTERNAL_API,
            onClick = { dataSourceChoice = OnboardingDataSourceChoice.DocesSonhos },
        )
        if (dataSourceChoice.sourceType == BusinessDataSourceType.EXTERNAL_API) {
            Spacer(Modifier.height(TinoSpacing.sm))
            Text(
                "Conectar sistema",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall,
            )
            OnboardingChoiceRow(
                label = "Doces & Sonhos",
                selected = true,
                onClick = {},
            )
        }
        TinoTextAction(
            label = if (customizeModules) "Ocultar recursos" else "Personalizar recursos",
            onClick = { customizeModules = !customizeModules },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Ativamos: ${modules.filter { it != BusinessModule.CORE }.joinToString { it.displayName() }}",
            color = TinoMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        if (customizeModules) {
            modulesForConfiguration().chunked(2).forEach { options ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                    options.forEach { option ->
                        TinoFilterChip(
                            label = option.displayName(),
                            selected = option in modules,
                            onClick = {
                                configurationError = null
                                val candidate = if (option in modules) modules - option else modules + option
                                runCatching {
                                    BusinessProfile(
                                        primaryVertical = vertical,
                                        enabledModules = candidate + BusinessModule.CORE,
                                        operationalPatterns = OperationalPatternCatalog.forVertical(vertical),
                                    )
                                }.onSuccess {
                                    modules = candidate
                                }.onFailure {
                                    configurationError = profileConfigurationError(it)
                                }
                            },
                        )
                    }
                }
            }
        }
        configurationError?.let { message ->
            TinoCard {
                Text("Não foi possível atualizar os recursos", color = TinoRed, fontWeight = FontWeight.SemiBold)
                Text(message, color = TinoMuted)
            }
        }
        when (onboardingState) {
            OnboardingState.Authenticating -> Text("Conectando sua conta com o TINO…", color = TinoMuted)
            OnboardingState.WhatsAppConfirmed -> Text("Acesso confirmado. Continuando com o TINO…", color = TinoMuted)
            is OnboardingState.AwaitingOtp -> Text("Confira seu WhatsApp; o acesso avança automaticamente ou você pode digitar o código.", color = TinoMuted)
            OnboardingState.LoadingBusiness -> Text("Criando o comércio no TINO…", color = TinoMuted)
            OnboardingState.RegisteringInstallation -> Text("Registrando este aparelho…", color = TinoMuted)
            is OnboardingState.Error -> TinoCard {
                Text("Não foi possível concluir o cadastro", color = TinoRed, fontWeight = FontWeight.SemiBold)
                Text(onboardingState.message, color = TinoMuted)
            }
            else -> Unit
        }
        Spacer(Modifier.height(TinoSpacing.lg))
        val onboardingWorking = onboardingState == OnboardingState.Authenticating ||
            onboardingState == OnboardingState.WhatsAppConfirmed ||
            onboardingState is OnboardingState.AwaitingOtp ||
            onboardingState == OnboardingState.LoadingBusiness ||
            onboardingState == OnboardingState.RegisteringInstallation
        TinoPrimaryButton("CONTINUAR", {
            submitAttempted = true
            if (missingFields.isEmpty()) {
                runCatching {
                    BusinessProfile(
                        primaryVertical = vertical,
                        enabledModules = modules + BusinessModule.CORE,
                        operationalPatterns = OperationalPatternCatalog.forVertical(vertical),
                    )
                }.onSuccess {
                    onContinue(store, owner, phone, vertical, modules + BusinessModule.CORE, dataSourceChoice)
                }.onFailure {
                    configurationError = profileConfigurationError(it)
                }
            }
        }, Modifier, enabled = !onboardingWorking, loading = onboardingWorking)
        if (submitAttempted && missingFields.isNotEmpty()) {
            Text(
                "Preencha: ${missingFields.joinToString(", ")}.",
                color = TinoRed,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        TinoTextAction(
            label = "Já tenho um comércio",
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth(),
            color = TinoGreenDark,
        )
    }
}

@Composable
private fun OnboardingChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = TinoInk)
    }
}

private fun modulesForConfiguration(): List<BusinessModule> = listOf(
    BusinessModule.SALES,
    BusinessModule.INVENTORY,
    BusinessModule.CUSTOMERS,
    BusinessModule.CREDIT,
    BusinessModule.STOCK_ENTRY,
    BusinessModule.FISCAL,
)

private fun BusinessModule.displayName(): String = when (this) {
    BusinessModule.CORE -> "Base"
    BusinessModule.SALES -> "Vendas"
    BusinessModule.INVENTORY -> "Estoque"
    BusinessModule.CUSTOMERS -> "Clientes"
    BusinessModule.CREDIT -> "Fiado"
    BusinessModule.STOCK_ENTRY -> "Entrada"
    BusinessModule.FISCAL -> "Fiscal"
    else -> name.lowercase().replace('_', ' ')
}

@Composable
internal fun RestoreStoreScreen(
    onBack: () -> Unit,
    onContinue: (String) -> Unit = {},
    onboardingState: OnboardingState = OnboardingState.Idle,
) {
    var phone by remember { mutableStateOf("") }
    var submitAttempted by remember { mutableStateOf(false) }
    val phoneDigits = phone.filter(Char::isDigit)
    val phoneLocked = onboardingState is OnboardingState.AwaitingOtp ||
        onboardingState == OnboardingState.WhatsAppConfirmed
    val onboardingWorking = onboardingState == OnboardingState.Authenticating ||
        onboardingState == OnboardingState.WhatsAppConfirmed ||
        onboardingState is OnboardingState.AwaitingOtp ||
        onboardingState == OnboardingState.LoadingBusiness ||
        onboardingState == OnboardingState.RegisteringInstallation
    ScreenColumn {
        TinoTopBar("Entrar no meu comércio", onBack)
        TinoCard {
            Text("Acesso ao comércio existente", style = MaterialTheme.typography.titleMedium)
            Text("Confirme seu celular para localizar o comércio autorizado no TINO.", color = TinoMuted)
        }
        TinoTextField(
            value = if (phoneLocked) maskOtpPhone(phone) else phone,
            onValueChange = { phone = it },
            label = "Celular",
            placeholder = "(86) 9 1234-5678",
            labelAbove = true,
            enabled = !phoneLocked,
        )
        when (onboardingState) {
            OnboardingState.Authenticating -> Text("Conectando sua conta com o TINO…", color = TinoMuted)
            OnboardingState.WhatsAppConfirmed -> Text("Acesso confirmado. Continuando com o TINO…", color = TinoMuted)
            is OnboardingState.AwaitingOtp -> Text("Confira seu WhatsApp; o acesso avança automaticamente ou você pode digitar o código.", color = TinoMuted)
            OnboardingState.RegisteringInstallation -> Text("Vinculando este aparelho ao comércio…", color = TinoMuted)
            is OnboardingState.Error -> TinoCard {
                Text("Não foi possível entrar no comércio", color = TinoRed, fontWeight = FontWeight.SemiBold)
                Text(onboardingState.message, color = TinoMuted)
            }
            else -> Unit
        }
        TinoPrimaryButton(
            "CONTINUAR",
            {
                submitAttempted = true
                if (phoneDigits.length in 10..13) onContinue(phone)
            },
            Modifier,
            enabled = !onboardingWorking,
            loading = onboardingWorking,
        )
        if (submitAttempted && phoneDigits.length !in 10..13) {
            Text("Informe um celular válido.", color = TinoRed)
        }
        TinoSecondaryButton("VOLTAR") { onBack() }
    }
}

@Composable
private fun BootLoadingScreen() {
    Box(
        Modifier.fillMaxSize().padding(TinoSpacing.screen),
        contentAlignment = Alignment.Center,
    ) {
        TinoLoadingState(
            icon = TinoIcons.Store,
            title = "Preparando o TINO",
            message = "Carregando seus dados locais.",
            illustrationState = TinoIllustrationState.LOADING,
        )
    }
}

@Composable
internal fun VoiceStageDiagnostics(metrics: AgenticVoiceMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.xxs)) {
        Text(
            "MIC → 1ª parcial ${metrics.firstPartialMs.durationText()} · " +
                "1ª → última ${metrics.firstToLastPartialMs.durationText()}",
            color = TinoMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "última → fim da fala ${metrics.lastPartialToEndOfSpeechMs.durationText()} · " +
                "fim → final ${metrics.endOfSpeechToFinalMs.durationText()}",
            color = TinoMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "voz total ${metrics.finalResultMs.durationText()} · " +
                "final confirmado: ${if (metrics.finalTranscriptConfirmed) "SIM" else "NÃO"}",
            color = TinoMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "spec HIT=${metrics.speculativeRouterHit} · " +
                "cancelado=${metrics.speculativeRouterCancelled} · " +
                "resultado=${metrics.speculativeResultReady}",
            color = TinoMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "COMMAND_ROUTER ${if (metrics.commandRouterHit) "HIT" else "MISS"} · " +
                "${metrics.commandRouterMs}ms",
            color = TinoMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

internal fun Long?.durationText(): String = this?.let { "${it}ms" } ?: "—"

@Composable
internal fun ProductsScreen(
    products: List<ProductSummary>,
    onNavigate: (TinoScreen) -> Unit,
    onSelectProduct: (ProductSummary) -> Unit,
    catalogSyncState: CatalogSyncState? = null,
    onSyncCatalog: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todos") }
    val searched = products.filter { it.name.contains(query, ignoreCase = true) }
    val shown = when (filter) {
        "Estoque baixo" -> searched.filter { it.stockTracked && it.stockQuantity in 1..6 }
        "Sem estoque" -> searched.filter { it.stockTracked && it.stockQuantity == 0 }
        else -> searched
    }
    val syncing = catalogSyncState?.status == CatalogSyncStatus.SYNCING
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = TinoSpacing.screen,
                top = TinoSpacing.lg,
                end = TinoSpacing.screen,
                bottom = TinoSpacing.lg,
            ),
        verticalArrangement = Arrangement.spacedBy(TinoSpacing.lg),
    ) {
        item {
            TinoTopBar("Estoque") { onNavigate(TinoScreen.Home) }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                TinoMetricCard(
                    TinoIcons.Products,
                    products.size.toString(),
                    "produtos",
                    Modifier.weight(1f),
                    TinoGreen,
                    TinoGreenLight,
                )
                TinoMetricCard(
                    TinoIcons.Warning,
                    products.count { it.stockTracked && it.stockQuantity in 0..6 }.toString(),
                    "estoque baixo",
                    Modifier.weight(1f),
                    TinoOrange,
                    TinoAmberContainer,
                )
            }
        }
        item {
            TinoSearchField(query, { query = it }, "Procurar produto")
        }
        item {
            TinoSecondaryButton(if (syncing) "ATUALIZANDO CATÁLOGO…" else "ATUALIZAR CATÁLOGO") {
                if (!syncing) onSyncCatalog()
            }
        }
        when (catalogSyncState?.status) {
            CatalogSyncStatus.SYNCING -> item {
                Text("Buscando catálogo no TINO…", color = TinoMuted)
            }
            CatalogSyncStatus.FAILED -> item {
                Text(catalogSyncState.errorMessage ?: "Catálogo local preservado.", color = TinoOrange)
            }
            CatalogSyncStatus.PARTIAL -> item {
                Text(
                    "${catalogSyncState.accepted} aplicados · ${catalogSyncState.rejected} rejeitados" +
                        if (catalogSyncState.possiblyPartial) " · consulta limitada" else "",
                    color = TinoMuted,
                )
            }
            CatalogSyncStatus.SUCCESS -> item {
                Text("${catalogSyncState.accepted} itens sincronizados", color = TinoMuted)
            }
            else -> Unit
        }
        item {
            TinoHorizontalCarousel {
                listOf("Todos", "Estoque baixo", "Sem estoque").forEach { option ->
                    item { TinoFilterChip(option, filter == option) { filter = option } }
                }
            }
        }
        item {
            TinoSectionLabel("Catálogo")
        }
        item {
            Text("Visão do estoque", style = MaterialTheme.typography.titleMedium)
        }
        if (shown.isEmpty()) {
            item {
                TinoEmptyState(
                    TinoIcons.Products,
                    if (products.isEmpty()) "Nenhum produto cadastrado" else "Nenhum produto encontrado",
                    if (products.isEmpty()) "Cadastre o primeiro produto para começar a vender." else "Tente outra busca ou cadastre um novo produto.",
                    actionLabel = "CADASTRAR PRODUTO",
                    onAction = { onNavigate(TinoScreen.NewProduct) },
                    illustrationState = if (products.isEmpty()) {
                        TinoIllustrationState.LEARNING
                    } else {
                        TinoIllustrationState.NOT_FOUND
                    },
                )
            }
        } else {
            items(shown, key = { it.id }) { product ->
                TinoProductRow(
                    product = product,
                    onClick = { onSelectProduct(product); onNavigate(TinoScreen.ProductDetail) },
                    motionBoundsEnabled = false,
                )
            }
        }
        item {
            TinoSecondaryButton("ADICIONAR PRODUTO") { onNavigate(TinoScreen.NewProduct) }
        }
    }
}

@Composable
internal fun ProductDetailScreen(product: ProductSummary?, onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        if (product == null) {
            TinoTopBar("Produto") { onNavigate(TinoScreen.Products) }
            TinoEmptyState(
                TinoIcons.Products,
                "Produto não selecionado",
                "Volte à lista e escolha um produto para ver os detalhes.",
                illustrationState = TinoIllustrationState.NOT_FOUND,
            )
        } else {
            TinoTopBar(product.name) { onNavigate(TinoScreen.Products) }
            TinoCardRenderer(
                TinoCardSpec.Catalog(
                    icon = TinoIcons.Products,
                    title = product.name,
                    context = "Produto",
                    primaryText = formatCents(product.priceCents.toLong()),
                    status = TinoCardStatus.SUCCESS,
                    modifier = Modifier.tinoSharedBounds(TinoSharedKeys.product(product.id)),
                ),
            )
            if (product.stockTracked) {
                Text("Estoque", style = MaterialTheme.typography.titleMedium)
                Text("${product.stockQuantity} unidades", style = MaterialTheme.typography.displaySmall)
                TinoSystemActionListCard(
                    title = "Ações",
                    actions = listOf(
                        TinoSystemAction(
                            icon = TinoIcons.Input,
                            title = "Registrar entrada",
                            subtitle = "Mercadoria que chegou",
                            onClick = { onNavigate(TinoScreen.StockEntry) },
                        ),
                    ),
                )
            } else {
                Text("Estoque", style = MaterialTheme.typography.titleMedium)
                Text("Feito sob demanda", style = MaterialTheme.typography.titleLarge, color = TinoGreen)
                Text("Este produto não possui quantidade em estoque.", color = TinoMuted)
            }
        }
    }
}

@Composable
internal fun QuickSaleScreen(
    products: List<ProductSummary>,
    onNavigate: (TinoScreen) -> Unit,
    contextualVoiceState: ContextualVoiceState = ContextualVoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
    onContinue: (List<SaleLine>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var cart by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val shown = products.filter { it.name.contains(query, true) }
    val source = products
    val selectedLines = source.mapNotNull { product ->
        cart[product.id]?.takeIf { it > 0 }?.let { SaleLine(product, it) }
    }
    val total = selectedLines.sumOf { it.quantity * it.product.priceCents }
    LaunchedEffect(contextualVoiceState) {
        val fields = contextualVoiceState.fieldsFor(VoiceContext.SALE)
        val reference = fields["products"]?.trim().orEmpty()
        if (reference.isNotBlank()) {
            val matches = products.filter { it.matchesVoiceReference(reference) }
            if (matches.size == 1) {
                val product = matches.single()
                val requestedQuantity = fields["quantity"]?.toIntOrNull()
                    ?: Regex("\\b([0-9]+)\\b").find(reference)?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1
                val quantity = if (product.stockTracked) {
                    requestedQuantity.coerceIn(1, product.stockQuantity.coerceAtLeast(1))
                } else {
                    requestedQuantity.coerceAtLeast(1)
                }
                cart = cart + (product.id to quantity)
                query = product.name
            } else {
                query = reference
            }
        }
    }
    ScreenColumn {
        TinoTopBar("Nova venda") { onNavigate(TinoScreen.Home) }
        ContextualVoicePanel(
            context = VoiceContext.SALE,
            state = contextualVoiceState,
            hint = "Diga qual produto e quantas unidades você quer vender",
            onStart = onVoiceStart,
            onStop = onVoiceStop,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
            TinoSearchField(query, { query = it }, "Procurar produto", Modifier.weight(1f))
        }
        Text("Produtos", style = MaterialTheme.typography.titleMedium)
        if (shown.isEmpty()) {
            TinoEmptyState(
                TinoIcons.Products,
                if (products.isEmpty()) "Nenhum produto para vender" else "Nenhum produto encontrado",
                if (products.isEmpty()) "Cadastre um produto antes de abrir uma venda." else "Tente outra busca.",
                actionLabel = if (products.isEmpty()) "CADASTRAR PRODUTO" else null,
                onAction = if (products.isEmpty()) ({ onNavigate(TinoScreen.NewProduct) }) else null,
                illustrationState = if (products.isEmpty()) {
                    TinoIllustrationState.LEARNING
                } else {
                    TinoIllustrationState.NOT_FOUND
                },
            )
        } else {
            shown.take(4).forEach { product ->
                TinoSaleProductRow(
                    product = product,
                    onAdd = { cart = cart + (product.id to ((cart[product.id] ?: 0) + 1)) },
                    enabled = !product.stockTracked || (cart[product.id] ?: 0) < product.stockQuantity,
                )
            }
        }
        TinoCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Carrinho", style = MaterialTheme.typography.titleMedium)
                Text("${selectedLines.sumOf { it.quantity }} itens", color = TinoMuted)
            }
            if (selectedLines.isEmpty()) {
                Text("Toque em um produto para adicionar", color = TinoMuted, style = MaterialTheme.typography.bodyMedium)
            } else {
                selectedLines.forEach { line ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(line.product.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                formatCents((line.quantity * line.product.priceCents).toLong()),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        TinoQuantitySelector(
                            quantity = line.quantity,
                            onDecrease = {
                                cart = cart.toMutableMap().also { next ->
                                    if (line.quantity <= 1) next.remove(line.product.id)
                                    else next[line.product.id] = line.quantity - 1
                                }
                            },
                            onIncrease = {
                                if (!line.product.stockTracked || line.quantity < line.product.stockQuantity) {
                                    cart = cart + (line.product.id to (line.quantity + 1))
                                }
                            },
                        )
                    }
                }
                HorizontalDivider()
            }
            MetricLine("Total", formatCents(total.toLong()), true, TinoGreenDark)
        }
        TinoPrimaryButton(
            label = "IR PARA PAGAMENTO",
            onClick = { onContinue(selectedLines) },
            modifier = Modifier,
            enabled = selectedLines.isNotEmpty(),
        )
    }
}

internal fun ProductSummary.matchesVoiceReference(reference: String): Boolean {
    val normalizedReference = reference.normalizeVoiceText()
    val normalizedName = name.normalizeVoiceText()
    return normalizedReference.contains(normalizedName) ||
        normalizedName.split(' ').filter { it.length > 2 }.all { it in normalizedReference.split(' ') }
}

internal fun String.normalizeVoiceText(): String = java.text.Normalizer
    .normalize(lowercase(), java.text.Normalizer.Form.NFD)
    .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    .replace("[^a-z0-9]+".toRegex(), " ")
    .trim()

@Composable
internal fun ReceiveSaleScreen(
    lines: List<SaleLine>,
    onNavigate: (TinoScreen) -> Unit,
    onSell: suspend (ProductSummary, Int, PaymentMethod) -> Result<Unit>,
    onComplete: (TinoCompletion) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var submittingMethod by remember { mutableStateOf<PaymentMethod?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    val total = lines.sumOf { it.quantity * it.product.priceCents }

    fun submit(paymentMethod: PaymentMethod) {
        if (submitting || lines.isEmpty()) return
        scope.launch {
            submitting = true
            submittingMethod = paymentMethod
            submissionError = null
            var failure: Throwable? = null
            for (line in lines) {
                val result = onSell(line.product, line.quantity, paymentMethod)
                failure = result.exceptionOrNull()
                if (failure != null) break
            }
            submitting = false
            submittingMethod = null
            if (failure == null) {
                onComplete(TinoCompletion("Venda registrada", "Pagamento salvo com segurança."))
            } else {
                submissionError = failure.message ?: "Não foi possível registrar a venda. Tente novamente."
            }
        }
    }

    ScreenColumn {
        TinoTopBar("Receber venda") { onNavigate(TinoScreen.QuickSale) }
        if (lines.isEmpty()) {
            TinoEmptyState(
                TinoIcons.Cart,
                "Nenhum item selecionado",
                "Volte à venda e escolha pelo menos um produto.",
                illustrationState = TinoIllustrationState.EXPLAINING,
            )
            TinoSecondaryButton("VOLTAR À VENDA") { onNavigate(TinoScreen.QuickSale) }
        } else {
            TinoCard {
                Text("Resumo da venda", style = MaterialTheme.typography.titleMedium)
                lines.forEach { line ->
                    MetricLine("${line.quantity} x ${line.product.name}", formatCents((line.quantity * line.product.priceCents).toLong()))
                }
                HorizontalDivider()
                MetricLine("Total", formatCents(total.toLong()), true, TinoGreenDark)
            }
            submissionError?.let { errorMessage ->
                TinoCard {
                    Text("Não foi possível registrar", style = MaterialTheme.typography.titleMedium, color = TinoRed)
                    Text(errorMessage, color = TinoMuted)
                    TinoSecondaryButton("TENTAR NOVAMENTE") { submissionError = null }
                }
            }
            Text("Como recebeu?", style = MaterialTheme.typography.titleMedium)
            PaymentChoice(TinoIcons.Cash, "Dinheiro", enabled = !submitting, loading = submittingMethod == PaymentMethod.CASH) { submit(PaymentMethod.CASH) }
            PaymentChoice(TinoIcons.Pix, "PIX", enabled = !submitting, loading = submittingMethod == PaymentMethod.PIX) { submit(PaymentMethod.PIX) }
            PaymentChoice(TinoIcons.Card, "Maquininha", enabled = !submitting, loading = submittingMethod == PaymentMethod.CARD) { submit(PaymentMethod.CARD) }
            PaymentChoice(TinoIcons.Credit, "Fiado", enabled = !submitting) { onNavigate(TinoScreen.SelectCustomer) }
        }
    }
}

@Composable
internal fun CustomersScreen(
    customers: List<CustomerBalance>,
    onNavigate: (TinoScreen) -> Unit,
    onSelectCustomer: (CustomerBalance) -> Unit,
    onAddCustomer: suspend (String, String?) -> Result<Unit>,
    contextualVoiceState: ContextualVoiceState = ContextualVoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
    openAddCustomerRequest: Boolean = false,
    onAddCustomerRequestConsumed: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todos") }
    var addVisible by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(contextualVoiceState) {
        val fields = contextualVoiceState.fieldsFor(VoiceContext.CUSTOMER_CREATE)
        if (fields.isNotEmpty()) addVisible = true
        fields["name"]?.takeIf { it.isNotBlank() }?.let { name = it }
        fields["phone"]?.takeIf { it.isNotBlank() }?.let { phone = it }
    }
    LaunchedEffect(openAddCustomerRequest) {
        if (openAddCustomerRequest) {
            addVisible = true
            onAddCustomerRequestConsumed()
        }
    }
    val shown = customers
        .filter { it.name.contains(query.trim(), ignoreCase = true) }
        .filter {
            when (filter) {
                "Em aberto" -> it.balanceCents > 0
                "Sem saldo" -> it.balanceCents <= 0
                "Com telefone" -> !it.phone.isNullOrBlank()
                else -> true
            }
        }
        .sortedBy { it.name.lowercase() }

    ScreenColumn(verticalSpacing = TinoSpacing.sm) {
        TinoTopBar("Clientes") { onNavigate(TinoScreen.Home) }
        TinoSectionLabel("Buscar e filtrar")
        TinoSearchField(query, { query = it }, "Procurar por nome")
        TinoHorizontalCarousel {
            listOf("Todos", "Em aberto", "Sem saldo", "Com telefone").forEach { option ->
                item { TinoFilterChip(option, filter == option) { filter = option } }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Clientes", style = MaterialTheme.typography.titleMedium)
            Text("${shown.size} encontrados", color = TinoMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (shown.isEmpty()) {
            TinoEmptyState(
                TinoIcons.People,
                if (customers.isEmpty()) "Nenhum cliente cadastrado" else "Nenhum cliente encontrado",
                if (customers.isEmpty()) "Adicione o primeiro cliente para começar." else "Tente outro nome ou tag.",
                illustrationState = if (customers.isEmpty()) {
                    TinoIllustrationState.LEARNING
                } else {
                    TinoIllustrationState.NOT_FOUND
                },
            )
        } else {
            shown.forEach { customer ->
                TinoCustomerRow(customer) {
                    onSelectCustomer(customer)
                }
            }
        }
        val reduceMotion = LocalTinoReduceMotion.current
        AnimatedVisibility(visible = addVisible, enter = tinoEnter(reduceMotion), exit = tinoExit(reduceMotion)) {
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                TinoTextField(name, { name = it }, "Nome", "Nome do cliente", labelAbove = true)
                TinoTextField(phone, { phone = it }, "Celular", "Opcional", labelAbove = true)
                submissionError?.let { errorMessage ->
                    TinoCard {
                        Text("Não foi possível cadastrar", color = TinoRed, fontWeight = FontWeight.SemiBold)
                        Text(errorMessage, color = TinoMuted)
                    }
                }
                ContextualVoicePanel(
                    context = VoiceContext.CUSTOMER_CREATE,
                    state = contextualVoiceState,
                    hint = "Diga o nome e o celular do cliente",
                    onStart = onVoiceStart,
                    onStop = onVoiceStop,
                )
                TinoPrimaryButton(
                    "SALVAR CLIENTE",
                    {
                        if (!submitting && name.isNotBlank()) scope.launch {
                            submitting = true
                            submissionError = null
                            val failure = onAddCustomer(name, phone.ifBlank { null }).exceptionOrNull()
                            submitting = false
                            if (failure == null) {
                                name = ""
                                phone = ""
                                addVisible = false
                            } else {
                                submissionError = failure.message ?: "Não foi possível cadastrar o cliente. Tente novamente."
                            }
                        }
                    },
                    Modifier,
                    enabled = !submitting && name.isNotBlank(),
                    loading = submitting,
                )
            }
        }
        AnimatedVisibility(visible = !addVisible, enter = tinoEnter(reduceMotion), exit = tinoExit(reduceMotion)) {
            TinoSecondaryButton("ADICIONAR CLIENTE") { addVisible = true }
        }
    }
}

@Composable
internal fun CustomerDetailScreen(
    customer: CustomerBalance?,
    onNavigate: (TinoScreen) -> Unit,
    onUpdateCustomer: suspend (CustomerBalance, String, String?) -> Result<Unit>,
) {
    var name by remember(customer?.id) { mutableStateOf(customer?.name.orEmpty()) }
    var phone by remember(customer?.id) { mutableStateOf(customer?.phone.orEmpty()) }
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    if (customer == null) {
        ScreenColumn {
            TinoTopBar("Cliente") { onNavigate(TinoScreen.Customers) }
            TinoEmptyState(
                TinoIcons.People,
                "Cliente não selecionado",
                "Volte à lista e escolha um cliente para ver os detalhes.",
                illustrationState = TinoIllustrationState.NOT_FOUND,
            )
            TinoSecondaryButton("VOLTAR A CLIENTES") { onNavigate(TinoScreen.Customers) }
        }
        return
    }
    ScreenColumn(verticalSpacing = TinoSpacing.sm) {
        TinoTopBar(customer.name) { onNavigate(TinoScreen.Customers) }
        TinoCardRenderer(
            TinoCardSpec.Catalog(
                icon = TinoIcons.Person,
                title = customer.name,
                context = if (customer.balanceCents > 0) "Em aberto na caderneta" else "Sem saldo em aberto",
                primaryText = formatCents(customer.balanceCents),
                status = if (customer.balanceCents > 0) TinoCardStatus.ERROR else TinoCardStatus.SUCCESS,
                modifier = Modifier.tinoSharedBounds(TinoSharedKeys.customer(customer.id)).tinoOccupiedBounds("customer-summary"),
                actionLabel = "Abrir caderneta",
                onAction = { onNavigate(TinoScreen.CustomerAccount) },
            ),
        )
        Text("Dados do cliente", style = MaterialTheme.typography.titleMedium)
        TinoTextField(name, { name = it }, "Nome", "Nome do cliente", labelAbove = true)
        TinoTextField(phone, { phone = it }, "Celular", "Opcional", labelAbove = true)
        submissionError?.let { errorMessage ->
            TinoCard {
                Text("Não foi possível salvar", color = TinoRed, fontWeight = FontWeight.SemiBold)
                Text(errorMessage, color = TinoMuted)
            }
        }
        TinoPrimaryButton(
            "SALVAR ALTERAÇÕES",
            {
                if (!submitting && name.isNotBlank()) scope.launch {
                    submitting = true
                    submissionError = null
                    val failure = onUpdateCustomer(customer, name, phone.ifBlank { null }).exceptionOrNull()
                    submitting = false
                    if (failure != null) submissionError = failure.message ?: "Não foi possível salvar o cliente. Tente novamente."
                }
            },
            Modifier,
            enabled = !submitting && name.isNotBlank(),
            loading = submitting,
        )
    }
}

@Composable
internal fun CreditListScreen(
    customers: List<CustomerBalance>,
    onNavigate: (TinoScreen) -> Unit,
    onAddCustomer: suspend (String, String?) -> Result<Unit>,
    onSelectCustomer: (CustomerBalance) -> Unit,
    contextualVoiceState: ContextualVoiceState = ContextualVoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
    openAddCustomerRequest: Boolean = false,
    onAddCustomerRequestConsumed: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todos") }
    var addCustomerVisible by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openAddCustomerRequest) {
        if (openAddCustomerRequest) {
            addCustomerVisible = true
            onAddCustomerRequestConsumed()
        }
    }
    val searched = customers.filter { it.name.contains(query, true) }
    val shown = when (filter) {
        "Em aberto" -> searched.filter { it.balanceCents > 0 }
        "Sem saldo" -> searched.filter { it.balanceCents <= 0 }
        else -> searched
    }
    LaunchedEffect(contextualVoiceState) {
        val fields = contextualVoiceState.fieldsFor(VoiceContext.CUSTOMER_CREATE)
        if (fields.isNotEmpty()) addCustomerVisible = true
        fields["name"]?.takeIf { it.isNotBlank() }?.let { name = it }
        fields["phone"]?.takeIf { it.isNotBlank() }?.let { phone = it }
    }
    ScreenColumn(verticalSpacing = TinoSpacing.sm) {
        TinoTopBar("Caderneta") { onNavigate(TinoScreen.Home) }
        TinoCardRenderer(
            TinoCardSpec.Metric(
                icon = TinoIcons.Credit,
                title = "Total a receber",
                value = formatCents(customers.sumOf { it.balanceCents }),
                supportingText = "${customers.count { it.balanceCents > 0 }} clientes com valor em aberto",
                modifier = Modifier.tinoOccupiedBounds("caderneta-summary"),
                status = TinoCardStatus.CREDIT,
            ),
        )
        TinoSearchField(query, { query = it }, "Procurar pessoa")
        TinoHorizontalCarousel {
            listOf("Todos", "Em aberto", "Sem saldo").forEach { option ->
                item { TinoFilterChip(option, filter == option) { filter = option } }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Saldo da caderneta", style = MaterialTheme.typography.titleMedium)
            Text("Ordenar: Maior valor", color = TinoGreen, style = MaterialTheme.typography.labelMedium)
        }
        if (shown.isEmpty()) {
            TinoEmptyState(
                TinoIcons.People,
                if (customers.isEmpty()) "Nenhum cliente cadastrado" else "Nenhum cliente encontrado",
                if (customers.isEmpty()) "Cadastre o primeiro cliente para começar a caderneta." else "Tente outra busca ou filtro.",
                illustrationState = if (customers.isEmpty()) {
                    TinoIllustrationState.LEARNING
                } else {
                    TinoIllustrationState.NOT_FOUND
                },
            )
        } else {
            shown.sortedByDescending { it.balanceCents }.forEach { customer -> TinoCustomerRow(customer) { onSelectCustomer(customer); onNavigate(TinoScreen.CustomerAccount) } }
        }
        val reduceMotion = LocalTinoReduceMotion.current
        AnimatedVisibility(visible = addCustomerVisible, enter = tinoEnter(reduceMotion), exit = tinoExit(reduceMotion)) {
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                TinoTextField(name, { name = it }, "Nome", "Nome do cliente", labelAbove = true)
                TinoTextField(phone, { phone = it }, "Celular", "Opcional", labelAbove = true)
                submissionError?.let { errorMessage ->
                    TinoCard {
                        Text("Não foi possível cadastrar", color = TinoRed, fontWeight = FontWeight.SemiBold)
                        Text(errorMessage, color = TinoMuted)
                    }
                }
                ContextualVoicePanel(
                    context = VoiceContext.CUSTOMER_CREATE,
                    state = contextualVoiceState,
                    hint = "Diga o nome e o celular do cliente",
                    onStart = onVoiceStart,
                    onStop = onVoiceStop,
                )
                TinoPrimaryButton(
                    "SALVAR CLIENTE",
                    {
                        if (!submitting && name.isNotBlank()) scope.launch {
                            submitting = true
                            submissionError = null
                            val failure = onAddCustomer(name, phone.ifBlank { null }).exceptionOrNull()
                            submitting = false
                            if (failure == null) {
                                name = ""
                                phone = ""
                                addCustomerVisible = false
                            } else {
                                submissionError = failure.message ?: "Não foi possível cadastrar o cliente. Tente novamente."
                            }
                        }
                    },
                    Modifier,
                    enabled = !submitting && name.isNotBlank(),
                    loading = submitting,
                )
            }
        }
        AnimatedVisibility(visible = !addCustomerVisible, enter = tinoEnter(reduceMotion), exit = tinoExit(reduceMotion)) {
            TinoSecondaryButton("ADICIONAR CLIENTE") { addCustomerVisible = true }
        }
    }
}

@Composable
internal fun SelectCustomerScreen(
    customers: List<CustomerBalance>,
    onNavigate: (TinoScreen) -> Unit,
    onSelectCustomer: (CustomerBalance) -> Unit,
    contextualVoiceState: ContextualVoiceState = ContextualVoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val shown = customers.filter { it.name.contains(query, true) }
    LaunchedEffect(contextualVoiceState) {
        contextualVoiceState.fieldsFor(VoiceContext.CREDIT_SALE)["customer"]
            ?.takeIf { it.isNotBlank() }
            ?.let { query = it }
    }
    ScreenColumn {
        TinoTopBar("Fiado") { onNavigate(TinoScreen.ReceiveSale) }
        ContextualVoicePanel(
            context = VoiceContext.CREDIT_SALE,
            state = contextualVoiceState,
            hint = "Diga o nome de quem está levando",
            onStart = onVoiceStart,
            onStop = onVoiceStop,
        )
        TinoSearchField(query, { query = it }, "Quem está levando?")
        if (shown.isEmpty()) {
            TinoEmptyState(
                TinoIcons.People,
                "Nenhum cliente encontrado",
                "Volte e cadastre a pessoa antes de anotar o fiado.",
                illustrationState = TinoIllustrationState.NOT_FOUND,
            )
            TinoSecondaryButton("CADASTRAR CLIENTE") { onNavigate(TinoScreen.CreditList) }
        } else {
            shown.forEach { customer ->
                TinoCustomerRow(customer) { onSelectCustomer(customer); onNavigate(TinoScreen.ConfirmCredit) }
            }
        }
    }
}

@Composable
internal fun ConfirmCreditScreen(
    customer: CustomerBalance?,
    lines: List<SaleLine>,
    onNavigate: (TinoScreen) -> Unit,
    onCreditSale: suspend (ProductSummary, String, Int) -> Result<Unit>,
    onComplete: (TinoCompletion) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    ScreenColumn {
        if (customer == null || lines.isEmpty()) {
            TinoTopBar("Confirmar fiado") { onNavigate(TinoScreen.SelectCustomer) }
            TinoEmptyState(
                TinoIcons.Credit,
                "Compra incompleta",
                "Escolha um cliente e mantenha ao menos um produto na venda.",
                illustrationState = TinoIllustrationState.WARNING,
            )
            TinoSecondaryButton("VOLTAR À VENDA") { onNavigate(TinoScreen.QuickSale) }
            return@ScreenColumn
        }
        val name = customer.name
        val current = customer.balanceCents
        val sale = lines.sumOf { it.quantity * it.product.priceCents }
        TinoTopBar("Confirmar fiado") { onNavigate(TinoScreen.SelectCustomer) }
        TinoCard {
            Text("Cliente", color = TinoMuted, style = MaterialTheme.typography.labelMedium)
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Resumo da compra", color = TinoMuted, style = MaterialTheme.typography.labelMedium)
            MetricLine("Esta compra", formatCents(sale.toLong()))
            MetricLine("Já devia", formatCents(current.toLong()))
            HorizontalDivider()
            MetricLine("Ficará devendo", formatCents((current + sale).toLong()), true, TinoRed)
            Text("Ao confirmar, esta compra será anotada na caderneta de $name.", color = TinoMuted, style = MaterialTheme.typography.bodySmall)
        }
        submissionError?.let { errorMessage ->
            TinoCard {
                Text("Não foi possível anotar o fiado", style = MaterialTheme.typography.titleMedium, color = TinoRed)
                Text(errorMessage, color = TinoMuted)
                TinoSecondaryButton("TENTAR NOVAMENTE") { submissionError = null }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
            TinoSecondaryButton("CANCELAR", { onNavigate(TinoScreen.Home) }, Modifier.weight(1f), enabled = !submitting)
            TinoPrimaryButton("ANOTAR", {
                if (!submitting) scope.launch {
                    submitting = true
                    submissionError = null
                    var failure: Throwable? = null
                    for (line in lines) {
                        failure = onCreditSale(line.product, name, line.quantity).exceptionOrNull()
                        if (failure != null) break
                    }
                    submitting = false
                    if (failure == null) onComplete(TinoCompletion("Fiado anotado", "A compra foi vinculada a $name."))
                    else submissionError = failure?.message ?: "Não foi possível anotar o fiado. Tente novamente."
                }
            }, Modifier.weight(1f), enabled = !submitting, loading = submitting)
        }
    }
}

@Composable
internal fun CustomerAccountScreen(
    customer: CustomerBalance?,
    timeline: CustomerCreditTimeline?,
    onNavigate: (TinoScreen) -> Unit,
    statement: SharedLedgerStatement? = null,
) {
    val context = LocalContext.current
    ScreenColumn {
        if (customer == null) {
            TinoTopBar("Conta do cliente") { onNavigate(TinoScreen.CreditList) }
            TinoEmptyState(
                TinoIcons.People,
                "Cliente não selecionado",
                "Volte à lista e escolha um cliente para consultar a conta.",
                illustrationState = TinoIllustrationState.NOT_FOUND,
            )
            return@ScreenColumn
        }
        TinoTopBar(customer.name) { onNavigate(TinoScreen.CreditList) }
        TinoCard(Modifier.tinoSharedBounds(TinoSharedKeys.customer(customer.id))) {
            Text("Está devendo", color = TinoMuted)
            Text(formatCents(customer.balanceCents.toLong()), color = TinoRed, style = MaterialTheme.typography.displaySmall)
        }
        timeline?.let { account ->
            val statusText = when {
                account.overdueCents > 0 -> "Atrasado"
                account.openCents > 0 -> "Em aberto"
                else -> "Quitado"
            }
            TinoStatusBadge(statusText, if (account.overdueCents > 0) TinoStatus.Error else TinoStatus.Normal)
            TinoSectionHeader("Linha do tempo")
            val events = account.ledgerEvents.map { event ->
                TimelineUiItem(
                    occurredAt = event.occurredAt,
                    label = ledgerTimelineLabel(event.type, event.paymentMethod),
                    amount = signedLedgerAmount(event.signedAmountCents),
                )
            }
            if (events.isEmpty()) {
                Text("Nenhum lançamento nesta conta ainda.", color = TinoMuted)
            } else {
                TinoCardRenderer(
                    TinoCardSpec.Timeline(
                        title = "Lançamentos",
                        items = events.map { event ->
                            Triple(event.label, event.amount, formatTimelineDate(event.occurredAt))
                        },
                        footerLabel = null,
                    ),
                )
            }
        }
        statement?.let { account ->
            TinoSecondaryButton("COMPARTILHAR EXTRATO") {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        SharedLedgerStatementFormatter.text(account, java.time.ZoneId.systemDefault()),
                    )
                }
                context.startActivity(Intent.createChooser(share, "Compartilhar extrato"))
            }
        }
        TinoPrimaryButton("RECEBER PAGAMENTO", { onNavigate(TinoScreen.ReceivePayment) }, Modifier, enabled = customer.balanceCents > 0)
    }
}

private data class TimelineUiItem(val occurredAt: Long, val label: String, val amount: String)

private fun ledgerTimelineLabel(type: SharedLedgerEventType, paymentMethod: String?): String = when (type) {
    SharedLedgerEventType.PURCHASE -> "Fiado"
    SharedLedgerEventType.PAYMENT -> "Pagou ${paymentMethodLabel(paymentMethod.orEmpty())}"
    SharedLedgerEventType.ADJUSTMENT -> "Ajuste"
    SharedLedgerEventType.REVERSAL -> "Reversão"
    SharedLedgerEventType.DISPUTE -> "Contestação"
    SharedLedgerEventType.SETTLEMENT -> "Quitação"
}

private fun signedLedgerAmount(cents: Long): String = if (cents >= 0L) {
    "+${formatCents(cents)}"
} else {
    "-${formatCents(-cents)}"
}

internal fun formatTimelineDate(timestamp: Long): String = java.time.Instant.ofEpochMilli(timestamp)
    .atZone(java.time.ZoneId.systemDefault())
    .toLocalDate()
    .let { "%02d/%02d".format(it.dayOfMonth, it.monthValue) }

internal fun paymentMethodLabel(value: String): String = when (value.lowercase()) {
    "pix" -> "no PIX"
    "card" -> "na maquininha"
    "cash" -> "em dinheiro"
    else -> "(não identificado)"
}

@Composable
internal fun ReceivePaymentScreen(
    customer: CustomerBalance?,
    onNavigate: (TinoScreen) -> Unit,
    onReceivePayment: suspend (CustomerBalance, String) -> Result<Unit>,
    onComplete: (TinoCompletion) -> Unit,
) {
    ScreenColumn {
        if (customer == null) {
            TinoTopBar("Receber pagamento") { onNavigate(TinoScreen.CreditList) }
            TinoEmptyState(
                TinoIcons.Credit,
                "Cliente não selecionado",
                "Volte ao fiado e escolha um cliente antes de receber.",
                illustrationState = TinoIllustrationState.NOT_FOUND,
            )
            TinoSecondaryButton("VOLTAR AO FIADO") { onNavigate(TinoScreen.CreditList) }
            return@ScreenColumn
        }
        var amount by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        var submitting by remember { mutableStateOf(false) }
        var submissionError by remember { mutableStateOf<String?>(null) }
        val paymentCents = parseCentsForUi(amount)
        val canConfirm = paymentCents > 0 && paymentCents <= customer.balanceCents
        TinoTopBar("Receber pagamento") { onNavigate(TinoScreen.CustomerAccount) }
        Text("${customer.name} pagou quanto?", style = MaterialTheme.typography.titleLarge)
        TinoMoneyField(amount, { amount = it })
        if (paymentCents > customer.balanceCents) {
            Text("O valor não pode ser maior que a dívida atual.", color = TinoRed, style = MaterialTheme.typography.bodyMedium)
        }
        TinoCard {
            MetricLine("Antes", formatCents(customer.balanceCents.toLong()))
            MetricLine("Pagamento", formatCents(paymentCents))
            MetricLine("Depois", formatCents((customer.balanceCents - paymentCents).coerceAtLeast(0)), true)
        }
        submissionError?.let { errorMessage ->
            TinoCard {
                Text("Não foi possível registrar o pagamento", style = MaterialTheme.typography.titleMedium, color = TinoRed)
                Text(errorMessage, color = TinoMuted)
                TinoSecondaryButton("TENTAR NOVAMENTE") { submissionError = null }
            }
        }
        TinoPrimaryButton("RECEBER PAGAMENTO", {
            if (!submitting) scope.launch {
                submitting = true
                submissionError = null
                val failure = onReceivePayment(customer, amount).exceptionOrNull()
                submitting = false
                if (failure == null) onComplete(TinoCompletion("Pagamento registrado", "O saldo de ${customer.name} foi atualizado."))
                else submissionError = failure.message ?: "Não foi possível registrar o pagamento. Tente novamente."
            }
        }, Modifier, enabled = canConfirm && !submitting, loading = submitting)
    }
}

@Composable
internal fun NewProductScreen(
    onNavigate: (TinoScreen) -> Unit,
    onAddProduct: suspend (String, String, String) -> Result<Unit>,
    contextualVoiceState: ContextualVoiceState = ContextualVoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    val priceCents = parseCentsForUi(price)
    val stockQuantity = stock.toIntOrNull()
    val canCreate = name.isNotBlank() && priceCents > 0 && stockQuantity != null && stockQuantity >= 0
    LaunchedEffect(contextualVoiceState) {
        val fields = contextualVoiceState.fieldsFor(VoiceContext.PRODUCT_CREATE)
        fields["product_name"]?.takeIf { it.isNotBlank() }?.let { name = it }
        fields["sale_price"]?.takeIf { it.isNotBlank() }?.let { price = it }
        fields["stock_initial"]?.takeIf { it.isNotBlank() }?.let { stock = it }
    }
    ScreenColumn {
        TinoContextHeader(
            title = "Novo produto",
            subtitle = "Cadastre uma vez e venda sem complicar.",
            icon = TinoIcons.Products,
            style = TinoHeaderStyle.Form,
            onBack = { onNavigate(TinoScreen.Products) },
        )
        TinoSectionLabel("Informações essenciais")
        TinoTextField(name, { name = it }, "Nome", "Ex.: Café 250g")
        TinoMoneyField(price, { price = it }, "Preço de venda")
        TinoTextField(stock, { stock = it }, "Estoque inicial", "Ex.: 12")
        if (price.isNotBlank() && priceCents <= 0) {
            Text("Informe um preço maior que zero.", color = TinoRed)
        }
        if (stock.isNotBlank() && (stockQuantity == null || stockQuantity < 0)) {
            Text("Informe um estoque igual ou maior que zero.", color = TinoRed)
        }
        submissionError?.let { errorMessage ->
            TinoCard {
                Text("Não foi possível cadastrar", style = MaterialTheme.typography.titleMedium, color = TinoRed)
                Text(errorMessage, color = TinoMuted)
                TinoSecondaryButton("TENTAR NOVAMENTE") { submissionError = null }
            }
        }
        ContextualVoicePanel(
            context = VoiceContext.PRODUCT_CREATE,
            state = contextualVoiceState,
            hint = "Diga o nome do produto e o preço de venda",
            onStart = onVoiceStart,
            onStop = onVoiceStop,
        )
        TinoPrimaryButton("CADASTRAR PRODUTO", {
            if (!submitting) scope.launch {
                submitting = true
                submissionError = null
                val failure = onAddProduct(name, price, stock).exceptionOrNull()
                submitting = false
                if (failure == null) onNavigate(TinoScreen.Products)
                else submissionError = failure.message ?: "Não foi possível cadastrar o produto. Tente novamente."
            }
        }, Modifier, enabled = canCreate && !submitting, loading = submitting)
    }
}

@Composable
internal fun AdjustStockScreen(product: ProductSummary?, onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        if (product == null) {
            TinoTopBar("Ajustar estoque") { onNavigate(TinoScreen.Products) }
            TinoEmptyState(
                TinoIcons.Products,
                "Produto não selecionado",
                "Volte à lista e escolha um produto para ajustar o estoque.",
                illustrationState = TinoIllustrationState.NOT_FOUND,
            )
            TinoSecondaryButton("VOLTAR A PRODUTOS") { onNavigate(TinoScreen.Products) }
        } else {
            TinoTopBar("Ajustar ${product.name}") { onNavigate(TinoScreen.ProductDetail) }
            TinoEmptyState(
                TinoIcons.Products,
                "Ajuste manual indisponível",
                "Para atualizar o estoque agora, registre uma entrada de mercadoria.",
                illustrationState = TinoIllustrationState.EXPLAINING,
            )
            TinoPrimaryButton("REGISTRAR ENTRADA") { onNavigate(TinoScreen.StockEntry) }
        }
    }
}

@Composable
internal fun StockEntryScreen(
    onNavigate: (TinoScreen) -> Unit,
    onReceiveStock: suspend (String, String, String, String) -> Result<Unit>,
    product: ProductSummary? = null,
    contextualVoiceState: ContextualVoiceState = ContextualVoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
) {
    var productName by remember(product?.id) { mutableStateOf(product?.name.orEmpty()) }
    var quantity by remember(product?.id) { mutableStateOf(if (product != null) "1" else "") }
    var cost by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    val canSave = productName.isNotBlank() && (quantity.toIntOrNull() ?: 0) > 0 && parseCentsForUi(cost) > 0
    LaunchedEffect(contextualVoiceState) {
        val fields = contextualVoiceState.fieldsFor(VoiceContext.STOCK_RECEIPT)
        fields["product"]?.takeIf { it.isNotBlank() }?.let { productName = it }
        fields["quantity"]?.takeIf { it.isNotBlank() }?.let { quantity = it }
        fields["unit_cost"]?.takeIf { it.isNotBlank() }?.let { cost = it }
        fields["supplier"]?.takeIf { it.isNotBlank() }?.let { supplier = it }
    }
    ScreenColumn {
        TinoContextHeader(
            title = "Entrada de mercadoria",
            subtitle = "Atualize o estoque com o que acabou de chegar.",
            icon = TinoIcons.Input,
            style = TinoHeaderStyle.Inventory,
            onBack = { onNavigate(TinoScreen.Products) },
        )
        TinoSectionLabel("Mercadoria recebida")
        if (product == null) {
            TinoTextField(productName, { productName = it }, "Produto", "Nome exato do produto")
        } else {
            TinoCard {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text("Produto selecionado", color = TinoMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (product == null) {
            TinoTextField(quantity, { quantity = it }, "Quantidade", "Ex.: 24")
        } else {
            Text("Quantidade", color = TinoMuted, style = MaterialTheme.typography.labelMedium)
            TinoQuantitySelector(
                quantity = quantity.toIntOrNull() ?: 1,
                onDecrease = { quantity = ((quantity.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString() },
                onIncrease = { quantity = ((quantity.toIntOrNull() ?: 1) + 1).toString() },
            )
        }
        TinoMoneyField(cost, { cost = it }, "Custo unitário")
        TinoTextField(supplier, { supplier = it }, "Fornecedor (opcional)", "Nome do fornecedor")
        submissionError?.let { errorMessage ->
            TinoCard {
                Text("Não foi possível registrar a entrada", style = MaterialTheme.typography.titleMedium, color = TinoRed)
                Text(errorMessage, color = TinoMuted)
                TinoSecondaryButton("TENTAR NOVAMENTE") { submissionError = null }
            }
        }
        ContextualVoicePanel(
            context = VoiceContext.STOCK_RECEIPT,
            state = contextualVoiceState,
            hint = "Diga o produto, a quantidade e o custo unitário",
            onStart = onVoiceStart,
            onStop = onVoiceStop,
        )
        TinoPrimaryButton(
            "REGISTRAR ENTRADA",
            {
                if (!submitting) scope.launch {
                    submitting = true
                    submissionError = null
                    val failure = onReceiveStock(productName, quantity, cost, supplier).exceptionOrNull()
                    submitting = false
                    if (failure == null) onNavigate(TinoScreen.Products)
                    else submissionError = failure.message ?: "Não foi possível registrar a entrada. Tente novamente."
                }
            },
            Modifier,
            enabled = canSave && !submitting,
            loading = submitting,
        )
    }
}

@Composable
internal fun FiscalFoundScreen(
    onNavigate: (TinoScreen) -> Unit,
    onImageSelected: (Uri) -> Unit = {},
) {
    ScreenColumn {
        TinoContextHeader(
            title = "Dar entrada de mercadoria",
            subtitle = "Escolha como registrar o que acabou de chegar.",
            icon = TinoIcons.Input,
            style = TinoHeaderStyle.Inventory,
            onBack = { onNavigate(TinoScreen.Products) },
        )
        TinoCard {
            Text("Usar NF-e", style = MaterialTheme.typography.titleMedium)
            Text("Informe a chave de acesso. O backend consulta a nota e prepara a conferência.", color = TinoMuted)
            Spacer(Modifier.height(TinoSpacing.md))
            TinoPrimaryButton("USAR NF-e") { onNavigate(TinoScreen.NfeKeyEntry) }
        }
        TinoCard {
            Text("Usar NFC-e", style = MaterialTheme.typography.titleMedium)
            Text("Leia o QR Code e consulte a NFC-e do Piauí com a verificação humana.", color = TinoMuted)
            Spacer(Modifier.height(TinoSpacing.md))
            TinoPrimaryButton("LER QR DA NFC-e") { onNavigate(TinoScreen.NfceCapture) }
        }
        TinoEmptyState(
            TinoIcons.Input,
            "Registrar manualmente",
            "Funciona offline e não depende de NF-e, SERPRO ou backend.",
            illustrationState = TinoIllustrationState.EXPLAINING,
        )
        TinoSecondaryButton("REGISTRAR MANUALMENTE") { onNavigate(TinoScreen.StockEntry) }
    }
}

@Composable
internal fun FiscalReviewScreen(
    onNavigate: (TinoScreen) -> Unit,
    captured: Boolean = false,
    result: ProductImportResult? = null,
    rectifiedPath: String? = null,
) {
    ScreenColumn {
        TinoTopBar("Produtos encontrados") { onNavigate(TinoScreen.FiscalFound) }
        if (captured) {
            TinoCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md),
                ) {
                    Icon(TinoIcons.Success, contentDescription = null, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconLarge))
                    Column {
                        Text("Nota lida", style = MaterialTheme.typography.titleMedium)
                        Text("Confira os produtos antes de registrar a entrada.", color = TinoMuted)
                    }
                }
            }
            rectifiedPath?.let { path ->
                FiscalRectifiedPreview(path)
            }
            when (result) {
                is ProductImportResult.Success -> {
                    Text("Confira estes itens", style = MaterialTheme.typography.titleMedium)
                    Text("A leitura terminou. Revise produto, quantidade e custo.", color = TinoMuted)
                    result.products.forEach { product -> FiscalImportedProductRow(product) }
                }
                is ProductImportResult.NeedsReview -> {
                    TinoCard {
                        Text("Alguns itens precisam de atenção", style = MaterialTheme.typography.titleMedium)
                        Text("Confira os dados destacados antes de continuar.", color = TinoOrange)
                    }
                    result.products.forEach { product -> FiscalImportedProductRow(product) }
                }
                is ProductImportResult.Unavailable -> {
                    TinoEmptyState(
                        TinoIcons.Error,
                        "Não consegui ler esta nota",
                        "Tente outra foto, com a nota inteira e boa luz.",
                        actionLabel = "TENTAR NOVAMENTE",
                        onAction = { onNavigate(TinoScreen.DocumentCamera) },
                        illustrationState = TinoIllustrationState.ERROR,
                    )
                }
                null -> TinoEmptyState(
                    TinoIcons.Document,
                    "Nenhum produto encontrado",
                    "A foto não trouxe linhas de produto legíveis.",
                    illustrationState = TinoIllustrationState.NOT_FOUND,
                )
            }
        } else {
            TinoEmptyState(
                TinoIcons.Document,
                "Nenhuma nota para conferir",
                "A conferência aparecerá aqui depois que uma nota for importada.",
                illustrationState = TinoIllustrationState.EXPLAINING,
            )
        }
        if (captured && result is ProductImportResult.Success && result.products.isNotEmpty()) {
            TinoPrimaryButton("CONTINUAR ENTRADA MANUAL") { onNavigate(TinoScreen.StockEntry) }
        } else if (captured && result is ProductImportResult.NeedsReview && result.products.isNotEmpty()) {
            TinoPrimaryButton("CORRIGIR ENTRADA MANUALMENTE") { onNavigate(TinoScreen.StockEntry) }
        } else {
            TinoPrimaryButton("ESCANEAR OUTRA NOTA") { onNavigate(TinoScreen.DocumentCamera) }
        }
        TinoSecondaryButton("VOLTAR PARA LER NOTA") { onNavigate(TinoScreen.FiscalFound) }
    }
}

@Composable
internal fun FiscalRectifiedPreview(path: String) {
    val preview = remember(path) { decodePreviewBitmap(path) }
    if (preview != null) {
        TinoCard {
            Text("Imagem corrigida", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(TinoSpacing.sm))
            Image(
                bitmap = preview,
                contentDescription = "Prévia retificada da nota",
                modifier = Modifier.fillMaxWidth().heightIn(max = TinoSize.fiscalPreviewMaxHeight),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

internal fun decodePreviewBitmap(path: String): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val maxDimension = 1200
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}

@Composable
internal fun FiscalImportedProductRow(product: com.tino.fiscal.core.ImportedProduct) {
    TinoCard {
        Text(product.description, style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                listOfNotNull(product.invoiceQuantity?.stripTrailingZeros()?.toPlainString(), product.invoiceUnit)
                    .joinToString(" ")
                    .ifBlank { "Quantidade para conferir" },
                color = TinoMuted,
            )
            product.unitCost?.let { Text("Custo unitário · R$ ${it.setScale(2).toPlainString().replace('.', ',')}") }
        }
        if (product.invoiceQuantity == null || product.invoiceUnit.isNullOrBlank() || product.confidence < 0.75f) {
            Text("Confira quantidade e unidade", color = TinoOrange, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun PurchaseSuggestionsScreen(
    products: List<ProductSummary>,
    suppliers: List<SupplierEntity>,
    onCreateSupplierOrder: suspend (String, Int, Long, String, Long) -> Result<Unit>,
    onNavigate: (TinoScreen) -> Unit,
) {
    var productQuery by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf<String?>(null) }
    var selectedSupplierId by remember { mutableStateOf<String?>(null) }
    var quantity by remember { mutableStateOf("1") }
    var unitCost by remember { mutableStateOf("0,00") }
    var expectedDays by remember { mutableStateOf("7") }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val selectedProduct = products.firstOrNull { it.id == selectedProductId }
    val matchingProducts = products.filter { it.name.contains(productQuery, ignoreCase = true) }.take(5)
    val parsedQuantity = quantity.toIntOrNull() ?: 0
    val parsedCost = parseCentsForUi(unitCost)
    val parsedDays = expectedDays.toIntOrNull() ?: 0

    ScreenColumn {
        TinoContextHeader(
            title = "Pedido ao fornecedor",
            subtitle = "Planeje a próxima reposição do seu estoque.",
            icon = TinoIcons.Supplier,
            style = TinoHeaderStyle.Inventory,
            onBack = { onNavigate(TinoScreen.More) },
        )
        TinoSectionLabel("O que precisa chegar")
        TinoCard {
            Text("Registrar uma entrega prevista", style = MaterialTheme.typography.titleMedium)
            Text("O TINO só avisará sobre atraso quando existir um pedido com data prevista e recebimento controlado.", color = TinoMuted)
        }
        if (products.isEmpty() || suppliers.isEmpty()) {
            TinoEmptyState(
                TinoIcons.Supplier,
                if (products.isEmpty()) "Cadastre um produto primeiro" else "Cadastre um fornecedor primeiro",
                "Produto e fornecedor são necessários para acompanhar uma entrega.",
                illustrationState = TinoIllustrationState.LEARNING,
            )
            TinoSecondaryButton("VOLTAR PARA MAIS") { onNavigate(TinoScreen.More) }
        } else {
            TinoSearchField(productQuery, { productQuery = it }, "Procurar produto")
            matchingProducts.forEach { product ->
                TinoListRow(
                    icon = TinoIcons.Products,
                    title = product.name,
                    supportingText = "${product.stockQuantity} ${product.unit} em estoque",
                    onClick = {
                    selectedProductId = product.id
                    productQuery = product.name
                    },
                )
            }
            selectedProduct?.let { product ->
                TinoCard {
                    Text("Produto selecionado", style = MaterialTheme.typography.titleSmall)
                    Text(product.name, fontWeight = FontWeight.SemiBold)
                }
            }
            TinoSectionLabel("De quem comprar")
            suppliers.take(5).forEach { supplier ->
                TinoFilterChip(
                    label = supplier.name,
                    selected = selectedSupplierId == supplier.id,
                    onClick = { selectedSupplierId = supplier.id },
                )
            }
            TinoTextField(quantity, { quantity = it }, "Quantidade", "Ex.: 10")
            TinoTextField(unitCost, { unitCost = it }, "Custo unitário", "Ex.: 4,50")
            TinoTextField(expectedDays, { expectedDays = it }, "Entrega em quantos dias?", "Ex.: 7")
            errorMessage?.let { Text(it, color = TinoRed) }
            TinoPrimaryButton(
                "REGISTRAR PEDIDO",
                {
                    val supplierId = selectedSupplierId
                    if (!submitting && selectedProduct != null && supplierId != null && parsedQuantity > 0 &&
                        parsedCost >= 0 && parsedDays > 0
                    ) scope.launch {
                        submitting = true
                        errorMessage = null
                        val failure = onCreateSupplierOrder(
                            selectedProduct.id,
                            parsedQuantity,
                            parsedCost,
                            supplierId,
                            System.currentTimeMillis() + parsedDays * 24L * 60L * 60L * 1_000L,
                        ).exceptionOrNull()
                        submitting = false
                        if (failure == null) onNavigate(TinoScreen.SupplierOrder)
                        else errorMessage = failure.message ?: "Não foi possível registrar o pedido."
                    }
                },
                Modifier,
                enabled = selectedProduct != null && selectedSupplierId != null && parsedQuantity > 0 &&
                    parsedCost >= 0 && parsedDays > 0 && !submitting,
                loading = submitting,
            )
        }
    }
}

@Composable
internal fun NewOrderScreen(
    products: List<ProductSummary>,
    customers: List<CustomerBalance>,
    onNavigate: (TinoScreen) -> Unit,
    onCreateOrder: suspend (String, Int, String?, String) -> Result<Unit>,
) {
    var query by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf<String?>(null) }
    var quantity by remember { mutableStateOf("1") }
    var customerName by remember { mutableStateOf("") }
    var fulfillment by remember { mutableStateOf("PICKUP") }
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    val selectedProduct = products.firstOrNull { it.id == selectedProductId }
    val shownProducts = products.filter { it.name.contains(query, ignoreCase = true) }.take(5)
    val customerSuggestions = customers.filter { it.name.contains(customerName, ignoreCase = true) }.take(3)
    val parsedQuantity = quantity.toIntOrNull() ?: 0

    ScreenColumn {
        TinoContextHeader(
            title = "Novo pedido",
            subtitle = "Monte o pedido e combine a forma de entrega.",
            icon = TinoIcons.Orders,
            style = TinoHeaderStyle.Form,
            onBack = { onNavigate(TinoScreen.Orders) },
        )
        TinoSectionLabel("Item do pedido")
        if (products.isEmpty()) {
            TinoEmptyState(
                TinoIcons.Products,
                "Nenhum produto cadastrado",
                "Cadastre um produto antes de criar um pedido.",
                illustrationState = TinoIllustrationState.LEARNING,
            )
            TinoSecondaryButton("VOLTAR A PRODUTOS") { onNavigate(TinoScreen.Products) }
        } else {
            TinoSearchField(query, { query = it }, "Procurar produto")
            shownProducts.forEach { product ->
                TinoSaleProductRow(
                    product = product,
                    onAdd = { selectedProductId = product.id; query = product.name },
                )
            }
            selectedProduct?.let { product ->
                TinoCard {
                    Text("Item do pedido", style = MaterialTheme.typography.titleMedium)
                    Text(product.name, fontWeight = FontWeight.SemiBold)
                    Text(formatCents(product.priceCents), color = TinoMuted)
                }
            }
            TinoSectionLabel("Para quem e como")
            TinoTextField(quantity, { quantity = it }, "Quantidade", "Ex.: 2")
            TinoTextField(customerName, { customerName = it }, "Cliente (opcional)", "Nome do cliente")
            customerSuggestions.forEach { customer ->
                TinoListRow(
                    icon = TinoIcons.Person,
                    title = customer.name,
                    supportingText = "Cliente cadastrado",
                    onClick = { customerName = customer.name },
                )
            }
            Text("Como será recebido?", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                TinoFilterChip("Retirada", fulfillment == "PICKUP") { fulfillment = "PICKUP" }
                TinoFilterChip("Entrega", fulfillment == "DELIVERY") { fulfillment = "DELIVERY" }
            }
            submissionError?.let { errorMessage ->
                TinoCard {
                    Text("Não foi possível criar o pedido", style = MaterialTheme.typography.titleMedium, color = TinoRed)
                    Text(errorMessage, color = TinoMuted)
                    TinoSecondaryButton("TENTAR NOVAMENTE") { submissionError = null }
                }
            }
            TinoPrimaryButton("CRIAR PEDIDO", {
                if (!submitting && selectedProduct != null && parsedQuantity > 0) scope.launch {
                    submitting = true
                    submissionError = null
                    val failure = onCreateOrder(selectedProduct.id, parsedQuantity, customerName, fulfillment).exceptionOrNull()
                    submitting = false
                    if (failure == null) onNavigate(TinoScreen.Orders)
                    else submissionError = failure.message ?: "Não foi possível criar o pedido. Tente novamente."
                }
            }, Modifier, enabled = selectedProduct != null && parsedQuantity > 0 && !submitting, loading = submitting)
        }
    }
}

@Composable
internal fun SupplierOrderScreen(
    purchases: List<com.tino.app.core.database.PurchaseEntity>,
    suppliers: List<SupplierEntity>,
    onReceiveSupplierOrder: suspend (String) -> Result<Unit>,
    onNavigate: (TinoScreen) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var receivingId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val supplierNames = suppliers.associate { it.id to it.name }
    val openPurchases = purchases.filter { it.status == com.tino.app.core.database.PurchaseStatus.ORDERED }
    ScreenColumn {
        TinoTopBar("Entregas ao fornecedor") { onNavigate(TinoScreen.More) }
        if (openPurchases.isEmpty()) {
            TinoEmptyState(
                TinoIcons.Success,
                "Nenhuma entrega pendente",
                "Os pedidos recebidos aparecerão no histórico do TINO.",
                illustrationState = TinoIllustrationState.SUCCESS,
            )
        } else {
            openPurchases.forEach { purchase ->
                TinoCard {
                    Text(supplierNames[purchase.supplierId] ?: "Fornecedor não identificado", style = MaterialTheme.typography.titleMedium)
                    Text("Entrega prevista: ${purchase.expectedDeliveryAt?.let(::formatTimelineDate) ?: "sem data"}", color = TinoMuted)
                    Text("Total: ${formatCents(purchase.totalCostCents)}", color = TinoMuted)
                    TinoPrimaryButton(
                        "REGISTRAR RECEBIMENTO",
                        {
                            if (receivingId == null) scope.launch {
                                receivingId = purchase.id
                                errorMessage = onReceiveSupplierOrder(purchase.id).exceptionOrNull()?.message
                                receivingId = null
                            }
                        },
                        Modifier,
                        enabled = receivingId == null,
                        loading = receivingId == purchase.id,
                    )
                }
            }
            errorMessage?.let { Text(it, color = TinoRed) }
        }
        TinoSecondaryButton("NOVO PEDIDO") { onNavigate(TinoScreen.PurchaseSuggestions) }
    }
}

@Composable
internal fun SuppliersScreen(
    suppliers: List<SupplierEntity>,
    onNavigate: (TinoScreen) -> Unit,
    onAddSupplier: suspend (String, String?) -> Result<Unit>,
    contextualVoiceState: ContextualVoiceState = ContextualVoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(contextualVoiceState) {
        val fields = contextualVoiceState.fieldsFor(VoiceContext.SUPPLIER_CREATE)
        fields["name"]?.takeIf { it.isNotBlank() }?.let { name = it }
        fields["phone"]?.takeIf { it.isNotBlank() }?.let { phone = it }
    }
    ScreenColumn {
        TinoContextHeader(
            title = "Fornecedores",
            subtitle = "Quem ajuda a abastecer seu comércio.",
            icon = TinoIcons.Supplier,
            style = TinoHeaderStyle.Directory,
            onBack = { onNavigate(TinoScreen.More) },
        )
        TinoSectionLabel("Lista de fornecedores")
        if (suppliers.isEmpty()) {
            TinoEmptyState(
                TinoIcons.Supplier,
                "Nenhum fornecedor cadastrado",
                "Cadastre quem abastece seu comércio para usar nas entradas.",
                illustrationState = TinoIllustrationState.LEARNING,
            )
        } else {
            suppliers.forEach { supplier ->
                TinoSupplierRow(supplier.name, "Fornecedor cadastrado", sharedKey = supplier.id)
            }
        }
        TinoSectionLabel("Novo cadastro")
        TinoTextField(name, { name = it }, "Novo fornecedor", "Nome")
        TinoTextField(phone, { phone = it }, "Celular (opcional)", "Ex.: (86) 9 4209-3500")
        submissionError?.let { errorMessage ->
            TinoCard {
                Text("Não foi possível cadastrar", style = MaterialTheme.typography.titleMedium, color = TinoRed)
                Text(errorMessage, color = TinoMuted)
                TinoSecondaryButton("TENTAR NOVAMENTE") { submissionError = null }
            }
        }
        ContextualVoicePanel(
            context = VoiceContext.SUPPLIER_CREATE,
            state = contextualVoiceState,
            hint = "Diga o nome e o celular do fornecedor",
            onStart = onVoiceStart,
            onStop = onVoiceStop,
        )
        TinoSecondaryButton("ADICIONAR FORNECEDOR", {
            if (!submitting && name.isNotBlank()) scope.launch {
                submitting = true
                submissionError = null
                val failure = onAddSupplier(name, phone.ifBlank { null }).exceptionOrNull()
                submitting = false
                if (failure == null) {
                    name = ""
                    phone = ""
                } else {
                    submissionError = failure.message ?: "Não foi possível cadastrar o fornecedor. Tente novamente."
                }
            }
        }, Modifier, enabled = !submitting && name.isNotBlank(), loading = submitting)
    }
}

@Composable
internal fun OrdersScreen(
    orders: List<com.tino.app.core.database.OrderSummary>,
    onNavigate: (TinoScreen) -> Unit,
    onOpenOrder: (String) -> Unit = {},
) {
    ScreenColumn {
        TinoTopBar("Pedidos") { onNavigate(TinoScreen.More) }
        TinoPrimaryButton("NOVO PEDIDO") { onNavigate(TinoScreen.NewOrder) }
        if (orders.isEmpty()) {
            TinoEmptyState(
                TinoIcons.Orders,
                "Nenhum pedido recebido",
                "Quando chegar um pedido, ele aparecerá aqui com o próximo passo.",
                illustrationState = TinoIllustrationState.EXPLAINING,
            )
        } else {
            Text("Pedidos recentes", style = MaterialTheme.typography.titleMedium)
            orders.forEach { order ->
                TinoOrderRow(
                    status = order.status.toOrderStatusLabel(),
                    customer = order.customerName ?: "Cliente não informado",
                    total = formatCents(order.totalCents),
                    onClick = { onOpenOrder(order.id) },
                    sharedKey = order.id,
                )
            }
        }
    }
}

private fun String.toOrderStatusLabel(): String = when (uppercase()) {
    "DRAFT" -> "Rascunho"
    "CONFIRMED" -> "Confirmado"
    "PREPARING" -> "Em preparo"
    "READY" -> "Pronto"
    "DELIVERED" -> "Entregue"
    "CANCELLED" -> "Cancelado"
    else -> this
}

@Composable
internal fun OrderDetailScreen(
    detail: com.tino.app.core.database.OrderDetail?,
    onNavigate: (TinoScreen) -> Unit,
    onUpdateStatus: suspend (String, String) -> Result<Unit> = { _, _ -> Result.success(Unit) },
) {
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ScreenColumn {
        TinoTopBar("Detalhe do pedido") { onNavigate(TinoScreen.Orders) }
        if (detail == null) {
            TinoLoadingState(
                TinoIcons.Orders,
                "Carregando pedido",
                "Aguarde os dados do pedido serem carregados.",
                illustrationState = TinoIllustrationState.LOADING,
            )
        } else {
            val order = detail.order
            TinoCard(Modifier.tinoSharedBounds(TinoSharedKeys.order(order.id))) {
                Text(order.customerName ?: "Cliente não informado", style = MaterialTheme.typography.titleLarge)
                Text("${order.fulfillment.toOrderFulfillmentLabel()} · ${order.status.toOrderStatusLabel()}", color = TinoMuted)
                Text(formatCents(order.totalCents), style = MaterialTheme.typography.headlineSmall, color = TinoGreen, fontWeight = FontWeight.Bold)
            }
            Text("Itens", style = MaterialTheme.typography.titleMedium)
            detail.items.forEach { item ->
                TinoCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(item.productName, fontWeight = FontWeight.SemiBold)
                            Text("${item.quantity} un. × ${formatCents(item.unitPriceCents)}", color = TinoMuted)
                        }
                        Text(formatCents(item.unitPriceCents * item.quantity), fontWeight = FontWeight.Bold)
                    }
                }
            }
            error?.let { message ->
                TinoCard {
                    Text("Não foi possível atualizar", color = TinoRed, fontWeight = FontWeight.Bold)
                    Text(message, color = TinoMuted)
                }
            }
            order.status.nextOrderStatus()?.let { (nextStatus, label) ->
                TinoPrimaryButton(label, {
                    if (!submitting) scope.launch {
                        submitting = true
                        error = null
                        val failure = onUpdateStatus(order.id, nextStatus).exceptionOrNull()
                        submitting = false
                        if (failure != null) error = failure.message ?: "Tente novamente."
                    }
                }, Modifier, enabled = !submitting, loading = submitting)
            }
            if (order.status == "PREPARING") {
                TinoSecondaryButton("ABRIR SEPARAÇÃO") { onNavigate(TinoScreen.Picking) }
            } else if (order.status == "READY" && order.fulfillment == "DELIVERY") {
                TinoSecondaryButton("ABRIR ENTREGA") { onNavigate(TinoScreen.Delivery) }
            }
        }
        TinoSecondaryButton("VOLTAR A PEDIDOS") { onNavigate(TinoScreen.Orders) }
    }
}

private fun String.toOrderFulfillmentLabel(): String = when (uppercase()) {
    "DELIVERY" -> "Entrega"
    "PICKUP" -> "Retirada"
    else -> this
}

private fun String.nextOrderStatus(): Pair<String, String>? = when (uppercase()) {
    "CONFIRMED" -> "PREPARING" to "INICIAR SEPARAÇÃO"
    "PREPARING" -> "READY" to "MARCAR COMO PRONTO"
    "READY" -> "DELIVERED" to "CONCLUIR PEDIDO"
    else -> null
}

@Composable
internal fun PickingScreen(
    detail: com.tino.app.core.database.OrderDetail?,
    onNavigate: (TinoScreen) -> Unit,
    onUpdateStatus: suspend (String, String) -> Result<Unit> = { _, _ -> Result.success(Unit) },
) {
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    ScreenColumn {
        TinoTopBar("Separar pedido") { onNavigate(TinoScreen.OrderDetail) }
        if (detail == null) {
            TinoLoadingState(
                TinoIcons.Orders,
                "Carregando pedido",
                "Aguarde os itens do pedido serem carregados.",
                illustrationState = TinoIllustrationState.LOADING,
            )
        } else {
            Text(detail.order.customerName ?: "Cliente não informado", style = MaterialTheme.typography.titleLarge)
            detail.items.forEach { item ->
                TinoCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.productName, fontWeight = FontWeight.SemiBold)
                        Text("${item.quantity} un.", fontWeight = FontWeight.Bold)
                    }
                }
            }
            TinoPrimaryButton("MARCAR COMO PRONTO", {
                if (!submitting) scope.launch {
                    submitting = true
                    submissionError = onUpdateStatus(detail.order.id, "READY").exceptionOrNull()?.message
                    submitting = false
                    if (submissionError == null) onNavigate(TinoScreen.OrderDetail)
                }
            }, Modifier, enabled = !submitting, loading = submitting)
            submissionError?.let { Text(it, color = TinoRed) }
        }
        TinoSecondaryButton("VOLTAR AO PEDIDO") { onNavigate(TinoScreen.OrderDetail) }
    }
}

@Composable
internal fun DeliveryScreen(
    detail: com.tino.app.core.database.OrderDetail?,
    onNavigate: (TinoScreen) -> Unit,
    onUpdateStatus: suspend (String, String) -> Result<Unit> = { _, _ -> Result.success(Unit) },
) {
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    ScreenColumn {
        TinoTopBar("Entrega") { onNavigate(TinoScreen.Orders) }
        if (detail == null) {
            TinoLoadingState(
                TinoIcons.Location,
                "Carregando entrega",
                "Aguarde os dados do pedido serem carregados.",
                illustrationState = TinoIllustrationState.LOADING,
            )
        } else {
            TinoCard {
                Text("Entregar para", style = MaterialTheme.typography.titleMedium)
                Text(detail.order.customerName ?: "Cliente não informado", style = MaterialTheme.typography.titleLarge)
                Text(formatCents(detail.order.totalCents), color = TinoGreen, fontWeight = FontWeight.Bold)
            }
            TinoPrimaryButton("CONCLUIR ENTREGA", {
                if (!submitting) scope.launch {
                    submitting = true
                    submissionError = onUpdateStatus(detail.order.id, "DELIVERED").exceptionOrNull()?.message
                    submitting = false
                    if (submissionError == null) onNavigate(TinoScreen.OrderDetail)
                }
            }, Modifier, enabled = !submitting, loading = submitting)
            submissionError?.let { Text(it, color = TinoRed) }
        }
        TinoSecondaryButton("VOLTAR A PEDIDOS") { onNavigate(TinoScreen.Orders) }
    }
}

@Composable
internal fun InsightsScreen(
    products: List<ProductSummary>,
    attentionItems: List<AttentionRecord> = emptyList(),
    onNavigate: (TinoScreen) -> Unit,
) {
    val attention = products.filter { it.stockTracked && it.stockQuantity in 0..6 }
    ScreenColumn {
        TinoTopBar("O TINO percebeu") { onNavigate(TinoScreen.Home) }
        if (attentionItems.isNotEmpty()) {
            Text("Acompanhe de perto", style = MaterialTheme.typography.titleMedium)
            attentionItems.forEach { item ->
                TinoInsightCard(
                    title = item.title,
                    message = item.explanation,
                    onView = { onNavigate(TinoScreen.Home) },
                )
            }
        } else if (attention.isEmpty()) {
            TinoEmptyState(
                TinoIcons.Trends,
                "Nenhum alerta por enquanto",
                "O TINO vai avisar quando houver algo importante no seu estoque.",
                illustrationState = TinoIllustrationState.SUCCESS,
            )
        } else {
            Text("Acompanhe de perto", style = MaterialTheme.typography.titleMedium)
            attention.forEach { product ->
                TinoInsightCard(
                    title = if (product.stockQuantity == 0) "${product.name} está sem estoque" else "${product.name} está acabando",
                    message = "Estoque atual: ${product.stockQuantity} ${product.unit}.",
                    onView = { onNavigate(TinoScreen.Products) },
                )
            }
        }
    }
}

@Composable
internal fun DailySummaryScreen(todayTotal: Long, todaySales: Int, creditTotal: Long, onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        TinoTopBar("Resumo do dia") { onNavigate(TinoScreen.More) }
        TinoCard {
            Text("Hoje", style = MaterialTheme.typography.titleMedium)
            MetricLine("Vendas", todaySales.toString())
            MetricLine("Total vendido", formatCents(todayTotal), true, TinoGreenDark)
            MetricLine("Em aberto no fiado", formatCents(creditTotal), true, TinoRed)
        }
        if (todaySales == 0) {
            TinoEmptyState(
                TinoIcons.Calendar,
                "Nenhuma venda hoje",
                "O resumo será preenchido conforme você registrar as vendas.",
                illustrationState = TinoIllustrationState.EXPLAINING,
            )
        }
    }
}

@Composable
internal fun AskTinoScreen(onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        TinoTopBar("Falar com o TINO") { onNavigate(TinoScreen.Home) }
        TinoEmptyState(TinoIcons.Conversation, "Faça uma pergunta", "Use sua voz para consultar vendas, estoque ou fiado.")
        TinoPrimaryButton("VOLTAR PARA O TINO") { onNavigate(TinoScreen.Home) }
    }
}

@Composable
internal fun A2uiValidationScreen(onNavigate: (TinoScreen) -> Unit) {
    val actionViewModel: TinoA2uiActionViewModel = hiltViewModel()
    val actionState by actionViewModel.state.collectAsStateWithLifecycle()
    var actionAccepted by remember { mutableStateOf(false) }
    val create = remember {
        A2uiSurfaceMessage(
            messageId = "physical-g310-create",
            surfaceId = "physical-g310-surface",
            operation = A2uiSurfaceOperation.CREATE_SURFACE,
            components = listOf(
                A2uiSurfaceComponent(
                    componentId = "filter",
                    type = CoreTinoComponentCatalog.CHOICE,
                    props = mapOf("label" to "Filtro", "value" to "Todos"),
                    bindings = mapOf("value" to "filter"),
                    actions = listOf(CoreTinoComponentCatalog.APPLY_FILTER.name),
                    actionLabels = mapOf(CoreTinoComponentCatalog.APPLY_FILTER.name to "Só os atrasados"),
                    actionPayloads = mapOf(
                        CoreTinoComponentCatalog.APPLY_FILTER.name to mapOf("filter" to "atrasados"),
                    ),
                ),
            ),
            dataModel = mapOf("filter" to "Todos", "answer" to "Selecione um filtro para continuar."),
        )
    }
    val createState = remember(create) {
        com.tino.app.interfaceadapter.a2ui.A2uiSurfaceState(
            surfaceId = create.surfaceId,
            components = create.components,
            dataModel = create.dataModel,
        )
    }
    LaunchedEffect(actionState) {
        if (actionState is TinoA2uiActionState.Completed) actionAccepted = true
    }
    val message = if (!actionAccepted) {
        create
    } else {
        A2uiSurfaceMessage(
            messageId = "physical-g310-update",
            surfaceId = create.surfaceId,
            operation = A2uiSurfaceOperation.UPDATE_DATA_MODEL,
            dataModel = mapOf(
                "filter" to "Só os atrasados",
                "answer" to when (val state = actionState) {
                    is TinoA2uiActionState.Completed -> when (val result = state.result) {
                        is com.tino.app.interfaceadapter.a2ui.A2uiActionDispatchResult.Agent -> result.turn.response.answer
                        else -> "Filtro aplicado pelo Agent Runtime."
                    }
                    else -> "Filtro aplicado pelo Agent Runtime."
                },
            ),
        )
    }
    ScreenColumn {
        TinoTopBar("A2UI Actions") { onNavigate(TinoScreen.More) }
        TinoCard {
            Text("G3.10 — A2UI → Agent Loop", style = MaterialTheme.typography.titleMedium)
            Text(
                "Toque em uma ação declarativa. O renderer emitirá o evento; o Agent Runtime decidirá o próximo estado.",
                color = TinoMuted,
            )
        }
        TinoA2UiSurfaceHost(
            message = message,
            onAction = { event -> actionViewModel.dispatch(event, createState) },
        )
        when (val state = actionState) {
            is TinoA2uiActionState.Processing -> Text("Processando no Agent Runtime…", color = TinoMuted)
            is TinoA2uiActionState.Rejected -> Text("Ação bloqueada: ${state.reason}", color = TinoRed)
            is TinoA2uiActionState.Completed -> Text("Evento validado e devolvido ao Agent Runtime.", color = TinoGreenDark)
            TinoA2uiActionState.Idle -> Unit
        }
        TinoPrimaryButton(
            label = "LIMPAR TESTE",
            onClick = { actionAccepted = false; actionViewModel.reset() },
            modifier = Modifier,
        )
        TinoSecondaryButton("VOLTAR") { onNavigate(TinoScreen.More) }
    }
}

@Composable
internal fun G311MutationSafetyScreen(onNavigate: (TinoScreen) -> Unit) {
    val viewModel: G311MutationSafetyViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenColumn {
        TinoTopBar("G3.11 Mutation Safety") { onNavigate(TinoScreen.More) }
        TinoCard {
            Text("Harness físico — operação segura", style = MaterialTheme.typography.titleMedium)
            Text("Esta operação é determinística e não altera vendas, estoque ou fiado reais.", color = TinoMuted)
            HorizontalDivider()
            Text("Operation: ${state.operationId}", fontWeight = FontWeight.SemiBold)
            Text("State: ${state.status}", color = if (state.status == "COMMITTED") TinoGreenDark else TinoInk)
            Text("Commit count: ${state.commitCount}")
            Text("Fingerprint: v${state.version}", color = TinoMuted)
            if (state.token.isNotBlank()) {
                Text("Token: ${state.token.take(8)}…", color = TinoMuted)
            }
        }
        TinoCard {
            Text(state.message, color = if (state.status == "REJEITADO" || state.status == "ERRO") TinoRed else TinoMuted)
        }
        TinoPrimaryButton("PREPARAR PRÉVIA") { viewModel.prepare() }
        TinoPrimaryButton("CONFIRMAR VIA A2UI") { viewModel.confirmViaA2ui() }
        TinoSecondaryButton("CONFIRMAR NOVAMENTE / REPLAY") { viewModel.replay() }
        TinoSecondaryButton("CANCELAR SEM MUTAR") { viewModel.cancel() }
        TinoSecondaryButton("TESTAR TOKEN DE OUTRA OPERAÇÃO") { viewModel.testWrongToken() }
        TinoSecondaryButton("TESTAR STALE FINGERPRINT") { viewModel.testStale() }
        TinoSecondaryButton("VOLTAR") { onNavigate(TinoScreen.More) }
    }
}

@Composable
internal fun G312MemoryScreen(onNavigate: (TinoScreen) -> Unit) {
    val viewModel: G312MemoryViewModel = hiltViewModel()
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    ScreenColumn {
        TinoTopBar("G3.12 Memória") { onNavigate(TinoScreen.More) }
        TinoCard {
            Text("Harness físico — Working + Session Memory", style = MaterialTheme.typography.titleMedium)
            Text(
                "A memória guarda contexto e rascunhos, nunca saldo, preço ou outro fato comercial.",
                color = TinoMuted,
            )
            HorizontalDivider()
            Text("Sessão: ${TinoAgentSession.DEFAULT_SESSION_ID}", fontWeight = FontWeight.SemiBold)
            Text("Tela: ${snapshot.sessionMemory.currentScreen.screen}")
            Text("Superfície: ${snapshot.sessionMemory.activeSurfaceId ?: "nenhuma"}")
            Text("Entidades recentes: ${snapshot.sessionMemory.recentEntities.joinToString { it.text }.ifBlank { "nenhuma" }}")
            Text("Objetivo: ${snapshot.sessionMemory.lastObjective?.name ?: "nenhum"}")
            Text("Turnos: ${snapshot.sessionMemory.turnCount}")
        }
        TinoCard {
            Text("Working Memory", style = MaterialTheme.typography.titleMedium)
            Text("Operação: ${snapshot.workingMemory.operationIntent?.name ?: "nenhuma"}")
            Text("Slots: ${snapshot.workingMemory.collectedSlots.entries.joinToString { "${it.key}=${it.value}" }.ifBlank { "nenhum" }}")
            Text("Pendências: ${snapshot.workingMemory.missingSlots.joinToString().ifBlank { "nenhuma" }}")
            Text("Clarificação: ${snapshot.workingMemory.pendingClarification?.prompt ?: "nenhuma"}")
        }
        TinoPrimaryButton("SALVAR CONTEXTO DA MARIA") { viewModel.seedSessionContext() }
        TinoPrimaryButton("CRIAR RASCUNHO + CLARIFICAÇÃO") { viewModel.seedWorkingMemory() }
        TinoSecondaryButton("LIMPAR WORKING MEMORY") { viewModel.clearWorkingMemory() }
        TinoSecondaryButton("LIMPAR TUDO") { viewModel.clearAll() }
        TinoSecondaryButton("VOLTAR") { onNavigate(TinoScreen.More) }
    }
}

@Composable
internal fun G4AgentLoopScreen(onNavigate: (TinoScreen) -> Unit) {
    val viewModel: G4AgentLoopViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenColumn {
        TinoTopBar("G4 Agent Loop") { onNavigate(TinoScreen.More) }
        TinoCard {
            Text("Harness físico — Observe → Plan → Execute → Replan", style = MaterialTheme.typography.titleMedium)
            Text(
                "Executa apenas leituras determinísticas de teste. Não altera vendas, estoque ou fiado.",
                color = TinoMuted,
            )
            HorizontalDivider()
            Text("Cenário: ${state.scenario.ifBlank { "nenhum" }}", fontWeight = FontWeight.SemiBold)
            Text("Status: ${state.status}", color = if (state.status == "PROTEGIDO") TinoGreenDark else TinoInk)
            Text("Terminal: ${state.terminalState}")
            Text("Turnos: ${state.turns}   Planos executados: ${state.toolCalls}   Replans: ${state.replans}")
        }
        TinoCard {
            Text(state.message, color = TinoMuted)
            if (state.trace.isNotEmpty()) {
                HorizontalDivider()
                Text("Trace: ${state.trace.joinToString(" → ")}", color = TinoMuted)
            }
        }
        TinoPrimaryButton("TESTAR OBSERVE + REPLAN") { viewModel.runObserveReplan() }
        TinoPrimaryButton("TESTAR CLARIFICAÇÃO") { viewModel.runClarification() }
        TinoSecondaryButton("TESTAR PROTEÇÃO DE LOOP") { viewModel.runLoopProtection() }
        TinoSecondaryButton("VOLTAR") { onNavigate(TinoScreen.More) }
    }
}

@Composable
internal fun G5BusinessMemoryScreen(onNavigate: (TinoScreen) -> Unit) {
    val viewModel: G5BusinessMemoryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenColumn {
        TinoTopBar("G5 Business Memory") { onNavigate(TinoScreen.More) }
        TinoCard {
            Text("Harness físico — memória governada", style = MaterialTheme.typography.titleMedium)
            Text("Guarda interpretações persistentes, nunca saldo, preço ou estoque.", color = TinoMuted)
            HorizontalDivider()
            Text(state.message, color = TinoMuted)
            state.records.forEach { record ->
                HorizontalDivider()
                Text("${record.memoryKey} → ${record.value}", fontWeight = FontWeight.SemiBold)
                Text("${record.lifecycle} · confiança ${record.confidence.value} · evidências ${record.supportCount}", color = TinoMuted)
            }
        }
        TinoPrimaryButton("REGISTRAR CORREÇÃO: MARACÁ → MARATÁ") { viewModel.recordCorrection() }
        TinoPrimaryButton("CONFIRMAR E PROMOVER") { viewModel.confirm() }
        TinoSecondaryButton("CONTRADIZER / DEMOVER") { viewModel.contradict() }
        TinoSecondaryButton("REMOVER MEMÓRIA") { viewModel.remove() }
        TinoSecondaryButton("RECARREGAR DO ROOM") { viewModel.reload() }
        TinoSecondaryButton("VOLTAR") { onNavigate(TinoScreen.More) }
    }
}

@Composable
internal fun SyncDetailsScreen(pendingSyncCount: Int, onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        TinoTopBar("Seus dados") { onNavigate(TinoScreen.Settings) }
        TinoCard {
            Text("Dados locais protegidos", style = MaterialTheme.typography.titleMedium)
            Text("O TINO continua funcionando neste aparelho mesmo sem internet.", color = TinoMuted)
            HorizontalDivider()
            MetricLine("Alterações aguardando sync", pendingSyncCount.toString(), true, if (pendingSyncCount == 0) TinoGreenDark else TinoOrange)
        }
        if (pendingSyncCount > 0) {
            TinoOfflineBanner("As alterações serão enviadas quando a conexão estiver disponível.")
        } else {
            TinoEmptyState(
                TinoIcons.Synced,
                "Tudo em dia neste aparelho",
                "Não há alterações locais aguardando sincronização.",
                illustrationState = TinoIllustrationState.SUCCESS,
            )
        }
    }
}

@Composable
internal fun MoreScreen(onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn(verticalSpacing = TinoSpacing.sm) {
        TinoTopBar("Mais") { onNavigate(TinoScreen.Home) }
        TinoSectionLabel("Operação")
        TinoMoreRow(TinoIcons.Orders, "Pedidos", "Ver e gerenciar pedidos") { onNavigate(TinoScreen.Orders) }
        TinoMoreRow(TinoIcons.Supplier, "Fornecedores", "Lista de fornecedores") { onNavigate(TinoScreen.Suppliers) }
        TinoMoreRow(TinoIcons.Cart, "Comprar", "Fazer novas compras") { onNavigate(TinoScreen.PurchaseSuggestions) }
        TinoMoreRow(TinoIcons.Calendar, "Histórico de compras", "Compras, entradas e custos") { onNavigate(TinoScreen.PurchaseHistory) }
        Spacer(Modifier.height(TinoSpacing.sm))
        TinoSectionLabel("Meu comércio")
        TinoMoreRow(TinoIcons.Calendar, "Resumo", "Visão geral do seu comércio") { onNavigate(TinoScreen.DailySummary) }
        TinoMoreRow(TinoIcons.Document, "Notas", "Anotações e lembretes") { onNavigate(TinoScreen.FiscalFound) }
        TinoMoreRow(TinoIcons.People, "Clientes", "Gerenciar seus clientes") { onNavigate(TinoScreen.Customers) }
        Spacer(Modifier.height(TinoSpacing.sm))
        TinoSectionLabel("TINO")
        TinoMoreRow(TinoIcons.Settings, "Configurações", "Ajustes do aplicativo") { onNavigate(TinoScreen.Settings) }
        TinoMoreRow(TinoIcons.Document, "Diagnóstico do catálogo", "Ver logs e dados da sincronização") { onNavigate(TinoScreen.CatalogDiagnostics) }
        TinoMoreRow(TinoIcons.Offline, "Modo offline", "Trabalhar sem internet") { onNavigate(TinoScreen.Offline) }
        if (BuildConfig.DEBUG) {
            TinoMoreRow(TinoIcons.Document, "A2UI Actions", "Validar Agent Loop no device") { onNavigate(TinoScreen.A2uiValidation) }
            TinoMoreRow(TinoIcons.Settings, "G3.11 Mutation Safety", "Confirmar, cancelar, replay e restart") { onNavigate(TinoScreen.G311MutationSafety) }
            TinoMoreRow(TinoIcons.Settings, "G3.12 Memória", "Working, sessão, TTL e restart") { onNavigate(TinoScreen.G312Memory) }
            TinoMoreRow(TinoIcons.Settings, "G4 Agent Loop", "Observe, replanejar, clarificar e proteger loop") { onNavigate(TinoScreen.G4AgentLoop) }
            TinoMoreRow(TinoIcons.Settings, "G5 Business Memory", "Correção, promoção, demote, remove e restart") { onNavigate(TinoScreen.G5BusinessMemory) }
        }
    }
}

@Composable
internal fun CatalogDiagnosticsScreen(
    diagnostics: CatalogSyncDiagnostics?,
    onNavigate: (TinoScreen) -> Unit,
) {
    ScreenColumn {
        TinoTopBar("Diagnóstico do catálogo") { onNavigate(TinoScreen.More) }
        if (diagnostics == null) {
            TinoEmptyState(
                TinoIcons.Document,
                "Nenhuma sincronização registrada",
                "Abra Estoque e toque em Atualizar catálogo para gerar os dados desta tela.",
                illustrationState = TinoIllustrationState.EXPLAINING,
            )
        } else {
            TinoCard {
                Text("Status: ${diagnostics.status}", style = MaterialTheme.typography.titleMedium)
                Text("Total retornado: ${diagnostics.total}", color = TinoMuted)
                Text("Aceitos: ${diagnostics.accepted} · Rejeitados: ${diagnostics.rejected}", color = TinoMuted)
                Text("Consulta limitada: ${if (diagnostics.possiblyPartial) "sim" else "não"}", color = TinoMuted)
                diagnostics.errorMessage?.let { Text("Erro: $it", color = TinoOrange) }
            }
            TinoSectionLabel("Logs da última tentativa")
            diagnostics.logs.forEach { entry ->
                TinoCard {
                    Text("${entry.status} · ${entry.step}", style = MaterialTheme.typography.labelLarge)
                    Text(entry.detail, color = TinoMuted)
                    Text(entry.timestamp.toString(), color = TinoMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
internal fun PurchaseHistoryScreen(
    onNavigate: (TinoScreen) -> Unit,
    onLoad: suspend (String) -> PurchaseHistory,
    onLoadDetail: suspend (String) -> PurchaseHistoryDetail,
    onLoadInsights: suspend (String) -> List<PurchaseInsight>,
) {
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf("MONTH") }
    var history by remember { mutableStateOf<PurchaseHistory?>(null) }
    var detail by remember { mutableStateOf<PurchaseHistoryDetail?>(null) }
    var insights by remember { mutableStateOf<List<PurchaseInsight>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(period) {
        error = null
        detail = null
        runCatching {
            history = onLoad(period)
            insights = onLoadInsights(period)
        }.onFailure { error = it.message ?: "Não foi possível carregar o histórico." }
    }
    ScreenColumn {
            TinoTopBar("Histórico de compras") { onNavigate(TinoScreen.More) }
        Row(horizontalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            listOf("WEEK" to "Semana", "MONTH" to "Mês", "YEAR" to "Ano").forEach { (value, label) ->
                if (period == value) TinoPrimaryButton(label, { period = value }, Modifier.weight(1f))
                else TinoSecondaryButton(label, { period = value }, Modifier.weight(1f))
            }
        }
        error?.let { TinoCard { Text(it, color = TinoRed) } }
        history?.let { current ->
            TinoCard {
                Text("Resumo", style = MaterialTheme.typography.titleMedium)
                MetricLine("Compras", current.purchaseCount.toString(), true, TinoGreenDark)
                MetricLine("Itens recebidos", current.itemCount.toString(), true, TinoGreenDark)
                MetricLine("Produtos novos", current.newProductCount.toString(), true, TinoOrange)
                MetricLine("Total", "R$ ${current.total.toPlainString()}", true, TinoGreenDark)
            }
            current.purchases.forEach { purchase ->
                TinoCard {
                    Text(purchase.issuerName ?: "Fornecedor não informado", style = MaterialTheme.typography.titleMedium)
                    Text("${purchase.confirmedAt.toLocalDate()} · R$ ${purchase.total?.toPlainString() ?: "não informado"}")
                    Text("${purchase.itemCount} itens · ${purchase.newProductCount} novos")
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { onLoadDetail(purchase.receiptId) }
                                .onSuccess { detail = it }
                                .onFailure { error = it.message ?: "Não foi possível abrir a compra." }
                        }
                    }) { Text("Ver detalhes") }
                }
            }
            if (insights.isNotEmpty()) {
                TinoCard {
                    Text("O TINO percebeu", style = MaterialTheme.typography.titleMedium)
                    insights.forEach { insight -> Text(insight.message) }
                }
            }
        }
        detail?.let { selected ->
            TinoCard {
                Text("Detalhes da compra", style = MaterialTheme.typography.titleMedium)
                Text("Chave: ${selected.accessKey}", style = MaterialTheme.typography.bodySmall)
                selected.items.forEach { item ->
                    Text("${item.lineNumber}. ${item.description} · ${item.stockQuantity ?: item.quantity ?: "—"} ${item.unit ?: ""} · ${item.matchStatus}")
                }
            }
        }
    }
}

@Composable
private fun TinoMoreRow(
    icon: ImageVector,
    label: String,
    detail: String,
    onClick: () -> Unit,
) {
    TinoMenuRow(icon, label, onClick, detail)
    HorizontalDivider(color = TinoGreenBorder.copy(alpha = 0.55f))
}

@Composable
internal fun SettingsScreen(
    profile: BusinessProfile?,
    onOpenBusinessProfile: () -> Unit,
    onNavigate: (TinoScreen) -> Unit,
) {
    ScreenColumn {
        TinoTopBar("Configurações") { onNavigate(TinoScreen.More) }
        TinoMenuRow(
            TinoIcons.Settings,
            "Meu negócio",
            onOpenBusinessProfile,
        )
        Text("Dados e segurança", modifier = Modifier.tinoOccupiedBounds("settings-security"), style = MaterialTheme.typography.titleMedium)
        TinoMenuRow(TinoIcons.Synced, "Backup e sincronização", onClick = { onNavigate(TinoScreen.SyncDetails) })
        TinoMenuRow(TinoIcons.Offline, "Trabalhar sem internet", onClick = { onNavigate(TinoScreen.Offline) })
        Text("Recursos do aparelho", modifier = Modifier.tinoOccupiedBounds("settings-device"), style = MaterialTheme.typography.titleMedium)
        TinoCard {
            Text("Som, voz, impressora e acessibilidade", fontWeight = FontWeight.SemiBold)
            Text("Essas opções serão configuradas quando os recursos estiverem disponíveis neste aparelho.", color = TinoMuted)
        }
        Text("Notas fiscais", modifier = Modifier.tinoOccupiedBounds("settings-fiscal"), style = MaterialTheme.typography.titleMedium)
        TinoMenuRow(TinoIcons.Document, "Importar nota fiscal", onClick = { onNavigate(TinoScreen.FiscalFound) })
    }
}

@Composable
internal fun BusinessProfileSettingsScreen(
    profile: BusinessProfile?,
    onSave: suspend (BusinessProfile) -> Result<Unit>,
    onNavigate: (TinoScreen) -> Unit,
) {
    val current = profile ?: BusinessProfile(
        primaryVertical = BusinessVertical.RETAIL,
        enabledModules = VerticalPresetCatalog.forVertical(BusinessVertical.RETAIL).defaultModules,
    )
    var vertical by remember(current) { mutableStateOf(current.primaryVertical) }
    var modules by remember(current) { mutableStateOf(current.enabledModules) }
    var permanentCapabilities by remember(current) { mutableStateOf(current.permanentCapabilities) }
    var customize by remember(current) { mutableStateOf(false) }
    var configurationError by remember(current) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    val selectableModules = modulesForConfiguration()

    ScreenColumn {
        TinoTopBar("Meu negócio") { onNavigate(TinoScreen.Settings) }
        TinoCard {
            Text("Perfil do estabelecimento", style = MaterialTheme.typography.titleMedium)
            Text("O perfil organiza a Home, a navegação e os recursos do TINO. Seus produtos, clientes e vendas não são apagados.", color = TinoMuted)
        }
        Text("Tipo de negócio", style = MaterialTheme.typography.titleMedium)
        TinoHorizontalCarousel {
            BusinessVertical.values().forEach { option ->
                item {
                    TinoFilterChip(
                        label = option.displayName(),
                        selected = vertical == option,
                        onClick = {
                            configurationError = null
                            vertical = option
                            modules = VerticalPresetCatalog.forVertical(option).defaultModules
                        },
                    )
                }
            }
        }
        Text("Recursos ativos", style = MaterialTheme.typography.titleMedium)
        Text("Ative apenas o que faz sentido para o seu dia a dia.", color = TinoMuted)
        if (!customize) {
            Text("Preset atual: ${vertical.displayName()}", fontWeight = FontWeight.SemiBold)
            TinoTextAction("PERSONALIZAR RECURSOS", { customize = true }, color = TinoGreenDark)
        } else {
            selectableModules.forEach { module ->
                TinoFilterChip(
                    label = module.displayName(),
                    selected = module in modules,
                    onClick = {
                        configurationError = null
                        val candidate = if (module in modules) modules - module else modules + module
                        runCatching {
                            current.copy(
                                primaryVertical = vertical,
                                enabledModules = candidate,
                                operationalPatterns = if (vertical == current.primaryVertical) {
                                    current.effectiveOperationalPatterns()
                                } else {
                                    OperationalPatternCatalog.forVertical(vertical)
                                },
                            )
                        }.onSuccess {
                            modules = candidate
                        }.onFailure {
                            configurationError = profileConfigurationError(it)
                        }
                    },
                )
            }
            Text("CORE permanece ativo porque é necessário para o funcionamento do TINO.", color = TinoMuted, style = MaterialTheme.typography.bodySmall)
        }
        configurationError?.let { message ->
            TinoCard {
                Text("Não foi possível atualizar os recursos", color = TinoRed, fontWeight = FontWeight.SemiBold)
                Text(message, color = TinoMuted)
            }
        }
        if (permanentCapabilities.isNotEmpty()) {
            Text("Acessos ativados sempre", style = MaterialTheme.typography.titleSmall)
            Text(
                "Esses recursos ficam disponíveis mesmo fora do preset atual. Você pode removê-los sem apagar dados.",
                color = TinoMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            permanentCapabilities.sortedBy { it.name }.forEach { capability ->
                TinoSecondaryButton("DESATIVAR ${capability.name.replace('_', ' ')}") {
                    permanentCapabilities = permanentCapabilities - capability
                }
            }
        }
        TinoPrimaryButton("SALVAR CONFIGURAÇÃO", {
            configurationError = null
            val candidate = runCatching {
                current.copy(
                    primaryVertical = vertical,
                    enabledModules = modules + BusinessModule.CORE,
                    permanentCapabilities = permanentCapabilities,
                    operationalPatterns = if (vertical == current.primaryVertical) {
                        current.effectiveOperationalPatterns()
                    } else {
                        OperationalPatternCatalog.forVertical(vertical)
                    },
                )
            }
            candidate.onFailure {
                configurationError = profileConfigurationError(it)
            }.onSuccess { nextProfile ->
                if (!submitting) scope.launch {
                    submitting = true
                    val failure = onSave(nextProfile).exceptionOrNull()
                    submitting = false
                    if (failure == null) onNavigate(TinoScreen.Settings)
                    else configurationError = profileConfigurationError(failure)
                }
            }
        }, Modifier, enabled = !submitting, loading = submitting)
        TinoSecondaryButton("CANCELAR", { onNavigate(TinoScreen.Settings) }, Modifier, enabled = !submitting)
    }
}

private fun profileConfigurationError(error: Throwable): String = when {
    error.message?.contains("STOCK_ENTRY") == true ->
        "Para desativar Estoque, desative Entrada de mercadoria primeiro."
    error.message?.contains("CREDIT") == true ->
        "Para desativar Clientes, desative Fiado primeiro."
    else ->
        error.message ?: "Revise os recursos ativos e tente novamente."
}

private fun BusinessVertical.displayName(): String = when (this) {
    BusinessVertical.RETAIL -> "Loja / varejo"
    BusinessVertical.BAKERY -> "Padaria"
    BusinessVertical.RESTAURANT -> "Restaurante"
    BusinessVertical.STORE -> "Comércio"
    BusinessVertical.OTHER -> "Outro"
}

@Composable
internal fun OfflineScreen(pendingSyncCount: Int, onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        TinoTopBar("Sem internet") { onNavigate(TinoScreen.Home) }
        TinoCard {
            TinoOfflineBanner(
                if (pendingSyncCount == 0) "Você pode continuar trabalhando sem internet."
                else "$pendingSyncCount alterações serão enviadas quando a conexão voltar.",
            )
        }
        Text("Vendas, estoque e fiado continuam disponíveis neste aparelho.", color = TinoMuted)
        TinoPrimaryButton("VER SINCRONIZAÇÃO") { onNavigate(TinoScreen.SyncDetails) }
    }
}

@Composable
internal fun VoiceErrorScreen(onNavigate: (TinoScreen) -> Unit) = ChoiceFlowScreen("Não entendi direito", TinoIcons.Error, "Não consegui transformar o que ouvi em uma operação segura. Você pode tentar pelo botão de voz na tela inicial ou continuar manualmente.", "VOLTAR AO TINO", "FAZER MANUALMENTE", onNavigate, TinoScreen.QuickSale, TinoScreen.Home)

@Composable
internal fun AmbiguityScreen(onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        TinoTopBar("Escolher produto") { onNavigate(TinoScreen.Home) }
        TinoCard {
            Icon(TinoIcons.Search, contentDescription = null, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconLarge))
            Text("Encontrei mais de um produto parecido.", style = MaterialTheme.typography.titleMedium)
            Text("Fale o nome completo ou escolha manualmente na venda.", color = TinoMuted)
        }
        TinoSecondaryButton("VOLTAR AO TINO") { onNavigate(TinoScreen.Home) }
        TinoPrimaryButton("ABRIR VENDA MANUAL") { onNavigate(TinoScreen.QuickSale) }
    }
}

@Composable
internal fun NotificationScreen(
    products: List<ProductSummary>,
    attentionItems: List<AttentionRecord> = emptyList(),
    onNavigate: (TinoScreen) -> Unit,
) {
    val attention = products.filter { it.stockTracked && it.stockQuantity in 0..6 }
    ScreenColumn {
        TinoTopBar("Avisos do TINO") { onNavigate(TinoScreen.Home) }
        if (attentionItems.isNotEmpty()) {
            attentionItems.forEach { item ->
                TinoInsightCard(
                    title = item.title,
                    message = item.explanation,
                    onView = { onNavigate(TinoScreen.Home) },
                )
            }
        } else if (attention.isEmpty()) {
            TinoEmptyState(
                TinoIcons.Success,
                "Nenhum aviso novo",
                "Quando houver algo importante, o TINO avisará por aqui.",
                illustrationState = TinoIllustrationState.SUCCESS,
            )
        } else {
            attention.forEach { product ->
                TinoInsightCard(
                    title = product.name,
                    message = if (product.stockQuantity == 0) "Produto sem estoque." else "Restam ${product.stockQuantity} ${product.unit}.",
                    onView = { onNavigate(TinoScreen.Products) },
                )
            }
        }
        TinoSecondaryButton("VOLTAR PARA HOJE") { onNavigate(TinoScreen.Home) }
    }
}

@Composable
internal fun UnderstoodScreen(onNavigate: (TinoScreen) -> Unit) = ChoiceFlowScreen(
    title = "Confira os dados",
    icon = TinoIcons.Products,
    body = "Revise os dados reconhecidos antes de confirmar a entrada.",
    secondary = "CORRIGIR",
    primary = "CONFIRMAR ENTRADA",
    onNavigate = onNavigate,
    target = TinoScreen.Completed,
    secondaryTarget = TinoScreen.Correction,
)

@Composable
internal fun CorrectionScreen(onNavigate: (TinoScreen) -> Unit) {
    var quantity by remember { mutableStateOf(1) }
    ScreenColumn {
        TinoTopBar("Corrigir entrada") { onNavigate(TinoScreen.Understood) }
        TinoCard {
            Text("Revise o produto e a quantidade", style = MaterialTheme.typography.titleMedium)
            Text("Os valores serão confirmados antes de salvar.", color = TinoMuted)
            TinoQuantitySelector(quantity, { quantity = (quantity - 1).coerceAtLeast(0) }, { quantity++ })
        }
        Spacer(Modifier.height(TinoSpacing.xl))
        TinoSecondaryButton("VOLTAR AO TINO") { onNavigate(TinoScreen.Home) }
        TinoPrimaryButton("SALVAR ALTERAÇÃO") { onNavigate(TinoScreen.Completed) }
    }
}

@Composable
internal fun CompletedScreen(
    onNavigate: (TinoScreen) -> Unit,
    completion: TinoCompletion = TinoCompletion(),
) {
    ScreenColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(TinoSpacing.xxl))
        Box(Modifier.size(TinoSize.successBadge).background(TinoGreenLight, CircleShape), contentAlignment = Alignment.Center) {
            Icon(TinoIcons.Success, contentDescription = "Concluído", tint = TinoGreen, modifier = Modifier.size(TinoSize.successIcon))
        }
        Text(completion.title, style = MaterialTheme.typography.titleLarge)
        Text(completion.detail, textAlign = TextAlign.Center, color = TinoMuted)
        Text("A operação foi salva neste aparelho.", textAlign = TextAlign.Center, color = TinoMuted)
        Spacer(Modifier.height(TinoSpacing.xl))
        TinoPrimaryButton("PRONTO") { onNavigate(TinoScreen.Home) }
    }
}

@Composable
internal fun ChoiceFlowScreen(title: String, icon: ImageVector, body: String, secondary: String, primary: String, onNavigate: (TinoScreen) -> Unit, target: TinoScreen, secondaryTarget: TinoScreen? = null) {
    ScreenColumn {
        TinoTopBar(title) { onNavigate(TinoScreen.Home) }
        TinoCard {
            Icon(icon, contentDescription = null, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconLarge))
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(TinoSpacing.xl))
        if (secondaryTarget != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                TinoSecondaryButton(secondary, { onNavigate(secondaryTarget) }, Modifier.weight(1f))
                TinoPrimaryButton(primary, { onNavigate(target) }, Modifier.weight(1f))
            }
        } else {
            TinoSecondaryButton(secondary) {}
            TinoPrimaryButton(primary) { onNavigate(target) }
        }
    }
}

@Composable
internal fun PaymentChoice(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val choiceEnabled = enabled && !loading
    TinoCardSurface(
        modifier = Modifier.tinoInteractiveBounds("payment-choice:" + label),
        status = if (choiceEnabled) TinoCardStatus.SUCCESS else TinoCardStatus.NEUTRAL,
        onClick = if (choiceEnabled) onClick else null,
        description = "Forma de pagamento: $label",
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = TinoSize.minTouch), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md)) {
            Icon(icon, contentDescription = label, tint = if (choiceEnabled) TinoGreen else TinoMuted)
            Text(label, fontWeight = FontWeight.Bold, color = if (choiceEnabled) TinoInk else TinoMuted)
            if (loading) {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(
                    modifier = Modifier.size(TinoSize.iconSmall),
                    color = TinoGreen,
                    strokeWidth = TinoSize.progressStrokeWidth,
                )
            }
        }
    }
}

@Composable
internal fun ScreenColumn(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalSpacing: Dp = TinoSpacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val scrollTelemetry = LocalTinoScrollTelemetry.current
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { offset ->
            scrollTelemetry.offsetPx = offset
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = TinoSpacing.screen,
                top = TinoSpacing.lg,
                end = TinoSpacing.screen,
                bottom = TinoSpacing.lg,
            ),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

@Composable
internal fun MetricLine(label: String, value: String, bold: Boolean = false, color: Color = TinoInk) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}

internal fun demoProducts() = listOf(
    ProductSummary("demo-1", "Café Maratá", 850, "un", 6),
    ProductSummary("demo-2", "Leite integral", 600, "un", 18),
    ProductSummary("demo-3", "Açúcar Cristal", 500, "un", 0),
)

internal fun demoCustomers() = listOf(
    CustomerBalance("demo-1", "João Ferreira", null, 7200),
    CustomerBalance("demo-2", "Maria", null, 1850),
    CustomerBalance("demo-3", "Antônio", null, 0),
)

internal fun formatCents(cents: Long): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0)

internal fun parseCentsForUi(value: String): Long = value
    .trim()
    .replace("R$", "")
    .replace(" ", "")
    .replace(',', '.')
    .toBigDecimalOrNull()
    ?.movePointRight(2)
    ?.toLong()
    ?: 0L

@Composable
internal fun PreviewFrame(content: @Composable () -> Unit) {
    TinoTheme { Surface(Modifier.fillMaxSize(), color = TinoPaper) { content() } }
}

@Preview(name = "UI-001 Splash", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi001() = PreviewFrame { TinoSplashScreen(onFinished = {}) }
@Preview(name = "UI-002 First access", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi002() = PreviewFrame { FirstAccessScreen({ _, _, _, _, _, _ -> }, {}) }
@Preview(name = "UI-003 Restore store", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi003() = PreviewFrame { RestoreStoreScreen({}) }
@Preview(name = "UI-004 Home", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi004() = PreviewFrame {
    HomeScreen(
        todayTotal = 84750,
        todayReceived = 84750,
        todayCash = 58000,
        todayPix = 26750,
        todayCard = 0,
        todaySales = 27,
        creditTotal = 142070,
        creditCustomers = 5,
        onNavigate = {},
    )
}
@Preview(name = "UI-005 Voice listening", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi005() = PreviewFrame { AskTinoScreen(onNavigate = {}) }
@Preview(name = "UI-006 Interpretation", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi006() = PreviewFrame { UnderstoodScreen {} }
@Preview(name = "UI-007 Correction", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi007() = PreviewFrame { CorrectionScreen {} }
@Preview(name = "UI-008 Success", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi008() = PreviewFrame { CompletedScreen(onNavigate = {}) }
@Preview(name = "UI-009 New sale", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi009() = PreviewFrame { QuickSaleScreen(demoProducts(), {}, onContinue = {}) }
@Preview(name = "UI-010 Payment", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi010() = PreviewFrame { ReceiveSaleScreen(listOf(SaleLine(demoProducts().first(), 1)), {}, { _, _, _ -> Result.success(Unit) }, {}) }
@Preview(name = "UI-011 Credit customer", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi011() = PreviewFrame { SelectCustomerScreen(demoCustomers(), {}, {}) }
@Preview(name = "UI-012 Credit confirmation", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi012() = PreviewFrame { ConfirmCreditScreen(demoCustomers().first(), listOf(SaleLine(demoProducts().first(), 1)), {}, { _, _, _ -> Result.success(Unit) }, {}) }
@Preview(name = "UI-013 Credit list", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi013() = PreviewFrame { CreditListScreen(demoCustomers(), {}, { _, _ -> Result.success(Unit) }, {}) }
@Preview(name = "UI-014 Customer account", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi014() = PreviewFrame { CustomerAccountScreen(demoCustomers().first(), null, {}) }
@Preview(name = "UI-015 Credit payment", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi015() = PreviewFrame { ReceivePaymentScreen(demoCustomers().first(), {}, { _, _ -> Result.success(Unit) }, {}) }
@Preview(name = "UI-016 Product list", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi016() = PreviewFrame { ProductsScreen(demoProducts(), {}, {}) }
@Preview(name = "UI-017 Product detail", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi017() = PreviewFrame { ProductDetailScreen(demoProducts().first(), {}) }
@Preview(name = "UI-018 Stock adjustment", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi018() = PreviewFrame { AdjustStockScreen(demoProducts().first(), {}) }
@Preview(name = "UI-019 New product", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi019() = PreviewFrame { NewProductScreen({}, { _, _, _ -> Result.success(Unit) }) }
@Preview(name = "UI-020 Stock intake", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi020() = PreviewFrame { StockEntryScreen({}, { _, _, _, _ -> Result.success(Unit) }) }
@Preview(name = "UI-021 Fiscal document", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi021() = PreviewFrame { FiscalFoundScreen(onNavigate = {}) }
@Preview(name = "UI-022 Fiscal review", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi022() = PreviewFrame { FiscalReviewScreen({}, false) }
@Preview(name = "UI-023 Purchase suggestions", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi023() = PreviewFrame {
    PurchaseSuggestionsScreen(demoProducts(), emptyList(), { _, _, _, _, _ -> Result.success(Unit) }, {})
}
@Preview(name = "UI-024 Supplier order", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi024() = PreviewFrame { SupplierOrderScreen(emptyList(), emptyList(), { Result.success(Unit) }, {}) }
@Preview(name = "UI-025 Suppliers", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi025() = PreviewFrame { SuppliersScreen(emptyList(), {}, { _, _ -> Result.success(Unit) }) }
@Preview(name = "UI-026 Orders", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi026() = PreviewFrame { OrdersScreen(emptyList(), {}) }
@Preview(name = "UI-027 Order detail", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi027() = PreviewFrame { OrderDetailScreen(null, {}) }
@Preview(name = "UI-028 Picking", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi028() = PreviewFrame { PickingScreen(null, {}) }
@Preview(name = "UI-029 Delivery", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi029() = PreviewFrame { DeliveryScreen(null, {}) }
@Preview(name = "UI-030 Insights", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi030() = PreviewFrame { InsightsScreen(demoProducts(), onNavigate = {}) }
@Preview(name = "UI-031 Daily summary", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi031() = PreviewFrame { DailySummaryScreen(84750, 27, 12000, {}) }
@Preview(name = "UI-032 TINO conversation", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi032() = PreviewFrame { AskTinoScreen {} }
@Preview(name = "UI-033 Sync", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi033() = PreviewFrame { SyncDetailsScreen(3, {}) }
@Preview(name = "UI-034 More", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi034() = PreviewFrame { MoreScreen {} }
@Preview(name = "UI-035 Settings", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi035() = PreviewFrame { SettingsScreen(null, {}, {}) }
@Preview(name = "UI-036 Offline", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi036() = PreviewFrame { OfflineScreen(3, {}) }
@Preview(name = "UI-037 Voice error", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi037() = PreviewFrame { VoiceErrorScreen {} }
@Preview(name = "UI-038 Ambiguity", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi038() = PreviewFrame { AmbiguityScreen {} }
@Preview(name = "UI-039 Notification", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi039() = PreviewFrame { NotificationScreen(demoProducts(), onNavigate = {}) }
