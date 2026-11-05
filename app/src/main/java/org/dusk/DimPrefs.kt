package org.dusk

import android.content.Context
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.os.Build

/** Persisted dimmer state: on/off and level. */
class DimPrefs(context: Context) {
    private val app = context.applicationContext
    val prefs: SharedPreferences = app.getSharedPreferences("dusk", Context.MODE_PRIVATE)

    /** Highest level this phone allows without blocking touches. */
    val maxLevel: Int = DimLevel.maxFor(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(InputManager::class.java)?.maximumObscuringOpacityForTouch
        } else {
            null
        }
    )

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var level: Int
        get() = DimLevel.clamp(prefs.getInt(KEY_LEVEL, DEFAULT_LEVEL), maxLevel)
        set(value) = prefs.edit().putInt(KEY_LEVEL, DimLevel.clamp(value, maxLevel)).apply()

    /** Whether the Quick Settings tile is in the panel (set by [DimTileService]). */
    var tileAdded: Boolean
        get() = prefs.getBoolean(KEY_TILE_ADDED, false)
        set(value) = prefs.edit().putBoolean(KEY_TILE_ADDED, value).apply()

    /** Whether the dimmer turns itself off at [autoOffMinute]. */
    var autoOff: Boolean
        get() = prefs.getBoolean(KEY_AUTO_OFF, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_OFF, value).apply()

    /** Auto-off time as minutes after midnight. */
    var autoOffMinute: Int
        get() = prefs.getInt(KEY_AUTO_OFF_MINUTE, DEFAULT_AUTO_OFF_MINUTE).coerceIn(0, 24 * 60 - 1)
        set(value) = prefs.edit().putInt(KEY_AUTO_OFF_MINUTE, value.coerceIn(0, 24 * 60 - 1)).apply()

    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_AUTO_OFF = "auto_off"
        const val KEY_AUTO_OFF_MINUTE = "auto_off_minute"
        const val DEFAULT_AUTO_OFF_MINUTE = 7 * 60
        const val KEY_TILE_ADDED = "tile_added"
        const val KEY_LEVEL = "level"
        const val DEFAULT_LEVEL = 50
    }
}
