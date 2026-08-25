package com.tino.app.feature.fiscal

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.tino.app.ui.components.TinoCard
import com.tino.app.ui.components.TinoSecondaryButton
import com.tino.app.ui.components.TinoTopBar
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoMuted
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
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TinoTopBar("Ler foto", onBack)
        TinoCard {
            if (processing) {
                CircularProgressIndicator(color = TinoGreen)
                Spacer(Modifier.height(14.dp))
                Text("Lendo a nota…", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(
                    "O TINO está procurando os produtos. Nada será alterado no estoque.",
                    color = TinoMuted,
                )
            } else {
                Text("Nota lida", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text("Confira os produtos antes de registrar a entrada.", color = TinoMuted)
            }
        }
        if (processing) {
            TinoSecondaryButton("CANCELAR", onBack)
        }
    }
}
