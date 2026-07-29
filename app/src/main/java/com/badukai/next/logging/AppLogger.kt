package com.badukai.next.logging

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "AppLogger"
    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_PREFIX = "app_"
    private const val MAX_LOG_FILES = 7

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private lateinit var appContext: Context
    private var logDir: File? = null
    private var currentLogFile: File? = null
    private var currentFileDate: String? = null

    enum class Level(val priority: Int, val tag: String) {
        VERBOSE(Log.VERBOSE, "V"),
        DEBUG(Log.DEBUG, "D"),
        INFO(Log.INFO, "I"),
        WARN(Log.WARN, "W"),
        ERROR(Log.ERROR, "E"),
        ASSERT(Log.ASSERT, "A")
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val dir = File(appContext.getExternalFilesDir(null), LOG_DIR_NAME)
            .takeIf { it != null }
            ?: File(appContext.filesDir, LOG_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        logDir = dir
        rotateIfNeeded()
        cleanupOldLogs()
        i(TAG, "AppLogger initialized, log dir: ${dir.absolutePath}")
    }

    fun getLogDirectory(): File? = logDir

    fun getLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(LOG_FILE_PREFIX) && it.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            ?: emptyList()
    }

    fun v(tag: String, msg: String, tr: Throwable? = null) = log(Level.VERBOSE, tag, msg, tr)
    fun d(tag: String, msg: String, tr: Throwable? = null) = log(Level.DEBUG, tag, msg, tr)
    fun i(tag: String, msg: String, tr: Throwable? = null) = log(Level.INFO, tag, msg, tr)
    fun w(tag: String, msg: String, tr: Throwable? = null) = log(Level.WARN, tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log(Level.ERROR, tag, msg, tr)
    fun wtf(tag: String, msg: String, tr: Throwable? = null) = log(Level.ASSERT, tag, msg, tr)

    fun logCrash(tr: Throwable) {
        val msg = "APPLICATION CRASHED\n${getStackTraceString(tr)}"
        writeToFile(Level.ASSERT, "CRASH", msg)
    }

    private fun log(level: Level, tag: String, msg: String, tr: Throwable?) {
        val fullMsg = if (tr != null) "$msg\n${getStackTraceString(tr)}" else msg
        Log.println(level.priority, tag, fullMsg)
        writeToFile(level, tag, fullMsg)
    }

    private fun writeToFile(level: Level, tag: String, msg: String) {
        if (!::appContext.isInitialized) return
        scope.launch {
            mutex.withLock {
                try {
                    rotateIfNeeded()
                    val file = currentLogFile ?: return@withLock
                    val timestamp = dateFormat.format(Date())
                    val threadName = Thread.currentThread().name
                    val line = "$timestamp [${level.tag}] [$threadName] [$tag] $msg\n"
                    FileWriter(file, true).use { it.append(line) }
                } catch (t: Throwable) {
                    try {
                        Log.e(TAG, "Failed to write log", t)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    private fun rotateIfNeeded() {
        val today = fileDateFormat.format(Date())
        if (currentFileDate == today && currentLogFile?.exists() == true) return
        val dir = logDir ?: return
        val file = File(dir, "$LOG_FILE_PREFIX$today.log")
        if (!file.exists()) {
            file.createNewFile()
        }
        currentLogFile = file
        currentFileDate = today
    }

    private fun cleanupOldLogs() {
        val dir = logDir ?: return
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(LOG_FILE_PREFIX) && it.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            ?: return
        if (files.size > MAX_LOG_FILES) {
            files.drop(MAX_LOG_FILES).forEach {
                try {
                    it.delete()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun getStackTraceString(tr: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        tr.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
}
