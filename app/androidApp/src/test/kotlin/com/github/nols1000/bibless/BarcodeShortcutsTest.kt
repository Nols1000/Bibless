package com.github.nols1000.bibless

import android.content.Context
import android.os.Looper
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Launcher shortcuts follow the list. */
@RunWith(AndroidJUnit4::class)
class BarcodeShortcutsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = Bibless.repository(context)

    @Before
    fun setUp() {
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
        // PhoneApplication publishes them in the background; Robolectric's clock doesn't move on its own
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (shortcuts().map { it.shortLabel.toString() } != names.toList()) {
            check(System.currentTimeMillis() < deadline) {
                "Expected ${names.toList()}, got ${shortcuts().map { it.shortLabel }}"
            }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
