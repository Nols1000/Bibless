package com.github.nols1000.bibless

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

/** Captures the Play Store phone screenshots. Run through fastlane: `bundle exec fastlane android screenshots`. */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun setUp() {
        // DemoData replaces the saved barcodes, so never run this on a real device
        check(Build.HARDWARE == "ranchu") { "Screenshot tests only run on an emulator (they replace saved barcodes)" }
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
        DemoData.load(Bibless.repository(ApplicationProvider.getApplicationContext()))
    }

    @Test
    fun screenshots() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // A cold emulator can take a while to render the first frame
            val me = checkNotNull(device.wait(Until.findObject(By.text("Me")), TIMEOUT)) { "Demo barcode list did not appear" }
            device.waitForIdle()
            Screengrab.screenshot("01-list")

            me.click()
            device.wait(Until.gone(By.text("Sam")), TIMEOUT)
            device.waitForIdle()
            Screengrab.screenshot("02-barcode")

            device.pressBack()
            checkNotNull(device.wait(Until.findObject(By.desc("Settings")), TIMEOUT)) { "List did not return" }.click()
            // Mark the barcode to open on launch; radio rows are checkable, unlike the list rows
            val option = By.checkable(true).hasDescendant(By.text("Me"))
            val row = checkNotNull(device.wait(Until.findObject(option), TIMEOUT)) { "Settings did not appear" }
            row.click()
            check(row.wait(Until.checked(true), TIMEOUT)) { "Default not selected" }
            device.waitForIdle()
            Screengrab.screenshot("04-settings")
        }

        // Relaunching in dark mode opens straight on the default barcode, still black on white
        device.executeShellCommand("cmd uimode night yes")
        ActivityScenario.launch(MainActivity::class.java).use {
            checkNotNull(device.wait(Until.findObject(By.text("A0123456")), TIMEOUT)) { "App did not start" }
            // The list shows for a frame before the barcode opens on top of it
            check(device.wait(Until.gone(By.text("Sam")), TIMEOUT)) { "Barcode did not open on launch" }
            device.waitForIdle()
            Screengrab.screenshot("03-barcode-dark")
        }
    }

    @After
    fun tearDown() {
        device.executeShellCommand("cmd uimode night no")
    }

    companion object {
        private const val TIMEOUT = 30_000L

        @get:ClassRule
        @JvmStatic
        val localeTestRule = LocaleTestRule()
    }
}
