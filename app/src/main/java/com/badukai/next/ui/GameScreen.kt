package com.badukai.next.ui

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.badukai.next.engine.KataGoEngine
import com.badukai.next.game.GameState
import com.badukai.next.game.StoneColor

/**
 * Main game screen composable
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BadukNextColors.Background)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar card with status
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = BadukNextColors.SurfaceVariant,
            shadowElevation = 1.dp
        ) {
            TopBar(
                state = state,
                onNewGame = onNewGame,
                onModelSelect = onShowModelSelector
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Game board with subtle card shadow
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 3.dp
        ) {
            GoBoard(
                board = state.board,
                lastMovePoint = state.lastMovePoint,
                onIntersectionTap = onBoardTap,
                enabled = state.isPlayerTurn && !state.isThinking && state.isEngineReady
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom controls card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = BadukNextColors.SurfaceVariant,
            shadowElevation = 1.dp
        ) {
            BottomControls(
                state = state,
                onPass = onPass,
                onResign = onResign,
                onUndo = onUndo
            )
        }
    }

    // New game dialog
    if (state.showNewGameDialog) {
        NewGameDialog(
            onDismiss = onDismissNewGame,
            onStartGame = onStartNewGame
        )
    }

    // Model selector dialog
    if (state.showModelSelector) {
        ModelSelectorDialog(
            currentModel = state.selectedModel,
            onDismiss = onDismissModelSelector,
            onSelectModel = onSelectModel
        )
    }
}

@Composable
private fun TopBar(
    state: GameState,
    onNewGame: () -> Unit,
    onModelSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // New Game button
        AccentButton(
            text = "New",
            onClick = onNewGame,
            small = true
        )

        // Status / Message
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            AnimatedVisibility(
                visible = state.isThinking,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(BadukNextColors.ThinkingBg)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(BadukNextColors.ThinkingIndicator)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI thinking",
                        color = BadukNextColors.ThinkingIndicator,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = state.gameMessage.ifEmpty {
                    if (state.isEngineReady) "Ready to play" else "Starting AI…"
                },
                color = BadukNextColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            CompositionLocalProvider(LocalContentColor provides BadukNextColors.TextSecondary) {
                Text(
                    text = "${state.boardSize}×${state.boardSize}",
                    fontSize = 11.sp
                )
            }
        }

        // Model selector
        SecondaryButton(
            text = state.selectedModel.displayName,
            onClick = onModelSelect,
            enabled = !state.isEngineStarting && !state.isThinking,
            small = true
        )
    }
}

@Composable
private fun BottomControls(
    state: GameState,
    onPass: () -> Unit,
    onResign: () -> Unit,
    onUndo: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Capture info row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CaptureDisplay(
                modifier = Modifier.weight(1f),
                color = StoneColor.BLACK,
                captured = state.capturedByBlack,
                isCurrentPlayer = state.currentPlayer == StoneColor.BLACK
            )

            CaptureDisplay(
                modifier = Modifier.weight(1f),
                color = StoneColor.WHITE,
                captured = state.capturedByWhite,
                isCurrentPlayer = state.currentPlayer == StoneColor.WHITE
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryButton(
                text = "Undo",
                onClick = onUndo,
                enabled = state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() >= 2,
                modifier = Modifier.weight(1f)
            )

            AccentButton(
                text = "Pass",
                onClick = onPass,
                enabled = state.isPlayerTurn && !state.isThinking && state.isEngineReady,
                modifier = Modifier.weight(1f)
            )

            DangerButton(
                text = "Resign",
                onClick = onResign,
                enabled = state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() > 0,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CaptureDisplay(
    modifier: Modifier = Modifier,
    color: StoneColor,
    captured: Int,
    isCurrentPlayer: Boolean
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrentPlayer) BadukNextColors.AccentLight else BadukNextColors.Surface,
        shadowElevation = if (isCurrentPlayer) 1.dp else 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .then(
                    if (isCurrentPlayer) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = BadukNextColors.Accent,
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Stone indicator with shadow
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        if (color == StoneColor.BLACK) BadukNextColors.BlackStone
                        else BadukNextColors.WhiteStone
                    )
                    .then(
                        if (color == StoneColor.WHITE) {
                            Modifier.border(0.8.dp, BadukNextColors.WhiteStoneBorder, CircleShape)
                        } else Modifier
                    )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(LocalContentColor provides BadukNextColors.TextSecondary) {
                    Text(
                        text = if (color == StoneColor.BLACK) "Black" else "White",
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "Captured ×$captured",
                    color = BadukNextColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false
) {
    val bgColor = if (enabled) BadukNextColors.ButtonActive else BadukNextColors.ButtonDisabled
    val textColor = if (enabled) BadukNextColors.TextOnAccent else BadukNextColors.ButtonDisabledText
    val vPad = if (small) 9.dp else 13.dp
    val fontSize = if (small) 13.sp else 15.sp

    Box(
        modifier = modifier
            .shadow(if (enabled) 1.dp else 0.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = vPad),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false
) {
    val bgColor = if (enabled) BadukNextColors.Surface else BadukNextColors.ButtonDisabled.copy(alpha = 0.35f)
    val textColor = if (enabled) BadukNextColors.TextPrimary else BadukNextColors.ButtonDisabledText
    val borderColor = if (enabled) BadukNextColors.Divider else Color.Transparent
    val vPad = if (small) 9.dp else 13.dp
    val fontSize = if (small) 13.sp else 15.sp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = vPad),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false
) {
    val bgColor = if (enabled) BadukNextColors.Danger else BadukNextColors.ButtonDisabled
    val textColor = if (enabled) BadukNextColors.TextOnAccent else BadukNextColors.ButtonDisabledText
    val vPad = if (small) 9.dp else 13.dp
    val fontSize = if (small) 13.sp else 15.sp

    Box(
        modifier = modifier
            .shadow(if (enabled) 1.dp else 0.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = vPad),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NewGameDialog(
    onDismiss: () -> Unit,
    onStartGame: (StoneColor, Int) -> Unit
) {
    var selectedColor by remember { mutableStateOf(StoneColor.BLACK) }
    var selectedSize by remember { mutableIntStateOf(19) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = BadukNextColors.Surface,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "New Game",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BadukNextColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Color selection
                Text(
                    text = "Play as",
                    color = BadukNextColors.TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ColorOption(
                        color = StoneColor.BLACK,
                        label = "Black",
                        selected = selectedColor == StoneColor.BLACK,
                        onClick = { selectedColor = StoneColor.BLACK }
                    )
                    ColorOption(
                        color = StoneColor.WHITE,
                        label = "White",
                        selected = selectedColor == StoneColor.WHITE,
                        onClick = { selectedColor = StoneColor.WHITE }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Board size selection
                Text(
                    text = "Board size",
                    color = BadukNextColors.TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(9, 13, 19).forEach { size ->
                        SizeOption(
                            size = size,
                            selected = selectedSize == size,
                            onClick = { selectedSize = size }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Start button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(BadukNextColors.ButtonActive)
                        .clickable { onStartGame(selectedColor, selectedSize) }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Start Game",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorOption(
    color: StoneColor,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) BadukNextColors.AccentLight else BadukNextColors.SurfaceVariant)
            .border(
                width = if (selected) 1.8.dp else 1.dp,
                color = if (selected) BadukNextColors.Accent else BadukNextColors.Divider,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    if (color == StoneColor.BLACK) BadukNextColors.BlackStone
                    else BadukNextColors.WhiteStone
                )
                .then(
                    if (color == StoneColor.WHITE) {
                        Modifier.border(0.8.dp, BadukNextColors.WhiteStoneBorder, CircleShape)
                    } else Modifier
                )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = BadukNextColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SizeOption(
    size: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) BadukNextColors.Accent else BadukNextColors.SurfaceVariant
            )
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = BadukNextColors.Divider,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${size}×${size}",
            color = if (selected) Color.White else BadukNextColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ModelSelectorDialog(
    currentModel: KataGoEngine.Model,
    onDismiss: () -> Unit,
    onSelectModel: (KataGoEngine.Model) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = BadukNextColors.Surface,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AI Strength",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BadukNextColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Choose your opponent",
                    color = BadukNextColors.TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                KataGoEngine.Model.entries.forEach { model ->
                    ModelOption(
                        model = model,
                        selected = model == currentModel,
                        onClick = { onSelectModel(model) }
                    )

                    if (model != KataGoEngine.Model.entries.last()) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelOption(
    model: KataGoEngine.Model,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) BadukNextColors.AccentLight else BadukNextColors.SurfaceVariant
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) BadukNextColors.Accent else BadukNextColors.Divider,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Radio indicator
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 6.dp else 1.5.dp,
                        color = if (selected) BadukNextColors.Accent else BadukNextColors.TextSecondary,
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.displayName,
                    color = BadukNextColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = model.description,
                    color = BadukNextColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
