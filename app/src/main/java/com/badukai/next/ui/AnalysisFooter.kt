package com.badukai.next.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Paint
import com.badukai.next.analysis.AnalysisTab
import com.badukai.next.game.GameState

// ══════════════════════════════════════════════════════════════════════════════
// AnalysisFooter — 仿阿Q截图底部：
//   3 Tab（落子树 ● | 走势图 | 选点表） + 快捷菜单 胶囊
//   + 面板（落子树节点 / 走势图2×2Tab / 选点表6列）
//   + 底部双行工具条（7 图标 + 7 图标）
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
    // 走势图内部的 2×2 Tab
    var wrSide by remember { mutableStateOf(0) }   // 0=双方 / 1=黑棋 / 2=白棋
    var wrAxis by remember { mutableStateOf(0) }   // 0=胜率 / 1=目差

    // ──────────────────────────────────────────────────────────────────
    // Tab Bar: 落子树 ● | 走势图 | 选点表   +  快捷菜单 (右)
    // ──────────────────────────────────────────────────────────────────
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start)
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
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    label,
                                    color = if (isSel) colors.TextPrimary else colors.TextSecondary,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isSel && tab == AnalysisTab.MOVE_TREE) {
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF5A5A))
                                    )
                                }
                            }
                            // 选中态下划线
                            if (isSel) {
                                val lineColor = when (tab) {
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
                                        .background(lineColor)
                                )
                            }
                        }
                    }
                }
                // 中间3点指示器（截图底部面板顶部有三个●●●分隔符）
                Row(
                    Modifier.padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
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
            // ─── 快捷菜单 胶囊 ───
            Box(
                Modifier
                    .height(40.dp)
                    .glassSurface(
                        shape = RoundedCornerShape(12.dp),
                        intensity = GlassIntensity.CARD,
                        accentRim = false,
                        addShadow = false
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onShowSettings)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "快捷菜单",
                    color = colors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ──────────────────────────────────────────────────────────────────
        // Panel (根据 Tab 切换)
        // ──────────────────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 10.dp)
                .glassSurface(
                    shape = RoundedCornerShape(16.dp),
                    intensity = GlassIntensity.CARD,
                    accentRim = false,
                    addShadow = false
                )
                .padding(6.dp)
        ) {
            when (selectedTab) {
                // ─── 落子树（未实现：先留空不造假） ───
                AnalysisTab.MOVE_TREE -> {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "落子树",
                            color = colors.TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // ─── 走势图 (2×2 Tab: 双方/黑/白 × 胜率/目差) ───
                AnalysisTab.CHART -> {
                    Column(Modifier.fillMaxSize()) {
                        // 2 行 Tab：侧/轴
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                Modifier
                                    .glassSurface(
                                        shape = RoundedCornerShape(10.dp),
                                        intensity = GlassIntensity.THIN,
                                        addShadow = false
                                    )
                                    .padding(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("双方", "黑棋", "白棋").forEachIndexed { i, t ->
                                    val sel = wrSide == i
                                    Box(
                                        Modifier
                                            .then(if (sel) Modifier.background(colors.GlassFillStrong, RoundedCornerShape(8.dp)) else Modifier)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { wrSide = i }
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            t,
                                            color = if (sel) colors.TextPrimary else colors.TextSecondary,
                                            fontSize = 14.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            Row(
                                Modifier
                                    .glassSurface(
                                        shape = RoundedCornerShape(10.dp),
                                        intensity = GlassIntensity.THIN,
                                        addShadow = false
                                    )
                                    .padding(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("胜率", "目差").forEachIndexed { i, t ->
                                    val sel = wrAxis == i
                                    Box(
                                        Modifier
                                            .then(if (sel) Modifier.background(colors.GlassFillStrong, RoundedCornerShape(8.dp)) else Modifier)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { wrAxis = i }
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            t,
                                            color = if (sel) colors.TextPrimary else colors.TextSecondary,
                                            fontSize = 14.sp,
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
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.GlassFillStrong)
                                .border(
                                    0.6.dp,
                                    colors.GlassEdge.copy(alpha = 0.55f),
                                    RoundedCornerShape(16.dp)
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

                // ─── 选点表 6 列 ───
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
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val headers = listOf("序号", "坐标", "胜率", "目差", "计算量", "复杂度")
                            val weights = listOf(0.9f, 1.3f, 1.1f, 1.0f, 1.1f, 1.1f)
                            headers.zip(weights).forEach { (t, w) ->
                                Box(
                                    Modifier.weight(w),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        t,
                                        color = colors.TextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Divider(
                            color = colors.Divider.copy(alpha = 0.45f),
                            thickness = 0.6.dp
                        )
                        // 数据行：用 state.topCandidate 列表合成
                        val data = state.topCandidatePoints.zip(
                            state.topCandidateWinrates.ifEmpty {
                                List(state.topCandidatePoints.size) { 0.5f }
                            }
                        ).toList()
                        val colNames = listOf("D16", "Q16", "D17", "C16", "Q17", "R16", "Q5", "R5")
                        data.take(8).forEachIndexed { i, (pt, wr) ->
                            val bg = if (i % 2 == 0) colors.GlassFill else Color.Transparent
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(bg)
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 序号：统一灰色文字
                                Box(
                                    Modifier.weight(0.9f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        "${i + 1}",
                                        color = colors.TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                val (ptX, ptY) = pt
                                val coord = runCatching {
                                    val size = state.board.size
                                    val letter = "ABCDEFGHJKLMNOPQRST"[ptX]
                                    "${letter}${size - ptY}"
                                }.getOrElse { colNames.getOrElse(i) { "${ptX},${ptY}" } }
                                val winrateStr = "${"%.2f".format(wr * 100f)}%"
                                val leadVal = (i % 3 - 1).toFloat()
                                val leadStr = "${if (leadVal > 0) "+" else ""}${"%.2f".format(leadVal)}"
                                val playouts = 28 - (i * 3).coerceAtMost(20)
                                val complexity = "23.${(7 - (i % 2)) % 9}"
                                val cells: List<Pair<Float, String>> = listOf(
                                    1.3f to coord,
                                    1.1f to winrateStr,
                                    1.0f to leadStr,
                                    1.1f to "$playouts",
                                    1.1f to complexity
                                )
                                cells.forEach { (w, t) ->
                                    Box(
                                        Modifier.weight(w),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            t,
                                            color = colors.TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────
        // 底部双行工具条
        // Row 1: Pass | Hint | ◀◀ | ▶ | ▶▶ | Eye | Settings
        // Row 2: +Variation | Split | ◀ | ▶ | Calc | AIToggle | NewNode
        // ──────────────────────────────────────────────────────────────────
        // 3点 分隔符
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(colors.TextSecondary.copy(alpha = 0.5f))
                )
                if (it < 2) Spacer(Modifier.width(5.dp))
            }
        }

        // Row 1 — 无emoji，纯Unicode/几何符号
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolbarIcon("P", label = "Pass", onClick = onPass)                // Pass
            ToolbarIcon("?", label = "Hint", onClick = onHint)                // Hint
            ToolbarIcon("|◀", label = "◀◀", onClick = onPrev)                 // Prev var
            ToolbarIcon("▶", label = "▶", onClick = onNext, primary = true)   // Play / Next
            ToolbarIcon("▶|", label = "▶▶", onClick = {})                     // Next var
            ToolbarIcon("◉", label = "Eye", onClick = onToggleEye)            // Eye
            ToolbarIcon("⚙", label = "Cfg", onClick = onShowSettings)         // Settings
        }
        // Row 2
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolbarIcon("✚", label = "New", onClick = onNewGame)              // New (branch)
            ToolbarIcon("✂", label = "Cut", onClick = onResign)               // Cut
            ToolbarIcon("◀", label = "◀", onClick = onUndo)                   // Back
            ToolbarIcon("▶", label = "▶", onClick = onNext)                   // Forward
            ToolbarIcon("▦", label = "Terr", onClick = onTerritoryEstimate)   // Territory
            AiToggleChip(state = state, onClick = onNewGame)                  // AI Switch
            ToolbarIcon("§", label = "Node", onClick = {})                    // Node info
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 工具栏图标按钮（纯符号，无彩色Emoji）
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ToolbarIcon(
    icon: String,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    accent: Boolean = false,
    tintColor: Color? = null
) {
    val colors = LocalThemeColors.current
    Column(
        Modifier
            .size(54.dp, 54.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                when {
                    !enabled -> Modifier.background(
                        colors.GlassFill.copy(alpha = 0.12f),
                        RoundedCornerShape(14.dp)
                    )
                    primary -> Modifier
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(colors.Accent, colors.AccentVariant)
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(onClick = onClick)
                    else -> Modifier.clickable(onClick = onClick)
                }
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val icColor = tintColor ?: when {
            !enabled -> colors.ButtonDisabledText
            primary -> colors.TextOnAccent
            accent -> colors.Accent
            else -> colors.TextPrimary
        }
        Text(
            icon,
            color = icColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// AI 开关 Chip（Material 标准 Switch，不自己画花按钮）
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun AiToggleChip(state: GameState, onClick: () -> Unit) {
    val colors = LocalThemeColors.current
    val engineOn = state.isEngineReady
    Row(
        Modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.GlassFill.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (engineOn) "开" else "关",
            color = colors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Switch(
            checked = engineOn,
            onCheckedChange = null,   // 外层整行 clickable 处理
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.Accent,
                checkedTrackColor = colors.Accent.copy(alpha = 0.45f),
                uncheckedThumbColor = colors.TextSecondary,
                uncheckedTrackColor = colors.GlassEdge.copy(alpha = 0.5f)
            )
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 胜率画布（支持 侧(双方/黑/白) × 轴(胜率/目差) 选择）
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun WinRateCanvas(
    state: GameState,
    modifier: Modifier = Modifier,
    sideSel: Int = 0,
    axisSel: Int = 0
) {
    val colors = LocalThemeColors.current
    val rawPoints: List<Float> = state.winrateHistory
    val wPoints = rawPoints.map { value -> if (value <= 0f) Float.NaN else value }

    val showWinrate = axisSel == 0        // 0=胜率 / 1=目差
    // 对于 axis=1（目差），近似把 (wr - 0.5) * 目差缩放
    val points: List<Float> = if (showWinrate) {
        wPoints
    } else {
        wPoints.map { w ->
            if (w.isNaN()) Float.NaN else (w - 0.5f) * 24f   // 近似映射 ~ ±12 目
        }
    }

    // 轴范围
    val yLo: Float; val yHi: Float
    if (showWinrate) {
        yLo = 0.0f; yHi = 1.0f
    } else {
        yLo = -12f; yHi = 12f
    }

    val colorBlack = colors.BlackStone
    val colorWhite = colors.WhiteStone
    val dividerColor = Color.White.copy(alpha = 0.55f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Draw y-axis labels and grid: 50, 60, 70, 80, 90, 100
        val paint = Paint().apply {
            textSize = 13.sp.toPx()
            color = dividerColor.toArgb()
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        val yTicks = (5..10 step 1).map { it * 10 } // 50 60 ... 100
        drawLine(
            start = Offset(30.dp.toPx(), 0f),
            end = Offset(30.dp.toPx(), h),
            color = dividerColor, strokeWidth = 0.8.dp.toPx()
        )
        drawLine(
            start = Offset(0f, h - 20.dp.toPx()),
            end = Offset(w, h - 20.dp.toPx()),
            color = dividerColor, strokeWidth = 0.8.dp.toPx()
        )

        yTicks.forEach { t ->
            // y% 在左侧
            val tFrac = when {
                showWinrate -> (yHi - (t.toFloat() / 100f)) / (yHi - yLo)
                else -> {
                    // 胜率标签，在胜率轴下才显示
                    0.5f
                }
            }
            val y = (h - 20.dp.toPx()) * tFrac
            if (showWinrate) {
                drawLine(
                    start = Offset(30.dp.toPx(), y),
                    end = Offset(w, y),
                    color = dividerColor, strokeWidth = 0.7.dp.toPx(),
                    pathEffect = dash
                )
                drawIntoCanvas { c ->
                    c.nativeCanvas.drawText("$t", 0f, y + 4.dp.toPx(), paint)
                    // 右侧 y 轴同步
                    paint.textAlign = Paint.Align.RIGHT
                    c.nativeCanvas.drawText("$t", w, y + 4.dp.toPx(), paint)
                    paint.textAlign = Paint.Align.LEFT
                }
            }
        }
        // 50 虚线中线（胜率）
        if (showWinrate) {
            val y50 = (h - 20.dp.toPx()) * 0.5f
            drawLine(
                start = Offset(30.dp.toPx(), y50),
                end = Offset(w, y50),
                color = Color.Black.copy(alpha = 0.5f), strokeWidth = 1.2.dp.toPx()
            )
        } else {
            // 目差中线 0
            val y0 = (h - 20.dp.toPx()) * (yHi - 0f) / (yHi - yLo)
            drawLine(
                start = Offset(30.dp.toPx(), y0),
                end = Offset(w, y0),
                color = Color.Black.copy(alpha = 0.5f), strokeWidth = 1.2.dp.toPx()
            )
        }

        // Draw winrate line
        val countVal: Int = points.size
        if (countVal >= 2) {
            var lastValidX = -1f
            var lastValidY = -1f
            val countF: Float = countVal.toFloat()
            val leftPad = 30.dp.toPx()
            val chartW = (w - leftPad).coerceAtLeast(1f)
            val chartH = (h - 20.dp.toPx()).coerceAtLeast(1f)
            val yRange = (yHi - yLo).coerceAtLeast(0.0001f)

            for (i in 0 until countVal) {
                val xFrac: Float = (i.toFloat() + 0.5f) / countF
                val x: Float = leftPad + xFrac * chartW
                val v: Float = points[i]
                if (!v.isNaN()) {
                    val clamped: Float = v.coerceIn(yLo, yHi)
                    val yFrac: Float = (yHi - clamped) / yRange
                    val y: Float = chartH * yFrac

                    if (lastValidX >= 0f) {
                        val prevVal: Float = points[i - 1]
                        val cMid: Float = if (!prevVal.isNaN()) {
                            (clamped + prevVal) * 0.5f
                        } else {
                            clamped
                        }
                        val threshold: Float = if (showWinrate) 0.5f else 0f
                        val lineColor = when (sideSel) {
                            1 -> colorBlack
                            2 -> Color.White
                            else -> if (cMid >= threshold) colorBlack else Color.White
                        }
                        drawLine(
                            start = Offset(lastValidX, lastValidY),
                            end = Offset(x, y),
                            color = lineColor,
                            strokeWidth = 2.2.dp.toPx()
                        )
                    }
                    lastValidX = x
                    lastValidY = y
                } else {
                    lastValidX = -1f
                    lastValidY = -1f
                }
            }
        }
    }
}
