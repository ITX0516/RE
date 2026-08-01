package com.badukai.next.game

import android.content.Context
import com.badukai.next.analysis.GameRecorder
import com.badukai.next.analysis.RecordedMove
import com.badukai.next.audio.StoneSoundPlayer
import com.badukai.next.game.SettingsStore
import com.badukai.next.engine.KataGoEngine
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
    val handicap: Int = 0,
    val komi: Float = 7.5f,
    val showTerritoryDialog: Boolean = false,
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
    val gameResult: GameResult? = null
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
    lateinit var settingsStore: SettingsStore
    val recorder = GameRecorder()

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    fun initialize(context: Context) {
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
                stoneAnimation = s.stoneAnimation
            )
            soundPlayer?.setPlaceSound(s.placeSoundIndex)
            BadukNextColors.setTheme(s.currentTheme)
        }

        recorder.reset()
    }

    fun startEngine(model: KataGoEngine.Model = _state.value.selectedModel) {
        val engine = this.engine ?: return
        if (_state.value.isEngineStarting) return

        _state.value = _state.value.copy(
            isEngineStarting = true,
            gameMessage = "Starting AI..."
        )

        viewModelScope.launch {
            try {
                val success = engine.start(model)
                if (success) {
                    engine.setBoardSize(_state.value.boardSize)
                    engine.clearBoard()
                    engine.setKomi(7.5f)
                    _state.value = _state.value.copy(
                        isEngineReady = true,
                        isEngineStarting = false,
                        selectedModel = model,
                        gameMessage = "Ready to play"
                    )
                    if (_state.value.playerColor == StoneColor.WHITE) {
                        requestAiMove()
                    }
                } else {
                    _state.value = _state.value.copy(
                        isEngineReady = false,
                        isEngineStarting = false,
                        gameMessage = "Failed to start AI"
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
        s.board.playMove(Move.Stone(point, freeStoneColor))
        val color = freeStoneColor
        freeStoneColor = freeStoneColor.opposite()
        _state.value = s.copy(
            lastMovePoint = point,
            capturedByBlack = s.board.getCapturedWhite(),
            capturedByWhite = s.board.getCapturedBlack()
        )
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
            analysisMoves = recorder.rebuildAnalysisMoves(),
            gameMessage = if (isPlayerTurn) "Your turn" else "AI thinking..."
        )

        requestAnalysis()
        viewModelScope.launch {
            engine?.playMove(color.toGtp(), point.toGtp(currentState.boardSize))
            if (!isPlayerTurn && !board.isGameOver) {
                requestAiMove()
            }
        }
    }

    private fun requestAnalysis() {
        viewModelScope.launch {
            val result = engine?.analyzePosition(100) // lower visits for speed
            if (result == null) return@launch
            val s = _state.value
            val candidates = result.moves.take(10).map { cm ->
                val coord = if (cm.x >= 0 && cm.y >= 0) {
                    val letters = "ABCDEFGHJKLMNOPQRST"
                    "${letters[cm.x]}${s.boardSize - cm.y}"
                } else "pass"
                val wr = cm.winRate?.let { "%.1f%%".format(it * 100) } ?: "-"
                val sc = cm.scoreLead?.let { if (it >= 0) "+%.1f".format(it) else "%.1f".format(it) } ?: "-"
                "$coord  $wr  $sc"
            }
            val topPts = result.moves.take(3).mapNotNull { cm ->
                if (cm.x >= 0 && cm.y >= 0) Pair(cm.x, cm.y) else null
            }
            val topWrs = result.moves.take(3).map { cm -> cm.winRate ?: 0.5f }
            // Convert winrate to Black's perspective for consistent history
            val blackWinrate = if (s.currentPlayer == StoneColor.WHITE)
                1f - result.winrate.toFloat() else result.winrate.toFloat()
            val newHistory = s.winrateHistory + blackWinrate
            val newScoreHistory = s.scoreLeadHistory + result.scoreLead.toFloat()

            _state.value = s.copy(
                winrate = result.winrate.toFloat(),
                scoreLead = result.scoreLead.toFloat(),
                ownership = result.ownership?.map { it.toFloat() },
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
                moverDelta <= -0.10f -> 2
                moverDelta <= -0.05f -> 1
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

        _state.value = s.copy(isThinking = true, gameMessage = "AI thinking...")

        viewModelScope.launch {
            try {
                val aiColor = s.currentPlayer
                val move = e.generateMove(aiColor.toGtp())
                withContext(Dispatchers.Main) { handleAiMove(move, aiColor) }
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
            move.equals("pass", ignoreCase = true) -> {
                val m = Move.Pass(color)
                s.board.playMove(m)
                recorder.recordMove(m)
                _state.value = _state.value.copy(
                    currentPlayer = color.opposite(), isPlayerTurn = true,
                    lastMovePoint = null, gameMessage = "AI passed. Your turn",
                    analysisMoves = recorder.rebuildAnalysisMoves()
                )
                if (s.board.isGameOver) handleGameEnd()
            }
            move.equals("resign", ignoreCase = true) -> {
                val m = Move.Resign(color)
                s.board.playMove(m)
                recorder.recordMove(m)
                _state.value = _state.value.copy(
                    gameMessage = "AI resigned. You win!",
                    analysisMoves = recorder.rebuildAnalysisMoves(),
                    gameResult = GameResult.WIN
                )
            }
            else -> {
                val pt = Point.fromGtp(move, s.boardSize)
                if (pt != null) {
                    val m = Move.Stone(pt, color)
                    s.board.playMove(m)
                    recorder.recordMove(m)
                    if (s.soundEnabled) soundPlayer?.playPlace()
                    requestAnalysis()
                    _state.value = _state.value.copy(
                        currentPlayer = color.opposite(), isPlayerTurn = true,
                        lastMovePoint = pt,
                        capturedByBlack = s.board.getCapturedWhite(),
                        capturedByWhite = s.board.getCapturedBlack(),
                        gameMessage = "Your turn",
                        analysisMoves = recorder.rebuildAnalysisMoves()
                    )
                } else {
                    _state.value = _state.value.copy(gameMessage = "AI error")
                }
            }
        }
    }

    fun pass() {
        val s = _state.value
        if (!s.isPlayerTurn || s.isThinking) return
        val m = Move.Pass(s.currentPlayer)
        s.board.playMove(m)
        recorder.recordMove(m)
        viewModelScope.launch { engine?.playMove(s.currentPlayer.toGtp(), "pass") }
        if (s.board.isGameOver) { handleGameEnd(); return }
        _state.value = s.copy(
            currentPlayer = s.currentPlayer.opposite(), isPlayerTurn = false,
            lastMovePoint = null, gameMessage = "You passed. AI thinking...",
            analysisMoves = recorder.rebuildAnalysisMoves()
        )
        requestAiMove()
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
        viewModelScope.launch {
            val score = engine?.getFinalScore()
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
        viewModelScope.launch { engine?.undo(); engine?.undo() }
        val lastMove = s.board.getLastMove()
        _state.value = s.copy(
            currentPlayer = s.playerColor, isPlayerTurn = true,
            lastMovePoint = (lastMove as? Move.Stone)?.point,
            capturedByBlack = s.board.getCapturedWhite(),
            capturedByWhite = s.board.getCapturedBlack(),
            gameMessage = "Undone. Your turn",
            analysisMoves = recorder.rebuildAnalysisMoves()
        )
    }

    fun showNewGameDialog() { _state.value = _state.value.copy(showNewGameDialog = true) }
    fun hideNewGameDialog() { _state.value = _state.value.copy(showNewGameDialog = false) }
    fun showModelSelector() { _state.value = _state.value.copy(showModelSelector = true) }
    fun hideModelSelector() { _state.value = _state.value.copy(showModelSelector = false) }
    fun showSettingsDialog() { _state.value = _state.value.copy(showSettings = true) }
    fun hideSettingsDialog() { _state.value = _state.value.copy(showSettings = false) }

    fun toggleTerritoryOverlay() {
        val s = _state.value
        val newVal = !s.showTerritoryOverlay
        if (newVal) estimateScore()
        _state.value = s.copy(showTerritoryOverlay = newVal, showTerritoryDialog = newVal)
    }
    fun hideTerritoryDialog() { _state.value = _state.value.copy(showTerritoryDialog = false, territoryResult = "") }

    fun toggleEyeOverlay() {
        _state.value = _state.value.copy(showEyeOverlay = !_state.value.showEyeOverlay)
    }

    fun dismissCelebration() {
        _state.value = _state.value.copy(gameResult = null)
    }

    private fun estimateScore() {
        val s = _state.value
        viewModelScope.launch {
            if (s.board.isGameOver) {
                val score = engine?.getFinalScore()
                _state.value = _state.value.copy(territoryResult = score ?: "No result")
            } else {
                // Use latest analysis if available
                if (s.winrate > 0) {
                    val bw = "%.1f".format((1f - s.winrate) * 100f)
                    val ww = "%.1f".format(s.winrate * 100f)
                    val lead = "%.1f".format(s.scoreLead)
                    val side = if (s.scoreLead >= 0) "B" else "W"
                    _state.value = _state.value.copy(
                        territoryResult = "Win rate: B $bw% / W $ww%\nScore: $side+$lead"
                    )
                } else {
                    val result = engine?.analyzePosition(500)
                    if (result != null) {
                        val bw = "%.1f".format((1f - result.winrate.toFloat()) * 100f)
                        val ww = "%.1f".format(result.winrate.toFloat() * 100f)
                        val lead = "%.1f".format(result.scoreLead.toFloat())
                        val side = if (result.scoreLead >= 0) "B" else "W"
                        _state.value = _state.value.copy(
                            winrate = result.winrate.toFloat(),
                            scoreLead = result.scoreLead.toFloat(),
                            territoryResult = "Win rate: B $bw% / W $ww%\nScore: $side+$lead"
                        )
                    } else {
                        val stoneCount = s.board.getMoveCount()
                        _state.value = _state.value.copy(territoryResult = "Analysis unavailable — check engine supports kata-analyze\nCaptures: B=${s.capturedByBlack} W=${s.capturedByWhite}\nStone count: $stoneCount")
                    }
                }
            }
        }
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

    fun setGameMode(mode: GameMode) {
        if (mode == _state.value.gameMode) return
        if (mode == GameMode.ANALYZE) {
            // Determine starting color from last move
            val lastMove = _state.value.board.getLastMove()
            freeStoneColor = when (val m = lastMove) {
                is Move.Stone -> m.color.opposite()
                else -> StoneColor.BLACK
            }
            val moves = recorder.rebuildAnalysisMoves()
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

    fun startNewGame(playerColor: StoneColor, boardSize: Int, handicap: Int, komi: Float) {
        val s = _state.value
        freeStoneColor = StoneColor.BLACK
        _state.value = _state.value.copy(
            winrateHistory = emptyList(), scoreLeadHistory = emptyList(),
            gameResult = null, showEyeOverlay = false,
            playedMovePoints = emptyList(), moveQualities = emptyList()
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

    fun setModelDisplayName(name: String) {
        // Used by analysis preset selection
    }

    fun getSoundPlayer(): StoneSoundPlayer? = soundPlayer

    override fun onCleared() {
        super.onCleared()
        stopEngine()
        soundPlayer?.release()
    }
}
