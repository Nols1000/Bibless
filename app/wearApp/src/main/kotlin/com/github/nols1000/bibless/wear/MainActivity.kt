package com.github.nols1000.bibless.wear

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.wear.ambient.AmbientLifecycleObserver
import com.github.nols1000.bibless.BarcodeLink
import com.github.nols1000.bibless.Bibless
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    /** Barcodes asked for by tiles, widgets or shortcuts while this activity runs. */
    private val openRequests = Channel<String>(Channel.CONFLATED)
    private val openRequestFlow = openRequests.receiveAsFlow()

    private val ambient = mutableStateOf(Ambient())

    // Stays on screen in ambient mode instead of going back to the watch face, so a timekeeper's
    // timer is still there with the wrist down; screens other than the timer go dark meanwhile.
    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                ambient.value = ambient.value.copy(isAmbient = true)
            }

            override fun onUpdateAmbient() {
                ambient.value = ambient.value.copy(updates = ambient.value.updates + 1)
            }

            override fun onExitAmbient() {
                ambient.value = ambient.value.copy(isAmbient = false)
            }
        },
    )
    private var timerObservation: AutoCloseable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)

        val repository = Bibless.repository(this)
        val timer = Bibless.timer(this)
        // Starts on the barcode asked for, the way the app would reopen on it after a restart.
        if (savedInstanceState == null) BarcodeLink.barcodeId(intent)?.let { repository.shownBarcodeId = it }
        // While timing, the screen stays on: a tap that only wakes it would miss a finisher.
        timerObservation = timer.observe { state ->
            runOnUiThread {
                if (state.session != null) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        setContent {
            WearApp(repository, timer, openRequestFlow, ambient.value)
        }
    }

    override fun onDestroy() {
        timerObservation?.close()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        BarcodeLink.barcodeId(intent)?.let { openRequests.trySend(it) }
    }

    override fun onStart() {
        super.onStart()
        Bibless.sync(this).start()
    }

    override fun onStop() {
        Bibless.sync(this).stop()
        super.onStop()
    }
}
