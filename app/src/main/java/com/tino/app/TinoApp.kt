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
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceMessage
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceOperation
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceComponent
import com.tino.app.interfaceadapter.a2ui.CoreTinoComponentCatalog
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
import com.tino.app.domain.language.EntityReference
import com.tino.app.domain.language.LanguageEntityType
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun TinoApp(
    viewModel: TinoViewModel = hiltViewModel(),
    voiceViewModel: VoiceViewModel = hiltViewModel(),
    contextualVoiceViewModel: ContextualVoiceViewModel = hiltViewModel(),
    agenticVoiceViewModel: AgenticVoiceViewModel = hiltViewModel(),
    agentSessionViewModel: TinoAgentSessionViewModel = hiltViewModel(),
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val customerTimeline by viewModel.customerTimeline.collectAsStateWithLifecycle()
    val todayTotal by viewModel.todayTotalCents.collectAsStateWithLifecycle()
    val todayReceived by viewModel.todayReceivedCents.collectAsStateWithLifecycle()
    val todayCash by viewModel.todayCashCents.collectAsStateWithLifecycle()
    val todayPix by viewModel.todayPixCents.collectAsStateWithLifecycle()
    val todayCard by viewModel.todayCardCents.collectAsStateWithLifecycle()
    val todaySales by viewModel.todaySalesCount.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val storeProfile by viewModel.storeProfile.collectAsStateWithLifecycle()
    val profileLoaded by viewModel.profileLoaded.collectAsStateWithLifecycle()
    val businessProfile by viewModel.businessProfile.collectAsStateWithLifecycle()
    val businessContext = businessProfile?.let { DefaultBusinessContextResolver().resolve(it) }
    val activeCapabilities = businessContext?.capabilities.orEmpty()
    var screen by remember { mutableStateOf(TinoScreen.Splash) }
    var splashAnimationFinished by remember { mutableStateOf(false) }
    var saleLines by remember { mutableStateOf<List<SaleLine>>(emptyList()) }
    var selectedCustomer by remember { mutableStateOf<CustomerBalance?>(null) }
    var selectedProduct by remember { mutableStateOf<ProductSummary?>(null) }
    var fiscalDocumentCaptured by remember { mutableStateOf(false) }
    var fiscalImportResult by remember { mutableStateOf<ProductImportResult?>(null) }
    var fiscalRectifiedPath by remember { mutableStateOf<String?>(null) }
    var fiscalUploadUri by remember { mutableStateOf<Uri?>(null) }
    var completion by remember { mutableStateOf(TinoCompletion()) }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val voiceState by voiceViewModel.state.collectAsStateWithLifecycle()
    val contextualVoiceState by contextualVoiceViewModel.state.collectAsStateWithLifecycle()
    val agenticVoiceState by agenticVoiceViewModel.state.collectAsStateWithLifecycle()
    val sharedAgentSnapshot by agentSessionViewModel.sharedState.snapshot.collectAsStateWithLifecycle()
    val agentPresence by agentSessionViewModel.presence.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingVoiceAction by remember { mutableStateOf<(() -> Unit)?>(null) }
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

    LaunchedEffect(profileLoaded, storeProfile, splashAnimationFinished) {
        if (!profileLoaded || !splashAnimationFinished) return@LaunchedEffect
        screen = if (storeProfile == null) TinoScreen.FirstAccess else TinoScreen.Home
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
    LaunchedEffect(screen, selectedCustomer?.id, selectedProduct?.id, activeCapabilities) {
        agentSessionViewModel.enterScreen(
            ScreenAgentContext(
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

    Surface(Modifier.fillMaxSize(), color = TinoPaper) {
        when (screen) {
            TinoScreen.Splash -> TinoSplashScreen(
                onFinished = { splashAnimationFinished = true },
            )
            TinoScreen.FirstAccess -> FirstAccessScreen(
                onContinue = { storeName, ownerName, phone, vertical, modules ->
                    viewModel.saveStoreProfile(storeName, ownerName, phone, vertical, modules)
                    screen = TinoScreen.Home
                },
                onRestore = { screen = TinoScreen.RestoreStore },
                contextualVoiceState = contextualVoiceState,
                onVoiceStart = {
                    requestVoiceAccess { contextualVoiceViewModel.listen(VoiceContext.ONBOARDING) }
                },
                onVoiceStop = contextualVoiceViewModel::stop,
            )
            TinoScreen.RestoreStore -> RestoreStoreScreen(
                onBack = { screen = TinoScreen.FirstAccess },
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
                customers = customers,
                customerTimeline = customerTimeline,
                todayTotalCents = todayTotal,
                todayReceivedCents = todayReceived,
                todayCashCents = todayCash,
                todayPixCents = todayPix,
                todayCardCents = todayCard,
                todaySales = todaySales,
                pendingSyncCount = pendingSyncCount,
                suppliers = suppliers,
                storeProfile = storeProfile,
                voiceState = voiceState,
                contextualVoiceState = contextualVoiceState,
                agenticVoiceState = agenticVoiceState,
                sharedAgentSnapshot = sharedAgentSnapshot,
                agentPresence = agentPresence,
                onVoiceStart = { requestVoiceAccess(voiceViewModel::start) },
                onVoiceClarificationRetry = { requestVoiceAccess(voiceViewModel::retryClarification) },
                onVoiceConfirmByVoice = { requestVoiceAccess(voiceViewModel::confirmByVoice) },
                onVoiceStop = voiceViewModel::stop,
                onVoiceSubmitText = voiceViewModel::submitText,
                onVoiceConfirm = voiceViewModel::confirm,
                onVoiceCancel = voiceViewModel::cancel,
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
                onAgenticCapabilitySubmit = { capability, label -> agenticVoiceViewModel.submitCapability(capability, label) },
                onAgenticCapabilityUseOnce = agenticVoiceViewModel::useCapabilityOnce,
                onAgenticCapabilityActivate = { capability ->
                    businessProfile?.let { profile ->
                        viewModel.updateBusinessProfile(
                            profile.copy(permanentCapabilities = profile.permanentCapabilities + capability),
                        )
                    }
                },
                businessProfile = businessProfile,
                activeCapabilities = activeCapabilities,
                onUpdateBusinessProfile = viewModel::updateBusinessProfile,
                onAddProduct = viewModel::addProduct,
                onSell = { product, quantity, paymentMethod -> viewModel.sell(product, quantity, paymentMethod) },
                onAddCustomer = viewModel::addCustomer,
                onUpdateCustomer = viewModel::updateCustomer,
                onCreditSale = viewModel::sellOnCredit,
                onReceivePayment = viewModel::receivePayment,
                onReceiveStock = viewModel::receiveStock,
                onAddSupplier = viewModel::addSupplier,
                onFiscalDocumentProcessed = { result, rectifiedPath ->
                    fiscalDocumentCaptured = true
                    fiscalImportResult = result
                    fiscalRectifiedPath = rectifiedPath
                },
                onFiscalImageSelected = { uri ->
                    fiscalUploadUri = uri
                    screen = TinoScreen.DocumentUpload
                },
            )
        }
    }
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TinoSpacing.md, vertical = TinoSpacing.sm),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = TinoShapes.large,
            colors = CardDefaults.cardColors(containerColor = TinoPaper),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
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
    val message = when (current) {
        is ContextualVoiceState.NeedsCorrection -> current.message
        is ContextualVoiceState.Unavailable -> current.message
        is ContextualVoiceState.Error -> current.message
        else -> ""
    }
    if (message.isNotBlank()) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth(),
            color = TinoRed,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
internal fun FirstAccessScreen(
    onContinue: (String, String, String, BusinessVertical, Set<BusinessModule>) -> Unit,
    onRestore: () -> Unit,
    contextualVoiceState: ContextualVoiceState = ContextualVoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
) {
    var store by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var submitAttempted by remember { mutableStateOf(false) }
    var configurationError by remember { mutableStateOf<String?>(null) }
    var vertical by remember { mutableStateOf(BusinessVertical.RETAIL) }
    var modules by remember { mutableStateOf(VerticalPresetCatalog.forVertical(vertical).defaultModules) }
    var customizeModules by remember { mutableStateOf(false) }
    val missingFields = buildList {
        if (store.isBlank()) add("nome do comércio")
        if (owner.isBlank()) add("seu nome")
        if (phone.isBlank()) add("celular")
    }

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
            value = phone,
            onValueChange = { phone = it },
            label = "Celular",
            placeholder = "(86) 9 1234-5678",
            labelAbove = true,
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
        TextButton(onClick = { customizeModules = !customizeModules }) {
            Text(if (customizeModules) "Ocultar recursos" else "Personalizar recursos")
        }
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
        Spacer(Modifier.height(TinoSpacing.lg))
        TinoPrimaryButton("CONTINUAR") {
            submitAttempted = true
            if (missingFields.isEmpty()) {
                runCatching {
                    BusinessProfile(
                        primaryVertical = vertical,
                        enabledModules = modules + BusinessModule.CORE,
                        operationalPatterns = OperationalPatternCatalog.forVertical(vertical),
                    )
                }.onSuccess {
                    onContinue(store, owner, phone, vertical, modules + BusinessModule.CORE)
                }.onFailure {
                    configurationError = profileConfigurationError(it)
                }
            }
        }
        if (submitAttempted && missingFields.isNotEmpty()) {
            Text(
                "Preencha: ${missingFields.joinToString(", ")}.",
                color = TinoRed,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        TextButton(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Já tenho um comércio", style = MaterialTheme.typography.labelMedium)
        }
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
internal fun RestoreStoreScreen(onBack: () -> Unit) {
    ScreenColumn {
        TinoTopBar("Recuperar meu comércio", onBack)
        TinoEmptyState(
            TinoIcons.Store,
            "Nenhum backup encontrado",
            "A recuperação ficará disponível quando houver um backup vinculado a este comércio.",
        )
        TinoSecondaryButton("VOLTAR") { onBack() }
    }
}

@Composable
internal fun VoiceStageDiagnostics(metrics: AgenticVoiceMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
internal fun VoiceScreen(
    onNavigate: (TinoScreen) -> Unit,
    state: VoiceUiState = VoiceUiState.Idle,
    onStart: () -> Unit = {},
    onClarificationRetry: () -> Unit = onStart,
    onConfirmByVoice: () -> Unit = onStart,
    onStop: () -> Unit = {},
    onSubmitText: (String) -> Unit = {},
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    var typing by remember { mutableStateOf(false) }
    var typedText by remember { mutableStateOf("") }
    ScreenColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TinoIconButton(TinoIcons.Close, "Fechar", { onNavigate(TinoScreen.Home) })
            Text("FALAR COM O TINO", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = TinoGreenDark, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(TinoSize.iconButton))
        }
        Spacer(Modifier.height(TinoSpacing.lg))
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(180.dp).background(TinoGreenTint, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(132.dp).background(TinoGreenLight, CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(88.dp).background(TinoGreen, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(TinoIcons.Voice, contentDescription = "Voz do TINO", tint = TinoSurface, modifier = Modifier.size(TinoSize.iconLarge))
                    }
                }
            }
        }
        if (state is VoiceUiState.Listening || state is VoiceUiState.Transcript) {
            Text("Estou ouvindo...", color = TinoGreenDark, style = MaterialTheme.typography.titleLarge)
            Text("Diga o que você precisa.", color = TinoMuted, style = MaterialTheme.typography.bodyLarge)
        }
        when (state) {
            VoiceUiState.Idle -> {
                TinoPrimaryButton("COMEÇAR A FALAR", onStart)
            }
            VoiceUiState.Cancelled -> {
                TinoEmptyState(TinoIcons.Success, "Operação cancelada", "Nada foi alterado.")
                TinoPrimaryButton("FALAR COM O TINO", onStart)
                TinoSecondaryButton("VOLTAR") { onNavigate(TinoScreen.Home) }
            }
            VoiceUiState.Listening -> {
                Text("Ouvindo...", style = MaterialTheme.typography.titleMedium)
                Text("Fale naturalmente. Nada será salvo sem revisão.", textAlign = TextAlign.Center, color = TinoMuted)
                Spacer(Modifier.height(TinoSpacing.xl))
                TinoSecondaryButton("CONCLUIR E ENTENDER", onStop)
            }
            is VoiceUiState.Transcript -> {
                TinoCard {
                    Text(if (state.committed) "Fala confirmada" else "Estou ouvindo", style = MaterialTheme.typography.titleMedium)
                    Text(state.text.ifBlank { "Aguardando fala..." }, color = TinoInk)
                }
                TinoSecondaryButton("CANCELAR") { onCancel(); onNavigate(TinoScreen.Home) }
            }
            is VoiceUiState.Understanding -> {
                TinoCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(TinoSize.iconNormal),
                            color = TinoGreen,
                            strokeWidth = 3.dp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text("ORGANIZANDO SUA FALA...", style = MaterialTheme.typography.titleMedium)
                            Text("Já recebi sua frase. Vou preparar a ação.", color = TinoMuted)
                        }
                    }
                    if (state.text.isNotBlank()) Text(state.text, color = TinoInk)
                }
                TinoSecondaryButton("CANCELAR", onCancel)
            }
            is VoiceUiState.Preview -> {
                TinoCard {
                    Text(state.preview.title, style = MaterialTheme.typography.titleMedium)
                    Text(state.preview.detail, color = TinoMuted)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                    TinoSecondaryButton("CANCELAR", onCancel, Modifier.weight(1f))
                    TinoPrimaryButton(state.preview.confirmLabel, onConfirm, Modifier.weight(1f))
                }
                TinoSecondaryButton("CONFIRMAR FALANDO", onConfirmByVoice)
            }
            is VoiceUiState.Answer -> {
                TinoCard {
                    Text(state.title, style = MaterialTheme.typography.titleMedium)
                    Text(state.message, color = TinoInk, style = MaterialTheme.typography.bodyLarge)
                    Text("Consulta feita nos dados deste aparelho.", color = TinoMuted)
                }
                TinoPrimaryButton("VOLTAR PARA O TINO") { onNavigate(TinoScreen.Home) }
            }
            is VoiceUiState.Clarification -> {
                TinoCard {
                    Text("PRECISO DE UM DETALHE", style = MaterialTheme.typography.titleMedium)
                    Text(state.message, color = TinoInk, style = MaterialTheme.typography.bodyLarge)
                    Text("A operação ainda não foi alterada. Diga o nome completo para continuar.", color = TinoMuted)
                }
                state.entityChoice?.let { choice ->
                    TinoA2UiRenderer(
                        message = choice,
                        onEntityChoiceSelected = onSubmitText,
                    )
                }
                TinoPrimaryButton("DIZER O NOME COMPLETO", onClarificationRetry)
                TinoSecondaryButton("FAZER MANUALMENTE") { onNavigate(TinoScreen.QuickSale) }
            }
            is VoiceUiState.ConfirmationNeeded -> {
                TinoCard {
                    Text("CONFIRME A OPERAÇÃO", style = MaterialTheme.typography.titleMedium)
                    Text(state.message, color = TinoInk, style = MaterialTheme.typography.bodyLarge)
                    Text("Nada foi alterado ainda.", color = TinoMuted)
                }
                TinoPrimaryButton("CONFIRMAR FALANDO", onConfirmByVoice)
                TinoSecondaryButton("CANCELAR", onCancel)
            }
            is VoiceUiState.Unavailable -> {
                TinoEmptyState(TinoIcons.Error, "Voz indisponível", state.message)
                TinoPrimaryButton("ABRIR VENDA MANUAL") { onNavigate(TinoScreen.QuickSale) }
                TinoSecondaryButton("VOLTAR") { onNavigate(TinoScreen.Home) }
            }
            is VoiceUiState.Error -> {
                TinoEmptyState(TinoIcons.Error, "Não foi possível entender", state.message)
                TinoPrimaryButton("TENTAR DE NOVO", onStart)
                TinoSecondaryButton("FAZER MANUALMENTE") { onNavigate(TinoScreen.QuickSale) }
            }
            is VoiceUiState.Completed -> {
                TinoEmptyState(TinoIcons.Success, "Comando concluído", state.message)
                TinoPrimaryButton("VOLTAR PARA O TINO") { onNavigate(TinoScreen.Home) }
            }
        }
        if (state == VoiceUiState.Idle || state == VoiceUiState.Listening || state is VoiceUiState.Transcript) {
            if (typing) {
                TinoTextField(
                    value = typedText,
                    onValueChange = { typedText = it },
                    label = "Digite o que você precisa",
                    placeholder = "Ex.: Quanto entrou hoje?",
                    labelAbove = true,
                )
                TinoPrimaryButton("ENVIAR", {
                    onSubmitText(typedText)
                    typing = false
                }, Modifier, enabled = typedText.isNotBlank())
                TinoSecondaryButton("VOLTAR PARA A VOZ") { typing = false }
            } else {
                TinoSecondaryButton("DIGITAR EM VEZ DE FALAR") { typing = true }
            }
        }
        if (state == VoiceUiState.Idle || state == VoiceUiState.Listening) {
            TinoCard {
                Text("Exemplos do que você pode dizer", color = TinoGreenDark, style = MaterialTheme.typography.titleMedium)
                Text("\"João levou 70 reais fiado\"", color = TinoMuted)
                Text("\"João pagou 50 no PIX\"", color = TinoMuted)
                Text("\"Quanto entrou hoje?\"", color = TinoMuted)
                Text("\"Chegaram 2 caixas de café\"", color = TinoMuted)
            }
        }
    }
}

@Composable
internal fun ProductsScreen(
    products: List<ProductSummary>,
    onNavigate: (TinoScreen) -> Unit,
    onSelectProduct: (ProductSummary) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todos") }
    val searched = products.filter { it.name.contains(query, ignoreCase = true) }
    val shown = when (filter) {
        "Estoque baixo" -> searched.filter { it.stockQuantity in 1..6 }
        "Sem estoque" -> searched.filter { it.stockQuantity == 0 }
        else -> searched
    }
    ScreenColumn {
        TinoTopBar("Estoque") { onNavigate(TinoScreen.Home) }
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
                products.count { it.stockQuantity in 0..6 }.toString(),
                "estoque baixo",
                Modifier.weight(1f),
                TinoOrange,
                TinoAmberContainer,
            )
        }
        TinoSearchField(query, { query = it }, "Procurar produto")
        TinoHorizontalCarousel {
            listOf("Todos", "Estoque baixo", "Sem estoque").forEach { option ->
                item { TinoFilterChip(option, filter == option) { filter = option } }
            }
        }
        TinoSectionLabel("Produtos")
        if (shown.isEmpty()) {
            TinoEmptyState(
                TinoIcons.Products,
                if (products.isEmpty()) "Nenhum produto cadastrado" else "Nenhum produto encontrado",
                if (products.isEmpty()) "Cadastre o primeiro produto para começar a vender." else "Tente outra busca ou cadastre um novo produto.",
                actionLabel = "CADASTRAR PRODUTO",
                onAction = { onNavigate(TinoScreen.NewProduct) },
            )
        } else {
            shown.forEach { product ->
                TinoProductRow(product) { onSelectProduct(product); onNavigate(TinoScreen.ProductDetail) }
            }
        }
        TinoSecondaryButton("ADICIONAR PRODUTO") { onNavigate(TinoScreen.NewProduct) }
    }
}

@Composable
internal fun ProductDetailScreen(product: ProductSummary?, onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        if (product == null) {
            TinoTopBar("Produto") { onNavigate(TinoScreen.Products) }
            TinoEmptyState(TinoIcons.Products, "Produto não selecionado", "Volte à lista e escolha um produto para ver os detalhes.")
            TinoSecondaryButton("VOLTAR A PRODUTOS") { onNavigate(TinoScreen.Products) }
        } else {
            TinoTopBar(product.name) { onNavigate(TinoScreen.Products) }
            TinoCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md)) {
                    Icon(TinoIcons.Products, contentDescription = null, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconLarge))
                    Column {
                        MetricLine("Venda", formatCents(product.priceCents.toLong()))
                    }
                }
            }
            Text("Estoque", style = MaterialTheme.typography.titleMedium)
            Text("${product.stockQuantity} unidades", style = MaterialTheme.typography.displaySmall)
            TinoCard {
                Text("Atualizar estoque", style = MaterialTheme.typography.titleMedium)
                Text("Registre uma entrada de mercadoria para adicionar unidades.", color = TinoMuted)
            }
            TinoSecondaryButton("AJUSTAR ESTOQUE") { onNavigate(TinoScreen.AdjustStock) }
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
                cart = cart + (product.id to requestedQuantity.coerceIn(1, product.stockQuantity.coerceAtLeast(1)))
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
            )
        } else {
            shown.take(4).forEach { product ->
                TinoSaleProductRow(
                    product = product,
                    onAdd = { cart = cart + (product.id to ((cart[product.id] ?: 0) + 1)) },
                    enabled = (cart[product.id] ?: 0) < product.stockQuantity,
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
                                if (line.quantity < line.product.stockQuantity) {
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
    onSell: (ProductSummary, Int, PaymentMethod) -> Unit,
    onComplete: (TinoCompletion) -> Unit,
) {
    val total = lines.sumOf { it.quantity * it.product.priceCents }
    ScreenColumn {
        TinoTopBar("Receber venda") { onNavigate(TinoScreen.QuickSale) }
        if (lines.isEmpty()) {
            TinoEmptyState(TinoIcons.Cart, "Nenhum item selecionado", "Volte à venda e escolha pelo menos um produto.")
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
            Text("Como recebeu?", style = MaterialTheme.typography.titleMedium)
            PaymentChoice(TinoIcons.Cash, "Dinheiro") { lines.forEach { onSell(it.product, it.quantity, PaymentMethod.CASH) }; onComplete(TinoCompletion("Venda registrada", "Pagamento em dinheiro salvo.")) }
            PaymentChoice(TinoIcons.Pix, "PIX") { lines.forEach { onSell(it.product, it.quantity, PaymentMethod.PIX) }; onComplete(TinoCompletion("Venda registrada", "Pagamento via PIX salvo.")) }
            PaymentChoice(TinoIcons.Card, "Maquininha") { lines.forEach { onSell(it.product, it.quantity, PaymentMethod.CARD) }; onComplete(TinoCompletion("Venda registrada", "Pagamento na maquininha salvo.")) }
            PaymentChoice(TinoIcons.Credit, "Fiado") { onNavigate(TinoScreen.SelectCustomer) }
        }
    }
}

@Composable
internal fun CustomersScreen(
    customers: List<CustomerBalance>,
    onNavigate: (TinoScreen) -> Unit,
    onSelectCustomer: (CustomerBalance) -> Unit,
    onAddCustomer: (String, String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todos") }
    var addVisible by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
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
            )
        } else {
            shown.forEach { customer ->
                TinoCustomerRow(customer) {
                    onSelectCustomer(customer)
                }
            }
        }
        if (addVisible) {
            TinoTextField(name, { name = it }, "Nome", "Nome do cliente", labelAbove = true)
            TinoTextField(phone, { phone = it }, "Celular", "Opcional", labelAbove = true)
            TinoPrimaryButton("SALVAR CLIENTE") {
                if (name.isNotBlank()) onAddCustomer(name, phone.ifBlank { null })
                name = ""
                phone = ""
                addVisible = false
            }
        } else {
            TinoSecondaryButton("ADICIONAR CLIENTE") { addVisible = true }
        }
    }
}

@Composable
internal fun CustomerDetailScreen(
    customer: CustomerBalance?,
    onNavigate: (TinoScreen) -> Unit,
    onUpdateCustomer: (CustomerBalance, String, String?) -> Unit,
) {
    var name by remember(customer?.id) { mutableStateOf(customer?.name.orEmpty()) }
    var phone by remember(customer?.id) { mutableStateOf(customer?.phone.orEmpty()) }
    if (customer == null) {
        ScreenColumn {
            TinoTopBar("Cliente") { onNavigate(TinoScreen.Customers) }
            TinoEmptyState(TinoIcons.People, "Cliente não selecionado", "Volte à lista e escolha um cliente para ver os detalhes.")
            TinoSecondaryButton("VOLTAR A CLIENTES") { onNavigate(TinoScreen.Customers) }
        }
        return
    }
    ScreenColumn(verticalSpacing = TinoSpacing.sm) {
        TinoTopBar(customer.name) { onNavigate(TinoScreen.Customers) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = TinoShapes.large,
            colors = CardDefaults.cardColors(containerColor = TinoGreenTint),
            elevation = CardDefaults.cardElevation(defaultElevation = TinoSize.cardElevation),
        ) {
            Row(Modifier.fillMaxWidth().padding(TinoSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md)) {
                Box(Modifier.size(52.dp).background(TinoGreenLight, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(TinoIcons.Person, contentDescription = "Cliente", tint = TinoGreen, modifier = Modifier.size(TinoSize.iconLarge))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                    Text(customer.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (customer.balanceCents > 0) "Em aberto na caderneta" else "Sem saldo em aberto", color = TinoMuted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatCents(customer.balanceCents),
                        color = if (customer.balanceCents > 0) TinoRed else TinoGreen,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            TinoSecondaryButton("ABRIR CADERNETA") { onNavigate(TinoScreen.CustomerAccount) }
        }
        Text("Dados do cliente", style = MaterialTheme.typography.titleMedium)
        TinoTextField(name, { name = it }, "Nome", "Nome do cliente", labelAbove = true)
        TinoTextField(phone, { phone = it }, "Celular", "Opcional", labelAbove = true)
        TinoPrimaryButton("SALVAR ALTERAÇÕES") {
            onUpdateCustomer(customer, name, phone.ifBlank { null })
        }
    }
}

@Composable
internal fun CreditListScreen(
    customers: List<CustomerBalance>,
    onNavigate: (TinoScreen) -> Unit,
    onAddCustomer: (String, String?) -> Unit,
    onSelectCustomer: (CustomerBalance) -> Unit,
    contextualVoiceState: ContextualVoiceState = ContextualVoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todos") }
    var addCustomerVisible by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = TinoShapes.large,
            colors = CardDefaults.cardColors(containerColor = TinoSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = TinoSize.cardElevation),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(TinoSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                    Text("Total a receber", color = TinoMuted, style = MaterialTheme.typography.bodySmall)
                    Text(formatCents(customers.sumOf { it.balanceCents }), color = TinoGreen, style = MaterialTheme.typography.headlineSmall, maxLines = 1, softWrap = false)
                    Text("${customers.count { it.balanceCents > 0 }} clientes com valor em aberto", color = TinoMuted, style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.size(44.dp).background(TinoGreenLight, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(TinoIcons.Credit, contentDescription = "Total a receber", tint = TinoGreen, modifier = Modifier.size(TinoSize.iconLarge))
                }
            }
        }
        TinoSearchField(query, { query = it }, "Procurar pessoa")
        TinoHorizontalCarousel {
            listOf("Todos", "Em aberto", "Sem saldo").forEach { option ->
                item { TinoFilterChip(option, filter == option) { filter = option } }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Clientes", style = MaterialTheme.typography.titleMedium)
            Text("Ordenar: Maior valor", color = TinoGreen, style = MaterialTheme.typography.labelMedium)
        }
        if (shown.isEmpty()) {
            TinoEmptyState(TinoIcons.People, if (customers.isEmpty()) "Nenhum cliente cadastrado" else "Nenhum cliente encontrado", if (customers.isEmpty()) "Cadastre o primeiro cliente para começar a caderneta." else "Tente outra busca ou filtro.")
        } else {
            shown.sortedByDescending { it.balanceCents }.forEach { customer -> TinoCustomerRow(customer) { onSelectCustomer(customer); onNavigate(TinoScreen.CustomerAccount) } }
        }
        if (addCustomerVisible) {
            TinoTextField(name, { name = it }, "Nome", "Nome do cliente", labelAbove = true)
            TinoTextField(phone, { phone = it }, "Celular", "Opcional", labelAbove = true)
            ContextualVoicePanel(
                context = VoiceContext.CUSTOMER_CREATE,
                state = contextualVoiceState,
                hint = "Diga o nome e o celular do cliente",
                onStart = onVoiceStart,
                onStop = onVoiceStop,
            )
            TinoPrimaryButton("SALVAR CLIENTE") {
                if (name.isNotBlank()) onAddCustomer(name, phone.ifBlank { null })
                name = ""
                phone = ""
                addCustomerVisible = false
            }
        } else {
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
            TinoEmptyState(TinoIcons.People, "Nenhum cliente encontrado", "Volte e cadastre a pessoa antes de anotar o fiado.")
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
    onCreditSale: (ProductSummary, String, Int) -> Unit,
    onComplete: (TinoCompletion) -> Unit,
) {
    ScreenColumn {
        if (customer == null || lines.isEmpty()) {
            TinoTopBar("Confirmar fiado") { onNavigate(TinoScreen.SelectCustomer) }
            TinoEmptyState(TinoIcons.Credit, "Compra incompleta", "Escolha um cliente e mantenha ao menos um produto na venda.")
            TinoSecondaryButton("VOLTAR À VENDA") { onNavigate(TinoScreen.QuickSale) }
            return@ScreenColumn
        }
        val name = customer.name
        val current = customer.balanceCents
        val sale = lines.sumOf { it.quantity * it.product.priceCents }
        TinoTopBar(name) { onNavigate(TinoScreen.SelectCustomer) }
        TinoCard {
            MetricLine("Esta compra", formatCents(sale.toLong()))
            MetricLine("Já devia", formatCents(current.toLong()))
            HorizontalDivider()
            MetricLine("Ficará devendo", formatCents((current + sale).toLong()), true, TinoRed)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
            TinoSecondaryButton("CANCELAR", { onNavigate(TinoScreen.Home) }, Modifier.weight(1f))
            TinoPrimaryButton("ANOTAR", { lines.forEach { onCreditSale(it.product, name, it.quantity) }; onComplete(TinoCompletion("Fiado anotado", "A compra foi vinculada a $name.")) }, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun CustomerAccountScreen(
    customer: CustomerBalance?,
    timeline: CustomerCreditTimeline?,
    onNavigate: (TinoScreen) -> Unit,
) {
    ScreenColumn {
        if (customer == null) {
            TinoTopBar("Conta do cliente") { onNavigate(TinoScreen.CreditList) }
            TinoEmptyState(TinoIcons.People, "Cliente não selecionado", "Volte à lista e escolha um cliente para consultar a conta.")
            TinoSecondaryButton("VOLTAR AO FIADO") { onNavigate(TinoScreen.CreditList) }
            return@ScreenColumn
        }
        TinoTopBar(customer.name) { onNavigate(TinoScreen.CreditList) }
        Text("Está devendo", color = TinoMuted)
        Text(formatCents(customer.balanceCents.toLong()), color = TinoRed, style = MaterialTheme.typography.displaySmall)
        timeline?.let { account ->
            val statusText = when {
                account.overdueCents > 0 -> "Atrasado"
                account.openCents > 0 -> "Em aberto"
                else -> "Quitado"
            }
            TinoStatusBadge(statusText, if (account.overdueCents > 0) TinoStatus.Error else TinoStatus.Normal)
            TinoSectionHeader("Linha do tempo")
            val events = buildList {
                account.entries.forEach { entry ->
                    add(
                        TimelineUiItem(
                            occurredAt = entry.occurredAt,
                            label = "Fiado",
                            amount = "+${formatCents(entry.amountCents)}",
                        ),
                    )
                }
                account.payments.forEach { payment ->
                    add(
                        TimelineUiItem(
                            occurredAt = payment.occurredAt,
                            label = "Pagou ${paymentMethodLabel(payment.paymentMethod)}",
                            amount = "-${formatCents(payment.amountCents)}",
                        ),
                    )
                }
            }.sortedByDescending { it.occurredAt }
            if (events.isEmpty()) {
                Text("Nenhum lançamento nesta conta ainda.", color = TinoMuted)
            } else {
                TinoCard {
                    events.forEachIndexed { index, event ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTimelineDate(event.occurredAt), modifier = Modifier.width(52.dp), color = TinoMuted, style = MaterialTheme.typography.labelSmall)
                            Text(event.label, modifier = Modifier.weight(1f), color = TinoInk)
                            Text(event.amount, color = if (event.amount.startsWith("+")) TinoRed else TinoGreen, fontWeight = FontWeight.Bold)
                        }
                        if (index < events.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
        TinoPrimaryButton("RECEBER PAGAMENTO", { onNavigate(TinoScreen.ReceivePayment) }, Modifier, enabled = customer.balanceCents > 0)
    }
}

private data class TimelineUiItem(val occurredAt: Long, val label: String, val amount: String)

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
    onReceivePayment: (CustomerBalance, String) -> Unit,
    onComplete: (TinoCompletion) -> Unit,
) {
    ScreenColumn {
        if (customer == null) {
            TinoTopBar("Receber pagamento") { onNavigate(TinoScreen.CreditList) }
            TinoEmptyState(TinoIcons.Credit, "Cliente não selecionado", "Volte ao fiado e escolha um cliente antes de receber.")
            TinoSecondaryButton("VOLTAR AO FIADO") { onNavigate(TinoScreen.CreditList) }
            return@ScreenColumn
        }
        var amount by remember { mutableStateOf("") }
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
        TinoPrimaryButton("RECEBER PAGAMENTO", { onReceivePayment(customer, amount); onComplete(TinoCompletion("Pagamento registrado", "O saldo de ${customer.name} foi atualizado.")) }, Modifier, enabled = canConfirm)
    }
}

@Composable
internal fun NewProductScreen(
    onNavigate: (TinoScreen) -> Unit,
    onAddProduct: (String, String, String) -> Unit,
    contextualVoiceState: ContextualVoiceState = ContextualVoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
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
        TinoTopBar("Novo produto") { onNavigate(TinoScreen.Products) }
        TinoTextField(name, { name = it }, "Nome", "Ex.: Café 250g")
        TinoMoneyField(price, { price = it }, "Preço de venda")
        TinoTextField(stock, { stock = it }, "Estoque inicial", "Ex.: 12")
        if (price.isNotBlank() && priceCents <= 0) {
            Text("Informe um preço maior que zero.", color = TinoRed)
        }
        if (stock.isNotBlank() && (stockQuantity == null || stockQuantity < 0)) {
            Text("Informe um estoque igual ou maior que zero.", color = TinoRed)
        }
        ContextualVoicePanel(
            context = VoiceContext.PRODUCT_CREATE,
            state = contextualVoiceState,
            hint = "Diga o nome do produto e o preço de venda",
            onStart = onVoiceStart,
            onStop = onVoiceStop,
        )
        TinoPrimaryButton("CADASTRAR PRODUTO", { onAddProduct(name, price, stock); onNavigate(TinoScreen.Products) }, Modifier, enabled = canCreate)
    }
}

@Composable
internal fun AdjustStockScreen(product: ProductSummary?, onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        if (product == null) {
            TinoTopBar("Ajustar estoque") { onNavigate(TinoScreen.Products) }
            TinoEmptyState(TinoIcons.Products, "Produto não selecionado", "Volte à lista e escolha um produto para ajustar o estoque.")
            TinoSecondaryButton("VOLTAR A PRODUTOS") { onNavigate(TinoScreen.Products) }
        } else {
            TinoTopBar("Ajustar ${product.name}") { onNavigate(TinoScreen.ProductDetail) }
            TinoEmptyState(
                TinoIcons.Products,
                "Ajuste manual indisponível",
                "Para atualizar o estoque agora, registre uma entrada de mercadoria.",
            )
            TinoPrimaryButton("REGISTRAR ENTRADA") { onNavigate(TinoScreen.StockEntry) }
        }
    }
}

@Composable
internal fun StockEntryScreen(
    onNavigate: (TinoScreen) -> Unit,
    onReceiveStock: (String, String, String, String) -> Unit,
    contextualVoiceState: ContextualVoiceState = ContextualVoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
) {
    var productName by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    val canSave = productName.isNotBlank() && (quantity.toIntOrNull() ?: 0) > 0 && parseCentsForUi(cost) > 0
    LaunchedEffect(contextualVoiceState) {
        val fields = contextualVoiceState.fieldsFor(VoiceContext.STOCK_RECEIPT)
        fields["product"]?.takeIf { it.isNotBlank() }?.let { productName = it }
        fields["quantity"]?.takeIf { it.isNotBlank() }?.let { quantity = it }
        fields["unit_cost"]?.takeIf { it.isNotBlank() }?.let { cost = it }
        fields["supplier"]?.takeIf { it.isNotBlank() }?.let { supplier = it }
    }
    ScreenColumn {
        TinoTopBar("Entrada de mercadoria") { onNavigate(TinoScreen.Products) }
        Text("O que chegou?", style = MaterialTheme.typography.titleLarge)
        Text("Registre a entrada para atualizar o estoque local.", color = TinoMuted)
        TinoTextField(productName, { productName = it }, "Produto", "Nome exato do produto")
        TinoTextField(quantity, { quantity = it }, "Quantidade", "Ex.: 24")
        TinoMoneyField(cost, { cost = it }, "Custo unitário")
        TinoTextField(supplier, { supplier = it }, "Fornecedor (opcional)", "Nome do fornecedor")
        ContextualVoicePanel(
            context = VoiceContext.STOCK_RECEIPT,
            state = contextualVoiceState,
            hint = "Diga o produto, a quantidade e o custo unitário",
            onStart = onVoiceStart,
            onStop = onVoiceStop,
        )
        TinoPrimaryButton(
            "REGISTRAR ENTRADA",
            { onReceiveStock(productName, quantity, cost, supplier); onNavigate(TinoScreen.Products) },
            Modifier,
            enabled = canSave,
        )
    }
}

@Composable
internal fun FiscalFoundScreen(
    onNavigate: (TinoScreen) -> Unit,
    onImageSelected: (Uri) -> Unit = {},
) {
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let(onImageSelected) }
    ScreenColumn {
        TinoTopBar("Ler nota") { onNavigate(TinoScreen.StockEntry) }
        TinoCard {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md),
            ) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape,
                    color = TinoGreenLight,
                ) {
                    Icon(
                        TinoIcons.Camera,
                        contentDescription = null,
                        tint = TinoGreenDark,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Escanear nota", style = MaterialTheme.typography.titleMedium)
                    Text("Enquadre a nota inteira. O TINO ajuda a conferir os produtos.", color = TinoMuted)
                }
            }
            Spacer(Modifier.height(TinoSpacing.md))
            TinoPrimaryButton("ESCANEAR NOTA") { onNavigate(TinoScreen.DocumentCamera) }
            Spacer(Modifier.height(TinoSpacing.sm))
            TinoSecondaryButton("ESCOLHER UMA FOTO") {
                imagePicker.launch("image/*")
            }
            Text(
                "Você também pode escolher uma foto já salva no celular.",
                color = TinoMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TinoEmptyState(
            TinoIcons.Document,
            "Comece pela nota de entrada",
            "O TINO vai encontrar os produtos. Nada entra no estoque sem sua confirmação.",
        )
        TinoSecondaryButton("PREENCHER MANUALMENTE") { onNavigate(TinoScreen.StockEntry) }
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
                    Icon(TinoIcons.Success, contentDescription = null, tint = TinoGreen, modifier = Modifier.size(32.dp))
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
                    )
                }
                null -> TinoEmptyState(
                    TinoIcons.Document,
                    "Nenhum produto encontrado",
                    "A foto não trouxe linhas de produto legíveis.",
                )
            }
        } else {
            TinoEmptyState(TinoIcons.Document, "Nenhuma nota para conferir", "A conferência aparecerá aqui depois que uma nota for importada.")
        }
        TinoPrimaryButton("ESCANEAR OUTRA NOTA") { onNavigate(TinoScreen.DocumentCamera) }
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
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
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
internal fun PurchaseSuggestionsScreen(onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        TinoTopBar("Sugestões de compra") { onNavigate(TinoScreen.More) }
        TinoEmptyState(TinoIcons.Cart, "Nenhuma sugestão ainda", "Quando um produto estiver acabando, o TINO vai mostrar uma sugestão aqui.")
        TinoSecondaryButton("VER PRODUTOS") { onNavigate(TinoScreen.Products) }
    }
}

@Composable
internal fun SupplierOrderScreen(onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        TinoTopBar("Pedido") { onNavigate(TinoScreen.PurchaseSuggestions) }
        TinoEmptyState(TinoIcons.Supplier, "Nenhum pedido preparado", "Monte uma sugestão de compra antes de enviar um pedido.")
        TinoSecondaryButton("VER SUGESTÕES") { onNavigate(TinoScreen.PurchaseSuggestions) }
    }
}

@Composable
internal fun SuppliersScreen(
    suppliers: List<SupplierEntity>,
    onNavigate: (TinoScreen) -> Unit,
    onAddSupplier: (String, String?) -> Unit,
    contextualVoiceState: ContextualVoiceState = ContextualVoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    LaunchedEffect(contextualVoiceState) {
        val fields = contextualVoiceState.fieldsFor(VoiceContext.SUPPLIER_CREATE)
        fields["name"]?.takeIf { it.isNotBlank() }?.let { name = it }
        fields["phone"]?.takeIf { it.isNotBlank() }?.let { phone = it }
    }
    ScreenColumn {
        TinoTopBar("Fornecedores") { onNavigate(TinoScreen.More) }
        if (suppliers.isEmpty()) {
            TinoEmptyState(TinoIcons.Supplier, "Nenhum fornecedor cadastrado", "Cadastre quem abastece seu comércio para usar nas entradas.")
        } else {
            suppliers.forEach { supplier -> TinoSupplierRow(supplier.name, "Fornecedor cadastrado") }
        }
        TinoTextField(name, { name = it }, "Novo fornecedor", "Nome")
        TinoTextField(phone, { phone = it }, "Celular (opcional)", "Ex.: (86) 9 4209-3500")
        ContextualVoicePanel(
            context = VoiceContext.SUPPLIER_CREATE,
            state = contextualVoiceState,
            hint = "Diga o nome e o celular do fornecedor",
            onStart = onVoiceStart,
            onStop = onVoiceStop,
        )
        TinoSecondaryButton("ADICIONAR FORNECEDOR") {
            if (name.isNotBlank()) onAddSupplier(name, phone.ifBlank { null })
            name = ""
            phone = ""
        }
    }
}

@Composable
internal fun OrdersScreen(onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        TinoTopBar("Pedidos") { onNavigate(TinoScreen.More) }
        TinoEmptyState(TinoIcons.Orders, "Nenhum pedido recebido", "Quando chegar um pedido, ele aparecerá aqui com o próximo passo.")
        TinoSecondaryButton("VOLTAR PARA MAIS") { onNavigate(TinoScreen.More) }
    }
}

@Composable
internal fun OrderDetailScreen(onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        TinoTopBar("Detalhe do pedido") { onNavigate(TinoScreen.Orders) }
        TinoEmptyState(TinoIcons.Orders, "Pedido não selecionado", "Volte à lista para escolher um pedido antes de separar.")
        TinoSecondaryButton("VOLTAR A PEDIDOS") { onNavigate(TinoScreen.Orders) }
    }
}

@Composable
internal fun PickingScreen(onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        TinoTopBar("Separar pedido") { onNavigate(TinoScreen.OrderDetail) }
        TinoEmptyState(TinoIcons.Orders, "Nenhum pedido em separação", "Escolha um pedido para acompanhar os itens separados.")
        TinoSecondaryButton("VOLTAR AO PEDIDO") { onNavigate(TinoScreen.OrderDetail) }
    }
}

@Composable
internal fun DeliveryScreen(onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        TinoTopBar("Entrega") { onNavigate(TinoScreen.Orders) }
        TinoEmptyState(TinoIcons.Location, "Nenhuma entrega pronta", "Os detalhes de endereço e pagamento aparecerão quando houver um pedido pronto.")
        TinoSecondaryButton("VOLTAR A PEDIDOS") { onNavigate(TinoScreen.Orders) }
    }
}

@Composable
internal fun InsightsScreen(products: List<ProductSummary>, onNavigate: (TinoScreen) -> Unit) {
    val attention = products.filter { it.stockQuantity in 0..6 }
    ScreenColumn {
        TinoTopBar("O TINO percebeu") { onNavigate(TinoScreen.Home) }
        if (attention.isEmpty()) {
            TinoEmptyState(TinoIcons.Trends, "Nenhum alerta por enquanto", "O TINO vai avisar quando houver algo importante no seu estoque.")
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
            TinoEmptyState(TinoIcons.Calendar, "Nenhuma venda hoje", "O resumo será preenchido conforme você registrar as vendas.")
        }
        TinoSecondaryButton("VOLTAR PARA MAIS") { onNavigate(TinoScreen.More) }
    }
}

@Composable
internal fun AskTinoScreen(onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        TinoTopBar("Falar com o TINO") { onNavigate(TinoScreen.Home) }
        TinoEmptyState(TinoIcons.Conversation, "Faça uma pergunta", "Use sua voz para consultar vendas, estoque ou fiado.")
        TinoPrimaryButton("COMEÇAR A FALAR") { onNavigate(TinoScreen.Voice) }
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
            TinoEmptyState(TinoIcons.Synced, "Tudo em dia neste aparelho", "Não há alterações locais aguardando sincronização.")
        }
        TinoSecondaryButton("VOLTAR ÀS CONFIGURAÇÕES") { onNavigate(TinoScreen.Settings) }
    }
}

@Composable
internal fun MoreScreen(onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn(verticalSpacing = TinoSpacing.sm) {
        TinoTopBar("Mais") { onNavigate(TinoScreen.Home) }
        TinoSectionLabel("Operação")
        TinoMenuCard(TinoIcons.Orders, "Pedidos", "Ver e gerenciar pedidos", { onNavigate(TinoScreen.Orders) })
        TinoMenuCard(TinoIcons.Supplier, "Fornecedores", "Lista de fornecedores", { onNavigate(TinoScreen.Suppliers) })
        TinoMenuCard(TinoIcons.Cart, "Comprar", "Fazer novas compras", { onNavigate(TinoScreen.PurchaseSuggestions) })
        Spacer(Modifier.height(TinoSpacing.sm))
        TinoSectionLabel("Meu comércio")
        TinoMenuCard(TinoIcons.Calendar, "Resumo", "Visão geral do seu comércio", { onNavigate(TinoScreen.DailySummary) })
        TinoMenuCard(TinoIcons.Document, "Notas", "Anotações e lembretes", { onNavigate(TinoScreen.FiscalFound) })
        TinoMenuCard(TinoIcons.People, "Clientes", "Gerenciar seus clientes", { onNavigate(TinoScreen.Customers) })
        Spacer(Modifier.height(TinoSpacing.sm))
        TinoSectionLabel("TINO")
        TinoMenuCard(TinoIcons.Settings, "Configurações", "Ajustes do aplicativo", { onNavigate(TinoScreen.Settings) }, highlighted = true)
        TinoMenuCard(TinoIcons.Offline, "Modo offline", "Trabalhar sem internet", { onNavigate(TinoScreen.Offline) })
        if (BuildConfig.DEBUG) {
            TinoMenuCard(TinoIcons.Document, "A2UI Actions", "Validar Agent Loop no device", { onNavigate(TinoScreen.A2uiValidation) })
            TinoMenuCard(TinoIcons.Settings, "G3.11 Mutation Safety", "Confirmar, cancelar, replay e restart", { onNavigate(TinoScreen.G311MutationSafety) })
            TinoMenuCard(TinoIcons.Settings, "G3.12 Memória", "Working, sessão, TTL e restart", { onNavigate(TinoScreen.G312Memory) })
            TinoMenuCard(TinoIcons.Settings, "G4 Agent Loop", "Observe, replanejar, clarificar e proteger loop", { onNavigate(TinoScreen.G4AgentLoop) })
            TinoMenuCard(TinoIcons.Settings, "G5 Business Memory", "Correção, promoção, demote, remove e restart", { onNavigate(TinoScreen.G5BusinessMemory) })
        }
    }
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
        Text("Dados e segurança", style = MaterialTheme.typography.titleMedium)
        TinoMenuRow(TinoIcons.Synced, "Backup e sincronização") { onNavigate(TinoScreen.SyncDetails) }
        TinoMenuRow(TinoIcons.Offline, "Trabalhar sem internet") { onNavigate(TinoScreen.Offline) }
        Text("Recursos do aparelho", style = MaterialTheme.typography.titleMedium)
        TinoCard {
            Text("Som, voz, impressora e acessibilidade", fontWeight = FontWeight.SemiBold)
            Text("Essas opções serão configuradas quando os recursos estiverem disponíveis neste aparelho.", color = TinoMuted)
        }
        Text("Notas fiscais", style = MaterialTheme.typography.titleMedium)
        TinoMenuRow(TinoIcons.Document, "Importar nota fiscal") { onNavigate(TinoScreen.FiscalFound) }
        TinoSecondaryButton("VOLTAR PARA MAIS") { onNavigate(TinoScreen.More) }
    }
}

@Composable
internal fun BusinessProfileSettingsScreen(
    profile: BusinessProfile?,
    onSave: (BusinessProfile) -> Unit,
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
            TextButton(onClick = { customize = true }) { Text("PERSONALIZAR RECURSOS") }
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
        TinoPrimaryButton("SALVAR CONFIGURAÇÃO") {
            configurationError = null
            runCatching {
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
            }.onSuccess {
                onSave(it)
                onNavigate(TinoScreen.Settings)
            }.onFailure {
                configurationError = profileConfigurationError(it)
            }
        }
        TinoSecondaryButton("CANCELAR") { onNavigate(TinoScreen.Settings) }
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
        TinoPrimaryButton("VOLTAR PARA O TINO") { onNavigate(TinoScreen.Home) }
    }
}

@Composable
internal fun VoiceErrorScreen(onNavigate: (TinoScreen) -> Unit) = ChoiceFlowScreen("Não entendi direito", TinoIcons.Error, "Não consegui transformar o que ouvi em uma operação segura.", "FALAR DE NOVO", "FAZER MANUALMENTE", onNavigate, TinoScreen.QuickSale, TinoScreen.Voice)

@Composable
internal fun AmbiguityScreen(onNavigate: (TinoScreen) -> Unit) {
    ScreenColumn {
        TinoTopBar("Escolher produto") { onNavigate(TinoScreen.Voice) }
        TinoCard {
            Icon(TinoIcons.Search, contentDescription = null, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconLarge))
            Text("Encontrei mais de um produto parecido.", style = MaterialTheme.typography.titleMedium)
            Text("Fale o nome completo ou escolha manualmente na venda.", color = TinoMuted)
        }
        TinoSecondaryButton("FALAR NOVAMENTE") { onNavigate(TinoScreen.Voice) }
        TinoPrimaryButton("ABRIR VENDA MANUAL") { onNavigate(TinoScreen.QuickSale) }
    }
}

@Composable
internal fun NotificationScreen(products: List<ProductSummary>, onNavigate: (TinoScreen) -> Unit) {
    val attention = products.filter { it.stockQuantity in 0..6 }
    ScreenColumn {
        TinoTopBar("Avisos do TINO") { onNavigate(TinoScreen.Home) }
        if (attention.isEmpty()) {
            TinoEmptyState(TinoIcons.Success, "Nenhum aviso novo", "Quando houver algo importante, o TINO avisará por aqui.")
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
        TinoSecondaryButton("FALAR NOVAMENTE") { onNavigate(TinoScreen.Voice) }
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
internal fun PaymentChoice(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(TinoSize.buttonHeight).clickable(onClick = onClick),
        shape = com.tino.app.ui.theme.TinoShapes.small,
        colors = CardDefaults.cardColors(containerColor = TinoGreenTint),
        border = androidx.compose.foundation.BorderStroke(1.dp, TinoGreenBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoSize.cardElevation),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = TinoSpacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md)) {
            Icon(icon, contentDescription = label, tint = TinoGreen)
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun ScreenColumn(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalSpacing: Dp = TinoSpacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TinoSpacing.screen, vertical = TinoSpacing.lg),
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
@Composable private fun PreviewUi002() = PreviewFrame { FirstAccessScreen({ _, _, _, _, _ -> }, {}) }
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
@Composable private fun PreviewUi005() = PreviewFrame { VoiceScreen(onNavigate = {}) }
@Preview(name = "UI-006 Interpretation", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi006() = PreviewFrame { UnderstoodScreen {} }
@Preview(name = "UI-007 Correction", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi007() = PreviewFrame { CorrectionScreen {} }
@Preview(name = "UI-008 Success", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi008() = PreviewFrame { CompletedScreen(onNavigate = {}) }
@Preview(name = "UI-009 New sale", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi009() = PreviewFrame { QuickSaleScreen(demoProducts(), {}, onContinue = {}) }
@Preview(name = "UI-010 Payment", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi010() = PreviewFrame { ReceiveSaleScreen(listOf(SaleLine(demoProducts().first(), 1)), {}, { _, _, _ -> }, {}) }
@Preview(name = "UI-011 Credit customer", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi011() = PreviewFrame { SelectCustomerScreen(demoCustomers(), {}, {}) }
@Preview(name = "UI-012 Credit confirmation", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi012() = PreviewFrame { ConfirmCreditScreen(demoCustomers().first(), listOf(SaleLine(demoProducts().first(), 1)), {}, { _, _, _ -> }, {}) }
@Preview(name = "UI-013 Credit list", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi013() = PreviewFrame { CreditListScreen(demoCustomers(), {}, { _, _ -> }, {}) }
@Preview(name = "UI-014 Customer account", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi014() = PreviewFrame { CustomerAccountScreen(demoCustomers().first(), null, {}) }
@Preview(name = "UI-015 Credit payment", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi015() = PreviewFrame { ReceivePaymentScreen(demoCustomers().first(), {}, { _, _ -> }, {}) }
@Preview(name = "UI-016 Product list", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi016() = PreviewFrame { ProductsScreen(demoProducts(), {}, {}) }
@Preview(name = "UI-017 Product detail", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi017() = PreviewFrame { ProductDetailScreen(demoProducts().first(), {}) }
@Preview(name = "UI-018 Stock adjustment", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi018() = PreviewFrame { AdjustStockScreen(demoProducts().first(), {}) }
@Preview(name = "UI-019 New product", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi019() = PreviewFrame { NewProductScreen({}, { _, _, _ -> }) }
@Preview(name = "UI-020 Stock intake", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi020() = PreviewFrame { StockEntryScreen({}, { _, _, _, _ -> }) }
@Preview(name = "UI-021 Fiscal document", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi021() = PreviewFrame { FiscalFoundScreen(onNavigate = {}) }
@Preview(name = "UI-022 Fiscal review", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi022() = PreviewFrame { FiscalReviewScreen({}, false) }
@Preview(name = "UI-023 Purchase suggestions", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi023() = PreviewFrame { PurchaseSuggestionsScreen {} }
@Preview(name = "UI-024 Supplier order", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi024() = PreviewFrame { SupplierOrderScreen {} }
@Preview(name = "UI-025 Suppliers", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi025() = PreviewFrame { SuppliersScreen(emptyList(), {}, { _, _ -> }) }
@Preview(name = "UI-026 Orders", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi026() = PreviewFrame { OrdersScreen {} }
@Preview(name = "UI-027 Order detail", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi027() = PreviewFrame { OrderDetailScreen {} }
@Preview(name = "UI-028 Picking", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi028() = PreviewFrame { PickingScreen {} }
@Preview(name = "UI-029 Delivery", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi029() = PreviewFrame { DeliveryScreen {} }
@Preview(name = "UI-030 Insights", showBackground = true, widthDp = 412, heightDp = 915)
@Composable private fun PreviewUi030() = PreviewFrame { InsightsScreen(demoProducts(), {}) }
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
@Composable private fun PreviewUi039() = PreviewFrame { NotificationScreen(demoProducts(), {}) }
