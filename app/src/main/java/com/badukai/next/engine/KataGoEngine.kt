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
 *
 * ENGINE START LIFECYCLE (3-path fallback, hotfix 2026-08-02):
 *
 *   Path A — Direct exec of jniLibs copy via /system/bin/linker64 loader
 *     + Best: no extra copy, minimal installed size
 *     - Sometimes blocked by SELinux/noexec mount on data/app-lib
 *
 *   Path B — Copy assets/libkatago.so → filesDir + exec via linker64
 *     + Uses app-private files/ dir (exec is almost always permitted there)
 *     - Costs one extra copy on disk (size trade-off for stability)
 *
 *   Path C — Direct exec of filesDir copy WITHOUT linker64 (PIE binary case)
 *     + Some devices don't expose linker64 at /system/bin/linker64
 *
 *   Each path probes: if process dies within 2s or throws IOException, we
 *   record the failure in AppLogger and proceed to the next path.
 */
class KataGoEngine(private val context: Context) {

    companion object {
        private const val TAG = "KataGoEngine"
        private const val BINARY_NAME = "libkatago.so"
        private const val CONFIG_NAME = "gtp_static.cfg"
        private val LINKER64_CANDIDATES = listOf(
            "/system/bin/linker64",
            "/apex/com.android.runtime/bin/linker64",
            "/system/bin/linker_android64"
        )
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

        AppLogger.i(TAG, "=== PATCHED KATAGO ENGINE (4-path start hotfix) ===")

        // ---- Preconditions: directories + model ----------------------------------
        val filesDir = context.filesDir
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)

        val hexagonDir = File(filesDir, "hexagon")
        if (!hexagonDir.exists()) {
            hexagonDir.mkdirs()
            AppLogger.i(TAG, "Created hexagon directory: ${hexagonDir.absolutePath}")
        }

        val configFile = File(filesDir, CONFIG_NAME)
        if (!configFile.exists()) {
            copyAssetToFile(CONFIG_NAME, configFile)
            AppLogger.i(TAG, "Copied config file: ${configFile.absolutePath}")
        }

        if (!ModelManager.isModelAvailable(context)) {
            AppLogger.i(TAG, "Downloading model (6b)...")
            val result = ModelManager.downloadModel(context)
            if (result.isFailure) {
                AppLogger.e(TAG, "Model download failed: ${result.exceptionOrNull()?.message}")
                return@withContext false
            }
        }

        val modelDir = File(context.filesDir, "models")
        val modelFile = File(modelDir, model.fileName)
        AppLogger.i(TAG, "Model: ${modelFile.absolutePath} (exists=${modelFile.exists()}, size=${modelFile.length()})")
        AppLogger.i(TAG, "Config: ${configFile.absolutePath} (exists=${configFile.exists()})")

        // ---- Build shared environment (same for all 4 paths) -------------------
        val envBase = LinkedHashMap<String, String>().apply {
            put("LD_LIBRARY_PATH", "$nativeLibDir:${hexagonDir.absolutePath}:/vendor/lib64:/system/vendor/lib64")
            put("ADSP_LIBRARY_PATH", "$nativeLibDir;${hexagonDir.absolutePath};/system/lib/rfsa/adsp;/system/vendor/lib/rfsa/adsp;/dsp")
            put("HOME", filesDir.absolutePath)
        }
        val gtpArgs = listOf("gtp", "-model", modelFile.absolutePath, "-config", configFile.absolutePath)
        val linker64 = LINKER64_CANDIDATES.firstOrNull { File(it).exists() }
        AppLogger.i(TAG, "Detected linker64: ${linker64 ?: "NONE — will also try direct PIE exec"}")

        // ---- Resolve 2 binary locations (jniLibs copy, filesDir copy) ----------
        val jniLibsBinary = File(nativeLibDir, BINARY_NAME).takeIf { it.exists() }
        if (jniLibsBinary != null) {
            AppLogger.i(TAG, "jniLibs binary available: ${jniLibsBinary.absolutePath} size=${jniLibsBinary.length()}")
        } else {
            AppLogger.w(TAG, "jniLibs binary MISSING from $nativeLibDir — is extractNativeLibs=true?")
            AppLogger.w(TAG, "nativeLibraryDir list: ${nativeLibDir.listFiles()?.map { it.name }?.take(20)}")
        }

        // Always ensure filesDir has an up-to-date copy (Path B/C fallback source).
        val filesDirBinary = File(filesDir, BINARY_NAME)
        val assetAvailable = try {
            context.assets.open(BINARY_NAME).close(); true
        } catch (_: Exception) {
            false
        }
        if (assetAvailable) {
            if (!filesDirBinary.exists() || shouldUpdateBinary(filesDirBinary)) {
                copyAssetToFile(BINARY_NAME, filesDirBinary)
                try { filesDirBinary.setExecutable(true, false) } catch (_: Exception) {}
                AppLogger.i(TAG, "Installed assets copy to filesDir: ${filesDirBinary.absolutePath} size=${filesDirBinary.length()}")
            } else {
                AppLogger.i(TAG, "filesDir binary up-to-date: ${filesDirBinary.absolutePath} size=${filesDirBinary.length()}")
            }
        } else {
            AppLogger.w(TAG, "assets/$BINARY_NAME not available in APK — Path B/Path C fallback disabled. " +
                "If Path A fails engine start will fail. Fix: ensure assets/libkatago.so is packaged.")
        }

        // ---- Candidate start plans, ordered by preference -----------------------
        data class StartPlan(
            val label: String,
            val binary: File?,
            val useLinker64: Boolean
        )
        val plans = mutableListOf<StartPlan>()
        if (linker64 != null && jniLibsBinary != null) {
            plans += StartPlan("Path A1 (jniLibs + linker64)", jniLibsBinary, true)
        }
        if (jniLibsBinary != null) {
            plans += StartPlan("Path A2 (jniLibs PIE direct)", jniLibsBinary, false)
        }
        if (linker64 != null && filesDirBinary.exists()) {
            plans += StartPlan("Path B1 (filesDir + linker64)", filesDirBinary, true)
        }
        if (filesDirBinary.exists()) {
            plans += StartPlan("Path B2 (filesDir PIE direct)", filesDirBinary, false)
        }

        AppLogger.i(TAG, "Engine start plans (in order): ${plans.joinToString { it.label }}")
        require(plans.isNotEmpty()) {
            "No engine start plans available. Need jniLibs/libkatago.so AND/OR assets/libkatago.so packaged."
        }

        // ---- Try each plan in order --------------------------------------------
        var lastFailureReason: String? = null
        for ((idx, plan) in plans.withIndex()) {
            val attempt = idx + 1
            AppLogger.i(TAG, "--- Plan $attempt/${plans.size}: ${plan.label} ---")
            val binary = plan.binary!!
            if (plan.label.contains("PIE")) {
                try {
                    binary.setExecutable(true, false)
                } catch (_: Exception) {}
            }
            val cmd = buildList {
                if (plan.useLinker64) add(linker64!!)
                add(binary.absolutePath)
                addAll(gtpArgs)
            }
            AppLogger.i(TAG, "Command: ${cmd.joinToString(" ")}")

            val result = try {
                runOnce(cmd, envBase, hexagonDir)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Plan $attempt/${plans.size} exception: ${e::class.java.simpleName} ${e.message}")
                lastFailureReason = "${plan.label} exception: ${e.message}"
                null
            }

            if (result != null) {
                process = result.process
                writer = result.writer
                reader = result.reader
                errorReader = result.errorReader
                isRunning.set(true)
                currentModel = model.fileName
                _isReady.value = true
                startReaderJob()
                startErrorReaderJob()
                AppLogger.i(TAG, "=== ENGINE STARTED SUCCESSFULLY via ${plan.label} ===")
                return@withContext true
            } else {
                lastFailureReason = "${plan.label} died within 2s or never launched — see stderr lines above in logcat"
                AppLogger.e(TAG, "Plan $attempt/${plans.size} FAILED: ${plan.label}")
            }
        }

        AppLogger.e(TAG, "=== ALL ENGINE START PLANS FAILED ===")
        AppLogger.e(TAG, "Final reason summary: $lastFailureReason")
        AppLogger.e(TAG, "Diagnostics checklist:")
        AppLogger.e(TAG, "  • APK unzip: is assets/libkatago.so present? (needed for B fallback)")
        AppLogger.e(TAG, "  • adb shell run-as com.badukai.next ls -l /data/app/~~*/lib/arm64/  → libkatago.so? (Path A)")
        AppLogger.e(TAG, "  • adb shell run-as com.badukai.next ls -l files/libkatago.so  → size matches APK entry? (Path B)")
        AppLogger.e(TAG, "  • adb shell ls -l /system/bin/linker64  → exists? (if no, only PIE-direct plans run)")
        false
    }

    /**
     * Try-launch once. Returns RunOnceResult on success (alive after 2s),
     * or null on immediate death (diagnostic stderr is logged here).
     */
    private fun runOnce(
        cmd: List<String>,
        envBase: Map<String, String>,
        workingDir: File
    ): RunOnceResult? {
        val builder = ProcessBuilder(cmd)
        builder.directory(workingDir)
        val env = builder.environment()
        env.putAll(envBase)

        AppLogger.i(TAG, "Environment:")
        envBase.forEach { (k, v) -> AppLogger.i(TAG, "  $k=$v") }
        AppLogger.i(TAG, "  Working dir=${workingDir.absolutePath}")

        val p = builder.start()
        val w = BufferedWriter(OutputStreamWriter(p.outputStream))
        val r = BufferedReader(InputStreamReader(p.inputStream))
        val er = BufferedReader(InputStreamReader(p.errorStream))

        // Drain any immediate startup stderr so dlopen/linker errors surface in log early
        val startupErr = StringBuilder()
        val startNs = System.nanoTime()
        while (System.nanoTime() - startNs < 200_000_000L) {
            if (!er.ready()) break
            val line = er.readLine() ?: break
            startupErr.append(line).append('\n')
        }
        if (startupErr.isNotEmpty()) {
            AppLogger.e(TAG, "Immediate startup stderr (first 200ms):\n$startupErr")
        }

        try { Thread.sleep(2000L) } catch (_: InterruptedException) {}
        val alive = p.isAlive
        val exitCode = try { p.exitValue() } catch (_: IllegalThreadStateException) { null }
        AppLogger.i(TAG, "Process alive=$alive, exitCode=$exitCode")

        if (!alive) {
            val errTail = try { er.readText().trim() } catch (_: Exception) ""
            val outTail = try { r.readText().take(2000) } catch (_: Exception) ""
            AppLogger.e(TAG, "Process died immediately! exit=$exitCode")
            if (errTail.isNotBlank()) AppLogger.e(TAG, "Stderr full:\n$errTail")
            if (outTail.isNotBlank()) AppLogger.e(TAG, "Stdout head:\n$outTail")
            try { w.close() } catch (_: Exception) {}
            try { r.close() } catch (_: Exception) {}
            try { er.close() } catch (_: Exception) {}
            try { p.destroyForcibly() } catch (_: Exception) {}
            return null
        }

        return RunOnceResult(p, w, r, er)
    }

    private data class RunOnceResult(
        val process: Process,
        val writer: BufferedWriter,
        val reader: BufferedReader,
        val errorReader: BufferedReader
    )

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
            sendCommand("lz-analyze 10")

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
