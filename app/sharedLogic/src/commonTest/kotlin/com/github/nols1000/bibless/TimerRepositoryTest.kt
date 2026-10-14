@file:OptIn(ExperimentalCoroutinesApi::class)

package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.RecordTimeEvent
import com.github.nols1000.bibless.race.RemoveTimeEvent
import com.github.nols1000.bibless.race.StartEvent
import com.github.nols1000.bibless.race.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class FakeClock(var wall: Long = 1_700_000_000_000, var monotonic: Long = 50_000, var boot: String = "boot-1") : RaceClock {
    override fun wallMillis() = wall
    override fun monotonicMillis() = monotonic
    override fun bootId() = boot

    fun advance(millis: Long) {
        wall += millis
        monotonic += millis
    }
}

class TimerRepositoryTest {
    private val store = InMemoryStore()
    private val clock = FakeClock()

    private fun TestScope.timer() = TimerRepository(RaceDevice(store, clock, backgroundScope))

    @Test
    fun tapsRecordPositionsWithElapsedTime() = runTest {
        val timer = timer()
        timer.start()
        clock.advance(17 * 60_000 + 2_000)
        timer.tap()
        clock.advance(13_000)
        timer.tap()

        val session = timer.state.value.session!!
        assertEquals(clock.wall - 17 * 60_000 - 15_000, session.startedAt)
        assertEquals(listOf(1 to "17:02", 2 to "17:15"), session.times.map { it.position to formatElapsed(it.elapsedMs) })
        assertEquals(17 * 60_000 + 15_000L, timer.elapsedMs())
    }

    @Test
    fun undoRemovesTheLastFinish() = runTest {
        val timer = timer()
        timer.start()
        clock.advance(60_000)
        timer.tap()
        clock.advance(1_000)
        timer.tap()
        timer.undo()

        assertEquals(listOf(60_000L), timer.state.value.session!!.times.map { it.elapsedMs })
        timer.undo()
        timer.undo()
        assertTrue(timer.state.value.session!!.times.isEmpty())
    }

    @Test
    fun wallClockChangesDontMoveTimes() = runTest {
        val timer = timer()
        timer.start()
        clock.advance(30_000)
        clock.wall -= 3_600_000 // the phone's clock jumps back an hour
        timer.tap()

        assertEquals(30_000L, timer.state.value.session!!.times.single().elapsedMs)
    }

    @Test
    fun aKilledAppPicksUpWhereItWas() = runTest {
        val first = timer()
        first.start()
        clock.advance(10_000)
        first.tap()

        clock.advance(5_000)
        val reopened = timer()
        reopened.tap()

        assertEquals(listOf(10_000L, 15_000L), reopened.state.value.session!!.times.map { it.elapsedMs })
    }

    @Test
    fun afterARestartTheWallClockCarriesOn() = runTest {
        val first = timer()
        first.start()
        clock.advance(10_000)
        first.tap()

        clock.wall += 60_000
        clock.monotonic = 2_000 // restarted: the monotonic clock begins again
        clock.boot = "boot-2"
        val reopened = timer()
        assertEquals(70_000L, reopened.elapsedMs())
        clock.advance(1_000)
        reopened.tap()

        assertEquals(listOf(10_000L, 71_000L), reopened.state.value.session!!.times.map { it.elapsedMs })
    }

    @Test
    fun everyEventIsSignedIntoTheSessionChain() = runTest {
        val timer = timer()
        timer.start()
        clock.advance(1_000)
        timer.tap()
        timer.undo()
        advanceUntilIdle()

        val id = timer.state.value.session!!.id
        val verification = timer.log(id)!!.verify()
        assertTrue(verification.problems.isEmpty(), "${verification.problems}")
        val events = verification.entries.sortedBy { it.entry.seq }.map { it.entry.event }
        assertEquals(listOf(StartEvent::class, RecordTimeEvent::class, RemoveTimeEvent::class), events.map { it::class })
        assertEquals(id, events.first().id)
    }

    @Test
    fun unsignedTapsAreSignedOnTheNextLaunch() = runTest {
        // The app dies before signing: the taps are only in the session record
        val killed = TimerRepository(RaceDevice(store, clock, TestScope()))
        killed.start()
        clock.advance(1_000)
        killed.tap()
        val id = killed.state.value.session!!.id

        val reopened = timer()
        advanceUntilIdle()
        assertEquals(2, reopened.log(id)!!.verify().entries.size)
    }

    @Test
    fun stoppingKeepsTheSession() = runTest {
        val timer = timer()
        timer.start()
        clock.advance(1_000)
        timer.tap()
        val id = timer.state.value.session!!.id
        timer.stop()

        assertNull(timer.state.value.session)
        assertNull(timer.elapsedMs())
        assertNull(timer().state.value.session)
        assertEquals(listOf(id), timer.sessionIds())
        assertEquals(2, timer.log(id)!!.events.size)

        timer.start()
        assertEquals(2, timer.sessionIds().size)
    }

    @Test
    fun theSameDeviceKeySignsEverySession() = runTest {
        val timer = timer()
        timer.start()
        val first = timer.state.value.session!!.id
        timer.stop()
        timer.start()
        val second = timer.state.value.session!!.id

        val authors = listOf(first, second).map { timer.log(it)!!.verify().entries.single().entry.author }
        assertEquals(1, authors.toSet().size)
    }

    @Test
    fun elapsedTimeIsFormattedLikeResults() {
        assertEquals("0:00", formatElapsed(999))
        assertEquals("17:02", formatElapsed(17 * 60_000 + 2_999))
        assertEquals("1:02:03", formatElapsed(3_723_000))
    }
}
