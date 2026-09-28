package com.github.nols1000.bibless.barcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QrCodeTest {

    @Test
    fun capacityTablesMatchSpec() {
        // Data codeword counts from ISO/IEC 18004 Table 7.
        assertEquals(16, numDataCodewords(1, QrEcc.MEDIUM))
        assertEquals(28, numDataCodewords(2, QrEcc.MEDIUM))
        assertEquals(19, numDataCodewords(1, QrEcc.LOW))
        assertEquals(9, numDataCodewords(1, QrEcc.HIGH))
        assertEquals(2956, numDataCodewords(40, QrEcc.LOW))
    }

    @Test
    fun parkrunIdFitsVersionOne() {
        assertEquals(21, encodeQr("A1234567").width)
    }

    @Test
    fun longerTextGrowsVersion() {
        // 15 bytes exceeds version 1-M's 14-byte capacity.
        assertEquals(25, encodeQr("A12345678901234").width)
    }

    @Test
    fun hasFinderPatterns() {
        val m = encodeQr("A1234567")
        for ((cx, cy) in listOf(3 to 3, m.width - 4 to 3, 3 to m.width - 4)) {
            assertTrue(m[cx, cy], "centre")
            assertTrue(!m[cx - 2, cy], "light ring")
            assertTrue(m[cx - 3, cy], "outer ring")
        }
    }
}
