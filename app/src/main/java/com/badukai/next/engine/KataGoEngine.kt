package com.badukai.next.engine

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Public engine facade — the type the rest of the app (GameViewModel, GameScreen,
 * etc.) references as `KataGoEngine`.
 *
 * Why a real class instead of `typealias KataGoEngine = EngineManager`?
 * Kotlin typealiases do not expose nested classifiers, so `KataGoEngine.Model`
 * (used widely by GameViewModel/GameScreen) would not resolve through a
 * typealias. Nested typealiases are not supported either, and nested classes
 * are not inherited for qualification. The only way to keep
 * `KataGoEngine.Model` / `KataGoEngine.Model.HUMAN` / `KataGoEngine.Model.entries`
 * working without touching call sites is to keep `Model` nested inside a real
 * `KataGoEngine` class.
 *
 * This facade therefore owns the public `Model` enum and delegates every engine
 * operation to an internal [EngineManager] (which in turn drives [GtpClient] +
 * [EngineBootstrap]). The public API is identical to the legacy KataGoEngine, so
 * GameViewModel and GameScreen compile unchanged.
 */
class KataGoEngine(context: Context) {

    enum class Model(val displayName: String, val fileName: String, val description: String) {
        HUMAN("Human", "10b.bin", "Approachable AI opponent, fast responses"),
        SUPERHUMAN("Superhuman", "18b.bin", "Very strong AI, balanced performance"),
        GODLIKE("Godlike", "28b.bin", "Ultimate strength, may be slower on some devices")
    }

    private val manager = EngineManager(context)

    val isReady: StateFlow<Boolean> get() = manager.isReady

    suspend fun start(model: Model = Model.HUMAN): Boolean =
        manager.start(model.fileName)

    fun stop() = manager.stop()

    suspend fun generateMove(color: String): String? =
        manager.generateMove(color)

    suspend fun playMove(color: String, move: String): Boolean =
        manager.playMove(color, move)

    suspend fun setBoardSize(size: Int): Boolean =
        manager.setBoardSize(size)

    suspend fun clearBoard(): Boolean =
        manager.clearBoard()

    suspend fun setKomi(komi: Float): Boolean =
        manager.setKomi(komi)

    suspend fun undo(): Boolean =
        manager.undo()

    suspend fun getFinalScore(): String? =
        manager.getFinalScore()

    fun isRunning(): Boolean = manager.isRunning()
}
