package com.badukai.next.analysis

/**
 * Result from kata-analyze command
 */
data class AnalyzeResult(
    val winrate: Double,
    val scoreLead: Double,
    val moves: List<CandidateMove>,
    val ownership: List<Double>?
)

/**
 * Holds analysis info for a single candidate move.
 */
data class CandidateMove(
    val x: Int = -1,
    val y: Int = -1,
    val winRate: Float? = null,
    val scoreLead: Float? = null,
    val visits: Int? = null,
    val isBest: Boolean = false
) {
    companion object {
        private val letters = "ABCDEFGHJKLMNOPQRST"
        fun fromGtp(move: String, boardSize: Int): CandidateMove? {
            if (move == "pass" || move.length < 2) return null
            val col = move[0].uppercaseChar()
            val colIndex = letters.indexOf(col)
            if (colIndex < 0 || colIndex >= boardSize) return null
            val row = move.substring(1).toIntOrNull() ?: return null
            return CandidateMove(x = colIndex, y = boardSize - row)
        }
    }
}

/**
 * Analysis data for a single position in the game.
 */
data class PositionAnalysis(
    val moveNumber: Int,
    val winRate: Float? = null,
    val scoreLead: Float? = null,
    val candidates: List<CandidateMove> = emptyList()
)

/**
 * Sub-tab in analysis mode.
 */
enum class AnalysisTab(val label: String, val icon: String) {
    MOVE_TREE("\u265F", "\u265F"),
    CHART("\u25A0", "\u25A0"),
    CANDIDATES("\u25B6", "\u25B6")
}
