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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.tino.app.ui.a2ui.TinoA2UiSurfaceHost
import com.tino.app.interfaceadapter.a2ui.A2uiSemanticMapper
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
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoOrange
import com.tino.app.ui.theme.TinoPaper
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
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.profile.CapabilityRecoveryPolicy
import com.tino.app.domain.profile.BusinessProfile
import com.tino.app.domain.profile.BusinessVertical
import com.tino.app.domain.profile.HomeActionId
import com.tino.app.domain.profile.HomeConfiguration
import com.tino.app.domain.profile.VerticalPresetCatalog
import com.tino.app.domain.language.EntityReference
import com.tino.app.domain.language.LanguageEntityType
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun HomeScreen(
    todayTotal: Long,
    todayReceived: Long,
    todayCash: Long,
    todayPix: Long,
    todayCard: Long,
    todaySales: Int,
    creditTotal: Long,
    creditCustomers: Int,
    customers: List<CustomerBalance> = emptyList(),
    products: List<ProductSummary> = emptyList(),
    onNavigate: (TinoScreen) -> Unit,
    storeProfile: StoreProfileEntity? = null,
    agenticVoiceState: AgenticVoiceState = AgenticVoiceState.Idle,
    onAgenticVoiceStart: () -> Unit = {},
    onAgenticVoiceStop: () -> Unit = {},
    onAgenticVoiceCancel: () -> Unit = {},
    onAgenticSubmitText: (String) -> Unit = {},
    onAgenticActionConfirm: (AgenticVoiceState.ActionPreview) -> Unit = {},
    onAgenticUndo: (String) -> Unit = {},
    onAgenticEntityChoiceSelected: (AgenticVoiceState.EntityChoice, String) -> Unit = { _, _ -> },
    onAgenticTranscriptEdit: () -> Unit = {},
    onAgenticTranscriptChange: (String) -> Unit = {},
    onAgenticTranscriptEditCancel: () -> Unit = {},
    onAgenticTranscriptContinue: () -> Unit = {},
    onAgenticTranscriptSubmit: () -> Unit = {},
    onAgenticCapabilityUseOnce: () -> Unit = {},
    onAgenticCapabilityActivate: (TinoCapabilityId) -> Unit = {},
    onQuickQueryOpen: () -> Unit = {},
    businessProfile: BusinessProfile? = null,
) {
    var queryText by remember { mutableStateOf("") }
    var keyboardInputVisible by remember { mutableStateOf(false) }
    val agenticSurfaceVisible = agenticVoiceState !is AgenticVoiceState.Idle &&
        agenticVoiceState !is AgenticVoiceState.Cancelled
    val homeConfiguration = HomeConfiguration.from(
        businessProfile ?: BusinessProfile(
            primaryVertical = BusinessVertical.RETAIL,
            enabledModules = VerticalPresetCatalog.forVertical(BusinessVertical.RETAIL).defaultModules,
        ),
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TinoSpacing.screen, vertical = TinoSpacing.md)
            .padding(bottom = TinoSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(TinoSpacing.md),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TinoLogo()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                    Box(Modifier.size(7.dp).background(TinoGreenBright, CircleShape))
                    Text("Online", color = TinoGreen, style = MaterialTheme.typography.labelMedium)
                }
                TinoIconButton(TinoIcons.Store, "Comércio ativo", true) { onNavigate(TinoScreen.Settings) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Text("Bom dia!", style = MaterialTheme.typography.headlineSmall)
            Text("O que vamos fazer hoje?", color = TinoMuted, style = MaterialTheme.typography.bodyMedium)
        }
        if (agenticSurfaceVisible) {
            HomeVoiceSurface(
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
        } else {
            TinoVoiceInputBar(
                text = queryText,
                textInputVisible = keyboardInputVisible,
                onTextChange = { queryText = it },
                onSubmitText = {
                    val text = queryText.trim()
                    if (text.isNotBlank()) {
                        onAgenticSubmitText(text)
                        queryText = ""
                        keyboardInputVisible = false
                    }
                },
                onStartVoice = onAgenticVoiceStart,
                onKeyboard = { keyboardInputVisible = !keyboardInputVisible },
                onQuickQueries = onQuickQueryOpen,
            )
        }
        homeConfiguration.primaryActions.toList().sortedBy { it.ordinal }.chunked(3).forEach { rowActions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                rowActions.forEach { action ->
                    HomeActionTile(action, Modifier.weight(1f), onNavigate)
                }
                repeat(3 - rowActions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if ((homeConfiguration.has(HomeActionId.SALES) && (todayTotal > 0L || todayReceived > 0L)) ||
            (homeConfiguration.has(HomeActionId.CREDIT) && creditTotal > 0L)
        ) {
            HomeTodaySummaryCard(
                totalCents = todayTotal,
                receivedCents = todayReceived,
                creditCents = creditTotal,
                salesCount = todaySales,
                showSales = homeConfiguration.has(HomeActionId.SALES),
                showCredit = homeConfiguration.has(HomeActionId.CREDIT),
                onClick = { onNavigate(TinoScreen.DailySummary) },
            )
        }
    }
}

@Composable
private fun HomeActionTile(action: HomeActionId, modifier: Modifier, onNavigate: (TinoScreen) -> Unit) {
    when (action) {
        HomeActionId.SALES -> TinoActionTile(TinoIcons.Cart, "Vender", "Venda rápida", modifier) { onNavigate(TinoScreen.QuickSale) }
        HomeActionId.CREDIT -> TinoActionTile(TinoIcons.People, "Fiado", "Caderneta", modifier) { onNavigate(TinoScreen.CreditList) }
        HomeActionId.STOCK_ENTRY -> TinoActionTile(TinoIcons.Supplier, "Entrada", "Mercadoria", modifier) { onNavigate(TinoScreen.StockEntry) }
        HomeActionId.INVENTORY -> TinoActionTile(TinoIcons.Products, "Estoque", "Produtos", modifier) { onNavigate(TinoScreen.Products) }
        HomeActionId.CUSTOMERS -> TinoActionTile(TinoIcons.Person, "Clientes", "Pesquisar", modifier) { onNavigate(TinoScreen.Customers) }
        HomeActionId.FISCAL -> TinoActionTile(TinoIcons.Document, "Nota", "Fiscal", modifier) { onNavigate(TinoScreen.FiscalFound) }
    }
}

@Composable
internal fun TinoVoiceInputBar(
    text: String,
    textInputVisible: Boolean,
    onTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    onStartVoice: () -> Unit,
    onKeyboard: () -> Unit,
    onQuickQueries: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onStartVoice),
            shape = TinoShapes.medium,
            colors = CardDefaults.cardColors(containerColor = TinoGreenLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = TinoSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
            ) {
                Box(Modifier.size(34.dp).background(TinoGreenTint, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(TinoIcons.Voice, contentDescription = "Falar com o TINO", tint = TinoGreen, modifier = Modifier.size(TinoSize.iconNormal))
                }
                Text(
                    if (textInputVisible) "Digite sua pergunta" else "Fale algo ou toque para falar",
                    modifier = Modifier.weight(1f),
                    color = TinoGreenDark,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TinoIconButton(TinoIcons.Edit, "Digitar", onKeyboard)
                TinoIconButton(TinoIcons.Grid, "Consultas rápidas", onQuickQueries)
            }
        }
        if (textInputVisible) {
            com.tino.app.ui.components.TinoAgentInput(
                value = text,
                onValueChange = onTextChange,
                onSubmit = onSubmitText,
            )
        }
    }
}

@Composable
internal fun HomeTodaySummaryCard(
    totalCents: Long,
    receivedCents: Long,
    creditCents: Long,
    salesCount: Int,
    showSales: Boolean = true,
    showCredit: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = TinoShapes.large,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoSize.cardElevation),
    ) {
        Column(Modifier.fillMaxWidth().padding(TinoSpacing.md), verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Resumo de hoje", color = TinoInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$salesCount vendas registradas", color = TinoMuted, style = MaterialTheme.typography.bodySmall)
                }
                Text("VER DETALHES", color = TinoGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            val metrics = buildList {
                if (showSales) {
                    add(Triple("VENDAS", formatCents(totalCents), TinoInk))
                    add(Triple("RECEBIDOS", formatCents(receivedCents), TinoGreen))
                }
                if (showCredit) add(Triple("FIADO", formatCents(creditCents), TinoOrange))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                metrics.forEach { (label, value, accent) ->
                    HomeSummaryMetric(label, value, Modifier.weight(1f), accent)
                }
            }
        }
    }
}

@Composable
private fun HomeSummaryMetric(label: String, value: String, modifier: Modifier, accent: Color = TinoInk) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = TinoMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
        Text(value, color = accent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun HomePaymentCard(
    icon: ImageVector,
    label: String,
    amountCents: Long,
    accent: Color,
    container: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = TinoShapes.small,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = TinoSpacing.md, horizontal = TinoSpacing.xs), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Box(Modifier.size(34.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(TinoSize.iconNormal))
            }
            Text(label, color = TinoInk, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(formatCents(amountCents), color = TinoInk, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
internal fun HomePaymentRow(icon: ImageVector, label: String, amountCents: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TinoSize.minTouch).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md),
    ) {
        Icon(icon, contentDescription = label, tint = TinoGreen, modifier = Modifier.size(TinoSize.iconNormal))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(formatCents(amountCents), fontWeight = FontWeight.Bold)
        Icon(TinoIcons.Forward, contentDescription = "Ver $label", tint = TinoMuted, modifier = Modifier.size(TinoSize.iconNormal))
    }
}

@Composable
internal fun HomeDebtorRow(customer: CustomerBalance, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TinoSize.minTouch).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(customer.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(formatCents(customer.balanceCents), color = TinoOrange, fontWeight = FontWeight.Bold)
        Icon(TinoIcons.Forward, contentDescription = "Abrir ${customer.name}", tint = TinoMuted, modifier = Modifier.size(TinoSize.iconNormal))
    }
}

@Composable
internal fun HomeVoiceSurface(
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
    when (state) {
        AgenticVoiceState.Idle, AgenticVoiceState.Cancelled -> TinoVoiceCard(
            title = "Fale com o TINO",
            message = "Toque para começar",
            showForward = false,
            emphasized = true,
            showVoiceIcon = false,
            onClick = onStart,
        )
        is AgenticVoiceState.Listening -> TinoCard {
            Text("ESTOU OUVINDO...", style = MaterialTheme.typography.titleMedium)
            Text(
                state.transcript.ifBlank { "Fale naturalmente. Estou ouvindo." },
                color = if (state.transcript.isBlank()) TinoMuted else TinoInk,
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                TinoSecondaryButton("CANCELAR", onCancel, Modifier.weight(1f))
                TinoPrimaryButton("PARAR", onStop, Modifier.weight(1f))
            }
        }
        is AgenticVoiceState.TranscriptReview -> TinoCard {
            Text("ENTENDI ISSO:", style = MaterialTheme.typography.titleMedium)
            if (state.editing) {
                TinoTextField(
                    value = state.transcript,
                    onValueChange = onTranscriptChange,
                    label = "Corrija se necessário",
                    labelAbove = true,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                    TinoSecondaryButton("CANCELAR", onTranscriptEditCancel, Modifier.weight(1f))
                    TinoPrimaryButton("CONFIRMAR TEXTO", onTranscriptSubmit, Modifier.weight(1f))
                }
            } else {
                Text(state.transcript, color = TinoInk, style = MaterialTheme.typography.bodyLarge)
                TranscriptReviewActions(
                    onContinue = onTranscriptContinue,
                    onEdit = onTranscriptEdit,
                    onSubmit = onTranscriptSubmit,
                )
            }
            TinoSecondaryButton("CANCELAR FALA", onCancel)
        }
        is AgenticVoiceState.Understanding -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = TinoShapes.medium,
            color = TinoGreenLight,
            tonalElevation = 0.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TinoSpacing.md, vertical = TinoSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(TinoSize.iconNormal),
                    color = TinoGreen,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = state.contextLabel,
                    modifier = Modifier.weight(1f),
                    color = TinoGreenDark,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Aguarde",
                    color = TinoMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        is AgenticVoiceState.Navigation -> Unit
        is AgenticVoiceState.Result -> {
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                TinoA2UiRenderer(state.response.message)
                TinoCard {
                    Text("Resposta pronta", style = MaterialTheme.typography.titleMedium)
                    if (BuildConfig.DEBUG) {
                        Text(
                            "Fast Router ${if (state.metrics.fastRouterHit) "HIT" else "MISS"} " +
                                "${state.metrics.fastRouterMs}ms · " +
                                "TTFP ${state.metrics.ttfpMs?.let { "${it}ms" } ?: "—"} · " +
                                "VOICE_FINAL ${state.metrics.voiceFinalMs ?: 0}ms · " +
                                "tool ${state.metrics.capabilityMs}ms · A2UI ${state.metrics.a2uiMs}ms · " +
                                "card ${state.metrics.totalToCardMs}ms · pós-final ${state.metrics.postFinalToCardMs}ms",
                            color = TinoMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        VoiceStageDiagnostics(state.metrics)
                    }
                    TinoPrimaryButton("NOVA PERGUNTA", onStart)
                }
            }
        }
        is AgenticVoiceState.CustomerBalanceResult -> {
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                TinoA2UiRenderer(state.response.message)
                TinoCard {
                    Text("Resposta pronta", style = MaterialTheme.typography.titleMedium)
                    if (BuildConfig.DEBUG) {
                        Text(
                            "Fast Router ${if (state.metrics.fastRouterHit) "HIT" else "MISS"} " +
                                "${state.metrics.fastRouterMs}ms · " +
                                "VOICE_FINAL ${state.metrics.voiceFinalMs ?: 0}ms · " +
                                "Gemma ${state.metrics.gemmaMs}ms · " +
                                "cliente ${state.metrics.customerResolutionMs ?: 0}ms · " +
                                "tool ${state.metrics.capabilityMs}ms · A2UI ${state.metrics.a2uiMs}ms · " +
                                "card ${state.metrics.totalToCardMs}ms · pós-final ${state.metrics.postFinalToCardMs}ms",
                            color = TinoMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        VoiceStageDiagnostics(state.metrics)
                    }
                    TinoPrimaryButton("NOVA PERGUNTA", onStart)
                }
            }
        }
        is AgenticVoiceState.CustomerTimelineResult -> {
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                TinoA2UiRenderer(state.response.message)
                TinoCard {
                    Text("Histórico pronto", style = MaterialTheme.typography.titleMedium)
                    if (BuildConfig.DEBUG) {
                        Text(
                            "Fast Router ${if (state.metrics.fastRouterHit) "HIT" else "MISS"} " +
                                "${state.metrics.fastRouterMs}ms · " +
                                "VOICE_FINAL ${state.metrics.voiceFinalMs ?: 0}ms · " +
                                "Gemma ${state.metrics.gemmaMs}ms · " +
                                "cliente ${state.metrics.customerResolutionMs ?: 0}ms · " +
                                "tool ${state.metrics.capabilityMs}ms · A2UI ${state.metrics.a2uiMs}ms · " +
                                "card ${state.metrics.totalToCardMs}ms · pós-final ${state.metrics.postFinalToCardMs}ms",
                            color = TinoMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        VoiceStageDiagnostics(state.metrics)
                    }
                    TinoPrimaryButton("NOVA PERGUNTA", onStart)
                }
            }
        }
        is AgenticVoiceState.ReadListResult -> {
            TinoA2UiRenderer(state.response.message)
        }
        is AgenticVoiceState.IntelligenceResult -> {
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                TinoA2UiSurfaceHost(
                    message = state.response.message,
                    surfaceId = "tino-intelligence-surface",
                )
                TinoCard {
                    Text("Análise concluída", style = MaterialTheme.typography.titleMedium)
                    if (BuildConfig.DEBUG) {
                        Text(
                            "status ${state.response.response.status.name} · " +
                                "plano ${state.response.response.plan.size} etapas · " +
                                "tool ${state.metrics.capabilityMs}ms · A2UI ${state.metrics.a2uiMs}ms",
                            color = TinoMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TinoPrimaryButton("NOVA PERGUNTA", onStart)
                }
            }
        }
        is AgenticVoiceState.EntityChoice -> {
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                TinoA2UiRenderer(
                    message = state.response.message,
                    onEntityChoiceSelected = { label -> onEntityChoiceSelected(state, label) },
                )
                TinoSecondaryButton("CANCELAR", onCancel)
            }
        }
        is AgenticVoiceState.ActionPreview -> {
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                TinoA2UiRenderer(
                    message = state.response.message,
                    onActionConfirmed = { onActionConfirm(state) },
                    onActionCancelled = onCancel,
                )
                if (BuildConfig.DEBUG) {
                    TinoCard {
                        Text(
                            "VOICE_FINAL ${state.metrics.voiceFinalMs ?: 0}ms · " +
                                "Gemma ${state.metrics.gemmaMs}ms · " +
                                "cliente ${state.metrics.customerResolutionMs ?: 0}ms · " +
                                "produto ${state.metrics.productResolutionMs ?: 0}ms · " +
                                "capability ${state.metrics.capabilityMs}ms",
                            color = TinoMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        VoiceStageDiagnostics(state.metrics)
                    }
                }
            }
        }
        is AgenticVoiceState.ActionCompleted -> {
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                TinoA2UiRenderer(
                    message = state.response.message,
                    onUndo = onUndo,
                )
                TinoPrimaryButton("NOVA PERGUNTA", onStart)
            }
        }
        is AgenticVoiceState.Unsupported -> Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
            TinoA2UiRenderer(
                message = A2uiSemanticMapper.error(
                    message = state.message,
                    title = "AINDA NÃO CONSIGO RESPONDER",
                ),
                onRetry = onStart,
            )
            if (state.debug?.capability != null) {
                TinoPrimaryButton("USAR UMA VEZ", onCapabilityUseOnce)
                val capability = runCatching {
                    TinoCapabilityId.valueOf(state.debug.capability)
                }.getOrNull()
                if (capability != null && CapabilityRecoveryPolicy.canActivatePermanently(capability)) {
                    TinoSecondaryButton("ATIVAR SEMPRE", { onCapabilityActivate(capability) })
                }
            }
            TinoSecondaryButton("CANCELAR", onCancel)
            if (BuildConfig.DEBUG) {
                state.debug?.let { debug ->
                    TinoCard {
                        Text("DEBUG · ${debug.code}", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "capability=${debug.capability ?: "—"} · " +
                                "inesperados=${debug.unexpectedKeys.joinToString().ifBlank { "—" }}",
                            color = TinoMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        debug.rawOutput?.let { raw ->
                            Text(
                                raw,
                                color = TinoMuted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        is AgenticVoiceState.Error -> Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
            TinoA2UiRenderer(
                message = A2uiSemanticMapper.error(
                    message = state.message,
                    title = "NÃO ENTENDI DIREITO",
                ),
                onRetry = onStart,
            )
            TinoSecondaryButton("CANCELAR", onCancel)
        }
    }
}

@Composable
private fun TranscriptReviewActions(
    onContinue: () -> Unit,
    onEdit: () -> Unit,
    onSubmit: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 380.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
                ) {
                    TinoSecondaryButton("CONTINUAR", onContinue, Modifier.weight(1f))
                    TinoSecondaryButton("EDITAR", onEdit, Modifier.weight(1f))
                }
                TinoPrimaryButton("ENVIAR", onSubmit)
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
            ) {
                TinoSecondaryButton("CONTINUAR", onContinue, Modifier.weight(1f))
                TinoSecondaryButton("EDITAR", onEdit, Modifier.weight(1f))
                TinoPrimaryButton("ENVIAR", onSubmit, Modifier.weight(1f))
            }
        }
    }
}
