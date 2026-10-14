package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.RunLog
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchSessionsTest {
    private val watchStore = InMemoryStore()
    private val phoneStore = InMemoryStore()

    // 2026-10-06T07:30:00Z
    private val clock = FakeClock(wall = 1_791_271_800_000)
    private val sent = mutableListOf<Pair<String, String>>()

    private fun TestScope.watchTimer() = TimerRepository(RaceDevice(watchStore, clock, backgroundScope))

    private fun sender(timer: TimerRepository, fail: Boolean = false) = WatchSessionSender(timer, watchStore, "Pixel Watch 3") { id, json ->
        if (!fail) sent += id to json
        !fail
    }

    private suspend fun TestScope.recordSession(timer: TimerRepository, vararg elapsed: Long): String {
        timer.start()
        val id = timer.state.value.session!!.id
        var at = 0L
        for (time in elapsed) {
            clock.advance(time - at)
            at = time
            timer.tap()
        }
        timer.stop()
        return id
    }

    @Test
    fun stoppedSessionsAreSentOnce() = runTest {
        val timer = watchTimer()
        val first = recordSession(timer, 61_000, 75_000)
        timer.start()
        val sender = sender(timer)

        sender.sendStopped()
        sender.sendStopped()

        assertEquals(listOf(first), sent.map { it.first })
        assertEquals("Pixel Watch 3", RunLog.fromJson(sent.single().second).source)
    }

    @Test
    fun sessionsThatCouldNotBeSentAreOfferedAgain() = runTest {
        val timer = watchTimer()
        val id = recordSession(timer, 61_000)

        sender(timer, fail = true).sendStopped()
        sender(timer).sendStopped()

        assertEquals(listOf(id), sent.map { it.first })
    }

    @Test
    fun thePhoneListsAndExportsWatchSessions() = runTest {
        val timer = watchTimer()
        val id = recordSession(timer, 61_000, 75_000)
        timer.start()
        timer.undo()
        sender(timer).sendStopped()
        val received = ReceivedSessions(phoneStore)

        assertTrue(received.receive(sent.single().second))
        val phone = RaceDevice(InMemoryStore(), FakeClock(), backgroundScope)
        val exporter = SessionExporter(TimerRepository(phone), ScannerRepository(phone), received)
        val session = exporter.sessions().single()
        assertEquals(RecordedSession(SessionKind.TIMER, id, 1_791_271_800_000, null, 2, running = false, source = "Pixel Watch 3"), session)

        val files = exporter.export(SessionKind.TIMER, id, "Pixel 10", utcOffsetMinutes = 120)!!
        assertEquals(listOf("bibless-timer-2026-10-06-0930-Pixel-Watch-3.csv", "bibless-timer-2026-10-06-0930-Pixel-Watch-3.json"), files.map { it.name })
        assertEquals("position,time,elapsed_ms\n1,1:01,61000\n2,1:15,75000\n", files[0].content)
        assertEquals("Pixel Watch 3", RunLog.fromJson(files[1].content).source)
        assertEquals(timer.log(id)!!.events, RunLog.fromJson(files[1].content).events)
    }

    @Test
    fun receivedSessionsSurviveARestartAndKeepTheFullerCopy() = runTest {
        val timer = watchTimer()
        val id = recordSession(timer, 61_000, 75_000)
        val full = timer.log(id)!!
        val partial = full.copy(events = full.events.sortedBy { it.json.substringAfter("\"seq\":").substringBefore(",").toLong() }.take(2))

        ReceivedSessions(phoneStore).receive(full.toJson())
        ReceivedSessions(phoneStore).receive(partial.toJson())

        assertEquals(2, ReceivedSessions(phoneStore).times(id)!!.size)
        assertEquals("Watch", ReceivedSessions(phoneStore).sessions().single().source)
    }

    @Test
    fun onlyTheVerifiedPartOfASessionIsKept() = runTest {
        val timer = watchTimer()
        val id = recordSession(timer, 61_000, 75_000)
        val log = timer.log(id)!!
        val bySeq = log.events.sortedBy { it.json.substringAfter("\"seq\":").substringBefore(",").toLong() }
        // The second time is changed after signing, so it and everything after it is left out
        val tampered = log.copy(events = bySeq.dropLast(1) + bySeq.last().copy(json = bySeq.last().json.replace("\"timestamp\":", "\"timestamp\":1")))

        assertTrue(ReceivedSessions(phoneStore).receive(tampered.toJson()))
        assertEquals(listOf(61_000L), ReceivedSessions(phoneStore).times(id)!!.map { it.elapsedMs })
    }

    @Test
    fun otherFilesAreNotSessions() = runTest {
        val received = ReceivedSessions(phoneStore)
        assertFalse(received.receive("not json"))
        assertFalse(received.receive(RunLog().toJson()))
        val timer = watchTimer()
        timer.start()
        timer.stop()
        assertFalse(received.receive(timer.log(timer.sessionIds().single())!!.toJson()), "a session without times")
        assertTrue(received.sessions().isEmpty())
    }
}
