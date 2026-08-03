package com.badukai.next.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Paint
import com.badukai.next.analysis.AnalysisTab
import com.badukai.next.game.GameState
import com.badukai.next.game.Move

// ══════════════════════════════════════════════════════════════════════════════
// AnalysisFooter — 3 Tab + 面板 + 底部1行（7个汉字按钮）
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun AnalysisFooter(
    state: GameState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJumpToMove: (Int) -> Unit,
    onToggleEye: () -> Unit,
    onPass: () -> Unit,
    onHint: () -> Unit,
    onUndo: () -> Unit,
    onResign: () -> Unit,
    onTerritoryEstimate: () -> Unit,
    onNewGame: () -> Unit,
    onShowSettings: () -> Unit
) {
    val colors = LocalThemeColors.current
    var selectedTab by remember { mutableStateOf(AnalysisTab.MOVE_TREE) }
    var wrSide by remember { mutableIntStateOf(0) }   // 0双方 1黑 2白
    var wrAxis by remember { mutableIntStateOf(0) }   // 0胜率 1目差

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
    ) {
        // ─── Tab 行 + 快捷菜单 ───
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp)
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                AnalysisTab.entries.forEach { tab ->
                    val label = when (tab) {
                        AnalysisTab.MOVE_TREE -> "落子树"
                        AnalysisTab.CHART -> "走势图"
                        AnalysisTab.CANDIDATES -> "选点表"
                        else -> tab.label
                    }
                    val isSel = tab == selectedTab
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                label,
                                color = if (isSel) colors.TextPrimary else colors.TextSecondary,
                                fontSize = 18.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isSel) {
                                val color = when (tab) {
                                    AnalysisTab.MOVE_TREE -> Color(0xFFFFD24A)
                                    AnalysisTab.CHART -> Color(0xFFB99057)
                                    AnalysisTab.CANDIDATES -> colors.AccentLight
                                    else -> colors.Divider
                                }
                                Box(
                                    Modifier
                                        .width(36.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(color)
                                )
                            }
                        }
                    }
                }
                // 三个小圆点分隔
                Row(
                    Modifier.padding(start = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(3) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(colors.TextSecondary.copy(alpha = 0.45f))
                        )
                    }
                }
            }
            // 快捷菜单
            Box(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.GlassFill.copy(alpha = 0.55f))
                    .border(0.5.dp, colors.GlassEdge.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onShowSettings)
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "快捷菜单",
                    color = colors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ─── 面板 220dp ───
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.GlassFillStrong)
                .border(0.6.dp, colors.GlassEdge.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .padding(6.dp)
        ) {
            when (selectedTab) {
                // =====================================================
                // 落子树 — 直接用 state.analysisMoves 显示真实记录
                // =====================================================
                AnalysisTab.MOVE_TREE -> MoveTreePanel(state = state, onJump = onJumpToMove)

                // =====================================================
                // 走势图 2×2 Tab
                // =====================================================
                AnalysisTab.CHART -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val sides = listOf("双方", "黑棋", "白棋")
                            val axes = listOf("胜率", "目差")
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.GlassFill.copy(alpha = 0.35f))
                                    .padding(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                sides.forEachIndexed { i, t ->
                                    val sel = wrSide == i
                                    Box(
                                        Modifier
                                            .then(if (sel) Modifier.background(
                                                colors.GlassFillStrong,
                                                RoundedCornerShape(8.dp)
                                            ) else Modifier)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { wrSide = i }
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            t,
                                            color = if (sel) colors.TextPrimary else colors.TextSecondary,
                                            fontSize = 15.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.GlassFill.copy(alpha = 0.35f))
                                    .padding(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                axes.forEachIndexed { i, t ->
                                    val sel = wrAxis == i
                                    Box(
                                        Modifier
                                            .then(if (sel) Modifier.background(
                                                colors.GlassFillStrong,
                                                RoundedCornerShape(8.dp)
                                            ) else Modifier)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { wrAxis = i }
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            t,
                                            color = if (sel) colors.TextPrimary else colors.TextSecondary,
                                            fontSize = 15.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF8A8D91), Color(0xFFA9ACB0))
                                    )
                                )
                        ) {
                            WinRateCanvas(
                                state = state,
                                modifier = Modifier.fillMaxSize(),
                                sideSel = wrSide,
                                axisSel = wrAxis
                            )
                        }
                    }
                }

                // =====================================================
                // 选点表 4列：序号 / 坐标 / 胜率 / 目差（我们没有计算量/复杂度，就不造假）
                // =====================================================
                AnalysisTab.CANDIDATES -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 表头
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val headers = listOf("序号" to 0.8f, "坐标" to 1.2f,
                                "胜率" to 1.2f, "目差" to 1.0f)
                            headers.forEach { (t, w) ->
                                Box(
                                    Modifier.fillMaxWidth().weight(w),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        t,
                                        color = colors.TextSecondary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        androidx.compose.foundation.layout.HorizontalDivider(
                            color = colors.Divider.copy(alpha = 0.5f),
                            thickness = 0.6.dp
                        )
                        val data = state.topCandidatePoints.zip(
                            state.topCandidateWinrates.ifEmpty {
                                List(state.topCandidatePoints.size) { 0.5f }
                            }
                        )
                        val leadHistory = state.scoreLeadHistory
                        if (data.isEmpty()) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "暂无候选点",
                                    color = colors.TextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            data.take(10).forEachIndexed { i, (pt, wr) ->
                                val bg = if (i % 2 == 0) colors.GlassFill else Color.Transparent
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(bg)
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val (x, y) = pt
                                    val size = state.board.size
                                    val coord = runCatching {
                                        val letter = "ABCDEFGHJKLMNOPQRST"[x]
                                        "${letter}${size - y}"
                                    }.getOrElse { "${x},${y}" }
                                    val winStr = "%.2f%%".format(wr * 100f)
                                    val lead = leadHistory.getOrElse(i) { state.scoreLead }
                                    val leadStr = if (lead >= 0f) "+%.2f".format(lead)
                                    else "%.2f".format(lead)
                                    val cells = listOf(
                                        Triple(0.8f, "${i + 1}", FontWeight.SemiBold),
                                        Triple(1.2f, coord, FontWeight.SemiBold),
                                        Triple(1.2f, winStr, FontWeight.SemiBold),
                                        Triple(1.0f, leadStr, FontWeight.SemiBold)
                                    )
                                    cells.forEach { (w, t, fw) ->
                                        Box(
                                            Modifier.fillMaxWidth().weight(w),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                t,
                                                color = colors.TextPrimary,
                                                fontSize = 17.sp,
                                                fontWeight = fw,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> Box(Modifier.fillMaxSize())
            }
        }

        // ─── 底部3个小圆点分隔符 ───
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) { i ->
                Box(
                    Modifier.size(5.dp).clip(CircleShape)
                        .background(colors.TextSecondary.copy(alpha = if (i == 1) 0.75f else 0.45f))
                )
                if (i < 2) Spacer(Modifier.width(5.dp))
            }
        }

        // ════════════════════════════════════════════════════════════
        // 底部 1行 7个汉字按钮（和原版数量/位置一致，全部汉字不用图标）
        // ════════════════════════════════════════════════════════════
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .height(54.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolbarText("变", onClick = onNewGame)            // 1. 变着/新分支
            ToolbarText("剪", onClick = onResign)            // 2. 剪分支/认输
            ToolbarText("退", onClick = onUndo)              // 3. 退一步
            ToolbarText("进", onClick = onNext, primary = true)  // 4. 进一步（主色强调）
            ToolbarText("算", onClick = onTerritoryEstimate)  // 5. 数子/形势
            AiSwitchText(state = state, onClick = onNewGame)  // 6. AI 开关
            ToolbarText("记", onClick = onShowSettings)       // 7. 记录/备注
        }
    }
}

// ================================================================
// 落子树面板：真实读取 state.analysisMoves / playedMovePoints
// ================================================================
@Composable
private fun MoveTreePanel(state: GameState, onJump: (Int) -> Unit) {
    val colors = LocalThemeColors.current
    val moves = state.analysisMoves
    val scroll = rememberScrollState()
    val letters = "ABCDEFGHJKLMNOPQRST"
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (moves.isEmpty()) {
            // analysisMoves 为空就退回用 playedMovePoints + history 拼接显示
            val points = state.playedMovePoints
            if (points.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无落子记录",
                        color = colors.TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                points.forEachIndexed { idx, (x, y) ->
                    val size = state.board.size
                    val letter = runCatching { letters[x] }.getOrElse { '?' }
                    val num = size - y
                    val color = if (idx % 2 == 0) StoneColor.BLACK else StoneColor.WHITE
                    val coordStr = "$letter$num"
                    val sel = state.analysisMoveIndex == (idx + 1)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (sel) colors.AccentLight.copy(alpha = 0.45f)
                                else if (idx % 2 == 0) colors.GlassFill else Color.Transparent
                            )
                            .clickable { onJump(idx + 1) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 手数序号
                        Box(
                            Modifier.width(40.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                "${idx + 1}.",
                                color = colors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        // 方/子
                        Text(
                            if (color == StoneColor.BLACK) "● 黑" else "○ 白",
                            color = colors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        // 坐标
                        Text(
                            coordStr,
                            color = colors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        } else {
            moves.forEachIndexed { idx, rec ->
                val mv = rec.move
                val (x, y) = if (mv is Move.Stone) {
                    (mv.point.x) to (mv.point.y)
                } else (-1 to -1)
                val size = state.board.size
                val coord = if (x >= 0 && y >= 0) {
                    runCatching {
                        val letter = letters[x]
                        "$letter${size - y}"
                    }.getOrElse { "—" }
                } else {
                    if (mv is Move.Pass) "停一手"
                    else if (mv is Move.Resign) "认输" else "—"
                }
                val color = if (idx % 2 == 0) StoneColor.BLACK else StoneColor.WHITE
                val sel = state.analysisMoveIndex == (idx + 1)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (sel) colors.AccentLight.copy(alpha = 0.45f)
                            else if (idx % 2 == 0) colors.GlassFill else Color.Transparent
                        )
                        .clickable { onJump(idx + 1) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            "${idx + 1}.",
                            color = colors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        if (color == StoneColor.BLACK) "● 黑" else "○ 白",
                        color = colors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        coord,
                        color = colors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    // 胜率（如果有）
                    val w = rec.winrate
                    if (w != null && w > 0f) {
                        Text(
                            "%.1f%%".format(w * 100f),
                            color = colors.Accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// ================================================================
// 底部汉字按钮（1字/2字）— 一律不画图标，就汉字
// ================================================================
@Composable
private fun ToolbarText(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    val colors = LocalThemeColors.current
    Box(
        Modifier
            .size(width = 48.dp, height = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                when {
                    !enabled -> Modifier.background(colors.GlassFill.copy(alpha = 0.15f))
                    primary -> Modifier.background(
                        brush = Brush.verticalGradient(
                            listOf(colors.Accent, colors.AccentVariant)
                        ),
                        RoundedCornerShape(12.dp)
                    )
                    else -> Modifier
                        .background(colors.GlassFill.copy(alpha = 0.3f))
                        .border(
                            0.5.dp,
                            colors.GlassEdge.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                }
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                !enabled -> colors.ButtonDisabledText
                primary -> colors.TextOnAccent
                else -> colors.TextPrimary
            },
            fontSize = 22.sp,
            fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.SemiBold
        )
    }
}

// ================================================================
// AI 开关 — 文字 "AI" + 标准 Switch
// ================================================================
@Composable
private fun AiSwitchText(state: GameState, onClick: () -> Unit) {
    val colors = LocalThemeColors.current
    val on = state.isEngineReady
    Row(
        Modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.GlassFill.copy(alpha = 0.35f))
            .border(0.5.dp, colors.GlassEdge.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            "AI",
            color = colors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Switch(
            checked = on,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.Accent,
                checkedTrackColor = colors.Accent.copy(alpha = 0.45f),
                uncheckedThumbColor = colors.TextSecondary,
                uncheckedTrackColor = colors.GlassEdge.copy(alpha = 0.5f)
            )
        )
    }
}

// ================================================================
// 走势图 Canvas
//   - Y轴：左/右 双100-0数字，大号黑粗体
//   - X轴：底 0,1,2,...N 手数 大字
//   - 折线每个点 下标胜率数字
//   - currentMoveIndex 位置画红色垂直分割线
// ================================================================
@Composable
private fun WinRateCanvas(
    state: GameState,
    modifier: Modifier = Modifier,
    sideSel: Int = 0,
    axisSel: Int = 0
) {
    val colors = LocalThemeColors.current
    val rawPoints: List<Float> = state.winrateHistory
    val wPoints = rawPoints.map { v -> if (v <= 0f) Float.NaN else v }

    val showWinrate = axisSel == 0
    val points: List<Float> = if (showWinrate) {
        wPoints
    } else {
        wPoints.map { w ->
            if (w.isNaN()) Float.NaN else (w - 0.5f) * 24f
        }
    }
    val yLo: Float; val yHi: Float
    if (showWinrate) { yLo = 0.0f; yHi = 1.0f } else { yLo = -12f; yHi = 12f }

    val colorBlack = colors.BlackStone
    val colorWhite = Color.White
    val divider = Color.White.copy(alpha = 0.7f)
    val textBlack = Color.Black

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val leftPad = 48.dp.toPx()
        val rightPad = 48.dp.toPx()
        val botPad = 36.dp.toPx()
        val topPad = 6.dp.toPx()
        val chartW = (w - leftPad - rightPad).coerceAtLeast(1f)
        val chartH = (h - topPad - botPad).coerceAtLeast(1f)
        val yRange = (yHi - yLo).coerceAtLeast(0.0001f)

        val yLabelPaint = Paint().apply {
            textSize = 22.sp.toPx()
            color = textBlack.toArgb()
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        val yLabelRPaint = Paint(yLabelPaint).apply { textAlign = Paint.Align.LEFT }
        val xLabelPaint = Paint(yLabelPaint).apply {
            textSize = 18.sp.toPx()
            textAlign = Paint.Align.CENTER
        }
        val valuePaint = Paint(yLabelPaint).apply {
            textSize = 13.sp.toPx()
            color = textBlack.toArgb()
            textAlign = Paint.Align.CENTER
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

        // 绘制 Y 轴横线（每 10% 一格，共0–100，11条）+ Y轴数字
        for (i in 0..10) {
            val pct = i * 10
            val fracVal = if (showWinrate) (pct / 100f) else {
                // 目差模式：对应纵轴 -12, -9, -6 … 12
                val step = -12f + i * (24f / 10f)
                step
            }
            val yFrac: Float = (yHi - fracVal) / yRange   // 上大下小
            val y = topPad + chartH * yFrac.coerceIn(0f, 1f)

            // 虚线
            drawLine(
                start = Offset(leftPad, y),
                end = Offset(w - rightPad, y),
                color = divider,
                strokeWidth = 0.8.dp.toPx(),
                pathEffect = dash
            )
            // Y 轴数字（左右都画）
            if (showWinrate) {
                drawIntoCanvas { c ->
                    yLabelPaint.textAlign = Paint.Align.RIGHT
                    c.nativeCanvas.drawText(
                        "$pct",
                        leftPad - 6.dp.toPx(),
                        y + 6.dp.toPx(),
                        yLabelPaint
                    )
                    c.nativeCanvas.drawText(
                        "$pct",
                        w - rightPad + 6.dp.toPx(),
                        y + 6.dp.toPx(),
                        yLabelRPaint
                    )
                }
            }
        }
        // X 轴底线 + Y轴左线
        drawLine(
            start = Offset(leftPad, h - botPad),
            end = Offset(w - rightPad, h - botPad),
            color = divider, strokeWidth = 1.dp.toPx()
        )
        drawLine(
            start = Offset(leftPad, topPad),
            end = Offset(leftPad, h - botPad),
            color = divider, strokeWidth = 1.dp.toPx()
        )

        // X 轴数字：0,1,2,... max(points.size, 10)
        val totalN = points.size.coerceAtLeast(2)
        val xTicksN = totalN.coerceAtMost(11)
        for (i in 0 until xTicksN) {
            val xFrac = i.toFloat() / (xTicksN - 1)
            val x = leftPad + chartW * xFrac
            val idx = (xFrac * (totalN - 1)).toInt().coerceIn(0, totalN - 1)
            // 底部 X 数字（手数 idx）
            drawIntoCanvas { c ->
                c.nativeCanvas.drawText(
                    "$idx",
                    x,
                    h - 6.dp.toPx(),
                    xLabelPaint
                )
            }
        }

        // 红色分割线：currentMoveIndex 位置
        val cur = (state.analysisMoveIndex - 1).coerceAtLeast(0)
        if (points.isNotEmpty() && cur < points.size) {
            val xF = (cur.toFloat() + 0.5f) / points.size
            val x = leftPad + chartW * xF.coerceIn(0f, 1f)
            drawLine(
                start = Offset(x, topPad),
                end = Offset(x, h - botPad),
                color = Color(0xFFE23B3B),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
            )
        }

        // 画折线 + 每个点下面标数值
        val countVal: Int = points.size
        if (countVal >= 1) {
            var lastX = -1f
            var lastY = -1f
            for (i in 0 until countVal) {
                val xFrac = (i.toFloat() + 0.5f) / countVal.toFloat()
                val x = leftPad + chartW * xFrac
                val v = points[i]
                if (!v.isNaN()) {
                    val clamped = v.coerceIn(yLo, yHi)
                    val yFrac = (yHi - clamped) / yRange
                    val y = topPad + chartH * yFrac.coerceIn(0f, 1f)

                    if (lastX >= 0f) {
                        val prevV = points[i - 1]
                        val mid = if (!prevV.isNaN()) (clamped + prevV) * 0.5f else clamped
                        val threshold = if (showWinrate) 0.5f else 0f
                        val lineColor = when (sideSel) {
                            1 -> colorBlack
                            2 -> colorWhite
                            else -> if (mid >= threshold) colorBlack else colorWhite
                        }
                        drawLine(
                            start = Offset(lastX, lastY),
                            end = Offset(x, y),
                            color = lineColor,
                            strokeWidth = 2.4.dp.toPx()
                        )
                    }
                    // 点下面标数值（隔2-3个标1个避免太密）
                    if (countVal <= 20 || i % 3 == 0 || i == countVal - 1) {
                        val disp = if (showWinrate) {
                            "%.2f".format(clamped * 100f)
                        } else {
                            if (clamped >= 0) "+%.2f".format(clamped)
                            else "%.2f".format(clamped)
                        }
                        drawIntoCanvas { c ->
                            c.nativeCanvas.drawText(
                                disp,
                                x,
                                (y + 14.dp.toPx()).coerceAtMost(h - botPad - 2.dp.toPx()),
                                valuePaint
                            )
                        }
                    }
                    lastX = x
                    lastY = y
                } else {
                    lastX = -1f
                    lastY = -1f
                }
            }
        }
    }
}
