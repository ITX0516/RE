package com.badukai.next.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
            // LINE 1: 顶栏（原版3个GlassPill：新对局/设置/SGF）+ 对弈/分析分段胶囊
            //         = 3个功能按钮 + 1个分段；按钮数量严格=3（底部再加5个=8个）
            // ────────────────────────────────────────────────────────
            val isDiagnostic = state.gameMessage.contains("=== AI START DIAGNOSTIC ===")
            if (isDiagnostic) {
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
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 12.sp,
                        )
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .height(46.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 原版 3 GlassPills：➕ 新对局 / ⚙ 设置 / SGF
                    GlassPill("➕ 新对局", onClick = onNewGame)
                    GlassPill("⚙ 设置", onClick = onShowSettings)
                    GlassPill("SGF", onClick = onSaveSgf, accentRim = true)

                    Spacer(Modifier.weight(1f))

                    // 对弈 / 分析 分段胶囊（用户截图最右侧，明确有这个）
                    Row(
                        Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.GlassFill.copy(alpha = 0.45f))
                            .border(0.5.dp, colors.GlassEdge.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
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
                                    .heightIn(min = 36.dp)
                                    .then(
                                        if (sel) Modifier.background(
                                            brush = Brush.horizontalGradient(
                                                listOf(colors.Accent, colors.AccentLight)
                                            ), RoundedCornerShape(12.dp)
                                        ) else Modifier
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSetGameMode(mode) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (sel) colors.TextOnAccent else colors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ────────────────────────────────────────────────────────
            // LINE 2: Match status — 压缩高度到38dp，棋子28dp，左黑右白，不挤
            // ────────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 0.dp)
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 黑方 左对齐
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StonePuckSmall(StoneColor.BLACK)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "黑棋",
                        color = colors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(10.dp))
                    ScoreChipSmall(text = "${state.capturedByBlack}")
                    Spacer(Modifier.width(6.dp))
                    ScoreChipSmall(text = "00:09", mono = true)
                }
                // 白方 右对齐
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreChipSmall(text = "--:--", mono = true)
                    Spacer(Modifier.width(6.dp))
                    ScoreChipSmall(text = "${state.capturedByWhite}")
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "白棋",
                        color = colors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(6.dp))
                    StonePuckSmall(StoneColor.WHITE)
                }
            }

            // ────────────────────────────────────────────────────────
            // LINE 3: 胜率条（恢复原版阿Q位置，就在状态条下，棋盘上）
            // ────────────────────────────────────────────────────────
            WinRateBarSmall(state = state)

            // Starting AI 提示 — 居中大字、紧贴胜率条下
            val message = state.gameMessage
            val showHint = message.isNotBlank()
                        && !message.contains("=== AI START DIAGNOSTIC ===")
                        && !message.startsWith("Diagnostics")
            AnimatedVisibility(
                visible = showHint,
                enter = fadeIn(animationSpec = tween(200)) + expandVertically(),
                exit = fadeOut(animationSpec = tween(120)) + shrinkVertically()
            ) {
                Text(
                    text = message,
                    color = colors.TextPrimary.copy(alpha = 0.85f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    textAlign = TextAlign.Center
                )
            }

            // ════════════════════════════════════════════════════════
            // BOARD：无玻璃框，底部加 padding 避免坐标被 AF 挡
            // ════════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    shape = RoundedCornerShape(0.dp),
                    shadowElevation = 0.dp,
                    color = colors.BoardBackground
                ) {
                    GoBoard(
                        board = state.board, lastMovePoint = state.lastMovePoint,
                        onIntersectionTap = onBoardTap,
                        enabled = state.isEngineReady && !state.isThinking,
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
                // Territory 浮层
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
                                Text(
                                    "终局",
                                    color = colors.TextOnAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════
            // FOOTER：AnalysisFooter（贴近棋盘；面板240dp + 走势图顶部Tab）
            // 然后原版 PlayButtonRow 5 个 GlassIconBtn
            // 按钮总数：顶栏3 + 底部5 = 8
            // ════════════════════════════════════════════════════════
            Spacer(Modifier.height(2.dp))
            AnalysisFooter(
                state = state,
                onPrev = onAnalysisPrev,
                onNext = onAnalysisNext,
                onJumpToMove = onAnalysisJump,
                onToggleEye = onToggleEye
            )
            Spacer(Modifier.height(2.dp))
            PlayButtonRow(
                state = state,
                onNewGame = onNewGame,
                onPass = onPass,
                onResign = onResign,
                onTerritoryEstimate = onTerritoryEstimate,
                onUndo = onUndo
            )
            Spacer(Modifier.height(6.dp))
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "btnScale"
    )
    Column(
        modifier = modifier
            .scale(scale)
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
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = true,
                            onClick = onClick
                        )
                else
                    Modifier
                        .glassSurface(
                            shape = RoundedCornerShape(18.dp),
                            intensity = GlassIntensity.THIN,
                            addShadow = false
                        )
                        .clip(RoundedCornerShape(18.dp))
            )
            .padding(vertical = 8.dp),
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
            fontSize = 22.sp
        )
        Spacer(Modifier.height(1.dp))
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
// 小一号 StonePuck — 28dp，避免被棋盘挡住
// ──────────────────────────────────────────────
@Composable
private fun StonePuckSmall(color: StoneColor) {
    val colors = LocalThemeColors.current
    when (color) {
        StoneColor.BLACK -> {
            Box(
                Modifier
                    .size(28.dp)
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
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(Color.White, colors.WhiteStone)
                        )
                    )
                    .border(0.5.dp, colors.WhiteStoneBorder, CircleShape)
            )
        }
        else -> {}
    }
}

// ──────────────────────────────────────────────
// 小一号 ScoreChip — 提子/计时器
// ──────────────────────────────────────────────
@Composable
private fun ScoreChipSmall(text: String, mono: Boolean = false) {
    val colors = LocalThemeColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.GlassFill.copy(alpha = 0.4f))
            .border(0.5.dp, colors.GlassEdge.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = colors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) FontFamily.Monospace else null
        )
    }
}

// ──────────────────────────────────────────────
// 小而清楚的胜率条（就在状态行下/棋盘上，仿阿Q原版）
// ──────────────────────────────────────────────
@Composable
private fun WinRateBarSmall(state: GameState) {
    val colors = LocalThemeColors.current
    val t = if (state.winrate > 0f) state.winrate else 0.5f
    val blackTarget = (1f - t).coerceIn(0f, 1f)
    val blackW by animateFloatAsState(
        targetValue = blackTarget,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 120f),
        label = "blackW"
    )
    val whiteW = 1f - blackW
    val lead = state.scoreLead
    val leadText = if (lead == 0f && state.winrate <= 0f) "0.0" else "%.1f".format(kotlin.math.abs(lead))
    val leadSide = if (lead >= 0f) "B" else "W"
    val blackPct = "%.1f".format(blackW * 100f)
    val whitePct = "%.1f".format(whiteW * 100f)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 0.dp)
            .clip(RoundedCornerShape(10.dp))
            .height(30.dp)
    ) {
        // 黑方区域 — 文字左对齐 + 足够 padding，不会被挤掉
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(blackW)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF222222), Color(0xFF444444))
                    )
                )
                .padding(start = 12.dp, end = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "$blackPct% ($leadSide-$leadText)",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
        // 白方区域 — 文字右对齐
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = 6.dp, end = 12.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                "$whitePct% ($leadSide+$leadText)",
                color = Color(0xFF111111),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
