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
 * ENGINE START STRATEGY (APK shrink v2 + Stage P3):
 *
 * - jniLibs/ is COMPLETELY EMPTY (all native binaries moved to assets/).
 * - android:extractNativeLibs="false" therefore costs nothing (nothing to extract).
 * - First launch copies assets into filesDir and execs from there.
 *
 *   APK inside assets:
 *     libkatago.so               (5.1MB, PIE executable)
 *     deps/libc++_shared.so      (.9MB ld.so dep)
 *     deps/libcalculator.so      (.2MB ld.so dep)
 *     deps/libffi.so, deps/libmain.so, deps/libcdsprpc.so  (tiny)
 *
 *   At runtime these are copied to filesDir and launched via:
 *     Plan A: /system/bin/linker64 filesDir/libkatago.so gtp ...
 *     Plan B: filesDir/libkatago.so gtp ... directly
 *   with LD_LIBRARY_PATH = filesDir (deps resolved from filesDir only).
 *
 *   This eliminates the historical triple-duplication:
 *     [jniLibs copy in APK]  +  [/data/app-lib system extract]  +  [filesDir copy]
 *   down to a SINGLE filesDir copy for the entire native footprint, while also
 *   removing any SELinux/noexec risk since jniLibs contains nothing the OS
 *   could try to exec-in-place from the APK mount.
 *
 * Diagnostics: both engine stderr and linker64 output are forwarded to logcat
 * (look for dlopen, permission-denied, missing-so, linker messages).
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

        /** Outcome of a single runOnce attempt (alive after 2s, or dead with diags). */
        private sealed class RunOnceOutcome {
            data class Alive(val result: RunOnceResult) : RunOnceOutcome()
            data class Dead(
                val exitCode: Int?,
                val stderrTail40: List<String>,
                val stdoutTail20: List<String>
            ) : RunOnceOutcome()
        }
    }

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _lastResponse = MutableStateFlow("")
    val lastResponse: StateFlow<String> = _lastResponse

    // 2026-08-02 DIAGNOSTIC-TO-TOAST: populated in full detail during every
    // start() attempt. GameViewModel.startEngine will concatenate the ENTIRE
    // string into the user-visible gameMessage if start() returns false, so a
    // single screenshot from the user reveals:
    //   - model/config on-disk file bytes + header hex (no aapt2 guesswork)
    //   - jniLibs / filesDir .so inventory (sizes + canExecute)
    //   - LD_LIBRARY_PATH + linker64 pick
    //   - per-Plan cmd, exitValue, stderr-first-40, stdout-first-20
    // Cost: ~2000-8000 chars on the fail path; perfectly fine for a single
    // Compose Text element and eliminates 10+ rounds of "guessing the fail".
    @Volatile
    var lastStartDiagnostic: String = ""

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
        SIX_B("6b", ModelManager.MODEL_FILENAME, "Efficient 6-block KataGo model (legacy selector enum; keep for existing callers)")
        ;
    }

    /**
     * Start KataGo. Multi-source weight selection (user request 2026-08-02):
     *   BUNDLED_ASSET  — built-in 6b (4.97MB) shipped inside APK → copy to filesDir/models/asset_copy/
     *   DOWNLOADED     — 6b fetched online (legacy path)
     *   CUSTOM         — user-picked gzip mirrored into filesDir/models/custom/ by importCustomModel()
     *
     * Preflight contract for every source:
     *   1) source-specific prepare (copy-from-APK / download / validate stored-import)
     *   2) strict validateOrDelete: size >= 1MB & gzip magic 1f8b → if not: clean up + fail early
     *   3) PREFLIGHT DOUBLE-CHECK: config >=1KB readable + model on disk readable
     *      → never spawn a native process doomed to "could not parse model error"
     */
    suspend fun start(
        source: ModelSource = ModelSource.BUNDLED_ASSET,
        customStoredPath: String? = null,
        @Suppress("UNUSED_PARAMETER") legacyModel: Model = Model.SIX_B
    ): Boolean = withContext(Dispatchers.IO) {
        if (isRunning.get()) {
            AppLogger.w(TAG, "Engine already running")
            return@withContext true
        }

        val diag = StringBuilder(4096)
        fun diagLine(s: String) { diag.append(s).append('\n') }
        diagLine("=== AI START DIAGNOSTIC ===")
        diagLine("source=$source   customPathSet=${!customStoredPath.isNullOrBlank()}")

        AppLogger.i(TAG, "=== KATAGO ENGINE START (source=$source customPathSet=${!customStoredPath.isNullOrBlank()}) ===")

        // ---- Directories + config ------------------------------------------------
        val filesDir = context.filesDir
        val nativeLibraryDir: File? = try {
            context.applicationInfo.nativeLibraryDir?.let { File(it) }?.takeIf { it.exists() && it.isDirectory }
        } catch (_: Exception) { null }
        AppLogger.i(TAG, "nativeLibraryDir: ${nativeLibraryDir?.absolutePath ?: "NULL"} (exists=${nativeLibraryDir?.exists()})")
        diagLine("filesDir=${filesDir.absolutePath}  r=${filesDir.canRead()} w=${filesDir.canWrite()}")
        diagLine("nativeLibraryDir=${nativeLibraryDir?.absolutePath ?: "NULL"}  exists=${nativeLibraryDir?.exists()}")

        val hexagonDir = File(filesDir, "hexagon")
        if (!hexagonDir.exists()) {
            hexagonDir.mkdirs()
            AppLogger.i(TAG, "Created hexagon directory: ${hexagonDir.absolutePath}")
        }

        val configFile = File(filesDir, CONFIG_NAME)
        if (!configFile.exists() || configFile.length() == 0L) {
            copyAssetToFile(CONFIG_NAME, configFile)
            AppLogger.i(TAG, "Copied config file: ${configFile.absolutePath} size=${configFile.length()}")
        }

        // ---- Model: source-specific prepare + strict validate -------------------
        AppLogger.i(TAG, "Preparing model: source=$source customSet=${!customStoredPath.isNullOrBlank()}")
        val modelFile: File = when (source) {
            ModelSource.BUNDLED_ASSET -> {
                // 2026-08-02 AI-START BUG FIX — always use the absolute path that
                // ensureBundledCopied actually wrote to disk (the only thing we
                // 100% know exists + is valid). NEVER call bundledFile(context)
                // here — it defaults to preferPlaintext=false and returns a
                // .txt.gz name that may NOT exist if the APK shipped the .bin
                // renamed form OR the aapt2-expanded .txt form. That mismatched
                // filename caused every real device install to fail preflight
                // with "modelFile missing/unreadable" → the user-visible toast.
                val prepared = ModelManager.ensureBundledCopied(context)
                if (prepared.isFailure) {
                    AppLogger.e(TAG, "BUNDLED_ASSET ensureBundledCopied failed: ${prepared.exceptionOrNull()?.message}")
                    diagLine("ensureBundledCopied FAILED: ${prepared.exceptionOrNull()?.message}")
                    lastStartDiagnostic = diag.toString()
                    return@withContext false
                }
                val actual = File(prepared.getOrThrow())
                // validateOrDelete is belt-and-suspenders (it uses
                // resolveModelFile tie-break and handles corrupt cached copies
                // from previous broken builds). After that passes we still use
                // the *actual* path from ensureBundledCopied, not the guess from
                // resolveModelFile.
                if (!ModelManager.validateOrDelete(context, ModelSource.BUNDLED_ASSET, null)) {
                    // validateOrDelete may have deleted a corrupt cached copy.
                    // Re-run ensureBundledCopied once more — it will re-copy
                    // from APK assets. If THAT also fails, give up.
                    val retry = ModelManager.ensureBundledCopied(context)
                    if (retry.isFailure) {
                        AppLogger.e(TAG, "BUNDLED_ASSET validation FAILED after re-copy — APK missing assets/models entry?")
                        diagLine("BUNDLED_ASSET retry ensureBundledCopied FAILED: ${retry.exceptionOrNull()?.message}")
                        lastStartDiagnostic = diag.toString()
                        return@withContext false
                    }
                    val again = File(retry.getOrThrow())
                    if (!again.exists() || !ModelManager.isValidModelFile(again)) {
                        AppLogger.e(TAG, "BUNDLED_ASSET still INVALID after retry copy: path=${again.absolutePath} size=${again.length()}")
                        diagLine("BUNDLED_ASSET retry after validateOrDelete still INVALID: path=${again.absolutePath} size=${again.length()} valid=${ModelManager.isValidModelFile(again)}")
                        lastStartDiagnostic = diag.toString()
                        return@withContext false
                    }
                    again
                } else {
                    // validateOrDelete passed; ensure the *actual* file we copied
                    // is still readable before we hand it to libkatago.
                    if (!actual.isFile || !actual.canRead() || !ModelManager.isValidModelFile(actual)) {
                        AppLogger.w(TAG, "BUNDLED_ASSET validateOrDelete passed but actual copied file bad — path=${actual.absolutePath} size=${actual.length()}. Falling back to resolveModelFile tie-break.")
                        ModelManager.resolveModelFile(context, ModelSource.BUNDLED_ASSET, null)
                    } else {
                        actual
                    }
                }
            }
            ModelSource.DOWNLOADED -> {
                if (!ModelManager.validateOrDelete(context, ModelSource.DOWNLOADED, null)) {
                    AppLogger.w(TAG, "DOWNLOADED not available → downloading from katagotraining.org now...")
                    val d = ModelManager.downloadModel(context)
                    if (d.isFailure) {
                        AppLogger.e(TAG, "DOWNLOADED prepare failed: ${d.exceptionOrNull()?.message}")
                        diagLine("DOWNLOADED download FAILED: ${d.exceptionOrNull()?.message}")
                        lastStartDiagnostic = diag.toString()
                        return@withContext false
                    }
                }
                ModelManager.downloadedFile(context)
            }
            ModelSource.CUSTOM -> {
                if (customStoredPath.isNullOrBlank()) {
                    AppLogger.e(TAG, "CUSTOM source selected but SettingsStore.customModelPath is empty — user has never imported a file, start aborted")
                    diagLine("CUSTOM customStoredPath blank (user has never imported)")
                    lastStartDiagnostic = diag.toString()
                    return@withContext false
                }
                if (!ModelManager.validateOrDelete(context, ModelSource.CUSTOM, customStoredPath)) {
                    AppLogger.e(TAG, "CUSTOM model FAILED strict validation (corrupt/removed? storedPath=$customStoredPath) — please re-import via Settings → start aborted")
                    diagLine("CUSTOM model validateOrDelete FAILED storedPath=$customStoredPath")
                    lastStartDiagnostic = diag.toString()
                    return@withContext false
                }
                ModelManager.resolveModelFile(context, ModelSource.CUSTOM, customStoredPath)
            }
            // else — exhaustive fallback; normally unreachable for 3-valued enum.
            else -> {
                AppLogger.e(TAG, "Unknown ModelSource=$source — cannot prepare model, start aborted")
                diagLine("Unknown ModelSource=$source (exhaustive fallback fired)")
                lastStartDiagnostic = diag.toString()
                return@withContext false
            }
        }
        AppLogger.i(TAG, "Model final: ${modelFile.absolutePath} (exists=${modelFile.exists()} size=${modelFile.length()})")
        AppLogger.i(TAG, "Config final: ${configFile.absolutePath} (exists=${configFile.exists()} size=${configFile.length()})")
        // 2026-08-02 DIAGNOSTIC-TO-TOAST: write exact on-disk bytes + header hex for
        // model & config (no "I think / probably" — exact numbers, verified every run).
        run {
            fun firstHex(f: File, n: Int): String = runCatching {
                val b = ByteArray(n)
                val r = java.io.FileInputStream(f).use { it.read(b) }
                (0 until r).joinToString(" ") { "%02x".format(b[it]) }
            }.getOrDefault("(read-failed)")
            diagLine("--- model_file ---")
            diagLine("  path   = ${modelFile.absolutePath}")
            diagLine("  exists = ${modelFile.isFile}  readable = ${modelFile.canRead()}  size = ${modelFile.length()}")
            diagLine("  first-8-hex = ${firstHex(modelFile, 8)}")
            diagLine("--- config_file ---")
            diagLine("  path   = ${configFile.absolutePath}")
            diagLine("  exists = ${configFile.isFile}  readable = ${configFile.canRead()}  size = ${configFile.length()}")
            diagLine("  first-8-hex = ${firstHex(configFile, 8)}")
        }
        run {
            val problems = mutableListOf<String>()
            if (!configFile.isFile || configFile.length() < 1000L || !configFile.canRead()) problems += "configFile invalid/missing (expect 7KB+ readable gtp cfg)"
            if (!modelFile.isFile || !modelFile.canRead()) problems += "modelFile(source=$source) missing/unreadable"
            diagLine("--- preflight ---")
            if (problems.isEmpty()) diagLine("  OK (pass)") else diagLine("  FAIL: ${problems.joinToString(" | ")}")
            if (problems.isNotEmpty()) {
                AppLogger.e(TAG, "PREFLIGHT FAIL — refusing to launch: ${problems.joinToString()}")
                lastStartDiagnostic = diag.toString()
                return@withContext false
            }
        }

        // ---- Install engine binary into filesDir (exec source) ------------------
        //
        // RELIABILITY REVERT layout:
        //   nativeLibraryDir/     ← 6 OS-extracted .so (DEPENDENCY SEARCH, PRIMARY)
        //       libkatago.so        (may be noexec in some vendor builds)
        //       libc++_shared.so
        //       libcalculator.so
        //       libffi.so
        //       libmain.so
        //       libcdsprpc.so
        //   filesDir/             ← COPY of libkatago.so only (EXEC CANDIDATE, chmod +x)
        //       libkatago.so        (from assets/libkatago.so, size-checked)
        //
        // Deps are NOT copied to filesDir any more — they are read DIRECTLY from
        // nativeLibraryDir via LD_LIBRARY_PATH (head of the path list). This is
        // exactly how upstream badukai launches and should remove 100% of the
        // "copy deps → LD_LIBRARY_PATH=filesDir" surface area that broke launch.
        // ---- 8< ----
        val filesDirBinary = File(filesDir, BINARY_NAME)
        // Prefer nativeLibraryDir/libkatago.so as the COPY SOURCE for exec bit
        // reason (OS already verified the ABI, sha matches apk signature);
        // fall back to assets/libkatago.so only if jniLibs copy is missing.
        val jniBinary: File? = nativeLibraryDir?.resolve(BINARY_NAME)
            ?.takeIf { it.exists() && it.isFile && it.length() > 1_000_000 }
        val assetAvailable = try {
            context.assets.open(BINARY_NAME).close(); true
        } catch (_: Exception) {
            false
        }
        AppLogger.i(TAG, "Binary sources: jniLibs=${jniBinary?.absolutePath} (exists=${jniBinary?.exists()}), assets=$assetAvailable")
        val copySource = when {
            jniBinary != null -> {
                AppLogger.i(TAG, "Binary copy source = jniLibs (preferred)")
                "jni"
            }
            assetAvailable -> {
                AppLogger.i(TAG, "Binary copy source = assets (fallback)")
                "assets"
            }
            else -> {
                AppLogger.e(TAG, "FATAL: no binary source available! Need either jniLibs/$BINARY_NAME or assets/$BINARY_NAME packaged.")
                return@withContext false
            }
        }
        val needCopy = when {
            !filesDirBinary.exists() -> true
            jniBinary != null -> filesDirBinary.length() != jniBinary.length()
            else -> shouldUpdateBinary(filesDirBinary) // asset size compare fallback
        }
        if (needCopy) {
            when (copySource) {
                "jni" -> {
                    jniBinary!!.inputStream().use { inp ->
                        java.io.FileOutputStream(filesDirBinary).use { out -> inp.copyTo(out) }
                    }
                    AppLogger.i(TAG, "Installed jniLibs→filesDir binary: ${filesDirBinary.absolutePath} size=${filesDirBinary.length()}")
                }
                else -> {
                    copyAssetToFile(BINARY_NAME, filesDirBinary)
                    AppLogger.i(TAG, "Installed assets→filesDir binary: ${filesDirBinary.absolutePath} size=${filesDirBinary.length()}")
                }
            }
            try { filesDirBinary.setExecutable(true, false) } catch (_: Exception) {}
        } else {
            AppLogger.i(TAG, "filesDir binary up-to-date: ${filesDirBinary.absolutePath} size=${filesDirBinary.length()}")
        }

        // Also try chmod +x on nativeLibraryDir/libkatago.so as a DIRECT EXEC CANDIDATE
        // (Plan 0 below: straight exec from OS-extracted location). This almost always
        // fails due to SELinux / nosuid / noexec on /data/app-lib, but cost is 1 syscall.
        if (jniBinary != null) {
            val ok = try { jniBinary.setExecutable(true, false) } catch (_: Exception) { false }
            AppLogger.i(TAG, "chmod +x jniLibs/$BINARY_NAME → $ok (direct exec candidate)")
        }
        diagLine("--- binary_install ---")
        diagLine("  jniBinary.source=${jniBinary?.absolutePath ?: "NULL"}  size=${jniBinary?.length() ?: -1}")
        diagLine("  filesDirBinary=${filesDirBinary.absolutePath}  size=${filesDirBinary.length()}  exists=${filesDirBinary.isFile}")
        diagLine("  assetAvailable=$assetAvailable")
        diagLine("  jniBinary.chmod_exec_ok=${jniBinary?.canExecute() ?: false}  filesDirBinary.chmod_exec_ok=${filesDirBinary.canExecute()}")
        // ---- ---- 8< ---- end of install ----

        // ---- Shared env / args ---------------------------------------------------
        // RELIABILITY REVERT: nativeLibraryDir AT HEAD of LD_LIBRARY_PATH so dlopen
        // finds libc++_shared.so / libcalculator.so / etc via the OS-extracted copies
        // FIRST (zero copy, zero latency, zero copy-version-mismatch risk).
        // filesDir still in the list for any stale copies from old builds, and
        // vendor paths for OpenCL ICDs / DSP stubs.
        val envBase = LinkedHashMap<String, String>().apply {
            put("LD_LIBRARY_PATH", listOfNotNull(
                nativeLibraryDir?.absolutePath,   // PRIMARY dep dir (OS extracted, trusted)
                filesDir.absolutePath,            // fallback (old copies / direct)
                hexagonDir.absolutePath,
                "/vendor/lib64",
                "/system/vendor/lib64"
            ).joinToString(":"))
            put("ADSP_LIBRARY_PATH", listOfNotNull(
                nativeLibraryDir?.absolutePath,
                filesDir.absolutePath,
                hexagonDir.absolutePath,
                "/system/lib/rfsa/adsp",
                "/system/vendor/lib/rfsa/adsp",
                "/dsp"
            ).joinToString(";"))
            put("HOME", filesDir.absolutePath)
        }
        AppLogger.i(TAG, "LD_LIBRARY_PATH = ${envBase["LD_LIBRARY_PATH"]}")
        val gtpArgs = listOf("gtp", "-model", modelFile.absolutePath, "-config", configFile.absolutePath)
        val linker64 = LINKER64_CANDIDATES.firstOrNull { File(it).exists() }
        AppLogger.i(TAG, "Detected linker64: ${linker64 ?: "NONE — linker64 plans skipped"}")
        diagLine("--- launch_env ---")
        diagLine("  linker64 = ${linker64 ?: "NONE"}")
        diagLine("  LD_LIBRARY_PATH = ${envBase["LD_LIBRARY_PATH"]}")
        diagLine("  ADSP_LIBRARY_PATH = ${envBase["ADSP_LIBRARY_PATH"]}")
        diagLine("  gtpArgs  = ${gtpArgs.joinToString("  ")}")
        AppLogger.i(TAG, "nativeLibraryDir so inventory (PRIMARY dep-search set):")
        diagLine("--- jniLibs/ inventory (nativeLibraryDir) ---")
        nativeLibraryDir?.listFiles()?.filter { it.name.endsWith(".so") }
            ?.sortedByDescending { it.length() }
            ?.forEach { f ->
                AppLogger.i(TAG, "  ${f.name}  ${f.length()} bytes  exec=${f.canExecute()}")
                diagLine("  ${f.name}  size=${f.length()}  exec=${f.canExecute()}")
            } ?: diagLine("  (nativeLibraryDir is NULL or empty)")
        AppLogger.i(TAG, "filesDir so inventory (exec-candidate set):")
        diagLine("--- filesDir/ .so inventory ---")
        filesDir.listFiles()?.filter { it.name.endsWith(".so") }
            ?.sortedByDescending { it.length() }
            ?.forEach { f ->
                AppLogger.i(TAG, "  ${f.name}  ${f.length()} bytes  exec=${f.canExecute()}")
                diagLine("  ${f.name}  size=${f.length()}  exec=${f.canExecute()}")
            } ?: diagLine("  (none)")

        // ---- Build launch plan list ----------------------------------------------
        // Try jniLibs direct locations FIRST, then filesDir copies, with & without
        // linker64 interposer. Total 4–6 plans, with the "always worked" upstream
        // equivalent (linker64 + jniLibs binary) tried BEFORE any filesDir copies.
        val plans = mutableListOf<StartPlan>().apply {
            if (jniBinary != null) {
                if (linker64 != null) add(StartPlan("Plan A1: jniLibs binary via linker64 (upstream default)", jniBinary, true))
                add(StartPlan("Plan A2: jniLibs binary direct (PIE exec)", jniBinary, false))
            }
            if (linker64 != null) add(StartPlan("Plan B1: filesDir binary via linker64", filesDirBinary, true))
            add(StartPlan("Plan B2: filesDir binary direct (PIE exec)", filesDirBinary, false))
        }.toList()
        AppLogger.i(TAG, "Engine start plans (in order, ${plans.size} total): ${plans.joinToString { it.label }}")

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
            diagLine("--- Plan $attempt/${plans.size}: ${plan.label} ---")
            diagLine("  cmd = ${cmd.joinToString(" ")}")

            val outcome = try {
                runOnce(cmd, envBase, hexagonDir)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Plan $attempt/${plans.size} exception: ${e::class.java.simpleName} ${e.message}")
                lastFailureReason = "${plan.label} exception: ${e.message}"
                diagLine("  exception = ${e::class.java.simpleName}: ${e.message}")
                RunOnceOutcome.Dead(null, listOf("exception: ${e::class.java.simpleName}: ${e.message}"), emptyList())
            }

            when (outcome) {
                is RunOnceOutcome.Alive -> {
                    val result = outcome.result
                    process = result.process
                    writer = result.writer
                    reader = result.reader
                    errorReader = result.errorReader
                    isRunning.set(true)
                    currentModel = "$source:${modelFile.name}"
                    _isReady.value = true
                    startReaderJob()
                    startErrorReaderJob()
                    AppLogger.i(TAG, "=== ENGINE STARTED SUCCESSFULLY via ${plan.label} ===")
                    diagLine("  plan_status = OK (engine alive after 2s)")
                    lastStartDiagnostic = diag.appendLine("=== OUTCOME: SUCCESS via ${plan.label} ===").toString()
                    return@withContext true
                }
                is RunOnceOutcome.Dead -> {
                    lastFailureReason = "${plan.label} died: exit=${outcome.exitCode}; stderr-head=${outcome.stderrTail40.firstOrNull() ?: "(empty)"}"
                    AppLogger.e(TAG, "Plan $attempt/${plans.size} FAILED: ${plan.label}")
                    diagLine("  plan_status = DIED within 2s")
                    diagLine("  exit = ${outcome.exitCode}")
                    if (outcome.stderrTail40.isNotEmpty()) {
                        diagLine("  stderr-tail (last ${outcome.stderrTail40.size} lines):")
                        outcome.stderrTail40.forEachIndexed { i, ln ->
                            val lineNo = (i + 1).toString().padStart(2, '0')
                            diagLine("    [$lineNo] $ln")
                        }
                    } else {
                        diagLine("  stderr = (empty)")
                    }
                    if (outcome.stdoutTail20.isNotEmpty()) {
                        diagLine("  stdout-tail (last ${outcome.stdoutTail20.size} lines):")
                        outcome.stdoutTail20.forEachIndexed { i, ln ->
                            val lineNo = (i + 1).toString().padStart(2, '0')
                            diagLine("    [$lineNo] $ln")
                        }
                    }
                }
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
        diagLine("=== OUTCOME: ALL ${plans.size} PLANS FAILED ===")
        lastFailureReason?.let { diagLine("final_reason = $it") }
        val finalDiag = diag.toString()
        lastStartDiagnostic = finalDiag

        // 2026-08-02 DIAG TO EXTERNAL FILE (so user never has to scroll-screenshot
        // the Compose top-bar). App already uses getExternalFilesDir() for SGF +
        // logs (see AppLogger.initialize + GameViewModel.saveGame) so no new
        // permissions needed. File is world-readable via MTP so user can:
        //   1) plug USB → Android File Transfer → Internal storage
        //        → Android/data/com.badukai.next/files/
        //        → ai_last_fail_diag.txt  → send via IM/attach to issue
        //   2) OR just long-press in 'Files' app.
        // We write BOTH (a) latest (constant name, always overwritten) +
        // (b) timestamped archive (user can attach the exact one). This gives us
        // the Plan-level stderr/stdout tails we NEED without relying on a
        // scroll-screenshot of a top-bar Text element.
        runCatching {
            val base: File? = context.getExternalFilesDir(null)
                ?: context.filesDir
            val dir = (base ?: context.filesDir).absoluteFile
            dir.mkdirs()
            val latest = File(dir, "ai_last_fail_diag.txt")
            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val stamp = File(dir, "ai_fail_diag_$ts.txt")
            val header = listOf(
                "AI start failure diagnostic dump",
                "UTC ms     : " + System.currentTimeMillis(),
                "package    : " + context.packageName,
                "modelPath  : " + modelFile.absolutePath + "  size=" + modelFile.length(),
                "configPath : " + configFile.absolutePath + "  size=" + configFile.length(),
                "",
            ).joinToString("\n")
            val body = header + finalDiag
            latest.writeText(body)
            stamp.writeText(body)
            AppLogger.i(TAG, "DIAGFILE latest=${latest.absolutePath} archive=${stamp.absolutePath}  bytes=${latest.length()}")
            // Embed file paths directly IN the toast text (above scroll cutoff) so
            // user sees 'where to get the file' even if the scroll-screenshot is
            // truncated again on the next run.
            diag.insert(0, "[DIAGFILES written for next attempt]\n  latest=${latest.absolutePath}\n  archive=${stamp.absolutePath}\n\n")
            lastStartDiagnostic = diag.toString()
        }.onFailure { t ->
            AppLogger.w(TAG, "DIAGFILE write failed (non-fatal, keep toast path only): ${t::class.java.simpleName} ${t.message}")
        }

        false
    }

    /**
     * Try-launch once. Returns [RunOnceOutcome.Alive] with process pipes when
     * alive after 2s, or [RunOnceOutcome.Dead] with exit+stderr+stdout tail
     * when it died within the grace window (diagnostic toast readable by user
     * without needing adb logcat).
     */
    private fun runOnce(
        cmd: List<String>,
        envBase: Map<String, String>,
        workingDir: File
    ): RunOnceOutcome {
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
        // Store last-40 stderr lines + last-20 stdout lines for diagnostic toast.
        // These buffers are only consumed on the DIED path; if alive we don't read
        // because the reader/errorReader coroutine jobs will own them.
        val diagStderrBuf = mutableListOf<String>()
        val diagStdoutBuf = mutableListOf<String>()
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
            // Process died within 2s → fully drain stderr/stdout into diagnostic
            // buffers first, then log + feed to lastStartDiag upstream.
            val errLines = mutableListOf<String>()
            val outLines = mutableListOf<String>()
            val errTailSb = StringBuilder()
            runCatching {
                repeat(80) {
                    val ln = er.readLine() ?: return@repeat
                    errLines += ln
                    errTailSb.append(ln).append('\n')
                    if (errLines.size > 80) errLines.removeAt(0)
                }
            }
            runCatching {
                repeat(40) {
                    val ln = r.readLine() ?: return@repeat
                    outLines += ln
                    if (outLines.size > 40) outLines.removeAt(0)
                }
            }
            val errTail = errTailSb.toString().trim()
            val outTail = outLines.joinToString("\n").take(2000)
            diagStderrBuf.clear(); diagStderrBuf.addAll(errLines.takeLast(40))
            diagStdoutBuf.clear(); diagStdoutBuf.addAll(outLines.takeLast(20))
            AppLogger.e(TAG, "Process died immediately! exit=$exitCode")
            if (errTail.isNotBlank()) AppLogger.e(TAG, "Stderr full:\n$errTail")
            if (outTail.isNotBlank()) AppLogger.e(TAG, "Stdout head:\n$outTail")
            try { w.close() } catch (_: Exception) {}
            try { r.close() } catch (_: Exception) {}
            try { er.close() } catch (_: Exception) {}
            try { p.destroyForcibly() } catch (_: Exception) {}
            return RunOnceOutcome.Dead(exitCode, errLines.takeLast(40), outLines.takeLast(20))
        }

        return RunOnceOutcome.Alive(RunOnceResult(p, w, r, er))
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
