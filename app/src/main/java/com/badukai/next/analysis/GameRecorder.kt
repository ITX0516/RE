package com.badukai.next.analysis

import com.badukai.next.game.Move
import com.badukai.next.game.Point
import com.badukai.next.game.StoneColor

data class RecordedMove(
    val move: Move,
    val moveNumber: Int,
    var winRate: Float? = null,
    var scoreLead: Float? = null
)

class GameRecorder {
    private val moves = mutableListOf<RecordedMove>()

    fun reset() {
        moves.clear()
    }

    fun recordMove(move: Move, winRate: Float? = null, scoreLead: Float? = null) {
        moves.add(RecordedMove(move, moves.size + 1, winRate, scoreLead))
    }

    fun getMoves(): List<RecordedMove> = moves.toList()

    fun getMoveCount(): Int = moves.size

    fun getAnalysisMoves(): List<RecordedMove> = moves.toList()

    fun removeLast() {
        if (moves.isNotEmpty()) moves.removeAt(moves.size - 1)
    }

    fun setAnalysisWinRate(moveIndex: Int, wr: Float, lead: Float) {
        if (moveIndex in moves.indices) {
            moves[moveIndex].winRate = wr
            moves[moveIndex].scoreLead = lead
        }
    }

    fun isAnalysisMode(): Boolean = moves.size > 0
}
