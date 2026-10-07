package com.github.nols1000.bibless

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.nols1000.bibless.barcode.BarcodeFormat
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Play Store phone screenshots, 1080x2160: Google Play takes none longer than twice its width.
 * Every run walks through the screens; with `-PstoreScreenshots` Roborazzi also writes them for
 * fastlane (`bundle exec fastlane android screenshots`), which uploads them as they are.
 *
 * The file names set the store order: the code in both formats, in dark mode, the list, then the
 * launch setting.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w432dp-h864dp-port-400dpi")
class ScreenshotTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val repository = Bibless.repository(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() {
        DemoData.load(repository)
    }

    @Test
    fun screenshots() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Me").assertExists()
            capture("04-list")

            compose.onNodeWithText("Me").performClick()
            compose.onNodeWithText("Sam").assertDoesNotExist()
            capture("01-barcode")

            pressBack()
            // Set in the repository rather than tapped, so the screen draws with it already selected
            repository.setOpenOnLaunch(LaunchScreen.FIRST_BARCODE)
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNode(isSelected() and hasText("First barcode")).assertExists()
            capture("05-settings")
        }

        // The other format, in light and dark mode; relaunching opens straight on the first barcode, Me
        repository.setDefaultFormat(Device.PHONE, BarcodeFormat.CODE128)
        captureOnLaunch("02-barcode-code128")
        RuntimeEnvironment.setQualifiers("+night")
        captureOnLaunch("03-barcode-dark")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("${System.getProperty("screenshotDir") ?: "build/outputs/roborazzi"}/$name.png")
    }

    /** Launches the app, waits for it to open on the default barcode and captures it as [name]. */
    private fun captureOnLaunch(name: String) {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("A0123456").assertExists()
            // The list shows for a frame before the barcode opens on top of it
            compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Sam").fetchSemanticsNodes().isEmpty() }
            capture(name)
        }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
