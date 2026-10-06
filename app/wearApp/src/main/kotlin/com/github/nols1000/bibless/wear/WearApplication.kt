package com.github.nols1000.bibless.wear

import android.app.Application
import android.os.Build
import android.provider.Settings
import com.github.nols1000.bibless.Bibless
import com.github.nols1000.bibless.DataLayerSessions
import com.github.nols1000.bibless.SharedPreferencesStore
import com.github.nols1000.bibless.WatchSessionSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps the tile in step with the list, whether it changed in the app or came from the phone, and
 * hands each stopped timer session to the phone.
 */
class WearApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        sendTimerSessions()
        var shown: Any? = null
        Bibless.repository(this).observe { state ->
            // Only what the tile shows; the first observation just records it.
            val first = state.barcodes.firstOrNull()
            val now = listOf(first?.id, first?.name, first?.athleteId, state.format)
            if (shown != null && now != shown) BarcodeTileService.requestUpdate(this)
            shown = now
        }
    }

    /** Now for sessions stopped before, then whenever the timer stops. */
    private fun sendTimerSessions() {
        val timer = Bibless.timer(this)
        val name = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
        val sender = WatchSessionSender(timer, SharedPreferencesStore(this, "bibless.race", durable = true), name) { id, json ->
            DataLayerSessions.send(this, id, json)
            true
        }
        var running: String? = null
        timer.observe { state ->
            val stopped = running != null && state.session == null
            running = state.session?.id
            if (stopped) scope.launch { sender.sendStopped() }
        }
        scope.launch { sender.sendStopped() }
    }
}
