package com.badukai.next.ui

import android.graphics.Paint
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.badukai.next.analysis.AnalysisTab
import com.badukai.next.engine.KataGoEngine
import com.badukai.next.game.GameMode
import com.badukai.next.game.GameResult
import com.badukai.next.game.GameState
import com.badukai.next.game.PlacementMode
import com.badukai.next.game.StoneAnimation
import com.badukai.next.game.StoneColor

/**
 * Main game screen composable — AHQ Go inspired layout
 */
@Composable
fun GameScreen(
    state: GameState,
    onBoardTap: (Int, Int) -> Unit,
    onPass: () -> Unit,
    onResign: () -> Unit,
    onTerritoryEstimate: () -> Unit,
    onForceEndGame: () -> Unit,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
    onStartNewGame: (StoneColor, Int, Int, Float) -> Unit,
    //                      color   size  handicap komi
    onDismissNewGame: () -> Unit,
    onShowModelSelector: () -> Unit,
    onSelectModel: (KataGoEngine.Model) -> Unit,
    onDismissModelSelector: () -> Unit,
    onShowSettings: () -> Unit,
    onDismissSettings: () -> Unit,
    onToggleCoordinates: () -> Unit,
    onToggleSound: () -> Unit,
    onSetTheme: (GameTheme) -> Unit,
    onSetGameMode: (GameMode) -> Unit,
    onSetPlacementMode: (PlacementMode) -> Unit,
    onAnalysisPrev: () -> Unit,
    onAnalysisJump: (Int) -> Unit,
    onAnalysisNext: () -> Unit,
    onConfirmMove: () -> Unit,
    onCancelMove: () -> Unit,
    onSetPlaceSound: (Int) -> Unit,
    onSetAnimation: (StoneAnimation) -> Unit,
    onToggleEye: () -> Unit,
    onDismissCelebration: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.currentTheme) { BadukNextColors.setTheme(state.currentTheme) }

    val colors = BadukNextColors

    Column(
        modifier = modifier.fillMaxSize().background(colors.Background).padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top bar: mode toggle + status + settings ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.Surface,
            shadowElevation = 0.3.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                // Line 1: status text + mode toggle
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        state.gameMessage.ifEmpty { "Ready" },
                        color = colors.TextPrimary, fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        GameMode.entries.forEach { mode ->
                            val sel = mode == state.gameMode
                            Box(
                                Modifier.clip(RoundedCornerShape(5.dp))
                                    .background(if (sel) colors.Accent else Color.Transparent)
                                    .clickable { onSetGameMode(mode) }
                                    .padding(horizontal = 12.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(mode.displayName, color = if (sel) colors.TextOnAccent else colors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                // Line 2: captures + board size + settings
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: black stone + captured
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(colors.BlackStone))
                        Spacer(Modifier.width(4.dp))
                        Text("${state.capturedByBlack}", color = colors.TextSecondary, fontSize = 13.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text("${state.boardSize}\u00D7${state.boardSize}", color = colors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.capturedByWhite}", color = colors.TextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.size(12.dp).clip(CircleShape).background(colors.WhiteStone).then(Modifier.border(0.5.dp, colors.WhiteStoneBorder, CircleShape)))
                    }
                    // Settings button — text, top-right
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant)
                            .clickable(onClick = onShowSettings)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Settings", color = colors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // ── Win rate bar (larger, smooth animation) ──
        val wrTarget = if (state.winrate > 0f) state.winrate else 0.5f
        val blackWrTarget = 1f - wrTarget
        val animatedBlackWr by animateFloatAsState(
            targetValue = blackWrTarget.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 600),
            label = "winrate"
        )
        val blackWR = animatedBlackWr
        val wr = 1f - blackWR
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Box(
                Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(colors.Divider)
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(blackWR)
                        .clip(RoundedCornerShape(6.dp)).background(colors.BlackStone)
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("B ${"%.1f".format(blackWR * 100)}%", color = colors.TextSecondary, fontSize = 13.sp)
                if (state.winrate > 0f) {
                    Text(
                        "${if (state.scoreLead >= 0) "B+" else "W+"}${"%.1f".format(kotlin.math.abs(state.scoreLead))}",
                        color = colors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Text("W ${"%.1f".format(wr * 100)}%", color = colors.TextSecondary, fontSize = 13.sp)
            }
        }

        // ── Board ──
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RoundedCornerShape(4.dp), shadowElevation = 1.dp
            ) {
                GoBoard(
                    board = state.board, lastMovePoint = state.lastMovePoint,
                    onIntersectionTap = onBoardTap, enabled = state.isEngineReady && !state.isThinking,
                    showCoordinates = state.showCoordinates,
                    pendingDot = state.pendingTap,
                    showTerritory = state.showTerritoryOverlay,
                    ownership = state.ownership,
                    animationMode = state.stoneAnimation.ordinal,
                    candidateMarkers = state.topCandidatePoints,
                    candidateWinrates = state.topCandidateWinrates,
                    showEyeOverlay = state.showEyeOverlay,
                    showCandidates = state.gameMode == GameMode.ANALYZE,
                    analysisMoveIndex = state.analysisMoveIndex,
                    playedMovePoints = state.playedMovePoints,
                    moveQualities = state.moveQualities
                )
            }
        }

        // ── Territory info bar (inline, non-blocking) ──
        if (state.showTerritoryOverlay && state.territoryResult.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp), color = colors.SurfaceVariant, shadowElevation = 0.5.dp
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(state.territoryResult, color = colors.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(colors.Danger).clickable { onForceEndGame() }.padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("End", color = colors.TextOnAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Play buttons (both modes) ──
        PlayButtonRow(state, onNewGame, onPass, onResign, onTerritoryEstimate, onUndo)

        // ── Analysis sub-tabs (both modes) ──
        AnalysisFooter(state, onAnalysisPrev, onAnalysisNext, onAnalysisJump, onToggleEye)
    }

    // ── Dialogs ──
    if (state.showNewGameDialog) {
        NewGameDialog(onDismiss = onDismissNewGame, onStartGame = onStartNewGame)
    }
    if (state.showModelSelector) {
        ModelSelectorDialog(
            currentModel = state.selectedModel,
            onDismiss = onDismissModelSelector,
            onSelectModel = onSelectModel
        )
    }
    if (state.showSettings) {
        SettingsDialog(
            showCoordinates = state.showCoordinates,
            soundEnabled = state.soundEnabled,
            currentTheme = state.currentTheme,
            currentPlacementMode = state.placementMode,
            placeSoundIndex = state.placeSoundIndex,
            onDismiss = onDismissSettings,
            onToggleCoordinates = onToggleCoordinates,
            onToggleSound = onToggleSound,
            onSetTheme = onSetTheme,
            onSetPlacementMode = onSetPlacementMode,
            onSetPlaceSound = onSetPlaceSound,
            currentAnimation = state.stoneAnimation,
            onSetAnimation = onSetAnimation
        )
    }

    // ── End-game celebration overlay ──
    if (state.gameResult != null) {
        CelebrationOverlay(result = state.gameResult, onDismiss = onDismissCelebration)
    }
}

// ──────────────────────────────────────────────
// Mode Tabs
// ──────────────────────────────────────────────
// ── New Footer composables ──
@Composable
private fun PlayButtonRow(
    state: GameState, onNewGame: () -> Unit, onPass: () -> Unit, onResign: () -> Unit,
    onTerritoryEstimate: () -> Unit, onUndo: () -> Unit
) {
    val colors = BadukNextColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconBtn("\u2795", "New", onClick = onNewGame)
        IconBtn("\u21BA", "Undo", onClick = onUndo, enabled = state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() >= 2)
        IconBtn("\u23ED", "Pass", onClick = onPass, enabled = state.isPlayerTurn && !state.isThinking && state.isEngineReady)
        IconBtn("\u2691", "Resign", onClick = onResign, enabled = state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() > 0)
        IconBtn("\u25CE", "Score", onClick = onTerritoryEstimate, enabled = true)
    }
}

@Composable
private fun IconBtn(icon: String, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = BadukNextColors
    Column(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, color = if (enabled) colors.TextPrimary else colors.ButtonDisabledText, fontSize = 30.sp)
        Text(label, color = if (enabled) colors.TextSecondary else colors.ButtonDisabledText, fontSize = 12.sp)
    }
}

@Composable
private fun AnalysisFooter(state: GameState, onPrev: () -> Unit, onNext: () -> Unit, onJumpToMove: (Int) -> Unit, onToggleEye: () -> Unit) {
    val colors = BadukNextColors
    var selectedTab by remember { mutableStateOf(AnalysisTab.MOVE_TREE) }

    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev, enabled = state.analysisMoveIndex > 0, modifier = Modifier.size(32.dp)) {
            Text("\u25C0", fontSize = 16.sp, color = if (state.analysisMoveIndex > 0) colors.TextPrimary else colors.ButtonDisabledText)
        }
        Text("${state.analysisMoveIndex}/${state.analysisMoves.size}", modifier = Modifier.width(70.dp), textAlign = TextAlign.Center, color = colors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        IconButton(onClick = onNext, enabled = state.analysisMoveIndex < state.analysisMoves.size, modifier = Modifier.size(32.dp)) {
            Text("\u25B6", fontSize = 16.sp, color = if (state.analysisMoveIndex < state.analysisMoves.size) colors.TextPrimary else colors.ButtonDisabledText)
        }
        // "Eye" replay toggle — text button, only in analyze mode
        if (state.gameMode == GameMode.ANALYZE) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (state.showEyeOverlay) colors.Accent else colors.SurfaceVariant)
                    .clickable(onClick = onToggleEye)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Eye",
                    color = if (state.showEyeOverlay) colors.TextOnAccent else colors.TextPrimary,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    Spacer(Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(8.dp)).background(colors.SurfaceVariant),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AnalysisTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Box(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSelected) colors.Accent else Color.Transparent).clickable { selectedTab = tab }.padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                Text(tab.label, color = if (isSelected) colors.TextOnAccent else colors.TextSecondary, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }

    Spacer(Modifier.height(4.dp))

    Box(
        modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 10.dp).clip(RoundedCornerShape(8.dp)).background(colors.Surface)
    ) {
        when (selectedTab) {
            AnalysisTab.MOVE_TREE -> MoveTreeContent(state, onJumpToMove)
            AnalysisTab.CHART -> WinrateChartContent(state.winrateHistory, state.scoreLeadHistory, state.analysisMoveIndex)
            AnalysisTab.CANDIDATES -> CandidatesPlaceholder(state.candidateInfo)
        }
    }
}

@Composable
private fun MoveTreeContent(state: GameState, onJumpToMove: (Int) -> Unit) {
    val colors = BadukNextColors
    if (state.analysisMoves.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No moves recorded", color = colors.TextSecondary, fontSize = 11.sp)
        }
        return
    }
    val scrollState = rememberScrollState()
    // Horizontal single-row scrollable move tree, left → right, clickable
    Box(Modifier.fillMaxSize().horizontalScroll(scrollState).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            state.analysisMoves.forEachIndexed { idx, _ ->
                val moveNum = idx + 1
                val isSelected = idx == state.analysisMoveIndex - 1
                Box(
                    modifier = Modifier
                        .size(width = 26.dp, height = 34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) colors.Accent else colors.SurfaceVariant)
                        .clickable { onJumpToMove(idx + 1) }
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$moveNum", fontSize = 9.sp, color = if (isSelected) colors.TextOnAccent else colors.TextSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun ChartPlaceholder() {
    WinrateChartContent(emptyList(), emptyList())
}

@Composable
private fun WinrateChartContent(winrateHistory: List<Float>, scoreLeadHistory: List<Float>, moveIndex: Int = 0) {
    val colors = BadukNextColors
    var chartType by remember { mutableStateOf("wr") }

    if (winrateHistory.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No analysis data", color = colors.TextSecondary, fontSize = 13.sp)
        }
        return
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val pad = 24f
        val cw = w - 2 * pad; val ch = h - 2 * pad

        // Grid
        val gridColor = colors.Divider
        for (i in 0..4) {
            val y = pad + ch * (1f - i / 4f)
            drawLine(gridColor, start = Offset(pad, y), end = Offset(w - pad, y), strokeWidth = 0.5f)
        }
        drawLine(gridColor, start = Offset(pad, pad), end = Offset(pad, h - pad), strokeWidth = 1f)
        drawLine(gridColor, start = Offset(pad, h - pad), end = Offset(w - pad, h - pad), strokeWidth = 1f)

        // Axis labels (bigger)
        val labelPaint = Paint().apply {
            color = colors.TextSecondary.toArgb()
            textSize = 12f
            isAntiAlias = true
        }
        val totalMoves = if (chartType == "wr") winrateHistory.size else scoreLeadHistory.size
        // Y-axis winrate % labels (0, 25, 50, 75, 100)
        for (i in 0..4) {
            val pct = (i * 25).toString()
            val y = pad + ch * (1f - i / 4f)
            drawIntoCanvas { c ->
                c.nativeCanvas.drawText(pct, 4f, y + 3f, labelPaint)
            }
        }
        // X-axis move number labels (1, N/2, N)
        if (totalMoves > 1) {
            listOf(0, totalMoves / 2, totalMoves - 1).forEach { idx ->
                val x = pad + (idx.toFloat() / (totalMoves - 1)) * cw
                val num = (idx + 1).toString()
                drawIntoCanvas { c ->
                    c.nativeCanvas.drawText(num, x - 4f, h - 4f, labelPaint)
                }
            }
        }

        // Red vertical line at current move position
        if (totalMoves > 1) {
            val idx = moveIndex.coerceIn(0, totalMoves)
            val x = pad + (idx.toFloat() / totalMoves) * cw
            drawLine(
                color = Color(0xFFE53935),
                start = Offset(x, pad),
                end = Offset(x, h - pad),
                strokeWidth = 2f
            )
        }

        when (chartType) {
            "perf" -> {
                // Performance bars: winrate change per move (positive = good move)
                if (winrateHistory.size < 2) return@Canvas
                val changes = mutableListOf<Float>()
                for (i in 1 until winrateHistory.size) changes.add(winrateHistory[i] - winrateHistory[i - 1])
                val maxAbs = changes.maxOf { kotlin.math.abs(it) }.coerceAtLeast(0.01f)
                val barW = (cw / changes.size * 0.6f).coerceAtMost(12f)
                val midY = pad + ch / 2f
                changes.forEachIndexed { i, chg ->
                    val x = pad + i * (cw / changes.size) + (cw / changes.size - barW) / 2f
                    val barH = (ch / 2f) * (chg / maxAbs)
                    val barColor = if (chg >= 0) colors.Accent else colors.Danger
                    drawRect(barColor, topLeft = Offset(x, if (chg >= 0) midY - barH else midY), size = androidx.compose.ui.geometry.Size(barW, kotlin.math.abs(barH)))
                }
            }
            else -> {
                val data = if (chartType == "wr") winrateHistory else scoreLeadHistory
                if (data.size < 2) return@Canvas
                val minVal = data.min().coerceAtMost(if (chartType == "wr") 0f else -20f)
                val maxVal = data.max().coerceAtLeast(if (chartType == "wr") 1f else 20f)
                val range = (maxVal - minVal).coerceAtLeast(0.01f)
                val stepX = cw / (data.size - 1).coerceAtLeast(1)
                val lineColor = if (chartType == "wr") colors.Accent else colors.Danger
                for (i in 0 until data.size - 1) {
                    val x1 = pad + i * stepX
                    val y1 = pad + ch * (1f - (data[i] - minVal) / range)
                    val x2 = pad + (i + 1) * stepX
                    val y2 = pad + ch * (1f - (data[i + 1] - minVal) / range)
                    drawLine(lineColor, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = 2f)
                    drawCircle(lineColor, radius = 2f, center = Offset(x1, y1))
                    if (i == data.size - 2) drawCircle(lineColor, radius = 2f, center = Offset(x2, y2))
                }
            }
        }
    }

    // Mini tabs for chart switching
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.clip(RoundedCornerShape(4.dp)).background(colors.SurfaceVariant)) {
            listOf("wr" to "Win%", "perf" to "Perf", "sl" to "Score").forEach { (key, label) ->
                val sel = chartType == key
                Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (sel) colors.Accent else Color.Transparent).clickable { chartType = key }.padding(horizontal = 6.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = if (sel) colors.TextOnAccent else colors.TextSecondary, fontSize = 9.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun CandidatesPlaceholder(candidateInfo: List<String>) {
    val colors = BadukNextColors
    if (candidateInfo.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Top candidate moves", color = colors.TextSecondary, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            Text("No AI analysis yet", color = colors.ButtonDisabledText, fontSize = 11.sp)
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Candidate moves", color = colors.TextSecondary, fontSize = 10.sp)
        candidateInfo.forEachIndexed { i, info ->
            val isBest = i == 0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("#${i+1}", color = if (isBest) colors.Accent else colors.TextSecondary, fontSize = 10.sp, fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(16.dp))
                Text(info, color = if (isBest) colors.TextPrimary else colors.TextSecondary, fontSize = 10.sp, fontWeight = if (isBest) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}
// ──────────────────────────────────────────────
// Capture Chip
// ──────────────────────────────────────────────
@Composable
private fun CaptureChip(
    modifier: Modifier = Modifier,
    color: StoneColor,
    captured: Int,
    isCurrentPlayer: Boolean
) {
    val colors = BadukNextColors
    val bg = if (isCurrentPlayer) colors.AccentLight else colors.Surface
    val border = if (isCurrentPlayer) colors.Accent else colors.Divider

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        tonalElevation = if (isCurrentPlayer) 1.dp else 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.then(
                if (isCurrentPlayer) Modifier.border(1.dp, border, RoundedCornerShape(8.dp))
                else Modifier
            ).padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (color == StoneColor.BLACK) colors.BlackStone else colors.WhiteStone)
                    .then(
                        if (color == StoneColor.WHITE) Modifier.border(0.5.dp, colors.WhiteStoneBorder, CircleShape)
                        else Modifier
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "\u00D7$captured",
                color = colors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ──────────────────────────────────────────────
// Play Button
// ──────────────────────────────────────────────
@Composable
private fun NewGameDialog(
    onDismiss: () -> Unit,
    onStartGame: (StoneColor, Int, Int, Float) -> Unit
) {
    val colors = BadukNextColors
    var selectedColor by remember { mutableStateOf(StoneColor.BLACK) }
    var selectedSize by remember { mutableIntStateOf(19) }
    var selectedHandicap by remember { mutableIntStateOf(0) }
    var komiText by remember { mutableStateOf("7.5") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = colors.Surface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("New Game", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                Spacer(Modifier.height(14.dp))

                // AI color
                Text("AI plays", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StoneColor.entries.forEach { c ->
                        val sel = selectedColor != c // player = opposite of AI
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel != (selectedColor == StoneColor.BLACK)) colors.AccentLight else colors.SurfaceVariant)
                                .border(1.dp, if (sel != (selectedColor == StoneColor.BLACK)) colors.Accent else colors.Divider, RoundedCornerShape(8.dp))
                                .clickable { selectedColor = c.opposite() }.padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(c.displayName, color = colors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Board size slider (7-19)
                Text("Board: ${selectedSize}\u00D7${selectedSize}", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).clickable { if (selectedSize > 7) selectedSize-- }.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text("\u2212", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("$selectedSize", Modifier.width(40.dp), textAlign = TextAlign.Center, color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).clickable { if (selectedSize < 19) selectedSize++ }.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text("+", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Handicap (0-9)
                Text("Handicap: $selectedHandicap", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).clickable { if (selectedHandicap > 0) selectedHandicap-- }.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text("\u2212", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("$selectedHandicap", Modifier.width(40.dp), textAlign = TextAlign.Center, color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).clickable { if (selectedHandicap < 9) selectedHandicap++ }.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text("+", color = colors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Komi
                Text("Komi", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.width(80.dp).clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant).border(1.dp, colors.Divider, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(komiText, color = colors.TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("0.5", "6.5", "7.5").forEach { preset ->
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(if (komiText == preset) colors.AccentLight else colors.SurfaceVariant).clickable { komiText = preset }.padding(horizontal = 10.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                            Text(preset, color = colors.TextPrimary, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.Surface).border(1.dp, colors.Divider, RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text("Cancel", color = colors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.Accent).clickable {
                        onStartGame(selectedColor, selectedSize, selectedHandicap, komiText.toFloatOrNull() ?: 7.5f)
                    }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text("Start", color = colors.TextOnAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorOption(color: StoneColor, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BadukNextColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.AccentLight else colors.SurfaceVariant)
            .border(1.dp, if (selected) colors.Accent else colors.Divider, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp).shadow(1.dp, CircleShape).clip(CircleShape)
                .background(if (color == StoneColor.BLACK) colors.BlackStone else colors.WhiteStone)
                .then(if (color == StoneColor.WHITE) Modifier.border(0.5.dp, colors.WhiteStoneBorder, CircleShape) else Modifier)
        )
        Spacer(Modifier.height(4.dp))
        Text(label, color = colors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SizeOption(size: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = BadukNextColors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.Accent else colors.SurfaceVariant)
            .border(if (selected) 0.dp else 1.dp, colors.Divider, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("${size}\u00D7${size}", color = if (selected) colors.TextOnAccent else colors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ModelSelectorDialog(
    currentModel: KataGoEngine.Model,
    onDismiss: () -> Unit,
    onSelectModel: (KataGoEngine.Model) -> Unit
) {
    val colors = BadukNextColors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = colors.Surface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AI Strength", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Choose your opponent", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                KataGoEngine.Model.entries.forEach { model ->
                    ModelOption(model = model, selected = model == currentModel, onClick = { onSelectModel(model) })
                    if (model != KataGoEngine.Model.entries.last()) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ModelOption(model: KataGoEngine.Model, selected: Boolean, onClick: () -> Unit) {
    val colors = BadukNextColors
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.AccentLight else colors.SurfaceVariant)
            .border(1.dp, if (selected) colors.Accent else colors.Divider, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(18.dp).clip(CircleShape).border(if (selected) 5.dp else 1.5.dp, if (selected) colors.Accent else colors.TextSecondary, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(model.displayName, color = colors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(model.description, color = colors.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    showCoordinates: Boolean,
    soundEnabled: Boolean,
    currentTheme: GameTheme,
    currentPlacementMode: PlacementMode,
    currentAnimation: StoneAnimation,
    placeSoundIndex: Int,
    onDismiss: () -> Unit,
    onToggleCoordinates: () -> Unit,
    onToggleSound: () -> Unit,
    onSetTheme: (GameTheme) -> Unit,
    onSetPlacementMode: (PlacementMode) -> Unit,
    onSetAnimation: (StoneAnimation) -> Unit,
    onSetPlaceSound: (Int) -> Unit
) {
    val colors = BadukNextColors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = colors.Surface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                Spacer(Modifier.height(14.dp))

                // ── Sound (collapsible) ──
                CollapsibleSection(title = "Sound", defaultExpanded = false) {
                    ToggleRow("Sound On", "Stone placement and capture sounds", soundEnabled, onToggleSound)
                    Spacer(Modifier.height(8.dp))
                    Text("Place sound", fontSize = 12.sp, color = colors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    // 5 sounds in a horizontal row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (i in 0 until 5) {
                            val label = "S${i + 1}"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (i == placeSoundIndex) colors.AccentLight else colors.SurfaceVariant)
                                    .border(1.dp, if (i == placeSoundIndex) colors.Accent else colors.Divider, RoundedCornerShape(8.dp))
                                    .clickable { onSetPlaceSound(i) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (i == placeSoundIndex) colors.Accent else colors.TextPrimary, fontSize = 12.sp, fontWeight = if (i == placeSoundIndex) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Game (collapsible) ──
                CollapsibleSection(title = "Game", defaultExpanded = false) {
                    ToggleRow("Coordinates", "Show board letters and numbers", showCoordinates, onToggleCoordinates)
                    Spacer(Modifier.height(10.dp))
                    Text("Placement mode", fontSize = 12.sp, color = colors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    PlacementMode.entries.forEach { mode ->
                        SettingsRadioOption(
                            label = mode.displayName,
                            selected = mode == currentPlacementMode,
                            onClick = { onSetPlacementMode(mode) }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Stone animation", fontSize = 12.sp, color = colors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    StoneAnimation.entries.forEach { anim ->
                        SettingsRadioOption(
                            label = anim.displayName,
                            selected = anim == currentAnimation,
                            onClick = { onSetAnimation(anim) }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Theme (collapsible) ──
                CollapsibleSection(title = "Theme", defaultExpanded = false) {
                    GameTheme.entries.forEach { theme ->
                        SettingsRadioOption(
                            label = theme.displayName,
                            selected = theme == currentTheme,
                            onClick = { onSetTheme(theme) }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))
                Divider(color = colors.Divider)
                Spacer(Modifier.height(10.dp))
                Text("BadukNext v1.0", fontSize = 11.sp, color = colors.TextSecondary)
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    defaultExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = BadukNextColors
    var expanded by remember { mutableStateOf(defaultExpanded) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.TextPrimary)
            Text(
                if (expanded) "\u25B2" else "\u25BC",
                fontSize = 12.sp,
                color = colors.TextSecondary
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 4.dp), content = content)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = BadukNextColors
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked, onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedThumbColor = colors.TextOnAccent, checkedTrackColor = colors.Accent, uncheckedThumbColor = colors.Surface, uncheckedTrackColor = colors.Divider)
        )
    }
}

@Composable
private fun SettingsRadioOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BadukNextColors
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.AccentLight else colors.SurfaceVariant)
            .border(1.dp, if (selected) colors.Accent else colors.Divider, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(16.dp).clip(CircleShape).border(if (selected) 5.dp else 1.5.dp, if (selected) colors.Accent else colors.TextSecondary, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(label, color = colors.TextPrimary, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}
