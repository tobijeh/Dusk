package org.dusk

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {
    private lateinit var prefs: DimPrefs
    private lateinit var enabled: Switch
    private lateinit var level: SeekBar
    private lateinit var levelLabel: TextView
    private lateinit var preview: View
    private lateinit var overlayStatus: TextView
    private lateinit var grantOverlay: Button
    private lateinit var batteryStatus: TextView

    // Keeps the screen in sync when the tile or notification changes the state.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = DimPrefs(this)

        enabled = findViewById(R.id.enabled)
        level = findViewById(R.id.level)
        levelLabel = findViewById(R.id.level_label)
        preview = findViewById(R.id.preview)
        overlayStatus = findViewById(R.id.overlay_status)
        grantOverlay = findViewById(R.id.grant_overlay)
        batteryStatus = findViewById(R.id.battery_status)

        // Edge-to-edge is enforced on recent SDKs; keep content clear of the system bars.
        val scroll = findViewById<View>(R.id.scroll)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            }
            insets
        }

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

        grantOverlay.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
        }

        findViewById<Button>(R.id.battery).setOnClickListener { openBatterySettings() }
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
        enabled.isEnabled = canDraw
        enabled.isChecked = prefs.enabled && canDraw
        if (level.progress != prefs.level) level.progress = prefs.level
        levelLabel.text = getString(R.string.dim_level, prefs.level)

        overlayStatus.setText(if (canDraw) R.string.overlay_granted else R.string.overlay_needed)
        grantOverlay.visibility = if (canDraw) View.GONE else View.VISIBLE

        val power = getSystemService(PowerManager::class.java)
        batteryStatus.setText(
            if (power.isIgnoringBatteryOptimizations(packageName)) R.string.battery_ok else R.string.battery_hint,
        )
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
}
