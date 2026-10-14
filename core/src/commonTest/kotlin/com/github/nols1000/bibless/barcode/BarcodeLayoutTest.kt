package com.github.nols1000.bibless.barcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BarcodeLayoutTest {

    @Test
    fun barcodesGetAWideQuietZoneAndAFixedHeight() {
        val layout = BarcodeFormat.CODE128.layout("A1234567")

        assertTrue(layout.isLinear)
        assertEquals(10, layout.quiet)
        assertEquals(layout.matrix.width + 20, layout.columns)
        assertEquals(layout.columns * 0.4f, layout.rows, 0.001f)
        assertEquals(2.5f, layout.aspectRatio, 0.001f)
    }

    @Test
    fun qrCodesAreSquareWithAFourModuleQuietZone() {
        val layout = BarcodeFormat.QR.layout("A1234567")

        assertFalse(layout.isLinear)
        assertEquals(4, layout.quiet)
        assertEquals(layout.matrix.width + 8, layout.columns)
        assertEquals((layout.matrix.height + 8).toFloat(), layout.rows)
        assertEquals(1f, layout.aspectRatio, 0.001f)
    }
}
