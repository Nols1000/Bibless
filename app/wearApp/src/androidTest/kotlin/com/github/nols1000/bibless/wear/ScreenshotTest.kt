package com.github.nols1000.bibless.wear

import android.app.Activity
import android.app.Instrumentation
import android.app.RemoteInput
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.os.bundleOf
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.wear.input.RemoteInputIntentHelper
import com.github.nols1000.bibless.Bibless
import com.github.nols1000.bibless.DemoData
import com.github.nols1000.bibless.Device
import com.github.nols1000.bibless.LaunchScreen
import com.github.nols1000.bibless.barcode.BarcodeFormat
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule
import java.io.ByteArrayOutputStream

/** Captures the Play Store Wear OS screenshots. Run through fastlane: `bundle exec fastlane android screenshots`. */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val repository = Bibless.repository(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() {
        // DemoData replaces the saved barcodes, so never run this on a real device
        check(Build.HARDWARE == "ranchu") { "Screenshot tests only run on an emulator (they replace saved barcodes)" }
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
        // A freshly created Wear OS emulator covers everything with "Starting…" for a while after boot
        val starting = By.res("com.google.android.wearable.sysui", "starting_screen_text_view")
        check(device.wait(Until.gone(starting), STARTUP_TIMEOUT)) { "Watch did not finish starting" }
        DemoData.load(repository)
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    /**
     * The file names set the store order, as on the phones: the code in both formats, the list,
     * adding one, then the launch setting. A square watch adds its own take on the first three
     * after the round ones; Google Play takes 8 Wear OS screenshots at most.
     */
    @Test
    fun screenshots() {
        val round = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.isScreenRound
        ActivityScenario.launch(MainActivity::class.java).use {
            // A cold emulator can take a while to render the first frame
            val me = waitFor(By.text("Me")) { "Demo barcode list did not appear" }
            capture(if (round) "03-list" else "08-square-list")

            me.click()
            check(device.wait(Until.gone(By.text("Sam")), TIMEOUT)) { "Barcode did not open" }
            capture(if (round) "01-qr" else "06-square-qr")
            // The open code follows the format setting
            repository.setDefaultFormat(Device.WATCH, BarcodeFormat.CODE128)
            capture(if (round) "02-barcode" else "07-square-barcode")
            repository.setDefaultFormat(Device.WATCH, BarcodeFormat.QR)
            if (!round) return

            device.pressBack()
            scrollTo(By.text("Add")).click()
            val addHeader = waitFor(By.text("Add barcode")) { "Add screen did not open" }
            device.waitForIdle()
            val (name, athleteId) = DemoData.newBarcode
            enterText("Name", name)
            enterText("Athlete ID", athleteId)
            // Show the enabled Save button below the filled fields
            nudgeIntoView(By.text("Save"), addHeader)
            capture("04-add")

            device.pressBack()
            check(device.wait(Until.gone(By.text("Add barcode")), TIMEOUT)) { "Add screen did not close" }
            // Set in the repository rather than tapped, so the screen draws with it already selected
            repository.setOpenOnLaunch(LaunchScreen.FIRST_BARCODE)
            scrollTo(By.text("Settings")).click()
            val header = waitFor(By.text("Open on launch")) { "Settings did not open" }
            waitFor(By.checked(true).hasDescendant(By.text("First barcode"))) {
                "First barcode is not marked to open on launch"
            }
            // Show the marked row below the header
            nudgeIntoView(By.text("First barcode"), header)
            capture("05-settings")
        }
    }

    /**
     * Captures the screen as [name]. The emulator can still hand out the previous frame right after
     * a screen change, even once the new screen reports idle, so give it a moment to draw.
     */
    private fun capture(name: String) {
        device.waitForIdle()
        SystemClock.sleep(SETTLE_MILLIS)
        Screengrab.screenshot(name)
    }

    /** Waits for an object matching [selector]; dumps the screen's hierarchy to logcat if it never shows up. */
    private fun waitFor(selector: BySelector, message: () -> String): UiObject2 =
        device.wait(Until.findObject(selector), TIMEOUT) ?: run {
            ByteArrayOutputStream().also(device::dumpWindowHierarchy).toString().lines().forEach { Log.e(TAG, it) }
            error(message())
        }

    /**
     * Taps the add form's [label] button and answers the system text input it opens with [text], so
     * the test needn't drive the watch keyboard (Gboard asks for contact access on first use).
     */
    private fun enterText(label: String, text: String) {
        val result = Intent()
        RemoteInput.addResultsToIntent(arrayOf(RemoteInput.Builder(label).build()), result, bundleOf(label to text))
        intending(hasAction(RemoteInputIntentHelper.createActionRemoteInputIntent().action))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, result))
        waitFor(By.text(label)) { "No $label button" }.click()
        waitFor(By.text(text)) { "$label was not entered" }
    }

    /** Scrolls the screen's list down until an object matching [selector] shows up. */
    private fun scrollTo(selector: BySelector): UiObject2 {
        val list = waitFor(By.scrollable(true)) { "No list to scroll" }
        return checkNotNull(list.scrollUntil(Direction.DOWN, Until.findObject(selector))) { "$selector not found" }
    }

    /**
     * The round screen fits a header and about two rows: drags the list up a little at a time,
     * slowly enough that it doesn't fling on, until [selector] clears the bottom edge while
     * [header] stays on screen.
     */
    private fun nudgeIntoView(selector: BySelector, header: UiObject2) {
        val x = device.displayWidth / 2
        val y = device.displayHeight / 2
        val bottom = device.displayHeight * 87 / 100
        var nudges = 0
        while ((device.findObject(selector)?.visibleBounds?.bottom ?: Int.MAX_VALUE) > bottom) {
            check(nudges++ < 10) { "$selector did not scroll into view" }
            device.drag(x, y + NUDGE / 2, x, y - NUDGE / 2, 50)
            device.waitForIdle()
        }
        check(header.visibleBounds.top > 0) { "Header scrolled away" }
    }

    companion object {
        private const val TIMEOUT = 30_000L
        private const val SETTLE_MILLIS = 1_000L
        private const val STARTUP_TIMEOUT = 180_000L
        private const val TAG = "ScreenshotTest"

        /** Drag distance in pixels; touch slop eats part of it, so the list moves less. */
        private const val NUDGE = 40

        @get:ClassRule
        @JvmStatic
        val localeTestRule = LocaleTestRule()
    }
}
