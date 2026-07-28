package com.badukai.next.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.badukai.next.game.GoBoard
import com.badukai.next.game.Intersection
import com.badukai.next.game.Point
import kotlin.math.roundToInt

private const val COORD_MARGIN_RATIO = 0.04f

/**
 * Go board component
 */
@Composable
fun GoBoard(
    board: GoBoard,
    lastMovePoint: Point?,
    onIntersectionTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val boardSize = board.size
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .background(BadukNextColors.BoardBackground)
    ) {
        val sizePx = with(density) {
            kotlin.math.min(maxWidth.toPx(), maxHeight.toPx())
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, boardSize, sizePx, COORD_MARGIN_RATIO) {
                    if (!enabled) return@pointerInput
                    val padding = sizePx * COORD_MARGIN_RATIO
                    val cs = if (boardSize > 1) (sizePx - 2f * padding) / (boardSize - 1).toFloat() else sizePx
                    detectTapGestures { offset ->
                        val x = ((offset.x - padding) / cs).roundToInt().coerceIn(0, boardSize - 1)
                        val y = ((offset.y - padding) / cs).roundToInt().coerceIn(0, boardSize - 1)
                        onIntersectionTap(x, y)
                    }
                }
        ) {
            val padding = sizePx * COORD_MARGIN_RATIO
            val cellSize = if (boardSize > 1) (sizePx - 2f * padding) / (boardSize - 1).toFloat() else sizePx

            // Draw grid lines
            drawGrid(boardSize, padding, cellSize)

            // Draw star points (hoshi)
            drawStarPoints(boardSize, padding, cellSize)

            // Draw stones
            drawStones(board, boardSize, padding, cellSize, lastMovePoint)
        }
    }
}

private fun DrawScope.drawGrid(
    boardSize: Int,
    padding: Float,
    cellSize: Float
) {
    val lineWidth = if (boardSize >= 13) 1.8f else 1.4f
    val outerLineWidth = lineWidth * 1.4f

    for (i in 0 until boardSize) {
        val pos = padding + i * cellSize
        val isEdge = i == 0 || i == boardSize - 1
        val w = if (isEdge) outerLineWidth else lineWidth

        // Vertical line
        drawLine(
            color = BadukNextColors.BoardLine,
            start = Offset(pos, padding),
            end = Offset(pos, padding + (boardSize - 1) * cellSize),
            strokeWidth = w
        )

        // Horizontal line
        drawLine(
            color = BadukNextColors.BoardLine,
            start = Offset(padding, pos),
            end = Offset(padding + (boardSize - 1) * cellSize, pos),
            strokeWidth = w
        )
    }
}

private fun DrawScope.drawStarPoints(
    boardSize: Int,
    padding: Float,
    cellSize: Float
) {
    val starPoints = getStarPoints(boardSize)
    val radius = cellSize * 0.13f

    for (point in starPoints) {
        val x = padding + point.x * cellSize
        val y = padding + point.y * cellSize

        drawCircle(
            color = BadukNextColors.StarPoint,
            radius = radius,
            center = Offset(x, y)
        )
    }
}

private fun getStarPoints(boardSize: Int): List<Point> {
    return when (boardSize) {
        19 -> listOf(
            Point(3, 3), Point(9, 3), Point(15, 3),
            Point(3, 9), Point(9, 9), Point(15, 9),
            Point(3, 15), Point(9, 15), Point(15, 15)
        )
        13 -> listOf(
            Point(3, 3), Point(6, 3), Point(9, 3),
            Point(3, 6), Point(6, 6), Point(9, 6),
            Point(3, 9), Point(6, 9), Point(9, 9)
        )
        9 -> listOf(
            Point(2, 2), Point(4, 2), Point(6, 2),
            Point(2, 4), Point(4, 4), Point(6, 4),
            Point(2, 6), Point(4, 6), Point(6, 6)
        )
        else -> emptyList()
    }
}

private fun DrawScope.drawStones(
    board: GoBoard,
    boardSize: Int,
    padding: Float,
    cellSize: Float,
    lastMovePoint: Point?
) {
    val stoneRadius = cellSize * 0.46f
    val shadowRadius = stoneRadius * 1.05f
    val shadowOffsetY = stoneRadius * 0.08f

    for (y in 0 until boardSize) {
        for (x in 0 until boardSize) {
            val intersection = board.get(x, y)
            if (intersection == Intersection.EMPTY) continue

            val centerX = padding + x * cellSize
            val centerY = padding + y * cellSize
            val center = Offset(centerX, centerY)
            val isLastMove = lastMovePoint?.x == x && lastMovePoint.y == y

            when (intersection) {
                Intersection.BLACK -> {
                    // Drop shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.28f),
                        radius = shadowRadius,
                        center = Offset(centerX, centerY + shadowOffsetY)
                    )
                    // Black stone radial gradient
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                BadukNextColors.BlackStoneHighlight,
                                BadukNextColors.BlackStone
                            ),
                            center = Offset(
                                centerX - stoneRadius * 0.28f,
                                centerY - stoneRadius * 0.28f
                            ),
                            radius = stoneRadius
                        ),
                        radius = stoneRadius,
                        center = center
                    )
                }
                Intersection.WHITE -> {
                    // Drop shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.22f),
                        radius = shadowRadius,
                        center = Offset(centerX, centerY + shadowOffsetY)
                    )
                    // White stone base
                    drawCircle(
                        color = BadukNextColors.WhiteStone,
                        radius = stoneRadius,
                        center = center
                    )
                    // Radial gradient highlight
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                BadukNextColors.WhiteStoneHighlight,
                                Color.Transparent
                            ),
                            center = Offset(
                                centerX - stoneRadius * 0.3f,
                                centerY - stoneRadius * 0.3f
                            ),
                            radius = stoneRadius * 0.7f
                        ),
                        radius = stoneRadius * 0.7f,
                        center = center
                    )
                    // Thin border
                    drawCircle(
                        color = BadukNextColors.WhiteStoneBorder,
                        radius = stoneRadius,
                        center = center,
                        style = Stroke(width = 0.8f)
                    )
                }
                Intersection.EMPTY -> {}
            }

            // Last move marker - small ring on the stone
            if (isLastMove) {
                val markerColor = if (intersection == Intersection.BLACK)
                    BadukNextColors.LastMoveMarkerOnBlack
                else
                    BadukNextColors.LastMoveMarkerOnWhite

                drawCircle(
                    color = markerColor,
                    radius = stoneRadius * 0.22f,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}
