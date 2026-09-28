package com.github.nols1000.bibless.barcode

/**
 * A grid of dark (`true`) / light (`false`) modules. 1D barcodes have [height] 1.
 */
class BitMatrix(val width: Int, val height: Int) {
    private val bits = BooleanArray(width * height)

    operator fun get(x: Int, y: Int): Boolean = bits[y * width + x]

    operator fun set(x: Int, y: Int, value: Boolean) {
        bits[y * width + x] = value
    }
}
