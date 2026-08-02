package com.badukai.next.engine

import android.content.Context
import android.net.Uri
import com.badukai.next.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader

// Where the KataGo neural-net weights come from — one selection enum shared by
// SettingsStore (persistence), UI (SettingsDialog radio rows), ViewModel
// (source-aware engine start), and engine preflight.
//
//   BUNDLED_ASSET — 6b shipped inside the APK at assets/models/. First launch
//                   works offline; copied to filesDir once.
//   DOWNLOADED    — legacy online downloader from katagotraining.org.
//   CUSTOM        — user picked a weights file via SAF ACTION_OPEN_DOCUMENT;
//                   bytes mirrored to filesDir/models/custom/ so we always
//                   have a readable app-private owned copy.
enum class ModelSource(val displayName: String) {
    BUNDLED_ASSET("内置（6b，离线可用）"),
    DOWNLOADED("在线下载（6b）"),
    CUSTOM("自定义文件")
}

// Downloads / validates / copies-into-place the KataGo model weights.
//
// 2026-08-02 EXTENDED with BUNDLED_ASSET and CUSTOM support.
//
// AI-START RELIABILITY INVARIANT:
//   Every on-disk path we hand to libkatago.so via `-model <path>` MUST pass
//   isValidModelFile(). The validator accepts two formats because Android's
//   aapt2 sometimes decompresses .gz asset entries during packaging (when
//   .gz is not in aaptOptions.noCompress):
//     A) COMPRESSED (.txt.gz / .bin.gz)
//          size >= 4.5MB  AND  first two bytes are gzip magic (0x1f 0x8b)
//     B) DECOMPRESSED (.txt / .bin text-format)
//          size >= 12MB  AND  head looks like a valid kata net:
//            line 1 = "<arch>-s<steps>-d<randseed>"  (e.g. b6c96-s175395328-d26788732)
//            lines 2..15 = short integer tokens
//
// BUNDLED_ASSET — we ship the 6b model inside the APK so first launch works
//   offline. Two forms are supported (probed in order at copy time):
//     PREFERRED: models/kata1-b6c96-s175395328-d26788732.txt.gz   (4,967,720 bytes, gzip)
//     FALLBACK:  models/kata1-b6c96-s175395328-d26788732.txt      (12,411,674 bytes, plaintext)
//   Only one will exist in a given build. We copy exactly what's present
//   into filesDir/models/asset_copy/ once and use that path forever after.
//
// DOWNLOADED — legacy online path; downloadModel() -> filesDir/models/<gz>.
//
// CUSTOM — user picked .txt.gz/.bin.gz/.txt/.bin from their own storage
//   via SAF; mirrored into filesDir/models/custom/ so we own a stable copy.
object ModelManager {
    private const val TAG = "ModelManager"

    const val MODEL_URL =
        "https://media.katagotraining.org/uploaded/networks/models/kata1/kata1-b6c96-s175395328-d26788732.txt.gz"
    const val MODEL_FILENAME = "kata1-b6c96-s175395328-d26788732.txt.gz"

    // Decompressed-plaintext variant name. When aapt2 has re-extracted the gz
    // entry during packaging (because .gz was absent from noCompress when the
    // APK was built) the assets/ tree contains this .txt file instead.
    private const val MODEL_FILENAME_TXT = "kata1-b6c96-s175395328-d26788732.txt"

    // 2026-08-02 AAPT2 WORKAROUND — rename the 6b gzip model to *.bin at build
    // time so that the built-in aapt2 ".gz special-case decompressor" can NEVER
    // silently turn 4.97MB .txt.gz into 12.4MB plaintext .txt inside the APK
    // (which made noCompress+=gz useless because the entry suffix was already
    // .txt by the time aapt2 ran its noCompress filter). *.bin has an explicit
    // entry in aaptOptions.noCompress so it always stays STORED in the APK
    // and the byte-for-byte gzip payload (1f 8b magic, 4,967,720 bytes) is
    // exactly what ModelManager/validator/libkatago expects.
    const val MODEL_FILENAME_BIN = "kata1-b6c96-s175395328-d26788732.bin"

    const val MODEL_DISPLAY_NAME = "6b"

    /** Preferred bundled asset entry (post-build renamed *.bin form, 4.97MB STORED gzip). */
    private const val BUNDLED_ASSET_PATH_BIN = "models/$MODEL_FILENAME_BIN"
    /** Bundled asset entry (gzip). Fallback when build renaming step was ever skipped. */
    private const val BUNDLED_ASSET_PATH_GZ = "models/$MODEL_FILENAME"
    /** Fallback bundled asset entry (decompressed plaintext). */
    private const val BUNDLED_ASSET_PATH_TXT = "models/$MODEL_FILENAME_TXT"

    /** filesDir/models/custom/ — user custom weights mirrored copy directory */
    private const val CUSTOM_DIR_NAME = "custom"
    /** filesDir/models/asset_copy/ — bundled weights cached copy directory */
    private const val ASSET_COPY_DIR_NAME = "asset_copy"

    // ---- size expectations for the shipped 6b model (both forms) ----
    private const val EXPECTED_6B_GZ_BYTES: Long  = 4_967_720L
    private const val EXPECTED_6B_TXT_BYTES: Long = 12_411_674L
    // 90% floor for "approximate match" when comparing downloaded/imported bytes
    private const val EXPECTED_6B_GZ_MIN: Long  = EXPECTED_6B_GZ_BYTES  * 9 / 10
    private const val EXPECTED_6B_TXT_MIN: Long = EXPECTED_6B_TXT_BYTES * 9 / 10

    // ---- universal validation floors ----
    // 6b compressed = 4.97MB; decompressed = 12.4MB.
    // Custom 20b = ~120MB, 40b = ~360MB, all well above 1MB floor.
    private const val MIN_VALID_COMPRESSED_BYTES: Long = 1_000_000L
    // Smallest plausible plaintext KataGo net is way bigger than 10MB so this
    // floor is very safe against accidentally treating a small HTML/error page
    // as a model.
    private const val MIN_VALID_PLAINTEXT_BYTES: Long  = 10_000_000L

    private val GZIP_MAGIC = byteArrayOf(0x1f.toByte(), 0x8b.toByte())
    // Line 1 of a plaintext kata net is e.g.
    //   b6c96-s175395328-d26788732
    // i.e. <alnum-dash-tokens separated by single '-'> with no whitespace.
    private val PLAINTEXT_HEADER_LINE_1 = Regex("""^[A-Za-z0-9]+(-[A-Za-z0-9]+){2,}$""")

    // ------------------------------------------------------------------
    // Path factories — one stable on-disk File per ModelSource.
    // ------------------------------------------------------------------

    /** DOWNLOADED: filesDir/models/<MODEL_FILENAME> (always gzip form). */
    fun downloadedFile(context: Context): File =
        File(getModelsDir(context), MODEL_FILENAME)

    /**
     * BUNDLED_ASSET cached copy: filesDir/models/asset_copy/<basename>.
     *
     * Basename is chosen based on which asset form the APK actually shipped
     * (detected inside ensureBundledCopied). Callers that just want a path to
     * hand to KataGo should use resolveModelFile / ensureBundledCopied result
     * rather than constructing one directly.
     */
    fun bundledFile(context: Context, preferPlaintext: Boolean = false): File {
        val dir = File(getModelsDir(context), ASSET_COPY_DIR_NAME)
        return if (preferPlaintext) File(dir, MODEL_FILENAME_TXT) else File(dir, MODEL_FILENAME)
    }

    /** BUNDLED_ASSET *.bin renamed form (post-build aapt2 workaround). */
    fun bundledBinFile(context: Context): File =
        File(File(getModelsDir(context), ASSET_COPY_DIR_NAME), MODEL_FILENAME_BIN)

    /** CUSTOM: filesDir/models/custom/ — app-private stable copies */
    fun customDir(context: Context): File =
        File(getModelsDir(context), CUSTOM_DIR_NAME).also { it.mkdirs() }

    /**
     * Resolve the on-disk File for a source.
     *
     * IMPORTANT: for BUNDLED_ASSET, the returned File may be either the .gz
     * or the .txt cached copy — call ensureBundledCopied first, then use the
     * absolute path it returns, not this File's name, to know what is real.
     */
    fun resolveModelFile(
        context: Context,
        source: ModelSource,
        customStoredPath: String?
    ): File = when (source) {
        ModelSource.BUNDLED_ASSET -> {
            // 2026-08-02 Tie-break order:
            //   1) *.bin renamed aapt2-workaround form (preferred after F2 fix)
            //   2) *.txt.gz legacy gzip form
            //   3) *.txt   aapt2-auto-decompressed plaintext form
            // The "else" branch (none found) falls back to *.bin — after we
            // ship the F2 fix that's the only form that will ever be built.
            val bin = bundledBinFile(context)
            val gz = bundledFile(context, preferPlaintext = false)
            val txt = bundledFile(context, preferPlaintext = true)
            when {
                bin.exists() && isValidModelFile(bin) -> bin
                gz.exists()  && isValidModelFile(gz)  -> gz
                txt.exists() && isValidModelFile(txt) -> txt
                else -> bin
            }
        }
        ModelSource.DOWNLOADED -> downloadedFile(context)
        ModelSource.CUSTOM -> {
            customStoredPath
                ?.takeIf { it.isNotBlank() }
                ?.let { File(it) }
                ?.takeIf { it.isAbsolute }
                ?: File(customDir(context), "__custom_not_set__.txt.gz")
        }
        // else — exhaustive fallback; should be unreachable for a 3-valued enum
        // but some strict Kotlin builds require the branch anyway.
        else -> bundledFile(context, preferPlaintext = false)
    }

    // ------------------------------------------------------------------
    // Strict validation (compressed + plaintext formats accepted).
    // ------------------------------------------------------------------

    /**
     * Strict is-usable check shared by every source. Accepts either:
     *   A) gzip-compressed weights  -> size >= 1MB, bytes[0..1] == 1f 8b
     *   B) plaintext kata weights   -> size >= 10MB, line 1 matches the
     *      "<arch>-s<steps>-d<seed>" pattern, lines 2..10 are short integer
     *      tokens each < 100 chars.
     */
    fun isValidModelFile(f: File): Boolean {
        if (!f.exists() || !f.isFile) return false
        val len = f.length()
        // Try gzip form first (cheap: only 2 bytes read + no charset decode).
        if (len >= MIN_VALID_COMPRESSED_BYTES) {
            val okGz = runCatching {
                FileInputStream(f).use { fis ->
                    val buf = ByteArray(2)
                    val n = fis.read(buf)
                    n >= 2 && buf[0] == GZIP_MAGIC[0] && buf[1] == GZIP_MAGIC[1]
                }
            }.getOrDefault(false)
            if (okGz) return true
        }
        // Fall back to plaintext kata net format.
        if (len < MIN_VALID_PLAINTEXT_BYTES) return false
        return runCatching {
            BufferedReader(InputStreamReader(FileInputStream(f), Charsets.US_ASCII)).use { br ->
                val line1 = br.readLine()?.trim() ?: return@use false
                if (!PLAINTEXT_HEADER_LINE_1.matches(line1)) return@use false
                repeat(8) {
                    val ln = br.readLine() ?: return@use false
                    val t = ln.trim()
                    if (t.isBlank() || t.length > 20) return@use false
                    // Permit plain integer / decimal tokens (KataGo txt nets
                    // use digits for everything after the header in the
                    // blocks we inspect). Not strict beyond "looks numeric"
                    // because KataGo itself does full parsing after we pass
                    // the path.
                    t.all { ch -> ch in "-.0123456789eE" } || return@use false
                }
                true
            }
        }.getOrDefault(false)
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
     * Validate a source; if the on-disk file is corrupt/incomplete AND we
     * own it (inside filesDir) delete it. Returns true iff usable.
     */
    fun validateOrDelete(
        context: Context,
        source: ModelSource,
        customStoredPath: String?
    ): Boolean {
        val f = resolveModelFile(context, source, customStoredPath)
        if (isValidModelFile(f)) return true
        if (f.exists()) {
            val isOwned = runCatching {
                val md = getModelsDir(context).canonicalPath
                f.canonicalPath.startsWith(md)
            }.getOrDefault(false)
            if (isOwned) {
                AppLogger.w(TAG, "validateOrDelete($source): corrupt/incomplete, deleting our copy: size=${f.length()} path=${f.absolutePath}")
                runCatching { f.delete() }
            } else {
                AppLogger.w(TAG, "validateOrDelete($source): corrupt but not app-owned (external), skipping delete: size=${f.length()} path=${f.absolutePath}")
            }
        }
        return false
    }

    // ------------------------------------------------------------------
    // BUNDLED_ASSET — APK -> filesDir copy (supports both gz and txt form).
    // ------------------------------------------------------------------

    // Which asset form should we copy out of the APK?
    //
    //   BIN is a renamed *.gz (identical gzip byte-for-byte payload, 4.97MB).
    //   It's the only form in APKs built after the 2026-08-02 aapt2 workaround.
    //   GZ and TXT are fallbacks so old/unrebuilt APK variants still boot.
    private enum class BundledAssetForm(
        val assetPath: String,
        val expectedBytes: Long,
        val targetFactory: (Context) -> File
    ) {
        BIN(BUNDLED_ASSET_PATH_BIN, EXPECTED_6B_GZ_BYTES, { ctx -> bundledBinFile(ctx) }),
        GZ(BUNDLED_ASSET_PATH_GZ,  EXPECTED_6B_GZ_BYTES,  { ctx -> bundledFile(ctx, preferPlaintext = false) }),
        TXT(BUNDLED_ASSET_PATH_TXT, EXPECTED_6B_TXT_BYTES, { ctx -> bundledFile(ctx, preferPlaintext = true) })
    }

    private fun detectBundledAssetForm(am: android.content.res.AssetManager): BundledAssetForm? {
        // 1) BIN (renamed aapt2-workaround) — highest priority
        val binListed = runCatching { am.list("models")?.contains(MODEL_FILENAME_BIN) }.getOrNull() == true
        val binOpen   = runCatching { am.open(BUNDLED_ASSET_PATH_BIN).use { true } }.getOrDefault(false)
        if (binListed || binOpen) return BundledAssetForm.BIN

        // 2) GZ (legacy gzip form)
        val gzListed = runCatching { am.list("models")?.contains(MODEL_FILENAME) }.getOrNull() == true
        val gzOpen   = runCatching { am.open(BUNDLED_ASSET_PATH_GZ).use { true } }.getOrDefault(false)
        if (gzListed || gzOpen) return BundledAssetForm.GZ

        // 3) TXT (aapt2 accidentally decompressed during packaging — rare)
        val txtListed = runCatching { am.list("models")?.contains(MODEL_FILENAME_TXT) }.getOrNull() == true
        val txtOpen   = runCatching { am.open(BUNDLED_ASSET_PATH_TXT).use { true } }.getOrDefault(false)
        if (txtListed || txtOpen) return BundledAssetForm.TXT

        return null
    }

    suspend fun ensureBundledCopied(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val am = context.assets
            val form = detectBundledAssetForm(am)
                ?: return@withContext Result.failure(IllegalStateException(
                    "Bundled 6b weights missing from APK assets. " +
                        "Expected models/$MODEL_FILENAME_BIN (bin, ${EXPECTED_6B_GZ_BYTES}B — post-aapt2-workaround form) " +
                        "OR models/$MODEL_FILENAME (gz, ${EXPECTED_6B_GZ_BYTES}B) " +
                        "OR models/$MODEL_FILENAME_TXT (txt, ${EXPECTED_6B_TXT_BYTES}B)."
                ))

            // Fast-path: a cached copy on disk already matches this form & is valid.
            val target = form.targetFactory(context)
            if (target.exists() && target.length() == form.expectedBytes && isValidModelFile(target)) {
                AppLogger.i(TAG, "ensureBundledCopied($form): cached copy present, skip copy. path=${target.absolutePath}")
                return@withContext Result.success(target.absolutePath)
            }

            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile!!, "${target.name}.tmp")
            runCatching { tmp.delete() }

            val written = am.open(form.assetPath).use { input ->
                FileOutputStream(tmp).use { out -> input.copyTo(out) }
            }
            AppLogger.i(TAG, "ensureBundledCopied($form): wrote $written bytes (expected ${form.expectedBytes})")

            // Approximate size check + full validation.
            val sizeOk = when (form) {
                // BIN is byte-for-byte identical to GZ (just a renamed copy to
                // defeat aapt2's overzealous gz decompressor). Use the same
                // min/max bounds as the legacy GZ form.
                BundledAssetForm.BIN -> written >= EXPECTED_6B_GZ_MIN  && written <= form.expectedBytes + 8192
                BundledAssetForm.GZ  -> written >= EXPECTED_6B_GZ_MIN  && written <= form.expectedBytes + 8192
                BundledAssetForm.TXT -> written >= EXPECTED_6B_TXT_MIN && written <= form.expectedBytes + 8192
            }
            if (!sizeOk || !isValidModelFile(tmp)) {
                runCatching { tmp.delete() }
                return@withContext Result.failure(IllegalStateException(
                    "Bundled asset copy($form) failed: written=$written expected=${form.expectedBytes}"
                ))
            }

            if (!tmp.renameTo(target)) {
                FileInputStream(tmp).use { i -> FileOutputStream(target).use { o -> i.channel.transferTo(0, written.toLong(), o.channel) } }
                runCatching { tmp.delete() }
            }
            if (!isValidModelFile(target)) {
                return@withContext Result.failure(IllegalStateException("Bundled copy($form) post-move validate failed: path=${target.absolutePath}"))
            }
            AppLogger.i(TAG, "ensureBundledCopied($form) success: ${target.absolutePath}")
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
    val DOWNLOADED_EXPECTED_BYTES: Long get() = EXPECTED_6B_GZ_BYTES

    @Deprecated("Use downloadedFile(context) directly (downloaded is always gz form).",
        ReplaceWith("downloadedFile(context)")
    )
    fun modelFile(context: Context): File = downloadedFile(context)
    @Deprecated("Use isAvailable(ModelSource.DOWNLOADED) instead.",
        ReplaceWith("isAvailable(context, ModelSource.DOWNLOADED, null)")
    )
    fun isModelAvailable(context: Context): Boolean = isAvailable(context, ModelSource.DOWNLOADED, null)
    @Deprecated("Use validateOrDelete(ModelSource.DOWNLOADED) instead.",
        ReplaceWith("validateOrDelete(context, ModelSource.DOWNLOADED, null)")
    )
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
            runCatching { tmpOut.delete() }
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
            val contentLength = conn.contentLengthLong.takeIf { it > 0 } ?: EXPECTED_6B_GZ_BYTES
            AppLogger.i(TAG, "downloadModel status=$status Content-Length=$contentLength")
            val written = conn.inputStream.use { inp ->
                FileOutputStream(tmpOut).use { out -> inp.copyTo(out) }
            }
            AppLogger.i(TAG, "downloadModel bytes=$written declared=$contentLength")
            if (written != contentLength || written < EXPECTED_6B_GZ_MIN) {
                runCatching { tmpOut.delete() }
                return@withContext Result.failure(IllegalStateException(
                    "Truncated download: written=$written declared=$contentLength"
                ))
            }
            if (!tmpOut.renameTo(file)) {
                FileInputStream(tmpOut).use { i ->
                    FileOutputStream(file).use { o -> i.channel.transferTo(0, written.toLong(), o.channel) }
                }
                runCatching { tmpOut.delete() }
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
    // CUSTOM — SAF Uri -> filesDir/models/custom/ stable mirror
    // ------------------------------------------------------------------

    suspend fun importCustomModel(context: Context, uri: Uri, displayNameHint: String?): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                require(uri.scheme == "content" || uri.scheme == "file") {
                    "Unsupported URI scheme: $uri"
                }
                val rawName = (displayNameHint ?: uri.lastPathSegment ?: "custom_model.txt.gz")
                    .replace('/', '_').trim('_')
                    .ifBlank { "custom_model.txt.gz" }

                // Pick a final filename suffix that matches its validated
                // form (gzip -> .gz, plaintext -> .txt/.bin as-appropriate).
                // KataGo infers format from the extension so keeping it
                // consistent avoids unnecessary "cannot parse model" errors.
                val dir = customDir(context)
                val tmp = File(dir, "import_${System.currentTimeMillis()}.tmp")
                runCatching { tmp.delete() }
                val written = context.contentResolver.openInputStream(uri)?.use { inp ->
                    FileOutputStream(tmp).use { out -> inp.copyTo(out) }
                } ?: return@withContext Result.failure(
                    IllegalStateException("contentResolver openInputStream returned null for $uri")
                )
                AppLogger.i(TAG, "importCustomModel: wrote=$written displayName=$displayNameHint tmp=${tmp.absolutePath}")
                if (!isValidModelFile(tmp)) {
                    val sz = runCatching { tmp.length() }.getOrDefault(-1L)
                    runCatching { tmp.delete() }
                    return@withContext Result.failure(IllegalStateException(
                        "Invalid custom model (size=$sz). " +
                            "Expected a real KataGo network: either a gzip (.txt.gz/.bin.gz, >= 1MB, " +
                            "first bytes 1f 8b) OR a plaintext kata net (.txt, >= 12MB, first line " +
                            "looks like \"b6c96-s175395328-d26788732\"). Pick a valid weights file."
                    ))
                }

                // Determine correct suffix by sniffing the actual header.
                val isGz: Boolean = runCatching {
                    FileInputStream(tmp).use { fis ->
                        val buf = ByteArray(2)
                        val n = fis.read(buf)
                        n >= 2 && buf[0] == GZIP_MAGIC[0] && buf[1] == GZIP_MAGIC[1]
                    }
                }.getOrDefault(false)
                val lowerName = rawName.lowercase()
                val suffix: String = when {
                    isGz && lowerName.endsWith(".txt.gz") -> ".txt.gz"
                    isGz && lowerName.endsWith(".bin.gz") -> ".bin.gz"
                    isGz                                   -> ".bin.gz"
                    lowerName.endsWith(".txt")            -> ".txt"
                    lowerName.endsWith(".bin")            -> ".bin"
                    // Plaintext kata nets are shipped as .txt most commonly.
                    else                                   -> ".txt"
                }
                val baseName = rawName
                    .removeSuffix(".gz")
                    .removeSuffix(".txt")
                    .removeSuffix(".bin")
                    .replace(Regex("\\s+"), "_")
                    .ifBlank { "custom_model" }
                val target = File(dir, "${baseName}_${System.currentTimeMillis()}$suffix")

                if (!tmp.renameTo(target)) {
                    FileInputStream(tmp).use { i ->
                        FileOutputStream(target).use { o -> i.channel.transferTo(0, written.toLong(), o.channel) }
                    }
                    runCatching { tmp.delete() }
                }
                AppLogger.i(TAG, "importCustomModel OK: ${target.absolutePath}")
                Result.success(target.absolutePath)
            } catch (e: Exception) {
                AppLogger.e(TAG, "importCustomModel failed", e)
                Result.failure(e)
            }
        }

    // ------------------------------------------------------------------
    // Cache / reset helpers used by SettingsDialog's "reset to bundled" button.
    // ------------------------------------------------------------------

    /** Wipe asset_copy + customDir. Leaves DOWNLOADED untouched. */
    fun clearCustomAndCache(context: Context) {
        runCatching {
            val dir = File(getModelsDir(context), ASSET_COPY_DIR_NAME)
            if (dir.isDirectory) dir.listFiles()?.forEach { it.delete() }
        }
        runCatching {
            val dir = customDir(context)
            if (dir.isDirectory) dir.listFiles()?.forEach { it.delete() }
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private fun getModelsDir(context: Context): File =
        File(context.filesDir, "models").also { it.mkdirs() }
}
