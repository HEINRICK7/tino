package com.tino.app.feature.nfce

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import com.tino.app.domain.nfce.NfceAccessKey
import com.tino.app.domain.nfce.NfceQrAccessKeyExtractor
import com.tino.app.domain.nfce.PurchaseDocument
import com.tino.app.domain.nfce.PurchaseDocumentPreview
import com.tino.app.domain.nfce.PurchaseReceipt
import com.tino.app.domain.nfce.SefazPiNfceParser
import com.tino.app.feature.fiscal.TinoDocumentCameraPreview
import com.tino.app.domain.agent.TinoPresenceMode
import com.tino.app.ui.components.TinoMascotPresence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun NfceCaptureScreen(
    onBack: () -> Unit,
    onDocumentCaptured: suspend (PurchaseDocument) -> PurchaseDocumentPreview? = { null },
    onPreviewConfirmed: suspend (PurchaseDocumentPreview) -> PurchaseReceipt? = { null },
    viewModel: NfcePreviewViewModel = hiltViewModel(),
) {
    var state by remember { mutableStateOf<NfceCaptureState>(NfceCaptureState.Preparing) }
    val scope = rememberCoroutineScope()

    fun prepare() {
        state = NfceCaptureState.Preparing
        scope.launch {
            runCatching { viewModel.ensureReadyForNfce() }
                .onSuccess { state = NfceCaptureState.Scanning }
                .onFailure { state = NfceCaptureState.Error(it.message ?: "Não foi possível preparar a NFC-e.") }
        }
    }

    LaunchedEffect(Unit) { prepare() }

    when (val current = state) {
        NfceCaptureState.Preparing -> NfceCapturePreparing(onBack)
        NfceCaptureState.Scanning -> NfceQrScanner(
            onBack = onBack,
            onQrContent = { raw ->
                val candidate = NfceQrAccessKeyExtractor.extract(raw)
                if (candidate == null) {
                    state = NfceCaptureState.Error("Não foi possível identificar a chave desta NFC-e.")
                } else {
                    runCatching { NfceAccessKey.normalizeAndValidate(candidate) }
                        .onSuccess { state = NfceCaptureState.Consulting(it.accessKey) }
                        .onFailure { state = NfceCaptureState.Error(it.message ?: "Chave de NFC-e inválida.") }
                }
            },
        )
        is NfceCaptureState.Consulting -> NfceSefazPage(
            accessKey = current.accessKey,
            onBack = onBack,
            onDocumentHtml = { html ->
                // HTML is consumed only on-device and never forwarded to TINO Backend.
                scope.launch {
                    val parsed = withContext(Dispatchers.Default) {
                        runCatching { SefazPiNfceParser().parse(html, current.accessKey) }
                    }
                    parsed.onSuccess {
                        runCatching { onDocumentCaptured(it) }
                            .onSuccess { preview -> state = NfceCaptureState.Success(it, preview) }
                            .onFailure { error -> state = NfceCaptureState.Error(error.message ?: "Não foi possível preparar a prévia da NFC-e.") }
                    }.onFailure { state = NfceCaptureState.Error(it.message ?: "Não foi possível ler a NFC-e.") }
                }
            },
        )
        is NfceCaptureState.Success -> NfceDocumentResult(current.document, current.preview, onBack, onPreviewConfirmed)
        is NfceCaptureState.Error -> NfceCaptureError(current.message, onBack, ::prepare)
    }
}

private sealed interface NfceCaptureState {
    data object Preparing : NfceCaptureState
    data object Scanning : NfceCaptureState
    data class Consulting(val accessKey: String) : NfceCaptureState
    data class Success(val document: PurchaseDocument, val preview: PurchaseDocumentPreview?) : NfceCaptureState
    data class Error(val message: String) : NfceCaptureState
}

@Composable
private fun NfceCapturePreparing(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Preparando a entrada de NFC-e", style = MaterialTheme.typography.titleLarge)
        Text("Validando sua sessão e o comércio autorizado antes de abrir a câmera.")
        Button(onClick = onBack) { Text("Voltar") }
    }
}

internal fun nfcePreviewExplanation(preview: PurchaseDocumentPreview): List<String> = buildList {
    add("Encontrei ${preview.summary.items} produtos nessa compra.")
    if (preview.summary.matched > 0) {
        add("${preview.summary.matched} já estavam cadastrados e serão atualizados no estoque.")
    }
    if (preview.summary.newProducts > 0) {
        add("${preview.summary.newProducts} são novos e serão cadastrados.")
    }
    if (preview.summary.needsReview > 0) {
        add("Preciso confirmar ${preview.summary.needsReview} produto(s) antes de terminar.")
    }
}

@Composable
private fun NfceQrScanner(
    onBack: () -> Unit,
    onQrContent: (String) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("A câmera é necessária para ler o QR Code da NFC-e.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onBack) { Text("Voltar") }
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Permitir câmera") }
            }
        }
        return
    }
    val controller = remember(context) {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_ANALYSIS) }
    }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val delivered = remember { AtomicBoolean(false) }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    DisposableEffect(controller, scanner, executor) {
        controller.setImageAnalysisAnalyzer(executor, QrAnalyzer(scanner, mainExecutor) { raw ->
            if (delivered.compareAndSet(false, true)) onQrContent(raw)
        })
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            executor.shutdownNow()
            scanner.close()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        TinoDocumentCameraPreview(controller, Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Aponte para o QR Code da NFC-e do Piauí", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        }
    }
}

private class QrAnalyzer(
    private val scanner: BarcodeScanner,
    private val mainExecutor: java.util.concurrent.Executor,
    private val onValue: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }
        scanner.process(InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees))
            .addOnSuccessListener(mainExecutor) { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onValue) }
            .addOnCompleteListener { image.close() }
    }
}

@Composable
private fun NfceSefazPage(
    accessKey: String,
    onBack: () -> Unit,
    onDocumentHtml: (String) -> Unit,
) {
    var loading by remember { mutableStateOf(false) }
    val currentHtml by rememberUpdatedState(onDocumentHtml)
    val handler = remember { Handler(Looper.getMainLooper()) }
    val attempted = remember { AtomicBoolean(false) }
    val bridge = remember {
        object {
            @JavascriptInterface
            fun postMessage(message: String) {
                handler.post {
                    val json = runCatching { JSONObject(message) }.getOrNull() ?: return@post
                    when (json.optString("type")) {
                        "query-submit" -> loading = true
                        "result-dom" -> json.optString("html").takeIf { it.isNotBlank() }?.let(currentHtml)
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (loading) "Lendo sua compra" else "Consulta NFC-e", style = MaterialTheme.typography.titleLarge)
            Text(if (loading) "Buscando os produtos..." else "Resolva o hCaptcha e toque em Consultar.")
            Text("Chave: $accessKey", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onBack) { Text("Cancelar") }
        }
        AndroidView(
            modifier = Modifier.weight(1f).alpha(if (loading) 0f else 1f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    addJavascriptInterface(bridge, "TinoNfceBridge")
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            !NfceSefazOfficial.allowsWebViewNavigation(request.isForMainFrame, request.url.toString())

                        override fun onPageFinished(view: WebView, url: String) {
                            if (!NfceSefazOfficial.allowsTopNavigation(url)) return
                            if (attempted.compareAndSet(false, true)) view.evaluateJavascript(autofillAccessKeyScript(accessKey), null)
                            view.evaluateJavascript(monitorNfceQuerySubmitScript, null)
                            view.evaluateJavascript(detectNfceResultScript, null)
                        }
                    }
                    loadUrl(NfceSefazOfficial.URL)
                }
            },
        )
    }
}

@Composable
private fun NfceDocumentResult(
    document: PurchaseDocument,
    preview: PurchaseDocumentPreview?,
    onBack: () -> Unit,
    onPreviewConfirmed: suspend (PurchaseDocumentPreview) -> PurchaseReceipt?,
) {
    val scope = rememberCoroutineScope()
    var receipt by remember { mutableStateOf<PurchaseReceipt?>(null) }
    var confirmationError by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }
    val hasReview = preview?.matches?.any { it.status == com.tino.app.domain.nfce.PurchaseDocumentMatch.Status.REVIEW_REQUIRED } == true
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            when {
                receipt != null -> "Entrada confirmada"
                preview == null -> "Compra encontrada"
                else -> "Prévia da compra pronta"
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        if (preview != null) {
            TinoMascotPresence(
                mode = when {
                    receipt != null -> TinoPresenceMode.COMPLETED
                    confirming -> TinoPresenceMode.THINKING
                    else -> TinoPresenceMode.WAITING_FOR_USER
                },
            )
            Text("O documento foi enviado ao TINO para conferência. Status: ${preview.status}")
            nfcePreviewExplanation(preview).forEach { explanation -> Text(explanation) }
        }
        receipt?.let {
            Text("Pronto. Atualizei o estoque e guardei os preços desta compra.")
            Text("Recibo: ${it.receiptId}")
        }
        confirmationError?.let { Text(it, color = Color.Red) }
        Text(document.issuer.name ?: "Estabelecimento não informado")
        Text("${document.items.size} produtos · Total ${document.total ?: "não informado"}")
        document.items.forEach { item ->
            val match = preview?.matches?.firstOrNull { it.lineNumber == item.lineNumber }
            Text("${item.lineNumber}. ${item.description} — ${item.quantity} ${item.unit ?: ""} · ${item.totalPrice ?: "valor não informado"}")
            if (match != null) {
                Text("Correspondência: ${match.status}${match.candidateName?.let { " · $it" }.orEmpty()}")
            }
        }
        if (preview == null || receipt != null) {
            Button(onClick = onBack) { Text("Concluir") }
        } else if (hasReview) {
            Text("É necessário revisar os produtos com dúvida antes de confirmar a entrada.")
            Button(onClick = onBack) { Text("Voltar") }
        } else {
            Button(
                enabled = !confirming,
                onClick = {
                    confirming = true
                    confirmationError = null
                    scope.launch {
                        runCatching { onPreviewConfirmed(preview) }
                            .onSuccess { receipt = it ?: error("O backend não confirmou a entrada.") }
                            .onFailure { confirmationError = it.message ?: "Não foi possível confirmar a entrada." }
                        confirming = false
                    }
                },
            ) { Text(if (confirming) "Confirmando..." else "Confirmar entrada") }
        }
    }
}

@Composable
private fun NfceCaptureError(message: String, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Não foi possível ler a NFC-e", style = MaterialTheme.typography.titleLarge)
        Text(message)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) { Text("Tentar novamente") }
            Button(onClick = onBack) { Text("Voltar") }
        }
    }
}
