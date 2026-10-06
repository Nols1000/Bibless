package com.github.nols1000.bibless.wear

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.github.nols1000.bibless.Bibless
import com.github.nols1000.bibless.DemoData
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Swiping a barcode away in the list, which deletes it on both devices unless undone in time. */
@RunWith(AndroidJUnit4::class)
class SwipeToDeleteTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = Bibless.repository(context)

    @Before
    fun setUp() {
        // DemoData replaces the saved barcodes, so never run this on a real device
        check(Build.HARDWARE == "ranchu") { "UI tests only run on an emulator (they replace saved barcodes)" }
        val starting = By.res("com.google.android.wearable.sysui", "starting_screen_text_view")
        check(device.wait(Until.gone(starting), STARTUP_TIMEOUT)) { "Watch did not finish starting" }
        // Touches don't reach an app behind a dark screen
        device.wakeUp()
        DemoData.load(repository)
    }

    @Test
    fun undoKeepsTheBarcode() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val sam = id("Sam")
            val y = swipeAway(waitFor("Sam"))
            // The undo button takes the row's place once it has faded in. It fades in from fully
            // transparent, which keeps it out of the accessibility tree that UiAutomator searches
            // until something else on screen changes, so it is tapped where it is drawn.
            SystemClock.sleep(UNDO_FADE_IN_MILLIS)
            device.click(device.displayWidth / 2, y)

            check(device.wait(Until.hasObject(By.text("A0246802")), TIMEOUT)) { "Sam's row did not come back" }
            SystemClock.sleep(UNDO_MILLIS + 1_000)
            assertNotNull("Sam was deleted despite the undo", repository.find(sam))
        }
    }

    @Test
    fun deletesOnceTheUndoIsGone() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val sam = id("Sam")
            swipeAway(waitFor("Sam"))
            SystemClock.sleep(UNDO_FADE_IN_MILLIS)
            assertNotNull("Sam was deleted before the undo ran out", repository.find(sam))

            SystemClock.sleep(UNDO_MILLIS)
            assertNull("Sam was not deleted", repository.find(sam))
            check(device.wait(Until.gone(By.text("A0246802")), TIMEOUT)) { "Sam's row did not go away" }
        }
    }

    private fun id(name: String) = repository.state.value.barcodes.first { it.name == name }.id

    /**
     * Drags the row holding [label] across the screen, past the point where the delete button
     * would only show, and returns the row's height on screen. The label itself is too narrow to
     * start from.
     */
    private fun swipeAway(label: UiObject2): Int {
        // After the previous test's activity has gone, which would otherwise swallow the swipe
        device.waitForIdle()
        val y = label.visibleBounds.centerY()
        device.swipe(device.displayWidth * 88 / 100, y, device.displayWidth * 7 / 100, y, 60)
        return y
    }

    private fun waitFor(text: String): UiObject2 =
        device.wait(Until.findObject(By.text(text)), TIMEOUT) ?: run {
            ByteArrayOutputStream().also(device::dumpWindowHierarchy).toString().lines().forEach { Log.e(TAG, it) }
            error("$text did not show up")
        }

    private companion object {
        const val TIMEOUT = 30_000L
        const val STARTUP_TIMEOUT = 180_000L
        /** The row settles and the undo button fades in well within this. */
        const val UNDO_FADE_IN_MILLIS = 1_500L
        const val TAG = "SwipeToDeleteTest"
    }
}
