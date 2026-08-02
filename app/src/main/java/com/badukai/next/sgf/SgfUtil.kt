package com.badukai.next.sgf

import com.badukai.next.game.Move
import com.badukai.next.game.Point
import com.badukai.next.game.StoneColor
import com.badukai.next.logging.AppLogger

/**
 * Minimal SGF (Smart Game Format) reader/writer for Go games.
 */
object SgfUtil {

    private const val TAG = "SgfUtil"
    // Standard SGF alphabet (skips 'i' to avoid confusion with 'j' per FF[4] spec)
    private val letters = "abcdefghjklmnopqrst"
    // Legacy alphabet (includes 'i') for backward-compatible parsing of old exports
    private val legacyLetters = "abcdefghijklmnopqrst"

    /** Convert a board point to SGF coordinate (e.g. (3,3) -> "dd" on 19x19). */
    fun pointToSgf(p: Point, boardSize: Int): String {
        val x = p.x.coerceIn(0, boardSize - 1)
        val y = p.y.coerceIn(0, boardSize - 1)
        return "${letters[x]}${letters[boardSize - 1 - y]}"
    }

    /** Convert SGF coordinate to a board point. Supports both standard (skip-i)
     *  and legacy (with-i) alphabets for backward compatibility. */
    fun sgfToPoint(sgf: String, boardSize: Int): Point? {
        if (sgf.length < 2) return null
        val x = letters.indexOf(sgf[0].lowercaseChar())
            .takeIf { it >= 0 } ?: legacyLetters.indexOf(sgf[0].lowercaseChar())
        val yFromTop = letters.indexOf(sgf[1].lowercaseChar())
            .takeIf { it >= 0 } ?: legacyLetters.indexOf(sgf[1].lowercaseChar())
        if (x < 0 || yFromTop < 0) return null
        val y = boardSize - 1 - yFromTop
        if (x >= boardSize || y < 0 || y >= boardSize) return null
        return Point(x, y)
    }

    /**
     * Export recorded moves to an SGF string.
     */
    fun exportSgf(moves: List<Move>, boardSize: Int, komi: Float): String {
        val sb = StringBuilder()
        sb.append("(;GM[1]FF[4]CA[UTF-8]SZ[$boardSize]KM[$komi]RU[Chinese]\n")
        for (m in moves) {
            when (m) {
                is Move.Stone -> {
                    val color = if (m.color == StoneColor.BLACK) "B" else "W"
                    val coord = pointToSgf(m.point, boardSize)
                    sb.append(";$color[$coord]")
                }
                is Move.Pass -> {
                    val color = if (m.color == StoneColor.BLACK) "B" else "W"
                    sb.append(";$color[]")
                }
                is Move.Resign -> { /* skip resign in SGF */ }
            }
        }
        sb.append(")\n")
        return sb.toString()
    }

    /**
     * Parse an SGF string into a list of moves.
     */
    /**
     * Parse an SGF string into a list of moves.
     * NOTE: regex-based — handles the main line only. Variations/comments with
     * B[...]/W[...] tokens are ignored for now (acceptable for save/load of
     * our own games, which have no variations).
     */
    fun parseSgf(sgfText: String, boardSize: Int): List<Move> {
        val moves = mutableListOf<Move>()
        try {
            val regex = Regex("([BW])\\[([a-z]*)\\]")
            for (match in regex.findAll(sgfText)) {
                val color = if (match.groupValues[1] == "B") StoneColor.BLACK else StoneColor.WHITE
                val coord = match.groupValues[2]
                if (coord.isEmpty()) {
                    moves.add(Move.Pass(color))
                } else {
                    val pt = sgfToPoint(coord, boardSize)
                    if (pt != null) moves.add(Move.Stone(pt, color))
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "SGF parse error", e)
        }
        return moves
    }
}
