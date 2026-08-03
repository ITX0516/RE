package com.badukai.next.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badukai.next.engine.ModelSource
import com.badukai.next.game.GameMode
import com.badukai.next.game.GameState
import com.badukai.next.game.PlacementMode
import com.badukai.next.game.StoneAnimation
import com.badukai.next.game.StoneColor
import com.badukai.next.engine.KataGoEngine

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
    onStartNewGame: (StoneColor, Int, Int, Float, Int, Boolean) -> Unit,
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
    onSetAiMoveTime: (Int) -> Unit,
    onSetAiCanResign: (Boolean) -> Unit,
    onSetAiModelSource: (ModelSource) -> Unit,
    onPickCustomModel: () -> Unit,
    onResetAiModelToBundled: () -> Unit,
    onSaveSgf: () -> Unit,
    onShowSavedGames: () -> Unit,
    onDismissSavedGames: () -> Unit,
    onLoadSgf: (String) -> Unit,
    onDismissCelebration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalThemeColors.current

    // ═══ SETTINGS: full-screen iOS route replacement ═══
    // When `showSettings` is true we swap the entire Board composition with
    // the new iOS-style grouped Settings page. This way we don't stack a
    // Dialog on top of the board (which was cramped on small phones), and
    // the overall UX matches Apple's Settings app navigation.
    if (state.showSettings) {
        SettingsScreen(
            showCoordinates = state.showCoordinates,
            soundEnabled = state.soundEnabled,
            currentTheme = state.currentTheme,
            currentPlacementMode = state.placementMode,
            currentAnimation = state.stoneAnimation,
            placeSoundIndex = state.placeSoundIndex,
            aiMoveTimeSeconds = state.aiMoveTimeSeconds,
            aiCanResign = state.aiCanResign,
            aiModelSource = state.aiModelSource,
            customModelDisplayName = state.customModelDisplayName,
            onBack = onDismissSettings,
            onToggleCoordinates = onToggleCoordinates,
            onToggleSound = onToggleSound,
            onSetTheme = onSetTheme,
            onSetPlacementMode = onSetPlacementMode,
            onSetAnimation = onSetAnimation,
            onSetPlaceSound = onSetPlaceSound,
            onSetAiMoveTime = onSetAiMoveTime,
            onSetAiCanResign = onSetAiCanResign,
            onSetAiModelSource = onSetAiModelSource,
            onPickCustomModel = onPickCustomModel,
            onResetAiModelToBundled = onResetAiModelToBundled
        )
        // NOTE: Dialogs (NewGame / SavedGames / Celebration / ModelSelector)
        // still render below via the post-Column overlay block. If the user
        // ever opens one while in SettingsPage (shouldn't happen from UI),
        // it'll appear on top — benign.
        return
    }

    // ═══ Liquid-glass background gradient + colored blobs ═══
    // (保留玻璃渐变底，工具条等浅色控件在此背景上能透出彩色边缘)
    Box(
        modifier = modifier
            .fillMaxSize()
            .glassBackgroundGradient()
            .glassBackgroundBlobs()
            .statusBarsPadding()
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ────────────────────────────────────────────────────────
            // LINE 1: Action bar (仿阿Q截图第一行：5个工具图标+分析/对弈分段胶囊)
            // ────────────────────────────────────────────────────────
            val isDiagnostic = state.gameMessage.contains("=== AI START DIAGNOSTIC ===")
            if (isDiagnostic) {
                // 紧急诊断信息（AI启动失败）占据顶栏 — 全屏展开便于排查
                val scroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp, max = 220.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .glassSurface(
                            shape = RoundedCornerShape(14.dp),
                            intensity = GlassIntensity.STRONG,
                            accentRim = true,
                            addShadow = false
                        )
                        .verticalScroll(scroll)
                        .padding(10.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = state.gameMessage,
                            color = colors.TextPrimary,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            lineHeight = 12.sp,
                        )
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .height(54.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 5个工具图标（左对齐均分）
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start)
                    ) {
                        TopBarActionBtn("\u2630", onClick = onShowSettings)   // ☰ 设置
                        TopBarActionBtn("\uD83D\uDCC2", onClick = onShowSavedGames) // 📂 打开
                        TopBarActionBtn("\uD83D\uDCBE", onClick = onSaveSgf)        // 💾 保存
                        TopBarActionBtn("\uD83D\uDD17", onClick = {})                // 🔗 分享（占位）
                        TopBarActionBtn("A\u2715", onClick = {})                    // A❌ 辅助/插件占位
                    }
                    // 右上角：分析 | 对弈 分段胶囊（和截图一致）
                    Row(
                        Modifier
                            .height(40.dp)
                            .glassSurface(
                                shape = RoundedCornerShape(14.dp),
                                intensity = GlassIntensity.CARD,
                                accentRim = false,
                                addShadow = false
                            )
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GameMode.entries.forEach { mode ->
                            val sel = mode == state.gameMode
                            val label = when (mode) {
                                GameMode.ANALYZE -> "分析"
                                GameMode.PLAY -> "对弈"
                            }
                            Box(
                                Modifier
                                    .heightIn(min = 40.dp)
                                    .then(
                                        if (sel)
                                            Modifier.background(
                                                brush = Brush.horizontalGradient(
                                                    listOf(colors.Accent, colors.AccentLight)
                                                ),
                                                RoundedCornerShape(12.dp)
                                            )
                                        else Modifier
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSetGameMode(mode) }
                                    .padding(horizontal = 18.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (sel) colors.TextOnAccent else colors.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ────────────────────────────────────────────────────────
            // LINE 2: Match status bar — 黑棋 [提子] [时间] 白棋 [提子] [时间]
            // ────────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // 黑方
                Row(
                    Modifier.wrapContentWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StonePuck(StoneColor.BLACK)
                    Spacer(Modifier.width(10.dp))
                    Text("黑棋", color = colors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(16.dp))
                    ScoreChip(text = "${state.capturedByBlack}")
                    Spacer(Modifier.width(8.dp))
                    ScoreChip(text = "00:09", mono = true, accent = false) // 计时器占位
                }
                Spacer(Modifier.width(8.dp))
                // 白方
                Row(
                    Modifier.wrapContentWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StonePuck(StoneColor.WHITE)
                    Spacer(Modifier.width(10.dp))
                    Text("白棋", color = colors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(16.dp))
                    ScoreChip(text = "${state.capturedByWhite}")
                    Spacer(Modifier.width(8.dp))
                    ScoreChip(text = "--:--", mono = true, accent = false)
                }
            }

            // ────────────────────────────────────────────────────────
            // LINE 3: Move counter (居中：手数 N 轮X下)
            // ────────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val moveCount = state.board.getMoveCount()
                val turnColor = state.currentPlayer
                val turnLabel = if (turnColor == StoneColor.BLACK) "黑" else "白"
                Text(
                    "手数 $moveCount  轮${turnLabel}下",
                    color = colors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // ════════════════════════════════════════════════════════
            // BOARD：无玻璃框，坐标直接放在棋盘边缘，100% 面积给棋盘
            // ════════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(0.dp),
                    shadowElevation = 0.dp,
                    color = colors.BoardBackground
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

                // Territory 估计状态浮层：悬浮在棋盘右下，不打断布局
                if (state.showTerritoryOverlay && state.territoryResult.isNotEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(vertical = 10.dp, horizontal = 14.dp)
                            .glassSurface(
                                shape = RoundedCornerShape(16.dp),
                                intensity = GlassIntensity.STRONG,
                                accentRim = true,
                                addShadow = true
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                state.territoryResult,
                                color = colors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(14.dp))
                            Box(
                                Modifier
                                    .glassButton(
                                        shape = RoundedCornerShape(12.dp),
                                        primary = true
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onForceEndGame() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("强制终局", color = colors.TextOnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════
            // FOOTER：AnalysisFooter（内部包含3 Tab + 面板 + 双行工具条）
            // ════════════════════════════════════════════════════════
            AnalysisFooter(
                state = state,
                onPrev = onAnalysisPrev,
                onNext = onAnalysisNext,
                onJumpToMove = onAnalysisJump,
                onToggleEye = onToggleEye,
                onPass = onPass,
                onHint = {},
                onUndo = onUndo,
                onResign = onResign,
                onTerritoryEstimate = onTerritoryEstimate,
                onNewGame = onNewGame,
                onShowSettings = onShowSettings
            )
        }

        // ═══ Dialogs (drawn OVER the board, never inside the scroll Column) ═══
        if (state.showNewGameDialog) {
            NewGameDialog(
                onDismiss = onDismissNewGame,
                onStartGame = onStartNewGame,
                initialAiTime = state.aiMoveTimeSeconds,
                initialAiCanResign = state.aiCanResign
            )
        }
        if (state.showModelSelector) {
            ModelSelectorDialog(
                currentModel = state.selectedModel,
                onDismiss = onDismissModelSelector,
                onSelectModel = onSelectModel
            )
        }
        // NOTE: showSettings no longer uses a Dialog — it's handled via
        // the early-return route-switch above.
        if (state.showSavedGamesDialog) {
            SavedGamesDialog(
                games = state.savedGames,
                onDismiss = onDismissSavedGames,
                onLoad = onLoadSgf
            )
        }

        // ═══ End-game celebration overlay ═══
        if (state.gameResult != null) {
            CelebrationOverlay(result = state.gameResult, onDismiss = onDismissCelebration)
        }
    } // close Box (background gradient + blobs)
}

// ──────────────────────────────────────────────
// Liquid-glass pill button (for Save/Load/Settings)
// ──────────────────────────────────────────────
@Composable
private fun GlassPill(text: String, onClick: () -> Unit, accentRim: Boolean = false) {
    val colors = LocalThemeColors.current
    Box(
        Modifier
            .glassSurface(
                shape = RoundedCornerShape(12.dp),
                intensity = GlassIntensity.CARD,
                accentRim = accentRim,
                addShadow = false
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = colors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ──────────────────────────────────────────────
// Play Button Row (liquid-glass, improved)
// ──────────────────────────────────────────────
@Composable
private fun PlayButtonRow(
    state: GameState, onNewGame: () -> Unit, onPass: () -> Unit, onResign: () -> Unit,
    onTerritoryEstimate: () -> Unit, onUndo: () -> Unit
) {
    val colors = LocalThemeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        GlassIconBtn(
            "\u2795", "New", onClick = onNewGame,
            enabled = true, primary = true,
            modifier = Modifier.weight(1f)
        )
        GlassIconBtn(
            "\u21BA", "Undo", onClick = onUndo,
            enabled = state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() >= 2,
            modifier = Modifier.weight(1f)
        )
        GlassIconBtn(
            "\u23ED", "Pass", onClick = onPass,
            enabled = state.isPlayerTurn && !state.isThinking && state.isEngineReady,
            modifier = Modifier.weight(1f)
        )
        GlassIconBtn(
            "\u2691", "Resign", onClick = onResign,
            enabled = state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() > 0,
            modifier = Modifier.weight(1f)
        )
        GlassIconBtn(
            "\u25CE", "形势", onClick = onTerritoryEstimate,
            enabled = true, accent = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GlassIconBtn(
    icon: String, label: String, onClick: () -> Unit,
    enabled: Boolean = true, primary: Boolean = false, accent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalThemeColors.current
    Column(
        modifier = modifier
            .then(
                if (enabled)
                    Modifier
                        .glassSurface(
                            shape = RoundedCornerShape(18.dp),
                            intensity = if (primary) GlassIntensity.STRONG else GlassIntensity.CARD,
                            accentRim = accent,
                            addShadow = true
                        )
                        .then(
                            if (primary) Modifier.background(
                                Brush.verticalGradient(
                                    listOf(colors.Accent, colors.AccentVariant)
                                ), RoundedCornerShape(18.dp)
                            ) else if (accent) Modifier.background(
                                colors.AccentLight.copy(alpha = 0.5f), RoundedCornerShape(18.dp)
                            ) else Modifier
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = true, onClick = onClick)
                else
                    Modifier
                        .glassSurface(
                            shape = RoundedCornerShape(18.dp),
                            intensity = GlassIntensity.THIN,
                            addShadow = false
                        )
                        .clip(RoundedCornerShape(18.dp))
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            icon,
            color = when {
                !enabled -> colors.ButtonDisabledText
                primary -> colors.TextOnAccent
                accent -> colors.Accent
                else -> colors.TextPrimary
            },
            fontSize = 24.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = when {
                !enabled -> colors.ButtonDisabledText
                primary -> colors.TextOnAccent.copy(alpha = 0.9f)
                accent -> colors.Accent
                else -> colors.TextSecondary
            },
            fontSize = 11.sp,
            fontWeight = if (accent || primary) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ──────────────────────────────────────────────
// TopBar Action Icon Button (仿阿Q截图：☰/📂/💾/🔗/A❌ 这种 48dp 图标方形按钮)
// ──────────────────────────────────────────────
@Composable
private fun TopBarActionBtn(
    icon: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val colors = LocalThemeColors.current
    Box(
        Modifier
            .size(44.dp)
            .then(
                if (enabled) Modifier
                    .glassSurface(
                        shape = RoundedCornerShape(12.dp),
                        intensity = GlassIntensity.CARD,
                        accentRim = false,
                        addShadow = false
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick)
                else Modifier
                    .glassSurface(
                        shape = RoundedCornerShape(12.dp),
                        intensity = GlassIntensity.THIN,
                        addShadow = false
                    )
                    .clip(RoundedCornerShape(12.dp))
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            icon,
            color = if (enabled) colors.TextPrimary else colors.ButtonDisabledText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ──────────────────────────────────────────────
// Stone puck (黑/白色棋子图标，26dp)
// ──────────────────────────────────────────────
@Composable
private fun StonePuck(color: StoneColor) {
    val colors = LocalThemeColors.current
    when (color) {
        StoneColor.BLACK -> {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(colors.BlackStoneHighlight, colors.BlackStone)
                        )
                    )
            )
        }
        StoneColor.WHITE -> {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(Color.White, colors.WhiteStone)
                        )
                    )
                    .border(0.6.dp, colors.WhiteStoneBorder, CircleShape)
            )
        }
        else -> { /* no-op, safety for new enum values */ }
    }
}

// ──────────────────────────────────────────────
// Score chip — 提子数 / 计时器 小胶囊
// ──────────────────────────────────────────────
@Composable
private fun ScoreChip(
    text: String,
    mono: Boolean = false,
    accent: Boolean = false
) {
    val colors = LocalThemeColors.current
    val bg = when {
        accent -> colors.Accent.copy(alpha = 0.9f)
        else -> colors.GlassFill
    }
    val fg = when {
        accent -> colors.TextOnAccent
        else -> colors.TextPrimary
    }
    Box(
        Modifier
            .glassSurface(
                shape = RoundedCornerShape(8.dp),
                intensity = if (accent) GlassIntensity.STRONG else GlassIntensity.CARD,
                accentRim = accent,
                addShadow = false
            )
            .then(if (accent) Modifier.background(bg, RoundedCornerShape(8.dp)) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = fg,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else null
        )
    }
}
