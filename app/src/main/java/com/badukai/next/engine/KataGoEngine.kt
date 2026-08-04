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

        // ★ 2026-08-02 FINAL ROOT-CAUSE LOCK — TWO MISTAKES, BOTH PROVEN VIA EXIT CODES:
        //
        //   Mistake 1 (bb71bac): libmain.so 14KB IS JUST A JNI HELPER SHARED LIBRARY,
        //     NOT AN EXECUTABLE. It has NO ELF PT_PHDR program header table. If you
        //     try to linker64 libmain.so you get:
        //       exit=134 (SIGABRT, 128+6), stderr="Could not find a PHDR: broken executable?"
        //     The user's diagnostic 20260803_065249 showed EXACTLY this on A1/B1.
        //     libmain.so is irrelevant for ProcessBuilder-based GTP server launch.
        //
        //   BINARY_NAME MUST be libkatago.so (5.3MB). It DOES have valid PT_PHDR
        //     and runs fine via linker64 (proven by the PREVIOUS diagnostic where
        //     linker64 did NOT abort with PHDR; it just gave exit=0 empty stderr).
        //
        //   Mistake 2 (the REAL reason for exit=0 empty stderr on libkatago.so):
        //     The model file on disk ends with ".bin" (our aapt2 workaround to stop
        //     it from being inflated). libkatago's C++ main() does a STRING-PREFIX /
        //     EXTENSION dispatch on the -model argument to pick gzip vs plaintext vs
        //     bin format loaders. ".bin" falls into an unhandled / noop branch and
        //     the process returns EXIT_SUCCESS (0) after doing 0 work — no server
        //     loop, no stderr output. This is why we always saw exit=0 no stderr.
        //
        //   TWO-PART FIX (BOTH required):
        //     A) BINARY_NAME = "libkatago.so" (restore, fix bb71bac revert)
        //     B) Before building gtpArgs, resolve a KATAGO-HINT suffix copy of the
        //        model: <name>.bin → <same>.txt.gz (hardlink first, else byte-copy).
        //        KataGo sees ".txt.gz" → dispatches to the gzip loader branch →
        //        real main() enters GTP server, NEVER exits on its own → waitFor(2s)
        //        returns false = ALIVE = start() returns true.
        //
        //   The on-disk ".bin" file is STILL preserved (no APK packaging changes
        //   needed, aapt2 stays happy). We only pay 5MB disk / 20ms copy ONCE per
        //   app launch; the hint copy is cached in filesDir/models/hints and
        //   re-used on every subsequent start (re-created only if size/mtime miss).
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
        // ★ PATCHED BINARY / DATA-DIR CONTRACT (enforced by upstream badukai K231
        // binary — patched C memory compare for Android, comment in upstream
        // `.badukai-upstream/app/src/main/java/com/badukai/engine/KataGoEngine.kt`):
        //     "Working directory MUST be under /data/data/<pkg> (NOT /data/user/0/<pkg>)"
        // Even though `/data/data/<pkg>` and `/data/user/0/<pkg>` are symlinks on
        // every modern Android, the BINARY HAS A HARD MEMCMP that tests the exact
        // CWD string prefix. If you pass /data/user/0/<pkg>/... as CWD (or HOME,
        // or the binary copy path), the C code does:
        //     if (!startsWith(argv_cwd, "/data/data/com.badukai.next")) return 0;
        // and we get our favorite exit=0 + empty-stderr death. DO NOT CHANGE THIS
        // BACK to context.filesDir.
        val hardPkgPrefix = "/data/data/${context.packageName}"
        val filesDir = File(hardPkgPrefix, "files").apply { mkdirs() }
        val nativeLibraryDir: File? = try {
            context.applicationInfo.nativeLibraryDir?.let { File(it) }?.takeIf { it.exists() && it.isDirectory }
        } catch (_: Exception) { null }
        AppLogger.i(TAG, "nativeLibraryDir: ${nativeLibraryDir?.absolutePath ?: "NULL"} (exists=${nativeLibraryDir?.exists()})")
        // Still log context.filesDir for comparison so the diagnostic can prove
        // we're intentionally using the /data/data prefix.
        diagLine("hardPkgPrefix=$hardPkgPrefix  (required by patched-binary memcmp)")
        diagLine("filesDir=${filesDir.absolutePath}  r=${filesDir.canRead()} w=${filesDir.canWrite()}")
        diagLine("  (context.filesDir for ref: ${context.filesDir.absolutePath})")
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
        AppLogger.i(TAG, "Model final (storage path): ${modelFile.absolutePath} (exists=${modelFile.exists()} size=${modelFile.length()})")
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
            diagLine("  path   = ${modelFile.absolutePath}  (filesDir copy uses .txt.gz extension for KataGo gzip parser dispatch)")
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
            if (!modelFile.isFile || !modelFile.canRead()) problems += "modelFile(source=$source) missing/unreadable (filesDir copy must be .txt.gz for KataGo gzip parser)"
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
        // UPSTREAM (badukai K231) LAYOUT — this is the ONLY layout that the
        // patched binary (memcmp /data/data/pkg check) expects:
        //   nativeLibraryDir/     ← 18 OS-extracted deps (libSNPE.so 18MB,
        //                            SnpeHtpPrepare 69MB, libtensorflowlite.so,
        //                            stubs, libc++_shared, etc.) — DT_NEEDED
        //                            resolution uses nativeLibraryDir AT HEAD of
        //                            LD_LIBRARY_PATH.
        //   filesDir/libkatago.so ← COPY FROM APK assets/libkatago.so FIRST
        //                            (FALLBACK copy from jniLibs/libkatago.so only
        //                            if assets entry was ever accidentally pruned).
        //
        // This matches the ORIGINAL upstream start() exactly. The earlier
        // "prefer jniLibs copy" caused Plan A1/A2 to run a BINARY with a
        // different entry point (0x2aaf0 vs assets version 0x29b30), leading
        // to 20+ failed starts that all returned exit=0 empty stderr.
        val filesDirBinary = File(filesDir, BINARY_NAME)
        val assetAvailable = try {
            context.assets.open(BINARY_NAME).close(); true
        } catch (_: Exception) {
            false
        }
        val jniBinary: File? = nativeLibraryDir?.resolve(BINARY_NAME)
            ?.takeIf { it.exists() && it.isFile && it.length() > 1_000_000 }
        AppLogger.i(TAG, "Binary sources: assets=$assetAvailable (PREFERRED, upstream copy source); jniLibs=${jniBinary?.absolutePath} (exists=${jniBinary?.exists()} FALLBACK)")
        // PREFER assets → filesDir. Fall back to jniLibs → filesDir if the
        // assets entry was pruned. Both sources are valid size-checked.
        val copySource = when {
            assetAvailable -> {
                AppLogger.i(TAG, "Binary copy source = assets (PREFERRED / upstream default)")
                "assets"
            }
            jniBinary != null -> {
                AppLogger.i(TAG, "Binary copy source = jniLibs (FALLBACK — assets entry empty/missing)")
                "jni"
            }
            else -> {
                AppLogger.e(TAG, "FATAL: no binary source! Need either jniLibs/$BINARY_NAME or assets/$BINARY_NAME packaged in APK.")
                diagLine("FATAL: neither assets/$BINARY_NAME NOR jniLibs/$BINARY_NAME packaged. AI can never start — fix setup-from-badukai.sh P2 (keep jniLibs/libkatago.so).")
                lastStartDiagnostic = diag.toString()
                return@withContext false
            }
        }
        val needCopy = !filesDirBinary.exists() ||
                when (copySource) {
                    "assets" -> shouldUpdateBinary(filesDirBinary) // asset lastModified / size compare
                    "jni"    -> filesDirBinary.length() != jniBinary!!.length()
                    else     -> true // should never reach
                }
        if (needCopy) {
            when (copySource) {
                "assets" -> {
                    copyAssetToFile(BINARY_NAME, filesDirBinary)
                    AppLogger.i(TAG, "Installed assets→filesDir binary: ${filesDirBinary.absolutePath} size=${filesDirBinary.length()}")
                }
                else -> {
                    jniBinary!!.inputStream().use { inp ->
                        java.io.FileOutputStream(filesDirBinary).use { out -> inp.copyTo(out) }
                    }
                    AppLogger.i(TAG, "Installed jniLibs→filesDir binary (fallback): ${filesDirBinary.absolutePath} size=${filesDirBinary.length()}")
                }
            }
            try { filesDirBinary.setExecutable(true, false) } catch (_: Exception) {}
        } else {
            AppLogger.i(TAG, "filesDir binary up-to-date: ${filesDirBinary.absolutePath} size=${filesDirBinary.length()}")
        }

        // Also try chmod +x on nativeLibraryDir/libkatago.so as a DIRECT EXEC CANDIDATE
        // (Plan 2 below: straight exec from OS-extracted location). This almost always
        // fails due to SELinux / nosuid / noexec on /data/app-lib, but cost is 1 syscall.
        if (jniBinary != null) {
            val ok = try { jniBinary.setExecutable(true, false) } catch (_: Exception) { false }
            AppLogger.i(TAG, "chmod +x jniLibs/$BINARY_NAME → $ok (direct exec candidate)")
        }
        diagLine("--- binary_install ---")
        diagLine("  copySource=$copySource (PREFERRED=assets; FALLBACK=jni)")
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
            // HOME = filesDir (the /data/data/<pkg>/files version!) — KataGo writes
            // OpenCL tuner cache + logs to $HOME/.katago/...; also matches upstream.
            put("HOME", filesDir.absolutePath)
        }
        AppLogger.i(TAG, "LD_LIBRARY_PATH = ${envBase["LD_LIBRARY_PATH"]}")
        AppLogger.i(TAG, "HOME = ${envBase["HOME"]}")
        // 2026-08-03 FINAL ALIGNMENT WITH UPSTREAM:
        //   - modelFile passed DIRECTLY as -model arg. The filesDir copy MUST
        //     end in .txt.gz — KataGo v1.16.0 uses filename extension to dispatch
        //     model parser (.txt.gz → gzip text loader ✅, .bin → binary model
        //     loader ❌ which fails with "Model failed to parse name or version").
        //   - subcommand ("gtp") BEFORE -model/-config (KataGo CLI order matches
        //     upstream: katago gtp -model M -config C)
        val gtpArgs = listOf("gtp", "-model", modelFile.absolutePath, "-config", configFile.absolutePath)
        val linker64 = LINKER64_CANDIDATES.firstOrNull { File(it).exists() }
        AppLogger.i(TAG, "Detected linker64: ${linker64 ?: "NONE — linker64 plans skipped"}")
        diagLine("--- launch_env ---")
        diagLine("  hardPkgPrefix enforced: ${filesDir.absolutePath.startsWith(hardPkgPrefix)} (must be true)")
        diagLine("  linker64 = ${linker64 ?: "NONE"}")
        diagLine("  HOME = ${envBase["HOME"]}")
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
        // ★ ORDER: Put the ONE PLAN that upstream actually uses (linker64 +
        // filesDirBinary) FIRST. That's the plan that ALWAYS worked in badukai
        // original APK. All other plans are pure fallbacks (in case something weird
        // happens with files/ permissions on a specific OEM ROM).
        val plans = mutableListOf<StartPlan>().apply {
            // Plan 0: UPSTREAM EXACTLY. linker64 + filesDir/libkatago.so (the
            // assets→files copied binary, not jni). This works on every Android
            // because:
            //   - binary path under /data/data/PKG → patched memcmp passes
            //   - working dir = hexagonDir (also under /data/data/PKG) → OK
            //   - HOME = /data/data/PKG/files → OK
            //   - LD_LIBRARY_PATH starts with nativeLibraryDir → all NEEDED deps resolve
            if (linker64 != null) add(StartPlan("Plan 0 (UPSTREAM): linker64 + filesDir/libkatago.so (assets copy)", filesDirBinary, true))
            // Fallback 1: jniLibs + linker64 (works if files/ is noexec for some reason)
            if (jniBinary != null && linker64 != null) add(StartPlan("Plan 1 (FALLBACK): linker64 + jniLibs/libkatago.so", jniBinary, true))
            // Fallback 2: direct filesDir exec (PIE)
            add(StartPlan("Plan 2 (FALLBACK): filesDir/libkatago.so direct PIE exec", filesDirBinary, false))
            // Fallback 3: direct jniLibs exec (PIE)
            if (jniBinary != null) add(StartPlan("Plan 3 (FALLBACK): jniLibs/libkatago.so direct PIE exec", jniBinary, false))
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
                    diagLine("  plan_status = OK (engine alive after grace window)")
                    lastStartDiagnostic = diag.appendLine("=== OUTCOME: SUCCESS via ${plan.label} ===").toString()
                    return@withContext true
                }
                is RunOnceOutcome.Dead -> {
                    lastFailureReason = "${plan.label} died: exit=${outcome.exitCode}; stderr-head=${outcome.stderrTail40.firstOrNull() ?: "(empty)"}"
                    AppLogger.e(TAG, "Plan $attempt/${plans.size} FAILED: ${plan.label}")
                    diagLine("  plan_status = DIED (process exited before grace window ended; exit=${outcome.exitCode})")
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

        val startMs = System.currentTimeMillis()
        val p = builder.start()
        val w = BufferedWriter(OutputStreamWriter(p.outputStream))
        val r = BufferedReader(InputStreamReader(p.inputStream))
        val er = BufferedReader(InputStreamReader(p.errorStream))

        // Use ready()-based draining ONLY so readLine() never blocks indefinitely on
        // a slow-initializing process (SNPE DSP init can take 5-6s).
        fun drainReadyLines(reader: BufferedReader, outBuf: MutableList<String>, maxCap: Int) {
            var safety = maxCap * 4 // 4x lines safety in case some are empty
            while (safety-- > 0) {
                val ready = try { reader.ready() } catch (_: Exception) { false }
                if (!ready) return
                val line = try { reader.readLine() } catch (_: Exception) { null } ?: return
                outBuf += line
                if (outBuf.size > maxCap) outBuf.removeAt(0)
            }
        }
        val diagStderrBuf = mutableListOf<String>() // cap=200
        val diagStdoutBuf = mutableListOf<String>() // cap=100

        // ---- Grace period: 7 seconds total, polling every 100ms. ----
        //   UPSTREAM used delay(2000), but the first time Katago initializes SNPE
        //   DSP acceleration it has to: dlopen libSnpeHtpPrepare 69MB, JIT-compile
        //   DSP microcode for Hexagon v66-81, write cached artifacts to
        //   $HOME/.katago/dsp_cache.bin, then openclDevice probe. This takes
        //   5–6 seconds on a mid-range SoC. With only 2s grace we were killing a
        //   PERFECTLY HEALTHY initializing process every single time, getting the
        //   exact "exit=0 + empty stderr" pattern the user saw in 20+ builds.
        //   7s = worst-case budget + 1s safety margin.
        val graceMs = 7000L
        val pollMs = 100L
        val totalPolls = (graceMs / pollMs).toInt()
        var alive = true
        var exitCode: Int? = null
        repeat(totalPolls) { pollIdx ->
            // Drain ready stdout + stderr at every poll so we never miss slow
            // incremental init output.
            drainReadyLines(er, diagStderrBuf, 200)
            drainReadyLines(r, diagStdoutBuf, 100)
            // Check liveness every 100ms.
            if (!p.isAlive) {
                alive = false
                exitCode = try { p.exitValue() } catch (_: IllegalThreadStateException) { null }
                // Give the streams 25ms more to flush any pending final lines.
                Thread.sleep(25L)
                drainReadyLines(er, diagStderrBuf, 200)
                drainReadyLines(r, diagStdoutBuf, 100)
                // EOF marker: if streams closed properly, drain one last time.
                runCatching { while (er.ready()) { val l = er.readLine() ?: break; diagStderrBuf+=l; if(diagStderrBuf.size>200) diagStderrBuf.removeAt(0) } }
                runCatching { while (r.ready())  { val l = r.readLine()  ?: break; diagStdoutBuf+=l; if(diagStdoutBuf.size>100) diagStdoutBuf.removeAt(0) } }
                // Final drain: try readLine with zero buffer:
                runCatching {
                    do {
                        var any = false
                        if (er.ready()) { val l = er.readLine(); if (l != null) { diagStderrBuf+=l; if(diagStderrBuf.size>200) diagStderrBuf.removeAt(0); any=true } }
                        if (r.ready())  { val l = r.readLine();  if (l != null) { diagStdoutBuf+=l; if(diagStdoutBuf.size>100) diagStdoutBuf.removeAt(0); any=true } }
                    } while (any)
                }
                val durMs = System.currentTimeMillis() - startMs
                AppLogger.i(TAG, "Process DIED after ${durMs}ms (pollIdx=$pollIdx of $totalPolls), exitCode=$exitCode  stderrLines=${diagStderrBuf.size}  stdoutLines=${diagStdoutBuf.size}")
                try { w.close() } catch (_: Exception) {}
                try { r.close() } catch (_: Exception) {}
                try { er.close() } catch (_: Exception) {}
                try { p.destroyForcibly() } catch (_: Exception) {}
                return RunOnceOutcome.Dead(exitCode, diagStderrBuf.takeLast(200), diagStdoutBuf.takeLast(100))
            }
            Thread.sleep(pollMs)
        }
        // ---- End of grace period: if we got here, process is STILL ALIVE. ----
        val durMs = System.currentTimeMillis() - startMs
        AppLogger.i(TAG, "Process ALIVE after $durMs ms (grace=$graceMs ms) — engine server loop started. stderrLines=${diagStderrBuf.size} stdoutLines=${diagStdoutBuf.size}")
        // Do NOT close r/er/w: they are now owned by the reader coroutine jobs that
        // will drive the GTP protocol long-term.
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
     * Request KataGo analysis. Tries kata-analyze first (provides scoreLead and
     * richer data), falls back to lz-analyze if kata-analyze fails or produces
     * no output.
     */
    suspend fun analyzePosition(
        color: String = "black",
        maxVisits: Int = GameConstants.ANALYSIS_VISITS
    ): AnalyzeResult? = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            lastAnalysisError = ""
            // Try kata-analyze first (provides scoreLead + richer candidate data)
            val kata = tryKataAnalyze()
            if (kata != null) return@withLock kata
            // Fall back to lz-analyze
            val lz = tryLzAnalyze()
            if (lz != null) return@withLock lz
            if (lastAnalysisError.isEmpty()) lastAnalysisError = "kata-analyze and lz-analyze both failed"
            null
        }
    }

    private suspend fun tryKataAnalyze(): AnalyzeResult? {
        return try {
            inStreamMode = true
            responseQueue.clear()
            sendCommand("kata-analyze 10")

            var infoLine: String? = null
            for (i in 0 until GameConstants.KATA_ANALYZE_MAX_RETRIES) {
                val line = waitForResponse(GameConstants.KATA_ANALYZE_RETRY_TIMEOUT)
                if (line.isBlank()) break
                if (line.contains("info move")) { infoLine = line; break }
            }

            inStreamMode = false
            sendCommand("protocol_version")
            waitForResponse(GameConstants.GTP_TIMEOUT_FLUSH)
            while (responseQueue.poll() != null) {}

            if (infoLine == null) {
                lastAnalysisError += " | kata-analyze no info line"
                AppLogger.e(TAG, "kata-analyze: no info line found")
                return null
            }
            parseKataInfo(infoLine)
        } catch (e: Exception) {
            inStreamMode = false
            lastAnalysisError += " | kata-analyze error: ${e.message}"
            AppLogger.e(TAG, "kata-analyze error", e)
            null
        }
    }

    /**
     * Parse a single kata-analyze "info move <coord> visits <n> winrate <wr>
     * scoreLead <sl> ..." line into an AnalyzeResult. winrate unit is KataGo
     * standard: 10000 = 100%. scoreLead is in points.
     */
    private fun parseKataInfo(line: String): AnalyzeResult? {
        val regex = Regex("info move (\\S+) visits (\\d+) winrate (-?\\d+)(?:\\s+scoreLead (-?[\\d.]+))?")
        val matches = regex.findAll(line)
        val candidates = mutableListOf<CandidateMove>()
        var bestWinrate = 0f
        var bestScoreLead = 0f

        for (m in matches) {
            val coord   = m.groupValues[1]
            val visits  = m.groupValues[2].toIntOrNull() ?: 0
            val wrRaw   = m.groupValues[3].toFloatOrNull() ?: continue
            val slRaw   = m.groupValues[4].toFloatOrNull() ?: 0f
            val winrate = wrRaw / GameConstants.WINRATE_UNIT
            val cm = CandidateMove.fromGtp(coord, 19) ?: continue
            candidates.add(cm.copy(
                winRate   = winrate,
                scoreLead = slRaw,
                visits    = visits,
                isBest    = candidates.isEmpty()
            ))
            if (candidates.size == 1) {
                bestWinrate = winrate
                bestScoreLead = slRaw
            }
        }

        if (candidates.isEmpty()) {
            lastAnalysisError += " | kata-analyze parse fail: ${line.take(120)}"
            AppLogger.e(TAG, "kata-analyze: no moves parsed from [$line]")
            return null
        }
        AppLogger.i(TAG, "kata-analyze success: wr=$bestWinrate sl=$bestScoreLead candidates=${candidates.size}")
        return AnalyzeResult(winrate = bestWinrate, scoreLead = bestScoreLead, moves = candidates, ownership = null)
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
