package com.github.nols1000.bibless

import android.app.Application
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Keeps the widget in step with the list, whether it changed in the app or came from the watch. */
class PhoneApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        var shown: Any? = null
        Bibless.repository(this).observe { state ->
            // Only what the widget shows; the first observation just records it.
            val first = state.barcodes.firstOrNull()
            val now = listOf(first?.id, first?.name, first?.athleteId, state.format)
            if (shown != null && now != shown) scope.launch { BarcodeWidget().updateAll(this@PhoneApplication) }
            shown = now
        }
    }
}
