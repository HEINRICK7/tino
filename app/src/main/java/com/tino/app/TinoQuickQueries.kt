package com.tino.app

import com.tino.app.ui.a2ui.TinoAgentCatalogSurface
import com.tino.app.ui.a2ui.presentsBottomRiseCatalog
import com.tino.app.ui.components.tinoClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.tino.app.domain.agent.AgentCapability
import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.feature.voice.AgenticVoiceState
import com.tino.app.ui.components.TinoCard
import com.tino.app.ui.components.LocalTinoScrollTelemetry
import com.tino.app.ui.components.tinoInteractiveBounds
import com.tino.app.ui.components.TinoIconButton
import com.tino.app.ui.components.TinoPrimaryButton
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoPaper
import com.tino.app.ui.theme.TinoElevation
import com.tino.app.ui.theme.TinoSize
import com.tino.app.ui.theme.TinoSpacing
import com.tino.app.ui.theme.TinoSurface
import kotlinx.coroutines.flow.collect

internal enum class QuickQueryCategory(val label: String) {
    STOCK("ESTOQUE"),
    SALES("VENDAS"),
    CUSTOMERS("CLIENTES / FIADO"),
}

internal data class QuickQueryDefinition(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val category: QuickQueryCategory,
    val intent: String,
    val capability: AgentCapability,
    val requiredCapabilities: Set<TinoCapabilityId>,
    val availability: Boolean = true,
    val priority: Int = 0,
)

internal object TinoQuickQueryCatalog {
    val all = listOf(
        QuickQueryDefinition("products", "Meus produtos", TinoIcons.Products, QuickQueryCategory.STOCK, "LIST_PRODUCTS", AgentCapability.LIST_PRODUCTS, setOf(TinoCapabilityId.LIST_PRODUCTS), priority = 10),
        QuickQueryDefinition("low-stock", "Estoque baixo", TinoIcons.Warning, QuickQueryCategory.STOCK, "REPLENISHMENT_QUERY", AgentCapability.REPLENISHMENT_QUERY, setOf(TinoCapabilityId.REPLENISHMENT_QUERY), priority = 9),
        QuickQueryDefinition("zero-stock", "Produtos zerados", TinoIcons.Error, QuickQueryCategory.STOCK, "REPLENISHMENT_QUERY", AgentCapability.REPLENISHMENT_QUERY, setOf(TinoCapabilityId.REPLENISHMENT_QUERY), priority = 8),
        QuickQueryDefinition("replenish", "Preciso repor", TinoIcons.Repeat, QuickQueryCategory.STOCK, "REPLENISHMENT_QUERY", AgentCapability.REPLENISHMENT_QUERY, setOf(TinoCapabilityId.REPLENISHMENT_QUERY), priority = 8),
        QuickQueryDefinition("today-sales", "Vendas de hoje", TinoIcons.Trends, QuickQueryCategory.SALES, "READ_FINANCIAL_SUMMARY", AgentCapability.READ_FINANCIAL_SUMMARY, setOf(TinoCapabilityId.READ_FINANCIAL_SUMMARY), priority = 7),
        QuickQueryDefinition("week-sales", "Vendas da semana", TinoIcons.Calendar, QuickQueryCategory.SALES, "READ_FINANCIAL_SUMMARY", AgentCapability.READ_FINANCIAL_SUMMARY, setOf(TinoCapabilityId.READ_FINANCIAL_SUMMARY), availability = false, priority = 6),
        QuickQueryDefinition("customers", "Meus clientes", TinoIcons.People, QuickQueryCategory.CUSTOMERS, "LIST_CUSTOMERS", AgentCapability.LIST_CUSTOMERS, setOf(TinoCapabilityId.LIST_CUSTOMERS), priority = 10),
        QuickQueryDefinition("receivables", "Quem deve?", TinoIcons.Credit, QuickQueryCategory.CUSTOMERS, "LIST_RECEIVABLES", AgentCapability.LIST_RECEIVABLES, setOf(TinoCapabilityId.LIST_RECEIVABLES), priority = 9),
    ).sortedByDescending { it.priority }
}

internal fun tinoCardActionDestination(action: String): TinoScreen = when {
    action.startsWith("products") -> TinoScreen.Products
    action.startsWith("customer") -> TinoScreen.Customers
    action.startsWith("receivables") -> TinoScreen.CreditList
    action.startsWith("financial") -> TinoScreen.DailySummary
    else -> TinoScreen.Home
}

internal fun availableQuickQueries(
    allowedCapabilities: Set<TinoCapabilityId>,
): List<QuickQueryDefinition> = TinoQuickQueryCatalog.all.filter { query ->
    query.requiredCapabilities.all(allowedCapabilities::contains)
}

@Composable
internal fun QuickQueriesScreen(
    state: AgenticVoiceState,
    allowedCapabilities: Set<TinoCapabilityId> = TinoCapabilityId.values().toSet(),
    onBack: () -> Unit,
    onQuery: (QuickQueryDefinition) -> Unit,
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
    onCardAction: (String) -> Unit = {},
) {
    val catalogOpen = state.presentsBottomRiseCatalog()
    val scrollState = rememberScrollState()
    val scrollTelemetry = LocalTinoScrollTelemetry.current
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { offset ->
            scrollTelemetry.offsetPx = offset
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(scrollState).padding(TinoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(TinoSpacing.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TinoIconButton(TinoIcons.Back, "Voltar", onBack)
            Column(Modifier.weight(1f).padding(horizontal = TinoSpacing.sm)) {
                Text("Consultas rápidas", style = MaterialTheme.typography.headlineSmall)
                Text("Veja respostas úteis em um toque", color = TinoMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        TinoQuickQuerySection(QuickQueryCategory.STOCK, allowedCapabilities, onQuery)
        TinoQuickQuerySection(QuickQueryCategory.SALES, allowedCapabilities, onQuery)
        TinoQuickQuerySection(QuickQueryCategory.CUSTOMERS, allowedCapabilities, onQuery)
        Spacer(Modifier.height(TinoSize.surfaceContentReservedHeight))
    }
    if (catalogOpen) {
        TinoAgentCatalogSurface(
            state = state,
            onDismiss = onCancel,
            onStart = onStart,
            onActionConfirm = onActionConfirm,
            onUndo = onUndo,
            onEntityChoiceSelected = onEntityChoiceSelected,
            onCapabilityUseOnce = onCapabilityUseOnce,
            onCapabilityActivate = onCapabilityActivate,
            onCardAction = onCardAction,
        )
    }
    }
}

@Composable
private fun TinoQuickQuerySection(
    category: QuickQueryCategory,
    allowedCapabilities: Set<TinoCapabilityId>,
    onQuery: (QuickQueryDefinition) -> Unit,
) {
    val queries = availableQuickQueries(allowedCapabilities).filter { it.category == category }
    Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
        Text(category.label, color = TinoMuted, style = MaterialTheme.typography.labelMedium)
        queries.chunked(2).forEach { rowQueries ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                rowQueries.forEach { query ->
                    TinoQuickQueryCard(query, Modifier.weight(1f), onQuery)
                }
                if (rowQueries.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TinoQuickQueryCard(
    query: QuickQueryDefinition,
    modifier: Modifier,
    onQuery: (QuickQueryDefinition) -> Unit,
) {
    val enabled = query.availability
    TinoCard(
        modifier = modifier.tinoInteractiveBounds("quick-query:" + query.id).tinoClickable(enabled = enabled) { onQuery(query) },
        containerColor = if (enabled) TinoSurface else TinoPaper,
        elevation = if (enabled) TinoElevation.subtle else TinoElevation.none,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(TinoSpacing.sm),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(TinoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
        ) {
            Icon(query.icon, contentDescription = null, tint = if (enabled) TinoGreen else TinoMuted, modifier = Modifier.size(TinoSize.iconNormal))
            Text(
                query.title,
                modifier = Modifier.weight(1f),
                color = if (enabled) TinoInk else TinoMuted,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
            )
            if (enabled) {
                Icon(
                    TinoIcons.Forward,
                    contentDescription = "Abrir ${query.title}",
                    tint = TinoGreen,
                    modifier = Modifier.size(TinoSize.iconNormal),
                )
            }
        }
    }
}
