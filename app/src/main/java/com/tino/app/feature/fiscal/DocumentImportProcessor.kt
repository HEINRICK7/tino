package com.tino.app.feature.fiscal

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tino.fiscal.core.DocumentImage
import com.tino.fiscal.core.ProductImportResult
import com.tino.fiscal.core.ProductImportSource
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ProcessedDocumentCapture(
    val result: ProductImportResult,
    val rectifiedPath: String?,
)

/** Shared post-capture pipeline for camera photos and uploaded DANFE images. */
internal suspend fun processDocumentFile(
    context: Context,
    file: File,
    source: ProductImportSource,
): ProcessedDocumentCapture = withContext(Dispatchers.IO) {
    processDocumentBytes(context, file.readBytes(), source)
}

internal suspend fun processDocumentUri(
    context: Context,
    uri: Uri,
): ProcessedDocumentCapture = withContext(Dispatchers.IO) {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    if (bytes == null) {
        return@withContext ProcessedDocumentCapture(
            ProductImportResult.Unavailable(
                reason = "Não consegui abrir essa imagem. Escolha uma foto JPG ou PNG da DANFE.",
                source = ProductImportSource.DANFE_IMAGE,
            ),
            null,
        )
    }
    // Keep a local evidence copy. The corrected/cropped image is derived from
    // this file and can be regenerated without asking the user to select again.
    val original = File.createTempFile("tino-danfe-upload-original-", ".img", context.cacheDir)
    original.writeBytes(bytes)
    processDocumentBytes(context, bytes, ProductImportSource.DANFE_IMAGE)
}

private suspend fun processDocumentBytes(
    context: Context,
    bytes: ByteArray,
    source: ProductImportSource,
): ProcessedDocumentCapture {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: return ProcessedDocumentCapture(
            ProductImportResult.Unavailable(
                reason = "Não foi possível abrir a imagem da nota.",
                source = source,
            ),
            null,
        )

    val quad = BitmapDocumentQuadDetector().detect(bitmap)
    val rectified = quad?.let { DocumentPerspectiveRectifier.rectify(bitmap, it) }
    if (quad == null || rectified == null) {
        bitmap.recycle()
        return ProcessedDocumentCapture(
            ProductImportResult.Unavailable(
                reason = "Não consegui encontrar as bordas da DANFE. Escolha uma imagem em que a nota esteja visível.",
                source = source,
            ),
            null,
        )
    }

    val cropped = DocumentContentCropper.trimTableMargins(rectified)
    val processedFile = File.createTempFile("tino-danfe-cropped-", ".jpg", context.cacheDir)
    FileOutputStream(processedFile).use { output ->
        cropped.compress(Bitmap.CompressFormat.JPEG, 95, output)
    }
    val processedBytes = processedFile.readBytes()
    val image = DocumentImage(
        bytes = processedBytes,
        width = cropped.width,
        height = cropped.height,
        mimeType = "image/jpeg",
        evidenceHashSha256 = sha256(processedBytes),
    )
    val result = MlKitDanfeVisionAdapter(context, source).extractProducts(image)
    if (cropped !== rectified) cropped.recycle()
    rectified.recycle()
    bitmap.recycle()
    return ProcessedDocumentCapture(result, processedFile.absolutePath)
}

private fun sha256(bytes: ByteArray): String = MessageDigest
    .getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }
