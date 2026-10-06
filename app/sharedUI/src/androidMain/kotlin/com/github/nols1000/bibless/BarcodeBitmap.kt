package com.github.nols1000.bibless

import android.graphics.Bitmap
import android.graphics.Color
import com.github.nols1000.bibless.barcode.BarcodeFormat
import com.github.nols1000.bibless.barcode.layout

/**
 * Draws [text] like [BarcodeImage] does, for surfaces outside Compose such as tiles and widgets:
 * black modules on white with a quiet zone. Every module is the same whole number of pixels, so
 * the bitmap can be at most [maxWidthPx] wide but is usually a little narrower.
 */
fun barcodeBitmap(text: String, format: BarcodeFormat, maxWidthPx: Int): Bitmap {
    val layout = format.layout(text)
    val matrix = layout.matrix
    val isLinear = layout.isLinear
    val quiet = layout.quiet
    val module = (maxWidthPx / layout.columns).coerceAtLeast(1)
    val width = layout.columns * module
    val height = (layout.rows * module).toInt()

    val pixels = IntArray(width * height) { Color.WHITE }
    for (x in 0 until matrix.width) {
        for (y in 0 until matrix.height) {
            if (!matrix[x, y]) continue
            val left = (quiet + x) * module
            // Bars run between the quiet zones at the top and bottom; QR modules are squares.
            val rows = if (isLinear) quiet * module until height - quiet * module else
                (quiet + y) * module until (quiet + y + 1) * module
            for (py in rows) pixels.fill(Color.BLACK, py * width + left, py * width + left + module)
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
