package com.badukai.next.game

import android.content.Context
import com.badukai.next.analysis.AnalyzeResult
import com.badukai.next.analysis.GameRecorder
import com.badukai.next.analysis.RecordedMove
import com.badukai.next.audio.StoneSoundPlayer
import com.badukai.next.engine.KataGoEngine
import com.badukai.next.logging.AppLogger
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
    val selectedModel: KataGoEngine.Model = KataGoEngine.Model.HUMAN,
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
    val pendingTap: Point? = null,
    val doubleTapActive: Boolean = false,
    val confirmMoveQueued: Point? = null,
    val placeSoundIndex: Int = 0,
    val showTerritoryDialog: Boolean = false,
    val territoryResult: String = "",
    val winrate: Float = 0f,
    val scoreLead: Float = 0f,
    val ownership: List<Float>? = null
)

class GameViewModel : ViewModel() {

    companion object {
        private const val TAG = "GameViewModel"
    }

    private var engine: KataGoEngine? = null
    private var soundPlayer: StoneSoundPlayer? = null
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
            val result = engine?.analyzePosition(300)
            if (result != null) {
                _state.value = _state.value.copy(
                    winrate = result.winrate.toFloat(),
                    scoreLead = result.scoreLead.toFloat(),
                    ownership = result.ownership?.map { it.toFloat() }
                )
            }
        }
    }

    fun forceEndGame() {
        viewModelScope.launch {
            val s = _state.value
            // Play both pass on engine
            engine?.playMove("black", "pass")
            engine?.playMove("white", "pass")
            val score = engine?.getFinalScore()
            _state.value = _state.value.copy(
                isPlayerTurn = false,
                gameMessage = score?.let { "Game over. $it" } ?: "Game over",
                territoryResult = score ?: "No result"
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
                    lastMovePoint = null, gameMessage = "AI passed. Your turn"
                )
                if (s.board.isGameOver) handleGameEnd()
            }
            move.equals("resign", ignoreCase = true) -> {
                val m = Move.Resign(color)
                s.board.playMove(m)
                recorder.recordMove(m)
                _state.value = _state.value.copy(gameMessage = "AI resigned. You win!")
            }
            else -> {
                val pt = Point.fromGtp(move, s.boardSize)
                if (pt != null) {
                    val m = Move.Stone(pt, color)
                    s.board.playMove(m)
                    recorder.recordMove(m)
                    _state.value = _state.value.copy(
                        currentPlayer = color.opposite(), isPlayerTurn = true,
                        lastMovePoint = pt,
                        capturedByBlack = s.board.getCapturedWhite(),
                        capturedByWhite = s.board.getCapturedBlack(),
                        gameMessage = "Your turn"
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
            lastMovePoint = null, gameMessage = "You passed. AI thinking..."
        )
        requestAiMove()
    }

    fun resign() {
        val s = _state.value
        val m = Move.Resign(s.playerColor)
        s.board.playMove(m)
        recorder.recordMove(m)
        val winner = if (s.playerColor == StoneColor.BLACK) "White" else "Black"
        _state.value = s.copy(gameMessage = "You resigned. $winner wins!")
    }

    private fun handleGameEnd() {
        viewModelScope.launch {
            val score = engine?.getFinalScore()
            _state.value = _state.value.copy(
                gameMessage = score?.let { "Game over. $it" } ?: "Game over"
            )
        }
    }

    fun undo() {
        val s = _state.value
        if (s.isThinking || s.board.getMoveCount() < 2) return
        s.board.undo(); s.board.undo()
        viewModelScope.launch { engine?.undo(); engine?.undo() }
        val lastMove = s.board.getLastMove()
        _state.value = s.copy(
            currentPlayer = s.playerColor, isPlayerTurn = true,
            lastMovePoint = (lastMove as? Move.Stone)?.point,
            capturedByBlack = s.board.getCapturedWhite(),
            capturedByWhite = s.board.getCapturedBlack(),
            gameMessage = "Undone. Your turn"
        )
    }

    fun showNewGameDialog() { _state.value = _state.value.copy(showNewGameDialog = true) }
    fun hideNewGameDialog() { _state.value = _state.value.copy(showNewGameDialog = false) }
    fun showModelSelector() { _state.value = _state.value.copy(showModelSelector = true) }
    fun hideModelSelector() { _state.value = _state.value.copy(showModelSelector = false) }
    fun showSettingsDialog() { _state.value = _state.value.copy(showSettings = true) }
    fun hideSettingsDialog() { _state.value = _state.value.copy(showSettings = false) }

    fun showTerritoryDialog() {
        estimateScore()
        _state.value = _state.value.copy(showTerritoryDialog = true)
    }
    fun hideTerritoryDialog() { _state.value = _state.value.copy(showTerritoryDialog = false, territoryResult = "") }

    private fun estimateScore() {
        val s = _state.value
        viewModelScope.launch {
            if (s.board.isGameOver) {
                val score = engine?.getFinalScore()
                _state.value = _state.value.copy(territoryResult = score ?: "No result")
            } else {
                // Use latest analysis if available
                if (s.winrate > 0) {
                    val wr = "%.1f".format(s.winrate * 100)
                    val lead = "%.1f".format(s.scoreLead)
                    _state.value = _state.value.copy(
                        territoryResult = "Win rate: B ${100 - s.winrate * 100:.1f}% / W ${wr}%\nScore: ${if (s.scoreLead >= 0) "B+" else "W+"}${kotlin.math.abs(s.scoreLead).let { "%.1f".format(it) }}"
                    )
                } else {
                    // No analysis yet, request it
                    val result = engine?.analyzePosition(500)
                    if (result != null) {
                        _state.value = _state.value.copy(
                            winrate = result.winrate.toFloat(),
                            scoreLead = result.scoreLead.toFloat(),
                            territoryResult = "Win rate: B ${"%.1f".format((1 - result.winrate) * 100)}% / W ${"%.1f".format(result.winrate * 100)}%\nScore: ${if (result.scoreLead >= 0) "B+" else "W+"}${"%.1f".format(kotlin.math.abs(result.scoreLead))}"
                        )
                    } else {
                        _state.value = _state.value.copy(territoryResult = "kata-analyze not available\nBlack captured: ${s.capturedByBlack}\nWhite captured: ${s.capturedByWhite}")
                    }
                }
            }
        }
    }
    fun toggleCoordinates() { _state.value = _state.value.copy(showCoordinates = !_state.value.showCoordinates) }
    fun toggleSound() { _state.value = _state.value.copy(soundEnabled = !_state.value.soundEnabled) }
    fun setTheme(theme: GameTheme) { _state.value = _state.value.copy(currentTheme = theme) }
    fun setPlaceSoundIndex(idx: Int) {
        _state.value = _state.value.copy(placeSoundIndex = idx)
        soundPlayer?.setPlaceSound(idx)
    }
    fun setPlacementMode(mode: PlacementMode) {
        _state.value = _state.value.copy(placementMode = mode, pendingTap = null, doubleTapActive = false, confirmMoveQueued = null)
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

    fun startNewGame(playerColor: StoneColor, boardSize: Int) {
        val s = _state.value
        freeStoneColor = StoneColor.BLACK
        recorder.reset()
        val newBoard = GoBoard(boardSize)
        val isPlayerFirst = playerColor == StoneColor.BLACK
        _state.value = s.copy(
            board = newBoard, boardSize = boardSize,
            currentPlayer = StoneColor.BLACK, playerColor = playerColor,
            isPlayerTurn = isPlayerFirst, lastMovePoint = null,
            capturedByBlack = 0, capturedByWhite = 0,
            gameMessage = if (isPlayerFirst) "Your turn" else "AI thinking...",
            showNewGameDialog = false, gameMode = GameMode.PLAY
        )
        viewModelScope.launch {
            engine?.setBoardSize(boardSize); engine?.clearBoard()
            engine?.setKomi(if (boardSize == 9) 5.5f else 7.5f)
            if (!isPlayerFirst) requestAiMove()
        }
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
