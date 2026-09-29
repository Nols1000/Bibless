package com.github.nols1000.bibless.wear

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.github.nols1000.bibless.Bibless
import com.github.nols1000.bibless.DemoData
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

/** Captures the Play Store Wear OS screenshots. Run through fastlane: `bundle exec fastlane android screenshots`. */
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
            device.wait(Until.hasObject(By.text("Me")), TIMEOUT)
            Screengrab.screenshot("01-list")

            device.findObject(By.text("Me")).click()
            device.wait(Until.gone(By.text("Sam")), TIMEOUT)
            device.waitForIdle()
            Screengrab.screenshot("02-barcode")
        }
    }

    companion object {
        private const val TIMEOUT = 10_000L

        @get:ClassRule
        @JvmStatic
        val localeTestRule = LocaleTestRule()
    }
}
