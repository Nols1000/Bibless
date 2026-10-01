package com.github.nols1000.bibless

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * The first barcode over the lock screen, opened from the Quick Settings tile, so it can be scanned
 * without unlocking. Shows nothing else: Back closes it, and the list stays behind the lock.
 */
class LockScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The manifest asks for this too, which is what lets the tile skip the unlock; these cover
        // Android 7 and 8.0, which only know the window flags.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        setContent {
            FirstBarcodeApp(Bibless.repository(this), onClose = ::finish)
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
