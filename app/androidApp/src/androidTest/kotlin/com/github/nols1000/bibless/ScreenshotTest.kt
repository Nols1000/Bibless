package com.github.nols1000.bibless

import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.github.nols1000.bibless.barcode.BarcodeFormat
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

    private val repository = Bibless.repository(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() {
        // DemoData replaces the saved barcodes, so never run this on a real device
        check(Build.HARDWARE == "ranchu") { "Screenshot tests only run on an emulator (they replace saved barcodes)" }
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
        DemoData.load(repository)
        dismissSystemDialogs()
    }

    /**
     * Captures the screens that tools/screenshots/frame.py builds the store screenshots from, as
     * listed in fastlane/screenshots/captions.json.
     */
    @Test
    fun screenshots() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // A cold emulator can take a while to render the first frame
            val me = checkNotNull(device.wait(Until.findObject(By.text("Me")), FIRST_FRAME_TIMEOUT)) {
                "Demo barcode list did not appear; the screen shows ${shownTexts()}"
            }
            capture("list")

            me.click()
            check(device.wait(Until.gone(By.text("Sam")), TIMEOUT)) { "Barcode did not open" }
            capture("barcode")

            device.pressBack()
            // Set in the repository rather than tapped, so the screen draws with it already selected
            repository.setOpenOnLaunch(LaunchScreen.FIRST_BARCODE)
            checkNotNull(device.wait(Until.findObject(By.desc("Settings")), TIMEOUT)) { "List did not return" }.click()
            val option = By.checked(true).hasDescendant(By.text("First barcode"))
            checkNotNull(device.wait(Until.findObject(option), TIMEOUT)) { "First barcode is not marked to open on launch" }
            capture("settings")
        }

        // The other format, in light and dark mode; relaunching opens straight on the first barcode, Me
        repository.setDefaultFormat(Device.PHONE, BarcodeFormat.CODE128)
        captureOnLaunch("barcode-code128")
        device.executeShellCommand("cmd uimode night yes")
        captureOnLaunch("barcode-dark")
    }

    /**
     * Answers the "isn't responding" dialog that the system UI or launcher can raise on a freshly
     * booted CI emulator, and closes any other system dialog, so none covers the app.
     */
    private fun dismissSystemDialogs() {
        device.findObject(By.text("Wait"))?.takeIf { device.hasObject(By.textContains("isn't responding")) }?.click()
        device.executeShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
        device.waitForIdle()
    }

    /** The texts on screen, for failure messages: the fastlane log shows those but no logcat. */
    private fun shownTexts(): List<String> =
        device.findObjects(By.textContains("")).mapNotNull { it.text?.takeIf(String::isNotBlank) }

    /**
     * Captures the screen as [name]. The emulator can still hand out the previous frame right after
     * a screen change, even once the new screen reports idle, so give it a moment to draw.
     */
    private fun capture(name: String) {
        device.waitForIdle()
        SystemClock.sleep(SETTLE_MILLIS)
        Screengrab.screenshot(name)
    }

    /** Launches the app, waits for it to open on the default barcode and captures it as [name]. */
    private fun captureOnLaunch(name: String) {
        ActivityScenario.launch(MainActivity::class.java).use {
            checkNotNull(device.wait(Until.findObject(By.text("A0123456")), TIMEOUT)) { "App did not start" }
            // The list shows for a frame before the barcode opens on top of it
            check(device.wait(Until.gone(By.text("Sam")), TIMEOUT)) { "Barcode did not open on launch" }
            capture(name)
        }
    }

    @After
    fun tearDown() {
        device.executeShellCommand("cmd uimode night no")
    }

    companion object {
        private const val TIMEOUT = 30_000L
        /** A freshly booted emulator keeps busy for minutes; later screens come quickly. */
        private const val FIRST_FRAME_TIMEOUT = 90_000L
        private const val SETTLE_MILLIS = 1_000L

        @get:ClassRule
        @JvmStatic
        val localeTestRule = LocaleTestRule()
    }
}
