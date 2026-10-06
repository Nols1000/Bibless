package com.github.nols1000.bibless.barcode

/**
 * How [matrix] sits in its drawn image, in modules: the quiet zone around it and the image's
 * proportions. Every renderer takes these from here, so the codes look the same everywhere.
 */
class BarcodeLayout(val matrix: BitMatrix) {
    /** 1D barcodes are one row of bars stretched to [rows]; 2D codes have square modules. */
    val isLinear: Boolean = matrix.height == 1

    /** Light modules left of, right of, above and below the code, which scanners need to find it. */
    val quiet: Int = if (isLinear) 10 else 4

    /** Width of the image in modules, quiet zone included. */
    val columns: Int = matrix.width + 2 * quiet

    /** Height of the image in modules, quiet zone included; bars are 0.4 times the image width. */
    val rows: Float = if (isLinear) columns * 0.4f else (matrix.height + 2 * quiet).toFloat()

    val aspectRatio: Float get() = columns / rows
}

fun BarcodeFormat.layout(text: String): BarcodeLayout = BarcodeLayout(encode(text))
