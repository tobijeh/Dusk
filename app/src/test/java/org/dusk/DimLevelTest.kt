package org.dusk

import org.junit.Assert.assertEquals
import org.junit.Test

class DimLevelTest {
    @Test fun stepUpSnapsToNextTen() {
        assertEquals(40, DimLevel.stepUp(35))
        assertEquals(50, DimLevel.stepUp(40))
        assertEquals(10, DimLevel.stepUp(0))
    }

    @Test fun stepDownSnapsToPreviousTen() {
        assertEquals(30, DimLevel.stepDown(35))
        assertEquals(30, DimLevel.stepDown(40))
        assertEquals(0, DimLevel.stepDown(5))
        assertEquals(0, DimLevel.stepDown(0))
    }

    @Test fun stepsStopAtLimits() {
        assertEquals(90, DimLevel.stepUp(90))
        assertEquals(80, DimLevel.stepUp(80, max = 80))
        assertEquals(80, DimLevel.stepUp(75, max = 80))
    }

    @Test fun alphaIsClamped() {
        assertEquals(0.5f, DimLevel.toAlpha(50), 0f)
        assertEquals(0.9f, DimLevel.toAlpha(150), 0f)
        assertEquals(0f, DimLevel.toAlpha(-5), 0f)
    }

    @Test fun maxFollowsTouchOpacityLimit() {
        assertEquals(90, DimLevel.maxFor(null))
        assertEquals(80, DimLevel.maxFor(0.8f))
        assertEquals(90, DimLevel.maxFor(1f))
    }
}
