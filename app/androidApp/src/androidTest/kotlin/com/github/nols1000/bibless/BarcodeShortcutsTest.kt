package com.github.nols1000.bibless

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Launcher shortcuts follow the list. */
@RunWith(AndroidJUnit4::class)
class BarcodeShortcutsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = Bibless.repository(context)

    @Before
    fun setUp() {
        // DemoData replaces the saved barcodes, so never run this on a real device
        check(Build.HARDWARE == "ranchu") { "UI tests only run on an emulator (they replace saved barcodes)" }
        DemoData.load(repository)
    }

    @Test
    fun offersTheBarcodesInListOrder() {
        awaitShortcuts("Me", "Sam", "Jamie (junior)")
    }

    @Test
    fun opensTheBarcode() {
        awaitShortcuts("Me", "Sam", "Jamie (junior)")
        val sam = shortcuts().first { it.shortLabel == "Sam" }
        assertTrue(sam.intent.filterEquals(BarcodeLink.intent(context, id("Sam"))))
        assertTrue(BarcodeLink.barcodeId(sam.intent) == id("Sam"))
    }

    @Test
    fun followsReorderingRenamingAndDeleting() {
        awaitShortcuts("Me", "Sam", "Jamie (junior)")

        repository.moveToTop(id("Jamie (junior)"))
        awaitShortcuts("Jamie (junior)", "Me", "Sam")

        repository.delete(id("Me"))
        awaitShortcuts("Jamie (junior)", "Sam")
    }

    @Test
    fun offersAtMostFour() {
        repository.add("Alex", "A0000001")
        repository.add("Kim", "A0000002")

        awaitShortcuts(*repository.state.value.barcodes.take(4).map { it.name }.toTypedArray())
    }

    private fun shortcuts() = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }

    private fun id(name: String) = repository.state.value.barcodes.first { it.name == name }.id

    private fun awaitShortcuts(vararg names: String) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT
        while (shortcuts().map { it.shortLabel.toString() } != names.toList()) {
            check(SystemClock.uptimeMillis() < deadline) {
                "Expected ${names.toList()}, got ${shortcuts().map { it.shortLabel }}"
            }
            SystemClock.sleep(100)
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
