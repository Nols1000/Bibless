package com.github.nols1000.bibless.race

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoldTest {

    @Test
    fun uncertifiedChainsAreLeftOut() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        val state = run.state()
        assertEquals(emptyList(), state.times)
        assertTrue(state.problems.single() is RunProblem.Uncertified)
    }

    @Test
    fun certificateCoversTheChainUpToItsHead() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        run.certify(timer)
        timer.time(2000)
        assertEquals(listOf(START + 1000), run.state().times.map { it.timestamp })
        assertTrue(run.state().problems.single() is RunProblem.Uncertified)

        // A late upload needs a newer certificate
        run.certify(timer)
        assertEquals(listOf(START + 1000, START + 2000), run.state().times.map { it.timestamp }.sorted())
        assertEquals(emptyList(), run.state().problems)
    }

    @Test
    fun certificatesFromOthersDontCount() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        run.certify(timer, issuer = timer.key)
        val state = run.state()
        assertEquals(emptyList(), state.times)
        assertTrue(state.problems.any { it is RunProblem.BadCertificate })
    }

    @Test
    fun certificateForMissingEntriesIsReported() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        timer.time(2000)
        run.certify(timer)
        val log = run.log().copy(events = run.events - timer.entries[1])
        val state = foldRun(log, run.run)!!
        assertEquals(emptyList(), state.times)
        assertTrue(state.problems.any { it is RunProblem.MissingHead })
    }

    @Test
    fun authorsRemoveTheirOwnRecords() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        val first = timer.time(1000)
        timer.time(2000)
        timer.removeTime(first)
        run.certify(timer)
        assertEquals(listOf(START + 2000), run.state().times.map { it.timestamp })
    }

    @Test
    fun organiserRemovesAnyRecord() = runTest {
        val run = TestRun.create()
        val scanner = run.session()
        val wrong = scanner.athlete("A1", 1)
        run.certify(scanner)
        run.removeAthlete(wrong)
        run.recordAthlete("A2", 1)
        assertEquals(listOf("A2"), run.state().athletes.map { it.athleteId })
    }

    @Test
    fun volunteersCantRemoveOthersRecords() = runTest {
        val run = TestRun.create()
        val scanner = run.session()
        val record = scanner.athlete("A1", 1)
        val other = run.session()
        other.removeAthlete(record)
        run.certify(scanner)
        run.certify(other)
        val state = run.state()
        assertEquals(listOf("A1"), state.athletes.map { it.athleteId })
        assertTrue(state.problems.single() is RunProblem.IgnoredRemoval)
    }

    @Test
    fun runDateIsOptional() = runTest {
        assertEquals("2026-10-10", TestRun.create(date = "2026-10-10").state().date)
        assertNull(TestRun.create().state().date)
    }

    @Test
    fun logsWithoutDateStillRead() {
        val json = """{"type":"created","id":"r","timestamp":1,"name":"Old run","format":"TOKENS"}"""
        assertEquals(CreatedEvent("r", 1, "Old run", TimingMode.TOKENS), raceJson.decodeFromString(Event.serializer(), json))
    }

    @Test
    fun sessionsWithoutRunStillRead() {
        val json = """{"type":"start","id":"s","timestamp":1}"""
        assertEquals(StartEvent("s", 1), raceJson.decodeFromString(Event.serializer(), json))
    }

    @Test
    fun unknownRunHasNoState() = runTest {
        val run = TestRun.create()
        assertNull(foldRun(run.log(), "other"))
    }
}
