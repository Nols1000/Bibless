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
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.github.nols1000.bibless.BarcodeLink
import com.github.nols1000.bibless.Bibless
import com.github.nols1000.bibless.DemoData
import org.junit.Before
import java.io.ByteArrayOutputStream
import org.junit.Test
import org.junit.runner.RunWith

/** Opening a barcode from outside the app, as the tile does. */
@RunWith(AndroidJUnit4::class)
class BarcodeLinkTest {
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
    fun opensTheLinkedBarcode() {
        ActivityScenario.launch<MainActivity>(BarcodeLink.intent(context, id("Sam"))).use {
            awaitShown("Sam", "A0246802")
        }
    }

    /** The case the Apple Watch widget got wrong at first: the pager had moved on from the barcode. */
    @Test
    fun startsOverOnTheLinkedBarcodeWhenPagedAway() {
        ActivityScenario.launch<MainActivity>(BarcodeLink.intent(context, id("Me"))).use {
            awaitShown("Me", "A0123456")
            // After the previous test's activity has gone, which would otherwise swallow the swipe
            device.waitForIdle()
            checkNotNull(device.findObject(By.scrollable(true))) { "No pager" }.swipe(Direction.UP, 0.5f)
            awaitShown("Sam", "A0246802")

            context.startActivity(BarcodeLink.intent(context, id("Me")))
            awaitShown("Me", "A0123456")

            // On top of the list, not on top of the earlier pager
            device.pressBack()
            check(device.wait(Until.hasObject(By.text("Barcodes")), TIMEOUT)) { "List is not below the barcode" }
        }
    }

    /** On round screens the labels are curved text, which TalkBack only reads out through the layout's semantics. */
    @Test
    fun readsOutALongNameInFull() {
        val name = "Jamie Alexandra Fitzgerald-Smith"
        val barcode = repository.add(name, "A0864213")
        ActivityScenario.launch<MainActivity>(BarcodeLink.intent(context, barcode.id)).use {
            awaitShown(name, "A0864213")
            check(device.wait(Until.hasObject(By.text(name)), TIMEOUT)) { "The full name is not read out" }
        }
    }

    private fun id(name: String) = repository.state.value.barcodes.first { it.name == name }.id

    private fun awaitShown(name: String, athleteId: String) {
        check(device.wait(Until.hasObject(By.text(athleteId)), TIMEOUT)) {
            ByteArrayOutputStream().also(device::dumpWindowHierarchy).toString().lines().forEach { Log.e(TAG, it) }
            "$name was not shown"
        }
        val deadline = SystemClock.uptimeMillis() + TIMEOUT
        while (repository.shownBarcodeId != id(name)) {
            check(SystemClock.uptimeMillis() < deadline) { "$name was not recorded as shown" }
            SystemClock.sleep(100)
        }
    }

    private companion object {
        const val TIMEOUT = 30_000L
        const val STARTUP_TIMEOUT = 180_000L
        const val TAG = "BarcodeLinkTest"
    }
}
