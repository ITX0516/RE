package com.badukai.next.analysis

/**
 * Holds analysis info for a single candidate move.
 */
data class CandidateMove(
    val x: Int,
    val y: Int,
    val winRate: Float? = null,
    val scoreLead: Float? = null,
    val visits: Int? = null,
    val isBest: Boolean = false
)

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
