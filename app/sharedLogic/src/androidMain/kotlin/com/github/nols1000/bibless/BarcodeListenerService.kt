package com.github.nols1000.bibless

import com.github.nols1000.bibless.DataLayerSync.Companion.applyTo
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/** Receives barcode snapshots from the paired device while the app is not running. */
class BarcodeListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.applyTo(Bibless.repository(this))
    }
}
