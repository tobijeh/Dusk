package org.dusk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by the [AutoOff] alarm: turns the dimmer off. */
class AutoOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The alarm only exists for the stored off time, but it can arrive a little early.
        if (!AutoOff.turnOffIfDue(context) && DimPrefs(context).enabled) {
            AutoOff.restore(context)
        }
    }
}
