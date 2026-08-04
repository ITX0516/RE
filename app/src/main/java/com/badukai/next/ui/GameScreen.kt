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
import androidx.compose.ui.geometry.Offset
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
                // ── Unified glass top bar ──
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .height(44.dp)
                        .glassSurface(
                            shape = RoundedCornerShape(14.dp),
                            intensity = GlassIntensity.CARD,
                            addShadow = false
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: text buttons separated by ·
                    Text(
                        "新局",
                        color = colors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(onClick = onNewGame)
                    )
                    Text(
                        " · ",
                        color = colors.Divider,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    Text(
                        "设置",
                        color = colors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(onClick = onShowSettings)
                    )
                    Text(
                        " · ",
                        color = colors.Divider,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    Text(
                        "棋谱",
                        color = colors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(onSaveSgf)
                    )

                    Spacer(Modifier.weight(1f))

                    // Right: mode segmented control
                    Row(
                        Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.GlassFill.copy(alpha = 0.4f))
                            .border(0.5.dp, colors.GlassEdge.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
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
                                    .heightIn(min = 28.dp)
                                    .then(
                                        if (sel) Modifier.background(
                                            brush = Brush.horizontalGradient(
                                                listOf(colors.Accent, colors.AccentLight)
                                            ), RoundedCornerShape(8.dp)
                                        ) else Modifier
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSetGameMode(mode) }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (sel) colors.TextOnAccent else colors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ────────────────────────────────────────────────────────
            // LINE 2: Player info — unified glass card, gradient stones
            // ────────────────────────────────────────────────────────
            val isBlackTurn = state.isPlayerTurn && state.currentPlayer == StoneColor.BLACK
            val isWhiteTurn = state.isPlayerTurn && state.currentPlayer == StoneColor.WHITE
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 0.dp)
                    .height(50.dp)
                    .glassSurface(
                        shape = RoundedCornerShape(16.dp),
                        intensity = GlassIntensity.CARD,
                        addShadow = false
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Black side
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (isBlackTurn) Modifier
                                .background(colors.Accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StonePuckSmall(StoneColor.BLACK)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "黑棋",
                        color = colors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "提:${state.capturedByBlack}",
                        color = colors.TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "00:09",
                        color = colors.TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                // Center divider
                Box(
                    Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(colors.Divider.copy(alpha = 0.4f))
                )
                // White side
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (isWhiteTurn) Modifier
                                .background(colors.Accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        "--:--",
                        color = colors.TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "提:${state.capturedByWhite}",
                        color = colors.TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "白棋",
                        color = colors.TextPrimary,
                        fontSize = 15.sp,
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
// Play Button Row (redesigned: horizontal text buttons)
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
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
    ) {
        GlassIconBtn(
            "+", "New", onClick = onNewGame,
            enabled = true, primary = true,
            modifier = Modifier.weight(1f)
        )
        GlassIconBtn(
            "\u21BA", "Undo", onClick = onUndo,
            enabled = state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() >= 2,
            modifier = Modifier.weight(1f)
        )
        GlassIconBtn(
            "\u2298", "Pass", onClick = onPass,
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
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "btnScale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .height(52.dp)
            .then(
                if (enabled)
                    Modifier
                        .then(
                            if (primary) Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(colors.Accent, colors.AccentVariant)
                                    ), RoundedCornerShape(14.dp)
                                )
                            else if (accent) Modifier
                                .border(0.5.dp, colors.Accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .background(colors.GlassFill.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                            else Modifier
                                .border(0.5.dp, colors.GlassEdge.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .background(colors.GlassFill.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = true,
                            onClick = onClick
                        )
                else
                    Modifier
                        .border(0.5.dp, colors.GlassEdge.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                icon,
                color = when {
                    !enabled -> colors.ButtonDisabledText.copy(alpha = 0.5f)
                    primary -> colors.TextOnAccent
                    accent -> colors.Accent
                    else -> colors.TextPrimary.copy(alpha = 0.8f)
                },
                fontSize = 18.sp
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                color = when {
                    !enabled -> colors.ButtonDisabledText.copy(alpha = 0.5f)
                    primary -> colors.TextOnAccent.copy(alpha = 0.9f)
                    accent -> colors.Accent
                    else -> colors.TextSecondary
                },
                fontSize = 12.sp,
                fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

// ──────────────────────────────────────────────
// StonePuck — 30dp gradient stone with subtle shadow
// ──────────────────────────────────────────────
@Composable
private fun StonePuckSmall(color: StoneColor) {
    val colors = LocalThemeColors.current
    when (color) {
        StoneColor.BLACK -> {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.BlackStoneHighlight,
                                colors.BlackStone,
                                colors.BlackStone.copy(alpha = 0.85f)
                            ),
                            center = Offset(0.35f, 0.3f),
                            radius = 0.7f
                        )
                    )
            )
        }
        StoneColor.WHITE -> {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White,
                                colors.WhiteStoneHighlight,
                                colors.WhiteStone
                            ),
                            center = Offset(0.35f, 0.3f),
                            radius = 0.7f
                        )
                    )
                    .border(0.5.dp, colors.WhiteStoneBorder.copy(alpha = 0.6f), CircleShape)
            )
        }
        else -> {}
    }
}

// ScoreChipSmall — removed, player info uses inline Text now

// ──────────────────────────────────────────────
// Win rate bar — 34dp, refined gradients, clear separator
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
    val lead = state.scoreLead
    val leadText = if (lead == 0f && state.winrate <= 0f) "0.0" else "%.1f".format(kotlin.math.abs(lead))
    val blackPct = "%.0f".format(blackW * 100f)
    val whitePct = "%.0f".format((1f - blackW) * 100f)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(0.5.dp, colors.GlassEdge.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
    ) {
        // Black section
        Box(
            Modifier
                .fillMaxHeight()
                .weight(blackW.coerceAtLeast(0.01f))
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF1A1A1A), Color(0xFF333333))
                    )
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "\u25CF $blackPct%  +$leadText",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
        // Thin separator line
        Box(
            Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color(0xFF666666))
        )
        // White section
        Box(
            Modifier
                .fillMaxHeight()
                .weight((1f - blackW).coerceAtLeast(0.01f))
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFFF2F2F2), Color(0xFFDADADA))
                    )
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                "+$leadText  $whitePct% \u25CB",
                color = Color(0xFF222222),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
