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
import java.io.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * KataGo engine wrapper that handles communication with the native KataGo process
 * via GTP (Go Text Protocol).
 *
 * ENGINE START STRATEGY (2026-08-02 — shrink v2 + P3):
 *
 *   ZERO native libraries live in jniLibs/ (jniLibs is fully removed,
 *   android:extractNativeLibs="false" is now free because there is nothing to
 *   extract). Every native binary in the APK is under assets/:
 *
 *   APK  assets/libkatago.so    (5.1MB, PIE executable)
 *        assets/deps/*.so       (1.2MB, 5 ld.so deps: libc++_shared,
 *                                 libcalculator, libffi, libmain, libcdsprpc)
 *                 │  first-launch copy (size-based update check)
 *                 ▼
 *   filesDir/libkatago.so    (app-private, exec always allowed)
 *   filesDir/libc++_shared.so
 *   filesDir/libcalculator.so
 *   filesDir/libffi.so
 *   filesDir/libmain.so
 *   filesDir/libcdsprpc.so
 *                 │  exec via one of two plans:
 *                 ├─ Plan 1 : /system/bin/linker64 filesDir/libkatago.so gtp ...
 *                 └─ Plan 2 : filesDir/libkatago.so gtp ...  (PIE binary direct)
 *                 ▲
 *   LD_LIBRARY_PATH = filesDir:filesDir/hexagon
 *        (ALL runtime deps resolved from filesDir — no /data/app-lib reference)
 *
 *   Why this removes the LAST 1.2MB mattered:
 *     • Before P3, those 5 .so lived in jniLibs/ → system extracted copies to
 *       /data/app-lib ON INSTALL, costing 1.2MB. After P3, they sit compressed
 *       in assets/ (APK) and only get a single copy inside filesDir at runtime,
 *       at the same time as the engine binary.
 *     • More importantly: by EMPTYING jniLibs/ we can flip
 *       android:extractNativeLibs="false" WITHOUT any SELinux/noexec risk,
 *       because there's literally nothing the system could try to exec-in-place
 *       from the APK. All exec happens 100% inside filesDir/.
 *     • This also cuts the ~3-way "APK jniLibs store + /data/app-lib extract +
 *       filesDir copy" duplication down to exactly ONE filesDir copy for the
 *       entire native footprint.
 *
 *   Failure diagnostics: both engine stderr + linker64 logs are printed into logcat
 *   (dlopen / permission denied / missing .so / linker errors).
 */
class KataGoEngine(private val context: Context) {

    companion object {
        private const val TAG = "KataGoEngine"
        private const val BINARY_NAME = "libkatago.so"
        private const val CONFIG_NAME = "gtp_static.cfg"

        /** Linker 64-bit loader candidates (in preference order). */
        private val LINKER64_CANDIDATES = listOf(
            "/system/bin/linker64",
            "/apex/com.android.runtime/bin/linker64",
            "/system/bin/linker_android64"
        )

        /** A single engine-launch plan: Path-B1 (linker64) or Path-B2 (PIE direct). */
        private data class StartPlan(
            val label: String,
            val binary: File,
            val useLinker64: Boolean
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
        ;
    }

    suspend fun start(model: Model = Model.SIX_B): Boolean = withContext(Dispatchers.IO) {
        if (isRunning.get()) {
            AppLogger.w(TAG, "Engine already running")
            return@withContext true
        }

        AppLogger.i(TAG, "=== KATAGO ENGINE START (shrink v2+P3 — assets→filesDir, 0 jniLibs extract) ===")

        // ---- Directories + config ------------------------------------------------
        val filesDir = context.filesDir
        // NOTE: context.applicationInfo.nativeLibraryDir is NO LONGER referenced.
        // P3 moved every runtime .so out of jniLibs/ into assets/deps/ → copies
        // live inside filesDir/ exclusively. android:extractNativeLibs=false means
        // the system doesn't even create /data/app-lib for us.

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

        // ---- Model (download-once at runtime) ------------------------------------
        if (!ModelManager.isModelAvailable(context)) {
            AppLogger.i(TAG, "Downloading model (6b)...")
            val result = ModelManager.downloadModel(context)
            if (result.isFailure) {
                AppLogger.e(TAG, "Model download failed: ${result.exceptionOrNull()?.message}")
                return@withContext false
            }
        }
        val modelFile = File(context.filesDir, "models").resolve(model.fileName)
        AppLogger.i(TAG, "Model: ${modelFile.absolutePath} (exists=${modelFile.exists()}, size=${modelFile.length()})")
        AppLogger.i(TAG, "Config: ${configFile.absolutePath} (exists=${configFile.exists()})")

        // ---- Install engine binary + all ld.so deps into filesDir ----------------
        //
        // P3 layout (total runtime copy ~6.3MB inside filesDir, 0 anywhere else):
        //   filesDir/libkatago.so     ← assets/libkatago.so
        //   filesDir/libc++_shared.so  ← assets/deps/libc++_shared.so
        //   filesDir/libcalculator.so  ← assets/deps/libcalculator.so
        //   filesDir/libffi.so         ← assets/deps/libffi.so
        //   filesDir/libmain.so        ← assets/deps/libmain.so
        //   filesDir/libcdsprpc.so     ← assets/deps/libcdsprpc.so
        //
        // All copies are size-verified (if file exists & size matches → skip).
        // ---- 8< ----
        val filesDirBinary = File(filesDir, BINARY_NAME)
        val assetAvailable = try {
            context.assets.open(BINARY_NAME).close(); true
        } catch (_: Exception) {
            false
        }
        if (!assetAvailable) {
            AppLogger.e(TAG, "assets/$BINARY_NAME is NOT packaged in APK. " +
                "Engine start requires assets/libkatago.so as the binary source. " +
                "Check setup-from-badukai.sh stage P2 accidentally deleted it — it should ONLY delete jniLibs/libkatago.so.")
            return@withContext false
        }
        if (!filesDirBinary.exists() || shouldUpdateBinary(filesDirBinary)) {
            copyAssetToFile(BINARY_NAME, filesDirBinary)
            try { filesDirBinary.setExecutable(true, false) } catch (_: Exception) {}
            AppLogger.i(TAG, "Installed assets→filesDir binary: ${filesDirBinary.absolutePath} size=${filesDirBinary.length()}")
        } else {
            AppLogger.i(TAG, "filesDir binary up-to-date: ${filesDirBinary.absolutePath} size=${filesDirBinary.length()}")
        }

        // assets/deps/*.so → filesDir/*.so (KataGo child-process ld.so deps)
        val depsAssetFiles: List<String> = try {
            context.assets.list("deps")?.filter { it.endsWith(".so") }?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        AppLogger.i(TAG, "assets/deps declares ${depsAssetFiles.size} .so: ${depsAssetFiles.joinToString()}")
        for (depName in depsAssetFiles) {
            val depAssetPath = "deps/$depName"
            val depOut = File(filesDir, depName)
            val needCopy = try {
                if (!depOut.exists()) true
                else {
                    val assetSize = context.assets.open(depAssetPath).use { it.available() }
                    depOut.length() != assetSize.toLong()
                }
            } catch (_: Exception) {
                true
            }
            if (needCopy) {
                copyAssetToFile(depAssetPath, depOut)
                try { depOut.setExecutable(true, false) } catch (_: Exception) {}
                AppLogger.i(TAG, "Installed dep: assets/$depAssetPath → ${depOut.absolutePath} size=${depOut.length()}")
            }
        }
        if (depsAssetFiles.isEmpty()) {
            AppLogger.w(TAG, "WARNING: assets/deps/ is empty or missing! If KataGo dies with " +
                "'libc++_shared.so: cannot open shared object file: No such file or directory' " +
                "then Stage P3 move-to-assets/deps failed in setup/CI.")
        }
        // ---- ---- 8< ---- end of P3 install ----

        // ---- Shared env / args ---------------------------------------------------
        // P3: LD_LIBRARY_PATH points ONLY at filesDir (where we just installed
        // libc++_shared.so & friends). NativeLibraryDir is intentionally dropped —
        // jniLibs is empty, extractNativeLibs=false, so the system never created
        // any app-lib copies; vendor paths kept in case some device ships OpenCL
        // ICDs there.
        val envBase = LinkedHashMap<String, String>().apply {
            put("LD_LIBRARY_PATH", listOfNotNull(
                filesDir.absolutePath,
                hexagonDir.absolutePath,
                "/vendor/lib64",
                "/system/vendor/lib64"
            ).joinToString(":"))
            put("ADSP_LIBRARY_PATH", listOfNotNull(
                filesDir.absolutePath,
                hexagonDir.absolutePath,
                "/system/lib/rfsa/adsp",
                "/system/vendor/lib/rfsa/adsp",
                "/dsp"
            ).joinToString(";"))
            put("HOME", filesDir.absolutePath)
        }
        val gtpArgs = listOf("gtp", "-model", modelFile.absolutePath, "-config", configFile.absolutePath)
        val linker64 = LINKER64_CANDIDATES.firstOrNull { File(it).exists() }
        AppLogger.i(TAG, "Detected linker64: ${linker64 ?: "NONE — Plan 1 skipped"}")
        AppLogger.i(TAG, "filesDir so inventory (exec candidate list):")
        filesDir.listFiles()?.filter { it.name.endsWith(".so") }
            ?.sortedByDescending { it.length() }
            ?.forEach { f -> AppLogger.i(TAG, "  ${f.name}  ${f.length()} bytes") }

        // ---- Build launch plan list ----------------------------------------------
        val plans = listOfNotNull(
            if (linker64 != null) StartPlan("Path B1 (filesDir + linker64)", filesDirBinary, true) else null,
            StartPlan("Path B2 (filesDir PIE direct)", filesDirBinary, false)
        )
        AppLogger.i(TAG, "Engine start plans (in order): ${plans.joinToString { it.label }}")

        // ---- Execute plans in order ----------------------------------------------
        var lastFailureReason: String? = null
        for ((idx, plan) in plans.withIndex()) {
            val attempt = idx + 1
            AppLogger.i(TAG, "--- Plan $attempt/${plans.size}: ${plan.label} ---")
            if (plan.useLinker64) {
                // link loader already handles protection — no need to chmod +x separately
            } else {
                try { plan.binary.setExecutable(true, false) } catch (_: Exception) {}
            }
            val cmd = buildList {
                if (plan.useLinker64) add(linker64!!)
                add(plan.binary.absolutePath)
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
                lastFailureReason = "${plan.label} died within 2s or never launched — see full stderr above in logcat"
                AppLogger.e(TAG, "Plan $attempt/${plans.size} FAILED: ${plan.label}")
            }
        }

        // ---- All plans failed ----------------------------------------------------
        AppLogger.e(TAG, "=== ALL ENGINE START PLANS FAILED ===")
        AppLogger.e(TAG, "Final reason: $lastFailureReason")
        AppLogger.e(TAG, "Diagnostics (dev only):")
        AppLogger.e(TAG, "  • APK unzip → assets/libkatago.so MUST exist (is source copy)")
        AppLogger.e(TAG, "  • adb shell run-as com.badukai.next ls -l files/libkatago.so  ← size equals APK entry?")
        AppLogger.e(TAG, "  • adb shell ls -l /system/bin/linker64  ← exists? (if not only B2 runs)")
        AppLogger.e(TAG, "  • Common fails: 'Permission denied' (noexec on files/) → will need Plan adjustment")
        false
    }

    /**
     * Try-launch once. Returns [RunOnceResult] on success (alive after 2s),
     * or null on immediate death with detailed stderr logged to AppLogger.
     */
    private fun runOnce(
        cmd: List<String>,
        envBase: Map<String, String>,
        workingDir: File
    ): RunOnceResult? {
        val builder = ProcessBuilder(cmd)
        builder.directory(workingDir)
        builder.environment().putAll(envBase)

        AppLogger.i(TAG, "Environment:")
        envBase.forEach { (k, v) -> AppLogger.i(TAG, "  $k=$v") }
        AppLogger.i(TAG, "  Working dir=${workingDir.absolutePath}")

        val p = builder.start()
        val w = BufferedWriter(OutputStreamWriter(p.outputStream))
        val r = BufferedReader(InputStreamReader(p.inputStream))
        val er = BufferedReader(InputStreamReader(p.errorStream))

        // Drain immediate startup stderr (first 200ms) so dlopen/linker surface early
        val startupErr = StringBuilder()
        val startNs = System.nanoTime()
        while (System.nanoTime() - startNs < 200_000_000L) {
            if (!er.ready()) break
            val line = er.readLine() ?: break
            startupErr.append(line).append('\n')
        }
        if (startupErr.isNotEmpty()) {
            AppLogger.e(TAG, "Startup stderr (first 200ms):\n$startupErr")
        }

        try { Thread.sleep(2000L) } catch (_: InterruptedException) {}
        val alive = p.isAlive
        val exitCode = try { p.exitValue() } catch (_: IllegalThreadStateException) { null }
        AppLogger.i(TAG, "Process alive=$alive, exitCode=$exitCode")

        if (!alive) {
            val errTail = try { er.readText().trim() } catch (_: Exception) { "" }
            val outTail = try { r.readText().take(2000) } catch (_: Exception) { "" }
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
                AppLogger.i(TAG, "Reader job started")
                while (isActive && isRunning.get()) {
                    val line = withContext(Dispatchers.IO) {
                        try { reader?.readLine() } catch (e: IOException) {
                            AppLogger.e(TAG, "IOException reading stdout: ${e.message}")
                            null
                        }
                    }
                    if (line == null) {
                        val alive = process?.isAlive
                        val exit = try { process?.exitValue() } catch (_: Exception) { -999 }
                        AppLogger.i(TAG, "KataGo stdout closed (alive=$alive, exit=$exit)")
                        break
                    }
                    AppLogger.d(TAG, "KataGo stdout: $line")
                    if (inStreamMode) {
                        if (line.isNotBlank()) responseQueue.offer(line + "\n")
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
                        try { errorReader?.readLine() } catch (_: IOException) { null }
                    }
                    if (line == null) {
                        AppLogger.i(TAG, "KataGo stderr stream closed")
                        break
                    }
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
        try { sendCommandSync("quit") } catch (_: Exception) {}
        readerJob?.cancel() ; readerJob = null
        errorReaderJob?.cancel() ; errorReaderJob = null
        try { writer?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { errorReader?.close() } catch (_: Exception) {}
        process?.let { p ->
            try { if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroyForcibly() }
            catch (_: Exception) { p.destroyForcibly() }
        }
        process = null ; writer = null ; reader = null ; errorReader = null
        responseQueue.clear()
        AppLogger.i(TAG, "KataGo stopped")
    }

    fun sendCommand(command: String): Boolean = sendCommandSync(command)

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
        return try { responseQueue.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: "" }
        catch (_: InterruptedException) { "" }
    }

    private suspend fun executeGtpCommand(
        cmd: String,
        tag: String,
        timeout: Int = GameConstants.GTP_TIMEOUT_DEFAULT
    ): Boolean = withContext(Dispatchers.IO) {
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
                parseGtpResponse(waitForResponse(GameConstants.GTP_TIMEOUT_GENMOVE))
                    ?.also { AppLogger.i(TAG, "Generated move for $color: $it") }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error generating move", e)
                null
            }
        }
    }

    /**
     * Request KataGo analysis. Verified locally: kata-analyze produces no output on
     * this engine build, so lz-analyze is PRIMARY (Leela Zero info format works).
     */
    suspend fun analyzePosition(
        color: String = "black",
        maxVisits: Int = GameConstants.ANALYSIS_VISITS
    ): AnalyzeResult? = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            lastAnalysisError = ""
            val lz = tryLzAnalyze()
            if (lz != null) return@withLock lz
            if (lastAnalysisError.isEmpty()) lastAnalysisError = "lz-analyze failed"
            null
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
                if (line.contains("info move")) { infoLine = line ; break }
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
     * Parse a single Leela Zero "info move <coord> visits <n> winrate <wr_raw> ..."
     * line into an AnalyzeResult. winrate unit is KataGo standard: 10000 = 100%.
     */
    private fun parseLzInfo(line: String): AnalyzeResult? {
        val regex = Regex("info move (\\S+) visits (\\d+) winrate (-?\\d+)")
        val matches = regex.findAll(line)
        val candidates = mutableListOf<CandidateMove>()
        var bestWinrate = 0f

        for (m in matches) {
            val coord     = m.groupValues[1]
            val visits    = m.groupValues[2].toIntOrNull() ?: 0
            val wrRaw     = m.groupValues[3].toFloatOrNull() ?: continue
            val winrate   = wrRaw / GameConstants.WINRATE_UNIT
            val cm = CandidateMove.fromGtp(coord, 19) ?: continue
            candidates.add(cm.copy(
                winRate = winrate,
                visits  = visits,
                isBest  = candidates.isEmpty()
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
    suspend fun setBoardSize(size: Int): Boolean     = executeGtpCommand("boardsize $size", "setBoardSize")
    suspend fun clearBoard(): Boolean                = executeGtpCommand("clear_board", "clearBoard")
    suspend fun setKomi(komi: Float): Boolean       = executeGtpCommand("komi $komi", "setKomi")
    suspend fun undo(): Boolean                      = executeGtpCommand("undo", "undo")

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
            trimmed.startsWith("=")  -> trimmed.substring(1).trim().split("\n").firstOrNull()?.trim()
            else                     -> null
        }
    }

    /** True when the filesDir copy is stale vs the APK assets copy (size-based check). */
    private fun shouldUpdateBinary(binaryFile: File): Boolean {
        return try {
            val assetSize = context.assets.open(BINARY_NAME).use { it.available() }
            binaryFile.length() != assetSize.toLong()
        } catch (_: Exception) {
            true
        }
    }

    private fun copyAssetToFile(assetPath: String, outFile: File) {
        context.assets.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        AppLogger.i(TAG, "Asset copied: $assetPath -> ${outFile.absolutePath}")
    }

    fun isRunning(): Boolean = isRunning.get()
}
