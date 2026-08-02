package com.badukai.next

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.badukai.next.game.GameViewModel
import com.badukai.next.logging.AppLogger
import com.badukai.next.ui.BadukNextTheme
import com.badukai.next.ui.GameScreen

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    companion object {
        private const val TAG = "MainActivity"
    }

    // SAF ACTION_OPEN_DOCUMENT for custom KataGo weight files.
    //
    // MIME filter is intentionally permissive (catch-all MIME is also accepted)
    // because most DocumentsProviders report application/octet-stream for
    // .bin.gz / .txt.gz, so a strict application/gzip filter would miss them.
    // Real validation happens inside ModelManager.importCustomModel():
    //   - size >= 1MB floor
    //   - first two bytes == 0x1f 0x8b (gzip magic)
    //   - failing either -> refuse import with a clear error message.
    private val pickCustomModelLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) {
                AppLogger.w(TAG, "Custom model picker cancelled (uri == null)")
                return@registerForActivityResult
            }
            runCatching {
                // Optional: take persistable READ permission so re-launches work even if
                // the user kills the app (we import into customDir synchronously, so
                // this is belt-and-braces; even if permission is revoked later our
                // already-imported app-private copy still launches fine).
                val readFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                runCatching { contentResolver.takePersistableUriPermission(uri, readFlags) }
            }
            val displayNameHint = documentDisplayName(uri)
            AppLogger.i(TAG, "Custom model picked: uri=$uri, display=$displayNameHint")
            viewModel.onCustomModelPicked(uri, displayNameHint)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        viewModel.initialize(applicationContext)

        setContent {
            val state by viewModel.state.collectAsState()

            BadukNextTheme(theme = state.currentTheme) {
                GameScreen(
                    state = state,
                    onBoardTap = viewModel::onBoardTap,
                    onPass = viewModel::pass,
                    onResign = viewModel::resign,
                    onTerritoryEstimate = viewModel::toggleTerritoryOverlay,
                    onForceEndGame = viewModel::forceEndGame,
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
                    onSetAnimation = viewModel::setStoneAnimation,
                    onToggleEye = viewModel::toggleEyeOverlay,
                    onSetAiMoveTime = viewModel::setAiMoveTime,
                    onSetAiCanResign = viewModel::setAiCanResign,
                    onSetAiModelSource = viewModel::setAiModelSource,
                    onPickCustomModel = { launchCustomModelPicker() },
                    onResetAiModelToBundled = { viewModel.resetAiModelToBundled() },
                    onSaveSgf = viewModel::saveGameAsSgf,
                    onShowSavedGames = viewModel::showSavedGamesDialog,
                    onDismissSavedGames = viewModel::dismissSavedGamesDialog,
                    onLoadSgf = viewModel::loadSavedGame,
                    onDismissCelebration = viewModel::dismissCelebration,
                    onAnalysisPrev = viewModel::analysisPrev,
                    onAnalysisNext = viewModel::analysisNext,
                    onAnalysisJump = viewModel::navigateToMove,
                    onConfirmMove = viewModel::confirmMove,
                    onCancelMove = viewModel::cancelMove,
                    onSetPlaceSound = viewModel::setPlaceSoundIndex,
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                )
            }
        }
    }

    private fun launchCustomModelPicker() {
        runCatching {
            pickCustomModelLauncher.launch(
                arrayOf(
                    "application/gzip",
                    "application/x-gzip",
                    "application/octet-stream",
                    "application/x-tar",
                    "*/*"
                )
            )
        }.onFailure { e ->
            AppLogger.e(TAG, "Failed to launch ACTION_OPEN_DOCUMENT for custom model: ${e.message}", e)
        }
    }

    private fun documentDisplayName(uri: Uri): String? = runCatching {
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } else {
            uri.lastPathSegment
        }
    }.getOrNull()

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
