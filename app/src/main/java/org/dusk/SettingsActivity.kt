package org.dusk

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {
    private lateinit var prefs: DimPrefs
    private lateinit var enabled: Switch
    private lateinit var enabledStatus: TextView
    private lateinit var level: SeekBar
    private lateinit var levelLabel: TextView
    private lateinit var preview: View
    private lateinit var overlayStatus: TextView
    private lateinit var overlayTrailing: ImageView
    private lateinit var batteryStatus: TextView
    private lateinit var batteryTrailing: ImageView
    private lateinit var tileStatus: TextView
    private lateinit var tileTrailing: ImageView

    // Keeps the screen in sync when the tile or notification changes the state.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = DimPrefs(this)

        enabled = findViewById(R.id.enabled)
        enabledStatus = findViewById(R.id.enabled_status)
        level = findViewById(R.id.level)
        levelLabel = findViewById(R.id.level_label)
        preview = findViewById(R.id.preview)
        overlayStatus = findViewById(R.id.overlay_status)
        overlayTrailing = findViewById(R.id.overlay_trailing)
        batteryStatus = findViewById(R.id.battery_status)
        batteryTrailing = findViewById(R.id.battery_trailing)
        tileStatus = findViewById(R.id.tile_status)
        tileTrailing = findViewById(R.id.tile_trailing)

        // Edge-to-edge is enforced on recent SDKs; keep content clear of the system bars.
        val scroll = findViewById<View>(R.id.scroll)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            }
            insets
        }

        // Keep row ripples inside the rounded cards.
        findViewById<View>(R.id.dimmer_card).clipToOutline = true
        findViewById<View>(R.id.setup_card).clipToOutline = true

        level.max = prefs.maxLevel
        if (prefs.maxLevel < DimLevel.MAX) {
            findViewById<TextView>(R.id.cap_note).apply {
                text = getString(R.string.dim_cap_note, prefs.maxLevel)
                visibility = View.VISIBLE
            }
        }

        enabled.setOnCheckedChangeListener { button, checked ->
            if (!button.isPressed) return@setOnCheckedChangeListener
            if (checked) turnOn() else Dimmer.turnOff(this)
        }
        // The whole row toggles, not just the switch.
        findViewById<View>(R.id.enabled_row).setOnClickListener {
            when {
                !Dimmer.canDraw(this) -> openOverlaySettings()
                prefs.enabled -> Dimmer.turnOff(this)
                else -> turnOn()
            }
        }

        level.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                // Writing the pref updates a running overlay live (DimmerService listens).
                prefs.level = progress
                if (!prefs.enabled) preview.alpha = DimLevel.toAlpha(progress, prefs.maxLevel)
            }

            override fun onStartTrackingTouch(bar: SeekBar) {
                if (!prefs.enabled) {
                    preview.alpha = DimLevel.toAlpha(bar.progress, prefs.maxLevel)
                    preview.visibility = View.VISIBLE
                }
            }

            override fun onStopTrackingTouch(bar: SeekBar) {
                preview.visibility = View.GONE
            }
        })

        findViewById<View>(R.id.overlay_row).setOnClickListener { openOverlaySettings() }
        findViewById<View>(R.id.battery_row).setOnClickListener { openBatterySettings() }
        findViewById<View>(R.id.tile_row).setOnClickListener { requestTile() }
    }

    override fun onResume() {
        super.onResume()
        prefs.prefs.registerOnSharedPreferenceChangeListener(listener)
        render()
    }

    override fun onPause() {
        prefs.prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onPause()
    }

    private fun render() {
        val canDraw = Dimmer.canDraw(this)
        val on = prefs.enabled && canDraw
        enabled.isEnabled = canDraw
        enabled.isChecked = on
        enabledStatus.text = when {
            !canDraw -> getString(R.string.dimmer_needs_overlay)
            on -> getString(R.string.dimmer_on, prefs.level)
            else -> getString(R.string.dimmer_off)
        }
        if (level.progress != prefs.level) level.progress = prefs.level
        levelLabel.text = getString(R.string.dim_level_value, prefs.level)

        overlayStatus.setText(if (canDraw) R.string.overlay_granted else R.string.overlay_needed)
        showDone(overlayTrailing, canDraw)

        val unrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        batteryStatus.setText(if (unrestricted) R.string.battery_ok else R.string.battery_hint)
        showDone(batteryTrailing, unrestricted)

        val canRequestTile = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        tileStatus.setText(
            when {
                prefs.tileAdded -> R.string.tile_added
                canRequestTile -> R.string.tile_add
                else -> R.string.tile_manual
            },
        )
        showDone(tileTrailing, prefs.tileAdded)
        tileTrailing.visibility = if (prefs.tileAdded || canRequestTile) View.VISIBLE else View.GONE
    }

    /** Trailing icon: a check when the step is done, a chevron when tapping will do something. */
    private fun showDone(view: ImageView, done: Boolean) {
        view.setImageResource(if (done) R.drawable.ic_check else R.drawable.ic_chevron)
        view.imageTintList = getColorStateList(if (done) R.color.dusk_ok else R.color.dusk_text_secondary)
    }

    private fun turnOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // The notification carries the −/+/Off controls; the dimmer works without it.
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        Dimmer.turnOn(this)
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun openBatterySettings() {
        val intents = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            // ColorOS keeps "Allow background activity" under App info → Battery.
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    private fun requestTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || prefs.tileAdded) return
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, DimTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_dusk),
            mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                prefs.tileAdded = true
            }
        }
    }
}
