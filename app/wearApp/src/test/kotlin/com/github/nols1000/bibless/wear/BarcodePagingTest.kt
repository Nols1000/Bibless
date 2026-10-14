package com.github.nols1000.bibless.wear

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.nols1000.bibless.BarcodeLink
import com.github.nols1000.bibless.Bibless
import com.github.nols1000.bibless.DemoData
import com.github.nols1000.bibless.Device
import com.github.nols1000.bibless.barcode.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Swiping up or down to the next barcode, and sideways to the other format. */
@RunWith(AndroidJUnit4::class)
class BarcodePagingTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = Bibless.repository(context)

    @Before
    fun setUp() {
        DemoData.load(repository)
        repository.setDefaultFormat(Device.WATCH, BarcodeFormat.CODE128)
    }

    @Test
    fun keepsTheSwipedFormatForTheNextBarcode() {
        ActivityScenario.launch<MainActivity>(BarcodeLink.intent(context, id("Me"))).use {
            swipe("Barcode for A0123456") { swipeLeft() }
            swipe("QR code for A0123456") { swipeUp() }
            awaitCode("QR code for A0246802")
            // Only for this screen, not as the default
            assertEquals(BarcodeFormat.CODE128, repository.state.value.format)
        }
    }

    /**
     * Swiping right on the first format still goes back, as everywhere else on the watch. On API 34
     * and later the system takes that swipe as the back gesture, which Robolectric doesn't draw, so
     * this runs where the app's own swipe to dismiss handles it.
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun swipingRightOnTheFirstFormatGoesBack() {
        ActivityScenario.launch<MainActivity>(BarcodeLink.intent(context, id("Me"))).use {
            swipe("Barcode for A0123456") { swipeLeft() }
            swipe("QR code for A0123456") { swipeRight() }
            awaitCode("Barcode for A0123456")
            // From the left edge, as the system's swipe to dismiss expects
            compose.onRoot().performTouchInput { swipeRight(startX = left, endX = right * 4 / 5, durationMillis = 400) }
            compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Barcodes").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    private fun id(name: String) = repository.state.value.barcodes.first { it.name == name }.id

    private fun swipe(description: String, gesture: TouchInjectionScope.() -> Unit) {
        awaitCode(description)
        compose.onNodeWithContentDescription(description).performTouchInput(gesture)
        compose.waitForIdle()
    }

    private fun awaitCode(description: String) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
