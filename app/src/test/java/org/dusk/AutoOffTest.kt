package org.dusk

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AutoOffTest {
    private val zone = ZoneId.of("America/New_York")

    private fun at(text: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), zone).toInstant().toEpochMilli()

    private fun next(now: String, minuteOfDay: Int) = AutoOff.nextTriggerMillis(at(now), minuteOfDay, zone)

    @Test fun laterTodayWhenTimeIsStillAhead() {
        assertEquals(at("2026-11-05T07:00"), next("2026-11-05T06:00", 7 * 60))
    }

    @Test fun tomorrowWhenTimeHasPassed() {
        assertEquals(at("2026-11-06T07:00"), next("2026-11-05T23:00", 7 * 60))
    }

    @Test fun tomorrowWhenItIsExactlyThatTime() {
        assertEquals(at("2026-11-06T07:00"), next("2026-11-05T07:00", 7 * 60))
    }

    @Test fun keepsMinutes() {
        assertEquals(at("2026-11-06T06:45"), next("2026-11-05T22:10", 6 * 60 + 45))
    }

    @Test fun followsWallClockAcrossDaylightSavingChange() {
        // US clocks fall back on 2026-11-01: still 7:00 local time, 25 hours later.
        assertEquals(at("2026-11-01T07:00"), next("2026-10-31T22:00", 7 * 60))
    }
}
