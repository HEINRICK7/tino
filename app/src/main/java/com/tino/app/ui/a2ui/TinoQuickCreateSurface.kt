package com.tino.app.ui.a2ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceSize
import com.tino.app.ui.components.TinoCardDivider
import com.tino.app.ui.components.TinoCardSurface
import com.tino.app.ui.components.tinoClickable
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoGreenTint
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoSize
import com.tino.app.ui.theme.TinoSpacing

/** Quick creation stays separate from the mascot: assist versus add data. */
data class TinoQuickCreateOption(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit,
)

@Composable
fun TinoCreateBottomSheet(
    options: List<TinoQuickCreateOption>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    TinoA2UiBottomSurface(
        size = if (options.size > 4) A2uiSurfaceSize.LARGE else A2uiSurfaceSize.MEDIUM,
        title = "O que você quer adicionar?",
        subtitle = "Escolha uma ação para continuar",
        onDismiss = onDismiss,
        modifier = modifier,
        scrollContent = true,
    ) {
        TinoCardSurface(description = "Ações rápidas para adicionar dados ao comércio") {
            options.forEachIndexed { index, option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = TinoSize.minTouch)
                        .tinoClickable {
                            onDismiss()
                            option.onClick()
                        }
                        .padding(vertical = TinoSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(TinoSize.cardIcon)
                            .background(TinoGreenTint, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(option.icon, contentDescription = null, tint = TinoGreen)
                    }
                    Spacer(Modifier.width(TinoSpacing.md))
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(TinoSpacing.xxs),
                    ) {
                        Text(
                            option.title,
                            color = TinoInk,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(option.description, color = TinoMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(TinoIcons.Forward, contentDescription = null, tint = TinoMuted)
                }
                if (index < options.lastIndex) TinoCardDivider()
            }
        }
    }
}

@Composable
fun TinoQuickCreateSurface(
    options: List<TinoQuickCreateOption>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TinoCreateBottomSheet(options = options, onDismiss = onDismiss, modifier = modifier)
}
