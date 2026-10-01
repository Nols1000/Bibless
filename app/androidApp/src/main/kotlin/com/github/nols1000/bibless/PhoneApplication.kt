package com.github.nols1000.bibless

import android.app.Application
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Keeps the widget, the Quick Settings tile and the shortcuts in step with the list, whether it changed in the app or came from the watch. */
class PhoneApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** One publish at a time, in the order the changes came. */
    private val shortcuts = Dispatchers.Default.limitedParallelism(1)

    override fun onCreate() {
        super.onCreate()
        var shown: Any? = null
        Bibless.repository(this).observe { state ->
            // Also on the first observation, for barcodes saved before the shortcuts existed
            scope.launch(shortcuts) { BarcodeShortcuts.publish(this@PhoneApplication, state.barcodes) }
            // Only what the widget and tile show; the first observation just records it.
            val first = state.barcodes.firstOrNull()
            val now = listOf(first?.id, first?.name, first?.athleteId, state.format)
            if (shown != null && now != shown) {
                scope.launch { BarcodeWidget().updateAll(this@PhoneApplication) }
                BarcodeQuickSettingsTile.requestUpdate(this)
            }
            shown = now
        }
    }
}
