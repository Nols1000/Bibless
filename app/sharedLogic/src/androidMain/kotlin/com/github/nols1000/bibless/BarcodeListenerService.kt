package com.github.nols1000.bibless

import com.github.nols1000.bibless.DataLayerSync.Companion.applyTo
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives barcode snapshots from the paired device, and on the phone the watch's timer sessions,
 * also while the app is not running.
 */
class BarcodeListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.applyTo(Bibless.repository(this))
        dataEvents.filter { it.type == DataEvent.TYPE_CHANGED && DataLayerSessions.isSession(it.dataItem) }.forEach {
            runCatching { DataLayerSessions.receive(this, it.dataItem) }
                .onFailure { error -> Log.w("BarcodeListenerService", "Failed to keep a timer session", error) }
        }
    }
}
