package org.dusk

import android.app.Activity
import android.os.Bundle

/** No UI. Starts the dimmer from the foreground when the tile couldn't start it directly. */
class StartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Dimmer.turnOn(this)
        finish()
    }
}
