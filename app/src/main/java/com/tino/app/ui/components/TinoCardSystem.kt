package com.tino.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.theme.TinoAmber
import com.tino.app.ui.theme.TinoAmberContainer
import com.tino.app.ui.theme.TinoBorder
import com.tino.app.ui.theme.TinoElevation
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoGreenTint
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoPaper
import com.tino.app.ui.theme.TinoPurple
import com.tino.app.ui.theme.TinoPurpleContainer
import com.tino.app.ui.theme.TinoRed
import com.tino.app.ui.theme.TinoRedContainer
import com.tino.app.ui.theme.TinoShapes
import com.tino.app.ui.theme.TinoSize
import com.tino.app.ui.theme.TinoSpacing
import com.tino.app.ui.theme.TinoSurface

/** The semantic state controls both meaning and the visual treatment. */
enum class TinoCardStatus { NEUTRAL, SUCCESS, WARNING, ERROR, INFO, CREDIT }

/** Closed visual vocabulary consumed by both Home and the A2UI adapter. */
sealed interface TinoCardSpec {
    data class Metric(
        val icon: ImageVector,
        val title: String,
        val value: String,
        val supportingText: String,
        val details: List<Pair<String, String>> = emptyList(),
        val modifier: Modifier = Modifier,
        val status: TinoCardStatus = TinoCardStatus.NEUTRAL,
        val actionLabel: String? = null,
        val onClick: (() -> Unit)? = null,
    ) : TinoCardSpec

    data class Catalog(
        val icon: ImageVector,
        val title: String,
        val context: String,
        val primaryText: String,
        val secondaryText: String? = null,
        val statusText: String? = null,
        val metadata: List<Pair<String, String>> = emptyList(),
        val modifier: Modifier = Modifier,
        val status: TinoCardStatus = TinoCardStatus.SUCCESS,
        val onClick: (() -> Unit)? = null,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
    ) : TinoCardSpec

    data class CatalogList(
        val title: String,
        val items: List<TinoCatalogItemSpec>,
        val emptyMessage: String? = null,
        val footerLabel: String? = null,
        val onFooter: (() -> Unit)? = null,
        val variant: String = "catalog",
        val modifier: Modifier = Modifier,
    ) : TinoCardSpec

    data class EntityList(
        val title: String,
        val items: List<TinoEntityCardSpec>,
        val emptyMessage: String? = null,
        val modifier: Modifier = Modifier,
    ) : TinoCardSpec

    data class Status(
        val icon: ImageVector,
        val title: String,
        val message: String,
        val modifier: Modifier = Modifier,
        val status: TinoCardStatus = TinoCardStatus.SUCCESS,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
        val footerActions: List<TinoCardAction> = emptyList(),
    ) : TinoCardSpec

    data class Action(
        val actions: List<TinoSystemAction>,
        val modifier: Modifier = Modifier,
        val title: String? = null,
        val supportingText: String? = null,
    ) : TinoCardSpec

    data class Timeline(
        val title: String,
        val items: List<Triple<String, String, String>>,
        val modifier: Modifier = Modifier,
        val footerLabel: String? = "Ver histórico completo",
        val onClick: (() -> Unit)? = null,
    ) : TinoCardSpec

    data class Preview(
        val title: String,
        val rows: List<Pair<String, String>>,
        val total: String,
        val modifier: Modifier = Modifier,
        val onCancel: (() -> Unit)? = null,
        val onConfirm: (() -> Unit)? = null,
    ) : TinoCardSpec

    data class Empty(
        val title: String = "Nada por aqui ainda",
        val message: String = "Assim que tiver informações, elas vão aparecer.",
        val modifier: Modifier = Modifier,
    ) : TinoCardSpec
}

@Composable
fun TinoCardRenderer(spec: TinoCardSpec) {
    when (spec) {
        is TinoCardSpec.Metric -> TinoSystemMetricCard(
            icon = spec.icon,
            title = spec.title,
            value = spec.value,
            supportingText = spec.supportingText,
            modifier = spec.modifier,
            status = spec.status,
            actionLabel = spec.actionLabel,
            onClick = spec.onClick,
            details = spec.details,
        )
        is TinoCardSpec.Catalog -> TinoSystemCatalogCard(
            icon = spec.icon,
            title = spec.title,
            context = spec.context,
            primaryText = spec.primaryText,
            secondaryText = spec.secondaryText,
            statusText = spec.statusText,
            metadata = spec.metadata,
            modifier = spec.modifier,
            status = spec.status,
            onClick = spec.onClick,
            actionLabel = spec.actionLabel,
            onAction = spec.onAction,
        )
        is TinoCardSpec.CatalogList -> TinoSystemCatalogListCard(spec)
        is TinoCardSpec.EntityList -> TinoSystemEntityList(spec)
        is TinoCardSpec.Status -> TinoSystemStatusCard(
            icon = spec.icon,
            title = spec.title,
            message = spec.message,
            modifier = spec.modifier,
            status = spec.status,
            actionLabel = spec.actionLabel,
            onAction = spec.onAction,
            footerActions = spec.footerActions,
        )
        is TinoCardSpec.Action -> TinoSystemActionListCard(spec.actions, spec.modifier, spec.title, spec.supportingText)
        is TinoCardSpec.Timeline -> TinoCardTimeline(spec.title, spec.items, spec.modifier, spec.footerLabel, spec.onClick)
        is TinoCardSpec.Preview -> TinoSystemPreviewCard(
            title = spec.title,
            rows = spec.rows,
            total = spec.total,
            modifier = spec.modifier,
            onCancel = spec.onCancel,
            onConfirm = spec.onConfirm,
        )
        is TinoCardSpec.Empty -> TinoSystemEmptyStateCard(spec.title, spec.message, spec.modifier)
    }
}

private fun TinoCardStatus.accent(): Color = when (this) {
    TinoCardStatus.SUCCESS -> TinoGreen
    TinoCardStatus.WARNING -> TinoAmber
    TinoCardStatus.ERROR -> TinoRed
    TinoCardStatus.INFO -> com.tino.app.ui.theme.TinoBlue
    TinoCardStatus.CREDIT -> TinoPurple
    TinoCardStatus.NEUTRAL -> TinoMuted
}

private fun TinoCardStatus.container(): Color = when (this) {
    TinoCardStatus.SUCCESS -> TinoGreenTint
    TinoCardStatus.WARNING -> TinoAmberContainer
    TinoCardStatus.ERROR -> TinoRedContainer
    TinoCardStatus.INFO, TinoCardStatus.NEUTRAL -> TinoPaper
    TinoCardStatus.CREDIT -> TinoPurpleContainer
}

private fun TinoCardStatus.border(): Color = when (this) {
    TinoCardStatus.SUCCESS -> TinoGreen.copy(alpha = 0.24f)
    TinoCardStatus.WARNING -> TinoAmber.copy(alpha = 0.28f)
    TinoCardStatus.ERROR -> TinoRed.copy(alpha = 0.24f)
    TinoCardStatus.INFO -> com.tino.app.ui.theme.TinoBlue.copy(alpha = 0.22f)
    TinoCardStatus.CREDIT -> TinoPurple.copy(alpha = 0.24f)
    TinoCardStatus.NEUTRAL -> TinoBorder
}

/** Shared shell tokens. Every card family is rendered through this surface. */
@Composable
fun TinoCardSurface(
    modifier: Modifier = Modifier,
    status: TinoCardStatus = TinoCardStatus.NEUTRAL,
    onClick: (() -> Unit)? = null,
    description: String,
    contentPadding: PaddingValues = PaddingValues(TinoSpacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .tinoAnimateContentSize()
            .tinoOccupiedBounds("tino-card-surface:" + description)
            .tinoClickable(onClick = onClick)
            .border(BorderStroke(TinoSize.cardBorder, status.border()), TinoShapes.large)
            .semantics { contentDescription = description },
        shape = TinoShapes.large,
        colors = CardDefaults.cardColors(containerColor = status.container()),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoElevation.subtle),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
            content = content,
        )
    }
}

@Composable
fun TinoCardHeader(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    status: TinoCardStatus = TinoCardStatus.NEUTRAL,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TinoSystemIcon(icon, status)
        Spacer(Modifier.width(TinoSpacing.sm))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TinoInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TinoMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.invoke()
    }
}

@Composable
fun TinoCardDivider() {
    HorizontalDivider(color = TinoBorder.copy(alpha = 0.55f))
}

@Composable
fun TinoCardFooter(
    label: String,
    status: TinoCardStatus = TinoCardStatus.NEUTRAL,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().tinoClickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = status.accent(), fontWeight = FontWeight.SemiBold)
        Icon(TinoIcons.Forward, contentDescription = null, tint = status.accent(), modifier = Modifier.size(TinoSize.iconNormal))
    }
}

@Composable
private fun TinoCardFrame(
    modifier: Modifier = Modifier,
    status: TinoCardStatus = TinoCardStatus.NEUTRAL,
    onClick: (() -> Unit)? = null,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    TinoCardSurface(
        modifier = modifier,
        status = status,
        onClick = onClick,
        description = description,
        content = content,
    )
}

@Composable
fun TinoSystemMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    supportingText: String,
    details: List<Pair<String, String>> = emptyList(),
    modifier: Modifier = Modifier,
    status: TinoCardStatus = TinoCardStatus.NEUTRAL,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    TinoCardFrame(
        modifier = modifier,
        status = status,
        onClick = onClick,
        description = "$title: $value. $supportingText",
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            TinoSystemIcon(icon, status)
            Spacer(Modifier.width(TinoSpacing.sm))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TinoInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.headlineSmall, color = status.accent(), fontWeight = FontWeight.Bold, maxLines = 1)
                Text(supportingText, style = MaterialTheme.typography.bodySmall, color = TinoMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (details.isNotEmpty()) {
            TinoCardDivider()
            details.forEach { (label, detail) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = TinoMuted)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = TinoInk, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        actionLabel?.let {
            TinoCardDivider()
            TinoCardFooter(it, status, onClick)
        }
    }
}

@Composable
fun TinoSystemCatalogCard(
    icon: ImageVector,
    title: String,
    context: String,
    primaryText: String,
    secondaryText: String? = null,
    statusText: String? = null,
    metadata: List<Pair<String, String>> = emptyList(),
    modifier: Modifier = Modifier,
    status: TinoCardStatus = TinoCardStatus.SUCCESS,
    onClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    TinoCardFrame(
        modifier = modifier,
        status = when (status) {
            TinoCardStatus.CREDIT, TinoCardStatus.ERROR -> status
            else -> TinoCardStatus.NEUTRAL
        },
        onClick = onClick,
        description = listOfNotNull(title, context, primaryText, statusText).joinToString(", "),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = TinoSize.minTouch),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TinoSystemIcon(icon, status)
            Spacer(Modifier.width(TinoSpacing.sm))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TinoInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(context, style = MaterialTheme.typography.labelSmall, color = TinoMuted, maxLines = 1)
                statusText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = status.accent(), maxLines = 1) }
            }
            Spacer(Modifier.width(TinoSpacing.md))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(primaryText, style = MaterialTheme.typography.titleMedium, color = status.accent(), fontWeight = FontWeight.Bold, maxLines = 1)
                secondaryText?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TinoMuted, maxLines = 1) }
            }
            if (actionLabel == null) {
                Icon(TinoIcons.Forward, contentDescription = "Abrir", tint = TinoMuted, modifier = Modifier.padding(start = TinoSpacing.xs))
            }
        }
        if (metadata.isNotEmpty()) {
            TinoCardDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md)) {
                metadata.forEach { (label, value) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = TinoMuted)
                        Text(value, style = MaterialTheme.typography.bodySmall, color = TinoInk, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (actionLabel != null) {
            TinoCardDivider()
            TinoCardFooter(actionLabel, status, onAction)
        }
    }
}

@Composable
fun TinoSystemStatusCard(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    status: TinoCardStatus = TinoCardStatus.SUCCESS,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    footerActions: List<TinoCardAction> = emptyList(),
) {
    TinoCardFrame(
        modifier = modifier,
        status = status,
        onClick = if (actionLabel == null) onAction else null,
        description = "$title. $message",
    ) {
        TinoCardHeader(
            icon = icon,
            title = title,
            subtitle = message,
            status = status,
            trailing = if (onAction != null && actionLabel == null) {
                { Icon(TinoIcons.Forward, contentDescription = "Abrir", tint = TinoMuted) }
            } else null,
        )
        if (actionLabel != null && onAction != null) {
            TinoPrimaryButton(actionLabel, onAction)
        }
        if (footerActions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                footerActions.forEach { action ->
                    TinoTextAction(
                        label = action.label,
                        onClick = action.onClick,
                        color = action.color ?: status.accent(),
                    )
                }
            }
        }
    }
}

data class TinoCardAction(
    val label: String,
    val onClick: () -> Unit,
    val color: Color? = null,
)

data class TinoCatalogItemSpec(
    val icon: ImageVector,
    val title: String,
    val context: String? = null,
    val primaryText: String = "",
    val secondaryText: String? = null,
    val supportingText: String? = null,
    val statusText: String? = null,
    val status: TinoCardStatus = TinoCardStatus.NEUTRAL,
    val onClick: (() -> Unit)? = null,
)

data class TinoEntityCardSpec(
    val icon: ImageVector,
    val title: String,
    val context: String,
    val primaryText: String,
    val secondaryText: String? = null,
    val statusText: String? = null,
    val metadata: List<Pair<String, String>> = emptyList(),
    val status: TinoCardStatus = TinoCardStatus.NEUTRAL,
    val footerLabel: String? = null,
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun TinoSystemCatalogListCard(spec: TinoCardSpec.CatalogList) {
    Column(
        modifier = spec.modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
    ) {
        Text(spec.title, style = MaterialTheme.typography.titleMedium, color = TinoInk, fontWeight = FontWeight.SemiBold)
        if (spec.items.isEmpty()) {
            TinoSystemEmptyStateCard(
                title = "Nada por aqui ainda",
                message = spec.emptyMessage ?: "Nenhum item encontrado.",
                modifier = Modifier,
            )
        } else {
            TinoCardFrame(description = spec.title) {
                spec.items.forEachIndexed { index, item ->
                    TinoCatalogListItem(item, spec.variant)
                    if (index < spec.items.lastIndex) TinoCardDivider()
                }
                if (spec.footerLabel != null) {
                    TinoCardDivider()
                    TinoCardFooter(spec.footerLabel, TinoCardStatus.SUCCESS, spec.onFooter)
                }
            }
        }
    }
}

@Composable
private fun TinoSystemEntityList(spec: TinoCardSpec.EntityList) {
    Column(
        modifier = spec.modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TinoSpacing.sm),
    ) {
        Text(spec.title, style = MaterialTheme.typography.titleMedium, color = TinoInk, fontWeight = FontWeight.SemiBold)
        if (spec.items.isEmpty()) {
            TinoSystemEmptyStateCard(
                title = "Nada por aqui ainda",
                message = spec.emptyMessage ?: "Nenhum item encontrado.",
            )
        } else {
            TinoCardSurface(description = spec.title) {
                spec.items.forEachIndexed { index, item ->
                    TinoCatalogListItem(
                        item = TinoCatalogItemSpec(
                            icon = item.icon,
                            title = item.title,
                            context = item.context,
                            primaryText = item.primaryText,
                            secondaryText = item.secondaryText,
                            supportingText = item.statusText,
                            status = item.status,
                            onClick = item.onClick,
                        ),
                        variant = "entity",
                    )
                    if (index < spec.items.lastIndex) TinoCardDivider()
                }
            }
        }
    }
}

@Composable
private fun TinoCatalogListItem(item: TinoCatalogItemSpec, variant: String) {
    val productCatalog = variant == "products"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TinoSize.minTouch)
            .tinoClickable(onClick = item.onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TinoSystemIcon(item.icon, item.status)
        Spacer(Modifier.width(TinoSpacing.sm))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = TinoInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (productCatalog) {
                item.primaryText.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = TinoMuted, maxLines = 1)
                }
            } else {
                item.context?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = TinoMuted, maxLines = 1)
                }
                item.supportingText?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = item.status.accent(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (productCatalog) {
            item.statusText?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = item.status.accent(), fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        } else {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                item.primaryText.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = item.status.accent(), fontWeight = FontWeight.Bold, maxLines = 1)
                }
                item.secondaryText?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = TinoMuted, maxLines = 1)
                }
                item.statusText?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = item.status.accent(), maxLines = 1)
                }
            }
        }
        Icon(TinoIcons.Forward, contentDescription = "Abrir ${item.title}", tint = TinoMuted, modifier = Modifier.padding(start = TinoSpacing.xs))
    }
}

@Composable
fun TinoSystemActionListCard(
    actions: List<TinoSystemAction>,
    modifier: Modifier = Modifier,
    title: String? = null,
    supportingText: String? = null,
) {
    TinoCardFrame(modifier = modifier, description = "Ações rápidas") {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TinoInk, fontWeight = FontWeight.SemiBold)
            supportingText?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TinoMuted)
            }
            TinoCardDivider()
        }
        actions.forEachIndexed { index, action ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = TinoSize.minTouch).tinoClickable(onClick = action.onClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TinoSystemIcon(action.icon, TinoCardStatus.SUCCESS)
                Spacer(Modifier.width(TinoSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(action.title, style = MaterialTheme.typography.titleSmall, color = TinoInk)
                    Text(action.subtitle, style = MaterialTheme.typography.bodySmall, color = TinoMuted)
                }
                Icon(TinoIcons.Forward, contentDescription = "Abrir ${action.title}", tint = TinoMuted)
            }
            if (index < actions.lastIndex) HorizontalDivider(color = TinoBorder.copy(alpha = 0.55f))
        }
    }
}

data class TinoSystemAction(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@Composable
fun TinoSystemPreviewCard(
    title: String,
    rows: List<Pair<String, String>>,
    total: String,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
) {
    TinoCardFrame(modifier = modifier, description = "$title. Total $total") {
        Text(title, style = MaterialTheme.typography.titleSmall, color = TinoInk)
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = TinoMuted)
                Text(value, style = MaterialTheme.typography.bodySmall, color = TinoInk, fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(color = TinoBorder)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Total", style = MaterialTheme.typography.titleSmall, color = TinoInk, fontWeight = FontWeight.SemiBold)
            Text(total, style = MaterialTheme.typography.titleMedium, color = TinoGreen, fontWeight = FontWeight.Bold)
        }
        if (onCancel != null || onConfirm != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                onCancel?.let { TinoSecondaryButton("CANCELAR", it, Modifier.weight(1f)) }
                onConfirm?.let { TinoPrimaryButton("CONFIRMAR", it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun TinoCardTimeline(
    title: String,
    items: List<Triple<String, String, String>>,
    modifier: Modifier = Modifier,
    footerLabel: String? = "Ver histórico completo",
    onClick: (() -> Unit)? = null,
) {
    TinoCardFrame(modifier = modifier, onClick = onClick, description = title) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = TinoInk, fontWeight = FontWeight.SemiBold)
        items.forEachIndexed { index, (label, value, timestamp) ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = TinoSize.minTouch),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TinoSystemIcon(TinoIcons.Success, TinoCardStatus.SUCCESS)
                Spacer(Modifier.width(TinoSpacing.sm))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = TinoInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(timestamp, style = MaterialTheme.typography.labelSmall, color = TinoMuted)
                }
                Text(value, style = MaterialTheme.typography.labelLarge, color = TinoGreen, fontWeight = FontWeight.SemiBold)
            }
            if (index < items.lastIndex) HorizontalDivider(color = TinoBorder.copy(alpha = 0.55f))
        }
        footerLabel?.let {
            TinoCardDivider()
            TinoCardFooter(it, TinoCardStatus.SUCCESS, onClick)
        }
    }
}

@Composable
fun TinoSystemEmptyStateCard(
    title: String = "Nada por aqui ainda",
    message: String = "Assim que tiver informações, elas vão aparecer.",
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = TinoSize.cardBorder.toPx()
                drawRoundRect(
                    color = TinoBorder,
                    cornerRadius = CornerRadius(TinoSpacing.lg.toPx()),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(TinoSpacing.sm.toPx(), (TinoSpacing.sm - TinoSpacing.xs / 2).toPx())),
                    ),
                )
            }
            .semantics { contentDescription = "$title. $message" },
        shape = TinoShapes.large,
        colors = CardDefaults.cardColors(containerColor = TinoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = TinoElevation.none),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(TinoSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TinoSystemIcon(TinoIcons.Products, TinoCardStatus.NEUTRAL)
            Spacer(Modifier.width(TinoSpacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TinoInk)
                Text(message, style = MaterialTheme.typography.bodySmall, color = TinoMuted)
            }
        }
    }
}

@Composable
private fun TinoSystemIcon(icon: ImageVector, status: TinoCardStatus) {
    val color = status.accent()
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .size(TinoSize.cardIcon)
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(TinoSpacing.xs),
    )
}
