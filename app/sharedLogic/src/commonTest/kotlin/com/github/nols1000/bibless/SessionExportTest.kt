package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.RunHint
import com.github.nols1000.bibless.race.RunLog
import com.github.nols1000.bibless.race.TimingMode
import com.github.nols1000.bibless.race.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionExportTest {
    private val store = InMemoryStore()

    // 2026-10-06T07:30:00Z
    private val clock = FakeClock(wall = 1_791_271_800_000)

    private fun TestScope.setUp(): Triple<RaceDevice, TimerRepository, ScannerRepository> {
        val device = RaceDevice(store, clock, backgroundScope)
        return Triple(device, TimerRepository(device), ScannerRepository(device))
    }

    @Test
    fun localTimesFollowTheCalendar() {
        assertEquals("1970-01-01T00:00:00", localDateTime(0))
        assertEquals("2026-10-06T07:30:00", localDateTime(1_791_271_800_000))
        assertEquals("2024-02-29T23:59:59", localDateTime(1_709_251_199_000))
        assertEquals("1969-12-31T23:59:59", localDateTime(-1_000))
    }

    @Test
    fun sessionsAreListedNewestFirst() = runTest {
        val (device, timer, scanner) = setUp()
        device.join(RunHint("run-1", "B".repeat(87), "Riverside 5k", TimingMode.TOKENS).toJson())
        timer.start()
        timer.tap()
        timer.stop()
        clock.advance(60_000)
        scanner.start(TimingMode.SCAN_ORDER)

        val sessions = SessionExporter(timer, scanner).sessions()
        assertEquals(listOf(SessionKind.SCANNER, SessionKind.TIMER), sessions.map { it.kind })
        assertEquals(listOf(true, false), sessions.map { it.running })
        assertEquals(listOf(0, 1), sessions.map { it.count })
        assertEquals(listOf("run-1", "run-1"), sessions.map { it.run })
        assertEquals(TimingMode.SCAN_ORDER, sessions.first().mode)
    }

    @Test
    fun timerExportsTimesAndItsSignedLog() = runTest {
        val (_, timer, scanner) = setUp()
        timer.start()
        clock.advance(17 * 60_000 + 2_000)
        timer.tap()
        clock.advance(13_000)
        timer.tap()
        val id = timer.state.value.session!!.id

        val files = SessionExporter(timer, scanner).export(SessionKind.TIMER, id, "Pixel 10", utcOffsetMinutes = 120)!!
        assertEquals(listOf("bibless-timer-2026-10-06-0930-Pixel-10.csv", "bibless-timer-2026-10-06-0930-Pixel-10.json"), files.map { it.name })
        assertEquals("position,time,elapsed_ms\n1,17:02,1022000\n2,17:15,1035000\n", files[0].content)

        val log = RunLog.fromJson(files[1].content)
        assertEquals("Pixel 10", log.source)
        val verification = log.verify()
        assertTrue(verification.problems.isEmpty(), "${verification.problems}")
        assertEquals(3, verification.entries.size)
    }

    @Test
    fun scannerExportsFinishersWithNamesAndScanTimes() = runTest {
        val (_, timer, scanner) = setUp()
        scanner.start(TimingMode.SCAN_ORDER)
        clock.advance(65_000)
        scanner.enter("A111111")
        clock.advance(5_000)
        scanner.addUnknown("Sam \"Speedy\", Jr")
        val id = scanner.state.value.session!!.id

        val files = SessionExporter(timer, scanner).export(SessionKind.SCANNER, id, "iPhone", utcOffsetMinutes = 0)!!
        assertEquals("bibless-scanner-2026-10-06-0730-iPhone.csv", files[0].name)
        assertEquals(
            "position,athlete_id,name,scanned_at\n" +
                "1,A111111,,2026-10-06T07:31:05\n" +
                "2,,\"Sam \"\"Speedy\"\", Jr\",2026-10-06T07:31:10\n",
            files[0].content,
        )
    }

    @Test
    fun unknownSessionsExportNothing() = runTest {
        val (_, timer, scanner) = setUp()
        assertNull(SessionExporter(timer, scanner).export(SessionKind.TIMER, "nope", "Pixel", 0))
    }
}
