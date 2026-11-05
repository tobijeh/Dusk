package org.dusk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by the [AutoOff] alarm: turns the dimmer off. */
class AutoOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (DimPrefs(context).enabled) Dimmer.turnOff(context)
    }
}
