package com.github.nols1000.bibless

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opening a barcode from outside the app, as widgets and shortcuts do. */
@RunWith(AndroidJUnit4::class)
class BarcodeLinkTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = Bibless.repository(context)

    @Before
    fun setUp() {
        DemoData.load(repository)
    }

    @Test
    fun opensTheLinkedBarcode() {
        ActivityScenario.launch<MainActivity>(BarcodeLink.intent(context, id("Sam"))).use {
            awaitShown("Sam", "A0246802")
        }
    }

    @Test
    fun replacesTheOpenBarcode() {
        ActivityScenario.launch<MainActivity>(BarcodeLink.intent(context, id("Sam"))).use { scenario ->
            awaitShown("Sam", "A0246802")

            // MainActivity is singleTop, so a link while it runs arrives as a new intent
            scenario.onActivity {
                InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, BarcodeLink.intent(context, id("Me")))
            }
            awaitShown("Me", "A0123456")

            // On top of the list, not on top of Sam
            pressBack()
            compose.onNodeWithText("Jamie (junior)").assertExists()
        }
    }

    /** Pending intents that differ only in extras would be reused for another barcode. */
    @Test
    fun keepsLinksToDifferentBarcodesApart() {
        assertFalse(BarcodeLink.intent(context, id("Me")).filterEquals(BarcodeLink.intent(context, id("Sam"))))
    }

    private fun id(name: String) = repository.state.value.barcodes.first { it.name == name }.id

    private fun awaitShown(name: String, athleteId: String) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText(athleteId).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(TIMEOUT) { repository.shownBarcodeId == id(name) }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
