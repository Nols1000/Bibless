package com.github.nols1000.bibless

import android.graphics.Bitmap
import android.graphics.Color
import com.github.nols1000.bibless.barcode.BarcodeFormat

/**
 * Draws [text] like [BarcodeImage] does, for surfaces outside Compose such as tiles and widgets:
 * black modules on white with a quiet zone. Every module is the same whole number of pixels, so
 * the bitmap can be at most [maxWidthPx] wide but is usually a little narrower.
 */
fun barcodeBitmap(text: String, format: BarcodeFormat, maxWidthPx: Int): Bitmap {
    val matrix = format.encode(text)
    val isLinear = matrix.height == 1
    val quiet = if (isLinear) 10 else 4
    val columns = matrix.width + 2 * quiet
    val module = (maxWidthPx / columns).coerceAtLeast(1)
    val width = columns * module
    val height = if (isLinear) (width * 0.4f).toInt() else (matrix.height + 2 * quiet) * module

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
