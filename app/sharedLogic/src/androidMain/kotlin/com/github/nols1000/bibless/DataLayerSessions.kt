package com.github.nols1000.bibless

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

/**
 * Moves stopped timer sessions from the watch to the phone through the Wearable Data Layer: one
 * data item per session under [PATH_PREFIX], its signed events as an asset, since a long session
 * outgrows a data item. The Data Layer keeps the item on the watch until the phone is in reach.
 * The phone deletes it once kept, which also frees it on the watch.
 */
object DataLayerSessions {
    const val PATH_PREFIX = "/timer-sessions"
    private const val KEY_LOG = "log"
    private const val TAG = "DataLayerSessions"
    private val background = Executors.newSingleThreadExecutor()

    /** On the watch: queues session [id]'s [json] for the phone. */
    fun send(context: Context, id: String, json: String) {
        val request = PutDataMapRequest.create("$PATH_PREFIX/$id")
            .apply { dataMap.putAsset(KEY_LOG, Asset.createFromBytes(json.encodeToByteArray())) }
            .asPutDataRequest()
            .setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
            .addOnFailureListener { Log.w(TAG, "Failed to queue session $id", it) }
    }

    /** On the phone: keeps the sessions that arrived while the app wasn't listening. */
    fun pull(context: Context) {
        val app = context.applicationContext
        val client = Wearable.getDataClient(app)
        val sessions = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(PATH_PREFIX).build()
        background.execute {
            runCatching {
                val items = Tasks.await(client.getDataItems(sessions, DataClient.FILTER_PREFIX))
                try {
                    items.forEach { receive(app, it) }
                } finally {
                    items.release()
                }
            }.onFailure { Log.w(TAG, "Failed to pull sessions", it) }
        }
    }

    fun isSession(item: DataItem) = item.uri.path?.startsWith("$PATH_PREFIX/") == true

    /**
     * On the phone: keeps the session in [item] and deletes the item. Blocks on the Data Layer, so
     * call it off the main thread, as [com.google.android.gms.wearable.WearableListenerService] does.
     */
    fun receive(context: Context, item: DataItem) {
        if (!isSession(item)) return
        val client = Wearable.getDataClient(context)
        val asset = DataMapItem.fromDataItem(item).dataMap.getAsset(KEY_LOG) ?: return
        val json = Tasks.await(client.getFdForAsset(asset)).inputStream.use { it.readBytes().decodeToString() }
        val kept = runBlocking { Bibless.receivedSessions(context).receive(json) }
        if (kept) Tasks.await(client.deleteDataItems(item.uri))
        else Log.w(TAG, "Ignored ${item.uri.path}: not a timer session")
    }
}
