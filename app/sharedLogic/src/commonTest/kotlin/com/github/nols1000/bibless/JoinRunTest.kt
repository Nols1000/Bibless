package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.RunHint
import com.github.nols1000.bibless.race.StartEvent
import com.github.nols1000.bibless.race.TimingMode
import com.github.nols1000.bibless.race.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JoinRunTest {
    private val store = InMemoryStore()
    private val clock = FakeClock()
    private val hint = RunHint("run-1", "B".repeat(87), "Riverside 5k", TimingMode.TOKENS, "2026-10-10")

    private fun TestScope.device() = RaceDevice(store, clock, backgroundScope)

    @Test
    fun joiningTakesTheRunQrCode() = runTest {
        val device = device()
        assertEquals(hint, device.join(hint.toJson()))
        assertEquals(hint, device.joinedRun.value)
        assertEquals(hint, device().joinedRun.value)
    }

    @Test
    fun otherCodesDontChangeTheRun() = runTest {
        val device = device()
        device.join(hint.toJson())
        assertNull(device.join("A123456"))
        assertNull(device.join("""{"run":"x","root":"short","name":"n","format":"TOKENS"}"""))
        assertEquals(hint, device.joinedRun.value)
    }

    @Test
    fun sessionsRecordForTheJoinedRun() = runTest {
        val device = device()
        device.join(hint.toJson())
        val timer = TimerRepository(device).also { it.start() }
        assertEquals("run-1", timer.state.value.session!!.run)

        device.leave()
        assertEquals("run-1", timer.state.value.session!!.run, "a running session keeps its run")
        val scanner = ScannerRepository(device).also { it.start() }
        assertNull(scanner.state.value.session!!.run)
        assertNull(device().joinedRun.value)

        advanceUntilIdle()
        val start = timer.log(timer.state.value.session!!.id)!!.verify().entries.single().entry.event
        assertEquals("run-1", (start as StartEvent).run)
    }

    @Test
    fun observersHearAboutJoins() = runTest {
        val device = device()
        val seen = mutableListOf<RunHint?>()
        val observation = device.observeJoinedRun { seen += it }
        device.join(hint.toJson())
        device.leave()
        observation.close()
        device.join(hint.toJson())
        assertEquals(listOf(null, hint, null), seen)
    }
}
