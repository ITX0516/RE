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
    onShowSettings: () -> Unit,
    onDismissSettings: () -> Unit,
    onToggleCoordinates: () -> Unit,
    onToggleSound: () -> Unit,
    onSetTheme: (GameTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    // Sync theme colors
    LaunchedEffect(state.currentTheme) {
        BadukNextColors.setTheme(state.currentTheme)
    }

    val colors = BadukNextColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.Background)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = colors.SurfaceVariant,
            shadowElevation = 1.dp
        ) {
            TopBar(
                state = state,
                onNewGame = onNewGame,
                onModelSelect = onShowModelSelector,
                onSettings = onShowSettings
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Board area: fixed square based on width, centered in remaining space
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 3.dp
            ) {
                GoBoard(
                    board = state.board,
                    lastMovePoint = state.lastMovePoint,
                    onIntersectionTap = onBoardTap,
                    enabled = state.isPlayerTurn && !state.isThinking && state.isEngineReady,
                    showCoordinates = state.showCoordinates
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom controls card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = colors.SurfaceVariant,
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

    if (state.showNewGameDialog) {
        NewGameDialog(
            onDismiss = onDismissNewGame,
            onStartGame = onStartNewGame
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
            onDismiss = onDismissSettings,
            onToggleCoordinates = onToggleCoordinates,
            onToggleSound = onToggleSound,
            onSetTheme = onSetTheme
        )
    }
}

@Composable
private fun TopBar(
    state: GameState,
    onNewGame: () -> Unit,
    onModelSelect: () -> Unit,
    onSettings: () -> Unit
) {
    val colors = BadukNextColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: New Game button
        AccentButton(
            text = "New",
            onClick = onNewGame,
            small = true
        )

        // Center: Status
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
                        .background(colors.ThinkingBg)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.ThinkingIndicator)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI thinking",
                        color = colors.ThinkingIndicator,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = state.gameMessage.ifEmpty {
                    if (state.isEngineReady) "Ready to play" else "Starting AI\u2026"
                },
                color = colors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            CompositionLocalProvider(LocalContentColor provides colors.TextSecondary) {
                Text(
                    text = "${state.boardSize}\u00D7${state.boardSize}",
                    fontSize = 11.sp
                )
            }
        }

        // Right: Model + Settings buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton(
                text = state.selectedModel.displayName,
                onClick = onModelSelect,
                enabled = !state.isEngineStarting && !state.isThinking,
                small = true
            )

            // Settings icon button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.Surface)
                    .border(1.dp, colors.Divider, CircleShape)
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u2699",
                    fontSize = 16.sp,
                    color = colors.TextSecondary
                )
            }
        }
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
    val colors = BadukNextColors

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrentPlayer) colors.AccentLight else colors.Surface,
        shadowElevation = if (isCurrentPlayer) 1.dp else 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .then(
                    if (isCurrentPlayer) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = colors.Accent,
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        if (color == StoneColor.BLACK) colors.BlackStone
                        else colors.WhiteStone
                    )
                    .then(
                        if (color == StoneColor.WHITE) {
                            Modifier.border(0.8.dp, colors.WhiteStoneBorder, CircleShape)
                        } else Modifier
                    )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(LocalContentColor provides colors.TextSecondary) {
                    Text(
                        text = if (color == StoneColor.BLACK) "Black" else "White",
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "Captured \u00D7$captured",
                    color = colors.TextPrimary,
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
    val colors = BadukNextColors
    val bgColor = if (enabled) colors.ButtonActive else colors.ButtonDisabled
    val textColor = if (enabled) colors.TextOnAccent else colors.ButtonDisabledText
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
    val colors = BadukNextColors
    val bgColor = if (enabled) colors.Surface else colors.ButtonDisabled.copy(alpha = 0.35f)
    val textColor = if (enabled) colors.TextPrimary else colors.ButtonDisabledText
    val borderColor = if (enabled) colors.Divider else Color.Transparent
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
    val colors = BadukNextColors
    val bgColor = if (enabled) colors.Danger else colors.ButtonDisabled
    val textColor = if (enabled) colors.TextOnAccent else colors.ButtonDisabledText
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
    val colors = BadukNextColors
    var selectedColor by remember { mutableStateOf(StoneColor.BLACK) }
    var selectedSize by remember { mutableIntStateOf(19) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.Surface,
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
                    color = colors.TextPrimary
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Play as",
                    color = colors.TextSecondary,
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

                Text(
                    text = "Board size",
                    color = colors.TextSecondary,
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.ButtonActive)
                        .clickable { onStartGame(selectedColor, selectedSize) }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Start Game",
                        color = colors.TextOnAccent,
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
    val colors = BadukNextColors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.AccentLight else colors.SurfaceVariant)
            .border(
                width = if (selected) 1.8.dp else 1.dp,
                color = if (selected) colors.Accent else colors.Divider,
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
                    if (color == StoneColor.BLACK) colors.BlackStone
                    else colors.WhiteStone
                )
                .then(
                    if (color == StoneColor.WHITE) {
                        Modifier.border(0.8.dp, colors.WhiteStoneBorder, CircleShape)
                    } else Modifier
                )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = colors.TextPrimary,
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
    val colors = BadukNextColors

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) colors.Accent else colors.SurfaceVariant
            )
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = colors.Divider,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${size}\u00D7${size}",
            color = if (selected) colors.TextOnAccent else colors.TextPrimary,
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
    val colors = BadukNextColors

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.Surface,
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
                    color = colors.TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Choose your opponent",
                    color = colors.TextSecondary,
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
    val colors = BadukNextColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) colors.AccentLight else colors.SurfaceVariant
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.Accent else colors.Divider,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 6.dp else 1.5.dp,
                        color = if (selected) colors.Accent else colors.TextSecondary,
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.displayName,
                    color = colors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = model.description,
                    color = colors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    showCoordinates: Boolean,
    soundEnabled: Boolean,
    currentTheme: GameTheme,
    onDismiss: () -> Unit,
    onToggleCoordinates: () -> Unit,
    onToggleSound: () -> Unit,
    onSetTheme: (GameTheme) -> Unit
) {
    val colors = BadukNextColors

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.Surface,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                Text(
                    text = "Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.TextPrimary
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Show coordinates toggle
                SettingsRow(
                    title = "Show Coordinates",
                    subtitle = "Display board letters and numbers",
                    checked = showCoordinates,
                    onToggle = onToggleCoordinates
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Sound toggle
                SettingsRow(
                    title = "Sound",
                    subtitle = "Stone placement and capture sounds",
                    checked = soundEnabled,
                    onToggle = onToggleSound
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Theme selector
                HorizontalDivider(color = colors.Divider)

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Theme",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                GameTheme.entries.forEach { theme ->
                    ThemeOption(
                        theme = theme,
                        selected = theme == currentTheme,
                        onClick = { onSetTheme(theme) }
                    )
                    if (theme != GameTheme.entries.last()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // About section
                HorizontalDivider(color = colors.Divider)

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "About",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.TextSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "RE Baduk AI v1.0\nPowered by KataGo",
                    fontSize = 12.sp,
                    color = colors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    theme: GameTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = BadukNextColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) colors.AccentLight else colors.SurfaceVariant
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.Accent else colors.Divider,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 5.dp else 1.5.dp,
                        color = if (selected) colors.Accent else colors.TextSecondary,
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = theme.displayName,
                color = colors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val colors = BadukNextColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = colors.TextSecondary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.TextOnAccent,
                checkedTrackColor = colors.Accent,
                uncheckedThumbColor = colors.Surface,
                uncheckedTrackColor = colors.Divider
            )
        )
    }
}
