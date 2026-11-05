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
 * morning. [DimmerService] owns the alarm: it schedules it while the dimmer is on and cancels it
 * when the dimmer goes off.
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

    /** Schedules the alarm when the dimmer is on and auto-off is set; cancels it otherwise. */
    fun sync(context: Context) {
        val prefs = DimPrefs(context)
        if (prefs.enabled && prefs.autoOff) schedule(context, prefs.autoOffMinute) else cancel(context)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    /** The set time in the phone's 12- or 24-hour format, like "7:00 AM". */
    fun format(context: Context, minuteOfDay: Int): String {
        val millis = ZonedDateTime.now()
            .with(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
            .toInstant().toEpochMilli()
        return DateFormat.getTimeFormat(context).format(Date(millis))
    }

    private fun schedule(context: Context, minuteOfDay: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = nextTriggerMillis(System.currentTimeMillis(), minuteOfDay, ZoneId.systemDefault())
        // Exact, and allowed in Doze: an inexact alarm can fire long after the user wakes up.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
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
