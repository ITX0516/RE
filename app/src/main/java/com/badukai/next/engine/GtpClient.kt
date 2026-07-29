package com.badukai.next.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Log level for the pure-Kotlin [GtpClient]. */
enum class GtpLogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Minimal logger interface so [GtpClient] stays free of any Android dependency.
 * The default instance is a no-op; [EngineManager] supplies a bridge to AppLogger.
 */
fun interface GtpLogger {
    fun log(level: GtpLogLevel, tag: String, msg: String, throwable: Throwable?)
}

/** Lifecycle state of the GTP client. */
enum class EngineState { IDLE, THINKING, STOPPED }

/** Parsed result of a single GTP command. */
data class GtpResponse(
    val success: Boolean,
    val raw: String,
    val value: String?
)

/**
 * Pure GTP (Go Text Protocol) client. Launches a native GTP-speaking process and
 * exchanges commands over stdin/stdout. Has no Android [android.content.Context]
 * dependency.
 *
 * Commands are serialized with a [Mutex] (replacing the legacy blocking queue); an
 * independent coroutine drains stderr so the native process never blocks on a full
 * error buffer. An [EngineState] state machine exposes the current lifecycle.
 */
class GtpClient(
    private val executablePath: String,
    private val workingDir: File,
    private val args: List<String>,
    private val env: Map<String, String>,
    private val logger: GtpLogger = GtpLogger { _, _, _, _ -> },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "GtpClient"
        private const val DEFAULT_TIMEOUT_MS = 5000L
        private const val GENMOVE_TIMEOUT_MS = 60000L
        private const val FINAL_SCORE_TIMEOUT_MS = 10000L
    }

    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())
    private val mutex = Mutex()

    private val _state = MutableStateFlow(EngineState.STOPPED)
    val state: StateFlow<EngineState> = _state

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var errorReader: BufferedReader? = null
    private var readerJob: Job? = null
    private var stderrJob: Job? = null

    @Volatile
    private var alive = false

    private val pendingResponse = AtomicReference<CompletableDeferred<String>?>(null)

    /** Launch the native process. Returns true if it started and stayed alive. */
    suspend fun start(): Boolean = withContext(ioDispatcher) {
        if (alive) {
            log(GtpLogLevel.WARN, "Engine already running")
            return@withContext true
        }

        try {
            val command = buildList {
                add(executablePath)
                addAll(args)
            }
            log(GtpLogLevel.INFO, "Command: ${command.joinToString(" ")}")

            val builder = ProcessBuilder(command)
            builder.directory(workingDir)
            val envMap = builder.environment()
            env.forEach { (k, v) -> envMap[k] = v }

            log(GtpLogLevel.INFO, "Launching process...")
            process = builder.start()

            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            errorReader = BufferedReader(InputStreamReader(process!!.errorStream))

            delay(2000)

            val isAlive = process?.isAlive ?: false
            val exitCode = try {
                process?.exitValue()
            } catch (e: IllegalThreadStateException) {
                null
            }
            log(GtpLogLevel.INFO, "Process alive: $isAlive, exitCode: $exitCode")

            if (!isAlive) {
                val error = errorReader?.readText() ?: ""
                val output = reader?.readText() ?: ""
                log(GtpLogLevel.ERROR, "Process died immediately!")
                log(GtpLogLevel.ERROR, "Stderr: $error")
                log(GtpLogLevel.ERROR, "Stdout: $output")
                cleanup()
                return@withContext false
            }

            alive = true
            _state.value = EngineState.IDLE
            startReaderJob()
            startStderrJob()

            log(GtpLogLevel.INFO, "=== GTP CLIENT STARTED ===")
            return@withContext true
        } catch (e: Exception) {
            log(GtpLogLevel.ERROR, "Failed to start GTP client", e)
            cleanup()
            return@withContext false
        }
    }

    /**
     * Continuously reads stdout, accumulating lines until the GTP response
     * terminator (an empty line), then completes the single pending request.
     */
    private fun startReaderJob() {
        readerJob = scope.launch {
            val buffer = StringBuilder()
            log(GtpLogLevel.INFO, "Reader job started, waiting for output...")
            while (isActive && alive) {
                val line = try {
                    reader?.readLine()
                } catch (e: IOException) {
                    log(GtpLogLevel.ERROR, "IOException reading: ${e.message}")
                    null
                }
                if (line == null) {
                    val p = process
                    val isAlive = p?.isAlive
                    val exit = try {
                        p?.exitValue()
                    } catch (e: Exception) {
                        -999
                    }
                    log(GtpLogLevel.INFO, "KataGo stdout stream closed (alive=$isAlive, exit=$exit)")
                    break
                }
                log(GtpLogLevel.DEBUG, "KataGo stdout: $line")
                buffer.append(line).append("\n")
                if (line.isEmpty() && buffer.isNotEmpty()) {
                    val response = buffer.toString()
                    buffer.clear()
                    pendingResponse.getAndSet(null)?.complete(response)
                }
            }
            // Stream ended — unblock any in-flight caller with an empty (failing) response.
            pendingResponse.getAndSet(null)?.complete("")
        }
    }

    /** Drains stderr independently so the native process cannot deadlock on a full buffer. */
    private fun startStderrJob() {
        stderrJob = scope.launch {
            try {
                while (isActive && alive) {
                    val line = try {
                        errorReader?.readLine()
                    } catch (e: IOException) {
                        null
                    }
                    if (line == null) {
                        log(GtpLogLevel.INFO, "KataGo stderr stream closed")
                        break
                    }
                    log(GtpLogLevel.WARN, "KataGo stderr: $line")
                }
            } catch (e: Exception) {
                log(GtpLogLevel.ERROR, "Stderr reader job error", e)
            }
        }
    }

    /**
     * Send a GTP [command] and await its response. Serialized by [mutex] so only
     * one command is ever in flight at a time.
     *
     * NOTE: [Mutex.withLock] takes a non-suspending action lambda, so it cannot
     * host the suspending [withTimeout] below. We therefore hold the lock
     * manually with `lock`/`unlock` and release it in a `finally` block.
     */
    suspend fun send(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): GtpResponse {
        mutex.lock()
        try {
            if (!alive) {
                log(GtpLogLevel.WARN, "Cannot send command, engine not running")
                return GtpResponse(false, "", null)
            }
            log(GtpLogLevel.DEBUG, "Sending: $command")

            val deferred = CompletableDeferred<String>()
            pendingResponse.set(deferred)
            try {
                writer?.write(command)
                writer?.newLine()
                writer?.flush()
            } catch (e: Exception) {
                log(GtpLogLevel.ERROR, "Error sending: $command", e)
                pendingResponse.set(null)
                return GtpResponse(false, "", null)
            }

            val raw = try {
                withTimeout(timeoutMs) { deferred.await() }
            } catch (e: Exception) {
                log(GtpLogLevel.ERROR, "Timeout/error waiting for response: $command", e)
                pendingResponse.set(null)
                ""
            }

            val success = raw.trimStart().startsWith("=")
            val value = parseGtpResponse(raw)
            return GtpResponse(success, raw, value)
        } finally {
            mutex.unlock()
        }
    }

    /** Request a move from the engine for [color] (e.g. "B" / "W"). */
    suspend fun genmove(color: String): String? {
        _state.value = EngineState.THINKING
        try {
            val resp = send("genmove $color", GENMOVE_TIMEOUT_MS)
            log(GtpLogLevel.INFO, "Generated move for $color: ${resp.value}")
            return if (resp.success) resp.value else null
        } finally {
            _state.value = if (alive) EngineState.IDLE else EngineState.STOPPED
        }
    }

    /** Tell the engine a move was played. */
    suspend fun play(color: String, move: String): Boolean =
        send("play $color $move").success

    /** Set the board size. */
    suspend fun boardsize(n: Int): Boolean =
        send("boardsize $n").success

    /** Clear the board state. */
    suspend fun clear_board(): Boolean =
        send("clear_board").success

    /** Set the komi. */
    suspend fun komi(k: Float): Boolean =
        send("komi $k").success

    /** Undo the last move. */
    suspend fun undo(): Boolean =
        send("undo").success

    /** Request the final score. */
    suspend fun final_score(): String? {
        val resp = send("final_score", FINAL_SCORE_TIMEOUT_MS)
        return if (resp.success) resp.value else null
    }

    private fun parseGtpResponse(response: String): String? {
        val trimmed = response.trim()
        return when {
            trimmed.startsWith("= ") -> trimmed.substring(2).trim().split("\n").firstOrNull()?.trim()
            trimmed.startsWith("=") -> trimmed.substring(1).trim().split("\n").firstOrNull()?.trim()
            else -> null
        }
    }

    fun isRunning(): Boolean = alive

    /** Tear down the process: best-effort `quit`, close streams, then kill. */
    fun close() {
        log(GtpLogLevel.INFO, "Closing GTP client...")
        alive = false
        _state.value = EngineState.STOPPED

        try {
            writer?.write("quit")
            writer?.newLine()
            writer?.flush()
        } catch (e: Exception) {
        }

        readerJob?.cancel()
        readerJob = null
        stderrJob?.cancel()
        stderrJob = null

        pendingResponse.getAndSet(null)?.complete("")

        cleanup()

        log(GtpLogLevel.INFO, "GTP client closed")
    }

    private fun cleanup() {
        try { writer?.close() } catch (e: Exception) {}
        try { reader?.close() } catch (e: Exception) {}
        try { errorReader?.close() } catch (e: Exception) {}
        val p = process
        if (p != null) {
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
        errorReader = null
    }

    private fun log(level: GtpLogLevel, msg: String, throwable: Throwable? = null) {
        logger.log(level, TAG, msg, throwable)
    }
}
