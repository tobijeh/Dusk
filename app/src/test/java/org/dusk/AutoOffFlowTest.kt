package org.dusk

import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSettings
import java.time.ZoneId

/** The whole auto-off path: arming, the alarm, the screen-on clock check, and restarts. */
@RunWith(RobolectricTestRunner::class)
class AutoOffFlowTest {
    private lateinit var app: Application
    private lateinit var prefs: DimPrefs

    @Before fun setUp() {
        app = RuntimeEnvironment.getApplication()
        prefs = DimPrefs(app)
        ShadowSettings.setCanDrawOverlays(true)
    }

    private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java))

    /** Turns the dimmer on the way the tile and settings do, and runs the service start. */
    private fun startDimmer(): ServiceController<DimmerService> {
        Dimmer.turnOn(app)
        val start = shadowOf(app).nextStartedService
        val service = Robolectric.buildService(DimmerService::class.java, start).create().startCommand(0, 1)
        ShadowLooper.idleMainLooper()
        return service
    }

    @Test fun turningOnSetsTheOffTimeAndAnAlarmForIt() {
        prefs.autoOff = true
        prefs.autoOffMinute = 7 * 60
        startDimmer()

        val expected = AutoOff.nextTriggerMillis(System.currentTimeMillis(), 7 * 60, ZoneId.systemDefault())
        assertEquals(expected, prefs.autoOffAt)
        assertEquals(expected, alarms().nextScheduledAlarm!!.triggerAtTime)
    }

    @Test fun noOffTimeWhenAutoOffIsOff() {
        startDimmer()
        assertEquals(0L, prefs.autoOffAt)
        assertNull(alarms().nextScheduledAlarm)
    }

    @Test fun theAlarmTurnsTheDimmerOff() {
        prefs.autoOff = true
        startDimmer()
        prefs.autoOffAt = System.currentTimeMillis() - 1000

        alarms().nextScheduledAlarm!!.operation!!.send()
        ShadowLooper.idleMainLooper()

        assertFalse(prefs.enabled)
        assertEquals(0L, prefs.autoOffAt)
    }

    @Test fun anEarlyAlarmLeavesTheDimmerOnAndSetsItAgain() {
        prefs.autoOff = true
        startDimmer()
        val offAt = prefs.autoOffAt

        AutoOffReceiver().onReceive(app, Intent())

        assertTrue(prefs.enabled)
        assertEquals(offAt, alarms().nextScheduledAlarm!!.triggerAtTime)
    }

    @Test fun theMinuteTickTurnsTheDimmerOffWithoutTheAlarm() {
        prefs.autoOff = true
        startDimmer()
        // The phone never delivers the alarm; the off time passes with the screen on.
        prefs.autoOffAt = System.currentTimeMillis() - 1000

        app.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
        ShadowLooper.idleMainLooper()

        assertFalse(prefs.enabled)
    }

    @Test fun turningTheScreenOnAfterTheOffTimeTurnsTheDimmerOff() {
        prefs.autoOff = true
        startDimmer()
        prefs.autoOffAt = System.currentTimeMillis() - 60_000

        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        ShadowLooper.idleMainLooper()

        assertFalse(prefs.enabled)
    }

    @Test fun theTickDoesNothingBeforeTheOffTime() {
        prefs.autoOff = true
        startDimmer()

        app.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
        ShadowLooper.idleMainLooper()

        assertTrue(prefs.enabled)
    }

    @Test fun changingTheTimeWhileOnMovesTheOffTime() {
        prefs.autoOff = true
        prefs.autoOffMinute = 7 * 60
        startDimmer()

        prefs.autoOffMinute = 6 * 60 + 30
        ShadowLooper.idleMainLooper()

        val expected = AutoOff.nextTriggerMillis(System.currentTimeMillis(), 6 * 60 + 30, ZoneId.systemDefault())
        assertEquals(expected, prefs.autoOffAt)
        assertEquals(expected, alarms().nextScheduledAlarm!!.triggerAtTime)
    }

    @Test fun aRestartAfterTheOffTimeTurnsTheDimmerOff() {
        prefs.autoOff = true
        startDimmer().destroy()
        // The process died overnight; the system restarts the sticky service after the off time.
        prefs.enabled = true
        prefs.autoOffAt = System.currentTimeMillis() - 60_000

        Robolectric.buildService(DimmerService::class.java).create().startCommand(0, 2)
        ShadowLooper.idleMainLooper()

        assertFalse(prefs.enabled)
    }

    @Test fun turningOffClearsTheOffTimeAndAlarm() {
        prefs.autoOff = true
        startDimmer()
        Dimmer.turnOff(app)

        assertEquals(0L, prefs.autoOffAt)
        assertNull(alarms().nextScheduledAlarm)
    }
}
