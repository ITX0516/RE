package com.badukai.next.engine

import android.content.Context
import com.badukai.next.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Engine lifecycle manager. Combines [EngineBootstrap] (asset release) with
 * [GtpClient] (process + GTP protocol) and owns the start/stop/proxy API.
 *
 * This class is intentionally free of any `Model` enum dependency — it accepts
 * the model's asset file name directly, so it can be reused and tested without
 * the public [KataGoEngine] facade. The user-facing `Model` enum lives on
 * [KataGoEngine] (see its docs for why a plain typealias was not sufficient).
 */
class EngineManager(private val context: Context) {

    companion object {
        private const val TAG = "KataGoEngine"
    }

    private val bootstrap = EngineBootstrap(context)
    private var client: GtpClient? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    /**
     * Release assets for [modelFileName] (e.g. "10b.bin"), build the launch
     * command + environment, and start the native KataGo process via [GtpClient].
     * Returns true on success.
     */
    suspend fun start(modelFileName: String): Boolean = withContext(Dispatchers.IO) {
        if (client?.isRunning() == true) {
            AppLogger.w(TAG, "Engine already running")
            return@withContext true
        }

        AppLogger.i(TAG, "=== PATCHED KATAGO ENGINE ===")

        val released = try {
            bootstrap.release(modelFileName)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to release engine assets", e)
            return@withContext false
        }
        if (!released.binaryReady) {
            AppLogger.e(TAG, "Engine binary or config missing (binaryReady=false). " +
                "binary=${released.binaryFile.exists()} " +
                "config=${released.configFile.exists()} " +
                "model=${released.modelFile.exists()}")
            return@withContext false
        }
        if (!released.modelFile.exists()) {
            // Weight file was not bundled with the APK — that's the expected UX:
            // the user picks their own .bin/.gz from on-device storage. Don't
            // abort startup; surface a clear message so the UI can prompt.
            AppLogger.w(TAG, "Model file ${released.modelFile.name} not bundled — " +
                "user must provide one before the engine can generate moves.")
        }

        val args: List<String> = if (released.modelFile.exists()) {
            listOf(
                released.binaryFile.absolutePath,
                "gtp",
                "-model", released.modelFile.absolutePath,
                "-config", released.configFile.absolutePath
            )
        } else {
            // User hasn't picked a weight file yet. Launch the engine in plain
            // "gtp" mode without a model so it can still respond to protocol
            // probes (list_commands, protocol_version, known_command, etc.) and
            // stay alive. play / genmove / clear_board / boardsize / komi will
            // be accepted as no-ops or errors until the user re-starts with a
            // real model via selectModel().
            AppLogger.w(TAG, "Launching engine WITHOUT model — AI moves disabled.")
            listOf(
                released.binaryFile.absolutePath,
                "gtp",
                "-config", released.configFile.absolutePath
            )
        }
        AppLogger.i(TAG, "Command: /system/bin/linker64 ${args.joinToString(" ")}")

        val env = mapOf(
            "LD_LIBRARY_PATH" to "${released.nativeLibDir}:${released.hexagonDir.absolutePath}:/vendor/lib64:/system/vendor/lib64",
            "ADSP_LIBRARY_PATH" to "${released.nativeLibDir};${released.hexagonDir.absolutePath};/system/lib/rfsa/adsp;/system/vendor/lib/rfsa/adsp;/dsp",
            "HOME" to released.filesDir.absolutePath
        )
        AppLogger.i(TAG, "Environment:")
        AppLogger.i(TAG, "  LD_LIBRARY_PATH=${env["LD_LIBRARY_PATH"]}")
        AppLogger.i(TAG, "  ADSP_LIBRARY_PATH=${env["ADSP_LIBRARY_PATH"]}")
        AppLogger.i(TAG, "  HOME=${env["HOME"]}")
        AppLogger.i(TAG, "  Working dir=${released.hexagonDir.absolutePath}")

        val gtpClient = GtpClient(
            executablePath = "/system/bin/linker64",
            workingDir = released.hexagonDir,
            args = args,
            env = env,
            logger = GtpLogger { level, _, msg, tr ->
                // Route all GTP-client logs through AppLogger under the legacy
                // "KataGoEngine" tag so pre-refactor log filtering stays intact.
                when (level) {
                    GtpLogLevel.DEBUG -> AppLogger.d(TAG, msg, tr)
                    GtpLogLevel.INFO -> AppLogger.i(TAG, msg, tr)
                    GtpLogLevel.WARN -> AppLogger.w(TAG, msg, tr)
                    GtpLogLevel.ERROR -> AppLogger.e(TAG, msg, tr)
                }
            }
        )

        val started = gtpClient.start()
        if (!started) {
            AppLogger.e(TAG, "Failed to start engine")
            return@withContext false
        }

        client = gtpClient
        _isReady.value = true
        AppLogger.i(TAG, "=== ENGINE STARTED SUCCESSFULLY ===")
        return@withContext true
    }

    /** Stop the native process and release the GTP client. */
    fun stop() {
        AppLogger.i(TAG, "Stopping KataGo...")
        client?.close()
        client = null
        _isReady.value = false
        AppLogger.i(TAG, "KataGo stopped")
    }

    suspend fun generateMove(color: String): String? =
        client?.genmove(color)

    suspend fun playMove(color: String, move: String): Boolean =
        client?.play(color, move) ?: false

    suspend fun setBoardSize(size: Int): Boolean =
        client?.boardsize(size) ?: false

    suspend fun clearBoard(): Boolean =
        client?.clear_board() ?: false

    suspend fun setKomi(komi: Float): Boolean =
        client?.komi(komi) ?: false

    suspend fun undo(): Boolean =
        client?.undo() ?: false

    suspend fun getFinalScore(): String? =
        client?.final_score()

    fun isRunning(): Boolean = client?.isRunning() ?: false
}
