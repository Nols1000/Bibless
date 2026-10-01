package com.github.nols1000.bibless

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the app on one barcode's detail screen from outside it: tiles, widgets and shortcuts. The
 * activity is `singleTop`, so a tap while the app is open reaches it through `onNewIntent`.
 */
object BarcodeLink {
    const val EXTRA_BARCODE_ID = "com.github.nols1000.bibless.BARCODE_ID"

    /** Starts the app's launcher activity on [barcodeId], or on its usual start screen if null. */
    fun intent(context: Context, barcodeId: String?): Intent =
        requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)).apply {
            barcodeId?.let {
                putExtra(EXTRA_BARCODE_ID, it)
                // Intents that differ only in extras count as the same, so each barcode's pending
                // intent in a widget or shortcut would be taken for the others'. Same URL as on iOS.
                data = Uri.Builder().scheme("bibless").authority("barcode").appendPath(it).build()
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

    /**
     * The barcode [intent] asks for. Null when it asks for none, and when the system replays it
     * from recents, where the runner expects the app as they left it.
     */
    fun barcodeId(intent: Intent?): String? =
        intent?.takeIf { it.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 }
            ?.getStringExtra(EXTRA_BARCODE_ID)
}
