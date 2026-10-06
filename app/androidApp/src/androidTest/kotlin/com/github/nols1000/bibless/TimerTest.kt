package com.github.nols1000.bibless

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The phone stopwatch, on a fake clock so the times are exact. */
@RunWith(AndroidJUnit4::class)
class TimerTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = object : RaceClock {
        var now = 1_000_000L
        override fun wallMillis() = 1_700_000_000_000 + now
        override fun monotonicMillis() = now
        override fun bootId() = "boot"
    }
    private val device = RaceDevice(InMemoryStore(), clock, CoroutineScope(Dispatchers.Default))
    private val timer = TimerRepository(device)

    private fun showApp() = compose.setContent {
        App(BarcodeRepository(InMemoryStore(), Device.PHONE), timer, ScannerRepository(device))
    }

    @Test
    fun timesFinishersFromTheList() {
        showApp()
        compose.onNodeWithContentDescription("Timer").performClick()
        compose.onNodeWithText("Start").performClick()

        clock.now += 17 * 60_000 + 2_000
        compose.onNodeWithText("Finish").performClick()
        clock.now += 13_000
        compose.onNodeWithText("Finish").performClick()

        compose.onNodeWithText("2 finishers").assertExists()
        compose.onNodeWithText("P1").assertExists()
        compose.onNodeWithText("17:02").assertExists()
        compose.onNodeWithText("Undo P2 (17:15)").performClick()
        compose.onNodeWithText("1 finisher").assertExists()
        compose.onNodeWithText("P2").assertDoesNotExist()
        assertEquals(listOf(17 * 60_000 + 2_000L), timer.state.value.session!!.times.map { it.elapsedMs })
    }

    @Test
    fun undoNeedsAFinish() {
        timer.start()
        showApp()
        compose.onNodeWithText("Undo").assertIsNotEnabled()
    }

    @Test
    fun aRunningTimerOpensOnLaunchWithItsClock() {
        timer.start()
        clock.now += 65_000
        showApp()
        compose.onNodeWithTag("elapsed").assertTextEquals("1:05")
    }

    @Test
    fun stoppingAsksFirst() {
        timer.start()
        timer.tap()
        showApp()
        val confirm = hasText("Stop") and hasAnyAncestor(isDialog())

        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithText("1 finisher recorded.", substring = true).assertExists()
        compose.onNodeWithText("Keep timing").performClick()
        assertEquals(1, timer.state.value.session?.times?.size)

        compose.onNodeWithText("Stop").performClick()
        compose.onNode(confirm).performClick()
        assertNull(timer.state.value.session)
        compose.onNodeWithText("Start").assertExists()
    }
}
