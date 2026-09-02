package com.tino.app.ui.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.tino.app.feature.voice.AgenticVoiceState
import com.tino.app.interfaceadapter.a2ui.A2uiSurfaceSize
import com.tino.app.ui.components.TinoPrimaryButton
import com.tino.app.ui.components.TinoSecondaryButton
import com.tino.app.ui.components.TinoTextField
import com.tino.app.ui.components.TinoCardSurface
import com.tino.app.ui.components.TinoCardStatus
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoGreenDark
import com.tino.app.ui.theme.TinoInk
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoSize
import com.tino.app.ui.theme.TinoSpacing
import androidx.compose.material3.MaterialTheme

fun AgenticVoiceState.isVoiceBackground(): Boolean = this is AgenticVoiceState.Listening ||
    this is AgenticVoiceState.Understanding ||
    this is AgenticVoiceState.TranscriptReview

/**
 * Minimal listening/processing chrome. The transcript stays operational
 * and only surfaces when Review/Edit is actually required.
 */
@Composable
fun TinoVoiceBackgroundSurface(
    state: AgenticVoiceState,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onTranscriptEdit: () -> Unit,
    onTranscriptChange: (String) -> Unit,
    onTranscriptEditCancel: () -> Unit,
    onTranscriptContinue: () -> Unit,
    onTranscriptSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is AgenticVoiceState.Listening -> CompactVoiceStatus(
            title = "Estou ouvindo",
            supporting = "Fale normalmente. A transcrição fica em segundo plano.",
            onStop = onStop,
            onCancel = onCancel,
            modifier = modifier,
        )
        is AgenticVoiceState.Understanding -> CompactVoiceStatus(
            title = state.contextLabel,
            supporting = "Consultando seus dados",
            onStop = null,
            onCancel = onCancel,
            modifier = modifier,
        )
        is AgenticVoiceState.TranscriptReview -> TinoA2UiBottomSurface(
            size = A2uiSurfaceSize.COMPACT,
            title = "Confira o que eu entendi",
            subtitle = "Ajuste só se a transcrição estiver errada.",
            onDismiss = onCancel,
            modifier = modifier,
        ) {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                    TinoSecondaryButton("CONTINUAR", onTranscriptContinue, Modifier.weight(1f))
                    TinoSecondaryButton("EDITAR", onTranscriptEdit, Modifier.weight(1f))
                }
                TinoPrimaryButton("ENVIAR", onTranscriptSubmit)
            }
        }
        else -> Unit
    }
}

@Composable
private fun CompactVoiceStatus(
    title: String,
    supporting: String,
    onStop: (() -> Unit)?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = TinoSpacing.md, vertical = TinoSpacing.sm),
        contentAlignment = Alignment.BottomCenter,
    ) {
        TinoCardSurface(
            status = TinoCardStatus.SUCCESS,
            description = "$title. $supporting",
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TinoSize.minTouch),
                verticalArrangement = Arrangement.spacedBy(TinoSpacing.xs),
            ) {
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
                        Text(
                            title,
                            color = TinoGreenDark,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            supporting,
                            color = TinoMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TinoSpacing.sm)) {
                    TinoSecondaryButton("CANCELAR", onCancel, Modifier.weight(1f))
                    if (onStop != null) {
                        TinoPrimaryButton("PARAR", onStop, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
