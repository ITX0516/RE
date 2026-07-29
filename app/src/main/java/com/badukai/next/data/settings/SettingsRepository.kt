package com.badukai.next.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class UserSettings(
    val theme: String = "WARM_LIGHT",        // GameTheme 枚举名
    val soundEnabled: Boolean = true,
    val showCoordinates: Boolean = true,
    val placementMode: String = "TAP",       // PlacementMode 枚举名
    val placeSoundIndex: Int = 0,
    val selectedModel: String = "HUMAN",     // Model 枚举名
    val boardSize: Int = 19,
    val playerColor: String = "BLACK"        // StoneColor 枚举名
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val SHOW_COORDINATES = booleanPreferencesKey("show_coordinates")
        val PLACEMENT_MODE = stringPreferencesKey("placement_mode")
        val PLACE_SOUND_INDEX = intPreferencesKey("place_sound_index")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val BOARD_SIZE = intPreferencesKey("board_size")
        val PLAYER_COLOR = stringPreferencesKey("player_color")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            theme = prefs[Keys.THEME] ?: "WARM_LIGHT",
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            showCoordinates = prefs[Keys.SHOW_COORDINATES] ?: true,
            placementMode = prefs[Keys.PLACEMENT_MODE] ?: "TAP",
            placeSoundIndex = prefs[Keys.PLACE_SOUND_INDEX] ?: 0,
            selectedModel = prefs[Keys.SELECTED_MODEL] ?: "HUMAN",
            boardSize = prefs[Keys.BOARD_SIZE] ?: 19,
            playerColor = prefs[Keys.PLAYER_COLOR] ?: "BLACK"
        )
    }

    suspend fun saveTheme(theme: String) {
        context.settingsDataStore.edit { it[Keys.THEME] = theme }
    }
    suspend fun saveSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }
    suspend fun saveShowCoordinates(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_COORDINATES] = show }
    }
    suspend fun savePlacementMode(mode: String) {
        context.settingsDataStore.edit { it[Keys.PLACEMENT_MODE] = mode }
    }
    suspend fun savePlaceSoundIndex(idx: Int) {
        context.settingsDataStore.edit { it[Keys.PLACE_SOUND_INDEX] = idx }
    }
    suspend fun saveSelectedModel(model: String) {
        context.settingsDataStore.edit { it[Keys.SELECTED_MODEL] = model }
    }
    suspend fun saveBoardSize(size: Int) {
        context.settingsDataStore.edit { it[Keys.BOARD_SIZE] = size }
    }
    suspend fun savePlayerColor(color: String) {
        context.settingsDataStore.edit { it[Keys.PLAYER_COLOR] = color }
    }
}
