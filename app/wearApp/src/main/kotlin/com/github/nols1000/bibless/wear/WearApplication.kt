package com.github.nols1000.bibless.wear

import android.app.Application
import com.github.nols1000.bibless.Bibless

/** Keeps the tile in step with the list, whether it changed in the app or came from the phone. */
class WearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        var shown: Any? = null
        Bibless.repository(this).observe { state ->
            // Only what the tile shows; the first observation just records it.
            val first = state.barcodes.firstOrNull()
            val now = listOf(first?.id, first?.name, first?.athleteId, state.format)
            if (shown != null && now != shown) BarcodeTileService.requestUpdate(this)
            shown = now
        }
    }
}
