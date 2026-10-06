package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.TimingMode
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
}
