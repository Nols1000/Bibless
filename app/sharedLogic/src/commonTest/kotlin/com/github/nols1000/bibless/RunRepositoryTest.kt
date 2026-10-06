package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.ChainWriter
import com.github.nols1000.bibless.race.Event
import com.github.nols1000.bibless.race.RecordAthleteEvent
import com.github.nols1000.bibless.race.RecordTimeEvent
import com.github.nols1000.bibless.race.ResultIssue
import com.github.nols1000.bibless.race.RunLog
import com.github.nols1000.bibless.race.SignedEvent
import com.github.nols1000.bibless.race.StartEvent
import com.github.nols1000.bibless.race.TimingMode
import com.github.nols1000.bibless.race.crypto.DeviceKey
import com.github.nols1000.bibless.race.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunRepositoryTest {
    private val store = InMemoryStore()
    private var clock = 1_000L
    private val repository = RunRepository(store) { clock++ }

    @Test
    fun createdRunsAreListedNewestFirst() = runTest {
        val first = repository.create("Riverside 5k", "2026-10-10", TimingMode.TOKENS)
        val second = repository.create("  Park 2k  ", null, TimingMode.SCAN_ORDER)

        assertEquals(listOf(second, first), repository.runs())
        assertEquals("Park 2k", second.name)
        assertNull(second.date)
        assertEquals(RunSummary(first.id, "Riverside 5k", "2026-10-10", TimingMode.TOKENS, 1_000), repository.run(first.id))
    }

    @Test
    fun runsSurviveANewRepository() = runTest {
        val run = repository.create("Riverside 5k", null, TimingMode.TOKENS)
        val reopened = RunRepository(store)

        assertEquals(listOf(run), reopened.runs())
        assertEquals(repository.organiserKey().author, reopened.organiserKey().author)
    }

    @Test
    fun theOrganiserKeyIsTheRunsRoot() = runTest {
        val run = repository.create("Riverside 5k", "2026-10-10", TimingMode.SCAN_ORDER)
        val hint = repository.hint(run.id)!!

        assertEquals(repository.organiserKey().author, hint.root)
        assertEquals(run.id, hint.run)
        assertEquals(TimingMode.SCAN_ORDER, hint.format)
        assertEquals("2026-10-10", hint.date)
    }

    @Test
    fun theStoredLogVerifies() = runTest {
        val run = repository.create("Riverside 5k", null, TimingMode.TOKENS)
        val verification = repository.log(run.id)!!.verify()

        assertTrue(verification.problems.isEmpty(), "${verification.problems}")
        assertEquals(1, verification.entries.size)
    }

    @Test
    fun deletedRunsAreGone() = runTest {
        val kept = repository.create("Riverside 5k", null, TimingMode.TOKENS)
        val deleted = repository.create("Park 2k", null, TimingMode.TOKENS)
        repository.delete(deleted.id)

        assertEquals(listOf(kept), repository.runs())
        assertNull(repository.run(deleted.id))
        assertNull(repository.hint(deleted.id))
    }

    @Test
    fun aRunNeedsAName() = runTest {
        assertFailsWith<IllegalArgumentException> { repository.create(" ", null, TimingMode.TOKENS) }
        assertTrue(repository.runs().isEmpty())
    }

    @Test
    fun importingJoinedSessionsCombinesThem() = runTest {
        val run = repository.create("Riverside 5k", null, TimingMode.TOKENS)
        val timer = Volunteer(run.id)
        timer.time(65_000)
        timer.time(70_000)
        val scanner = Volunteer(run.id)
        scanner.finisher("A2", 2)
        scanner.finisher("A1", 1)

        assertEquals(1, repository.import(run.id, timer.export("Pixel 9")))
        assertEquals(1, repository.import(run.id, scanner.export("iPhone")))
        val review = repository.review(run.id)!!

        assertEquals(listOf(listOf(1, 65_000L, "A1"), listOf(2, 70_000L, "A2")), review.table())
        assertEquals(emptyList(), review.results.issues)
        assertEquals(
            listOf(
                RunSession(timer.chain, "Pixel 9", SessionKind.TIMER, START, run.id, 2, included = true),
                RunSession(scanner.chain, "iPhone", SessionKind.SCANNER, START + 1, run.id, 2, included = true),
            ),
            review.sessions,
        )
    }

    @Test
    fun importingTwiceChangesNothing() = runTest {
        val run = repository.create("Riverside 5k", null, TimingMode.TOKENS)
        val timer = Volunteer(run.id)
        timer.time(65_000)
        repository.import(run.id, timer.export("Pixel 9"))
        val log = repository.log(run.id)

        assertEquals(0, repository.import(run.id, timer.export("Pixel 9")))
        assertEquals(log, repository.log(run.id))
    }

    @Test
    fun newerExportsOfAnIncludedSessionAreIncluded() = runTest {
        val run = repository.create("Riverside 5k", null, TimingMode.TOKENS)
        val timer = Volunteer(run.id)
        timer.time(65_000)
        repository.import(run.id, timer.export("Pixel 9"))
        timer.time(70_000)
        repository.import(run.id, timer.export("Pixel 9"))

        assertEquals(listOf(65_000L, 70_000L), repository.review(run.id)!!.results.rows.map { it.elapsedMs })
    }

    @Test
    fun sessionsThatDidNotJoinWaitToBeIncluded() = runTest {
        val run = repository.create("Riverside 5k", null, TimingMode.TOKENS)
        val timer = Volunteer(run = null)
        timer.time(65_000)
        repository.import(run.id, timer.export("Pixel 9"))

        val before = repository.review(run.id)!!
        assertEquals(false, before.sessions.single().included)
        assertTrue(before.results.rows.isEmpty())

        repository.include(run.id, timer.chain)
        timer.time(70_000)
        repository.import(run.id, timer.export("Pixel 9"))
        val after = repository.review(run.id)!!
        assertEquals(true, after.sessions.single().included)
        assertEquals(listOf(65_000L, 70_000L), after.results.rows.map { it.elapsedMs })
    }

    @Test
    fun aFileThatIsNotARunLogIsRejected() = runTest {
        val run = repository.create("Riverside 5k", null, TimingMode.TOKENS)
        assertFailsWith<IllegalArgumentException> { repository.import(run.id, "position,time\n1,1:05") }
        assertFailsWith<IllegalArgumentException> { repository.import("unknown", RunLog().toJson()) }
    }

    @Test
    fun theOrganiserChoosesTheTimer() = runTest {
        val run = repository.create("Riverside 5k", null, TimingMode.TOKENS)
        val main = Volunteer(run.id)
        main.time(65_000)
        main.time(70_000)
        val backup = Volunteer(run.id)
        backup.time(66_000)
        repository.import(run.id, main.export("Pixel 9"))
        repository.import(run.id, backup.export("iPhone"))

        assertEquals(main.chain, repository.review(run.id)!!.results.timerChain)
        repository.chooseTimer(run.id, backup.chain)
        assertEquals(listOf(66_000L), repository.review(run.id)!!.results.rows.map { it.elapsedMs })
    }

    @Test
    fun theOrganiserFixesTimesAndFinishers() = runTest {
        val run = repository.create("Riverside 5k", null, TimingMode.TOKENS)
        val timer = Volunteer(run.id)
        timer.time(65_000)
        timer.time(66_000)
        val scanner = Volunteer(run.id)
        val a1 = scanner.finisher("A1", 1)
        scanner.finisher("A2", 1)
        repository.import(run.id, timer.export("Pixel 9"))
        repository.import(run.id, scanner.export("iPhone"))
        val duplicate = repository.review(run.id)!!.results.issues.filterIsInstance<ResultIssue.DuplicatePosition>().single()

        repository.removeTime(run.id, repository.review(run.id)!!.results.rows[1].timeEventId!!)
        repository.addTime(run.id, 70_000)
        repository.editFinisher(run.id, duplicate.eventIds[1], 2, "a2", null)
        repository.addFinisher(run.id, 3, null, " Sam ")
        repository.editFinisher(run.id, a1, 1, "1", null)
        val review = repository.review(run.id)!!

        assertEquals(listOf(listOf(1, 65_000L, "A1"), listOf(2, 70_000L, "A2"), listOf(3, null, null)), review.table())
        assertEquals("Sam", review.results.rows[2].name)
        assertEquals(listOf<ResultIssue>(ResultIssue.MissingTime(3)), review.results.issues)
        assertTrue(repository.log(run.id)!!.verify().problems.isEmpty())
        assertFailsWith<IllegalArgumentException> { repository.addFinisher(run.id, 4, "B12", null) }
    }

    @Test
    fun insertingInScanOrderMovesEveryoneBehind() = runTest {
        val run = repository.create("Park 2k", null, TimingMode.SCAN_ORDER)
        val scanner = Volunteer(run.id)
        scanner.finisher("A1")
        scanner.finisher("A3")
        repository.import(run.id, scanner.export("iPhone"))
        repository.addFinisher(run.id, 2, null, null)

        assertEquals(listOf("A1", null, "A3"), repository.review(run.id)!!.results.rows.map { it.athleteId })
    }

    @Test
    fun resultsExportAsCsv() = runTest {
        val run = repository.create("Riverside 5k", null, TimingMode.TOKENS)
        val timer = Volunteer(run.id)
        timer.time(65_000)
        timer.time(3_725_000)
        val scanner = Volunteer(run.id)
        scanner.finisher("A1", 1)
        scanner.finisher(null, 2, "Sam, Jr.")
        repository.import(run.id, timer.export("Pixel 9"))
        repository.import(run.id, scanner.export("iPhone"))

        assertEquals(
            "position,time,elapsed_ms,athlete_id,name\n1,1:05,65000,A1,\n2,1:02:05,3725000,,\"Sam, Jr.\"\n",
            repository.resultsCsv(run.id),
        )
    }

    @Test
    fun deletingARunForgetsItsImports() = runTest {
        val run = repository.create("Riverside 5k", null, TimingMode.TOKENS)
        val timer = Volunteer(run = null)
        timer.time(65_000)
        repository.import(run.id, timer.export("Pixel 9"))
        repository.include(run.id, timer.chain)
        repository.chooseTimer(run.id, timer.chain)
        repository.delete(run.id)

        val keys = listOf("run.${run.id}", "run.${run.id}.sources", "run.${run.id}.included", "run.${run.id}.timer")
        assertTrue(keys.all { store.get(it).isNullOrEmpty() })
    }

    private fun RunReview.table() = results.rows.map { listOf(it.position, it.elapsedMs, it.athleteId) }

    /** A volunteer's phone: one signed timer or scanner session, exported the way the apps do. */
    private inner class Volunteer(run: String?) {
        private val start = StartEvent(nextId(), START + volunteers++, run)
        val chain = start.id
        private var writer: ChainWriter? = null
        private val events = mutableListOf<SignedEvent>()

        suspend fun time(elapsed: Long) = add(RecordTimeEvent(nextId(), start.timestamp + elapsed))
        suspend fun finisher(athleteId: String?, position: Int? = null, name: String? = null) =
            add(RecordAthleteEvent(nextId(), clock++, athleteId, position, name))

        private suspend fun add(event: Event): String {
            val writer = writer ?: ChainWriter.start(DeviceKey.generate(), start).also {
                writer = it
                events += it.append(start)
            }
            events += writer.append(event)
            return event.id
        }

        fun export(device: String) = RunLog(events = events.toList(), source = device).toJson()
    }

    private var ids = 0
    private var volunteers = 0
    private fun nextId() = "v${ids++}"
}

private const val START = 5_000_000L
