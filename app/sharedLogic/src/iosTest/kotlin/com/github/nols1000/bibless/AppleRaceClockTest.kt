package com.github.nols1000.bibless

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppleRaceClockTest {
    @Test
    fun bootIdIsStableAndSet() {
        val clock = AppleRaceClock()
        assertTrue(clock.bootId().length >= 32, "boot ID: ${clock.bootId()}")
        assertEquals(clock.bootId(), AppleRaceClock().bootId())
    }

    @Test
    fun monotonicTimeMovesForward() {
        val clock = AppleRaceClock()
        val first = clock.monotonicMillis()
        assertTrue(first > 0)
        assertTrue(clock.monotonicMillis() >= first)
    }
}
