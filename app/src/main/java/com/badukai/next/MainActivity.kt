package com.badukai.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.badukai.next.game.GameViewModel
import com.badukai.next.ui.GameScreen

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        viewModel.initialize(applicationContext)

        setContent {
            val state by viewModel.state.collectAsState()

            GameScreen(
                state = state,
                onBoardTap = viewModel::onBoardTap,
                onPass = viewModel::pass,
                onResign = viewModel::resign,
                onUndo = viewModel::undo,
                onNewGame = viewModel::showNewGameDialog,
                onStartNewGame = viewModel::startNewGame,
                onDismissNewGame = viewModel::hideNewGameDialog,
                onShowModelSelector = viewModel::showModelSelector,
                onSelectModel = viewModel::selectModel,
                onDismissModelSelector = viewModel::hideModelSelector,
                onShowSettings = viewModel::showSettingsDialog,
                onDismissSettings = viewModel::hideSettingsDialog,
                onToggleCoordinates = viewModel::toggleCoordinates,
                onToggleSound = viewModel::toggleSound,
                onSetTheme = viewModel::setTheme,
                onSetGameMode = viewModel::setGameMode,
                onSetPlacementMode = viewModel::setPlacementMode,
                onAnalysisPrev = viewModel::analysisPrev,
                onAnalysisNext = viewModel::analysisNext,
                onConfirmMove = viewModel::confirmMove,
                onCancelMove = viewModel::cancelMove,
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (!viewModel.state.value.isEngineReady && !viewModel.state.value.isEngineStarting) {
            viewModel.startEngine()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            viewModel.stopEngine()
        }
    }
}
