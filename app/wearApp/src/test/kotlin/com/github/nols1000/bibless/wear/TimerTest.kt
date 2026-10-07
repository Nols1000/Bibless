package com.github.nols1000.bibless.wear

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.nols1000.bibless.BarcodeRepository
import com.github.nols1000.bibless.Device
import com.github.nols1000.bibless.InMemoryStore
import com.github.nols1000.bibless.RaceClock
import com.github.nols1000.bibless.RaceDevice
import com.github.nols1000.bibless.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The watch stopwatch, on a fake clock so the times are exact. */
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
    private val timer = TimerRepository(RaceDevice(InMemoryStore(), clock, CoroutineScope(Dispatchers.Default)))
    private val buzzes = mutableListOf<HapticFeedbackType>()

    private fun showApp(ambient: Ambient = Ambient()) = compose.setContent {
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                buzzes += hapticFeedbackType
            }
        }
        CompositionLocalProvider(LocalHapticFeedback provides haptics) {
            WearApp(BarcodeRepository(InMemoryStore(), Device.WATCH), timer, ambient = ambient)
        }
    }

    @Test
    fun timesFinishersWithABuzzEach() {
        showApp()
        compose.onNodeWithText("Timer").performClick()
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithText("Undo").assertIsNotEnabled()

        clock.now += 17 * 60_000 + 2_000
        compose.onNodeWithText("Finish").performClick()
        clock.now += 13_000
        compose.onNodeWithText("Finish").performClick()

        compose.onNodeWithText("P3").assertExists()
        compose.onNodeWithText("P2 17:15").assertExists()
        assertEquals(listOf(HapticFeedbackType.Confirm, HapticFeedbackType.Confirm), buzzes)

        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("P1 17:02").assertExists()
        assertEquals(listOf(17 * 60_000 + 2_000L), timer.state.value.session!!.times.map { it.elapsedMs })
    }

    @Test
    fun stoppingAsksFirst() {
        timer.start()
        timer.tap()
        showApp()

        // Opens on the running timer
        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithText("Stop the timer?").assertExists()
        compose.onNodeWithText("1 finisher. The times go to your phone.").assertExists()
        compose.onNodeWithTag("elapsed").assertExists()
    }

    @Test
    fun confirmingStopsTheTimer() {
        timer.start()
        showApp()
        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithContentDescription("Confirm").performClick()
        compose.waitUntil(5_000) { timer.state.value.session == null }

        assertNull(timer.state.value.session)
        compose.onNodeWithText("Start").assertExists()
        assertEquals(1, timer.sessionIds().size)
    }

    @Test
    fun ambientShowsMinutesAndTheCount() {
        timer.start()
        timer.tap()
        clock.now += 24 * 60_000 + 30_000
        showApp(Ambient(isAmbient = true))

        compose.onNodeWithText("24 min").assertExists()
        compose.onNodeWithText("1 finisher").assertExists()
        compose.onNodeWithText("Finish").assertDoesNotExist()
    }

    @Test
    fun otherScreensGoDarkInAmbient() {
        showApp(Ambient(isAmbient = true))
        compose.onNodeWithText("Bibless").assertExists()
    }
}
