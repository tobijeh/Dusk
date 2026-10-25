package org.dusk

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: tap toggles the dimmer, long-press opens [SettingsActivity]. */
class DimTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        DimPrefs(this).tileAdded = true
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        DimPrefs(this).tileAdded = false
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = DimPrefs(this)
        when {
            prefs.enabled -> Dimmer.turnOff(this)
            !Dimmer.canDraw(this) -> launch(SettingsActivity::class.java)
            !Dimmer.turnOn(this) -> launch(StartActivity::class.java)
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val prefs = DimPrefs(this)
        tile.state = if (prefs.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (prefs.enabled) "${prefs.level}%" else null
        }
        tile.updateTile()
    }

    // The Intent overload is only called below Android 14, where it is still supported.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launch(activity: Class<*>) {
        val intent = Intent(this, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
