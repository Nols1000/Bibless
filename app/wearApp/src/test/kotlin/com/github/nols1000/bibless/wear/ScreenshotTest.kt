package com.github.nols1000.bibless.wear

import android.app.Activity
import android.app.Instrumentation
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.input.RemoteInputIntentHelper
import com.github.nols1000.bibless.Bibless
import com.github.nols1000.bibless.DemoData
import com.github.nols1000.bibless.Device
import com.github.nols1000.bibless.LaunchScreen
import com.github.nols1000.bibless.SampleColors
import com.github.nols1000.bibless.barcode.BarcodeFormat
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Play Store Wear OS screenshots, on a large round watch (see robolectric.properties). Every run
 * walks through the screens; with `-PstoreScreenshots` Roborazzi also writes them for fastlane
 * (`bundle exec fastlane android screenshots`).
 *
 * The file names set the store order, as on the phones: the code in both formats, the list, adding
 * one, then the launch setting.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val repository = Bibless.repository(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() {
        DemoData.load(repository)
        // In a typical user's colours, see SampleColors
        SampleColors.replaceDeviceColors = true
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
        SampleColors.replaceDeviceColors = false
    }

    @Test
    fun screenshots() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Me").assertExists()
            capture("03-list")

            compose.onNodeWithText("Me").performClick()
            compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Sam").fetchSemanticsNodes().isEmpty() }
            capture("01-qr")
            // The open code follows the format setting
            repository.setDefaultFormat(Device.WATCH, BarcodeFormat.CODE128)
            capture("02-barcode")
            repository.setDefaultFormat(Device.WATCH, BarcodeFormat.QR)

            pressBack()
            // The code's pager scrolls too, so let it close before looking for the list
            compose.onNodeWithText("Sam").assertExists()
            scrollTo(hasText("Add")).performClick()
            compose.onNodeWithText("Add barcode").assertExists()
            val (name, athleteId) = DemoData.newBarcode
            enterText("Name", name)
            enterText("Athlete ID", athleteId)
            // The Save edge button only grows in at the end of the list
            scrollToEnd()
            compose.onNode(hasText("Save") and isEnabled()).assertIsDisplayed()
            capture("04-add")

            pressBack()
            compose.onNodeWithText("Add barcode").assertDoesNotExist()
            // Set in the repository rather than tapped, so the screen draws with it already selected
            repository.setOpenOnLaunch(LaunchScreen.FIRST_BARCODE)
            scrollTo(hasText("Settings")).performClick()
            compose.onNodeWithText("Open on launch").assertExists()
            // Show the marked row below the header
            compose.onNode(hasScrollAction()).performScrollToNode(isSelected() and hasText("First barcode"))
            compose.onNodeWithText("Open on launch").assertExists()
            capture("05-settings")
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("${System.getProperty("screenshotDir") ?: "build/outputs/roborazzi"}/$name.png")
    }

    /**
     * Taps the add form's [label] button and answers the system text input it opens with [text], so
     * the test needn't drive the watch keyboard.
     */
    private fun enterText(label: String, text: String) {
        val result = Intent()
        RemoteInput.addResultsToIntent(arrayOf(RemoteInput.Builder(label).build()), result, Bundle().apply { putCharSequence(label, text) })
        intending(hasAction(RemoteInputIntentHelper.createActionRemoteInputIntent().action))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, result))
        compose.onNodeWithText(label).performClick()
        compose.onNodeWithText(text).assertExists()
    }

    /**
     * Swipes the screen's list up until a node matching [matcher] is on screen. An edge button only
     * grows in once the list stops at its end, so it isn't in the list for performScrollToNode.
     */
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
