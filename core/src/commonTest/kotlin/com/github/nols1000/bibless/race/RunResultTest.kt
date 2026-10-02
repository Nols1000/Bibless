package com.github.nols1000.bibless.race

import com.github.nols1000.bibless.race.crypto.DeviceKey
import com.github.nols1000.bibless.race.crypto.toBase64Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RunResultTest {

    private suspend fun publishedRun(): Pair<TestRun, SignedRunResult> {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        timer.time(2000)
        val scanner = run.session()
        scanner.athlete("A1", 1)
        scanner.athlete("A2", 2)
        run.certify(timer)
        run.certify(scanner)
        val state = run.state()
        return run to publishResult(run.organiser, state, combineResults(state), START + 10_000)
    }

    @Test
    fun publishedResultVerifies() = runTest {
        val (run, result) = publishedRun()
        val check = verifyRunResult(run.log(), result)
        assertIs<ResultCheck.Valid>(check)
        assertEquals(listOf("A1", "A2"), check.result.rows.map { it.athleteId })
    }

    @Test
    fun laterDataDoesNotInvalidateAPublishedResult() = runTest {
        val (run, result) = publishedRun()
        val late = run.session()
        late.athlete("A3", 3)
        run.certify(late)
        assertIs<ResultCheck.Valid>(verifyRunResult(run.log(), result))
    }

    @Test
    fun editedRowFails() = runTest {
        val (run, result) = publishedRun()
        val edited = result.copy(json = result.json.replace("\"A2\"", "\"A7\""))
        assertEquals(ResultCheck.BadSignature, verifyRunResult(run.log(), edited))
    }

    @Test
    fun rowsThatDontMatchTheLogFail() = runTest {
        val (run, _) = publishedRun()
        val state = run.state()
        val results = combineResults(state)
        val wrong = results.copy(rows = results.rows.reversed())
        val signed = publishResult(run.organiser, state, wrong, START)
        assertIs<ResultCheck.RowsDiffer>(verifyRunResult(run.log(), signed))
    }

    @Test
    fun missingEventFails() = runTest {
        val (run, result) = publishedRun()
        val log = run.log()
        val withoutAScan = log.copy(events = log.events.filterNot { "\"A1\"" in it.json })
        assertIs<ResultCheck.MissingEvents>(verifyRunResult(withoutAScan, result))
    }

    @Test
    fun missingCertificateFails() = runTest {
        val (run, result) = publishedRun()
        val log = run.log().copy(certificates = run.certificates.take(1))
        assertIs<ResultCheck.BadCertificates>(verifyRunResult(log, result))
    }

    @Test
    fun onlyTheOrganiserPublishes() = runTest {
        val (run, _) = publishedRun()
        val state = run.state()
        val impostor = DeviceKey.generate()
        assertFailsWith<IllegalArgumentException> { publishResult(impostor, state, combineResults(state), START) }

        // Signed by someone else, the result is rejected even though it is well-formed
        val result = publishResult(run.organiser, state, combineResults(state), START)
        val forged = raceJson.decodeFromString(RunResult.serializer(), result.json).copy(publisher = impostor.author)
        val json = raceJson.encodeToString(RunResult.serializer(), forged)
        val signed = SignedRunResult(json, impostor.sign(json.encodeToByteArray()).toBase64Url())
        assertEquals(ResultCheck.NotOrganiser, verifyRunResult(run.log(), signed))
    }
}
