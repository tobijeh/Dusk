package org.dusk

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

/**
 * Turns the dimmer off at a set time of day, so the screen isn't left too dark to use in the
 * morning.
 *
 * Arming stores the off time (`autoOffAt`). Two things act on it, because phone makers (ColorOS
 * among them) may delay or drop an app's alarms:
 * - an exact alarm, for when the screen is off;
 * - [DimmerService], which checks the clock every minute while the screen is on and whenever it
 *   turns on or unlocks.
 */
object AutoOff {

    /** Next time the clock reads [minuteOfDay] after [nowMillis], in [zone]. Never [nowMillis] itself. */
    fun nextTriggerMillis(nowMillis: Long, minuteOfDay: Int, zone: ZoneId): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var next = now.with(time)
        if (!next.isAfter(now)) next = now.plusDays(1).with(time)
        return next.toInstant().toEpochMilli()
    }

    /** Whether an off time stored as [offAt] (0 = none) has been reached at [nowMillis]. */
    fun isDue(offAt: Long, nowMillis: Long): Boolean = offAt > 0 && nowMillis >= offAt

    /** Sets the off time from the settings (when the dimmer turns on, or the settings change). */
    fun arm(context: Context) {
        val prefs = DimPrefs(context)
        if (!prefs.autoOff) {
            disarm(context)
            return
        }
        val at = nextTriggerMillis(System.currentTimeMillis(), prefs.autoOffMinute, ZoneId.systemDefault())
        prefs.autoOffAt = at
        setAlarm(context, at)
    }

    /** Clears the off time (when the dimmer turns off). */
    fun disarm(context: Context) {
        DimPrefs(context).autoOffAt = 0
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    /** Puts the alarm back for a stored off time, after the service was restarted. */
    fun restore(context: Context) {
        val at = DimPrefs(context).autoOffAt
        if (at > 0) setAlarm(context, at)
    }

    /** Turns the dimmer off if its off time has been reached. Returns true if it did. */
    fun turnOffIfDue(context: Context): Boolean {
        val prefs = DimPrefs(context)
        if (!prefs.enabled || !isDue(prefs.autoOffAt, System.currentTimeMillis())) return false
        Dimmer.turnOff(context)
        return true
    }

    /** False when Android won't let Dusk set exact alarms, so the off time may run late with the screen off. */
    fun canBeExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** The set time in the phone's 12- or 24-hour format, like "7:00 AM". */
    fun format(context: Context, minuteOfDay: Int): String {
        val millis = ZonedDateTime.now()
            .with(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
            .toInstant().toEpochMilli()
        return DateFormat.getTimeFormat(context).format(Date(millis))
    }

    private fun setAlarm(context: Context, at: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        // Exact, and allowed in Doze: an inexact alarm can fire long after the user wakes up.
        if (canBeExact(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AutoOffReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
