package com.github.nols1000.bibless

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AthleteIdTest {

    @Test
    fun normalizes() {
        assertEquals("A1234567", normalizeAthleteId("A1234567"))
        assertEquals("A1234567", normalizeAthleteId(" a1234567 "))
        assertEquals("A1234567", normalizeAthleteId("1234567"))
    }

    @Test
    fun rejectsInvalid() {
        assertNull(normalizeAthleteId(""))
        assertNull(normalizeAthleteId("A"))
        assertNull(normalizeAthleteId("B123"))
        assertNull(normalizeAthleteId("A12 34"))
        assertNull(normalizeAthleteId("A12345678901"))
    }
}
