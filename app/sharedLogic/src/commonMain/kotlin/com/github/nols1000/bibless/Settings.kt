package com.github.nols1000.bibless

import com.github.nols1000.bibless.barcode.BarcodeFormat
import kotlinx.serialization.Serializable

/** Which kind of device this app runs on; each kind has its own default [BarcodeFormat]. */
enum class Device { PHONE, WATCH }

/**
 * Display preferences shared between phone and watch. The whole object syncs as one unit: the
 * version with the newest [updatedAt] wins.
 */
@Serializable
data class Settings(
    val phoneFormat: BarcodeFormat = BarcodeFormat.QR,
    val watchFormat: BarcodeFormat = BarcodeFormat.QR,
    val updatedAt: Long = 0,
) {
    fun formatFor(device: Device): BarcodeFormat = when (device) {
        Device.PHONE -> phoneFormat
        Device.WATCH -> watchFormat
    }
}
