package org.dusk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.TileService

/** The one place that turns the dimmer on and off. */
object Dimmer {
    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Starts the overlay service. Returns false when the system refused a background start,
     * so the caller can retry from an activity.
     */
    fun turnOn(context: Context): Boolean {
        DimPrefs(context).enabled = true
        return try {
            context.startForegroundService(Intent(context, DimmerService::class.java))
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+) is an IllegalStateException.
            false
        } finally {
            refreshTile(context)
        }
    }

    fun turnOff(context: Context) {
        DimPrefs(context).enabled = false
        context.stopService(Intent(context, DimmerService::class.java))
        refreshTile(context)
    }

    fun refreshTile(context: Context) {
        TileService.requestListeningState(context, ComponentName(context, DimTileService::class.java))
    }
}
