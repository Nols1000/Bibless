package com.github.nols1000.bibless.wear

import android.app.Activity
import android.app.Instrumentation
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.input.RemoteInputIntentHelper
import com.github.nols1000.bibless.BarcodeRepository
import com.github.nols1000.bibless.DemoData
import com.github.nols1000.bibless.Device
import com.github.nols1000.bibless.InMemoryStore
import com.github.nols1000.bibless.LaunchScreen
import com.github.nols1000.bibless.RaceClock
import com.github.nols1000.bibless.RaceDevice
import com.github.nols1000.bibless.TimerRepository
import com.github.nols1000.bibless.barcode.BarcodeFormat
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The watch screens the landing page (app/webApp/src/landing) shows in its watch frames, on the
 * large round watch (see robolectric.properties). Every run walks through the screens; with
 * `-PsiteScreenshots` Roborazzi also writes them to app/webApp/public/screens/watch.
 */
@RunWith(AndroidJUnit4::class)
class SiteScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = object : RaceClock {
        var now = 1_000_000L
        override fun wallMillis() = 1_700_000_000_000 + now
        override fun monotonicMillis() = now
        override fun bootId() = "boot"
    }
    private val repository = BarcodeRepository(InMemoryStore(), Device.WATCH).also(DemoData::load)
    private val timer = TimerRepository(RaceDevice(InMemoryStore(), clock, CoroutineScope(Dispatchers.Default)))

    @Before
    fun setUp() = Intents.init()

    @After
    fun tearDown() = Intents.release()

    private fun showApp() = compose.setContent { WearApp(repository, timer) }

    private fun openMe() {
        compose.onNodeWithText("Me").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Sam").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun list() {
        showApp()
        compose.onNodeWithText("Me").assertExists()
        capture("list")
    }

    /**
     * The list on a tall, flat screen the width of the scrolling watch frame's strip, so the page
     * can slide it up through the watch.
     */
    @Test
    @Config(qualifiers = "w168dp-h376dp-notround-watch-xhdpi")
    fun listTall() {
        showApp()
        compose.onNodeWithText("Me").assertExists()
        capture("list-tall")
    }

    @Test
    fun qr() {
        showApp()
        openMe()
        capture("qr")
    }

    @Test
    fun barcode() {
        repository.setDefaultFormat(Device.WATCH, BarcodeFormat.CODE128)
        showApp()
        openMe()
        capture("barcode")
    }

    @Test
    fun add() {
        showApp()
        scrollTo(hasText("Add")).performClick()
        compose.onNodeWithText("Add barcode").assertExists()
        val (name, athleteId) = DemoData.newBarcode
        enterText("Name", name)
        enterText("Athlete ID", athleteId)
        scrollToEnd()
        compose.onNode(hasText("Save") and isEnabled()).assertExists()
        capture("add")
    }

    @Test
    fun settings() {
        showApp()
        compose.onNodeWithText("Me").assertExists()
        // Set once the list shows, since the app would otherwise open on the barcode
        repository.setOpenOnLaunch(LaunchScreen.FIRST_BARCODE)
        scrollTo(hasText("Settings")).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(isSelected() and hasText("First barcode"))
        compose.onNodeWithText("Open on launch").assertExists()
        capture("settings")
    }

    /** The watch timer mid-run, which the app opens on: three finishers in. */
    @Test
    fun timer() {
        timer.start()
        listOf(16 * 60_000 + 48_000L, 14_000L, 13_000L).forEach {
            clock.now += it
            timer.tap()
        }
        clock.now += 16_000
        showApp()
        compose.onNodeWithText("P4").assertExists()
        capture("timer")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("${System.getProperty("siteScreenshotDir") ?: "build/outputs/roborazzi/site"}/$name.png")
    }

    /** Answers the system text input that the add form's [label] button opens with [text]. */
    private fun enterText(label: String, text: String) {
        val result = Intent()
        RemoteInput.addResultsToIntent(arrayOf(RemoteInput.Builder(label).build()), result, Bundle().apply { putCharSequence(label, text) })
        intending(hasAction(RemoteInputIntentHelper.createActionRemoteInputIntent().action))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, result))
        compose.onNodeWithText(label).performClick()
        compose.onNodeWithText(text).assertExists()
    }

    /** Swipes the screen's list up until a node matching [matcher] is on screen. */
    private fun scrollTo(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        val node = compose.onNode(matcher)
        var swipes = 0
        while (compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() || !node.isDisplayed()) {
            check(swipes++ < 10) { "$matcher not found" }
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        return node
    }

    /** Swipes the screen's list up until it can't scroll any further. */
    private fun scrollToEnd() {
        val list = compose.onNode(hasScrollAction())
        var swipes = 0
        while (list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].let { it.value() < it.maxValue() }) {
            check(swipes++ < 10) { "The list did not reach its end" }
            list.performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
