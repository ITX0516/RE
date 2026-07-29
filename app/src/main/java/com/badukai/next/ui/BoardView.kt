package com.badukai.next.ui

import android.graphics.Paint
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.badukai.next.game.GoBoard
import com.badukai.next.game.Intersection
import com.badukai.next.game.Point
import kotlin.math.roundToInt

private const val MARGIN_RATIO = 0.04f
private const val MARGIN_RATIO_WITH_COORDS = 0.075f

/**
 * Go board component
 */
@Composable
fun GoBoard(
    board: GoBoard,
    lastMovePoint: Point?,
    onIntersectionTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCoordinates: Boolean = false,
    pendingDot: Point? = null
) {
    val boardSize = board.size
    val density = LocalDensity.current
    val marginRatio = if (showCoordinates) MARGIN_RATIO_WITH_COORDS else MARGIN_RATIO
    val colors = BadukNextColors

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.BoardBackground)
    ) {
        val sizePx = with(density) {
            kotlin.math.min(maxWidth.toPx(), maxHeight.toPx())
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, boardSize, sizePx, marginRatio) {
                    if (!enabled) return@pointerInput
                    val padding = sizePx * marginRatio
                    val cs = if (boardSize > 1) (sizePx - 2f * padding) / (boardSize - 1).toFloat() else sizePx
                    detectTapGestures { offset ->
                        val x = ((offset.x - padding) / cs).roundToInt().coerceIn(0, boardSize - 1)
                        val y = ((offset.y - padding) / cs).roundToInt().coerceIn(0, boardSize - 1)
                        onIntersectionTap(x, y)
                    }
                }
        ) {
            val padding = sizePx * marginRatio
            val cellSize = if (boardSize > 1) (sizePx - 2f * padding) / (boardSize - 1).toFloat() else sizePx

            drawGrid(boardSize, padding, cellSize)
            drawStarPoints(boardSize, padding, cellSize)
            drawStones(board, boardSize, padding, cellSize, lastMovePoint)

            if (showCoordinates) {
                drawCoordinates(boardSize, padding, cellSize, sizePx)
            }

            // Draw pending placement dot (for confirm/double-tap modes)
            if (pendingDot != null) {
                val px = padding + pendingDot.x * cellSize
                val py = padding + pendingDot.y * cellSize
                drawCircle(
                    color = BadukNextColors.Accent.copy(alpha = 0.35f),
                    radius = cellSize * 0.22f,
                    center = Offset(px, py)
                )
                drawCircle(
                    color = BadukNextColors.Accent.copy(alpha = 0.7f),
                    radius = cellSize * 0.22f,
                    center = Offset(px, py),
                    style = Stroke(width = 2f)
                )
            }
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

        drawLine(
            color = BadukNextColors.BoardLine,
            start = Offset(pos, padding),
            end = Offset(pos, padding + (boardSize - 1) * cellSize),
            strokeWidth = w
        )

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

private fun DrawScope.drawCoordinates(
    boardSize: Int,
    padding: Float,
    cellSize: Float,
    sizePx: Float
) {
    val coordColor = BadukNextColors.CoordinateText
    val argb = coordColor.toArgb()

    val paint = Paint().apply {
        color = argb
        textSize = (cellSize * 0.33f).coerceIn(12f, 32f)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    val letters = "ABCDEFGHJKLMNOPQRST"
    val textOffset = cellSize * 0.5f + paint.textSize * 0.35f

    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas

        // Top letters
        for (i in 0 until boardSize) {
            val x = padding + i * cellSize
            nc.drawText(letters[i].toString(), x, padding - textOffset + paint.textSize, paint)
        }

        // Bottom letters
        for (i in 0 until boardSize) {
            val x = padding + i * cellSize
            nc.drawText(letters[i].toString(), x, sizePx - padding + textOffset, paint)
        }

        // Left numbers (boardSize at top, 1 at bottom)
        for (i in 0 until boardSize) {
            val y = padding + i * cellSize
            val num = (boardSize - i).toString()
            nc.drawText(num, padding - textOffset, y + paint.textSize * 0.35f, paint)
        }

        // Right numbers
        for (i in 0 until boardSize) {
            val y = padding + i * cellSize
            val num = (boardSize - i).toString()
            nc.drawText(num, sizePx - padding + textOffset, y + paint.textSize * 0.35f, paint)
        }
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
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.28f),
                        radius = shadowRadius,
                        center = Offset(centerX, centerY + shadowOffsetY)
                    )
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
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.22f),
                        radius = shadowRadius,
                        center = Offset(centerX, centerY + shadowOffsetY)
                    )
                    drawCircle(
                        color = BadukNextColors.WhiteStone,
                        radius = stoneRadius,
                        center = center
                    )
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
                    drawCircle(
                        color = BadukNextColors.WhiteStoneBorder,
                        radius = stoneRadius,
                        center = center,
                        style = Stroke(width = 0.8f)
                    )
                }
                Intersection.EMPTY -> {}
            }

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
