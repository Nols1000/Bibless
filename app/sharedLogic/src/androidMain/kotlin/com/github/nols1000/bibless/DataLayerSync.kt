package com.github.nols1000.bibless

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable

/**
 * Syncs barcodes between phone and watch through the Wearable Data Layer. Each device publishes
 * its full snapshot at [PATH]; the other side merges it.
 */
class DataLayerSync(
    context: Context,
    private val repository: BarcodeRepository,
) : BarcodeSync {

    private val dataClient = Wearable.getDataClient(context)
    private val listener = DataClient.OnDataChangedListener { it.applyTo(repository) }

    override fun push(payloadJson: String) {
        val request = PutDataMapRequest.create(PATH)
            .apply { dataMap.putString(KEY_PAYLOAD, payloadJson) }
            .asPutDataRequest()
            .setUrgent()
        dataClient.putDataItem(request)
            .addOnFailureListener { Log.w(TAG, "Failed to push barcodes", it) }
    }

    /** Listens for changes while the app is in the foreground and pulls what arrived meanwhile. */
    fun start() {
        dataClient.addListener(listener)
        val allNodes = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(PATH).build()
        dataClient.getDataItems(allNodes)
            .addOnSuccessListener { items ->
                items.forEach(::apply)
                items.release()
            }
            .addOnFailureListener { Log.w(TAG, "Failed to pull barcodes", it) }
    }

    fun stop() {
        dataClient.removeListener(listener)
    }

    private fun apply(item: DataItem) = item.applyTo(repository)

    internal companion object {
        const val PATH = "/barcodes"
        const val KEY_PAYLOAD = "payload"
        private const val TAG = "DataLayerSync"

        fun DataEventBuffer.applyTo(repository: BarcodeRepository) = forEach {
            if (it.type == DataEvent.TYPE_CHANGED) it.dataItem.applyTo(repository)
        }

        fun DataItem.applyTo(repository: BarcodeRepository) {
            if (uri.path != PATH) return
            DataMapItem.fromDataItem(this).dataMap.getString(KEY_PAYLOAD)?.let(repository::applyRemote)
        }
    }
}
