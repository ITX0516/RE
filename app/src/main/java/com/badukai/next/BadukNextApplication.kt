package com.badukai.next

import android.app.Application
import com.badukai.next.logging.AppLogger
import com.badukai.next.logging.CrashHandler

class BadukNextApplication : Application() {
    companion object {
        private const val TAG = "BadukNextApplication"
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(this)
        CrashHandler.install()
        AppLogger.i(TAG, "BadukNext application created")
        AppLogger.i(TAG, "Log directory: ${AppLogger.getLogDirectory()?.absolutePath}")
    }
}
