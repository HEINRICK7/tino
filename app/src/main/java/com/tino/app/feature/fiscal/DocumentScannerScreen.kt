package com.tino.app.feature.fiscal

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.tino.app.ui.components.TinoPrimaryButton
import com.tino.app.ui.components.TinoCard
import com.tino.app.ui.components.TinoTextAction
import com.tino.app.ui.components.TinoSecondaryButton
import com.tino.app.ui.components.TinoTopBar
import com.tino.app.ui.icons.TinoIcons
import com.tino.app.ui.theme.TinoGreen
import com.tino.app.ui.theme.TinoGreenLight
import com.tino.app.ui.theme.TinoMuted
import com.tino.app.ui.theme.TinoPaper
import com.tino.app.ui.theme.TinoShapes
import com.tino.app.ui.theme.TinoSize
import com.tino.app.ui.theme.TinoSpacing
import com.tino.app.ui.theme.TinoSurface
import com.tino.fiscal.core.CaptureUiState
import com.tino.fiscal.core.DocumentCaptureGuidance
import com.tino.fiscal.core.DocumentCaptureQualityGate
import com.tino.fiscal.core.DocumentFrameMetrics
import com.tino.fiscal.core.ProductImportResult
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DocumentScannerScreen(
    onBack: () -> Unit,
    onProcessed: (ProductImportResult, String?) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(activity) {
        activity?.let { cameraActivity ->
            com.tino.app.core.ui.AppOrientationController(cameraActivity).allowCameraLandscape()
            WindowCompat.setDecorFitsSystemWindows(cameraActivity.window, false)
            cameraActivity.window.statusBarColor = android.graphics.Color.TRANSPARENT
            cameraActivity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowInsetsControllerCompat(cameraActivity.window, cameraActivity.window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            activity?.let { cameraActivity ->
                WindowInsetsControllerCompat(cameraActivity.window, cameraActivity.window.decorView).show(
                    WindowInsetsCompat.Type.systemBars(),
                )
                WindowCompat.setDecorFitsSystemWindows(cameraActivity.window, true)
                cameraActivity.window.statusBarColor = TinoPaper.toArgb()
                cameraActivity.window.navigationBarColor = TinoPaper.toArgb()
                WindowInsetsControllerCompat(cameraActivity.window, cameraActivity.window.decorView).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
                com.tino.app.core.ui.AppOrientationController(cameraActivity).restorePortrait()
            }
        }
    }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var capturing by remember { mutableStateOf(false) }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var processing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf(false) }
    var frameMetrics by remember {
        mutableStateOf(DocumentFrameMetrics(false, 0f, 0f, 0f, 0))
    }
    var detectedQuad by remember { mutableStateOf<NormalizedDocumentQuad?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(capturedFile) {
        capturedFile?.let { file ->
            processing = true
            val result = processDocumentFile(
                context = context,
                file = file,
                source = com.tino.fiscal.core.ProductImportSource.DANFE_CAMERA,
            )
            processing = false
            onProcessed(result.result, result.rectifiedPath)
        }
    }

    if (!permissionGranted) {
        CameraPermissionScreen(
            onBack = onBack,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        )
        return
    }

    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }
    val captureGuidance = DocumentCaptureQualityGate.evaluate(frameMetrics)
    val captureState = when {
        capturing -> CaptureUiState.CAPTURING
        capturedFile != null -> CaptureUiState.CAPTURED
        captureGuidance is DocumentCaptureGuidance.DetectingSheet -> CaptureUiState.SEARCHING_DOCUMENT
        captureGuidance is DocumentCaptureGuidance.ReadyToCapture &&
            !frameMetrics.quadrilateralDetected -> CaptureUiState.ADJUST_FRAMING
        captureGuidance is DocumentCaptureGuidance.MoveCloser ||
            captureGuidance is DocumentCaptureGuidance.MoveFarther -> CaptureUiState.ADJUST_FRAMING
        captureGuidance is DocumentCaptureGuidance.MoreLight -> CaptureUiState.IMPROVE_LIGHT
        captureGuidance is DocumentCaptureGuidance.HoldSteady -> CaptureUiState.HOLD_STILL
        captureGuidance is DocumentCaptureGuidance.ReadyToCapture -> CaptureUiState.READY
        else -> CaptureUiState.SEARCHING_DOCUMENT
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val frameAnalyzer = remember {
        TinoDocumentFrameAnalyzer { metrics, quad ->
            mainHandler.post {
                frameMetrics = metrics
                detectedQuad = quad
            }
        }
    }

    DisposableEffect(controller, frameAnalyzer, analysisExecutor) {
        controller.setImageAnalysisAnalyzer(
            analysisExecutor,
            frameAnalyzer,
        )
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            analysisExecutor.shutdownNow()
        }
    }

    fun capturePhoto() {
        if (captureState != CaptureUiState.READY || capturing || processing || capturedFile != null) return
        captureError = false
        capturing = true
        val file = File.createTempFile("tino-danfe-", ".jpg", context.cacheDir)
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        controller.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    capturing = false
                    capturedFile = file
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    captureError = true
                }
            },
        )
    }

    LaunchedEffect(
        captureGuidance,
        captureState,
    ) {
        if (
            captureGuidance is DocumentCaptureGuidance.ReadyToCapture &&
            frameMetrics.quadrilateralDetected &&
            capturedFile == null &&
            !capturing &&
            !processing
        ) {
            delay(650)
            if (
                DocumentCaptureQualityGate.evaluate(frameMetrics) is DocumentCaptureGuidance.ReadyToCapture &&
                    frameMetrics.quadrilateralDetected
            ) {
                capturePhoto()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        TinoDocumentCameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
        TinoDocumentScannerOverlay(
            state = captureState,
            progress = DocumentCaptureQualityGate.stabilityProgress(frameMetrics),
            detectedQuad = null,
            detectedNormalizedQuad = detectedQuad,
            tableMode = false,
            modifier = Modifier.fillMaxSize(),
        )

        if (processing) {
            TinoCard(modifier = Modifier.align(Alignment.Center).padding(TinoSpacing.xl)) {
                Column(
                    Modifier.padding(TinoSpacing.screen),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(TinoSpacing.md),
                ) {
                    CircularProgressIndicator(color = TinoGreen)
                    Text("Lendo a nota…", fontWeight = FontWeight.SemiBold)
                    Text("Nada será alterado no estoque ainda.", color = TinoMuted)
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(TinoSpacing.lg),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.58f),
            contentColor = Color.White,
        ) {
            TinoTextAction("FECHAR", onBack, color = Color.White)
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(TinoSize.cameraControlBarHeight)
                .padding(horizontal = TinoSpacing.screen, vertical = TinoSpacing.md),
            shape = TinoShapes.large,
            color = Color.Black.copy(alpha = 0.62f),
            contentColor = Color.White,
        ) {
            Row(
                modifier = Modifier.padding(start = TinoSpacing.lg, end = TinoSpacing.sm, top = TinoSpacing.sm, bottom = TinoSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TinoSpacing.md),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TinoSpacing.xxs)) {
                    Text(
                        "Enquadre a nota",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        scannerGuidance(captureState, frameMetrics),
                        color = Color.White.copy(alpha = 0.74f),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = {
                        capturePhoto()
                    },
                    modifier = Modifier
                        .size(TinoSize.cameraCaptureButton)
                        .background(TinoGreen, CircleShape),
                        enabled = captureState == CaptureUiState.READY && !capturing && !processing && capturedFile == null,
                ) {
                    Icon(
                        TinoIcons.Camera,
                        contentDescription = "Capturar tabela",
                        tint = Color.White,
                        modifier = Modifier.size(TinoSize.iconProminent),
                    )
                }
            }
        }
        if (captureError) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = TinoSpacing.xl),
                shape = TinoShapes.medium,
                color = Color.Black.copy(alpha = 0.74f),
                contentColor = Color.White,
            ) {
                Text(
                    "Não consegui tirar essa foto. Tente novamente.",
                    modifier = Modifier.padding(horizontal = TinoSpacing.lg, vertical = TinoSpacing.md),
                )
            }
        }
    }
}

private fun scannerGuidance(state: CaptureUiState, metrics: DocumentFrameMetrics): String = when (state) {
    CaptureUiState.SEARCHING_DOCUMENT -> "Posicione a nota inteira"
    CaptureUiState.ADJUST_FRAMING -> when {
        !metrics.quadrilateralDetected -> "Enquadre a nota inteira"
        metrics.coverageRatio > 0.97f -> "Afaste um pouco"
        else -> "Chegue um pouco mais perto"
    }
    CaptureUiState.IMPROVE_LIGHT -> "Melhore a iluminação"
    CaptureUiState.HOLD_STILL -> "Mantenha firme"
    CaptureUiState.READY -> "Pronto — capturando"
    CaptureUiState.CAPTURING -> "Capturando..."
    CaptureUiState.CAPTURED -> "Nota capturada"
}

@Composable
private fun CameraPermissionScreen(
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = TinoPaper) {
        Column(
            modifier = Modifier.fillMaxSize().padding(TinoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(TinoSpacing.lg),
        ) {
            TinoTopBar("Escanear nota", onBack)
            Spacer(Modifier.size(TinoSpacing.md))
            Icon(TinoIcons.Camera, contentDescription = null, tint = TinoGreen, modifier = Modifier.size(TinoSize.cameraPermissionIcon))
            Text("Permita o acesso à câmera", fontWeight = FontWeight.Bold)
            Text(
                "O TINO usa a câmera para ler a nota e preparar os produtos para conferência. A entrada só é salva depois da sua confirmação.",
                color = TinoMuted,
            )
            TinoPrimaryButton("PERMITIR CÂMERA", onRequestPermission)
            TinoSecondaryButton("VOLTAR", onBack)
        }
    }
}
