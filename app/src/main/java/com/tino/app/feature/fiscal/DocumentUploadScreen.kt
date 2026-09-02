package com.tino.app.feature.fiscal

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tino.app.ui.components.TinoCard
import com.tino.app.ui.components.TinoLoadingState
import com.tino.app.ui.components.TinoSecondaryButton
import com.tino.app.ui.components.TinoTopBar
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.illustration.TinoIllustrationState
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoPaper
import com.tino.app.ui.theme.TinoSpacing
import com.tino.fiscal.core.ProductImportResult

@Composable
fun DocumentUploadScreen(
    uri: Uri,
    onBack: () -> Unit,
    onProcessed: (ProductImportResult, String?) -> Unit,
) {
    val context = LocalContext.current
    var processing by remember(uri) { mutableStateOf(true) }

    LaunchedEffect(uri) {
        val processed = processDocumentUri(context, uri)
        processing = false
        onProcessed(processed.result, processed.rectifiedPath)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(TinoPaper).padding(TinoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(TinoSpacing.lg),
    ) {
        TinoTopBar("Ler foto", onBack)
        if (processing) {
            TinoLoadingState(
                icon = TinoIcons.Document,
                title = "Lendo a nota…",
                message = "O TINO está procurando os produtos. Nada será alterado no estoque.",
                illustrationState = TinoIllustrationState.SEARCHING,
            )
        } else {
            TinoCard {
                Text("Nota lida", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text("Confira os produtos antes de registrar a entrada.", color = TinoMuted)
            }
        }
        if (processing) {
            TinoSecondaryButton("CANCELAR", onBack)
        }
    }
}
