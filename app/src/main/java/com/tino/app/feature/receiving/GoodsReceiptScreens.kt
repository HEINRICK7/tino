package com.tino.app.feature.receiving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tino.app.domain.receiving.GoodsReceiptConfirmation
import com.tino.app.domain.receiving.GoodsReceiptDecision
import com.tino.app.domain.receiving.GoodsReceiptDecisionAction
import com.tino.app.domain.receiving.GoodsReceiptPreview
import com.tino.app.domain.receiving.GoodsReceiptRemoteState
import com.tino.app.domain.receiving.GoodsReceiptResult
import com.tino.app.domain.receiving.ProductResolutionStatus
import com.tino.app.domain.receiving.ProductSearchItem
import com.tino.app.domain.receiving.FiscalStatus
import com.tino.app.ui.components.TinoCard
import com.tino.app.ui.components.TinoContextHeader
import com.tino.app.ui.components.TinoEmptyState
import com.tino.app.ui.components.TinoPrimaryButton
import com.tino.app.ui.components.TinoSecondaryButton
import com.tino.app.ui.components.TinoSectionLabel
import com.tino.app.ui.components.TinoTextField
import com.tino.app.ui.components.TinoHeaderStyle
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoOrange
import com.tino.app.ui.theme.TinoSpacing
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.illustration.TinoIllustrationState
import java.math.BigDecimal

@Composable
internal fun NfeKeyEntryScreen(
    state: GoodsReceiptRemoteState,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onRetry: () -> Unit,
) {
    var accessKey by remember { mutableStateOf("") }
    val loading = state is GoodsReceiptRemoteState.ReadingKey || state is GoodsReceiptRemoteState.Retrieving
    ScreenColumnReceiving {
        TinoContextHeader(
            title = "Usar NF-e",
            subtitle = "Informe a chave de acesso. O TINO consulta o backend e mostra o que chegou.",
            icon = TinoIcons.Document,
            style = TinoHeaderStyle.Inventory,
            onBack = onBack,
        )
        TinoTextField(accessKey, { accessKey = it.filter(Char::isDigit).take(44) }, "Chave de acesso", "Cole ou digite os 44 dígitos")
        Text("A NF-e é consultada pelo TINO Backend. Nenhum dado fiscal é reconstruído por foto.", color = TinoMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(TinoSpacing.md))
        when (state) {
            GoodsReceiptRemoteState.ReadingKey,
            GoodsReceiptRemoteState.Retrieving,
            -> Text("Consultando a NF-e…", color = TinoMuted)
            GoodsReceiptRemoteState.Waiting -> TinoCard { Text("Consulta em processamento", style = MaterialTheme.typography.titleMedium); Text("Aguarde a confirmação do backend.", color = TinoMuted) }
            is GoodsReceiptRemoteState.RetryableError -> TinoCard { Text("Não foi possível consultar agora", color = TinoOrange); Text(state.message, color = TinoMuted); TinoSecondaryButton("TENTAR NOVAMENTE", onRetry) }
            is GoodsReceiptRemoteState.TerminalError -> TinoCard { Text("Entrada NF-e indisponível", color = TinoOrange); Text(state.message, color = TinoMuted) }
            else -> Unit
        }
        TinoPrimaryButton("CONSULTAR NF-e", { if (!loading) onSubmit(accessKey) }, Modifier, enabled = accessKey.length == 44 && !loading, loading = loading)
        TinoSecondaryButton("REGISTRAR MANUALMENTE", onBack)
    }
}

@Composable
internal fun NfePreviewScreen(
    state: GoodsReceiptRemoteState,
    searchResults: List<ProductSearchItem>,
    onBack: () -> Unit,
    onSearchProducts: (String) -> Unit,
    onRetry: () -> Unit,
    onConfirm: (GoodsReceiptPreview, GoodsReceiptConfirmation) -> Unit,
) {
    val preview = when (state) {
        is GoodsReceiptRemoteState.PreviewReady -> state.preview
        is GoodsReceiptRemoteState.ReviewRequired -> state.preview
        is GoodsReceiptRemoteState.Confirming -> state.preview
        else -> null
    }
    if (preview == null) {
        NfeResultScreen(state, onBack, onRetry)
        return
    }
    val actions = remember(preview.previewId, preview.version) {
        mutableStateMapOf<Int, GoodsReceiptDecision>().apply {
            preview.items.filter { it.resolutionStatus == ProductResolutionStatus.MATCHED && it.productId != null }
                .forEach { item -> put(item.lineNumber, GoodsReceiptDecision(item.lineNumber, GoodsReceiptDecisionAction.USE_EXISTING, item.productId)) }
            preview.items.filter { it.resolutionStatus == ProductResolutionStatus.IGNORED }
                .forEach { item -> put(item.lineNumber, GoodsReceiptDecision(item.lineNumber, GoodsReceiptDecisionAction.IGNORE)) }
        }
    }
    var searchText by remember(preview.previewId) { mutableStateOf("") }
    var baseUnits by remember(preview.previewId) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var factors by remember(preview.previewId) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val canConfirm = preview.fiscalStatus == FiscalStatus.AUTHORIZED && preview.items.all { item ->
        val decision = actions[item.lineNumber]
        if (decision == null || decision.action == GoodsReceiptDecisionAction.IGNORE) {
            decision != null
        } else {
            val baseUnit = decision.baseUnit ?: item.baseUnit
            val productDecisionValid = when (decision.action) {
                GoodsReceiptDecisionAction.USE_EXISTING -> decision.productId != null
                GoodsReceiptDecisionAction.CREATE_PRODUCT -> baseUnit != null
                GoodsReceiptDecisionAction.IGNORE -> true
            }
            productDecisionValid && baseUnit != null &&
                (item.purchaseUnit == baseUnit || decision.conversionFactor?.signum() == 1)
        }
    }
    ScreenColumnReceiving {
        TinoContextHeader(
            title = "Conferir entrada",
            subtitle = "${preview.summary.totalItems} item(ns) · ${preview.issuer?.legalName ?: "fornecedor não informado"}",
            icon = TinoIcons.Document,
            style = TinoHeaderStyle.Inventory,
            onBack = onBack,
        )
        TinoCard {
            Text("NF-e ${listOfNotNull(preview.documentNumber, preview.series).joinToString(" · ").ifBlank { "consultada" }}", style = MaterialTheme.typography.titleMedium)
            Text("Revise os itens. Nada entra no estoque sem sua confirmação.", color = TinoMuted)
        }
        TinoSectionLabel("Itens da entrada")
        preview.items.forEach { item ->
            val decision = actions[item.lineNumber]
            TinoCard {
                Text(item.description, style = MaterialTheme.typography.titleMedium)
                Text("${item.purchaseQuantity.toPlainString()} ${item.purchaseUnit} · custo ${item.purchaseUnitCost.toPlainString()}", color = TinoMuted)
                Text("${item.resolutionStatus.name}", color = if (item.requiresUserAction) TinoOrange else TinoGreen)
                if (item.resolutionStatus == ProductResolutionStatus.NEEDS_REVIEW && decision?.productId == null) {
                    TinoTextField(searchText, { searchText = it; onSearchProducts(it) }, "Buscar produto", "Nome ou GTIN")
                    searchResults.forEach { product ->
                        TinoSecondaryButton(product.name) {
                            actions[item.lineNumber] = GoodsReceiptDecision(item.lineNumber, GoodsReceiptDecisionAction.USE_EXISTING, product.productId)
                        }
                    }
                }
                if (decision?.action == GoodsReceiptDecisionAction.USE_EXISTING) {
                    Text("Produto selecionado", color = TinoGreen)
                } else if (item.resolutionStatus == ProductResolutionStatus.MATCHED && item.productId != null) {
                    TinoPrimaryButton("USAR ${item.candidateName ?: "PRODUTO RECONHECIDO"}") {
                        actions[item.lineNumber] = GoodsReceiptDecision(item.lineNumber, GoodsReceiptDecisionAction.USE_EXISTING, item.productId)
                    }
                } else {
                    TinoPrimaryButton("CRIAR PRODUTO COM ESTE NOME") {
                        actions[item.lineNumber] = GoodsReceiptDecision(
                            lineNumber = item.lineNumber,
                            action = GoodsReceiptDecisionAction.CREATE_PRODUCT,
                            baseUnit = baseUnits[item.lineNumber]?.takeIf(String::isNotBlank),
                            conversionFactor = factors[item.lineNumber]?.toBigDecimalOrNull(),
                        )
                    }
                }
                if (decision?.action == GoodsReceiptDecisionAction.CREATE_PRODUCT || item.baseUnit == null) {
                    TinoTextField(baseUnits[item.lineNumber].orEmpty(), { value ->
                        baseUnits = baseUnits + (item.lineNumber to value)
                        actions[item.lineNumber]?.let { actions[item.lineNumber] = it.copy(baseUnit = value.takeIf(String::isNotBlank)) }
                    }, "Unidade no estoque", "Ex.: UN, KG, CX")
                }
                val selectedBaseUnit = decision?.baseUnit ?: item.baseUnit
                if (selectedBaseUnit != null && item.purchaseUnit != selectedBaseUnit && decision?.action != GoodsReceiptDecisionAction.IGNORE) {
                    TinoTextField(factors[item.lineNumber].orEmpty(), { value -> factors = factors + (item.lineNumber to value); actions[item.lineNumber]?.let { actions[item.lineNumber] = it.copy(conversionFactor = value.toBigDecimalOrNull()) } }, "Conversão", "Quantas unidades de estoque há em 1 ${item.purchaseUnit}?")
                }
                TinoSecondaryButton("IGNORAR ITEM") { actions[item.lineNumber] = GoodsReceiptDecision(item.lineNumber, GoodsReceiptDecisionAction.IGNORE) }
            }
        }
        TinoPrimaryButton(
            "CONFIRMAR ENTRADA",
            { onConfirm(preview, GoodsReceiptConfirmation(preview.version, preview.items.mapNotNull { actions[it.lineNumber] })) },
            Modifier,
            enabled = canConfirm && state !is GoodsReceiptRemoteState.Confirming,
            loading = state is GoodsReceiptRemoteState.Confirming,
        )
    }
}

@Composable
private fun NfeResultScreen(state: GoodsReceiptRemoteState, onBack: () -> Unit, onRetry: () -> Unit) {
    ScreenColumnReceiving {
        TinoContextHeader("Entrada NF-e", "Resultado da operação", TinoIcons.Document, TinoHeaderStyle.Inventory, onBack)
        when (state) {
            is GoodsReceiptRemoteState.Confirmed -> {
                TinoEmptyState(
                    TinoIcons.Success,
                    "Entrada confirmada",
                    "${state.result.itemCount} item(ns) projetado(s) no estoque.",
                    illustrationState = TinoIllustrationState.SUCCESS,
                )
                state.result.items.forEach { item -> Text("${item.productName}: ${item.quantityAdded.toPlainString()} ${item.baseUnit}", color = TinoMuted) }
            }
            is GoodsReceiptRemoteState.TerminalError -> TinoEmptyState(
                TinoIcons.Error,
                "Não foi possível concluir",
                state.message,
                illustrationState = TinoIllustrationState.ERROR,
            )
            is GoodsReceiptRemoteState.RetryableError -> {
                TinoEmptyState(
                    TinoIcons.Error,
                    "Tente novamente",
                    state.message,
                    illustrationState = TinoIllustrationState.WARNING,
                )
                TinoPrimaryButton("TENTAR NOVAMENTE", onRetry)
            }
            else -> TinoEmptyState(
                TinoIcons.Document,
                "Aguardando dados",
                "Informe uma chave de acesso para começar.",
                illustrationState = TinoIllustrationState.EXPLAINING,
            )
        }
        TinoSecondaryButton("VOLTAR", onBack)
    }
}

@Composable
private fun ScreenColumnReceiving(content: @Composable ColumnScope.() -> Unit) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
