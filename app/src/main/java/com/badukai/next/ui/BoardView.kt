package com.badukai.next.ui

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.badukai.next.game.GoBoard
import com.badukai.next.game.Intersection
import com.badukai.next.game.Point
import kotlin.math.roundToInt

private const val MARGIN_RATIO = 0.04f
private const val MARGIN_RATIO_WITH_COORDS = 0.075f

@Composable
fun GoBoard(
    board: GoBoard,
    lastMovePoint: Point?,
    onIntersectionTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCoordinates: Boolean = false,
    pendingDot: Point? = null,
    showTerritory: Boolean = false,
    ownership: List<Float>? = null,
    animationMode: Int = 0, // 0=fade, 1=drop, 2=none
    candidateMarkers: List<Pair<Int,Int>> = emptyList(),
    candidateWinrates: List<Float> = emptyList()
) {
    val boardSize = board.size
    val density = LocalDensity.current
    val marginRatio = if (showCoordinates) MARGIN_RATIO_WITH_COORDS else MARGIN_RATIO
    val totalStones = board.getMoveCount()
    val colors = BadukNextColors

    // ── Candidate markers ──
    // Passed in from parent

    // Animation for newly placed stone
    val stoneAnimScale = remember { Animatable(1f) }
    val stoneAnimOffset = remember { Animatable(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.BoardBackground)
    ) {
        val sizePx = with(density) {
            kotlin.math.min(maxWidth.toPx(), maxHeight.toPx())
        }
        val padding = sizePx * marginRatio
        val cellSize = if (boardSize > 1) (sizePx - 2f * padding) / (boardSize - 1).toFloat() else sizePx

        // Stone animation
        LaunchedEffect(totalStones, animationMode) {
            when (animationMode) {
                0 -> { // Fade in (scale)
                    stoneAnimOffset.snapTo(0f)
                    stoneAnimScale.snapTo(0.6f)
                    stoneAnimScale.animateTo(1f, animationSpec = tween(200))
                }
                1 -> { // Drop
                    stoneAnimScale.snapTo(1f)
                    stoneAnimOffset.snapTo(-cellSize * 0.5f)
                    stoneAnimOffset.animateTo(0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                }
                2 -> { // None
                    stoneAnimScale.snapTo(1f)
                    stoneAnimOffset.snapTo(0f)
                }
            }
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

            // Territory overlay (behind stones)
            if (showTerritory && ownership != null && ownership.size >= boardSize * boardSize) {
                drawTerritory(boardSize, padding, cellSize, ownership)
            }

            drawStones(board, boardSize, padding, cellSize, lastMovePoint, stoneAnimScale.value, stoneAnimOffset.value)

            // Candidate markers (top 3 green-to-yellow circles)
            candidateMarkers.forEachIndexed { idx, (cx, cy) ->
                val wr = candidateWinrates.getOrElse(idx) { 0.5f }
                val maxWr = candidateWinrates.maxOrNull() ?: 1f
                val ratio = if (maxWr > 0f) (wr / maxWr).coerceIn(0f, 1f) else 0.5f
                val r = (1f - ratio) * 0.4f // green=0, yellow=0.4
                val g = 0.7f + ratio * 0.3f  // green 0.7->1.0
                val b = 0f
                val markerColor = Color(r, g, b, 0.45f)
                val px = padding + cx * cellSize
                val py = padding + cy * cellSize
                drawCircle(markerColor, radius = cellSize * 0.35f, center = Offset(px, py))
                drawCircle(markerColor.copy(alpha = 0.7f), radius = cellSize * 0.35f, center = Offset(px, py), style = Stroke(width = 2f))
            }

            if (showCoordinates) {
                drawCoordinates(boardSize, padding, cellSize, sizePx)
            }

            if (pendingDot != null) {
                val px = padding + pendingDot.x * cellSize
                val py = padding + pendingDot.y * cellSize
                drawCircle(
                    color = colors.Accent.copy(alpha = 0.35f),
                    radius = cellSize * 0.22f,
                    center = Offset(px, py)
                )
                drawCircle(
                    color = colors.Accent.copy(alpha = 0.7f),
                    radius = cellSize * 0.22f,
                    center = Offset(px, py),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

// ── Territory overlay ──
private fun DrawScope.drawTerritory(boardSize: Int, padding: Float, cellSize: Float, ownership: List<Float>) {
    val sqSize = cellSize * 0.3f

    for (y in 0 until boardSize) {
        for (x in 0 until boardSize) {
            val idx = y * boardSize + x
            val val_ = ownership.getOrElse(idx) { 0f }
            if (kotlin.math.abs(val_) < 0.15f) continue // unsettled

            val cx = padding + x * cellSize
            val cy = padding + y * cellSize

            if (val_ > 0.15f) {
                // Black territory — dark square
                drawRect(
                    color = BadukNextColors.BlackStone.copy(alpha = 0.35f),
                    topLeft = Offset(cx - sqSize / 2, cy - sqSize / 2),
                    size = androidx.compose.ui.geometry.Size(sqSize, sqSize)
                )
            } else if (val_ < -0.15f) {
                // White territory — light square with border
                drawRect(
                    color = BadukNextColors.WhiteStone.copy(alpha = 0.50f),
                    topLeft = Offset(cx - sqSize / 2, cy - sqSize / 2),
                    size = androidx.compose.ui.geometry.Size(sqSize, sqSize)
                )
                drawRect(
                    color = BadukNextColors.WhiteStoneBorder.copy(alpha = 0.50f),
                    topLeft = Offset(cx - sqSize / 2, cy - sqSize / 2),
                    size = androidx.compose.ui.geometry.Size(sqSize, sqSize),
                    style = Stroke(width = 0.5f)
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
        drawCircle(color = BadukNextColors.StarPoint, radius = radius, center = Offset(x, y))
    }
}

private fun getStarPoints(boardSize: Int): List<Point> {
    return when (boardSize) {
        19 -> listOf(
            Point(3,3), Point(9,3), Point(15,3),
            Point(3,9), Point(9,9), Point(15,9),
            Point(3,15), Point(9,15), Point(15,15)
        )
        13 -> listOf(
            Point(3,3), Point(6,3), Point(9,3),
            Point(3,6), Point(6,6), Point(9,6),
            Point(3,9), Point(6,9), Point(9,9)
        )
        9 -> listOf(Point(2,2), Point(4,4), Point(6,6), Point(2,6), Point(6,2))
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
        for (i in 0 until boardSize) {
            val x = padding + i * cellSize
            nc.drawText(letters[i].toString(), x, padding - textOffset + paint.textSize, paint)
            nc.drawText(letters[i].toString(), x, sizePx - padding + textOffset, paint)
        }
        for (i in 0 until boardSize) {
            val y = padding + i * cellSize
            val num = (boardSize - i).toString()
            nc.drawText(num, padding - textOffset, y + paint.textSize * 0.35f, paint)
            nc.drawText(num, sizePx - padding + textOffset, y + paint.textSize * 0.35f, paint)
        }
    }
}

private fun DrawScope.drawStones(
    board: GoBoard,
    boardSize: Int,
    padding: Float,
    cellSize: Float,
    lastMovePoint: Point?,
    animScale: Float,
    animOffset: Float = 0f
) {
    val stoneRadius = cellSize * 0.46f
    val shadowRadius = stoneRadius * 1.05f
    val shadowOffsetY = stoneRadius * 0.08f

    // Deterministic offset for each position (for realism)
    fun stoneOffset(x: Int, y: Int): Pair<Float, Float> {
        val seed = x * 31 + y * 37
        val ox = (((seed * 13 + 7) % 11) - 5) / 5f * cellSize * 0.04f
        val oy = (((seed * 17 + 11) % 11) - 5) / 5f * cellSize * 0.04f
        return Pair(ox, oy)
    }

    for (y in 0 until boardSize) {
        for (x in 0 until boardSize) {
            val intersection = board.get(x, y)
            if (intersection == Intersection.EMPTY) continue

            val (ox, oy) = stoneOffset(x, y)
            val centerX = padding + x * cellSize + ox
            val centerY = padding + y * cellSize + oy + dropOff
            val center = Offset(centerX, centerY)
            val isNewStone = lastMovePoint?.x == x && lastMovePoint.y == y
            val scale = if (isNewStone && animScale < 1f) animScale else 1f
            val dropOff = if (isNewStone) animOffset else 0f
            val r = stoneRadius * scale

            when (intersection) {
                Intersection.BLACK -> {
                    drawCircle(Color.Black.copy(alpha = 0.28f), radius = shadowRadius, center = Offset(centerX, centerY + shadowOffsetY))
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(BadukNextColors.BlackStoneHighlight, BadukNextColors.BlackStone),
                            center = Offset(centerX - stoneRadius * 0.28f, centerY - stoneRadius * 0.28f),
                            radius = stoneRadius
                        ),
                        radius = r, center = center
                    )
                }
                Intersection.WHITE -> {
                    drawCircle(Color.Black.copy(alpha = 0.22f), radius = shadowRadius, center = Offset(centerX, centerY + shadowOffsetY))
                    drawCircle(BadukNextColors.WhiteStone, radius = r, center = center)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(BadukNextColors.WhiteStoneHighlight, Color.Transparent),
                            center = Offset(centerX - stoneRadius * 0.3f, centerY - stoneRadius * 0.3f),
                            radius = stoneRadius * 0.7f
                        ),
                        radius = r * 0.7f, center = center
                    )
                    drawCircle(BadukNextColors.WhiteStoneBorder, radius = r, center = center, style = Stroke(width = 0.8f))
                }
                Intersection.EMPTY -> {}
            }

            if (isNewStone) {
                val markerColor = if (intersection == Intersection.BLACK)
                    BadukNextColors.LastMoveMarkerOnBlack else BadukNextColors.LastMoveMarkerOnWhite
                drawCircle(markerColor, radius = r * 0.22f, center = center, style = Stroke(width = 2f))
            }
        }
    }
}
