package com.github.nols1000.bibless.wear

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.nols1000.bibless.Bibless
import com.github.nols1000.bibless.DemoData
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Swiping a barcode away in the list, with a few seconds to undo it. */
@RunWith(AndroidJUnit4::class)
class SwipeToDeleteTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = Bibless.repository(context)

    @Before
    fun setUp() {
        DemoData.load(repository)
    }

    @Test
    fun undoKeepsTheBarcode() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val sam = id("Sam")
            swipeAway("Sam")
            compose.onNodeWithText("Undo").performClick()

            await("A0246802")
            compose.mainClock.advanceTimeBy(UNDO_MILLIS + 1_000)
            assertNotNull("Sam was deleted despite the undo", repository.find(sam))
        }
    }

    @Test
    fun deletesOnceTheUndoIsGone() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val sam = id("Sam")
            swipeAway("Sam")
            assertNotNull("Sam was deleted before the undo ran out", repository.find(sam))

            compose.mainClock.advanceTimeBy(UNDO_MILLIS + 1_000)
            compose.waitUntil(TIMEOUT) { repository.find(sam) == null }
            assertNull("Sam was not deleted", repository.find(sam))
        }
    }

    private fun id(name: String) = repository.state.value.barcodes.first { it.name == name }.id

    /** Drags the row holding [name] across the screen, past the point where the delete button would only show. */
    private fun swipeAway(name: String) {
        await(name)
        val y = compose.onNode(hasText(name), useUnmergedTree = true).getBoundsInRoot().let { (it.top + it.bottom) / 2 }
        compose.onRoot().performTouchInput {
            val row = y.toPx()
            swipe(Offset(width * 0.88f, row), Offset(width * 0.07f, row), durationMillis = 400)
        }
        await("Undo")
    }

    private fun await(text: String) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
