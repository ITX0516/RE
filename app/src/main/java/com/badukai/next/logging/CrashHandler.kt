package com.badukai.next.logging

import android.os.Process
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"

        fun install() {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is CrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(current))
        }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            AppLogger.e(
                TAG,
                "Uncaught exception in thread: ${t.name} (id=${t.id}, priority=${t.priority})",
                e
            )
            AppLogger.logCrash(e)
        } catch (_: Throwable) {
        }

        try {
            defaultHandler?.uncaughtException(t, e)
        } catch (_: Throwable) {
        }

        try {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        } catch (_: Throwable) {
        }
    }
}
