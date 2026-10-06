package com.github.nols1000.bibless

import android.content.Context
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.github.nols1000.bibless.barcode.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Swiping up or down to the next barcode, and sideways to the other format. */
@RunWith(AndroidJUnit4::class)
class BarcodePagingTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = Bibless.repository(context)

    @Before
    fun setUp() {
        // DemoData replaces the saved barcodes, so never run this on a real device
        check(Build.HARDWARE == "ranchu") { "UI tests only run on an emulator (they replace saved barcodes)" }
        DemoData.load(repository)
        repository.setDefaultFormat(Device.PHONE, BarcodeFormat.CODE128)
    }

    @Test
    fun keepsTheSwipedFormatForTheNextBarcode() {
        ActivityScenario.launch<MainActivity>(BarcodeLink.intent(context, id("Me"))).use {
            awaitCode("Barcode for A0123456").swipe(Direction.LEFT, 0.8f)
            awaitCode("QR code for A0123456").swipe(Direction.UP, 0.8f)
            awaitCode("QR code for A0246802")
            // Only for this screen, not as the default
            assertEquals(BarcodeFormat.CODE128, repository.state.value.format)
        }
    }

    @Test
    fun swipesBackToTheDefaultFormat() {
        ActivityScenario.launch<MainActivity>(BarcodeLink.intent(context, id("Me"))).use {
            awaitCode("Barcode for A0123456").swipe(Direction.LEFT, 0.8f)
            awaitCode("QR code for A0123456").swipe(Direction.RIGHT, 0.8f)
            awaitCode("Barcode for A0123456")
        }
    }

    private fun id(name: String) = repository.state.value.barcodes.first { it.name == name }.id

    /** Waits for the code, and for its page to finish sliding in, so the next swipe starts on it. */
    private fun awaitCode(description: String): UiObject2 {
        check(device.wait(Until.hasObject(By.desc(description)), TIMEOUT)) { "$description was not shown" }
        device.waitForIdle()
        return device.findObject(By.desc(description))
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
