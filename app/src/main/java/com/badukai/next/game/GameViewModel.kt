package com.badukai.next.game

import android.content.Context
import com.badukai.next.analysis.GameRecorder
import java.io.File
import com.badukai.next.analysis.RecordedMove
import com.badukai.next.audio.StoneSoundPlayer
import com.badukai.next.game.SettingsStore
import com.badukai.next.engine.KataGoEngine
import com.badukai.next.engine.ModelSource
import com.badukai.next.engine.ModelManager
import com.badukai.next.logging.AppLogger
import com.badukai.next.ui.BadukNextColors
import com.badukai.next.ui.GameTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GameMode(val displayName: String) {
    PLAY("Play"),
    ANALYZE("Analyze")
}

enum class StoneAnimation(val displayName: String) {
    FADE_IN("Fade In"), DROP("Drop"), DOWN("Down"), NONE("None")
}

enum class PlacementMode(val displayName: String) {
    TAP("Single Tap"),
    DOUBLE_TAP("Double Tap"),
    CONFIRM("Confirm Button")
}

data class GameState(
    val board: GoBoard = GoBoard(19),
    val currentPlayer: StoneColor = StoneColor.BLACK,
    val playerColor: StoneColor = StoneColor.BLACK,
    val isPlayerTurn: Boolean = true,
    val isThinking: Boolean = false,
    val isEngineReady: Boolean = false,
    val isEngineStarting: Boolean = false,
    val selectedModel: KataGoEngine.Model = KataGoEngine.Model.SIX_B,
    val aiModelSource: ModelSource = ModelSource.BUNDLED_ASSET,
    val customModelDisplayName: String = "",
    val gameMessage: String = "",
    val lastMovePoint: Point? = null,
    val capturedByBlack: Int = 0,
    val capturedByWhite: Int = 0,
    val boardSize: Int = 19,
    val showNewGameDialog: Boolean = false,
    val showModelSelector: Boolean = false,
    val showSettings: Boolean = false,
    val showCoordinates: Boolean = true,
    val soundEnabled: Boolean = true,
    val currentTheme: GameTheme = GameTheme.WARM_LIGHT,
    val gameMode: GameMode = GameMode.PLAY,
    val placementMode: PlacementMode = PlacementMode.TAP,
    val analysisMoveIndex: Int = 0,
    val analysisMoves: List<RecordedMove> = emptyList(),
    val winrateHistory: List<Float> = emptyList(),
    val scoreLeadHistory: List<Float> = emptyList(),
    val pendingTap: Point? = null,
    val doubleTapActive: Boolean = false,
    val confirmMoveQueued: Point? = null,
    val placeSoundIndex: Int = 0,
    val stoneAnimation: StoneAnimation = StoneAnimation.FADE_IN,
    val aiMoveTimeSeconds: Int = 20,
    val aiCanResign: Boolean = true,
    val handicap: Int = 0,
    val komi: Float = GameConstants.DEFAULT_KOMI,
    val showTerritoryOverlay: Boolean = false,
    val territoryResult: String = "",
    val topCandidatePoints: List<Pair<Int,Int>> = emptyList(),
    val topCandidateWinrates: List<Float> = emptyList(),
    val winrate: Float = 0f,
    val scoreLead: Float = 0f,
    val ownership: List<Float>? = null,
    val candidateInfo: List<String> = emptyList(),
    val showEyeOverlay: Boolean = false,
    val playedMovePoints: List<Pair<Int,Int>> = emptyList(),
    val moveQualities: List<Int> = emptyList(),
    val gameResult: GameResult? = null,
    val analysisError: String = "",
    val showSavedGamesDialog: Boolean = false,
    val savedGames: List<Pair<String, String>> = emptyList()
)

enum class GameResult(val label: String) {
    WIN("You win!"), LOSE("You lose"), DRAW("Draw")
}

class GameViewModel : ViewModel() {

    companion object {
        private const val TAG = "GameViewModel"
    }

    private var engine: KataGoEngine? = null
    private var soundPlayer: StoneSoundPlayer? = null
    private var appContext: Context? = null
    private var gameGeneration = 0
    lateinit var settingsStore: SettingsStore
    val recorder = GameRecorder()

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (engine == null) {
            engine = KataGoEngine(context)
        }
        if (soundPlayer == null) {
            soundPlayer = StoneSoundPlayer(context)
        }

        // Load persisted settings
        if (!::settingsStore.isInitialized) {
            settingsStore = SettingsStore(context)
            val s = settingsStore
            _state.value = _state.value.copy(
                showCoordinates = s.showCoordinates,
                soundEnabled = s.soundEnabled,
                currentTheme = s.currentTheme,
                placementMode = s.placementMode,
                placeSoundIndex = s.placeSoundIndex,
                stoneAnimation = s.stoneAnimation,
                aiMoveTimeSeconds = s.aiMoveTimeSeconds,
                aiCanResign = s.aiCanResign,
                aiModelSource = s.aiModelSource,
                customModelDisplayName = s.customModelDisplayName.ifBlank { "" }
            )
            soundPlayer?.setPlaceSound(s.placeSoundIndex)
            BadukNextColors.setTheme(s.currentTheme)
        }

        recorder.reset()
    }

    fun startEngine(model: KataGoEngine.Model = _state.value.selectedModel) {
        val engine = this.engine ?: return
        if (_state.value.isEngineStarting) return
        val s = _state.value
        _state.value = s.copy(
            isEngineStarting = true,
            gameMessage = "Starting AI..."
        )

        viewModelScope.launch {
            try {
                val source: ModelSource = settingsStore.aiModelSource
                val customPath: String? = settingsStore.customModelPath.takeIf { it.isNotBlank() }
                val success = engine.start(source = source, customStoredPath = customPath, legacyModel = model)
                if (success) {
                    engine.setBoardSize(_state.value.boardSize)
                    engine.clearBoard()
                    engine.setKomi(GameConstants.DEFAULT_KOMI)
                    engine.sendCommand("time_settings 0 ${_state.value.aiMoveTimeSeconds} 1")
                    val msg: String = when (source) {
                        ModelSource.BUNDLED_ASSET -> "Ready (内置 6b，离线可用)"
                        ModelSource.DOWNLOADED -> "Ready (在线下载 6b)"
                        ModelSource.CUSTOM -> "Ready (自定义权重)"
                        else -> "Ready (内置 6b，离线可用)"
                    }
                    _state.value = _state.value.copy(
                        isEngineReady = true,
                        isEngineStarting = false,
                        selectedModel = model,
                        gameMessage = msg
                    )
                    if (_state.value.playerColor == StoneColor.WHITE) {
                        requestAiMove()
                    }
                } else {
                    _state.value = _state.value.copy(
                        isEngineReady = false,
                        isEngineStarting = false,
                        gameMessage = "Failed to start AI (source=$source). Check logcat ModelManager/KataGoEngine for details."
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error starting engine", e)
                _state.value = _state.value.copy(
                    isEngineReady = false,
                    isEngineStarting = false,
                    gameMessage = "Error: ${e.message}"
                )
            }
        }
    }

    fun stopEngine() {
        engine?.stop()
        _state.value = _state.value.copy(
            isEngineReady = false,
            isEngineStarting = false
        )
    }

    fun onBoardTap(x: Int, y: Int) {
        val s = _state.value
        if (s.gameMode == GameMode.ANALYZE) {
            freePlaceStone(Point(x, y))
            return
        }
        if (!s.isPlayerTurn || s.isThinking) return
        if (s.board.isGameOver) return

        when (s.placementMode) {
            PlacementMode.TAP -> tryPlaceStone(Point(x, y))
            PlacementMode.DOUBLE_TAP -> handleDoubleTap(Point(x, y))
            PlacementMode.CONFIRM -> handleConfirmTap(Point(x, y))
        }
    }

    // Free placement in analysis mode — no engine interaction
    private var freeStoneColor = StoneColor.BLACK
    private fun freePlaceStone(point: Point) {
        val s = _state.value
        if (!s.board.isLegalMove(point, freeStoneColor)) return
        val color = freeStoneColor
        s.board.playMove(Move.Stone(point, color))
        freeStoneColor = freeStoneColor.opposite()
        if (s.soundEnabled) soundPlayer?.playPlace()
        _state.value = s.copy(
            currentPlayer = freeStoneColor,  // correct perspective for analysis
            lastMovePoint = point,
            capturedByBlack = s.board.getCapturedWhite(),
            capturedByWhite = s.board.getCapturedBlack()
        )
        // Sync to engine and re-analyze the new position
        viewModelScope.launch {
            engine?.playMove(color.toGtp(), point.toGtp(s.boardSize))
            requestAnalysis()
        }
    }

    private fun handleDoubleTap(point: Point) {
        val s = _state.value
        if (s.doubleTapActive && s.pendingTap == point) {
            // Second tap on same spot
            tryPlaceStone(point, resetDoubleTap = true)
        } else {
            // First tap or different spot
            _state.value = s.copy(
                pendingTap = point,
                doubleTapActive = true
            )
        }
    }

    private fun handleConfirmTap(point: Point) {
        val s = _state.value
        if (s.board.isLegalMove(point, s.currentPlayer)) {
            _state.value = s.copy(
                confirmMoveQueued = point,
                pendingTap = point
            )
        }
    }

    fun confirmMove() {
        val s = _state.value
        val point = s.confirmMoveQueued ?: return
        tryPlaceStone(point)
        _state.value = _state.value.copy(confirmMoveQueued = null, pendingTap = null)
    }

    fun cancelMove() {
        _state.value = _state.value.copy(confirmMoveQueued = null, pendingTap = null)
    }

    private fun tryPlaceStone(point: Point, resetDoubleTap: Boolean = false) {
        val s = _state.value
        if (!s.board.isLegalMove(point, s.currentPlayer)) return

        if (resetDoubleTap) {
            _state.value = s.copy(doubleTapActive = false, pendingTap = null)
        }

        // Sound
        if (s.soundEnabled) soundPlayer?.playPlace()

        playMove(point, s.currentPlayer)
    }

    private fun playMove(point: Point, color: StoneColor) {
        val currentState = _state.value
        val board = currentState.board

        board.playMove(Move.Stone(point, color))
        recorder.recordMove(Move.Stone(point, color))

        val newPlayer = color.opposite()
        val isPlayerTurn = newPlayer == currentState.playerColor

        _state.value = currentState.copy(
            currentPlayer = newPlayer,
            isPlayerTurn = isPlayerTurn,
            lastMovePoint = point,
            capturedByBlack = board.getCapturedWhite(),
            capturedByWhite = board.getCapturedBlack(),
            analysisMoves = recorder.getAnalysisMoves(),
            gameMessage = if (isPlayerTurn) "Your turn" else "AI thinking..."
        )

        viewModelScope.launch {
            // Sync engine with the player's move
            engine?.playMove(color.toGtp(), point.toGtp(currentState.boardSize))
            if (!isPlayerTurn && !board.isGameOver) {
                // AI responds; analysis runs once after AI move (in handleAiMove)
                requestAiMove()
            }
        }
    }

    private fun requestAnalysis(recordToHistory: Boolean = true) {
        val color = _state.value.currentPlayer.toGtp()
        val gen = gameGeneration
        viewModelScope.launch {
            val result = engine?.analyzePosition(color, GameConstants.ANALYSIS_VISITS) // lower visits for speed
            if (result == null) {
                _state.value = _state.value.copy(analysisError = engine?.lastAnalysisError ?: "analysis failed")
                return@launch
            }
            if (gen != gameGeneration) return@launch  // stale game — discard
            val s = _state.value
            val candidates = result.moves.take(GameConstants.CANDIDATE_DISPLAY_COUNT).map { cm ->
                val coord = if (cm.x >= 0 && cm.y >= 0) {
                    "${Point.GTP_LETTERS[cm.x]}${s.boardSize - cm.y}"
                } else "pass"
                val wr = cm.winRate?.let { "%.1f%%".format(it * GameConstants.WINRATE_UNIT / 100f) } ?: "-"
                val sc = cm.scoreLead?.let { if (it >= 0) "+%.1f".format(it) else "%.1f".format(it) } ?: "-"
                "$coord  $wr  $sc"
            }
            val topPts = result.moves.take(GameConstants.TOP_CANDIDATE_COUNT).mapNotNull { cm ->
                if (cm.x >= 0 && cm.y >= 0) Pair(cm.x, cm.y) else null
            }
            val topWrs = result.moves.take(GameConstants.TOP_CANDIDATE_COUNT).map { cm -> cm.winRate ?: 0.5f }
            // Convert winrate to Black's perspective for consistent history
            val blackWinrate = if (s.currentPlayer == StoneColor.WHITE)
                1f - result.winrate else result.winrate
            val newHistory = if (recordToHistory) s.winrateHistory + blackWinrate else s.winrateHistory
            val newScoreHistory = if (recordToHistory) s.scoreLeadHistory + result.scoreLead else s.scoreLeadHistory

            // If territory overlay is active, keep the heuristic annotation fresh
            val freshOwnership = if (s.showTerritoryOverlay)
                computeHeuristicOwnership(s.board) else result.ownership
            _state.value = s.copy(
                // state.winrate = White's winrate (winrate bar expects White perspective)
                winrate = if (s.currentPlayer == StoneColor.WHITE)
                    result.winrate else 1f - result.winrate,
                scoreLead = result.scoreLead,
                ownership = freshOwnership,
                candidateInfo = candidates,
                topCandidatePoints = topPts,
                topCandidateWinrates = topWrs,
                winrateHistory = newHistory,
                scoreLeadHistory = newScoreHistory,
                playedMovePoints = s.analysisMoves.mapNotNull { (it.move as? Move.Stone)?.point?.let { p -> Pair(p.x, p.y) } },
                moveQualities = computeMoveQualities(newHistory, s.analysisMoves)
            )
        }
    }

    // 0 = good, 1 = pink (5-10% drop), 2 = red (>=10% drop)
    private fun computeMoveQualities(blackHistory: List<Float>, moves: List<RecordedMove>): List<Int> {
        val qualities = MutableList(moves.size) { 0 }
        if (blackHistory.size < 2) return qualities
        for (i in 1 until blackHistory.size) {
            val moveIdx = i - 1
            if (moveIdx >= moves.size) break
            val moveColor = (moves[moveIdx].move as? Move.Stone)?.color
            val delta = blackHistory[i] - blackHistory[i - 1]
            // Mover's winrate change: if White moved, black improving hurts White
            val moverDelta = if (moveColor == StoneColor.WHITE) -delta else delta
            qualities[moveIdx] = when {
                moverDelta <= -GameConstants.MISTAKE_THRESHOLD_MAX -> 2
                moverDelta <= -GameConstants.MISTAKE_THRESHOLD_MIN -> 1
                else -> 0
            }
        }
        return qualities
    }

    fun forceEndGame() {
        viewModelScope.launch {
            val s = _state.value
            // Play both pass on engine
            engine?.playMove("black", "pass")
            engine?.playMove("white", "pass")
            val score = engine?.getFinalScore()
            val result = resolveGameResult(score)
            _state.value = _state.value.copy(
                isPlayerTurn = false,
                gameMessage = score?.let { "Game over. $it" } ?: "Game over",
                territoryResult = score ?: "No result",
                gameResult = result
            )
        }
    }

    private fun requestAiMove() {
        val s = _state.value
        val e = engine ?: return
        if (!s.isEngineReady) return
        val gen = gameGeneration

        _state.value = s.copy(isThinking = true, gameMessage = "AI thinking...")

        viewModelScope.launch {
            try {
                val aiColor = s.currentPlayer
                val move = e.generateMove(aiColor.toGtp())
                if (gen != gameGeneration) return@launch  // stale game
                withContext(Dispatchers.Main) { handleAiMove(move, aiColor) }
            } catch (ex: kotlinx.coroutines.CancellationException) {
                // Coroutine cancelled (e.g. new game started) — not a real error
                AppLogger.d(TAG, "AI move coroutine cancelled")
            } catch (ex: Exception) {
                AppLogger.e(TAG, "AI move error", ex)
                _state.value = _state.value.copy(isThinking = false, gameMessage = "AI error: ${ex.message}")
            }
        }
    }

    private fun handleAiMove(move: String?, color: StoneColor) {
        val s = _state.value
        _state.value = s.copy(isThinking = false)

        when {
            move == null -> _state.value = _state.value.copy(gameMessage = "AI returned no move")
            move.equals("pass", ignoreCase = true) -> handleAiPass(color)
            move.equals("resign", ignoreCase = true) -> {
                if (!s.aiCanResign) {
                    handleAiPass(color)
                } else {
                    val m = Move.Resign(color)
                    s.board.playMove(m)
                    recorder.recordMove(m)
                    _state.value = _state.value.copy(
                        gameMessage = "AI resigned. You win!",
                        analysisMoves = recorder.getAnalysisMoves(),
                        gameResult = GameResult.WIN
                    )
                }
            }
            else -> {
                val pt = Point.fromGtp(move, s.boardSize)
                if (pt != null) {
                    val m = Move.Stone(pt, color)
                    s.board.playMove(m)
                    recorder.recordMove(m)
                    if (s.soundEnabled) soundPlayer?.playPlace()
                    _state.value = _state.value.copy(
                        currentPlayer = color.opposite(), isPlayerTurn = true,
                        lastMovePoint = pt,
                        capturedByBlack = s.board.getCapturedWhite(),
                        capturedByWhite = s.board.getCapturedBlack(),
                        gameMessage = "Your turn",
                        analysisMoves = recorder.getAnalysisMoves()
                    )
                    requestAnalysis()  // after state update → correct perspective
                } else {
                    AppLogger.e(TAG, "AI returned unparseable move: [$move]")
                    _state.value = _state.value.copy(gameMessage = "AI error (move: $move)")
                }
            }
        }
    }

    private fun handleAiPass(color: StoneColor) {
        val s = _state.value
        val m = Move.Pass(color)
        s.board.playMove(m)
        recorder.recordMove(m)
        _state.value = _state.value.copy(
            currentPlayer = color.opposite(), isPlayerTurn = true,
            lastMovePoint = null, gameMessage = "AI passed. Your turn",
            analysisMoves = recorder.getAnalysisMoves()
        )
        if (s.board.isGameOver) handleGameEnd()
    }

    fun pass() {
        val s = _state.value
        if (!s.isPlayerTurn || s.isThinking) return
        val passer = s.currentPlayer
        val m = Move.Pass(passer)
        s.board.playMove(m)
        recorder.recordMove(m)
        val isGameOver = s.board.isGameOver
        _state.value = s.copy(
            currentPlayer = passer.opposite(), isPlayerTurn = false,
            lastMovePoint = null, gameMessage = if (isGameOver) "Passed" else "You passed. AI thinking...",
            analysisMoves = recorder.getAnalysisMoves()
        )
        viewModelScope.launch {
            // Sync engine pass BEFORE final_score / next genmove
            engine?.playMove(passer.toGtp(), "pass")
            if (isGameOver) handleGameEnd() else requestAiMove()
        }
    }

    fun resign() {
        val s = _state.value
        val m = Move.Resign(s.playerColor)
        s.board.playMove(m)
        recorder.recordMove(m)
        val winner = if (s.playerColor == StoneColor.BLACK) "White" else "Black"
        _state.value = s.copy(
            gameMessage = "You resigned. $winner wins!",
            gameResult = GameResult.LOSE
        )
    }

    private fun handleGameEnd() {
        val gen = gameGeneration
        viewModelScope.launch {
            val score = engine?.getFinalScore()
            if (gen != gameGeneration) return@launch  // stale game
            val result = resolveGameResult(score)
            _state.value = _state.value.copy(
                gameMessage = score?.let { "Game over. $it" } ?: "Game over",
                gameResult = result
            )
        }
    }

    private fun resolveGameResult(score: String?): GameResult? {
        val s = _state.value
        if (score == null) return null
        val trimmed = score.trim()
        if (trimmed.startsWith("0") || trimmed.equals("draw", true) || trimmed.equals("tie", true)) {
            return GameResult.DRAW
        }
        val blackWins = trimmed.startsWith("B")
        return if ((s.playerColor == StoneColor.BLACK) == blackWins) GameResult.WIN else GameResult.LOSE
    }

    fun undo() {
        val s = _state.value
        if (s.isThinking || s.board.getMoveCount() < 2) return
        s.board.undo(); s.board.undo()
        recorder.removeLast(); recorder.removeLast()
        // Trim analysis history (one analysis per round = 2 moves)
        val newWrHistory = s.winrateHistory.dropLast(1)
        val newSlHistory = s.scoreLeadHistory.dropLast(1)
        viewModelScope.launch {
            engine?.undo(); engine?.undo()
            // Re-analyze the post-undo position so winrate refreshes
            requestAnalysis()
        }
        val lastMove = s.board.getLastMove()
        _state.value = s.copy(
            currentPlayer = s.playerColor, isPlayerTurn = true,
            lastMovePoint = (lastMove as? Move.Stone)?.point,
            capturedByBlack = s.board.getCapturedWhite(),
            capturedByWhite = s.board.getCapturedBlack(),
            gameMessage = "Undone. Your turn",
            analysisMoves = recorder.getAnalysisMoves(),
            winrateHistory = newWrHistory,
            scoreLeadHistory = newSlHistory
        )
    }

    fun showNewGameDialog() { _state.value = _state.value.copy(showNewGameDialog = true) }
    fun hideNewGameDialog() { _state.value = _state.value.copy(showNewGameDialog = false) }
    fun showModelSelector() { _state.value = _state.value.copy(showModelSelector = true) }
    fun hideModelSelector() { _state.value = _state.value.copy(showModelSelector = false) }
    fun showSettingsDialog() { _state.value = _state.value.copy(showSettings = true) }
    fun hideSettingsDialog() { _state.value = _state.value.copy(showSettings = false) }

    // --- AI Weight source (2026-08-02) -------------------------------------
    fun setAiModelSource(source: ModelSource) {
        if (!::settingsStore.isInitialized) return
        // Safety: user switched to CUSTOM but hasn't imported a file → auto-switch back if custom path is empty
        val src = if (source == ModelSource.CUSTOM && settingsStore.customModelPath.isBlank()) {
            AppLogger.w(TAG, "Tried to select CUSTOM source without imported model — fallback to BUNDLED_ASSET. Ask user to tap \"选择自定义文件\" first.")
            ModelSource.BUNDLED_ASSET
        } else source
        settingsStore.aiModelSource = src
        _state.value = _state.value.copy(
            aiModelSource = src,
            customModelDisplayName = settingsStore.customModelDisplayName
        )
        // Restart engine if it was running under a different weight source.
        if (_state.value.isEngineReady || _state.value.isEngineStarting) {
            AppLogger.i(TAG, "Weight source changed → restart engine.")
            stopEngine()
            startEngine()
        }
    }

    /** Called after SAF ACTION_OPEN_DOCUMENT succeeds with a content:// Uri + display name. */
    fun onCustomModelPicked(uri: android.net.Uri, displayNameHint: String?) {
        val ctx = appContext ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(gameMessage = "正在导入自定义权重…")
            val res = ModelManager.importCustomModel(ctx, uri, displayNameHint)
            if (res.isSuccess) {
                val storedPath = res.getOrNull() ?: ""
                settingsStore.customModelPath = storedPath
                settingsStore.customModelDisplayName = (displayNameHint ?: storedPath.substringAfterLast('/')).take(64)
                settingsStore.aiModelSource = ModelSource.CUSTOM
                _state.value = _state.value.copy(
                    aiModelSource = ModelSource.CUSTOM,
                    customModelDisplayName = settingsStore.customModelDisplayName,
                    gameMessage = "自定义权重导入成功"
                )
                if (_state.value.isEngineReady || _state.value.isEngineStarting) {
                    stopEngine(); startEngine()
                }
            } else {
                val msg = res.exceptionOrNull()?.message ?: "unknown"
                _state.value = _state.value.copy(gameMessage = "导入失败：$msg")
                AppLogger.e(TAG, "Custom model import failed: $msg")
            }
        }
    }

    /** "恢复默认内置" → SettingsStore 清空 metadata + 可选清 custom 与 asset_copy 缓存。 */
    fun resetAiModelToBundled(clearCustomCacheToo: Boolean = true) {
        if (!::settingsStore.isInitialized) return
        settingsStore.resetModelSourceToBundled()
        val ctx = appContext
        if (clearCustomCacheToo && ctx != null) {
            ModelManager.clearCustomAndCache(ctx)
        }
        _state.value = _state.value.copy(
            aiModelSource = ModelSource.BUNDLED_ASSET,
            customModelDisplayName = "",
            gameMessage = "已恢复默认内置权重（6b）"
        )
        if (_state.value.isEngineReady || _state.value.isEngineStarting) {
            stopEngine(); startEngine()
        }
    }

    fun currentCustomDisplayName(): String =
        if (::settingsStore.isInitialized) settingsStore.customModelDisplayName else ""

    fun toggleTerritoryOverlay() {
        val s = _state.value
        val newVal = !s.showTerritoryOverlay
        if (newVal) {
            // Compute heuristic ownership synchronously so the board colors immediately
            val ownership = computeHeuristicOwnership(s.board)
            val (blackTerr, whiteTerr) = countTerritoryFromOwnership(ownership, s.board)
            val nonZero = ownership.count { it != 0f }
            val blackScore = blackTerr + s.board.getCapturedWhite().toFloat()
            val whiteScore = whiteTerr + s.board.getCapturedBlack().toFloat() + s.komi
            val diff = blackScore - whiteScore
            val lead = if (diff >= 0) "B+${"%.1f".format(diff)}" else "W+${"%.1f".format(-diff)}"
            val scoreLine = "$lead (B:$blackTerr W:$whiteTerr K:${s.komi})"
            AppLogger.i(TAG, "Territory: B=$blackTerr W=$whiteTerr nonzero=$nonZero diff=$diff stones=${s.board.getMoveCount()}")
            _state.value = s.copy(
                showTerritoryOverlay = true,
                ownership = ownership,
                territoryResult = scoreLine
            )
            // Refresh winrate in background, but do NOT add a data point to the chart
            requestAnalysis(recordToHistory = false)
        } else {
            _state.value = s.copy(showTerritoryOverlay = false)
        }
    }

    fun toggleEyeOverlay() {
        _state.value = _state.value.copy(showEyeOverlay = !_state.value.showEyeOverlay)
    }

    fun dismissCelebration() {
        _state.value = _state.value.copy(gameResult = null)
    }

    /**
     * Territory ownership via flood-fill: an empty region enclosed only by black
     * stones is black territory, only white is white territory, both = neutral.
     */
    private fun computeHeuristicOwnership(board: GoBoard): List<Float> {
        val size = board.size
        val ownership = MutableList(size * size) { 0f }
        for (y in 0 until size) for (x in 0 until size) {
            when (board.get(x, y)) {
                Intersection.BLACK -> ownership[y * size + x] = 1f
                Intersection.WHITE -> ownership[y * size + x] = -1f
                else -> {}
            }
        }

        val visited = mutableSetOf<Point>()
        for (y in 0 until size) for (x in 0 until size) {
            val start = Point(x, y)
            if (board.get(start) != Intersection.EMPTY || start in visited) continue

            val region = mutableListOf<Point>()
            val queue = ArrayDeque<Point>()
            queue.add(start); visited.add(start)
            var touchesBlack = false
            var touchesWhite = false
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                region.add(cur)
                listOf(
                    Point(cur.x - 1, cur.y), Point(cur.x + 1, cur.y),
                    Point(cur.x, cur.y - 1), Point(cur.x, cur.y + 1)
                ).forEach { n ->
                    if (n.x in 0 until size && n.y in 0 until size) {
                        when (board.get(n)) {
                            Intersection.EMPTY -> if (n !in visited) { visited.add(n); queue.add(n) }
                            Intersection.BLACK -> touchesBlack = true
                            Intersection.WHITE -> touchesWhite = true
                            else -> {}
                        }
                    }
                }
            }
            val owner = when {
                touchesBlack && !touchesWhite -> 1f
                touchesWhite && !touchesBlack -> -1f
                else -> 0f
            }
            if (owner != 0f) for (q in region) ownership[q.y * size + q.x] = owner
        }
        return ownership
    }

    private fun countTerritoryFromOwnership(ownership: List<Float>, board: GoBoard): Pair<Float, Float> {
        var black = 0f
        var white = 0f
        val size = board.size
        for (y in 0 until size) for (x in 0 until size) {
            // Only count empty intersections as territory, skip stones
            if (board.get(x, y) != Intersection.EMPTY) continue
            val i = y * size + x
            when {
                ownership.getOrElse(i) { 0f } > 0.5f -> black++
                ownership.getOrElse(i) { 0f } < -0.5f -> white++
            }
        }
        return Pair(black, white)
    }

    fun toggleCoordinates() {
        val v = !_state.value.showCoordinates
        _state.value = _state.value.copy(showCoordinates = v)
        if (::settingsStore.isInitialized) settingsStore.showCoordinates = v
    }
    fun toggleSound() {
        val v = !_state.value.soundEnabled
        _state.value = _state.value.copy(soundEnabled = v)
        if (::settingsStore.isInitialized) settingsStore.soundEnabled = v
    }
    fun setTheme(theme: GameTheme) {
        _state.value = _state.value.copy(currentTheme = theme)
        if (::settingsStore.isInitialized) settingsStore.currentTheme = theme
    }
    fun setPlaceSoundIndex(idx: Int) {
        _state.value = _state.value.copy(placeSoundIndex = idx)
        soundPlayer?.setPlaceSound(idx)
        if (::settingsStore.isInitialized) settingsStore.placeSoundIndex = idx
    }
    fun setStoneAnimation(anim: StoneAnimation) {
        _state.value = _state.value.copy(stoneAnimation = anim)
        if (::settingsStore.isInitialized) settingsStore.stoneAnimation = anim
    }
    fun setPlacementMode(mode: PlacementMode) {
        _state.value = _state.value.copy(placementMode = mode, pendingTap = null, doubleTapActive = false, confirmMoveQueued = null)
        if (::settingsStore.isInitialized) settingsStore.placementMode = mode
    }
    fun setAiMoveTime(seconds: Int) {
        val v = seconds.coerceIn(1, 120)
        _state.value = _state.value.copy(aiMoveTimeSeconds = v)
        if (::settingsStore.isInitialized) settingsStore.aiMoveTimeSeconds = v
        applyAiMoveTime(v)
    }
    fun setAiCanResign(can: Boolean) {
        _state.value = _state.value.copy(aiCanResign = can)
        if (::settingsStore.isInitialized) settingsStore.aiCanResign = can
        // Resignation handled in-app (handleAiMove treats AI resign as pass when disabled)
    }
    private fun applyAiMoveTime(seconds: Int) {
        viewModelScope.launch {
            // Canadian byo-yomi: N seconds per move (verified working on KataGo)
            engine?.sendCommand("time_settings 0 $seconds 1")
        }
    }

    fun setGameMode(mode: GameMode) {
        if (mode == _state.value.gameMode) return
        if (mode == GameMode.ANALYZE) {
            // Determine starting color from last move
            val lastMove = _state.value.board.getLastMove()
            freeStoneColor = when (val m = lastMove) {
                is Move.Stone -> m.color.opposite()
                else -> StoneColor.BLACK
            }
            val moves = recorder.getAnalysisMoves()
            _state.value = _state.value.copy(
                gameMode = GameMode.ANALYZE,
                analysisMoves = moves,
                analysisMoveIndex = moves.size
            )
            if (moves.isNotEmpty()) navigateToMove(moves.size)
        } else {
            _state.value = _state.value.copy(
                gameMode = GameMode.PLAY,
                analysisMoveIndex = 0,
                analysisMoves = emptyList()
            )
        }
    }

    fun navigateToMove(targetIndex: Int) {
        val s = _state.value
        val moves = s.analysisMoves
        if (moves.isEmpty() || targetIndex < 0 || targetIndex > moves.size) return

        // Rebuild board state up to targetIndex
        val replayBoard = GoBoard(s.boardSize)
        for (i in 0 until targetIndex) {
            replayBoard.playMove(moves[i].move)
        }

        val lastMove = if (targetIndex > 0) (moves[targetIndex - 1].move as? Move.Stone)?.point else null

        _state.value = s.copy(
            board = replayBoard,
            analysisMoveIndex = targetIndex,
            lastMovePoint = lastMove,
            capturedByBlack = replayBoard.getCapturedWhite(),
            capturedByWhite = replayBoard.getCapturedBlack()
        )
    }

    fun analysisNext() {
        val s = _state.value
        if (s.analysisMoveIndex < s.analysisMoves.size) {
            navigateToMove(s.analysisMoveIndex + 1)
        }
    }

    fun analysisPrev() {
        val s = _state.value
        if (s.analysisMoveIndex > 0) {
            navigateToMove(s.analysisMoveIndex - 1)
        }
    }

    fun startNewGame(playerColor: StoneColor, boardSize: Int, handicap: Int, komi: Float, aiTime: Int = 20, aiCanResign: Boolean = true) {
        gameGeneration++
        _state.value = _state.value.copy(aiMoveTimeSeconds = aiTime, aiCanResign = aiCanResign)
        val s = _state.value
        freeStoneColor = StoneColor.BLACK
        _state.value = _state.value.copy(
            winrateHistory = emptyList(), scoreLeadHistory = emptyList(),
            gameResult = null, showEyeOverlay = false,
            playedMovePoints = emptyList(), moveQualities = emptyList(),
            analysisMoves = emptyList(), analysisMoveIndex = 0,
            winrate = 0f, scoreLead = 0f, ownership = null,
            candidateInfo = emptyList(), topCandidatePoints = emptyList(),
            topCandidateWinrates = emptyList(), showTerritoryOverlay = false,
            territoryResult = ""
        )
        recorder.reset()

        // Clamp handicap for non-standard boards
        val maxHandicap = when (boardSize) { 19 -> 9; 13 -> 9; 9 -> 5; else -> 4 }
        val realHandicap = handicap.coerceIn(0, maxHandicap)

        // After handicap, White always plays first
        val aiColor = playerColor.opposite()
        val isPlayerFirst = if (realHandicap > 0) {
            playerColor == StoneColor.WHITE  // White plays first after handicap
        } else {
            playerColor == StoneColor.BLACK  // Normal: Black first
        }

        val newBoard = GoBoard(boardSize)
        _state.value = s.copy(
            board = newBoard, boardSize = boardSize,
            currentPlayer = StoneColor.BLACK, playerColor = playerColor,
            isPlayerTurn = isPlayerFirst, lastMovePoint = null,
            capturedByBlack = 0, capturedByWhite = 0,
            handicap = realHandicap, komi = komi,
            gameMessage = if (isPlayerFirst) "Your turn" else "AI thinking...",
            showNewGameDialog = false, gameMode = GameMode.PLAY
        )

        viewModelScope.launch {
            engine?.setBoardSize(boardSize)
            engine?.clearBoard()
            engine?.setKomi(komi)
            engine?.sendCommand("time_settings 0 $aiTime 1")

            if (realHandicap > 0) {
                // Place handicap stones via GTP
                sendGtpCommand("fixed_handicap $realHandicap")
                // Apply handicap on local board
                val handicapPoints = getHandicapPoints(boardSize, realHandicap)
                for (pt in handicapPoints) {
                    newBoard.playMove(Move.Stone(pt, StoneColor.BLACK))
                }
                _state.value = _state.value.copy(
                    board = newBoard,
                    currentPlayer = StoneColor.WHITE,
                    isPlayerTurn = playerColor == StoneColor.WHITE,
                    capturedByBlack = newBoard.getCapturedWhite(),
                    capturedByWhite = newBoard.getCapturedBlack()
                )
            }

            if (!isPlayerFirst) requestAiMove()
        }
    }

    private fun sendGtpCommand(cmd: String) {
        viewModelScope.launch {
            engine?.sendCommand(cmd)
            engine?.waitForResponse(5000)
        }
    }

    private fun getHandicapPoints(boardSize: Int, handicap: Int): List<Point> {
        val maxHandicap = when (boardSize) { 19 -> 9; 13 -> 9; 9 -> 5; else -> 4 }
        val h = handicap.coerceIn(0, maxHandicap)
        val points = when (boardSize) {
            19 -> listOf(
                Point(3,3), Point(15,15), Point(15,3), Point(3,15),
                Point(3,9), Point(15,9), Point(9,3), Point(9,15), Point(9,9)
            )
            13 -> listOf(
                Point(3,3), Point(9,9), Point(9,3), Point(3,9),
                Point(3,6), Point(9,6), Point(6,3), Point(6,9), Point(6,6)
            )
            9 -> listOf(
                Point(2,2), Point(6,6), Point(6,2), Point(2,6), Point(4,4)
            )
            else -> {
                val sp = (boardSize - 1) / 4
                listOf(Point(sp, sp), Point(boardSize-1-sp, boardSize-1-sp), Point(boardSize-1-sp, sp), Point(sp, boardSize-1-sp))
            }
        }
        return points.take(h)
    }

    fun selectModel(model: KataGoEngine.Model) {
        _state.value = _state.value.copy(selectedModel = model, showModelSelector = false)
        stopEngine(); startEngine(model)
    }

    fun getSoundPlayer(): StoneSoundPlayer? = soundPlayer

    fun showSavedGamesDialog() {
        _state.value = _state.value.copy(
            showSavedGamesDialog = true,
            savedGames = listSavedGames()
        )
    }
    fun dismissSavedGamesDialog() {
        _state.value = _state.value.copy(showSavedGamesDialog = false)
    }
    fun loadSavedGame(path: String) {
        loadGameFromSgf(path)
        dismissSavedGamesDialog()
    }

    // ── SGF import/export ──

    /** Export current game to an SGF file in the app's external files dir. */
    fun saveGameAsSgf(): String? {
        val ctx = appContext ?: return null
        val moves = recorder.getMoves().map { it.move }
        if (moves.isEmpty()) return null
        val sgf = com.badukai.next.sgf.SgfUtil.exportSgf(moves, _state.value.boardSize, _state.value.komi)
        return try {
            val dir = File(ctx.getExternalFilesDir(null), "games").apply { mkdirs() }
            val time = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val file = File(dir, "game_$time.sgf")
            file.writeText(sgf)
            _state.value = _state.value.copy(gameMessage = "Saved: ${file.name}")
            file.absolutePath
        } catch (e: Exception) {
            AppLogger.e(TAG, "SGF save failed", e)
            _state.value = _state.value.copy(gameMessage = "Save failed: ${e.message}")
            null
        }
    }

    /** List saved SGF games in the app's external files dir. */
    fun listSavedGames(): List<Pair<String, String>> {
        val ctx = appContext ?: return emptyList()
        val dir = File(ctx.getExternalFilesDir(null), "games")
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.isFile && it.name.endsWith(".sgf") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name to it.absolutePath }
            ?: emptyList()
    }

    /** Load a saved SGF game into analysis mode. */
    fun loadGameFromSgf(filePath: String) {
        val ctx = appContext ?: return
        val file = File(filePath)
        if (!file.exists()) return
        val text = file.readText()
        loadSgfText(text)
    }

    /** Load SGF text into analysis mode (used for import and file load). */
    fun loadSgfText(text: String) {
        val s = _state.value
        val moves = com.badukai.next.sgf.SgfUtil.parseSgf(text, s.boardSize)
        if (moves.isEmpty()) {
            _state.value = _state.value.copy(gameMessage = "No moves in SGF")
            return
        }
        recorder.reset()
        freeStoneColor = StoneColor.BLACK
        val newBoard = GoBoard(s.boardSize)
        for (m in moves) newBoard.playMove(m)
        for (m in moves) recorder.recordMove(m)
        _state.value = s.copy(
            board = newBoard,
            analysisMoves = recorder.getAnalysisMoves(),
            analysisMoveIndex = moves.size,
            currentPlayer = moves.lastOrNull()?.let {
                (it as? Move.Stone)?.color?.opposite() ?: StoneColor.BLACK
            } ?: StoneColor.BLACK,
            isPlayerTurn = false,
            gameMode = GameMode.ANALYZE,
            gameMessage = "Loaded ${moves.size} moves",
            winrateHistory = emptyList(),
            scoreLeadHistory = emptyList()
        )
        // Sync engine to this position
        viewModelScope.launch {
            engine?.setBoardSize(_state.value.boardSize)
            engine?.clearBoard()
            for (m in moves) {
                if (m is Move.Stone) {
                    engine?.playMove(m.color.toGtp(), com.badukai.next.sgf.SgfUtil.pointToSgf(m.point, _state.value.boardSize))
                } else if (m is Move.Pass) {
                    engine?.playMove(m.color.toGtp(), "pass")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopEngine()
        soundPlayer?.release()
    }
}
