package com.github.nols1000.bibless

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Opening a barcode from outside the app, as widgets and shortcuts do. */
@RunWith(AndroidJUnit4::class)
class BarcodeLinkTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = Bibless.repository(context)

    @Before
    fun setUp() {
        // DemoData replaces the saved barcodes, so never run this on a real device
        check(Build.HARDWARE == "ranchu") { "UI tests only run on an emulator (they replace saved barcodes)" }
        DemoData.load(repository)
    }

    @Test
    fun opensTheLinkedBarcode() {
        ActivityScenario.launch<MainActivity>(BarcodeLink.intent(context, id("Sam"))).use {
            awaitShown("Sam", "A0246802")
        }
    }

    @Test
    fun replacesTheOpenBarcode() {
        ActivityScenario.launch<MainActivity>(BarcodeLink.intent(context, id("Sam"))).use {
            awaitShown("Sam", "A0246802")

            context.startActivity(BarcodeLink.intent(context, id("Me")))
            awaitShown("Me", "A0123456")

            // On top of the list, not on top of Sam
            device.pressBack()
            check(device.wait(Until.hasObject(By.text("Jamie (junior)")), TIMEOUT)) { "List is not below the barcode" }
        }
    }

    private fun id(name: String) = repository.state.value.barcodes.first { it.name == name }.id

    private fun awaitShown(name: String, athleteId: String) {
        check(device.wait(Until.hasObject(By.text(athleteId)), TIMEOUT)) { "$name was not shown" }
        val deadline = SystemClock.uptimeMillis() + TIMEOUT
        while (repository.shownBarcodeId != id(name)) {
            check(SystemClock.uptimeMillis() < deadline) { "$name was not recorded as shown" }
            SystemClock.sleep(100)
        }
    }

    private companion object {
        const val TIMEOUT = 30_000L
    }
}
