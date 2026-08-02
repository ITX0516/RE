package com.badukai.next.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.badukai.next.game.GameMode
import com.badukai.next.game.GameState
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
    onStartNewGame: (StoneColor, Int, Int, Float, Int, Boolean) -> Unit,
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
    onSetAiMoveTime: (Int) -> Unit,
    onSetAiCanResign: (Boolean) -> Unit,
    onSaveSgf: () -> Unit,
    onShowSavedGames: () -> Unit,
    onDismissSavedGames: () -> Unit,
    onLoadSgf: (String) -> Unit,
    onDismissCelebration: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant)
                                .clickable(onClick = onSaveSgf)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Save", color = colors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant)
                                .clickable(onClick = onShowSavedGames)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Load", color = colors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(colors.SurfaceVariant)
                                .clickable(onClick = onShowSettings)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Settings", color = colors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
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
            onSetAiMoveTime = onSetAiMoveTime,
            onSetAiCanResign = onSetAiCanResign
        )
    }
    if (state.showSavedGamesDialog) {
        SavedGamesDialog(
            games = state.savedGames,
            onDismiss = onDismissSavedGames,
            onLoad = onLoadSgf
        )
    }

    // ── End-game celebration overlay ──
    if (state.gameResult != null) {
        CelebrationOverlay(result = state.gameResult, onDismiss = onDismissCelebration)
    }
}

// ──────────────────────────────────────────────
// Play Button Row
// ──────────────────────────────────────────────
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
        IconBtn("\u25CE", "形势", onClick = onTerritoryEstimate, enabled = true)
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
