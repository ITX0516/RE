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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar with status
        TopBar(
            state = state,
            onNewGame = onNewGame,
            onModelSelect = onShowModelSelector
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Game board
        GoBoard(
            board = state.board,
            lastMovePoint = state.lastMovePoint,
            onIntersectionTap = onBoardTap,
            enabled = state.isPlayerTurn && !state.isThinking && state.isEngineReady,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Bottom controls
        BottomControls(
            state = state,
            onPass = onPass,
            onResign = onResign,
            onUndo = onUndo
        )
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // New Game button
        MinimalButton(
            text = "New",
            onClick = onNewGame
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
                Text(
                    text = "●",
                    color = BadukNextColors.ThinkingIndicator,
                    fontSize = 12.sp
                )
            }
            
            Text(
                text = state.gameMessage.ifEmpty { 
                    if (state.isEngineReady) "Ready" else "Initializing..."
                },
                color = BadukNextColors.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        
        // Model selector
        MinimalButton(
            text = state.selectedModel.displayName,
            onClick = onModelSelect,
            enabled = !state.isEngineStarting && !state.isThinking
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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Capture info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CaptureDisplay(
                color = StoneColor.BLACK,
                captured = state.capturedByBlack,
                isCurrentPlayer = state.currentPlayer == StoneColor.BLACK
            )
            
            CaptureDisplay(
                color = StoneColor.WHITE,
                captured = state.capturedByWhite,
                isCurrentPlayer = state.currentPlayer == StoneColor.WHITE
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MinimalButton(
                text = "Undo",
                onClick = onUndo,
                enabled = state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() >= 2
            )
            
            MinimalButton(
                text = "Pass",
                onClick = onPass,
                enabled = state.isPlayerTurn && !state.isThinking && state.isEngineReady
            )
            
            MinimalButton(
                text = "Resign",
                onClick = onResign,
                enabled = state.isPlayerTurn && !state.isThinking && state.board.getMoveCount() > 0
            )
        }
    }
}

@Composable
private fun CaptureDisplay(
    color: StoneColor,
    captured: Int,
    isCurrentPlayer: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(
                width = if (isCurrentPlayer) 2.dp else 0.dp,
                color = if (isCurrentPlayer) BadukNextColors.Accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        // Stone indicator
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (color == StoneColor.BLACK) BadukNextColors.BlackStone 
                    else BadukNextColors.WhiteStone
                )
                .then(
                    if (color == StoneColor.WHITE) {
                        Modifier.border(1.dp, BadukNextColors.WhiteStoneBorder, CircleShape)
                    } else Modifier
                )
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "×$captured",
            color = BadukNextColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MinimalButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (enabled) BadukNextColors.AccentLight else BadukNextColors.AccentLight.copy(alpha = 0.5f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = if (enabled) BadukNextColors.TextPrimary else BadukNextColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
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
            shape = RoundedCornerShape(16.dp),
            color = BadukNextColors.Surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "New Game",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BadukNextColors.TextPrimary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Color selection
                Text(
                    text = "Play as",
                    color = BadukNextColors.TextSecondary,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ColorOption(
                        color = StoneColor.BLACK,
                        selected = selectedColor == StoneColor.BLACK,
                        onClick = { selectedColor = StoneColor.BLACK }
                    )
                    ColorOption(
                        color = StoneColor.WHITE,
                        selected = selectedColor == StoneColor.WHITE,
                        onClick = { selectedColor = StoneColor.WHITE }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Board size selection
                Text(
                    text = "Board size",
                    color = BadukNextColors.TextSecondary,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(9, 13, 19).forEach { size ->
                        SizeOption(
                            size = size,
                            selected = selectedSize == size,
                            onClick = { selectedSize = size }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Start button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BadukNextColors.ButtonActive)
                        .clickable { onStartGame(selectedColor, selectedSize) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Start",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorOption(
    color: StoneColor,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) BadukNextColors.Accent else BadukNextColors.AccentLight,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (color == StoneColor.BLACK) BadukNextColors.BlackStone 
                    else BadukNextColors.WhiteStone
                )
                .then(
                    if (color == StoneColor.WHITE) {
                        Modifier.border(1.dp, BadukNextColors.WhiteStoneBorder, CircleShape)
                    } else Modifier
                )
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
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) BadukNextColors.Accent else BadukNextColors.AccentLight
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "${size}×${size}",
            color = if (selected) Color.White else BadukNextColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
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
            shape = RoundedCornerShape(16.dp),
            color = BadukNextColors.Surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AI Model",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BadukNextColors.TextPrimary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Select AI strength",
                    color = BadukNextColors.TextSecondary,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                KataGoEngine.Model.entries.forEach { model ->
                    ModelOption(
                        model = model,
                        selected = model == currentModel,
                        onClick = { onSelectModel(model) }
                    )
                    
                    if (model != KataGoEngine.Model.entries.last()) {
                        Spacer(modifier = Modifier.height(12.dp))
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
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) BadukNextColors.AccentLight else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (selected) BadukNextColors.Accent else BadukNextColors.AccentLight,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = model.displayName,
                color = BadukNextColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = model.description,
                color = BadukNextColors.TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
