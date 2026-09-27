package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ColorPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val upColorPreset: ColorPreset = ColorPreset.YELLOW,
    val downColorPreset: ColorPreset = ColorPreset.GREEN,
    val thresholdPercent: Float = 5.0f,
    val swipeDistanceRatio: Float = 0.50f, // 0.20 to 0.80 of screen height
    val swipeDurationMs: Long = 250L,       // 150ms to 600ms
    val cooldownSeconds: Float = 1.8f,     // 1.0s to 3.0s
    val isDetectionActive: Boolean = true,
    val isFloatingOverlayEnabled: Boolean = false
)

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    fun getSettings(): AppSettings = _settingsFlow.value

    private fun loadSettings(): AppSettings {
        val upColorId = prefs.getString(KEY_UP_COLOR, ColorPreset.YELLOW.id) ?: ColorPreset.YELLOW.id
        val downColorId = prefs.getString(KEY_DOWN_COLOR, ColorPreset.GREEN.id) ?: ColorPreset.GREEN.id
        val threshold = prefs.getFloat(KEY_THRESHOLD, 5.0f)
        val distance = prefs.getFloat(KEY_DISTANCE, 0.50f)
        val duration = prefs.getLong(KEY_DURATION, 250L)
        val cooldown = prefs.getFloat(KEY_COOLDOWN, 1.8f)
        val detectionActive = prefs.getBoolean(KEY_DETECTION_ACTIVE, true)
        val floatingOverlay = prefs.getBoolean(KEY_FLOATING_OVERLAY, false)

        return AppSettings(
            upColorPreset = ColorPreset.fromId(upColorId, ColorPreset.YELLOW),
            downColorPreset = ColorPreset.fromId(downColorId, ColorPreset.GREEN),
            thresholdPercent = threshold.coerceIn(1.0f, 20.0f),
            swipeDistanceRatio = distance.coerceIn(0.20f, 0.80f),
            swipeDurationMs = duration.coerceIn(100L, 800L),
            cooldownSeconds = cooldown.coerceIn(0.8f, 3.5f),
            isDetectionActive = detectionActive,
            isFloatingOverlayEnabled = floatingOverlay
        )
    }

    fun updateUpColor(preset: ColorPreset) {
        prefs.edit().putString(KEY_UP_COLOR, preset.id).apply()
        _settingsFlow.value = _settingsFlow.value.copy(upColorPreset = preset)
    }

    fun updateDownColor(preset: ColorPreset) {
        prefs.edit().putString(KEY_DOWN_COLOR, preset.id).apply()
        _settingsFlow.value = _settingsFlow.value.copy(downColorPreset = preset)
    }

    fun updateThreshold(threshold: Float) {
        val clamped = threshold.coerceIn(1.0f, 20.0f)
        prefs.edit().putFloat(KEY_THRESHOLD, clamped).apply()
        _settingsFlow.value = _settingsFlow.value.copy(thresholdPercent = clamped)
    }

    fun updateDistance(distanceRatio: Float) {
        val clamped = distanceRatio.coerceIn(0.20f, 0.80f)
        prefs.edit().putFloat(KEY_DISTANCE, clamped).apply()
        _settingsFlow.value = _settingsFlow.value.copy(swipeDistanceRatio = clamped)
    }

    fun updateDuration(durationMs: Long) {
        val clamped = durationMs.coerceIn(100L, 800L)
        prefs.edit().putLong(KEY_DURATION, clamped).apply()
        _settingsFlow.value = _settingsFlow.value.copy(swipeDurationMs = clamped)
    }

    fun updateCooldown(cooldownSec: Float) {
        val clamped = cooldownSec.coerceIn(0.8f, 3.5f)
        prefs.edit().putFloat(KEY_COOLDOWN, clamped).apply()
        _settingsFlow.value = _settingsFlow.value.copy(cooldownSeconds = clamped)
    }

    fun updateDetectionActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_DETECTION_ACTIVE, active).apply()
        _settingsFlow.value = _settingsFlow.value.copy(isDetectionActive = active)
    }

    fun updateFloatingOverlay(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_OVERLAY, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(isFloatingOverlayEnabled = enabled)
    }

    companion object {
        private const val PREFS_NAME = "color_scroll_preferences"
        private const val KEY_UP_COLOR = "key_up_color"
        private const val KEY_DOWN_COLOR = "key_down_color"
        private const val KEY_THRESHOLD = "key_threshold"
        private const val KEY_DISTANCE = "key_distance"
        private const val KEY_DURATION = "key_duration"
        private const val KEY_COOLDOWN = "key_cooldown"
        private const val KEY_DETECTION_ACTIVE = "key_detection_active"
        private const val KEY_FLOATING_OVERLAY = "key_floating_overlay"

        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
