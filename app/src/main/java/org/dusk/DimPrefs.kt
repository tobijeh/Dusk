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

    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_TILE_ADDED = "tile_added"
        const val KEY_LEVEL = "level"
        const val DEFAULT_LEVEL = 50
    }
}
