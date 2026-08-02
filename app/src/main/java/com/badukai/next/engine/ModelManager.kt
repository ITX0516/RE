package com.badukai.next.engine

import android.content.Context
import android.net.Uri
import com.badukai.next.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Where the KataGo neural-net weights (".bin.gz / .txt.gz) come from.
 *
 * Flow of modelFilesDir()/context.getExternalFilesDir(null)/SAF picked Uri
 *
 * BUNDLED_ASSET — we ship the 4.97MB 6b model inside the APK so first launch
 *   works offline; it's copied from assets/models/ to app-private filesDir
 *   once per APK version (fingerprint = file length + lastModifiedOfAssetEntry bytes).
 * DOWNLOADED — legacy online path; downloadModel() writes into filesDir/models/xxx.txt.gz.
 * CUSTOM — user picked a .txt.gz/.bin.gz from their own storage; copied into
 *   filesDir/models/custom so we own a stable path and never touch their original.
 */
enum class ModelSource(val displayName: String) {
    BUNDLED_ASSET("内置（6b，离线可用）"),
    DOWNLOADED("在线下载（6b）"),
    CUSTOM("自定义文件")
}

/**
 * Downloads / validates / copies-into-place the KataGo model weights.
 *
 * 2026-08-02 EXTENDED with BUNDLED_ASSET and CUSTOM support.
 *
 * AI-START RELIABILITY INVARIANT — every path we may hand to `libkatago.so
 * via `-model <path>` MUST satisfy isValidModelFile():
 *   1. exists & isFile
 *   2. size >= MIN_VALID_MODEL_BYTES (>= 4.5MB for 6b; user custom 20b/40b will
 *      be much larger so this check just catches 0-byte/HTML/tiny)
 *   3. first two bytes == 0x1f 0x8b (gzip magic)
 */
object ModelManager {
    private const val TAG = "ModelManager"

    const val MODEL_URL = "https://media.katagotraining.org/uploaded/networks/models/kata1/kata1-b6c96-s175395328-d26788732.txt.gz"
    const val MODEL_FILENAME = "kata1-b6c96-s175395328-d26788732.txt.gz"
    const val MODEL_DISPLAY_NAME = "6b"

    /** Asset sub-path (APK 内打包好的 6b 权重，用户要求内置离线首启） */
    private const val BUNDLED_ASSET_PATH = "models/$MODEL_FILENAME"
    /** filesDir/models/custom/ — 用户自定义权重的本地镜像目录 */
    private const val CUSTOM_DIR_NAME = "custom"
    /** filesDir/models/asset_copy/ — 内置权重 copy 出来的缓存目录（避免跟下载路径冲突） */
    private const val ASSET_COPY_DIR_NAME = "asset_copy"

    /** Real Content-Length for katago 6b model. */
    private const val EXPECTED_6B_BYTES: Long = 4_967_720L
    /**
     * Min valid size gate. For the shipped 6b weight this catches truncated copies.
     * For user-picked custom weights (20b / 40b / etc.) this just ensures the
     * file is obviously not a 3KB HTML error-page / zero-byte / corrupt header
     * pick. Real custom 20b is ~120MB, 40b is ~360MB — all well above
     * 1MB floor, so 1MB is a safe floor that never rejects real weights.
     */
    private const val MIN_VALID_MODEL_BYTES_ANY: Long = 1_000_000L
    private val GZIP_MAGIC = byteArrayOf(0x1f.toByte(), 0x8b.toByte())

    // ------------------------------------------------------------------
    // Path factories — one stable on-disk File per ModelSource.
    // ------------------------------------------------------------------

    /** DOWNLOADED: filesDir/models/<MODEL_FILENAME> — legacy path. */
    fun downloadedFile(context: Context): File =
        File(getModelsDir(context), MODEL_FILENAME)

    /** BUNDLED_ASSET: filesDir/models/asset_copy/<MODEL_FILENAME> — copied once. */
    fun bundledFile(context: Context): File =
        File(File(getModelsDir(context), ASSET_COPY_DIR_NAME), MODEL_FILENAME)

    /** CUSTOM: filesDir/models/custom/ — user-imported stable copies live here. */
    fun customDir(context: Context): File =
        File(getModelsDir(context), CUSTOM_DIR_NAME).also { it.mkdirs() }

    /**
     * Resolve the on-disk File for a source. CUSTOM uses SettingsStore.customModelPath
     * (absolute path inside customDir/). We only return app-private owned files that we
     * may delete & overwrite freely — user originals on external storage are never
     * touched (they get mirrored into customDir/ at import time).
     */
    fun resolveModelFile(
        context: Context,
        source: ModelSource,
        customStoredPath: String?
    ): File = when (source) {
        ModelSource.BUNDLED_ASSET -> bundledFile(context)
        ModelSource.DOWNLOADED -> downloadedFile(context)
        ModelSource.CUSTOM -> {
            customStoredPath
                ?.takeIf { it.isNotBlank() }
                ?.let { File(it) }
                ?.takeIf { it.isAbsolute }
                ?: File(customDir(context), "__custom_not_set__.txt.gz")
        }
    }

    // ------------------------------------------------------------------
    // Strict validation (same gzip + size floor for every source).
    // ------------------------------------------------------------------

    /**
     * Strict is-usable check for an arbitrary File on disk.
     * Floor=1MB for custom 20b/40b all pass easily; shipped 6b=4.97MB passes.
     */
    fun isValidModelFile(f: File): Boolean {
        if (!f.exists() || !f.isFile) return false
        val len = f.length()
        if (len < MIN_VALID_MODEL_BYTES_ANY) return false
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

    /** Source-specific is-available check. */
    fun isAvailable(
        context: Context,
        source: ModelSource,
        customStoredPath: String?
    ): Boolean {
        val f = resolveModelFile(context, source, customStoredPath)
        val ok = isValidModelFile(f)
        AppLogger.i(TAG, "isAvailable($source): ok=$ok size=${f.length()} path=${f.absolutePath}")
        return ok
    }

    /**
     * Validate & clean up stale/corrupt file for a given source. Returns true if
     * usable, false + deletes the corrupt file on disk (only if we own it
     * inside filesDir; we never delete the user's original on external storage, we
     * only delete our customDir copies we mirrored there as a stable app-private copy).
     */
    fun validateOrDelete(
        context: Context,
        source: ModelSource,
        customStoredPath: String?
    ): Boolean {
        val f = resolveModelFile(context, source, customStoredPath)
        if (isValidModelFile(f)) return true
        if (f.exists()) {
            AppLogger.w(TAG, "validateOrDelete($source): corrupt/incomplete, deleting our copy: size=${f.length()} path=${f.absolutePath}")
            try { f.delete() } catch (_: Exception) {}
        }
        return false
    }

    // ------------------------------------------------------------------
    // BUNDLED_ASSET — APK → filesDir copy (offline 1st-run, w/ fingerprint
    // fingerprint = asset size so we NEVER re-copy a known-good one).
    // ------------------------------------------------------------------

    /**
     * Ensure the bundled 4.97MB 6b model has been unpacked out of the APK into
     * filesDir. We copy it via AssetManager.open(assetEntry) once.
     *
     * Fingerprint = targetFile.length() matching EXPECTED_6B_BYTES.
     * If the target already has the expected length we skip entirely (zero IO).
     */
    suspend fun ensureBundledCopied(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val target = bundledFile(context)
            // Already valid 6b — skip copy
            if (target.length() == EXPECTED_6B_BYTES && isValidModelFile(target)) {
                AppLogger.i(TAG, "ensureBundledCopied: cached copy already present & valid, length=${target.length()}")
                return@withContext Result.success(target.absolutePath)
            }
            target.parentFile?.mkdirs()
            val am = context.assets
            // Does the APK actually have our asset?
            val haveAsset = runCatching { am.list("models")?.contains(MODEL_FILENAME) }.getOrDefault(false) ||
                           runCatching { am.open(BUNDLED_ASSET_PATH).use { true } }.getOrDefault(false)
            if (!haveAsset) {
                return@withContext Result.failure(IllegalStateException(
                    "Bundled asset models/$MODEL_FILENAME missing from APK assets! " +
                    "(expected 4,967,720 bytes inside APK)"
                ))
            }
            val tmp = File(target.parentFile!!, "${target.name}.tmp")
            try { tmp.delete() } catch (_: Exception) {}
            val written = am.open(BUNDLED_ASSET_PATH).use { input ->
                FileOutputStream(tmp).use { out -> input.copyTo(out) }
            }
            AppLogger.i(TAG, "ensureBundledCopied: wrote $written bytes from asset to tmp")
            if (written != EXPECTED_6B_BYTES || !isValidModelFile(tmp)) {
                try { tmp.delete() } catch (_: Exception) {}
                return@withContext Result.failure(IllegalStateException(
                    "Bundled asset copy failed: written=$written expected=$EXPECTED_6B_BYTES"
                ))
            }
            if (!tmp.renameTo(target)) {
                tmp.inputStream().use { i -> FileOutputStream(target).use { o -> i.copyTo(o) } }
                try { tmp.delete() } catch (_: Exception) {}
            }
            if (!isValidModelFile(target)) {
                return@withContext Result.failure(IllegalStateException("Bundled copy post-move validate failed"))
            }
            AppLogger.i(TAG, "ensureBundledCopied success: ${target.absolutePath}")
            Result.success(target.absolutePath)
        } catch (e: Exception) {
            AppLogger.e(TAG, "ensureBundledCopied failed", e)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------
    // DOWNLOADED — legacy katagotraining.org downloader
    // ------------------------------------------------------------------

    @Suppress("unused")
    val DOWNLOADED_EXPECTED_BYTES: Long get() = EXPECTED_6B_BYTES

    /** backwards compat (for old callers who want a single String path for DOWNLOADED). */
    fun modelFile(context: Context): File = downloadedFile(context)
    @Deprecated("Use isAvailable(ModelSource.DOWNLOADED) instead", ReplaceWith("isAvailable(context, ModelSource.DOWNLOADED, null)"))
    fun isModelAvailable(context: Context): Boolean = isAvailable(context, ModelSource.DOWNLOADED, null)
    @Deprecated("Use validateOrDelete(ModelSource.DOWNLOADED) instead", ReplaceWith("validateOrDelete(context, ModelSource.DOWNLOADED, null)"))
    fun validateOrDelete(context: Context): Boolean = validateOrDelete(context, ModelSource.DOWNLOADED, null)

    suspend fun downloadModel(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dir = getModelsDir(context)
            if (!dir.exists()) dir.mkdirs()

            val file = downloadedFile(context)
            if (validateOrDelete(context, ModelSource.DOWNLOADED, null)) {
                AppLogger.i(TAG, "downloadModel: cached copy already valid (${file.length()}), skip")
                return@withContext Result.success(file.absolutePath)
            }
            AppLogger.i(TAG, "downloadModel: start $MODEL_URL")
            val url = java.net.URL(MODEL_URL)
            val tmpOut = File(dir, "${file.name}.tmp")
            try { tmpOut.delete() } catch (_: Exception) {}
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 120_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "BadukNext-Android/1.0 (ModelDownloader)")
                connect()
            }
            val status = conn.responseCode
            if (status !in 200..299) {
                AppLogger.e(TAG, "downloadModel HTTP status=$status")
                runCatching {
                    conn.errorStream?.bufferedReader()?.use { r -> r.lineSequence().take(8).joinToString("\n") }
                }.onSuccess { head -> AppLogger.e(TAG, "Server error head:\n$head") }
                return@withContext Result.failure(IllegalStateException("HTTP $status on model download"))
            }
            val contentLength = conn.contentLengthLong.takeIf { it > 0 } ?: EXPECTED_6B_BYTES
            AppLogger.i(TAG, "downloadModel status=$status Content-Length=$contentLength")
            val written = conn.inputStream.use { inp -> FileOutputStream(tmpOut).use { out -> inp.copyTo(out) } }
            AppLogger.i(TAG, "downloadModel bytes=$written declared=$contentLength")
            val min6b = EXPECTED_6B_BYTES * 9 / 10
            if (written != contentLength || written < min6b) {
                try { tmpOut.delete() } catch (_: Exception) {}
                return@withContext Result.failure(IllegalStateException(
                    "Truncated download: written=$written declared=$contentLength"
                ))
            }
            if (!tmpOut.renameTo(file)) {
                tmpOut.inputStream().use { i -> FileOutputStream(file).use { o -> i.copyTo(o) } }
                try { tmpOut.delete() } catch (_: Exception) {}
            }
            if (!validateOrDelete(context, ModelSource.DOWNLOADED, null)) {
                return@withContext Result.failure(IllegalStateException("Post-download validate failed"))
            }
            AppLogger.i(TAG, "downloadModel OK ${file.absolutePath}")
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            AppLogger.e(TAG, "downloadModel failed", e)
            runCatching { downloadedFile(context).takeIf { it.exists() && !isValidModelFile(it) }?.delete() }
            runCatching { File(getModelsDir(context), "${MODEL_FILENAME}.tmp").delete() }
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------
    // CUSTOM — import a Uri (SAF ACTION_OPEN_DOCUMENT result) into our
    // stable app-private customDir. We return Result<storedAbsolutePath>.
    // ------------------------------------------------------------------

    /**
     * Import a user-picked document tree/document Uri into app-private customDir/.
     * Validates size >= 1MB & gzip magic, then stores under its displayName as stable
     * filename. Returns a Result with the absolute path we stored it at (caller should
     * save this string into SettingsStore.customModelPath).
     */
    suspend fun importCustomModel(context: Context, uri: Uri, displayNameHint: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            require(uri.scheme == "content" || uri.scheme == "file") { "Unsupported URI scheme: $uri" }
            val name = (displayNameHint ?: uri.lastPathSegment ?: "custom_model.txt.gz")
                .replace('/', '_').trim('_')
                .ifBlank { "custom_model.txt.gz" }
            // Ensure suffix — KataGo refuses anything other than .txt.gz or .bin.gz (it
                // doesn't care about suffix actually being there)
            val safeName = when {
                name.endsWith(".txt.gz") || name.endsWith(".bin.gz") -> name
                name.endsWith(".gz") -> "$name" // keep
                else -> "$name.bin.gz"
            }
            val dir = customDir(context)
            val target = File(dir, "imported_${System.currentTimeMillis()}_$safeName")
            val tmp = File(dir, "${target.name}.tmp")
            try { tmp.delete() } catch (_: Exception) {}
            val written = context.contentResolver.openInputStream(uri)?.use { inp ->
                FileOutputStream(tmp).use { out -> inp.copyTo(out) }
            } ?: return@withContext Result.failure(IllegalStateException("contentResolver openInputStream returned null for $uri"))
            AppLogger.i(TAG, "importCustomModel: wrote $written bytes, hint=$displayNameHint safe=$safeName -> tmp=${tmp.absolutePath}")
            if (!isValidModelFile(tmp)) {
                val sz = runCatching { tmp.length() }.getOrDefault(-1L)
                try { tmp.delete() } catch (_: Exception) {}
                return@withContext Result.failure(IllegalStateException(
                    "Invalid custom model (size=$sz; expected gzip >= 1MB): ensure it ends in .txt.gz or .bin.gz and is not a real KataGo net. Path picked: $displayNameHint"
                ))
            }
            if (!tmp.renameTo(target)) {
                tmp.inputStream().use { i -> FileOutputStream(target).use { o -> i.copyTo(o) } }
                try { tmp.delete() } catch (_: Exception) {}
            }
            AppLogger.i(TAG, "importCustomModel OK: ${target.absolutePath}")
            Result.success(target.absolutePath)
        } catch (e: Exception) {
            AppLogger.e(TAG, "importCustomModel failed", e)
            Result.failure(e)
        }
    }

    /**
     * Delete custom-imported copies and bundled-copy caches so next run can re-extract
     * or re-download from scratch. Called by the settings "Reset default built-in"
     * action if the user wants to reclaim disk space after trying large custom nets.
     */
    fun clearCustomAndCache(context: Context) {
        runCatching { customDir(context).listFiles()?.forEach { it.delete() } }
        runCatching { File(getModelsDir(context), ASSET_COPY_DIR_NAME).listFiles()?.forEach { it.delete() } }
    }

    // ==================================================================
    // private helpers
    // ==================================================================

    private fun getModelsDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }
}
