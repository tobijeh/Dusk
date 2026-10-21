package org.dusk

/** Dim level math, in whole percent. Pure so it can be unit tested. */
object DimLevel {
    const val MAX = 90
    const val STEP = 10

    fun clamp(level: Int, max: Int = MAX): Int = level.coerceIn(0, max)

    /** One notification step up, snapping to the next multiple of [STEP] (35 → 40). */
    fun stepUp(level: Int, max: Int = MAX): Int = clamp((Math.floorDiv(level, STEP) + 1) * STEP, max)

    /** One notification step down, snapping to the previous multiple of [STEP] (35 → 30). */
    fun stepDown(level: Int, max: Int = MAX): Int = clamp((Math.floorDiv(level + STEP - 1, STEP) - 1) * STEP, max)

    fun toAlpha(level: Int, max: Int = MAX): Float = clamp(level, max) / 100f

    /**
     * Highest level that still lets touches through. Android 12+ blocks touches under an
     * untrusted overlay whose opacity is above [maxObscuringOpacity] (0.8 by default).
     */
    fun maxFor(maxObscuringOpacity: Float?): Int =
        if (maxObscuringOpacity == null) MAX
        else minOf(MAX, (maxObscuringOpacity * 100 + 1e-3f).toInt())
}
