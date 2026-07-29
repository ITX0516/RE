package com.badukai.next.ui

import androidx.compose.animation.*
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
import com.badukai.next.engine.KataGoEngine
import com.badukai.next.game.GameMode
import com.badukai.next.game.GameState
import com.badukai.next.game.PlacementMode
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
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
    onStartNewGame: (StoneColor, Int) -> Unit,
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
    onAnalysisNext: () -> Unit,
    onConfirmMove: () -> Unit,
    onCancelMove: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.currentTheme) { BadukNextColors.setTheme(state.currentTheme) }

    val colors = BadukNextColors

    Column(
        modifier = modifier.fillMaxSize().background(colors.Background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Mode Tabs ──
        ModeTabs(
            currentMode = state.gameMode,
            onModeChange = onSetGameMode,
            analysisEnabled = state.analysisMoves.isNotEmpty()
        )

        if (state.gameMode == GameMode.ANALYZE) {
            // ── Analysis Mode ──
            AnalysisContent(
                state = state,
                onPrev = onAnalysisPrev,
                onNext = onAnalysisNext,
                onNewGame = onNewGame,
                onModelSelect = onShowModelSelector,
                onSettings = onShowSettings,
                onBoardTap = onBoardTap
            )
        } else {
            // ── Play Mode ──
            PlayContent(
                state = state,
                onBoardTap = onBoardTap,
                onPass = onPass,
                onResign = onResign,
                onUndo = onUndo,
                onNewGame = onNewGame,
                onModelSelect = onShowModelSelector,
                onSettings = onShowSettings,
                onConfirmMove = onConfirmMove,
                onCancelMove = onCancelMove
            )
        }
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
            onDismiss = onDismissSettings,
            onToggleCoordinates = onToggleCoordinates,
            onToggleSound = onToggleSound,
            onSetTheme = onSetTheme,
            onSetPlacementMode = onSetPlacementMode
        )
    }
}

// ──────────────────────────────────────────────
// Mode Tabs
// ──────────────────────────────────────────────
@Composable
private fun ModeTabs(
    currentMode: GameMode,
    onModeChange: (GameMode) -> Unit,
    analysisEnabled: Boolean
) {
    val colors = BadukNextColors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.Surface,
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GameMode.entries.forEach { mode ->
                val enabled = mode != GameMode.ANALYZE || analysisEnabled
                val selected = mode == currentMode
                TabButton(
                    text = mode.displayName,
                    selected = selected,
                    enabled = enabled,
                    onClick = { if (enabled) onModeChange(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BadukNextColors
    val bg = if (selected) colors.AccentLight else Color.Transparent
    val txtColor = if (!enabled) colors.ButtonDisabledText
        else if (selected) colors.Accent else colors.TextSecondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = txtColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ──────────────────────────────────────────────
// Play Content
// ──────────────────────────────────────────────
@Composable
private fun PlayContent(
    state: GameState,
    onBoardTap: (Int, Int) -> Unit,
    onPass: () -> Unit,
    onResign: () -> Unit,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
    onModelSelect: () -> Unit,
    onSettings: () -> Unit,
    onConfirmMove: () -> Unit,
    onCancelMove: () -> Unit
) {
    val colors = BadukNextColors

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Status Bar ──
        PlayStatusBar(
            state = state,
            onNewGame = onNewGame,
            onModelSelect = onModelSelect,
            onSettings = onSettings
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Board ──
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 2.dp
            ) {
                GoBoard(
                    board = state.board,
                    lastMovePoint = state.lastMovePoint,
                    onIntersectionTap = onBoardTap,
                    enabled = state.isPlayerTurn && !state.isThinking && state.isEngineReady,
                    showCoordinates = state.showCoordinates,
                    pendingDot = state.pendingTap
                )
            }
        }

        // ── Confirm/Cancel overlay for CONFIRM mode ──
        Box(modifier = Modifier.height(42.dp).fillMaxWidth()) {
            if (state.placementMode == PlacementMode.CONFIRM && state.confirmMoveQueued != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.Surface)
                            .border(1.dp, colors.Divider, RoundedCornerShape(8.dp))
                            .clickable(onClick = onCancelMove)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = colors.TextPrimary, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.Accent)
                            .clickable(onClick = onConfirmMove)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Place Here", color = colors.TextOnAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Captures ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CaptureChip(
                modifier = Modifier.weight(1f),
                color = StoneColor.BLACK,
                captured = state.capturedByBlack,
                isCurrentPlayer = state.currentPlayer == StoneColor.BLACK
            )
            CaptureChip(
                modifier = Modifier.weight(1f),
                color = StoneColor.WHITE,
                captured = state.capturedByWhite,
                isCurrentPlayer = state.currentPlayer == StoneColor.WHITE
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Action Buttons ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PlayButton(
                text = "Undo",
                onClick = onUndo,
                enabled = state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() >= 2,
                modifier = Modifier.weight(1f)
            )
            PlayButton(
                text = "Pass",
                onClick = onPass,
                enabled = state.isPlayerTurn && !state.isThinking && state.isEngineReady,
                modifier = Modifier.weight(1f)
            )
            PlayButton(
                text = "Resign",
                onClick = onResign,
                enabled = state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() > 0,
                isDanger = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun PlayStatusBar(
    state: GameState,
    onNewGame: () -> Unit,
    onModelSelect: () -> Unit,
    onSettings: () -> Unit
) {
    val colors = BadukNextColors

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: New Game
        IconButton(
            onClick = onNewGame,
            modifier = Modifier.size(36.dp)
        ) {
            Text("\u2795", fontSize = 16.sp) // heavy plus
        }

        // Center: Status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = state.gameMessage.ifEmpty {
                    if (state.isEngineReady) "Ready" else "Starting\u2026"
                },
                color = colors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = "${state.boardSize}\u00D7${state.boardSize}",
                fontSize = 11.sp,
                color = colors.TextSecondary
            )
        }

        // Right: Model + Settings
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = onModelSelect,
                enabled = !state.isEngineStarting && !state.isThinking
            ) {
                Text(
                    state.selectedModel.displayName,
                    fontSize = 12.sp,
                    color = colors.Accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                Text("\u2699", fontSize = 16.sp, color = colors.TextSecondary)
            }
        }
    }
}

// ──────────────────────────────────────────────
// Analysis Content
// ──────────────────────────────────────────────
@Composable
private fun AnalysisContent(
    state: GameState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onNewGame: () -> Unit,
    onModelSelect: () -> Unit,
    onSettings: () -> Unit,
    onBoardTap: (Int, Int) -> Unit
) {
    val colors = BadukNextColors

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Analysis Header ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onNewGame, modifier = Modifier.size(36.dp)) {
                Text("\u2795", fontSize = 16.sp)
            }
            Text(
                text = "Analysis",
                color = colors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                Text("\u2699", fontSize = 16.sp, color = colors.TextSecondary)
            }
        }

        // ── Board ──
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 2.dp
            ) {
                GoBoard(
                    board = state.board,
                    lastMovePoint = state.lastMovePoint,
                    onIntersectionTap = { _, _ -> },
                    enabled = false,
                    showCoordinates = state.showCoordinates
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Move Slider ──
        if (state.analysisMoves.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Move ${state.analysisMoveIndex} / ${state.analysisMoves.size}",
                    color = colors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    itemsIndexed(state.analysisMoves) { idx, _ ->
                        val isSelected = idx == state.analysisMoveIndex - 1
                        val moveNum = idx + 1
                        Box(
                            modifier = Modifier
                                .width(28.dp).height(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) colors.Accent else colors.SurfaceVariant)
                                .clickable { /* would navigate */ }
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$moveNum",
                                fontSize = 10.sp,
                                color = if (isSelected) colors.TextOnAccent else colors.TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // ── Nav Buttons ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { /* jump to first */ },
                enabled = state.analysisMoveIndex > 0
            ) {
                Text("\u23EE", fontSize = 20.sp, color = if (state.analysisMoveIndex > 0) colors.TextPrimary else colors.ButtonDisabledText)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onPrev,
                enabled = state.analysisMoveIndex > 0
            ) {
                Text("\u25C0", fontSize = 24.sp, color = if (state.analysisMoveIndex > 0) colors.TextPrimary else colors.ButtonDisabledText)
            }

            Spacer(Modifier.width(24.dp))

            Text(
                text = "${state.analysisMoveIndex}/${state.analysisMoves.size}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.TextPrimary
            )

            Spacer(Modifier.width(24.dp))

            IconButton(
                onClick = onNext,
                enabled = state.analysisMoveIndex < state.analysisMoves.size
            ) {
                Text("\u25B6", fontSize = 24.sp, color = if (state.analysisMoveIndex < state.analysisMoves.size) colors.TextPrimary else colors.ButtonDisabledText)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { /* jump to last */ },
                enabled = state.analysisMoveIndex < state.analysisMoves.size
            ) {
                Text("\u23ED", fontSize = 20.sp, color = if (state.analysisMoveIndex < state.analysisMoves.size) colors.TextPrimary else colors.ButtonDisabledText)
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
private fun PlayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDanger: Boolean = false
) {
    val colors = BadukNextColors
    val bg = when {
        !enabled -> colors.ButtonDisabled
        isDanger -> colors.Danger
        else -> colors.Accent
    }
    val txtColor = if (enabled) colors.TextOnAccent else colors.ButtonDisabledText

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .shadow(if (enabled) 1.dp else 0.dp, RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = txtColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ──────────────────────────────────────────────
// Dialogs
// ──────────────────────────────────────────────
@Composable
private fun NewGameDialog(
    onDismiss: () -> Unit,
    onStartGame: (StoneColor, Int) -> Unit
) {
    val colors = BadukNextColors
    var selectedColor by remember { mutableStateOf(StoneColor.BLACK) }
    var selectedSize by remember { mutableIntStateOf(19) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.Surface,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("New Game", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                Spacer(Modifier.height(16.dp))

                Text("Play as", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColorOption(color = StoneColor.BLACK, label = "Black", selected = selectedColor == StoneColor.BLACK, onClick = { selectedColor = StoneColor.BLACK })
                    ColorOption(color = StoneColor.WHITE, label = "White", selected = selectedColor == StoneColor.WHITE, onClick = { selectedColor = StoneColor.WHITE })
                }

                Spacer(Modifier.height(16.dp))
                Text("Board size", color = colors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(9, 13, 19).forEach { size ->
                        SizeOption(size = size, selected = selectedSize == size, onClick = { selectedSize = size })
                    }
                }

                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)).background(colors.Accent)
                        .clickable { onStartGame(selectedColor, selectedSize) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Start", color = colors.TextOnAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
    onDismiss: () -> Unit,
    onToggleCoordinates: () -> Unit,
    onToggleSound: () -> Unit,
    onSetTheme: (GameTheme) -> Unit,
    onSetPlacementMode: (PlacementMode) -> Unit
) {
    val colors = BadukNextColors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = colors.Surface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                Spacer(Modifier.height(16.dp))

                ToggleRow("Coordinates", "Show board letters and numbers", showCoordinates, onToggleCoordinates)
                Spacer(Modifier.height(12.dp))
                ToggleRow("Sound", "Stone placement sounds", soundEnabled, onToggleSound)
                Spacer(Modifier.height(12.dp))

                // ── Placement mode ──
                Text("Placement", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                PlacementMode.entries.forEach { mode ->
                    SettingsRadioOption(
                        label = mode.displayName,
                        selected = mode == currentPlacementMode,
                        onClick = { onSetPlacementMode(mode) }
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(14.dp))
                Divider(color = colors.Divider)
                Spacer(Modifier.height(14.dp))

                Text("Theme", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                GameTheme.entries.forEach { theme ->
                    SettingsRadioOption(
                        label = theme.displayName,
                        selected = theme == currentTheme,
                        onClick = { onSetTheme(theme) }
                    )
                    Spacer(Modifier.height(4.dp))
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
