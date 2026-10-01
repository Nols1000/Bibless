package com.github.nols1000.bibless

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * A Quick Settings tile that shows the first barcode, also on the lock screen, without unlocking.
 * With no barcode it is unavailable. [PhoneApplication] refreshes it when the list changes.
 */
class BarcodeQuickSettingsTile : TileService() {
    override fun onStartListening() {
        val barcode = Bibless.repository(this).state.value.barcodes.firstOrNull()
        qsTile?.apply {
            state = if (barcode == null) Tile.STATE_UNAVAILABLE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = barcode?.name
            updateTile()
        }
    }

    // The activity shows over the lock screen, so the system opens it without asking to unlock.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val intent = LockScreenActivity.intent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Asks the system to redraw the tile, if it is in Quick Settings. */
        fun requestUpdate(context: Context) {
            requestListeningState(context, ComponentName(context, BarcodeQuickSettingsTile::class.java))
        }
    }
}
