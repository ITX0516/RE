package com.badukai.next.engine

import android.content.Context
import com.badukai.next.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads and manages the 6b KataGo model file.
 * Uses a dedicated data directory: Android/data/<pkg>/files/models/
 */
object ModelManager {
    private const val TAG = "ModelManager"

    const val MODEL_URL = "https://media.katagotraining.org/uploaded/networks/models/kata1/kata1-b6c96-s175395328-d26788732.txt.gz"
    const val MODEL_FILENAME = "kata1-b6c96-s175395328-d26788732.txt.gz"
    const val MODEL_DISPLAY_NAME = "6b"

    /**
     * Check if the model file already exists
     */
    fun isModelAvailable(context: Context): Boolean {
        val dir = getModelDir(context)
        return File(dir, MODEL_FILENAME).exists().also {
            AppLogger.i(TAG, "Model available: $it, path: ${File(dir, MODEL_FILENAME).absolutePath}")
        }
    }

    /**
     * Download the model file from katagotraining.org
     * Runs on IO dispatcher. Returns the local file path on success.
     */
    suspend fun downloadModel(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dir = getModelDir(context)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, MODEL_FILENAME)

            // Already exists?
            if (file.exists() && file.length() > 0) {
                AppLogger.i(TAG, "Model already exists, size: ${file.length()}")
                return@withContext Result.success(file.absolutePath)
            }

            AppLogger.i(TAG, "Downloading model from $MODEL_URL")
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.connect()

            val contentLength = connection.contentLength
            AppLogger.i(TAG, "Content-Length: $contentLength")

            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            AppLogger.i(TAG, "Download complete: ${file.absolutePath}, size: ${file.length()}")
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }

    /**
     * Get the directory where models are stored.
     * Uses Context.filesDir which is app-private and persists across updates.
     * Will be removed on app uninstall (standard Android behavior).
     */
    private fun getModelDir(context: Context): File {
        return File(context.filesDir, "models")
    }
}
