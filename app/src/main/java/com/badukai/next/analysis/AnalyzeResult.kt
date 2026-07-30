package com.badukai.next.analysis

data class AnalyzeResult(
    val winrate: Double,
    val scoreLead: Double,
    val moves: List<CandidateMove>,
    val ownership: List<Double>?
)

data class CandidateMove(
    val move: String?,
    val winrate: Double,
    val scoreLead: Double,
    val visits: Int,
    val order: Int
) {
    fun toDisplayString(): String = "${move ?: "pass"} -- ${"%.1f".format(winrate * 100)}% (${if (scoreLead >= 0) "+" else ""}${"%.1f".format(scoreLead)})"
}
