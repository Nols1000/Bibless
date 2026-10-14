package com.github.nols1000.bibless.race

import com.github.nols1000.bibless.barcode.encodeQr
import com.github.nols1000.bibless.race.crypto.DeviceKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChainTest {

    @Test
    fun cleanLogVerifies() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        timer.time(2000)
        val verification = run.log().verify()
        assertEquals(emptyList(), verification.problems)
        assertEquals(4, verification.entries.size)
    }

    @Test
    fun changedByteBreaksTheSignature() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        val tampered = run.events.map {
            if ("recordTime" in it.json) it.copy(json = it.json.replace("${START + 1000}", "${START + 900}")) else it
        }
        val verification = RunLog(events = tampered).verify()
        assertTrue(verification.problems.single() is LogProblem.BadSignature)
        assertEquals(2, verification.entries.size)
    }

    @Test
    fun droppedEntryIsAGap() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        timer.time(2000)
        timer.time(3000)
        val log = RunLog(events = run.events - timer.entries[2])
        val verification = log.verify()
        assertEquals(listOf<LogProblem>(LogProblem.Gap(timer.key.author, timer.chain, 2)), verification.problems)
        // The entries after the gap are left out until it is filled
        assertEquals(1 + 2, verification.entries.size)
    }

    @Test
    fun twoEntriesWithTheSameSeqAreAFork() = runTest {
        val run = TestRun.create()
        val key = DeviceKey.generate()
        val genesis = StartEvent("s", START)
        val first = ChainWriter.start(key, genesis)
        val second = ChainWriter.start(key, genesis)
        val events = listOf(first.append(genesis), first.append(RecordTimeEvent("a", START + 1)))
        second.append(genesis)
        val fork = second.append(RecordTimeEvent("b", START + 2))
        val verification = RunLog(events = run.events + events + fork).verify()
        assertTrue(verification.problems.single() is LogProblem.Fork)
        assertEquals(1 + 1, verification.entries.size)
    }

    @Test
    fun chainMustStartWithItsOwnStartEvent() = runTest {
        val key = DeviceKey.generate()
        assertFailsWith<IllegalArgumentException> { ChainWriter(key, "s").append(RecordTimeEvent("s", START)) }
        assertFailsWith<IllegalArgumentException> { ChainWriter(key, "s").append(StartEvent("other", START)) }
    }

    @Test
    fun mergeIsCommutativeAndIdempotent() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        val scanner = run.session()
        scanner.athlete("A1", 1)
        val a = RunLog(events = run.events.take(3))
        val b = RunLog(events = run.events.drop(2))
        assertEquals(a.merge(b), b.merge(a))
        assertEquals(a.merge(b), a.merge(b).merge(a).merge(b))
        assertEquals(run.events.size, a.merge(b).events.size)
    }

    @Test
    fun logRoundTripsThroughJson() = runTest {
        val run = TestRun.create()
        val timer = run.session()
        timer.time(1000)
        run.certify(timer)
        val log = run.log()
        assertEquals(log, RunLog.fromJson(log.toJson()))
        assertFailsWith<IllegalArgumentException> { RunLog.fromJson("[]") }
    }

    @Test
    fun newerDataIsReportedNotDropped() = runTest {
        val run = TestRun.create()
        val json = run.log().copy(version = RunLog.VERSION + 1).toJson()
            .replace("\"events\":[", "\"events\":[{\"json\":\"{\\\"type\\\":\\\"future\\\"}\",\"sig\":\"x\"},")
        val verification = RunLog.fromJson(json).verify()
        assertTrue(verification.problems.any { it is LogProblem.UnsupportedVersion })
        assertTrue(verification.problems.any { it is LogProblem.Unreadable })
        assertEquals(1, verification.entries.size)
    }

    @Test
    fun runHintFitsASmallQrCode() = runTest {
        val run = TestRun.create(name = "Riverside Park Saturday 5k")
        val hint = RunHint("0f8fad5b-d9cb-469f-a165-70867728950e", run.organiser.author, "Riverside Park Saturday 5k", TimingMode.SCAN_ORDER, "2026-10-10")
        assertEquals(hint, RunHint.fromJson(hint.toJson()))
        // Version 11 is 61 modules wide: 1.3 mm per module on an 8 cm print
        assertTrue(encodeQr(hint.toJson()).width <= 61, "QR code is ${encodeQr(hint.toJson()).width} modules wide")
    }

    @Test
    fun mergingKeepsTheSourceOnlyIfBothAgree() {
        val pixel = RunLog(source = "Pixel 10")
        assertEquals("Pixel 10", pixel.merge(pixel).source)
        assertEquals(null, pixel.merge(RunLog(source = "iPhone")).source)
        assertEquals("Pixel 10", RunLog.fromJson(pixel.toJson()).source)
    }
}
