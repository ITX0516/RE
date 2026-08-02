package com.badukai.next.engine

import android.content.Context
import com.badukai.next.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.badukai.next.analysis.AnalyzeResult
import com.badukai.next.analysis.CandidateMove
import com.badukai.next.game.GameConstants
import com.badukai.next.game.Point
import org.json.JSONObject
import org.json.JSONArray
import java.io.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * KataGo engine wrapper that handles communication with the native KataGo process
 * via GTP (Go Text Protocol)
 */
class KataGoEngine(private val context: Context) {

    companion object {
        private const val TAG = "KataGoEngine"
        private const val BINARY_NAME = "libkatago.so"
        private const val CONFIG_NAME = "gtp_static.cfg"
    }

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _lastResponse = MutableStateFlow("")
    val lastResponse: StateFlow<String> = _lastResponse

    private var currentModel: String = ""
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var errorReader: BufferedReader? = null
    private var readerJob: Job? = null
    private var errorReaderJob: Job? = null
    private val responseQueue = LinkedBlockingQueue<String>()
    private val isRunning = AtomicBoolean(false)
    private val commandMutex = Mutex()
    @Volatile private var inStreamMode = false
    @Volatile var lastAnalysisError: String = ""

    enum class Model(val displayName: String, val fileName: String, val description: String) {
        SIX_B("6b", ModelManager.MODEL_FILENAME, "Efficient 6-block KataGo model")

        // Single model only — downloaded on first launch.
        // Remove these when other models are no longer supported.
        ;
    }

    suspend fun start(model: Model = Model.SIX_B): Boolean = withContext(Dispatchers.IO) {
        if (isRunning.get()) {
            AppLogger.w(TAG, "Engine already running")
            return@withContext true
        }

        AppLogger.i(TAG, "=== PATCHED KATAGO ENGINE ===")

        try {
            val filesDir = context.filesDir
            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)

            val hexagonDir = File(filesDir, "hexagon")
            if (!hexagonDir.exists()) {
                hexagonDir.mkdirs()
                AppLogger.i(TAG, "Created hexagon directory: ${hexagonDir.absolutePath}")
            }

            // Use the jniLibs binary directly — it is already unpacked / mmap'd by the
            // system into nativeLibraryDir. This avoids copying a second identical
            // binary from assets/ → files/ (the "3 copies problem" copy #3).
            val binaryFile = File(nativeLibDir, BINARY_NAME)
            if (!binaryFile.exists()) {
                AppLogger.e(TAG, "Missing jniLibs binary: ${binaryFile.absolutePath}")
                AppLogger.e(TAG, "nativeLibraryDir contents (first 20): ${nativeLibDir.listFiles()?.take(20)?.joinToString { it.name }}")
                return@withContext false
            }
            AppLogger.i(TAG, "Using jniLibs binary directly: ${binaryFile.absolutePath} (size=${binaryFile.length()})")

            val configFile = File(filesDir, CONFIG_NAME)
            if (!configFile.exists()) {
                copyAssetToFile(CONFIG_NAME, configFile)
                AppLogger.i(TAG, "Copied config file: ${configFile.absolutePath}")
            }

            // Download 6b model if not already present
            if (!ModelManager.isModelAvailable(context)) {
                AppLogger.i(TAG, "Downloading model (6b)...")
                val result = ModelManager.downloadModel(context)
                if (result.isFailure) {
                    AppLogger.e(TAG, "Model download failed: ${result.exceptionOrNull()?.message}")
                    return@withContext false
                }
            }

            // The model is in filesDir/models/ — locate it
            val modelDir = File(context.filesDir, "models")
            val modelFile = File(modelDir, model.fileName)

            AppLogger.i(TAG, "Model: ${modelFile.absolutePath} (exists=${modelFile.exists()}, size=${modelFile.length()})")
            AppLogger.i(TAG, "Config: ${configFile.absolutePath} (exists=${configFile.exists()})")

            val command = listOf(
                binaryFile.absolutePath,
                "gtp",
                "-model", modelFile.absolutePath,
                "-config", configFile.absolutePath
            )
            AppLogger.i(TAG, "Command: ${command.joinToString(" ")}")

            val builder = ProcessBuilder(command)
            builder.directory(hexagonDir)

            val env = builder.environment()
            env["LD_LIBRARY_PATH"] = "$nativeLibDir:${hexagonDir.absolutePath}:/vendor/lib64:/system/vendor/lib64"
            env["ADSP_LIBRARY_PATH"] = "$nativeLibDir;${hexagonDir.absolutePath};/system/lib/rfsa/adsp;/system/vendor/lib/rfsa/adsp;/dsp"
            env["HOME"] = filesDir.absolutePath

            AppLogger.i(TAG, "Environment:")
            AppLogger.i(TAG, "  LD_LIBRARY_PATH=${env["LD_LIBRARY_PATH"]}")
            AppLogger.i(TAG, "  ADSP_LIBRARY_PATH=${env["ADSP_LIBRARY_PATH"]}")
            AppLogger.i(TAG, "  HOME=${env["HOME"]}")
            AppLogger.i(TAG, "  Working dir=${hexagonDir.absolutePath}")

            AppLogger.i(TAG, "Launching process...")
            process = builder.start()

            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            errorReader = BufferedReader(InputStreamReader(process!!.errorStream))

            delay(2000)

            val alive = process?.isAlive ?: false
            val exitCode = try { process?.exitValue() } catch (e: IllegalThreadStateException) { null }
            AppLogger.i(TAG, "Process alive: $alive, exitCode: $exitCode")

            if (!alive) {
                val error = errorReader?.readText() ?: ""
                val output = reader?.readText() ?: ""
                AppLogger.e(TAG, "Process died immediately!")
                AppLogger.e(TAG, "Stderr: $error")
                AppLogger.e(TAG, "Stdout: $output")
                return@withContext false
            }

            isRunning.set(true)
            currentModel = model.fileName
            _isReady.value = true
            startReaderJob()
            startErrorReaderJob()

            AppLogger.i(TAG, "=== ENGINE STARTED SUCCESSFULLY ===")
            return@withContext true

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start engine", e)
            return@withContext false
        }
    }

    private fun startReaderJob() {
        readerJob = scope.launch {
            try {
                val buffer = StringBuilder()
                AppLogger.i(TAG, "Reader job started, waiting for output...")

                while (isActive && isRunning.get()) {
                    val line = withContext(Dispatchers.IO) {
                        try {
                            reader?.readLine()
                        } catch (e: IOException) {
                            AppLogger.e(TAG, "IOException reading: ${e.message}")
                            null
                        }
                    }

                    if (line == null) {
                        val alive = process?.isAlive
                        val exit = try { process?.exitValue() } catch (e: Exception) { -999 }
                        AppLogger.i(TAG, "KataGo stdout stream closed (alive=$alive, exit=$exit)")
                        break
                    }

                    AppLogger.d(TAG, "KataGo stdout: $line")

                    if (inStreamMode) {
                        // Streaming mode (kata-analyze): each non-blank line is a complete JSON result
                        if (line.isNotBlank()) {
                            responseQueue.offer(line + "\n")
                        }
                    } else {
                        buffer.append(line).append("\n")
                        if (line.isEmpty() && buffer.isNotEmpty()) {
                            val response = buffer.toString()
                            buffer.clear()
                            responseQueue.offer(response)
                            _lastResponse.value = response
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Stdout reader job error", e)
            }
        }
    }

    private fun startErrorReaderJob() {
        errorReaderJob = scope.launch {
            try {
                while (isActive && isRunning.get()) {
                    val line = withContext(Dispatchers.IO) {
                        try {
                            errorReader?.readLine()
                        } catch (e: IOException) {
                            null
                        }
                    }

                    if (line == null) {
                        AppLogger.i(TAG, "KataGo stderr stream closed")
                        break
                    }

                    AppLogger.e(TAG, "KataGo stderr: $line")
                    AppLogger.w(TAG, "KataGo stderr: $line")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Stderr reader job error", e)
            }
        }
    }

    fun stop() {
        AppLogger.i(TAG, "Stopping KataGo...")

        isRunning.set(false)
        _isReady.value = false

        try {
            sendCommandSync("quit")
        } catch (e: Exception) {
        }

        readerJob?.cancel()
        readerJob = null

        errorReaderJob?.cancel()
        errorReaderJob = null

        try {
            writer?.close()
        } catch (e: Exception) {}

        try {
            reader?.close()
        } catch (e: Exception) {}

        try {
            errorReader?.close()
        } catch (e: Exception) {}

        process?.let { p ->
            try {
                if (!p.waitFor(1, TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                }
            } catch (e: Exception) {
                p.destroyForcibly()
            }
        }

        process = null
        writer = null
        reader = null
        responseQueue.clear()

        AppLogger.i(TAG, "KataGo stopped")
    }

    fun sendCommand(command: String): Boolean {
        return sendCommandSync(command)
    }

    private fun sendCommandSync(command: String): Boolean {
        if (!isRunning.get() && command != "quit") {
            AppLogger.w(TAG, "Cannot send command, engine not running")
            return false
        }

        return try {
            AppLogger.d(TAG, "Sending: $command")
            writer?.write(command)
            writer?.newLine()
            writer?.flush()
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error sending command: $command", e)
            false
        }
    }

    fun waitForResponse(timeoutMs: Int = 30000): String {
        return try {
            val response = responseQueue.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            response ?: ""
        } catch (e: InterruptedException) {
            ""
        }
    }

    /**
     * Execute a simple GTP command returning success/failure.
     */
    private suspend fun executeGtpCommand(cmd: String, tag: String, timeout: Int = GameConstants.GTP_TIMEOUT_DEFAULT): Boolean = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            try {
                responseQueue.clear()
                sendCommand(cmd)
                waitForResponse(timeout).startsWith("=")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error $tag", e)
                false
            }
        }
    }

    suspend fun generateMove(color: String): String? = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            try {
                responseQueue.clear()
                sendCommand("genmove $color")
                val response = waitForResponse(GameConstants.GTP_TIMEOUT_GENMOVE)
                val move = parseGtpResponse(response)
                AppLogger.i(TAG, "Generated move for $color: $move")
                move
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error generating move", e)
                null
            }
        }
    }

    /**
     * Request KataGo analysis for current position.
     * Verified locally: kata-analyze produces no output on this engine,
     * so lz-analyze is PRIMARY (Leela Zero info format works reliably).
     */
    suspend fun analyzePosition(color: String = "black", maxVisits: Int = GameConstants.ANALYSIS_VISITS): AnalyzeResult? = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            lastAnalysisError = ""

            // Only lz-analyze — verified working on this engine. Skip dead fallbacks
            // to avoid holding the mutex too long (which can stall genmove).
            val lz = tryLzAnalyze()
            if (lz != null) return@withLock lz
            if (lastAnalysisError.isEmpty()) lastAnalysisError = "lz-analyze failed"

            return@withLock null
        }
    }

    private suspend fun tryLzAnalyze(): AnalyzeResult? {
        return try {
            inStreamMode = true
            responseQueue.clear()
            // interval 10cs = 100ms, first result comes fast
            sendCommand("lz-analyze 10")

            // lz-analyze emits "= " first, then lines like:
            // "info move E5 visits 4812 winrate 4492 ... info move F5 ..."
            // Read lines until we find an info line (give up fast to not stall genmove).
            var infoLine: String? = null
            for (i in 0 until 3) {
                val line = waitForResponse(GameConstants.LZ_ANALYZE_RETRY_TIMEOUT)
                if (line.isBlank()) break
                if (line.contains("info move")) {
                    infoLine = line
                    break
                }
            }

            inStreamMode = false
            sendCommand("protocol_version")
            waitForResponse(GameConstants.GTP_TIMEOUT_FLUSH)
            while (responseQueue.poll() != null) {}

            if (infoLine == null) {
                lastAnalysisError += " | lz-analyze no info line"
                AppLogger.e(TAG, "lz-analyze: no info line found")
                return null
            }
            parseLzInfo(infoLine)
        } catch (e: Exception) {
            inStreamMode = false
            lastAnalysisError += " | lz-analyze error: ${e.message}"
            AppLogger.e(TAG, "lz-analyze error", e)
            null
        }
    }

    /**
     * Parse Leela Zero "info" format from lz-analyze:
     * "info move E5 visits 4812 winrate 4492 ... info move F5 visits ..."
     * winrate unit: 10000 = 100% (so 4492 = 44.92%).
     */
    private fun parseLzInfo(line: String): AnalyzeResult? {
        val regex = Regex("info move (\\S+) visits (\\d+) winrate (-?\\d+)")
        val matches = regex.findAll(line)
        val candidates = mutableListOf<CandidateMove>()
        var bestWinrate = 0f

        for (m in matches) {
            val coord = m.groupValues[1]
            val visits = m.groupValues[2].toIntOrNull() ?: 0
            val wrRaw = m.groupValues[3].toFloatOrNull() ?: continue
            val winrate = wrRaw / GameConstants.WINRATE_UNIT
            val cm = CandidateMove.fromGtp(coord, 19) ?: continue
            candidates.add(cm.copy(
                winRate = winrate,
                visits = visits,
                isBest = candidates.isEmpty()
            ))
            if (candidates.size == 1) bestWinrate = winrate
        }

        if (candidates.isEmpty()) {
            lastAnalysisError += " | lz-analyze parse fail: ${line.take(120)}"
            AppLogger.e(TAG, "lz-analyze: no moves parsed from [$line]")
            return null
        }
        AppLogger.i(TAG, "lz-analyze success: wr=$bestWinrate candidates=${candidates.size}")
        return AnalyzeResult(winrate = bestWinrate, scoreLead = 0f, moves = candidates, ownership = null)
    }

    suspend fun playMove(color: String, move: String): Boolean = executeGtpCommand("play $color $move", "playMove")
    suspend fun setBoardSize(size: Int): Boolean = executeGtpCommand("boardsize $size", "setBoardSize")
    suspend fun clearBoard(): Boolean = executeGtpCommand("clear_board", "clearBoard")
    suspend fun setKomi(komi: Float): Boolean = executeGtpCommand("komi $komi", "setKomi")
    suspend fun undo(): Boolean = executeGtpCommand("undo", "undo")

    suspend fun getFinalScore(): String? = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            try {
                responseQueue.clear()
                sendCommand("final_score")
                parseGtpResponse(waitForResponse(GameConstants.GTP_TIMEOUT_SCORE))
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error getFinalScore", e)
                null
            }
        }
    }

    private fun parseGtpResponse(response: String): String? {
        val trimmed = response.trim()
        return when {
            trimmed.startsWith("= ") -> trimmed.substring(2).trim().split("\n").firstOrNull()?.trim()
            trimmed.startsWith("=") -> trimmed.substring(1).trim().split("\n").firstOrNull()?.trim()
            else -> null
        }
    }

    private fun shouldUpdateBinary(binaryFile: File): Boolean {
        try {
            val assetSize = context.assets.open(BINARY_NAME).use { it.available() }
            return binaryFile.length() != assetSize.toLong()
        } catch (e: Exception) {
            return true
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

    fun isRunning(): Boolean = isRunning.get()
}
