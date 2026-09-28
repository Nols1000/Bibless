package com.github.nols1000.bibless.barcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Code128Test {

    @Test
    fun patternsAreElevenModulesWide() {
        assertEquals(106, PATTERNS.size)
        PATTERNS.forEach { assertEquals(11, it.sumOf { c -> c.digitToInt() }, it) }
    }

    @Test
    fun parkrunIdSwitchesToCodeCForTrailingDigits() {
        // Start B, 'A', '1', Code C, 23, 45, 67
        assertEquals(listOf(104, 33, 17, 99, 23, 45, 67), code128Symbols("A1234567"))
    }

    @Test
    fun shortDigitRunsStayInCodeB() {
        assertEquals(listOf(104, 33, 17, 18, 19), code128Symbols("A123"))
    }

    @Test
    fun allDigitsStartInCodeC() {
        assertEquals(listOf(105, 12, 34), code128Symbols("1234"))
    }

    @Test
    fun matrixWidthMatchesSymbolCount() {
        // 7 symbols + checksum at 11 modules each, plus the 13-module stop pattern.
        val matrix = encodeCode128("A1234567")
        assertEquals(8 * 11 + 13, matrix.width)
        assertEquals(1, matrix.height)
        assertTrue(matrix[0, 0], "Starts with a bar")
        assertTrue(matrix[matrix.width - 1, 0], "Ends with a bar")
    }

    @Test
    fun rejectsNonAscii() {
        assertFailsWith<IllegalArgumentException> { encodeCode128("Ä1") }
    }
}
