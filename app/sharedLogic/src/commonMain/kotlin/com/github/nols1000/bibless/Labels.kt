package com.github.nols1000.bibless

import com.github.nols1000.bibless.barcode.BarcodeFormat

/** How settings name the format, the same on phone and watch. */
val BarcodeFormat.label: String
    get() = if (this == BarcodeFormat.QR) "QR code" else "Barcode"

/** How settings name the launch screen, the same on phone and watch. */
val LaunchScreen.label: String
    get() = if (this == LaunchScreen.FIRST_BARCODE) "First barcode" else "Barcode list"
