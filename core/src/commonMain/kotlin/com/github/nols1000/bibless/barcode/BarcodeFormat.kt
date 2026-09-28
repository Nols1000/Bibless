package com.github.nols1000.bibless.barcode

enum class BarcodeFormat {
    CODE128,
    QR;

    fun encode(text: String): BitMatrix = when (this) {
        CODE128 -> encodeCode128(text)
        QR -> encodeQr(text)
    }
}
