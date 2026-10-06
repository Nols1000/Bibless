package com.github.nols1000.bibless.race

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultsTest {

    private fun Results.table() = rows.map { listOf(it.position, it.elapsedMs, it.athleteId) }

    @Test
    fun tokensJoinOnPosition() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1_020_000)
        timer.time(1_035_000)
        val scanner = run.session()
        scanner.athlete("A2", 2)
        scanner.athlete("A1", 1)
        run.certify(timer)
        run.certify(scanner)
        val results = run.results()
        assertEquals(listOf(listOf(1, 1_020_000L, "A1"), listOf(2, 1_035_000L, "A2")), results.table())
        assertEquals(emptyList(), results.issues)
        assertEquals(timer.chain, results.timerChain)
    }

    @Test
    fun removingATimeRenumbersTheRest() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        val accidental = timer.time(1500)
        timer.time(2000)
        timer.removeTime(accidental)
        run.certify(timer)
        assertEquals(listOf(1000L, 2000L), run.results().rows.map { it.elapsedMs })
    }

    @Test
    fun organiserInsertsAMissedTime() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        timer.time(3000)
        run.certify(timer)
        run.recordTime(2000)
        assertEquals(listOf(1000L, 2000L, 3000L), run.results().rows.map { it.elapsedMs })
    }

    @Test
    fun gapsAndConflictsAreReported() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        timer.time(2000)
        val scanner = run.session()
        val first = scanner.athlete("A1", 1)
        val second = scanner.athlete("A9", 1)
        scanner.athlete("A1", 3)
        scanner.athlete("A5")
        run.certify(timer)
        run.certify(scanner)
        val results = run.results()
        assertEquals(listOf(listOf<Any?>(1, 1000L, "A1"), listOf<Any?>(2, 2000L, null), listOf<Any?>(3, null, "A1")), results.table())
        assertEquals(
            listOf(
                ResultIssue.DuplicatePosition(1, listOf(first, second)),
                ResultIssue.MissingPosition(results.issues.filterIsInstance<ResultIssue.MissingPosition>().single().eventId),
                ResultIssue.MissingAthlete(2),
                ResultIssue.MissingTime(3),
                ResultIssue.DuplicateAthlete("A1", listOf(1, 3)),
            ),
            results.issues,
        )
    }

    @Test
    fun unknownFinisherIsNotMissing() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        val scanner = run.session()
        scanner.athlete(null, 1)
        run.certify(timer)
        run.certify(scanner)
        val results = run.results()
        assertEquals(listOf(listOf<Any?>(1, 1000L, null)), results.table())
        assertEquals(emptyList(), results.issues)
    }

    @Test
    fun scanOrderGivesPositions() = runTest {
        val run = TestRun.create(TimingMode.SCAN_ORDER)
        val timer = run.session()
        timer.time(1000)
        timer.time(2000)
        timer.time(3000)
        val scanner = run.session()
        scanner.athlete("A1")
        scanner.athlete("A2")
        run.certify(timer)
        run.certify(scanner)
        val results = run.results()
        assertEquals(listOf("A1", "A2", null), results.rows.map { it.athleteId })
        assertEquals(listOf(ResultIssue.CountMismatch(3, 2), ResultIssue.MissingAthlete(3)), results.issues)
    }

    @Test
    fun insertingIntoScanOrderShiftsTheRest() = runTest {
        val run = TestRun.create(TimingMode.SCAN_ORDER)
        val timer = run.session()
        repeat(3) { timer.time(1000L * (it + 1)) }
        val scanner = run.session()
        scanner.athlete("A1")
        scanner.athlete("A3")
        run.certify(timer)
        run.certify(scanner)
        run.recordAthlete("A2", position = 2)
        val results = run.results()
        assertEquals(listOf("A1", "A2", "A3"), results.rows.map { it.athleteId })
        assertEquals(emptyList(), results.issues)
    }

    @Test
    fun movesInScanOrderBuildOnEachOther() = runTest {
        val run = TestRun.create(TimingMode.SCAN_ORDER)
        val scanner = run.session()
        val ids = listOf("A1", "A2", "A3", "A4").associateWith { scanner.athlete(it) }
        run.certify(scanner)
        // Move A4 to 2nd, then A3 to 2nd: both corrections hold, nobody is dropped
        run.removeAthlete(ids.getValue("A4"))
        run.recordAthlete("A4", position = 2)
        run.removeAthlete(ids.getValue("A3"))
        run.recordAthlete("A3", position = 2)
        val results = run.results()
        assertEquals(listOf("A1", "A3", "A4", "A2"), results.rows.map { it.athleteId })
        assertEquals(listOf(ResultIssue.CountMismatch(0, 4)), results.issues.filterIsInstance<ResultIssue.CountMismatch>())
        assertTrue(results.issues.none { it is ResultIssue.DuplicatePosition })
    }

    @Test
    fun aPositionPastTheEndLeavesAGap() = runTest {
        val run = TestRun.create(TimingMode.SCAN_ORDER)
        val scanner = run.session()
        scanner.athlete("A1")
        run.certify(scanner)
        run.recordAthlete("A3", position = 3)
        assertEquals(listOf("A1", null, "A3"), run.results().rows.map { it.athleteId })
    }

    @Test
    fun unknownFinishersKeepTheirName() = runTest {
        val run = TestRun.create()
        val scanner = run.session()
        scanner.athlete(null, 1, name = "Sam")
        run.certify(scanner)
        assertEquals("Sam", run.results().rows.single().name)
    }

    @Test
    fun severalScannersInScanOrderAreReported() = runTest {
        val run = TestRun.create(TimingMode.SCAN_ORDER)
        val a = run.session()
        a.athlete("A1")
        val b = run.session()
        b.athlete("A2")
        run.certify(a)
        run.certify(b)
        assertEquals(
            ResultIssue.SeveralScannersInScanOrder(listOf(a.chain, b.chain).sorted()),
            run.results().issues.first(),
        )
    }

    @Test
    fun timerWithMostTimesIsUsedUnlessChosen() = runTest {
        val run = TestRun.create()
        val main = run.session()
        main.time(1000)
        main.time(2000)
        val backup = run.session(start = START + 500)
        backup.time(1600)
        run.certify(main)
        run.certify(backup)
        val results = run.results()
        assertEquals(main.chain, results.timerChain)
        assertEquals(ResultIssue.SeveralTimers(listOf(main.chain, backup.chain).sorted(), main.chain), results.issues.first())
        assertEquals(listOf(1600L), run.results(backup.chain).rows.map { it.elapsedMs })
    }

    @Test
    fun timesAreMeasuredFromTheSessionStart() = runTest {
        // Times are start + monotonic elapsed, so the session's start is all that matters
        val run = TestRun.create()
        val timer = run.session(start = START + 3_600_000)
        timer.time(1000)
        run.certify(timer)
        assertEquals(listOf(1000L), run.results().rows.map { it.elapsedMs })
    }

    @Test
    fun bundlesFromTwoDevicesMerge() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        val scanner = run.session()
        scanner.athlete("A1", 1)
        val organiserDevice = RunLog(events = run.events.filter { it !in timer.entries && it !in scanner.entries })
        val merged = organiserDevice
            .merge(RunLog(events = timer.entries.toList()))
            .merge(RunLog(events = scanner.entries.toList()))
            .merge(RunLog(events = timer.entries.toList()))
        val certificates = listOf(
            certify(run.organiser, run.run, timer.head(), 1),
            certify(run.organiser, run.run, scanner.head(), 2),
        )
        val state = foldRun(merged.copy(certificates = certificates), run.run)!!
        assertEquals(listOf(ResultRow(1, 1000, "A1", state.times.single().eventId, state.athletes.single().eventId)), combineResults(state).rows)
    }
}
