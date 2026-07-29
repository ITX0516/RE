package com.badukai.next.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
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
    onTerritoryEstimate: () -> Unit,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
    onStartNewGame: (StoneColor, Int) -> Unit,
    onDismissNewGame: () -> Unit,
    onShowModelSelector: () -> Unit,
    onSelectModel: (KataGoEngine.Model) -> Unit,
    onDismissModelSelector: () -> Unit,
    onShowSettings: () -> Unit,
    onDismissSettings: () -> Unit,
    onDismissTerritory: () -> Unit,
    onToggleCoordinates: () -> Unit,
    onToggleSound: () -> Unit,
    onSetTheme: (GameTheme) -> Unit,
    onSetGameMode: (GameMode) -> Unit,
    onSetPlacementMode: (PlacementMode) -> Unit,
    onAnalysisPrev: () -> Unit,
    onAnalysisNext: () -> Unit,
    onConfirmMove: () -> Unit,
    onCancelMove: () -> Unit,
    onSetPlaceSound: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.currentTheme) { BadukNextColors.setTheme(state.currentTheme) }

    val colors = BadukNextColors

    Column(
        modifier = modifier.fillMaxSize().background(colors.Background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top bar: New game / Mode toggle / Settings
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.Surface,
            shadowElevation = 0.5.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNewGame, modifier = Modifier.size(36.dp)) {
                    Text("\u2795", fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.SurfaceVariant),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GameMode.entries.forEach { mode ->
                        val selected = mode == state.gameMode
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) colors.Accent else Color.Transparent)
                                .clickable { onSetGameMode(mode) }
                                .padding(horizontal = 20.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                mode.displayName,
                                color = if (selected) colors.TextOnAccent else colors.TextSecondary,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                IconButton(onClick = onShowSettings, modifier = Modifier.size(36.dp)) {
                    Text("\u2699", fontSize = 16.sp, color = colors.TextSecondary)
                }
            }
        }

        // 2. Game info: captures + current player
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GameInfoChip("Black", state.capturedByBlack, state.currentPlayer == StoneColor.BLACK)
            Text(
                "${state.boardSize}\u00D7${state.boardSize}",
                color = colors.TextSecondary, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            GameInfoChip("White", state.capturedByWhite, state.currentPlayer == StoneColor.WHITE)
        }

        // 3. Win rate bar (simple capture ratio placeholder)
        Box(
            modifier = Modifier.fillMaxWidth().height(6.dp).padding(horizontal = 12.dp).clip(RoundedCornerShape(3.dp)).background(colors.Divider)
        ) {
            val total = state.capturedByBlack + state.capturedByWhite
            if (total > 0) {
                val ratio = state.capturedByBlack.toFloat() / total
                Box(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(ratio.coerceIn(0.05f, 0.95f))
                        .clip(RoundedCornerShape(3.dp)).background(colors.Accent)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // 4. Board
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp
            ) {
                GoBoard(
                    board = state.board, lastMovePoint = state.lastMovePoint,
                    onIntersectionTap = onBoardTap, enabled = state.isEngineReady && !state.isThinking,
                    showCoordinates = state.showCoordinates,
                    pendingDot = state.pendingTap
                )
            }
        }

        // 5. Bottom area
        if (state.gameMode == GameMode.ANALYZE) {
            AnalysisFooter(state, onAnalysisPrev, onAnalysisNext)
        } else {
            PlayFooter(state, onPass, onResign, onTerritoryEstimate, onUndo, onConfirmMove, onCancelMove)
        }

        Spacer(Modifier.height(6.dp))
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
            onSetPlaceSound = onSetPlaceSound
        )
    }
    if (state.showTerritoryDialog) {
        TerritoryDialog(
            result = state.territoryResult,
            onDismiss = onDismissTerritory
        )
    }
}

// ──────────────────────────────────────────────
// Mode Tabs
// ──────────────────────────────────────────────
// ── New Footer composables ──
@Composable
private fun GameInfoChip(label: String, captured: Int, isCurrent: Boolean) {
    val colors = BadukNextColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(
            if (label == "Black") colors.BlackStone else colors.WhiteStone
        ).then(
            if (label == "White") Modifier.border(0.5.dp, colors.WhiteStoneBorder, CircleShape) else Modifier
        ))
        Spacer(Modifier.width(6.dp))
        Text("\u00D7$captured", color = if (isCurrent) colors.Accent else colors.TextSecondary, fontSize = 13.sp, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun PlayFooter(
    state: GameState, onPass: () -> Unit, onResign: () -> Unit,
    onTerritoryEstimate: () -> Unit, onUndo: () -> Unit,
    onConfirmMove: () -> Unit, onCancelMove: () -> Unit
) {
    val colors = BadukNextColors
    Box(modifier = Modifier.height(36.dp).fillMaxWidth().padding(horizontal = 10.dp)) {
        if (state.placementMode == PlacementMode.CONFIRM && state.confirmMoveQueued != null) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.Surface).border(1.dp, colors.Divider, RoundedCornerShape(8.dp)).clickable(onClick = onCancelMove).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text("Cancel", color = colors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.Accent).clickable(onClick = onConfirmMove).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text("Place Here", color = colors.TextOnAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PlayButton("Territory", onTerritoryEstimate, Modifier.weight(1f), state.isEngineReady)
        PlayButton("Undo", onUndo, Modifier.weight(1f), state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() >= 2)
        PlayButton("Pass", onPass, Modifier.weight(1f), state.isPlayerTurn && !state.isThinking && state.isEngineReady)
        PlayButton("Resign", onResign, Modifier.weight(1f), state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() > 0, isDanger = true)
    }
}

@Composable
private fun AnalysisFooter(state: GameState, onPrev: () -> Unit, onNext: () -> Unit) {
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
            AnalysisTab.MOVE_TREE -> MoveTreeContent(state)
            AnalysisTab.CHART -> ChartPlaceholder()
            AnalysisTab.CANDIDATES -> CandidatesPlaceholder()
        }
    }
}

@Composable
private fun MoveTreeContent(state: GameState) {
    val colors = BadukNextColors
    if (state.analysisMoves.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No moves recorded", color = colors.TextSecondary, fontSize = 11.sp)
        }
        return
    }
    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize().horizontalScroll(scrollState).padding(6.dp)) {
        Column {
            val perRow = 10
            val rows = (state.analysisMoves.size + perRow - 1) / perRow
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val start = row * perRow
                    val end = minOf(start + perRow, state.analysisMoves.size)
                    for (idx in start until end) {
                        val isSelected = idx == state.analysisMoveIndex - 1
                        Box(Modifier.size(22.dp).clip(RoundedCornerShape(3.dp)).background(if (isSelected) colors.Accent else colors.SurfaceVariant).padding(1.dp), contentAlignment = Alignment.Center) {
                            Text("${idx + 1}", fontSize = 9.sp, color = if (isSelected) colors.TextOnAccent else colors.TextSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun ChartPlaceholder() {
    val colors = BadukNextColors
    val lineColor = colors.Divider
    Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
        val w = size.width; val h = size.height; val pad = 25f
        for (i in 0..4) { val y = pad + (h - 2 * pad) * (1f - i / 4f); drawLine(lineColor, start = Offset(pad, y), end = Offset(w - pad, y), strokeWidth = 0.5f) }
        drawLine(lineColor, start = Offset(pad, pad), end = Offset(pad, h - pad), strokeWidth = 1f)
        drawLine(lineColor, start = Offset(pad, h - pad), end = Offset(w - pad, h - pad), strokeWidth = 1f)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Analysis required", color = colors.TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun CandidatesPlaceholder() {
    val colors = BadukNextColors
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Top candidate moves", color = colors.TextSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Text("No AI analysis yet", color = colors.ButtonDisabledText, fontSize = 11.sp)
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
    placeSoundIndex: Int,
    onDismiss: () -> Unit,
    onToggleCoordinates: () -> Unit,
    onToggleSound: () -> Unit,
    onSetTheme: (GameTheme) -> Unit,
    onSetPlacementMode: (PlacementMode) -> Unit,
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

@Composable
private fun TerritoryDialog(result: String, onDismiss: () -> Unit) {
    val colors = BadukNextColors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = colors.Surface, shadowElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Territory Estimate", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.TextPrimary)
                Spacer(Modifier.height(14.dp))
                Text(result, color = colors.TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)).background(colors.Accent)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Close", color = colors.TextOnAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
