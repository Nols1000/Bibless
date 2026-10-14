package com.github.nols1000.bibless

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.nols1000.bibless.barcode.BarcodeFormat
import com.github.nols1000.bibless.race.TimingMode
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The phone screens the landing page (app/webApp/src/landing) shows in its device frame, on a 20:9
 * screen (1080x2400) like current phones. Every run walks through the screens; with
 * `-PsiteScreenshots` Roborazzi also writes them to app/webApp/public/screens/phone.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w432dp-h960dp-port-400dpi")
class SiteScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = object : RaceClock {
        var now = 1_000_000L
        override fun wallMillis() = 1_700_000_000_000 + now
        override fun monotonicMillis() = now
        override fun bootId() = "boot"
    }
    private val device = RaceDevice(InMemoryStore(), clock, CoroutineScope(Dispatchers.Default))
    private val repository = BarcodeRepository(InMemoryStore(), Device.PHONE).also(DemoData::load)
    private val timer = TimerRepository(device)
    private val scanner = ScannerRepository(device)

    @Before
    fun setUp() {
        // In a typical user's colours, see SampleColors
        SampleColors.replaceDeviceColors = true
    }

    @After
    fun tearDown() {
        SampleColors.replaceDeviceColors = false
    }

    private fun showApp() = compose.setContent { App(repository, timer, scanner, device) }

    /**
     * The list, reached by opening a barcode and going back. Captured as the app's first screen, it
     * sometimes came out blank when the watch's tests had run first in the same build.
     */
    private fun showList() {
        showApp()
        openMe()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Sam").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openMe() {
        compose.onNodeWithText("Me").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Sam").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun list() {
        showList()
        compose.onNodeWithText("Jamie (junior)").assertExists()
        capture("list")
    }

    /** The list once the barcode added on the watch has synced. */
    @Test
    fun listSynced() {
        val (name, athleteId) = DemoData.newBarcode
        repository.add(name, athleteId)
        showList()
        compose.onNodeWithText(name).assertExists()
        capture("list-synced")
    }

    @Test
    fun barcode() {
        showApp()
        openMe()
        capture("barcode")
    }

    @Test
    fun barcodeCode128() {
        repository.setDefaultFormat(Device.PHONE, BarcodeFormat.CODE128)
        showApp()
        openMe()
        capture("barcode-code128")
    }

    @Test
    fun barcodeDark() {
        RuntimeEnvironment.setQualifiers("+night")
        repository.setDefaultFormat(Device.PHONE, BarcodeFormat.CODE128)
        showApp()
        openMe()
        capture("barcode-dark")
    }

    @Test
    fun settings() {
        showApp()
        compose.onNodeWithText("Me").assertExists()
        // Set once the list shows, since the app would otherwise open on the barcode
        repository.setOpenOnLaunch(LaunchScreen.FIRST_BARCODE)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Open on launch").assertExists()
        capture("settings")
    }

    /** A timer mid-run: three finishers in, the clock at 17:31. */
    @Test
    fun timer() {
        recordFinishes()
        clock.now += 16_000
        showApp()
        compose.onNodeWithText("3 finishers").assertExists()
        capture("timer")
    }

    @Test
    fun sessions() {
        recordFinishes()
        timer.stop()
        showApp()
        compose.onNodeWithContentDescription("Timer").performClick()
        compose.onNodeWithText("Recorded sessions").performClick()
        compose.onNodeWithText("3 finish times", substring = true).assertExists()
        capture("sessions")
    }

    /** The finish scanner after two runners; the page animates the camera area. */
    @Test
    fun scanner() {
        var onCode: ((String) -> Unit)? = null
        scanner.start(TimingMode.TOKENS)
        compose.setContent {
            val state by scanner.state.collectAsState()
            ScannerScreen(
                session = state.session,
                joinedRun = null,
                onJoin = {},
                onLeave = {},
                onSessions = {},
                onStart = scanner::start,
                onScan = scanner::scan,
                onEnter = scanner::enter,
                onUnknown = scanner::addUnknown,
                onUndo = scanner::undo,
                onStop = scanner::stop,
                onBack = {},
                camera = { code, modifier ->
                    onCode = code
                    Box(modifier.background(Color(0xFF15171A)))
                },
            )
        }
        listOf("A0123456", "P0041", "A0246802", "P0042").forEach { code ->
            clock.now += 2_000
            compose.runOnIdle { onCode!!(code) }
        }
        compose.onNodeWithText("2 finishers").assertExists()
        capture("scanner")
    }

    private fun recordFinishes() {
        timer.start()
        listOf(16 * 60_000 + 48_000L, 14_000L, 13_000L).forEach {
            clock.now += it
            timer.tap()
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        compose.waitForIdle()
        val file = "${System.getProperty("siteScreenshotDir") ?: "build/outputs/roborazzi/site"}/$name.png"
        captureScreenRoboImage(file)
        // Roborazzi only writes the file when recording (-PsiteScreenshots), and never a blank one
        BitmapFactory.decodeFile(file)?.let { assertFalse("$name.png is blank", isBlank(it)) }
    }

    /** Whether every pixel on a coarse grid is the same colour, as in a capture of an empty window. */
    private fun isBlank(bitmap: Bitmap) =
        (0 until bitmap.height step 40).flatMap { y -> (0 until bitmap.width step 40).map { x -> bitmap.getPixel(x, y) } }
            .distinct().size == 1

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
