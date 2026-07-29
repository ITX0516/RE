package com.badukai.next

import android.app.Application
import com.badukai.next.data.game.GameDatabase
import com.badukai.next.data.game.GameRepository
import com.badukai.next.data.settings.SettingsRepository
import com.badukai.next.logging.AppLogger
import com.badukai.next.logging.CrashHandler

class BadukNextApplication : Application() {
    companion object {
        private const val TAG = "BadukNextApplication"
    }

    lateinit var gameDatabase: GameDatabase
    lateinit var gameRepository: GameRepository
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(this)
        CrashHandler.install()
        gameDatabase = GameDatabase.getInstance(this)
        gameRepository = GameRepository(gameDatabase.gameDao())
        settingsRepository = SettingsRepository(this)
        AppLogger.i(TAG, "BadukNext application created")
        AppLogger.i(TAG, "Log directory: ${AppLogger.getLogDirectory()?.absolutePath}")
    }
}
