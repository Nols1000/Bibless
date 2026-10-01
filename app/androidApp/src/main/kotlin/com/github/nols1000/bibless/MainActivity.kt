package com.github.nols1000.bibless

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    /** Barcodes asked for by tiles, widgets or shortcuts while this activity runs. */
    private val openRequests = Channel<String>(Channel.CONFLATED)
    private val openRequestFlow = openRequests.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repository = Bibless.repository(this)
        // Starts on the barcode asked for, the way the app would reopen on it after a restart.
        if (savedInstanceState == null) BarcodeLink.barcodeId(intent)?.let { repository.shownBarcodeId = it }
        setContent {
            App(repository, openRequestFlow)
        }
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
