package com.github.nols1000.bibless

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Launcher shortcuts to the first barcodes in list order, from a long press on the app icon; they
 * can also be pinned to the home screen. [PhoneApplication] republishes them when the list changes.
 */
object BarcodeShortcuts {
    /** Launchers show about four; more would push the app's own entries out. */
    private const val MAX_COUNT = 4

    fun publish(context: Context, barcodes: List<Barcode>) {
        val count = minOf(MAX_COUNT, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))
        val shortcuts = barcodes.take(count).mapIndexed { rank, barcode ->
            ShortcutInfoCompat.Builder(context, barcode.id)
                .setShortLabel(barcode.name)
                .setLongLabel("Show ${barcode.name}")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(BarcodeLink.intent(context, barcode.id))
                .setRank(rank)
                .build()
        }
        // Publishing is rate limited in the background, so skip it when nothing changed.
        val published = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }
        if (published.map { it.id to it.shortLabel.toString() } != shortcuts.map { it.id to it.shortLabel.toString() }) {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }

        // Pinned ones outlive the list: rename those still there, and grey out the deleted ones.
        val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
        val byId = barcodes.associateBy { it.id }
        val renamed = pinned.filter { it.id in byId && it.shortLabel.toString() != byId.getValue(it.id).name }
        if (renamed.isNotEmpty()) {
            ShortcutManagerCompat.updateShortcuts(
                context,
                renamed.map { shortcut ->
                    val name = byId.getValue(shortcut.id).name
                    ShortcutInfoCompat.Builder(context, shortcut.id)
                        .setShortLabel(name)
                        .setLongLabel("Show $name")
                        .build()
                },
            )
        }
        val deleted = pinned.filter { it.id !in byId && it.isEnabled }.map { it.id }
        if (deleted.isNotEmpty()) ShortcutManagerCompat.disableShortcuts(context, deleted, "This barcode was deleted")
    }
}
