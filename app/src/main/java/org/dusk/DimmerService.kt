package org.dusk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** Foreground service that owns the black overlay and the persistent notification. */
class DimmerService : Service() {
    private lateinit var prefs: DimPrefs
    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null

    // Held as a field: SharedPreferences keeps listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == DimPrefs.KEY_LEVEL) applyLevel()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = DimPrefs(this)
        windowManager = getSystemService(WindowManager::class.java)
        createChannel()
        prefs.prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must come first on every start, even one that ends in stopSelf.
        startInForeground()
        when (intent?.action) {
            ACTION_UP -> prefs.level = DimLevel.stepUp(prefs.level, prefs.maxLevel)
            ACTION_DOWN -> prefs.level = DimLevel.stepDown(prefs.level, prefs.maxLevel)
            ACTION_OFF -> {
                Dimmer.turnOff(this)
                return START_NOT_STICKY
            }
        }
        // A sticky restart (null intent) after the process was killed: honour the saved state.
        if (!prefs.enabled || !Dimmer.canDraw(this)) {
            prefs.enabled = false
            Dimmer.refreshTile(this)
            stopSelf()
            return START_NOT_STICKY
        }
        showOverlay()
        Dimmer.refreshTile(this)
        return START_STICKY
    }

    override fun onDestroy() {
        prefs.prefs.unregisterOnSharedPreferenceChangeListener(listener)
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (overlay != null) {
            applyLevel()
            return
        }
        // A square as big as the longest screen side covers status and nav bars in any rotation.
        val side = longestScreenSide()
        val p = WindowManager.LayoutParams(
            side,
            side,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // The window's own alpha is what Android 12+ checks before passing touches through,
            // so the view is solid black and the window carries the dim level.
            alpha = DimLevel.toAlpha(prefs.level, prefs.maxLevel)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
        }
        val view = View(this).apply { setBackgroundColor(Color.BLACK) }
        windowManager.addView(view, p)
        overlay = view
        params = p
    }

    private fun applyLevel() {
        val view = overlay ?: return
        val p = params ?: return
        p.alpha = DimLevel.toAlpha(prefs.level, prefs.maxLevel)
        runCatching { windowManager.updateViewLayout(view, p) }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun longestScreenSide(): Int {
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
        return maxOf(w, h) + 200 // margin for rounded corners and odd insets
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dusk)
            .setContentTitle(getString(R.string.notif_title, prefs.level))
            .setContentText(getString(R.string.notif_text))
            .setColor(getColor(R.color.dusk_accent))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(ACTION_DOWN, R.string.action_down, 1))
            .addAction(action(ACTION_UP, R.string.action_up, 2))
            .addAction(action(ACTION_OFF, R.string.action_off, 3))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
    }

    private fun action(action: String, label: Int, requestCode: Int): Notification.Action {
        val intent = Intent(this, DimmerService::class.java).setAction(action)
        val pending = PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Action.Builder(null, getString(label), pending).build()
    }

    companion object {
        private const val CHANNEL_ID = "dimmer"
        private const val NOTIFICATION_ID = 1
        const val ACTION_UP = "org.dusk.UP"
        const val ACTION_DOWN = "org.dusk.DOWN"
        const val ACTION_OFF = "org.dusk.OFF"

    }
}
