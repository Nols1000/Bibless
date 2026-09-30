package com.github.nols1000.bibless

import com.github.nols1000.bibless.barcode.BarcodeFormat
import kotlinx.serialization.Serializable

/** Which kind of device this app runs on; each kind has its own default [BarcodeFormat]. */
enum class Device { PHONE, WATCH }

/** What the apps show on launch when no barcode was left open. */
enum class LaunchScreen { FIRST_BARCODE, BARCODE_LIST }

/**
 * Display preferences shared between phone and watch. The whole object syncs as one unit: the
 * version with the newest [updatedAt] wins.
 */
@Serializable
data class Settings(
    val phoneFormat: BarcodeFormat = BarcodeFormat.QR,
    val watchFormat: BarcodeFormat = BarcodeFormat.QR,
    val openOnLaunch: LaunchScreen = LaunchScreen.BARCODE_LIST,
    /**
     * Barcode IDs in the order the user put them. Kept here rather than on each [Barcode] so that
     * two devices reordering at once can't mix their orders. Null in settings written before
     * barcodes could be reordered; see [migrated].
     */
    val barcodeOrder: List<String>? = null,
    /** Barcode picked to open on launch by versions before [openOnLaunch]; only read to migrate. */
    val defaultBarcodeId: String? = null,
    val updatedAt: Long = 0,
) {
    fun formatFor(device: Device): BarcodeFormat = when (device) {
        Device.PHONE -> phoneFormat
        Device.WATCH -> watchFormat
    }

    /**
     * Upgrades settings written by a version without [barcodeOrder]: [order] is kept, except that
     * the barcode the old version opened on launch moves to the front and becomes the one opened
     * first. Without such a pick, [openOnLaunch] falls back to [launch]. [updatedAt] stays, so the
     * upgrade alone doesn't win over the paired device's settings.
     */
    fun migrated(order: List<String>, launch: LaunchScreen): Settings {
        if (barcodeOrder != null) return this
        val pick = defaultBarcodeId?.takeIf { it in order }
        return copy(
            openOnLaunch = if (pick != null) LaunchScreen.FIRST_BARCODE else launch,
            barcodeOrder = listOfNotNull(pick) + order.filter { it != pick },
            defaultBarcodeId = null,
        )
    }
}
