package com.badukai.next.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Paint
import com.badukai.next.analysis.AnalysisTab
import com.badukai.next.game.GameMode
import com.badukai.next.game.GameState

// ══════════════════════════════════════════════════════════════════════════════
// AnalysisFooter — 还原原版 74025d6：
// 顶行：◀ 手数/总数 ▶ + 3 Tab + (分析模式下Eye按钮)
// 面板：100dp 高，3Tab内容（落子树横条/走势图/选点表）
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun AnalysisFooter(
    state: GameState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJumpToMove: (Int) -> Unit,
    onToggleEye: () -> Unit,
    // 保留原来 AF 所有对外签名（兼容 GameScreen 调用），后面这些不用
    onPass: () -> Unit = {},
    onHint: () -> Unit = {},
    onUndo: () -> Unit = {},
    onResign: () -> Unit = {},
    onTerritoryEstimate: () -> Unit = {},
    onNewGame: () -> Unit = {},
    onShowSettings: () -> Unit = {}
) {
    val colors = LocalThemeColors.current
    var selectedTab by remember { mutableStateOf(AnalysisTab.MOVE_TREE) }

    // ═══ Combined nav + tabs (原版单一玻璃卡片，省空间) ═══
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .glassSurface(
                shape = RoundedCornerShape(16.dp),
                intensity = GlassIntensity.CARD,
                addShadow = false
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Navigation controls
        IconButton(
            onClick = onPrev,
            enabled = state.analysisMoveIndex > 0,
            modifier = Modifier.size(32.dp)
        ) {
            Text(
                "\u25C0",
                fontSize = 15.sp,
                color = if (state.analysisMoveIndex > 0) colors.TextPrimary else colors.ButtonDisabledText
            )
        }
        Text(
            "${state.analysisMoveIndex + 1}/${state.analysisMoves.size}",
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center,
            color = colors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        IconButton(
            onClick = onNext,
            enabled = state.analysisMoveIndex < state.analysisMoves.size,
            modifier = Modifier.size(32.dp)
        ) {
            Text(
                "\u25B6",
                fontSize = 15.sp,
                color = if (state.analysisMoveIndex < state.analysisMoves.size) colors.TextPrimary else colors.ButtonDisabledText
            )
        }

        // Divider between nav and tabs
        Box(
            Modifier
                .height(24.dp)
                .width(1.dp)
                .padding(horizontal = 4.dp)
                .background(colors.Divider.copy(alpha = 0.5f))
        )

        // Tab selector (3 Tab: 落子树/走势图/选点表)
        Row(
            modifier = Modifier
                .weight(1f)
                .glassSurface(
                    shape = RoundedCornerShape(12.dp),
                    intensity = GlassIntensity.THIN,
                    addShadow = false
                )
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AnalysisTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                Box(
                    Modifier
                        .weight(1f)
                        .glassSurface(
                            shape = RoundedCornerShape(10.dp),
                            intensity = if (isSelected) GlassIntensity.STRONG else GlassIntensity.THIN,
                            accentRim = isSelected,
                            addShadow = false
                        )
                        .background(if (isSelected) colors.Accent.copy(alpha = 0.9f) else Color.Transparent)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedTab = tab }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab.label,
                        color = if (isSelected) colors.TextOnAccent else colors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        // Eye toggle button (仅ANALYZE模式显示，原版逻辑)
        if (state.gameMode == GameMode.ANALYZE) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .glassSurface(
                        shape = RoundedCornerShape(10.dp),
                        intensity = GlassIntensity.CARD,
                        accentRim = state.showEyeOverlay,
                        addShadow = false
                    )
                    .background(if (state.showEyeOverlay) colors.Accent.copy(alpha = 0.9f) else Color.Transparent)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleEye)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Eye",
                    color = if (state.showEyeOverlay) colors.TextOnAccent else colors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    Spacer(Modifier.height(6.dp))

    // ═══ Chart / move-tree panel: glassed container 100dp ═══
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 10.dp)
            .glassSurface(
                shape = RoundedCornerShape(16.dp),
                intensity = GlassIntensity.CARD,
                addShadow = false
            )
    ) {
        when (selectedTab) {
            AnalysisTab.MOVE_TREE -> MoveTreeContent(state, onJumpToMove)
            AnalysisTab.CHART -> WinrateChartContent(
                state.winrateHistory, state.scoreLeadHistory,
                state.analysisMoveIndex, state.analysisMoves.size
            )
            AnalysisTab.CANDIDATES -> CandidatesPlaceholder(state.candidateInfo)
            else -> {}
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 落子树：原版水平移动小方块（28×36dp）
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun MoveTreeContent(state: GameState, onJumpToMove: (Int) -> Unit) {
    val colors = LocalThemeColors.current
    if (state.analysisMoves.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No moves recorded", color = colors.TextSecondary, fontSize = 12.sp)
        }
        return
    }
    val scrollState = rememberScrollState()
    Box(
        Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            state.analysisMoves.forEachIndexed { idx, _ ->
                val moveNum = idx + 1
                val isSelected = idx == state.analysisMoveIndex - 1
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 36.dp)
                        .glassSurface(
                            shape = RoundedCornerShape(8.dp),
                            intensity = if (isSelected) GlassIntensity.STRONG else GlassIntensity.THIN,
                            accentRim = isSelected,
                            addShadow = false
                        )
                        .background(if (isSelected) colors.Accent.copy(alpha = 0.92f) else Color.Transparent)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onJumpToMove(idx + 1) }
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$moveNum",
                        fontSize = 10.sp,
                        color = if (isSelected) colors.TextOnAccent else colors.TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 走势图：还原原版 Win% / Perf / Score 三个 Tab（不搞双方/黑棋/白棋/胜率/目差那堆）
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun WinrateChartContent(
    winrateHistory: List<Float>,
    scoreLeadHistory: List<Float>,
    moveIndex: Int = 0,
    totalMovesInGame: Int = 0
) {
    val colors = LocalThemeColors.current
    var chartType by remember { mutableStateOf("wr") }

    if (winrateHistory.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No analysis data", color = colors.TextSecondary, fontSize = 13.sp)
        }
        return
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 24f
        val cw = w - 2 * pad
        val ch = h - 2 * pad

        val gridColor = colors.Divider
        for (i in 0..4) {
            val y = pad + ch * (1f - i / 4f)
            drawLine(
                gridColor,
                start = Offset(pad, y),
                end = Offset(w - pad, y),
                strokeWidth = 0.5f
            )
        }
        drawLine(gridColor, start = Offset(pad, pad), end = Offset(pad, h - pad), strokeWidth = 1f)
        drawLine(gridColor, start = Offset(pad, h - pad), end = Offset(w - pad, h - pad), strokeWidth = 1f)

        val labelPaint = Paint().apply {
            color = colors.TextSecondary.toArgb()
            textSize = 12f
            isAntiAlias = true
        }
        val totalMoves = if (totalMovesInGame > 0) totalMovesInGame
        else if (chartType == "wr") winrateHistory.size
        else scoreLeadHistory.size
        for (i in 0..4) {
            val pct = (i * 25).toString()
            val y = pad + ch * (1f - i / 4f)
            drawIntoCanvas { c ->
                c.nativeCanvas.drawText(pct, 4f, y + 3f, labelPaint)
            }
        }
        if (totalMoves > 1) {
            listOf(0, totalMoves / 2, totalMoves - 1).forEach { idx ->
                val x = pad + (idx.toFloat() / (totalMoves - 1)) * cw
                val num = (idx + 1).toString()
                drawIntoCanvas { c ->
                    c.nativeCanvas.drawText(num, x - 4f, h - 4f, labelPaint)
                }
            }
        }

        if (totalMoves > 1) {
            val idx = moveIndex.coerceIn(0, totalMoves)
            val x = pad + (idx.toFloat() / totalMoves) * cw
            drawLine(
                Color(0xFFE53935),
                start = Offset(x, pad),
                end = Offset(x, h - pad),
                strokeWidth = 2f
            )
        }

        when (chartType) {
            "perf" -> {
                if (winrateHistory.size < 2) return@Canvas
                val changes = mutableListOf<Float>()
                for (i in 1 until winrateHistory.size) {
                    changes.add(winrateHistory[i] - winrateHistory[i - 1])
                }
                val maxAbs = changes.maxOf { kotlin.math.abs(it) }.coerceAtLeast(0.01f)
                val barW = (cw / changes.size * 0.6f).coerceAtMost(12f)
                val midY = pad + ch / 2f
                changes.forEachIndexed { i, chg ->
                    val x = pad + i * (cw / changes.size) + (cw / changes.size - barW) / 2f
                    val barH = (ch / 2f) * (chg / maxAbs)
                    val barColor = if (chg >= 0) colors.Accent else colors.Danger
                    drawRect(
                        barColor,
                        topLeft = Offset(x, if (chg >= 0) midY - barH else midY),
                        size = androidx.compose.ui.geometry.Size(barW, kotlin.math.abs(barH))
                    )
                }
            }
            else -> {
                val data = if (chartType == "wr") winrateHistory else scoreLeadHistory
                if (data.size < 2) return@Canvas
                val minVal = data.min().coerceAtMost(if (chartType == "wr") 0f else -20f)
                val maxVal = data.max().coerceAtLeast(if (chartType == "wr") 1f else 20f)
                val range = (maxVal - minVal).coerceAtLeast(0.01f)
                val xStep = cw / (totalMoves - 1).coerceAtLeast(1)
                val lineColor = if (chartType == "wr") colors.Accent else colors.Danger
                for (i in 0 until data.size - 1) {
                    val x1 = pad + i * xStep
                    val y1 = pad + ch * (1f - (data[i] - minVal) / range)
                    val x2 = pad + (i + 1) * xStep
                    val y2 = pad + ch * (1f - (data[i + 1] - minVal) / range)
                    drawLine(lineColor, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = 2f)
                    drawCircle(lineColor, radius = 2f, center = Offset(x1, y1))
                    if (i == data.size - 2) drawCircle(lineColor, radius = 2f, center = Offset(x2, y2))
                }
            }
        }
    }

    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .glassSurface(
                    shape = RoundedCornerShape(8.dp),
                    intensity = GlassIntensity.THIN,
                    addShadow = false
                )
                .padding(2.dp)
        ) {
            listOf("wr" to "Win%", "perf" to "Perf", "sl" to "Score").forEach { (key, label) ->
                val sel = chartType == key
                Box(
                    Modifier
                        .glassSurface(
                            shape = RoundedCornerShape(6.dp),
                            intensity = if (sel) GlassIntensity.STRONG else GlassIntensity.THIN,
                            accentRim = sel,
                            addShadow = false
                        )
                        .background(if (sel) colors.Accent.copy(alpha = 0.92f) else Color.Transparent)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { chartType = key }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (sel) colors.TextOnAccent else colors.TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 选点表：原版 CandidateInfo（不是我造的6列假表格）
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun CandidatesPlaceholder(candidateInfo: List<String>) {
    val colors = LocalThemeColors.current
    if (candidateInfo.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Top candidate moves", color = colors.TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text("No AI analysis yet", color = colors.ButtonDisabledText, fontSize = 12.sp)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("Candidate moves", color = colors.TextSecondary, fontSize = 11.sp)
        candidateInfo.forEachIndexed { i, info ->
            val isBest = i == 0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${i + 1}",
                    color = if (isBest) colors.Accent else colors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.width(18.dp)
                )
                Text(
                    info,
                    color = if (isBest) colors.TextPrimary else colors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isBest) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}
