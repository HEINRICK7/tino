package com.tino.app.core.speech

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the local Gemma model artifact without making the domain know about files or Android.
 *
 * The official .task artifact is bundled in the APK assets and copied to private storage on
 * first use, so the first-run experience does not depend on a network download.
 */
@Singleton
class GemmaModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val modelFile: File = context.filesDir
        .resolve("models")
        .resolve(MODEL_FILE_NAME)

    suspend fun availableModelPath(): String? = withContext(Dispatchers.IO) {
        modelFile
            .takeIf { it.isFile && it.length() > 0L }
            ?.absolutePath
            ?: copyBundledModelIfPresent()
    }

    fun expectedModelPath(): String = modelFile.absolutePath

    private fun copyBundledModelIfPresent(): String? {
        val temporaryFile = File(modelFile.parentFile, "${modelFile.name}.part")
        return runCatching {
            modelFile.parentFile?.mkdirs()
            context.assets.open(BUNDLED_ASSET_PATH).use { input ->
                temporaryFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            check(temporaryFile.length() > 0L)
            check(temporaryFile.renameTo(modelFile))
            modelFile.absolutePath
        }.getOrElse {
            temporaryFile.delete()
            null
        }
    }

    companion object {
        const val MODEL_FILE_NAME = "gemma3-1b-it-int4.task"
        private const val BUNDLED_ASSET_PATH = "models/$MODEL_FILE_NAME"
    }
}
