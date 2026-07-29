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
            copyAssetToFile(BINARY_NAME, binaryFile)
            // The KataGo executable ships as libkatago.so but must be runnable.
            binaryFile.setExecutable(true)
            AppLogger.i(TAG, "Installed patched KataGo binary")
        }

        val configFile = File(filesDir, CONFIG_NAME)
        if (!configFile.exists()) {
            copyAssetToFile(CONFIG_NAME, configFile)
            AppLogger.i(TAG, "Copied config file: ${configFile.absolutePath}")
        }

        val appDir = File(filesDir, "app")
        if (!appDir.exists()) appDir.mkdirs()

        val modelFile = File(appDir, modelFileName)
        if (!modelFile.exists()) {
            copyAssetToFile("models/$modelFileName", modelFile)
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
        return try {
            val assetSize = context.assets.open(BINARY_NAME).use { it.available() }
            binaryFile.length() != assetSize.toLong()
        } catch (e: Exception) {
            true
        }
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
