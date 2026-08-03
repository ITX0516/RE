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

    // ═══ Liquid-glass background gradient ═══
    Column(
        modifier = modifier
            .fillMaxSize()
            .glassBackgroundGradient()
            .padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ═══ Top bar: liquid-glass capsule ═══
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            shape = RoundedCornerShape(18.dp),
            intensity = GlassIntensity.CARD
        ) {
            // Line 1: status text + mode toggle
            val isDiagnostic = state.gameMessage.contains("=== AI START DIAGNOSTIC ===")
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = if (isDiagnostic) Alignment.Top else Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isDiagnostic) {
                    val scroll = rememberScrollState()
                    SelectionContainer(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 28.dp, max = 220.dp)
                            .verticalScroll(scroll)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = state.gameMessage.ifEmpty { "Ready" },
                            color = colors.TextPrimary,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            lineHeight = 12.sp,
                        )
                    }
                } else {
                    Text(
                        state.gameMessage.ifEmpty { "Ready" },
                        color = colors.TextPrimary, fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Mode toggle: liquid-glass segmented chip
                Row(
                    Modifier
                        .glassSurface(
                            shape = RoundedCornerShape(12.dp),
                            intensity = GlassIntensity.THIN,
                            accentRim = false,
                            addShadow = false
                        )
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GameMode.entries.forEach { mode ->
                        val sel = mode == state.gameMode
                        Box(
                            Modifier
                                .glassSurface(
                                    shape = RoundedCornerShape(10.dp),
                                    intensity = if (sel) GlassIntensity.STRONG else GlassIntensity.THIN,
                                    accentRim = sel,
                                    addShadow = false
                                )
                                .background(if (sel) colors.Accent.copy(alpha = 0.9f) else Color.Transparent)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSetGameMode(mode) }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                mode.displayName,
                                color = if (sel) colors.TextOnAccent else colors.TextSecondary,
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            // Line 2: captures + board size + settings
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: black stone + captured
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(colors.BlackStone)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${state.capturedByBlack}", color = colors.TextSecondary, fontSize = 13.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "${state.boardSize}\u00D7${state.boardSize}",
                        color = colors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.capturedByWhite}", color = colors.TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(colors.WhiteStone)
                            .then(Modifier.border(0.5.dp, colors.WhiteStoneBorder, CircleShape))
                    )
                }
                // Quick action pills
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlassPill(text = "Save", onClick = onSaveSgf)
                    GlassPill(text = "Load", onClick = onShowSavedGames)
                    GlassPill(text = "Settings", onClick = onShowSettings, accentRim = true)
                }
            }
        }

        // ═══ Win rate bar (glassed) ═══
        val wrTarget = if (state.winrate > 0f) state.winrate else 0.5f
        val blackWrTarget = 1f - wrTarget
        val animatedBlackWr by animateFloatAsState(
            targetValue = blackWrTarget.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 600),
            label = "winrate"
        )
        val blackWR = animatedBlackWr
        val wr = 1f - blackWR
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            // Glass capsule container
            Box(
                Modifier
                    .fillMaxWidth()
                    .glassSurface(
                        shape = RoundedCornerShape(12.dp),
                        intensity = GlassIntensity.THIN,
                        addShadow = false
                    )
                    .padding(4.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.Divider.copy(alpha = 0.5f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(blackWR)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.BlackStone)
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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

        // ═══ Board: liquid-glass framed ═══
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer glass frame — the board sits inside a slightly larger glass
            // capsule so you can see the refracted gradient bleed around the
            // edges (classic iOS 26 liquid-glass look).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .glassSurface(
                        shape = RoundedCornerShape(20.dp),
                        intensity = GlassIntensity.CARD,
                        accentRim = false,
                        addShadow = true
                    )
                    .padding(6.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    shape = RoundedCornerShape(14.dp),
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
            }
        }

        // ═══ Territory info bar (glassed) ═══
        if (state.showTerritoryOverlay && state.territoryResult.isNotEmpty()) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(14.dp),
                intensity = GlassIntensity.CARD
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        state.territoryResult,
                        color = colors.TextPrimary, fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier
                            .glassButton(
                                shape = RoundedCornerShape(10.dp),
                                primary = false
                            )
                            .background(colors.Danger.copy(alpha = 0.9f))
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onForceEndGame() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("End", color = colors.TextOnAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ═══ Play buttons (glassed) ═══
        PlayButtonRow(state, onNewGame, onPass, onResign, onTerritoryEstimate, onUndo)

        // ═══ Analysis sub-tabs (glassed) ═══
        AnalysisFooter(state, onAnalysisPrev, onAnalysisNext, onAnalysisJump, onToggleEye)
    }

    // ═══ Dialogs ═══
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
            onSetAnimation = onSetAnimation,
            aiMoveTimeSeconds = state.aiMoveTimeSeconds,
            aiCanResign = state.aiCanResign,
            aiModelSource = state.aiModelSource,
            customModelDisplayName = state.customModelDisplayName,
            onSetAiMoveTime = onSetAiMoveTime,
            onSetAiCanResign = onSetAiCanResign,
            onSetAiModelSource = onSetAiModelSource,
            onPickCustomModel = onPickCustomModel,
            onResetAiModelToBundled = onResetAiModelToBundled
        )
    }
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
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = colors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ──────────────────────────────────────────────
// Play Button Row (liquid-glass)
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
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        GlassIconBtn(
            "\u2795", "New", onClick = onNewGame,
            enabled = true, primary = true
        )
        GlassIconBtn(
            "\u21BA", "Undo", onClick = onUndo,
            enabled = state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() >= 2
        )
        GlassIconBtn(
            "\u23ED", "Pass", onClick = onPass,
            enabled = state.isPlayerTurn && !state.isThinking && state.isEngineReady
        )
        GlassIconBtn(
            "\u2691", "Resign", onClick = onResign,
            enabled = state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() > 0
        )
        GlassIconBtn(
            "\u25CE", "形势", onClick = onTerritoryEstimate,
            enabled = true, accentRim = true
        )
    }
}

@Composable
private fun GlassIconBtn(
    icon: String, label: String, onClick: () -> Unit,
    enabled: Boolean = true, primary: Boolean = false, accentRim: Boolean = false
) {
    val colors = LocalThemeColors.current
    val alpha = if (enabled) 1f else 0.4f
    Column(
        modifier = Modifier
            .then(
                if (enabled)
                    Modifier
                        .glassButton(shape = RoundedCornerShape(16.dp), primary = primary)
                        .glassSurface(
                            shape = RoundedCornerShape(16.dp),
                            intensity = if (primary) GlassIntensity.STRONG else GlassIntensity.CARD,
                            accentRim = accentRim,
                            addShadow = true
                        )
                        .clickable(enabled = true, onClick = onClick)
                else
                    Modifier
                        .glassSurface(
                            shape = RoundedCornerShape(16.dp),
                            intensity = GlassIntensity.THIN,
                            addShadow = false
                        )
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            icon,
            color = if (enabled) (if (primary) colors.TextOnAccent else colors.TextPrimary) else colors.ButtonDisabledText,
            fontSize = 28.sp
        )
        Text(
            label,
            color = if (enabled) (if (primary) colors.TextOnAccent.copy(alpha = 0.9f) else colors.TextSecondary) else colors.ButtonDisabledText,
            fontSize = 11.sp
        )
    }
}
