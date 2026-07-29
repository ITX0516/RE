package com.badukai.next.game

import android.content.Context
import com.badukai.next.logging.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.badukai.next.engine.KataGoEngine
import com.badukai.next.ui.GameTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val currentTheme: GameTheme = GameTheme.WARM_LIGHT
)

class GameViewModel : ViewModel() {

    companion object {
        private const val TAG = "GameViewModel"
    }

    private var engine: KataGoEngine? = null

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    fun initialize(context: Context) {
        if (engine == null) {
            engine = KataGoEngine(context)
        }
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
        val currentState = _state.value

        if (!currentState.isPlayerTurn || currentState.isThinking) {
            AppLogger.d(TAG, "Not player's turn or AI is thinking")
            return
        }

        if (currentState.board.isGameOver) {
            AppLogger.d(TAG, "Game is over")
            return
        }

        val point = Point(x, y)

        if (!currentState.board.isLegalMove(point, currentState.currentPlayer)) {
            AppLogger.d(TAG, "Illegal move at $x, $y")
            return
        }

        playMove(point, currentState.currentPlayer)
    }

    private fun playMove(point: Point, color: StoneColor) {
        val currentState = _state.value
        val board = currentState.board

        board.playMove(Move.Stone(point, color))

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

        viewModelScope.launch {
            engine?.playMove(color.toGtp(), point.toGtp(currentState.boardSize))

            if (!isPlayerTurn && !board.isGameOver) {
                requestAiMove()
            }
        }
    }

    private fun requestAiMove() {
        val currentState = _state.value
        val engine = this.engine

        if (engine == null || !currentState.isEngineReady) {
            AppLogger.e(TAG, "Engine not ready")
            return
        }

        _state.value = currentState.copy(isThinking = true, gameMessage = "AI thinking...")

        viewModelScope.launch {
            try {
                val aiColor = currentState.currentPlayer
                val move = engine.generateMove(aiColor.toGtp())

                withContext(Dispatchers.Main) {
                    handleAiMove(move, aiColor)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error getting AI move", e)
                _state.value = _state.value.copy(
                    isThinking = false,
                    gameMessage = "AI error: ${e.message}"
                )
            }
        }
    }

    private fun handleAiMove(move: String?, color: StoneColor) {
        val currentState = _state.value

        _state.value = currentState.copy(isThinking = false)

        when {
            move == null -> {
                _state.value = _state.value.copy(gameMessage = "AI returned no move")
            }
            move.equals("pass", ignoreCase = true) -> {
                currentState.board.playMove(Move.Pass(color))
                _state.value = _state.value.copy(
                    currentPlayer = color.opposite(),
                    isPlayerTurn = true,
                    lastMovePoint = null,
                    gameMessage = "AI passed. Your turn"
                )

                if (currentState.board.isGameOver) {
                    handleGameEnd()
                }
            }
            move.equals("resign", ignoreCase = true) -> {
                currentState.board.playMove(Move.Resign(color))
                _state.value = _state.value.copy(
                    gameMessage = "AI resigned. You win!"
                )
            }
            else -> {
                val point = Point.fromGtp(move, currentState.boardSize)
                if (point != null) {
                    currentState.board.playMove(Move.Stone(point, color))
                    _state.value = _state.value.copy(
                        currentPlayer = color.opposite(),
                        isPlayerTurn = true,
                        lastMovePoint = point,
                        capturedByBlack = currentState.board.getCapturedWhite(),
                        capturedByWhite = currentState.board.getCapturedBlack(),
                        gameMessage = "Your turn"
                    )
                } else {
                    AppLogger.e(TAG, "Failed to parse AI move: $move")
                    _state.value = _state.value.copy(gameMessage = "AI error")
                }
            }
        }
    }

    fun pass() {
        val currentState = _state.value

        if (!currentState.isPlayerTurn || currentState.isThinking) return

        currentState.board.playMove(Move.Pass(currentState.currentPlayer))

        viewModelScope.launch {
            engine?.playMove(currentState.currentPlayer.toGtp(), "pass")
        }

        if (currentState.board.isGameOver) {
            handleGameEnd()
            return
        }

        val newPlayer = currentState.currentPlayer.opposite()
        _state.value = currentState.copy(
            currentPlayer = newPlayer,
            isPlayerTurn = false,
            lastMovePoint = null,
            gameMessage = "You passed. AI thinking..."
        )

        requestAiMove()
    }

    fun resign() {
        val currentState = _state.value

        currentState.board.playMove(Move.Resign(currentState.playerColor))

        val winner = if (currentState.playerColor == StoneColor.BLACK) "White" else "Black"
        _state.value = currentState.copy(
            gameMessage = "You resigned. $winner wins!"
        )
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
        val currentState = _state.value

        if (currentState.isThinking) return
        if (currentState.board.getMoveCount() < 2) return

        currentState.board.undo()
        currentState.board.undo()

        viewModelScope.launch {
            engine?.undo()
            engine?.undo()
        }

        val lastMove = currentState.board.getLastMove()
        val lastPoint = (lastMove as? Move.Stone)?.point

        _state.value = currentState.copy(
            currentPlayer = currentState.playerColor,
            isPlayerTurn = true,
            lastMovePoint = lastPoint,
            capturedByBlack = currentState.board.getCapturedWhite(),
            capturedByWhite = currentState.board.getCapturedBlack(),
            gameMessage = "Undone. Your turn"
        )
    }

    fun showNewGameDialog() {
        _state.value = _state.value.copy(showNewGameDialog = true)
    }

    fun hideNewGameDialog() {
        _state.value = _state.value.copy(showNewGameDialog = false)
    }

    fun showModelSelector() {
        _state.value = _state.value.copy(showModelSelector = true)
    }

    fun hideModelSelector() {
        _state.value = _state.value.copy(showModelSelector = false)
    }

    fun showSettingsDialog() {
        _state.value = _state.value.copy(showSettings = true)
    }

    fun hideSettingsDialog() {
        _state.value = _state.value.copy(showSettings = false)
    }

    fun toggleCoordinates() {
        _state.value = _state.value.copy(showCoordinates = !_state.value.showCoordinates)
    }

    fun toggleSound() {
        _state.value = _state.value.copy(soundEnabled = !_state.value.soundEnabled)
    }

    fun setTheme(theme: GameTheme) {
        _state.value = _state.value.copy(currentTheme = theme)
    }

    fun startNewGame(playerColor: StoneColor, boardSize: Int) {
        val currentState = _state.value

        val newBoard = GoBoard(boardSize)
        val isPlayerFirst = playerColor == StoneColor.BLACK

        _state.value = currentState.copy(
            board = newBoard,
            boardSize = boardSize,
            currentPlayer = StoneColor.BLACK,
            playerColor = playerColor,
            isPlayerTurn = isPlayerFirst,
            lastMovePoint = null,
            capturedByBlack = 0,
            capturedByWhite = 0,
            gameMessage = if (isPlayerFirst) "Your turn" else "AI thinking...",
            showNewGameDialog = false
        )

        viewModelScope.launch {
            engine?.setBoardSize(boardSize)
            engine?.clearBoard()
            engine?.setKomi(if (boardSize == 9) 5.5f else 7.5f)

            if (!isPlayerFirst) {
                requestAiMove()
            }
        }
    }

    fun selectModel(model: KataGoEngine.Model) {
        _state.value = _state.value.copy(
            selectedModel = model,
            showModelSelector = false
        )

        stopEngine()
        startEngine(model)
    }

    override fun onCleared() {
        super.onCleared()
        stopEngine()
    }
}
