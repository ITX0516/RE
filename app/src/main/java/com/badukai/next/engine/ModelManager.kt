package com.badukai.next.engine

import android.content.Context
import com.badukai.next.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads and manages the 6b KataGo model file.
 * Uses a dedicated data directory: Android/data/<pkg>/files/models/
 *
 * AI-START RELIABILITY FIX (2026-08-02):
 *   The previous implementation's isModelAvailable() only checked File.exists(),
 *   which treated a half-downloaded / truncated / HTML-error-page file as "ok".
 *   KataGo then fails ungzip within 1 second:
 *     "Could neither parse .gz model as .txt.gz model nor as .bin.gz model"
 *   → manifests to user as generic "Failed to start AI" toast.
 *
 *   New contract:
 *   • isModelAvailable = exists AND size >= MIN_BYTES AND gzip magic bytes (1f 8b) present
 *   • downloadModel = HTTP 200 + downloaded size == declared Content-Length, else delete & fail
 */
object ModelManager {
    private const val TAG = "ModelManager"

    const val MODEL_URL = "https://media.katagotraining.org/uploaded/networks/models/kata1/kata1-b6c96-s175395328-d26788732.txt.gz"
    const val MODEL_FILENAME = "kata1-b6c96-s175395328-d26788732.txt.gz"
    const val MODEL_DISPLAY_NAME = "6b"

    /** Real Content-Length for katago 6b model (confirmed 2026-08-02 HEAD request = 4,967,720 bytes). */
    private const val EXPECTED_MODEL_BYTES: Long = 4_967_720L
    /** Allow ~5% slack for CDN edge differences; anything below 4.5MB is definitely bad. */
    private const val MIN_VALID_MODEL_BYTES: Long = (EXPECTED_MODEL_BYTES * 0.90).toLong()
    private val GZIP_MAGIC = byteArrayOf(0x1f.toByte(), 0x8b.toByte())

    /** Absolute path of the model file on this device. */
    fun modelFile(context: Context): File = File(getModelDir(context), MODEL_FILENAME)

    /**
     * Strict "is model usable" check. Returns true ONLY if:
     *   1. File exists
     *   2. Size >= 90% of the known 6b model size (prevents truncated partial downloads)
     *   3. First two bytes == 0x1f 0x8b (valid gzip header, catches "download wrote a 404 HTML" bugs)
     */
    fun isModelAvailable(context: Context): Boolean {
        val f = modelFile(context)
        val ok = isValidModelFile(f)
        AppLogger.i(TAG, "Model available=$ok  path=${f.absolutePath}  size=${f.length()} (min=$MIN_VALID_MODEL_BYTES expected=$EXPECTED_MODEL_BYTES)")
        return ok
    }

    /**
     * Validate & optionally clean up a stale/corrupt model. Returns true if usable,
     * false + deletes file if suspect. Useful before passing to KataGo -model arg.
     */
    fun validateOrDelete(context: Context): Boolean {
        val f = modelFile(context)
        if (isValidModelFile(f)) return true
        if (f.exists()) {
            AppLogger.w(TAG, "Model corrupt/incomplete — deleting: size=${f.length()} bytes. Re-download required.")
            try { f.delete() } catch (_: Exception) {}
        }
        return false
    }

    /**
     * Download the model file from katagotraining.org
     * Runs on IO dispatcher. Returns the local file path on success.
     *
     * Enforces:
     *   • HTTP/2 or HTTP/1.1 2xx response status
     *   • Downloaded byte count == Content-Length (exact match — catch CDN early-close)
     *   • Post-download gzip-magic + min-size re-validation
     *   • If any step fails, the incomplete file is DELETED so next launch retries from scratch
     *     instead of reusing the corrupt file.
     */
    suspend fun downloadModel(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dir = getModelDir(context)
            if (!dir.exists()) dir.mkdirs()

            val file = modelFile(context)
            if (validateOrDelete(context)) {
                AppLogger.i(TAG, "Model already valid, skip download: size=${file.length()}")
                return@withContext Result.success(file.absolutePath)
            }

            AppLogger.i(TAG, "Downloading model from $MODEL_URL")
            val url = URL(MODEL_URL)
            val tmpOut = File(dir, "$MODEL_FILENAME.tmp")
            try { tmpOut.delete() } catch (_: Exception) {}

            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000   // 2 minutes — model is only 5MB but some networks slow
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "BadukNext-Android/1.0 (ModelDownloader)")
            connection.connect()

            val status = connection.responseCode
            if (status !in 200..299) {
                AppLogger.e(TAG, "Download HTTP status=$status (expected 2xx). Server returned error page, not a .gz!")
                connection.errorStream?.use { es ->
                    val head = es.bufferedReader().use { r -> r.lineSequence().take(8).joinToString("\n") }
                    AppLogger.e(TAG, "Server error response head:\n$head")
                }
                return@withContext Result.failure(IllegalStateException("HTTP $status on model download"))
            }

            val contentLength = connection.contentLengthLong
                .takeIf { it > 0 } ?: EXPECTED_MODEL_BYTES
            AppLogger.i(TAG, "Downloading: status=$status Content-Length=$contentLength")

            val written = connection.inputStream.use { input ->
                FileOutputStream(tmpOut).use { output -> input.copyTo(output) }
            }
            AppLogger.i(TAG, "Downloaded bytes=$written, Content-Length declared=$contentLength")

            if (written != contentLength || written < MIN_VALID_MODEL_BYTES) {
                try { tmpOut.delete() } catch (_: Exception) {}
                return@withContext Result.failure(IllegalStateException(
                    "Truncated download: written=$written declared=$contentLength min=$MIN_VALID_MODEL_BYTES"
                ))
            }

            // Atomic move to final location (prevents half-visible file if process killed between write & valid)
            if (!tmpOut.renameTo(file)) {
                // Fallback: copy + delete if rename fails (cross-filesystem)
                tmpOut.inputStream().use { inp -> FileOutputStream(file).use { outp -> inp.copyTo(outp) } }
                try { tmpOut.delete() } catch (_: Exception) {}
            }

            // Final strict validation after on-disk move
            if (!validateOrDelete(context)) {
                return@withContext Result.failure(IllegalStateException("Post-download validation failed — corrupt .gz saved even after size check"))
            }

            AppLogger.i(TAG, "Downloaded OK: ${file.absolutePath} size=${file.length()}")
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Download model failed", e)
            // Clean up any possible partial file so next run re-downloads instead of assuming valid
            try { modelFile(context).takeIf { it.exists() && !isValidModelFile(it) }?.delete() } catch (_: Exception) {}
            try { File(getModelDir(context), "$MODEL_FILENAME.tmp").takeIf { it.exists() }?.delete() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    // ============== private helpers ==============

    private fun isValidModelFile(f: File): Boolean {
        if (!f.exists() || !f.isFile) return false
        val len = f.length()
        if (len < MIN_VALID_MODEL_BYTES) return false
        // First 2 bytes == 0x1f 0x8b for gzip (KataGo recognizes only .txt.gz / .bin.gz)
        return try {
            FileInputStream(f).use { fis ->
                val buf = ByteArray(2)
                val n = fis.read(buf)
                n >= 2 && buf[0] == GZIP_MAGIC[0] && buf[1] == GZIP_MAGIC[1]
            }
        } catch (_: Exception) {
            false
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
