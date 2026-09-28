package com.github.nols1000.bibless

/** Sends the full barcode snapshot to the paired device. */
interface BarcodeSync {
    fun push(payloadJson: String)
}
