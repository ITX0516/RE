package com.badukai.next.game

import android.content.Context
import android.content.SharedPreferences
import com.badukai.next.engine.ModelSource
import com.badukai.next.ui.GameTheme

/**
 * Persists settings to SharedPreferences.
 *
 * 2026-08-02 ADDED AI-weight fields:
 *   aiModelSource            → BUNDLED_ASSET (默认离线内置 6b) / DOWNLOADED / CUSTOM
 *   customModelPath          → filesDir/models/custom/* 绝对路径（app 私有，卸载即走）
 *   customModelDisplayName   → 设置对话框里显示的短文件名
 *   resetModelSourceToBundled() → 用户点"恢复默认内置"一键还原
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("baduknext_settings", Context.MODE_PRIVATE)

    var showCoordinates: Boolean
        get() = prefs.getBoolean("show_coordinates", true)
        set(v) = prefs.edit().putBoolean("show_coordinates", v).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)
        set(v) = prefs.edit().putBoolean("sound_enabled", v).apply()

    var placeSoundIndex: Int
        get() = prefs.getInt("place_sound_index", 0)
        set(v) = prefs.edit().putInt("place_sound_index", v).apply()

    var currentTheme: GameTheme
        get() = try { GameTheme.valueOf(prefs.getString("current_theme", GameTheme.WARM_LIGHT.name) ?: GameTheme.WARM_LIGHT.name) }
        catch (_: Exception) { GameTheme.WARM_LIGHT }
        set(v) = prefs.edit().putString("current_theme", v.name).apply()

    var placementMode: PlacementMode
        get() = try { PlacementMode.valueOf(prefs.getString("placement_mode", PlacementMode.TAP.name) ?: PlacementMode.TAP.name) }
        catch (_: Exception) { PlacementMode.TAP }
        set(v) = prefs.edit().putString("placement_mode", v.name).apply()
    var stoneAnimation: StoneAnimation
        get() = try { StoneAnimation.valueOf(prefs.getString("stone_animation", StoneAnimation.FADE_IN.name) ?: StoneAnimation.FADE_IN.name) }
        catch (_: Exception) { StoneAnimation.FADE_IN }
        set(v) = prefs.edit().putString("stone_animation", v.name).apply()

    var aiMoveTimeSeconds: Int
        get() = prefs.getInt("ai_move_time_seconds", 20)
        set(v) = prefs.edit().putInt("ai_move_time_seconds", v).apply()

    var aiCanResign: Boolean
        get() = prefs.getBoolean("ai_can_resign", true)
        set(v) = prefs.edit().putBoolean("ai_can_resign", v).apply()

    // --- AI 权重来源 (user request 2026-08-02): 内置 / 下载 / 自定义
    var aiModelSource: ModelSource
        get() = try {
            ModelSource.valueOf(
                prefs.getString("ai_model_source", ModelSource.BUNDLED_ASSET.name)
                    ?: ModelSource.BUNDLED_ASSET.name
            )
        } catch (_: Exception) { ModelSource.BUNDLED_ASSET }
        set(v) = prefs.edit().putString("ai_model_source", v.name).apply()

    var customModelPath: String
        get() = prefs.getString("custom_model_path", "") ?: ""
        set(v) = prefs.edit().putString("custom_model_path", v).apply()

    var customModelDisplayName: String
        get() = prefs.getString("custom_model_display_name", "") ?: ""
        set(v) = prefs.edit().putString("custom_model_display_name", v).apply()

    fun resetModelSourceToBundled() {
        prefs.edit()
            .putString("ai_model_source", ModelSource.BUNDLED_ASSET.name)
            .remove("custom_model_path")
            .remove("custom_model_display_name")
            .apply()
    }
}
