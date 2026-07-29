package com.badukai.next.engine

import android.content.Context
import com.badukai.next.logging.AppLogger
import java.io.File
import java.io.FileOutputStream

/**
 * Result of releasing the native engine assets to the app's private storage.
 *
 * @param binaryFile  Released KataGo executable (libkatago.so), marked executable.
 * @param configFile  Released gtp_static.cfg.
 * @param modelFile   Released network model file under filesDir/app.
 * @param hexagonDir  Hexagon working directory (used as the process working dir).
 * @param filesDir    App filesDir used for HOME.
 * @param nativeLibDir Application nativeLibraryDir, used in library path env vars.
 * @param ready       True when binary, config and model all exist on disk.
 */
data class ReleaseResult(
    val binaryFile: File,
    val configFile: File,
    val modelFile: File,
    val hexagonDir: File,
    val filesDir: File,
    val nativeLibDir: String,
    val ready: Boolean
)

/**
 * Pure asset release layer. Copies the native KataGo binary, GTP config and the
 * requested network model out of the APK `assets/` into the app's private storage,
 * keeping them up to date based on asset size.
 *
 * This layer owns no process state and speaks no GTP — it only ensures the files
 * [GtpClient] / [EngineManager] need are present and correctly permissioned.
 */
class EngineBootstrap(private val context: Context) {

    companion object {
        // Preserve the legacy "KataGoEngine" tag so existing log filtering keeps
        // surfacing engine/asset-release logs after the refactor.
        private const val TAG = "KataGoEngine"
        private const val BINARY_NAME = "libkatago.so"
        private const val CONFIG_NAME = "gtp_static.cfg"
        private const val ALTERNATE_CONFIG_NAME = "default_gtp.cfg"
        // Asset subdirectories tried in order (newer, tidier layout first).
        private val ASSET_PREFIXES = listOf("engine/", "")
    }

    /**
     * Release engine assets for [modelFileName] (e.g. "10b.bin") into filesDir.
     * Idempotent: existing files are reused unless the bundled binary changed.
     */
    fun release(modelFileName: String): ReleaseResult {
        val dataDataPath = "/data/data/${context.packageName}"
        val filesDir = File(dataDataPath, "files")
        val nativeLibDir = context.applicationInfo.nativeLibraryDir

        val hexagonDir = File(filesDir, "hexagon")
        if (!hexagonDir.exists()) {
            hexagonDir.mkdirs()
            AppLogger.i(TAG, "Created hexagon directory: ${hexagonDir.absolutePath}")
        }

        val binaryFile = File(filesDir, BINARY_NAME)
        if (!binaryFile.exists() || shouldUpdateBinary(binaryFile)) {
            copyAssetToFileAny(BINARY_NAME, binaryFile)
            // The KataGo executable ships as libkatago.so but must be runnable.
            binaryFile.setExecutable(true)
            AppLogger.i(TAG, "Installed patched KataGo binary")
        }

        val configFile = File(filesDir, CONFIG_NAME)
        if (!configFile.exists()) {
            var copied = tryCopyAssetToFileAny(CONFIG_NAME, configFile)
            if (!copied) {
                copied = tryCopyAssetToFileAny(ALTERNATE_CONFIG_NAME, configFile)
            }
            if (copied) {
                AppLogger.i(TAG, "Copied config file: ${configFile.absolutePath}")
            }
        }

        val appDir = File(filesDir, "app")
        if (!appDir.exists()) appDir.mkdirs()

        val modelFile = File(appDir, modelFileName)
        if (!modelFile.exists()) {
            // Try bundled models/ first, fall back to user-selected copy handled upstream.
            tryCopyAssetToFileAny("models/$modelFileName", modelFile)
        }

        AppLogger.i(TAG, "Model: ${modelFile.absolutePath} (exists=${modelFile.exists()}, size=${modelFile.length()})")
        AppLogger.i(TAG, "Config: ${configFile.absolutePath} (exists=${configFile.exists()})")
        AppLogger.i(TAG, "Binary: ${binaryFile.absolutePath} (exists=${binaryFile.exists()})")

        val ready = binaryFile.exists() && configFile.exists() && modelFile.exists()
        return ReleaseResult(
            binaryFile = binaryFile,
            configFile = configFile,
            modelFile = modelFile,
            hexagonDir = hexagonDir,
            filesDir = filesDir,
            nativeLibDir = nativeLibDir,
            ready = ready
        )
    }

    /** Re-release the binary when the bundled asset size differs from the on-disk file. */
    private fun shouldUpdateBinary(binaryFile: File): Boolean {
        return resolveAssetPath(BINARY_NAME)?.let { assetPath ->
            try {
                val assetSize = context.assets.open(assetPath).use { it.available() }
                binaryFile.length() != assetSize.toLong()
            } catch (e: Exception) {
                true
            }
        } ?: true
    }

    /** Search for the first asset path that exists (trying ASSET_PREFIXES in order). */
    private fun resolveAssetPath(name: String): String? {
        for (prefix in ASSET_PREFIXES) {
            val p = prefix + name
            try {
                context.assets.open(p).use { return p }
            } catch (_: Exception) { /* not here */ }
        }
        return null
    }

    /** Copy a named asset (searches ASSET_PREFIXES) or throw if missing. */
    private fun copyAssetToFileAny(name: String, outFile: File) {
        val path = resolveAssetPath(name)
            ?: error("Asset '$name' not found under prefixes $ASSET_PREFIXES")
        copyAssetToFile(path, outFile)
    }

    /** Copy a named asset (searches ASSET_PREFIXES), returning true on success. */
    private fun tryCopyAssetToFileAny(name: String, outFile: File): Boolean {
        val path = resolveAssetPath(name) ?: return false
        copyAssetToFile(path, outFile)
        return true
    }

    private fun copyAssetToFile(assetPath: String, outFile: File) {
        context.assets.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        AppLogger.i(TAG, "Asset copied: $assetPath -> ${outFile.absolutePath}")
    }
}
